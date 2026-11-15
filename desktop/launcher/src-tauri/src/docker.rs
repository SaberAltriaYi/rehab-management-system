use crate::error::{LauncherError, LauncherResult};
use crate::model::{default_service_states, ServiceState, COMPOSE_PROJECT};
use crate::runner::{find_docker, CommandOutput, CommandRunner};
use serde::Deserialize;
use std::fs;
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

const CORE_IDENTITY_OVERRIDE_NAME: &str = "core-identity.override.yml";
const CORE_IDENTITY_OVERRIDE: &str =
    include_str!("../resources/core-identity.override.yml");

#[cfg(feature = "studio-internal")]
const CORE_C2_IMAGE_OVERRIDE_NAME: &str = "core-c2-qa-image.override.yml";
#[cfg(feature = "studio-internal")]
const CORE_C2_IMAGE_OVERRIDE: &str =
    include_str!("../resources/core-c2-qa-image.override.yml");
#[cfg(feature = "studio-internal")]
const CORE_C2_IMAGE_TAG: &str = "rehab-c2-not-deployed:f3570d7b6db4";
#[cfg(feature = "studio-internal")]
const CORE_C2_IMAGE_ID: &str =
    "sha256:b0f26de5e12d6cddd8a5fd2a0b1e7e000af6aa6e6f207da05c5ab0b798fca660";

/// Reject symlinks, altered templates and nonprivate files. The override is
/// reviewed source, never arbitrary YAML loaded from the user profile.
fn private_source_pinned_override(
    directory: &Path, filename: &str, expected: &str,
) -> LauncherResult<Option<PathBuf>> {
    let path = directory.join(filename);
    match fs::symlink_metadata(&path) {
        Ok(meta) => {
            if !meta.file_type().is_file() || meta.len() > 4096 {
                return Err(LauncherError::InvalidConfig(format!(
                    "{filename} 不是受信任的私有文件"
                )));
            }
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                if meta.permissions().mode() & 0o077 != 0 {
                    return Err(LauncherError::InvalidConfig(format!(
                        "{filename} 必须使用 0600 权限"
                    )));
                }
            }
            if fs::read(&path)? != expected.as_bytes() {
                return Err(LauncherError::InvalidConfig(format!(
                    "{filename} 与已审核源码模板不符"
                )));
            }
            Ok(Some(path))
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(error) => Err(error.into()),
    }
}

impl DockerContext {
    pub fn pinned_core_candidate(&self) -> LauncherResult<bool> {
        #[cfg(feature = "studio-internal")]
        {
            if let Some(parent) = self.env_file.parent() {
                return Ok(private_source_pinned_override(
                    parent, CORE_C2_IMAGE_OVERRIDE_NAME, CORE_C2_IMAGE_OVERRIDE,
                )?.is_some());
            }
        }
        Ok(false)
    }

    pub fn compose_args<I, S>(&self, trailing: I) -> LauncherResult<Vec<OsString>>
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
        // Identity and image approvals are independent; the image approval
        // must never suppress the identity check, alter ports or add secrets.
        if let Some(parent) = self.env_file.parent() {
            if let Some(override_file) = private_source_pinned_override(
                parent, CORE_IDENTITY_OVERRIDE_NAME, CORE_IDENTITY_OVERRIDE,
            )? {
                args.push(OsString::from("--file"));
                args.push(override_file.into_os_string());
            }
            #[cfg(feature = "studio-internal")]
            if let Some(override_file) = private_source_pinned_override(
                parent, CORE_C2_IMAGE_OVERRIDE_NAME, CORE_C2_IMAGE_OVERRIDE,
            )? {
                    args.push(OsString::from("--file"));
                    args.push(override_file.into_os_string());
            }
        }
        args.extend(trailing.into_iter().map(Into::into));
        Ok(args)
    }
}

