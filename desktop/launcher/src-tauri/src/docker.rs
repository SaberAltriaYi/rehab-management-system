use crate::error::{LauncherError, LauncherResult};
use crate::model::{default_service_states, ServiceState, COMPOSE_PROJECT};
use crate::runner::{find_docker, CommandOutput, CommandRunner};
use serde::Deserialize;
use std::ffi::OsString;
use std::path::{Path, PathBuf};
use std::thread;
use std::time::{Duration, Instant};

#[derive(Clone, Debug)]
pub struct DockerContext {
    pub executable: PathBuf,
    pub compose_file: PathBuf,
    pub env_file: PathBuf,
    pub working_dir: PathBuf,
}

impl DockerContext {
    pub fn compose_args<I, S>(&self, trailing: I) -> Vec<OsString>
    where
        I: IntoIterator<Item = S>,
        S: Into<OsString>,
    {
        let mut args = vec![
            OsString::from("compose"),
            OsString::from("--project-name"),
            OsString::from(COMPOSE_PROJECT),
            OsString::from("--env-file"),
            self.env_file.as_os_str().to_owned(),
            OsString::from("--file"),
            self.compose_file.as_os_str().to_owned(),
        ];
        args.extend(trailing.into_iter().map(Into::into));
        args
    }
}

pub fn detect_docker(runner: &dyn CommandRunner) -> LauncherResult<PathBuf> {
    let executable = find_docker(runner)?;
    let info = runner.run(
        &executable,
        &[
            OsString::from("info"),
            OsString::from("--format"),
            OsString::from("{{json .ServerVersion}}"),
        ],
        None,
    )?;
    if !info.success {
        return Err(LauncherError::DockerDaemonUnavailable);
    }
    let compose = runner.run(
        &executable,
        &[
            OsString::from("compose"),
            OsString::from("version"),
            OsString::from("--short"),
        ],
        None,
    )?;
    if !compose.success {
        return Err(LauncherError::ComposeUnavailable);
    }
    Ok(executable)
}

pub fn run_compose(
    runner: &dyn CommandRunner,
    context: &DockerContext,
    trailing: &[&str],
) -> LauncherResult<CommandOutput> {
    let args = context.compose_args(trailing.iter().copied());
    let output = runner.run(&context.executable, &args, Some(&context.working_dir))?;
    if !output.success {
        return Err(LauncherError::CommandFailed(safe_error_summary(
            &output.stderr,
        )));
    }
    Ok(output)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "PascalCase")]
struct ComposeProcess {
    service: Option<String>,
    state: Option<String>,
    health: Option<String>,
}

pub fn read_service_states(
    runner: &dyn CommandRunner,
    context: &DockerContext,
) -> LauncherResult<Vec<ServiceState>> {
    let output = run_compose(runner, context, &["ps", "--format", "json"])?;
    if output.stdout.trim().is_empty() {
        return Ok(default_service_states("stopped", "未启动"));
    }
    let mut states = default_service_states("stopped", "未启动");
    for value in parse_json_stream(&output.stdout) {
        let process: ComposeProcess = serde_json::from_value(value)
            .map_err(|error| LauncherError::Internal(format!("解析容器状态失败：{error}")))?;
        let Some(service_name) = process.service else {
            continue;
        };
        let state = process.state.unwrap_or_default().to_lowercase();
        let health = process.health.unwrap_or_default().to_lowercase();
        if let Some(service) = states
            .iter_mut()
            .find(|service| service.name == service_name)
        {
            if state == "running" && health == "healthy" {
                service.state = "healthy".to_owned();
                service.detail = "运行正常".to_owned();
            } else if state == "running" {
                service.state = "starting".to_owned();
                service.detail = if health.is_empty() {
                    "正在运行".to_owned()
                } else {
                    format!("健康状态：{health}")
                };
            } else {
                service.state = "stopped".to_owned();
                service.detail = if state.is_empty() {
                    "未启动".to_owned()
                } else {
                    format!("容器状态：{state}")
                };
            }
        }
    }
    Ok(states)
}

pub fn wait_for_healthy<F, S>(
    mut read: F,
    timeout: Duration,
    mut sleep: S,
) -> LauncherResult<Vec<ServiceState>>
where
    F: FnMut() -> LauncherResult<Vec<ServiceState>>,
    S: FnMut(Duration),
{
    let started = Instant::now();
    loop {
        let states = read()?;
        if states.len() == 4 && states.iter().all(|service| service.state == "healthy") {
            return Ok(states);
        }
        if started.elapsed() >= timeout {
            return Err(LauncherError::HealthTimeout);
        }
        sleep(Duration::from_secs(2));
    }
}

pub fn wait_for_healthy_default(
    runner: &dyn CommandRunner,
    context: &DockerContext,
) -> LauncherResult<Vec<ServiceState>> {
    wait_for_healthy(
        || read_service_states(runner, context),
        Duration::from_secs(300),
        thread::sleep,
    )
}

fn parse_json_stream(value: &str) -> Vec<serde_json::Value> {
    let trimmed = value.trim();
    if trimmed.starts_with('[') {
        return serde_json::from_str(trimmed).unwrap_or_default();
    }
    trimmed
        .lines()
        .filter_map(|line| serde_json::from_str(line).ok())
        .collect()
}

