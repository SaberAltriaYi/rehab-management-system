use crate::config::{
    check_port_available, initialize_config, load_settings, save_settings, AppPaths,
};
use crate::docker::{
    context_for, detect_docker, read_service_states, redact_sensitive, run_compose,
    wait_for_healthy_default, DockerContext,
};
use crate::error::{LauncherError, LauncherResult};
use crate::model::{
    default_service_states, LauncherOverview, LauncherSettings, DELETE_CONFIRMATION, VOLUME_NAMES,
};
use crate::runner::{CommandRunner, ProcessCommandRunner};
use crate::runtime::{ensure_runtime, locate_bundled_runtime};
use crate::single_instance::InstanceLock;
use age::secrecy::SecretString;
use age::Encryptor;
use chrono::{DateTime, Utc};
use sha2::{Digest, Sha256};
use std::ffi::OsString;
use std::fs::{self, OpenOptions};
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::Arc;

pub struct LauncherService {
    paths: AppPaths,
    resource_dir: PathBuf,
    runner: Arc<dyn CommandRunner>,
    first_login_password: Option<String>,
    last_error: Option<String>,
    operation: Option<String>,
    _instance_lock: InstanceLock,
}

impl LauncherService {
    pub fn new(paths: AppPaths, resource_dir: PathBuf) -> LauncherResult<Self> {
        Self::with_runner(paths, resource_dir, Arc::new(ProcessCommandRunner))
    }

    pub fn with_runner(
        paths: AppPaths,
        resource_dir: PathBuf,
        runner: Arc<dyn CommandRunner>,
    ) -> LauncherResult<Self> {
        let instance_lock = InstanceLock::acquire(&paths.data_dir)?;
        let first = initialize_config(&paths)?;
        Ok(Self {
            paths,
            resource_dir,
            runner,
            first_login_password: first.temporary_admin_password,
            last_error: None,
            operation: None,
            _instance_lock: instance_lock,
        })
    }

    pub fn settings(&self) -> LauncherResult<LauncherSettings> {
        load_settings(&self.paths)
    }

    pub fn save_settings(&mut self, settings: LauncherSettings) -> LauncherResult<()> {
        let existing = self.docker_context();
        if let Ok(context) = existing {
            let states = read_service_states(self.runner.as_ref(), &context).unwrap_or_default();
            if states.iter().any(|service| service.state != "stopped") {
                return Err(LauncherError::InvalidConfig(
                    "请先停止服务，再修改端口".to_owned(),
                ));
            }
        }
        check_port_available(&settings.bind_address, settings.http_port)?;
        check_port_available(&settings.bind_address, settings.https_port)?;
        save_settings(&self.paths, &settings)?;
        let secrets = read_environment_secrets(&self.paths.env_path())?;
        crate::config::write_environment(&self.paths, &settings, &secrets)?;
        Ok(())
    }

    pub fn overview(&mut self) -> LauncherOverview {
        let settings = self.settings().unwrap_or_default();
        let access_url = access_url(&settings);
        let mut overview =
            LauncherOverview::unavailable(self.paths.data_dir.display().to_string(), access_url);
        overview.first_login_password = self.first_login_password.clone();
        overview.last_error = self.last_error.clone();
        overview.operation = self.operation.clone();
        overview.last_backup_at = latest_backup_time(&self.paths.backups_dir);
        match self.docker_context() {
            Ok(context) => {
                overview.docker_state = "healthy".to_owned();
                overview.docker_detail = "Docker Desktop 与 Compose v2 可用".to_owned();
                match read_service_states(self.runner.as_ref(), &context) {
                    Ok(states) => {
                        overview.ready = states.len() == 4
                            && states.iter().all(|state| state.state == "healthy");
                        overview.services = states;
                    }
                    Err(error) => {
                        overview.services = default_service_states("error", "状态读取失败");
                        overview.last_error = Some(error.to_string());
                    }
                }
            }
            Err(error) => {
                overview.docker_state = "unavailable".to_owned();
                overview.docker_detail = error.to_string();
                overview.last_error = Some(error.to_string());
            }
        }
        overview
    }

