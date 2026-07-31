use serde::{Deserialize, Serialize};

pub const APP_VERSION: &str = env!("CARGO_PKG_VERSION");
pub const APP_IDENTIFIER: &str = "com.saberaltriayi.rehab";
pub const COMPOSE_PROJECT: &str = "rehab-desktop";
pub const DELETE_CONFIRMATION: &str = "删除所有本地数据";
pub const VOLUME_NAMES: [&str; 4] = [
    "rehab-desktop-mysql-data",
    "rehab-desktop-redis-data",
    "rehab-desktop-rehab-data",
    "rehab-desktop-server-logs",
];

#[derive(Clone, Debug, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct LauncherSettings {
    pub bind_address: String,
    pub http_port: u16,
    pub https_port: u16,
    pub lan_enabled: bool,
}

impl Default for LauncherSettings {
    fn default() -> Self {
        Self {
            bind_address: "127.0.0.1".to_owned(),
            http_port: 8080,
            https_port: 8443,
            lan_enabled: false,
        }
    }
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServiceState {
    pub name: String,
    pub label: String,
    pub state: String,
    pub detail: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LauncherOverview {
    pub app_version: String,
    pub docker_state: String,
    pub docker_detail: String,
    pub services: Vec<ServiceState>,
    pub access_url: String,
    pub data_directory: String,
    pub last_backup_at: Option<String>,
    pub last_error: Option<String>,
    pub operation: Option<String>,
    pub ready: bool,
    pub first_login_password: Option<String>,
}

impl LauncherOverview {
    pub fn unavailable(data_directory: String, access_url: String) -> Self {
        Self {
            app_version: APP_VERSION.to_owned(),
            docker_state: "unavailable".to_owned(),
            docker_detail: "尚未检查 Docker".to_owned(),
            services: default_service_states("stopped", "未启动"),
            access_url,
            data_directory,
            last_backup_at: None,
            last_error: None,
            operation: None,
            ready: false,
            first_login_password: None,
        }
    }
}

pub fn default_service_states(state: &str, detail: &str) -> Vec<ServiceState> {
    [
        ("mysql", "MySQL"),
        ("redis", "Redis"),
        ("server", "后端服务"),
        ("admin", "管理端"),
    ]
    .iter()
    .map(|(name, label)| ServiceState {
        name: (*name).to_owned(),
        label: (*label).to_owned(),
        state: state.to_owned(),
        detail: detail.to_owned(),
    })
    .collect()
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct VersionState {
    pub application_version: String,
    pub runtime_version: String,
    pub bundle_identifier: String,
    pub compose_project: String,
    pub data_format: u32,
}

impl Default for VersionState {
    fn default() -> Self {
        Self {
            application_version: APP_VERSION.to_owned(),
            runtime_version: APP_VERSION.to_owned(),
            bundle_identifier: APP_IDENTIFIER.to_owned(),
            compose_project: COMPOSE_PROJECT.to_owned(),
            data_format: 1,
        }
    }
}
