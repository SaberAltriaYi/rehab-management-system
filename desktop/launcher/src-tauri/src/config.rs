use crate::error::{LauncherError, LauncherResult};
use crate::model::{LauncherSettings, VersionState, APP_VERSION};
use bcrypt::{hash, DEFAULT_COST};
use directories::ProjectDirs;
use rand::distr::{Alphanumeric, SampleString};
use rcgen::{generate_simple_self_signed, CertifiedKey};
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::net::{IpAddr, TcpListener};
use std::path::{Path, PathBuf};

const PASSWORD_LENGTH: usize = 48;

#[derive(Clone, Debug)]
pub struct AppPaths {
    pub data_dir: PathBuf,
    pub config_dir: PathBuf,
    pub runtime_dir: PathBuf,
    pub backups_dir: PathBuf,
    pub logs_dir: PathBuf,
    pub secrets_dir: PathBuf,
}

impl AppPaths {
    pub fn from_data_dir(data_dir: PathBuf) -> Self {
        Self {
            config_dir: data_dir.join("config"),
            runtime_dir: data_dir.join("runtime").join(APP_VERSION),
            backups_dir: data_dir.join("backups"),
            logs_dir: data_dir.join("logs"),
            secrets_dir: data_dir.join("secrets"),
            data_dir,
        }
    }

    pub fn platform_default() -> LauncherResult<Self> {
        let project = ProjectDirs::from("com", "saberaltriayi", "rehab")
            .ok_or_else(|| LauncherError::Internal("无法解析操作系统用户数据目录".to_owned()))?;
        Ok(Self::from_data_dir(project.data_dir().to_path_buf()))
    }

    pub fn create(&self) -> LauncherResult<()> {
        for path in [
            &self.data_dir,
            &self.config_dir,
            &self.runtime_dir,
            &self.backups_dir,
            &self.logs_dir,
            &self.secrets_dir,
            &self.config_dir.join("first-start"),
            &self.config_dir.join("tls"),
        ] {
            fs::create_dir_all(path)?;
        }
        Ok(())
    }

    pub fn settings_path(&self) -> PathBuf {
        self.config_dir.join("settings.json")
    }

    pub fn env_path(&self) -> PathBuf {
        self.config_dir.join(".env")
    }

    pub fn version_path(&self) -> PathBuf {
        self.data_dir.join("version.json")
    }

