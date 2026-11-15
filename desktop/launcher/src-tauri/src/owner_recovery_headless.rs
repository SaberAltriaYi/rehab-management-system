//! Single-operation local recovery process. Not an HTTP server or shell tool.
//! It reuses the same signed-off LauncherService transactional recovery path.
use crate::{config::AppPaths, service::LauncherService};
use std::{fs, io::{self, BufRead, Read, Write}, path::Path};
use serde::Deserialize;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct RecoveryRequest {
    authorization_code: String,
    new_password: String,
}

fn verified_runtime() -> Result<(AppPaths, std::path::PathBuf), ()> {
    let paths = AppPaths::platform_default().map_err(|_| ())?;
    if !paths.env_path().is_file() || !paths.settings_path().is_file() ||
        !paths.version_path().is_file() ||
        !paths.secrets_dir.join("backup.passphrase").is_file() {
        return Err(());
    }
    // This helper never selects a caller-specified data directory, URL,
    // username, tenant ID or runtime resource path.
    let resources = Path::new("/Applications/康复管理系统内部版.app/Contents/Resources");
    if !resources.is_dir() { return Err(()); }
    let canonical = fs::canonicalize(resources).map_err(|_| ())?;
    if !canonical.starts_with("/Applications/康复管理系统内部版.app/Contents/Resources") {
        return Err(());
    }
    Ok((paths, canonical))
}

fn valid_request(req: &RecoveryRequest) -> bool {
    req.authorization_code.len() == 43 &&
        req.authorization_code.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_' || c == b'-') &&
        (12..=16).contains(&req.new_password.len())
}

pub fn run() -> Result<(), ()> {
    let (paths, resources) = verified_runtime()?;
    // An exclusive instance lock prevents a simultaneous desktop recovery
    // and avoids overlapping encrypted backup / password update operations.
    let mut service = LauncherService::new(paths, resources).map_err(|_| ())?;
    let challenge = service.begin_admin_recovery().map_err(|_| ())?;
    println!("{}", serde_json::json!({"type": "challenge", "challenge":challenge}));
    io::stdout().flush().map_err(|_| ())?;

    let mut line = String::new();
    // Max 1 KB stdin payload, no line echo, no log. One request per process.
    let mut stdin = io::stdin().lock().take(1024);
    stdin.read_line(&mut line).map_err(|_| ())?;
    let request: RecoveryRequest = serde_json::from_str(&line).map_err(|_| ())?;
    if !valid_request(&request) { return Err(()); }
    service.recover_admin_credentials(&request.authorization_code, &request.new_password)
        .map_err(|_| ())?;
    line.clear();
    println!("{}", serde_json::json!({"type":"result","success":true}));
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn denies_unknown_fields_and_passwords_outside_contract() {
        for invalid in [
            r#"{"authorizationCode":"test","newPassword":"synthetic1234","tenantId":2}"#,
            r#"{"authorizationCode":"test","newPassword":"short"}"#,
        ] {
            let value: Result<RecoveryRequest, _> = serde_json::from_str(invalid);
            assert!(match value { Ok(ref value) => !valid_request(value), Err(_) => true });
        }
        assert!(valid_request(&RecoveryRequest {
            authorization_code: "A".repeat(43), new_password: "synthetic1234".into()
        }));
    }
}