/// Pin the local immutable image digest before invoking any Compose command.
/// Never pull a replacement tag, build the older bundled JAR, or trust a tag
/// alone when handling an explicitly approved internal QA upgrade.
pub fn check_local_pinned_core_image(
    runner: &dyn CommandRunner, context: &DockerContext,
) -> LauncherResult<bool> {
    if !context.pinned_core_candidate()? { return Ok(false); }
    #[cfg(feature = "studio-internal")]
    {
        let output = runner.run(&context.executable, &[
            OsString::from("image"), OsString::from("inspect"),
            OsString::from(CORE_C2_IMAGE_TAG), OsString::from("--format"),
            OsString::from("{{.Id}}"),
        ], None)?;
        if !output.success || output.stdout.trim() != CORE_C2_IMAGE_ID {
            return Err(LauncherError::InvalidConfig(
                "Core QA 镜像缺失或摘要不符：禁止使用旧 JAR 重建".into(),
            ));
        }
        return Ok(true);
    }
    #[allow(unreachable_code)]
    Ok(false)
}

/// Check the image actually instantiated for Core, not just its configured tag.
pub fn verify_running_core_candidate(
    runner: &dyn CommandRunner, context: &DockerContext,
) -> LauncherResult<()> {
    if !context.pinned_core_candidate()? { return Ok(()); }
    #[cfg(feature = "studio-internal")]
    {
        let name = format!("{COMPOSE_PROJECT}-server-1");
        let output = runner.run(&context.executable, &[
            OsString::from("inspect"), OsString::from(name),
            OsString::from("--format"), OsString::from("{{.Image}}"),
        ], None)?;
        if !output.success || output.stdout.trim() != CORE_C2_IMAGE_ID {
            return Err(LauncherError::InvalidConfig(
                "运行中 Core 镜像并非已验证 D1 候选，拒绝报告成功".into(),
            ));
        }
    }
    Ok(())
}

/// Without the trusted override, a Launcher Start would undo the D1 update.
/// Refuse that destructive reconciliation instead of silently downgrading.
pub fn reject_unpinned_core_downgrade(
    runner: &dyn CommandRunner, context: &DockerContext,
) -> LauncherResult<()> {
    #[cfg(feature = "studio-internal")]
    if !context.pinned_core_candidate()? {
        let name = format!("{COMPOSE_PROJECT}-server-1");
        let output = runner.run(&context.executable, &[
            OsString::from("inspect"), OsString::from(name),
            OsString::from("--format"), OsString::from("{{.Image}}"),
        ], None)?;
        if output.success && output.stdout.trim() == CORE_C2_IMAGE_ID {
            return Err(LauncherError::InvalidConfig(
                "已运行新 Core，但缺少受审核的私有镜像钉住配置；拒绝重建旧版".into(),
            ));
        }
    }
    #[cfg(not(feature = "studio-internal"))]
    let _ = (runner, context);
    Ok(())
}

