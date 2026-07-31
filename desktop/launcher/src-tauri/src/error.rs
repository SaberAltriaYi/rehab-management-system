use std::io;

#[derive(Debug, thiserror::Error)]
pub enum LauncherError {
    #[error("Docker CLI 未安装。请安装 Docker Desktop，完成后重新打开启动器。")]
    DockerMissing,
    #[error("Docker Desktop 未运行或 daemon 无法连接。请启动 Docker Desktop，等待其显示 Running 后重试。")]
    DockerDaemonUnavailable,
    #[error("docker compose 不可用。请升级 Docker Desktop，并确认 Compose v2 插件已启用。")]
    ComposeUnavailable,
    #[error("端口 {0} 已被其他程序占用。请停止占用程序，或在“端口设置”中选择其他端口。")]
    PortInUse(u16),
    #[error("等待服务健康检查超时。容器仍会保留，请查看日志定位失败服务。")]
    HealthTimeout,
    #[error("运行资源缺失或损坏：{0}。请重新安装与当前版本匹配的安装包。")]
    RuntimeInvalid(String),
    #[error("服务操作失败：{0}")]
    CommandFailed(String),
    #[error("配置无效：{0}")]
    InvalidConfig(String),
    #[error("确认文字不匹配，未删除任何数据。")]
    DeleteConfirmationMismatch,
    #[error("本机已有另一个康复管理系统启动器实例。")]
    AlreadyRunning,
    #[error("文件操作失败：{0}")]
    Io(#[from] io::Error),
    #[error("配置解析失败：{0}")]
    Json(#[from] serde_json::Error),
    #[error("内部错误：{0}")]
    Internal(String),
}

pub type LauncherResult<T> = Result<T, LauncherError>;

impl serde::Serialize for LauncherError {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: serde::Serializer,
    {
        serializer.serialize_str(&self.to_string())
    }
}