    pub fn start(&mut self) -> LauncherResult<LauncherOverview> {
        self.operation =
            Some("正在校验运行资源并启动服务；首次运行可能需要下载固定版本镜像".to_owned());
        let result = self.start_inner();
        let result = self.finish(result);
        self.operation = None;
        result
    }

    fn start_inner(&mut self) -> LauncherResult<LauncherOverview> {
        self.install_runtime()?;
        let context = self.docker_context()?;
        let current = read_service_states(self.runner.as_ref(), &context).unwrap_or_default();
        if current.iter().all(|service| service.state == "stopped") {
            let settings = self.settings()?;
            check_port_available(&settings.bind_address, settings.http_port)?;
            check_port_available(&settings.bind_address, settings.https_port)?;
        }
        run_compose(
            self.runner.as_ref(),
            &context,
            &["up", "--detach", "--build"],
        )?;
        wait_for_healthy_default(self.runner.as_ref(), &context)?;
        Ok(self.overview())
    }

    pub fn stop(&mut self) -> LauncherResult<LauncherOverview> {
        self.operation = Some("正在停止服务（保留所有数据卷）".to_owned());
        let result = (|| {
            let context = self.docker_context()?;
            run_compose(self.runner.as_ref(), &context, &["stop"])?;
            Ok(self.overview())
        })();
        let result = self.finish(result);
        self.operation = None;
        result
    }

    pub fn restart(&mut self) -> LauncherResult<LauncherOverview> {
        self.operation = Some("正在重启服务（保留所有数据卷）".to_owned());
        let result = (|| {
            let context = self.docker_context()?;
            run_compose(self.runner.as_ref(), &context, &["restart"])?;
            wait_for_healthy_default(self.runner.as_ref(), &context)?;
            Ok(self.overview())
        })();
        let result = self.finish(result);
        self.operation = None;
        result
    }

    pub fn logs(&mut self) -> LauncherResult<String> {
        let launcher_log = fs::read_to_string(self.paths.logs_dir.join("launcher.log"))
            .unwrap_or_else(|_| "尚无启动器日志".to_owned());
        let container_log = match self.docker_context() {
            Ok(context) => run_compose(
                self.runner.as_ref(),
                &context,
                &["logs", "--no-color", "--tail", "200"],
            )
            .map(|output| output.stdout)
            .unwrap_or_else(|error| format!("无法读取容器日志：{error}")),
            Err(error) => format!("Docker 当前不可用：{error}"),
        };
        Ok(redact_log_output(&format!(
            "=== 启动器日志 ===\n{launcher_log}\n\n=== 容器日志（最近 200 行）===\n{container_log}"
        )))
    }

    pub fn diagnostics(&mut self) -> String {
        let overview = self.overview();
        let services = overview
            .services
            .iter()
            .map(|service| format!("{}: {} ({})", service.label, service.state, service.detail))
            .collect::<Vec<_>>()
            .join("\n");
        redact_sensitive(&format!(
            "康复管理系统启动器 V{}\nDocker: {} ({})\n{}\n访问地址: {}\n数据目录: {}\n最近错误: {}",
            overview.app_version,
            overview.docker_state,
            overview.docker_detail,
            services,
            overview.access_url,
            overview.data_directory,
            overview.last_error.unwrap_or_else(|| "无".to_owned())
        ))
    }

    pub fn acknowledge_initial_password(&mut self) -> LauncherResult<LauncherOverview> {
        let path = self.paths.initial_admin_password_path();
        if path.exists() {
            fs::remove_file(path)?;
        }
        self.first_login_password = None;
        Ok(self.overview())
    }