pub fn safe_error_summary(stderr: &str) -> String {
    let one_line = stderr
        .lines()
        .filter(|line| !line.trim().is_empty())
        .take(3)
        .collect::<Vec<_>>()
        .join(" ");
    redact_sensitive(&one_line)
}

pub fn redact_sensitive(value: &str) -> String {
    let mut output = redact_bearer_tokens(value);
    for key in [
        "DB_PASSWORD",
        "MYSQL_ROOT_PASSWORD",
        "REDIS_PASSWORD",
        "password",
        "secret",
        "token",
        "authorization",
        "cookie",
    ] {
        output = redact_assignment(&output, key);
    }
    output = redact_phone_numbers(&output);
    output
}

fn redact_assignment(value: &str, key: &str) -> String {
    let key_lower = key.to_lowercase();
    let mut result = value.to_owned();
    let mut offset = 0;
    loop {
        let lower = result.to_lowercase();
        let Some(relative) = lower[offset..].find(&key_lower) else {
            break;
        };
        let start = offset + relative;
        let after_key = start + key.len();
        let rest = &result[after_key..];
        let Some(separator_relative) = rest.find(['=', ':']) else {
            break;
        };
        if separator_relative > 3 {
            offset = after_key;
            continue;
        }
        let value_start = after_key + separator_relative + 1;
        let value_end = result[value_start..]
            .find(|character: char| {
                character.is_whitespace() || character == ',' || character == ';'
            })
            .map(|relative| value_start + relative)
            .unwrap_or(result.len());
        result.replace_range(value_start..value_end, "[已脱敏]");
        offset = value_start + "[已脱敏]".len();
    }
    result
}

fn redact_bearer_tokens(value: &str) -> String {
    value
        .split('\n')
        .map(|line| {
            line.split_whitespace()
                .scan(false, |after_bearer, part| {
                    if *after_bearer {
                        *after_bearer = false;
                        Some("[已脱敏]".to_owned())
                    } else {
                        *after_bearer = part.eq_ignore_ascii_case("bearer");
                        Some(part.to_owned())
                    }
                })
                .collect::<Vec<_>>()
                .join(" ")
        })
        .collect::<Vec<_>>()
        .join("\n")
}

fn redact_phone_numbers(value: &str) -> String {
    value
        .split_whitespace()
        .map(|part| {
            let digits = part.chars().filter(char::is_ascii_digit).count();
            if digits == 11 && part.contains('1') {
                "[手机号已脱敏]".to_owned()
            } else {
                part.to_owned()
            }
        })
        .collect::<Vec<_>>()
        .join(" ")
}

pub fn context_for(docker: PathBuf, runtime_dir: &Path, env_file: PathBuf) -> DockerContext {
    DockerContext {
        executable: docker,
        compose_file: runtime_dir.join("docker-compose.yml"),
        env_file,
        working_dir: runtime_dir.to_path_buf(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::runner::test_support::MockRunner;
    use crate::runner::CommandOutput;
    use std::cell::Cell;

    #[test]
    fn compose_arguments_are_arrays_and_keep_paths_intact() {
        let context = context_for(
            PathBuf::from("docker"),
            Path::new("/tmp/runtime with spaces"),
            PathBuf::from("/tmp/data with spaces/.env"),
        );
        let args = context.compose_args(["up", "-d"]);
        assert_eq!(args[0], "compose");
        assert!(args.contains(&OsString::from("/tmp/data with spaces/.env")));
        assert!(args.contains(&OsString::from(
            "/tmp/runtime with spaces/docker-compose.yml"
        )));
        assert!(!args
            .iter()
            .any(|arg| arg.to_string_lossy().contains("sh -c")));
    }

    #[test]
    fn docker_missing_is_actionable() {
        let runner = MockRunner::with_outputs(false, vec![]);
        assert!(matches!(
            detect_docker(&runner),
            Err(LauncherError::DockerMissing)
        ));
    }

    #[test]
    fn daemon_not_running_is_actionable() {
        let runner = MockRunner::with_outputs(
            true,
            vec![CommandOutput {
                success: false,
                stderr: "cannot connect".to_owned(),
                ..Default::default()
            }],
        );
        assert!(matches!(
            detect_docker(&runner),
            Err(LauncherError::DockerDaemonUnavailable)
        ));
    }

    #[test]
    fn health_timeout_does_not_require_docker() {
        let calls = Cell::new(0);
        let result = wait_for_healthy(
            || {
                calls.set(calls.get() + 1);
                Ok(default_service_states("starting", "等待"))
            },
            Duration::ZERO,
            |_| {},
        );
        assert!(matches!(result, Err(LauncherError::HealthTimeout)));
        assert_eq!(calls.get(), 1);
    }

    #[test]
    fn logs_are_redacted() {
        let input =
            "DB_PASSWORD=top-secret DB_PASSWORD=second token:abc Bearer ey-secret 13800138000";
        let output = redact_sensitive(input);
        assert!(!output.contains("top-secret"));
        assert!(!output.contains("second"));
        assert!(!output.contains("abc"));
        assert!(!output.contains("ey-secret"));
        assert!(!output.contains("13800138000"));
    }
}