    pub fn initial_admin_password_path(&self) -> PathBuf {
        self.secrets_dir.join("initial-admin-password.txt")
    }
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub struct RuntimeSecrets {
    pub db_password: String,
    pub mysql_root_password: String,
    pub redis_password: String,
    pub backup_passphrase: String,
}

#[derive(Clone, Debug)]
pub struct FirstStartState {
    pub temporary_admin_password: Option<String>,
}

pub fn random_password() -> String {
    Alphanumeric.sample_string(&mut rand::rng(), PASSWORD_LENGTH)
}

pub fn generate_distinct_secrets() -> RuntimeSecrets {
    loop {
        let values = [
            random_password(),
            random_password(),
            random_password(),
            random_password(),
        ];
        let distinct: HashSet<&String> = values.iter().collect();
        if distinct.len() == values.len() {
            return RuntimeSecrets {
                db_password: values[0].clone(),
                mysql_root_password: values[1].clone(),
                redis_password: values[2].clone(),
                backup_passphrase: values[3].clone(),
            };
        }
    }
}

pub fn load_settings(paths: &AppPaths) -> LauncherResult<LauncherSettings> {
    if !paths.settings_path().exists() {
        return Ok(LauncherSettings::default());
    }
    let settings: LauncherSettings = serde_json::from_slice(&fs::read(paths.settings_path())?)?;
    validate_settings(&settings)?;
    Ok(settings)
}

pub fn save_settings(paths: &AppPaths, settings: &LauncherSettings) -> LauncherResult<()> {
    validate_settings(settings)?;
    write_private(
        &paths.settings_path(),
        serde_json::to_string_pretty(settings)?.as_bytes(),
    )
}

pub fn validate_settings(settings: &LauncherSettings) -> LauncherResult<()> {
    if settings.lan_enabled || settings.bind_address != "127.0.0.1" {
        return Err(LauncherError::InvalidConfig(
            "V1.0 默认仅支持本机 127.0.0.1；局域网模式尚未启用".to_owned(),
        ));
    }
    if settings.http_port < 1024 || settings.https_port < 1024 {
        return Err(LauncherError::InvalidConfig(
            "端口必须在 1024 到 65535 之间".to_owned(),
        ));
    }
    if settings.http_port == settings.https_port {
        return Err(LauncherError::InvalidConfig(
            "HTTP 与 HTTPS 端口不能相同".to_owned(),
        ));
    }
    Ok(())
}

pub fn check_port_available(address: &str, port: u16) -> LauncherResult<()> {
    let ip: IpAddr = address
        .parse()
        .map_err(|_| LauncherError::InvalidConfig("绑定地址不是有效 IP".to_owned()))?;
    TcpListener::bind((ip, port))
        .map(|listener| drop(listener))
        .map_err(|_| LauncherError::PortInUse(port))
}

pub fn initialize_config(paths: &AppPaths) -> LauncherResult<FirstStartState> {
    paths.create()?;
    let settings = load_settings(paths)?;
    if !paths.settings_path().exists() {
        save_settings(paths, &settings)?;
    }
    if !paths.version_path().exists() {
        write_private(
            &paths.version_path(),
            serde_json::to_string_pretty(&VersionState::default())?.as_bytes(),
        )?;
    }

    let mut first_login = None;
    if !paths.env_path().exists() {
        let secrets = generate_distinct_secrets();
        let admin_password = random_password();
        write_environment(paths, &settings, &secrets)?;
        write_mysql_client_config(paths, &secrets.mysql_root_password)?;
        write_admin_credentials_sql(paths, &admin_password)?;
        write_private(
            &paths.initial_admin_password_path(),
            admin_password.as_bytes(),
        )?;
        write_private(
            &paths.secrets_dir.join("backup.passphrase"),
            secrets.backup_passphrase.as_bytes(),
        )?;
        first_login = Some(admin_password);
    } else if paths.initial_admin_password_path().exists() {
        first_login = Some(
            fs::read_to_string(paths.initial_admin_password_path())?
                .trim()
                .to_owned(),
        );
    }
    ensure_tls(paths)?;
    Ok(FirstStartState {
        temporary_admin_password: first_login,
    })
}

fn write_mysql_client_config(paths: &AppPaths, root_password: &str) -> LauncherResult<()> {
    let content = format!("[client]\nuser=root\npassword={root_password}\n");
    write_private(
        &paths.secrets_dir.join("mysql-client.cnf"),
        content.as_bytes(),
    )
}

pub fn write_environment(
    paths: &AppPaths,
    settings: &LauncherSettings,
    secrets: &RuntimeSecrets,
) -> LauncherResult<()> {
    let values = [
        ("TZ", "Asia/Shanghai".to_owned()),
        ("DB_NAME", "ruoyi-vue-pro".to_owned()),
        ("DB_USERNAME", "yudao".to_owned()),
        ("DB_PASSWORD", secrets.db_password.clone()),
        ("MYSQL_ROOT_PASSWORD", secrets.mysql_root_password.clone()),
        ("REDIS_PASSWORD", secrets.redis_password.clone()),
        ("BIND_ADDRESS", settings.bind_address.clone()),
        ("APP_PORT", settings.http_port.to_string()),
        ("TLS_PORT", settings.https_port.to_string()),
        (
            "TLS_CERT_PATH",
            docker_path(&paths.config_dir.join("tls/server.crt")),
        ),
        (
            "TLS_KEY_PATH",
            docker_path(&paths.config_dir.join("tls/server.key")),
        ),
        (
            "FIRST_START_SQL_PATH",
            docker_path(
                &paths
                    .config_dir
                    .join("first-start/999-runtime-credentials.sql"),
            ),
        ),
        (
            "MYSQL_CLIENT_CONFIG_PATH",
            docker_path(&paths.secrets_dir.join("mysql-client.cnf")),
        ),
    ];
    let content = values
        .iter()
        .map(|(key, value)| format!("{key}={value}"))
        .collect::<Vec<_>>()
        .join("\n");
    write_private(&paths.env_path(), format!("{content}\n").as_bytes())
}

fn write_admin_credentials_sql(paths: &AppPaths, password: &str) -> LauncherResult<()> {
    let hashed = hash(password, DEFAULT_COST)
        .map_err(|error| LauncherError::Internal(format!("管理员密码哈希失败：{error}")))?;
    let sql = format!(
        "SET NAMES utf8mb4;\n\
         UPDATE system_users\n\
         SET password = '{}', status = 0, updater = 'desktop-first-start', update_time = NOW()\n\
         WHERE tenant_id = 1 AND username = 'admin' AND deleted = b'0';\n",
        hashed.replace('\'', "''")
    );
    // MySQL 官方镜像以 mysql 用户读取 /docker-entrypoint-initdb.d。
    // 此文件只包含 bcrypt 单向哈希，不含生成的管理员明文密码。
    write_container_readable(
        &paths
            .config_dir
            .join("first-start/999-runtime-credentials.sql"),
        sql.as_bytes(),
    )
}

fn ensure_tls(paths: &AppPaths) -> LauncherResult<()> {
    let cert_path = paths.config_dir.join("tls/server.crt");
    let key_path = paths.config_dir.join("tls/server.key");
    if cert_path.exists() && key_path.exists() {
        return Ok(());
    }
    let CertifiedKey { cert, signing_key } =
        generate_simple_self_signed(vec!["localhost".to_owned(), "127.0.0.1".to_owned()])
            .map_err(|error| LauncherError::Internal(format!("生成本机 TLS 证书失败：{error}")))?;
    write_private(&cert_path, cert.pem().as_bytes())?;
    write_private(&key_path, signing_key.serialize_pem().as_bytes())
}

pub fn write_private(path: &Path, bytes: &[u8]) -> LauncherResult<()> {
    write_with_unix_mode(path, bytes, 0o600)
}

fn write_container_readable(path: &Path, bytes: &[u8]) -> LauncherResult<()> {
    write_with_unix_mode(path, bytes, 0o644)
}

fn write_with_unix_mode(path: &Path, bytes: &[u8], unix_mode: u32) -> LauncherResult<()> {
    #[cfg(not(unix))]
    let _ = unix_mode;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut options = OpenOptions::new();
    options.create(true).truncate(true).write(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(unix_mode);
    }
    let mut file = options.open(path)?;
    file.write_all(bytes)?;
    file.sync_all()?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(path, fs::Permissions::from_mode(unix_mode))?;
    }
    Ok(())
}