    pub fn create_backup(&mut self) -> LauncherResult<LauncherOverview> {
        self.operation = Some("正在创建本机备份".to_owned());
        let result = (|| {
            let context = self.docker_context()?;
            let states = read_service_states(self.runner.as_ref(), &context)?;
            if !states.iter().all(|service| service.state == "healthy") {
                return Err(LauncherError::CommandFailed(
                    "服务未全部健康，不能创建一致性备份".to_owned(),
                ));
            }
            fs::create_dir_all(&self.paths.backups_dir)?;
            let timestamp = Utc::now().format("%Y%m%dT%H%M%SZ").to_string();
            let sql_path = self
                .paths
                .backups_dir
                .join(format!("rehab-{timestamp}.sql"));
            let attachment_path = self
                .paths
                .backups_dir
                .join(format!("rehab-{timestamp}-attachments.tar.gz"));
            let sql_encrypted_path = encrypted_path(&sql_path);
            let attachment_encrypted_path = encrypted_path(&attachment_path);
            let backup_result = (|| {
                self.capture_command_stdout(
                    &context,
                    &[
                        "exec",
                        "--no-TTY",
                        "mysql",
                        "mysqldump",
                        "--defaults-extra-file=/run/rehab-secrets/mysql-client.cnf",
                        "--single-transaction",
                        "--routines",
                        "--triggers",
                        "ruoyi-vue-pro",
                    ],
                    &sql_path,
                )?;
                self.capture_command_stdout(
                    &context,
                    &[
                        "exec",
                        "--no-TTY",
                        "server",
                        "tar",
                        "-czf",
                        "-",
                        "-C",
                        "/app/data/rehab",
                        ".",
                    ],
                    &attachment_path,
                )?;
                let backup_passphrase =
                    fs::read_to_string(self.paths.secrets_dir.join("backup.passphrase"))?;
                let sql_encrypted = encrypt_backup_file(&sql_path, &backup_passphrase)?;
                let attachment_encrypted =
                    encrypt_backup_file(&attachment_path, &backup_passphrase)?;
                Ok((sql_encrypted, attachment_encrypted))
            })();
            let (sql_encrypted, attachment_encrypted) = match backup_result {
                Ok(paths) => paths,
                Err(error) => {
                    for path in [
                        &sql_path,
                        &attachment_path,
                        &sql_encrypted_path,
                        &attachment_encrypted_path,
                    ] {
                        let _ = fs::remove_file(path);
                    }
                    return Err(error);
                }
            };
            let manifest_path = self
                .paths
                .backups_dir
                .join(format!("rehab-{timestamp}.json"));
            let finalize_result = (|| {
                let manifest = serde_json::json!({
                    "createdAt": Utc::now().to_rfc3339(),
                    "applicationVersion": crate::model::APP_VERSION,
                    "database": sql_encrypted.file_name().and_then(|value| value.to_str()),
                    "databaseSha256": sha256_file(&sql_encrypted)?,
                    "attachments": attachment_encrypted.file_name().and_then(|value| value.to_str()),
                    "attachmentsSha256": sha256_file(&attachment_encrypted)?,
                    "containsPatientData": true,
                    "encryption": "age-scrypt",
                    "keyFile": "../secrets/backup.passphrase",
                    "storage": "请将 .age 文件复制到受控介质，并将恢复口令分开保管"
                });
                crate::config::write_private(
                    &manifest_path,
                    serde_json::to_string_pretty(&manifest)?.as_bytes(),
                )
            })();
            if let Err(error) = finalize_result {
                for path in [&sql_encrypted, &attachment_encrypted, &manifest_path] {
                    let _ = fs::remove_file(path);
                }
                return Err(error);
            }
            Ok(self.overview())
        })();
        let result = self.finish(result);
        self.operation = None;
        result
    }

    fn capture_command_stdout(
        &self,
        context: &DockerContext,
        trailing: &[&str],
        destination: &Path,
    ) -> LauncherResult<()> {
        let args = context.compose_args(trailing.iter().copied());
        let output = self.runner.run_to_file(
            &context.executable,
            &args,
            Some(&context.working_dir),
            destination,
        )?;
        if !output.success {
            return Err(LauncherError::CommandFailed(redact_sensitive(
                &output.stderr,
            )));
        }
        Ok(())
    }

