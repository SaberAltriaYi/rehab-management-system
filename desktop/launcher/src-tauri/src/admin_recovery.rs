//! Dedicated Owner-MFA Core admin recovery. No arbitrary Owner URL, no browser
//! session tokens, no direct SQL/password payload sent to the Owner service.
use crate::error::{LauncherError, LauncherResult};
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use ring::rand::{SecureRandom, SystemRandom};
use serde::Deserialize;
use std::{io::Write, process::{Command, Stdio}};

const REDEEM_ENDPOINT: &str =
    "https://127.0.0.1:9444/owner/v1/core-admin-recovery/redeem";

fn bad(reason: &str) -> LauncherError { LauncherError::InvalidConfig(reason.to_owned()) }

fn valid_code(value: &str) -> bool {
    value.len() == 43 && value.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_' || c == b'-')
        && URL_SAFE_NO_PAD.decode(value).is_ok_and(|bytes|
            bytes.len() == 32 && URL_SAFE_NO_PAD.encode(bytes) == value)
}

pub fn new_device_challenge() -> LauncherResult<String> {
    let mut random = [0u8; 32];
    SystemRandom::new().fill(&mut random)
        .map_err(|_| bad("无法生成安全设备挑战码"))?;
    Ok(URL_SAFE_NO_PAD.encode(random))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Redeemed {
    tenant_id: u64,
    user_id: u64,
    username: String,
    scope: String,
}

fn verify_redemption(output: &[u8]) -> LauncherResult<()> {
    let redeemed: Redeemed = serde_json::from_slice(output)
        .map_err(|_| bad("所有者恢复授权响应不符合协议"))?;
    if redeemed.tenant_id != 1 || redeemed.user_id != 1 ||
        redeemed.username != "admin" || redeemed.scope != "password-only" {
        return Err(bad("所有者授权身份与内置管理员不一致"));
    }
    Ok(())
}

pub fn redeem_owner_approval(code: &str, challenge: &str) -> LauncherResult<()> {
    if !valid_code(code) || !valid_code(challenge) {
        return Err(bad("恢复授权码或设备挑战码格式无效"));
    }
    // Only a strict verified local TLS connection can authorize this. Never
    // disable verification, select a caller-supplied CA, follow redirects,
    // expand shell input, honor a proxy or load user curl configuration.
    let curl = if cfg!(windows) { "curl.exe" }
        else if cfg!(target_os = "macos") { "/usr/bin/curl" }
        else { "curl" };
    let mut child = Command::new(curl)
        .args(["--disable", "--silent", "--show-error", "--fail", "--noproxy", "*",
            "--proto", "=https", "--tlsv1.2", "--max-redirs", "0",
            "--connect-timeout", "3", "--max-time", "8", "--max-filesize", "2048",
            "--header", "Content-Type: application/json", "--header", "Accept: application/json",
            "--request", "POST", "--data-binary", "@-", "--url", REDEEM_ENDPOINT])
        .stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::null())
        .spawn().map_err(|_| bad("缺少严格 HTTPS 客户端 curl，恢复功能不可用"))?;
    let body = serde_json::json!({"authorizationCode": code,"deviceChallenge":challenge});
    if let Some(mut stdin) = child.stdin.take() {
        stdin.write_all(&serde_json::to_vec(&body)?)
            .map_err(|_| bad("发送本地所有者授权失败"))?;
    }
    let output = child.wait_with_output()
        .map_err(|_| bad("所有者恢复授权服务不可达"))?;
    if !output.status.success() { return Err(bad("所有者拒绝恢复授权，请重新验证 MFA 并生成授权码")); }
    if output.stdout.len() > 2048 { return Err(bad("恢复授权响应长度异常")); }
    verify_redemption(&output.stdout)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn challenges_have_256_bits_and_strict_canonical_encoding() {
        let first = new_device_challenge().unwrap();
        let second = new_device_challenge().unwrap();
        assert_ne!(first, second);
        assert!(valid_code(&first));
        assert!(!valid_code("bad"));
        assert!(!valid_code(&format!(" {first}")));
        assert!(!valid_code(&format!("{first}=")));
    }
    #[test]
    fn owner_redemption_cannot_escalate_to_another_tenant_or_user() {
        let valid = br#"{"tenantId":1,"userId":1,"username":"admin","scope":"password-only"}"#;
        assert!(verify_redemption(valid).is_ok());
        for data in [
            br#"{"tenantId":2,"userId":1,"username":"admin","scope":"password-only"}"#.as_slice(),
            br#"{"tenantId":1,"userId":2,"username":"admin","scope":"password-only"}"#.as_slice(),
            br#"{"tenantId":1,"userId":1,"username":"admin","scope":"create-user"}"#.as_slice(),
            br#"{"tenantId":1,"userId":1,"username":"admin","scope":"password-only","admin":true}"#.as_slice(),
        ] {
            assert!(verify_redemption(data).is_err());
        }
    }
}