fn docker_path(path: &Path) -> String {
    path.to_string_lossy().replace('\\', "/")
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn generated_passwords_are_long_and_distinct() {
        let secrets = generate_distinct_secrets();
        let values = [
            secrets.db_password,
            secrets.mysql_root_password,
            secrets.redis_password,
            secrets.backup_passphrase,
        ];
        assert!(values.iter().all(|value| value.len() >= 32));
        assert_eq!(values.iter().collect::<HashSet<_>>().len(), values.len());
    }

    #[test]
    fn configuration_is_created_without_exposing_admin_password() {
        let dir = tempdir().unwrap();
        let paths = AppPaths::from_data_dir(dir.path().join("app"));
        let state = initialize_config(&paths).unwrap();
        let admin = state.temporary_admin_password.unwrap();
        let env = fs::read_to_string(paths.env_path()).unwrap();
        let sql = fs::read_to_string(
            paths
                .config_dir
                .join("first-start/999-runtime-credentials.sql"),
        )
        .unwrap();
        assert!(!env.contains(&admin));
        assert!(!sql.contains(&admin));
        assert!(sql.contains("$2"));
        let password_hash = sql
            .split("SET password = '")
            .nth(1)
            .and_then(|value| value.split('\'').next())
            .unwrap();
        assert!(bcrypt::verify(&admin, password_hash).unwrap());
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mode = fs::metadata(
                paths
                    .config_dir
                    .join("first-start/999-runtime-credentials.sql"),
            )
            .unwrap()
            .permissions()
            .mode()
                & 0o777;
            assert_eq!(mode, 0o644);
        }
    }

    #[test]
    fn explicit_data_directory_is_stable_across_versions() {
        let base = PathBuf::from("/tmp/rehab-stable-data");
        let first = AppPaths::from_data_dir(base.clone());
        let second = AppPaths::from_data_dir(base);
        assert_eq!(first.data_dir, second.data_dir);
        assert_eq!(first.runtime_dir, second.runtime_dir);
    }

    #[test]
    fn platform_data_directory_resolves_to_the_application_identifier() {
        let paths = AppPaths::platform_default().unwrap();
        assert!(paths
            .data_dir
            .to_string_lossy()
            .contains("com.saberaltriayi.rehab"));
    }

    #[test]
    fn default_uninstall_policy_has_no_delete_flag() {
        let state = VersionState::default();
        let serialized = serde_json::to_string(&state).unwrap();
        assert!(!serialized.contains("delete"));
        assert!(!serialized.contains("remove"));
    }

    #[test]
    fn port_conflict_is_reported() {
        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let port = listener.local_addr().unwrap().port();
        assert!(matches!(
            check_port_available("127.0.0.1", port),
            Err(LauncherError::PortInUse(value)) if value == port
        ));
    }

    #[test]
    fn local_only_settings_are_enforced() {
        let mut settings = LauncherSettings::default();
        settings.bind_address = "0.0.0.0".to_owned();
        assert!(validate_settings(&settings).is_err());
        settings.bind_address = "192.168.1.50".to_owned();
        settings.lan_enabled = true;
        assert!(validate_settings(&settings).is_err());
    }

    #[cfg(unix)]
    #[test]
    fn secret_files_are_owner_only() {
        use std::os::unix::fs::PermissionsExt;
        let dir = tempdir().unwrap();
        let file = dir.path().join("secret");
        write_private(&file, b"value").unwrap();
        assert_eq!(
            fs::metadata(file).unwrap().permissions().mode() & 0o777,
            0o600
        );
    }
}