    pub fn delete_all_data(&mut self, confirmation: &str) -> LauncherResult<LauncherOverview> {
        if confirmation.trim() != DELETE_CONFIRMATION {
            return Err(LauncherError::DeleteConfirmationMismatch);
        }
        let context = self.docker_context()?;
        let _ = run_compose(
            self.runner.as_ref(),
            &context,
            &["down", "--remove-orphans"],
        );
        for volume in VOLUME_NAMES {
            let output = self.runner.run(
                &context.executable,
                &[
                    OsString::from("volume"),
                    OsString::from("rm"),
                    OsString::from(volume),
                ],
                None,
            )?;
            if !output.success && !output.stderr.contains("No such volume") {
                return Err(LauncherError::CommandFailed(redact_sensitive(
                    &output.stderr,
                )));
            }
        }
        for path in [
            &self.paths.config_dir,
            &self.paths.runtime_dir,
            &self.paths.backups_dir,
            &self.paths.logs_dir,
            &self.paths.secrets_dir,
            &self.paths.version_path(),
        ] {
            remove_fixed_path(path, &self.paths.data_dir)?;
        }
        let first = initialize_config(&self.paths)?;
        self.first_login_password = first.temporary_admin_password;
        Ok(self.overview())
    }

    fn install_runtime(&self) -> LauncherResult<()> {
        let source = std::env::var_os("REHAB_DESKTOP_RUNTIME_ROOT")
            .map(PathBuf::from)
            .map(Ok)
            .unwrap_or_else(|| locate_bundled_runtime(&self.resource_dir))?;
        ensure_runtime(&source, &self.paths)
    }

    fn docker_context(&self) -> LauncherResult<DockerContext> {
        let docker = detect_docker(self.runner.as_ref())?;
        if !self.paths.runtime_dir.join("docker-compose.yml").exists() {
            self.install_runtime()?;
        }
        Ok(context_for(
            docker,
            &self.paths.runtime_dir,
            self.paths.env_path(),
        ))
    }

    fn finish(
        &mut self,
        result: LauncherResult<LauncherOverview>,
    ) -> LauncherResult<LauncherOverview> {
        match result {
            Ok(overview) => {
                self.last_error = None;
                let operation = self.operation.as_deref().unwrap_or("状态检查");
                let _ = append_launcher_log(&self.paths.logs_dir, &format!("{operation}完成"));
                Ok(overview)
            }
            Err(error) => {
                let operation = self.operation.as_deref().unwrap_or("启动器操作");
                let summary = format!(
                    "{operation}失败：{error} 日志位置：{}",
                    self.paths.logs_dir.join("launcher.log").display()
                );
                let _ = append_launcher_log(&self.paths.logs_dir, &summary);
                self.last_error = Some(summary);
                Err(error)
            }
        }
    }
}

fn encrypt_backup_file(source: &Path, passphrase: &str) -> LauncherResult<PathBuf> {
    let destination = encrypted_path(source);
    let input = fs::File::open(source)?;
    let output = fs::File::create(&destination)?;
    let encryptor =
        Encryptor::with_user_passphrase(SecretString::from(passphrase.trim().to_owned()));
    let mut writer = encryptor
        .wrap_output(output)
        .map_err(|error| LauncherError::Internal(format!("创建备份加密流失败：{error}")))?;
    let copy_result = io::copy(&mut io::BufReader::new(input), &mut writer);
    let finish_result = writer.finish();
    match (copy_result, finish_result) {
        (Ok(_), Ok(_)) => {
            fs::remove_file(source)?;
            Ok(destination)
        }
        (Err(error), _) => {
            let _ = fs::remove_file(&destination);
            Err(LauncherError::Io(error))
        }
        (_, Err(error)) => {
            let _ = fs::remove_file(&destination);
            Err(LauncherError::Internal(format!(
                "完成备份加密失败：{error}"
            )))
        }
    }
}

fn encrypted_path(source: &Path) -> PathBuf {
    PathBuf::from(format!("{}.age", source.display()))
}

fn sha256_file(path: &Path) -> LauncherResult<String> {
    Ok(format!("{:x}", Sha256::digest(fs::read(path)?)))
}