/// Reload an already-running Nginx after Compose recreates only Core. Nginx
/// normally resolves the `server` hostname once when its config is loaded.
pub fn reload_nginx_after_pinned_core_start(
    runner: &dyn CommandRunner, context: &DockerContext,
) -> LauncherResult<()> {
    if !context.pinned_core_candidate()? { return Ok(()); }
    let admin = format!("{COMPOSE_PROJECT}-admin-1");
    let check_args = ["exec".into(), admin.clone().into(), "nginx".into(), "-t".into()];
    let reload_args = ["exec".into(), admin.into(), "nginx".into(),
                       "-s".into(), "reload".into()];
    for args in [check_args.as_slice(), reload_args.as_slice()] {
        let output = runner.run(&context.executable, args, None)?;
        if !output.success {
            return Err(LauncherError::InvalidConfig(
                "Core 已启动，但 Nginx 反向代理重载失败，拒绝宣告启动完成".into(),
            ));
        }
    }
    Ok(())
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
    let args = context.compose_args(trailing.iter().copied())?;
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
    // Compose emits normal image-build progress before the actionable error.
    let lines: Vec<_> = stderr.lines().map(str::trim)
        .filter(|line| !line.is_empty()).collect();
    let failure = lines.iter().rev().find(|line| {
        let lower = line.to_ascii_lowercase();
        lower.contains("error") || lower.contains("failed") ||
            lower.contains("unhealthy") || lower.contains("exited")
    });
    let text = match failure {
        Some(error) => (*error).to_owned(),
        None => lines.iter().rev().take(3).rev().copied().collect::<Vec<_>>().join(" ")
    };
    redact_sensitive(&text.chars().take(800).collect::<String>())
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
    use tempfile::tempdir;

    #[test]
    fn compose_arguments_are_arrays_and_keep_paths_intact() {
        let temp = tempdir().unwrap();
        let runtime_dir = temp.path().join("runtime with spaces");
        let env_file = temp.path().join("data with spaces").join(".env");
        let context = context_for(PathBuf::from("docker"), &runtime_dir, env_file.clone());
        let args = context.compose_args(["up", "-d"]).unwrap();
        assert_eq!(args[0], "compose");
        assert!(args.contains(&env_file.into_os_string()));
        assert!(args.contains(&runtime_dir.join("docker-compose.yml").into_os_string()));
        assert!(!args
            .iter()
            .any(|arg| arg.to_string_lossy().contains("sh -c")));
    }

    #[test]
    fn identity_override_requires_private_byte_exact_template_and_never_changes_default() {
        let temp = tempdir().unwrap();
        let runtime = temp.path().join("runtime");
        let config = temp.path().join("config");
        fs::create_dir(&runtime).unwrap();
        fs::create_dir(&config).unwrap();
        let context = context_for(PathBuf::from("docker"), &runtime, config.join(".env"));
        let plain = context.compose_args(["config"]).unwrap();
        assert_eq!(plain.iter().filter(|x| *x == "--file").count(), 1);
        let override_path = config.join(CORE_IDENTITY_OVERRIDE_NAME);
        fs::write(&override_path, CORE_IDENTITY_OVERRIDE).unwrap();
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&override_path, fs::Permissions::from_mode(0o600)).unwrap();
        }
        let approved = context.compose_args(["config"]).unwrap();
        assert_eq!(approved.iter().filter(|x| *x == "--file").count(), 2);
        assert!(approved.contains(&override_path.clone().into_os_string()));
        fs::write(&override_path, "services:\n  server:\n    privileged: true\n").unwrap();
        assert!(context.compose_args(["config"]).is_err());
        fs::write(&override_path, CORE_IDENTITY_OVERRIDE).unwrap();
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&override_path, fs::Permissions::from_mode(0o644)).unwrap();
            assert!(context.compose_args(["config"]).is_err());
            fs::remove_file(&override_path).unwrap();
            std::os::unix::fs::symlink("/etc/passwd", &override_path).unwrap();
            assert!(context.compose_args(["config"]).is_err());
        }
    }

    #[cfg(feature = "studio-internal")]
    #[test]
    fn image_pin_requires_exact_private_template_and_is_separate_from_identity() {
        let temp = tempdir().unwrap();
        let config = temp.path().join("config");
        fs::create_dir(&config).unwrap();
        let context = context_for(PathBuf::from("docker"), temp.path(), config.join(".env"));
        assert!(!context.pinned_core_candidate().unwrap());
        let image_file = config.join(CORE_C2_IMAGE_OVERRIDE_NAME);
        fs::write(&image_file, CORE_C2_IMAGE_OVERRIDE).unwrap();
        #[cfg(unix)] {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&image_file, fs::Permissions::from_mode(0o600)).unwrap();
        }
        assert!(context.pinned_core_candidate().unwrap());
        assert_eq!(context.compose_args(["config"]).unwrap().iter()
            .filter(|value| *value == "--file").count(), 2);
        let identity = config.join(CORE_IDENTITY_OVERRIDE_NAME);
        fs::write(&identity, CORE_IDENTITY_OVERRIDE).unwrap();
        #[cfg(unix)] {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&identity, fs::Permissions::from_mode(0o600)).unwrap();
        }
        let args = context.compose_args(["config"]).unwrap();
        assert_eq!(args.iter().filter(|value| *value == "--file").count(), 3);
        assert!(args.contains(&identity.into_os_string()));
        assert!(args.contains(&image_file.clone().into_os_string()));
        fs::write(&image_file, "services:\n  server:\n    privileged: true\n").unwrap();
        assert!(context.compose_args(["config"]).is_err());
        fs::write(&image_file, CORE_C2_IMAGE_OVERRIDE).unwrap();
        #[cfg(unix)] {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&image_file, fs::Permissions::from_mode(0o644)).unwrap();
            assert!(context.pinned_core_candidate().is_err());
            fs::remove_file(&image_file).unwrap();
            std::os::unix::fs::symlink("/etc/passwd", &image_file).unwrap();
            assert!(context.pinned_core_candidate().is_err());
        }
    }

    #[cfg(feature = "studio-internal")]
    #[test]
    fn candidate_digest_is_checked_before_compose_and_after_start() {
        let temp = tempdir().unwrap();
        let image_file = temp.path().join(CORE_C2_IMAGE_OVERRIDE_NAME);
        fs::write(&image_file, CORE_C2_IMAGE_OVERRIDE).unwrap();
        #[cfg(unix)] {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&image_file, fs::Permissions::from_mode(0o600)).unwrap();
        }
        let context = context_for(PathBuf::from("docker"), temp.path(), temp.path().join(".env"));
        let good = CommandOutput { success: true,
            stdout: format!("{CORE_C2_IMAGE_ID}\n"), ..Default::default() };
        let runner = MockRunner::with_outputs(true, vec![good.clone(), good]);
        assert!(check_local_pinned_core_image(&runner, &context).unwrap());
        verify_running_core_candidate(&runner, &context).unwrap();
        let calls = runner.calls.lock().unwrap();
        assert!(calls[0].contains(&OsString::from("image")));
        assert!(calls[1].contains(&OsString::from("inspect")));
        let bad = CommandOutput { success: true,
            stdout: "sha256:wrong".into(), ..Default::default() };
        let bad_runner = MockRunner::with_outputs(true, vec![bad.clone(), bad]);
        assert!(check_local_pinned_core_image(&bad_runner, &context).is_err());
        assert!(verify_running_core_candidate(&bad_runner, &context).is_err());
    }

    #[cfg(feature = "studio-internal")]
    #[test]
    fn missing_pin_refuses_launcher_start_when_new_core_is_running() {
        let temp = tempdir().unwrap();
        let context = context_for(PathBuf::from("docker"), temp.path(), temp.path().join(".env"));
        let new_image = CommandOutput { success: true,
            stdout: CORE_C2_IMAGE_ID.into(), ..Default::default() };
        let runner = MockRunner::with_outputs(true, vec![new_image]);
        assert!(reject_unpinned_core_downgrade(&runner, &context).is_err());
        let old_image = CommandOutput { success: true,
            stdout: "sha256:old".into(), ..Default::default() };
        let runner_old = MockRunner::with_outputs(true, vec![old_image]);
        assert!(reject_unpinned_core_downgrade(&runner_old, &context).is_ok());
    }

    #[cfg(feature = "studio-internal")]
    #[test]
    fn nginx_reload_after_pin_tests_config_first_and_fails_closed() {
        let temp = tempdir().unwrap();
        let image_file = temp.path().join(CORE_C2_IMAGE_OVERRIDE_NAME);
        fs::write(&image_file, CORE_C2_IMAGE_OVERRIDE).unwrap();
        #[cfg(unix)] {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&image_file, fs::Permissions::from_mode(0o600)).unwrap();
        }
        let context = context_for(PathBuf::from("docker"), temp.path(), temp.path().join(".env"));
        let ok = CommandOutput { success: true, ..Default::default() };
        let good = MockRunner::with_outputs(true, vec![ok.clone(), ok.clone()]);
        reload_nginx_after_pinned_core_start(&good, &context).unwrap();
        let calls = good.calls.lock().unwrap();
        assert!(calls[0].contains(&OsString::from("-t")));
        assert!(calls[1].contains(&OsString::from("reload")));
        let failed = MockRunner::with_outputs(true, vec![CommandOutput {
            success: false, ..Default::default()
        }]);
        assert!(reload_nginx_after_pinned_core_start(&failed, &context).is_err());
        assert_eq!(failed.calls.lock().unwrap().len(), 1);
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

    #[test]
    fn compose_failures_do_not_get_hidden_behind_successful_image_build_progress() {
        let output = safe_error_summary("Image server Building\nImage admin Built\nError: unhealthy password=should-not-leak");
        assert!(output.contains("Error: unhealthy"));
        assert!(!output.contains("admin Built"));
        assert!(!output.contains("should-not-leak"));
        assert!(safe_error_summary("Image server Building\nImage admin Built")
            .contains("admin Built"));
    }
}