fn append_launcher_log(logs_dir: &Path, message: &str) -> LauncherResult<()> {
    fs::create_dir_all(logs_dir)?;
    let path = logs_dir.join("launcher.log");
    let mut options = OpenOptions::new();
    options.create(true).append(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options.open(&path)?;
    writeln!(
        file,
        "{} {}",
        Utc::now().to_rfc3339(),
        redact_log_output(message)
    )?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(path, fs::Permissions::from_mode(0o600))?;
    }
    Ok(())
}

fn access_url(settings: &LauncherSettings) -> String {
    format!("https://{}:{}", settings.bind_address, settings.https_port)
}

fn read_environment_secrets(path: &Path) -> LauncherResult<crate::config::RuntimeSecrets> {
    let content = fs::read_to_string(path)?;
    let get = |key: &str| {
        content
            .lines()
            .find_map(|line| line.strip_prefix(&format!("{key}=")))
            .map(str::to_owned)
            .ok_or_else(|| LauncherError::InvalidConfig(format!(".env 缺少 {key}")))
    };
    let backup_path = path
        .parent()
        .and_then(Path::parent)
        .ok_or_else(|| LauncherError::Internal("配置目录结构异常".to_owned()))?
        .join("secrets/backup.passphrase");
    Ok(crate::config::RuntimeSecrets {
        db_password: get("DB_PASSWORD")?,
        mysql_root_password: get("MYSQL_ROOT_PASSWORD")?,
        redis_password: get("REDIS_PASSWORD")?,
        backup_passphrase: fs::read_to_string(backup_path)?.trim().to_owned(),
    })
}

fn latest_backup_time(path: &Path) -> Option<String> {
    fs::read_dir(path)
        .ok()?
        .filter_map(Result::ok)
        .filter_map(|entry| entry.metadata().ok())
        .filter_map(|metadata| metadata.modified().ok())
        .max()
        .map(|time| DateTime::<Utc>::from(time).to_rfc3339())
}

fn redact_log_output(value: &str) -> String {
    value
        .lines()
        .map(|line| {
            let lower = line.to_lowercase();
            if [
                "rehabpatient",
                "rehab_patient",
                "patient_id",
                "patientid",
                "patient_name",
                "id_card",
                "idcard",
                "medical_history",
                "diagnosis",
                "mobile",
                "address",
                "/patient",
                "患者",
                "诊断",
                "病史",
                "手机号",
                "身份证",
            ]
            .iter()
            .any(|term| lower.contains(term))
            {
                "[包含潜在患者字段的日志行已隐藏]".to_owned()
            } else {
                redact_sensitive(line)
            }
        })
        .collect::<Vec<_>>()
        .join("\n")
}

fn remove_fixed_path(path: &Path, data_dir: &Path) -> LauncherResult<()> {
    if path == data_dir || !path.starts_with(data_dir) {
        return Err(LauncherError::InvalidConfig(
            "拒绝删除非应用专属路径".to_owned(),
        ));
    }
    if path.is_dir() {
        fs::remove_dir_all(path)?;
    } else if path.exists() {
        fs::remove_file(path)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn normal_operations_never_construct_down_with_volumes() {
        let stop = ["stop"];
        let restart = ["restart"];
        let update = ["up", "--detach", "--build"];
        for args in [stop.as_slice(), restart.as_slice(), update.as_slice()] {
            assert!(!args.contains(&"-v"));
            assert!(!args.contains(&"--volumes"));
        }
    }

    #[test]
    fn deletion_rejects_paths_outside_app_data() {
        let dir = tempdir().unwrap();
        let outside = tempdir().unwrap();
        assert!(remove_fixed_path(outside.path(), dir.path()).is_err());
    }

    #[test]
    fn log_redaction_hides_patient_fields_and_secrets() {
        let output =
            redact_log_output("patient_name=张三\nDB_PASSWORD=private\nordinary service message");
        assert!(!output.contains("张三"));
        assert!(!output.contains("private"));
        assert!(output.contains("ordinary service message"));
    }
}
