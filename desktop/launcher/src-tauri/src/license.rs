//! L3 client-side enrollment, Ed25519 verifier and persisted offline state.
//! The Java server remains the final enforcement authority. No issuer private
//! key, clinical data or customer-provided public key is ever trusted here.
use crate::config::{write_private, AppPaths};
use crate::error::{LauncherError, LauncherResult};
use base64::{engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD}, Engine as _};
use chrono::{DateTime, SecondsFormat, Utc};
use ring::{rand::SystemRandom, signature::{Ed25519KeyPair, KeyPair, UnparsedPublicKey, ED25519}};
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, fs, io::Write, path::{Path, PathBuf}, process::{Command, Stdio}};
use url::Url;
use uuid::Uuid;

pub const MAX_OFFLINE_MS: i64 = 72 * 60 * 60 * 1000;
const SKEW_MS: i64 = 5 * 60 * 1000;

fn bad(reason: &str) -> LauncherError { LauncherError::InvalidConfig(reason.to_owned()) }
fn now() -> i64 { Utc::now().timestamp_millis() }
fn exact_uuid(id: &str) -> bool {
    Uuid::parse_str(id).is_ok_and(|uuid| uuid.to_string() == id &&
        matches!(id.as_bytes()[14], b'1'..=b'8') &&
        matches!(id.as_bytes()[19], b'8' | b'9' | b'a' | b'b'))
}
fn time_ms(value: &str) -> LauncherResult<i64> {
    let dt = DateTime::parse_from_rfc3339(value)
        .map_err(|_| bad("许可证使用了不合法 UTC 时间"))?;
    let ms = dt.timestamp_millis();
    if dt.with_timezone(&Utc).to_rfc3339_opts(SecondsFormat::Millis, true) != value {
        return Err(bad("许可证必须使用标准 UTC 毫秒时间"));
    }
    Ok(ms)
}
fn valid_owner_url(value: &str) -> LauncherResult<Url> {
    let url = Url::parse(value).map_err(|_| bad("授权服务器地址无效"))?;
    if url.scheme() != "https" || url.host_str().is_none() || !url.username().is_empty()
        || url.password().is_some() || url.query().is_some() || url.fragment().is_some()
        || url.path() != "/" {
        return Err(bad("授权服务器必须是受信任的 HTTPS 站点根地址"));
    }
    Ok(url)
}

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
pub struct Issuer {
    pub kid: String,
    pub public_key_base64url: String,
}
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
struct TrustConfig {
    owner_base_url: Option<String>,
    issuers: Vec<Issuer>,
}
#[derive(Clone)]
pub struct TrustedOwner {
    endpoint: Option<String>,
    keys: HashMap<String, Vec<u8>>,
}
impl TrustedOwner {
    pub fn from_bundled_resources(resource_dir: &Path) -> LauncherResult<Self> {
        // Bundled, code-signed trust roots only. No key enrollment from online
        // /issuers or arbitrary customer-supplied activation files.
        let path = resource_dir.join("license/trusted-issuers.json");
        let bytes = fs::read(path).map_err(|_| bad("发行商可信公钥文件缺失，禁止激活"))?;
        if bytes.len() > 8192 { return Err(bad("发行商公钥配置长度无效")); }
        let config: TrustConfig = serde_json::from_slice(&bytes)?;
        let endpoint = config.owner_base_url.as_deref().map(valid_owner_url).transpose()?
            .map(|v| v.as_str().to_owned());
        let mut keys = HashMap::new();
        for issuer in config.issuers {
            if issuer.kid.is_empty() || issuer.kid.len() > 48 ||
                !issuer.kid.bytes().all(|c| c.is_ascii_alphanumeric() || b"._-".contains(&c)) {
                return Err(bad("非法发行商密钥编号"));
            }
            let key = URL_SAFE_NO_PAD.decode(&issuer.public_key_base64url)
                .map_err(|_| bad("非法的发行商公钥编码"))?;
            if key.len() != 32 || URL_SAFE_NO_PAD.encode(&key) != issuer.public_key_base64url ||
                keys.insert(issuer.kid, key).is_some() {
                return Err(bad("非法或重复的发行商公钥"));
            }
        }
        Ok(Self { endpoint, keys })
    }
    fn key(&self, kid: &str) -> LauncherResult<&[u8]> {
        self.keys.get(kid).map(Vec::as_slice)
            .ok_or_else(|| bad("发行商签名未被软件发行渠道信任"))
    }
    pub fn publisher_ready(&self) -> bool { !self.keys.is_empty() }
    pub fn java_pinned_keyring(&self) -> String {
        let mut keys: Vec<_> = self.keys.iter().map(|(k, v)|
            format!("{}={}", k, URL_SAFE_NO_PAD.encode(v))).collect();
        keys.sort();
        keys.join(",")
    }
    pub fn java_status_url(&self) -> String {
        self.endpoint("/license/v1/status").unwrap_or_default()
    }
    fn endpoint(&self, path: &str) -> LauncherResult<String> {
        if !matches!(path, "/license/v1/applications" | "/license/v1/challenges"
            | "/license/v1/leases/online" | "/license/v1/status") {
            return Err(bad("不允许调用此授权接口"));
        }
        let url = self.endpoint.as_deref().ok_or_else(|| bad("尚未配置正式 HTTPS 授权域名"))?;
        Ok(format!("{}{}", url.trim_end_matches('/'), path))
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
pub struct LeaseClaims {
    schema_version: u8,
    mode: String,
    lease_id: String,
    tenant_id: String,
    installation_id: String,
    issued_at: String,
    not_before: String,
    expires_at: String,
    entitlement_expires_at: String,
    revocation_epoch: u64,
    seat_limit: Option<u32>,
    max_installations: u32,
    modules: Vec<String>,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
pub struct StatusClaims {
    schema_version: u8,
    tenant_id: String,
    installation_id: String,
    status: String,
    revocation_epoch: u64,
    observed_at: String,
    expires_at: String,
}
fn verify_signed<T: for<'de> Deserialize<'de> + Serialize>(
    token: &str, kind: &str, owner: &TrustedOwner
) -> LauncherResult<T> {
    if token.len() > 8192 { return Err(bad("许可证文件长度超过限制")); }
    let parts: Vec<_> = token.split('.').collect();
    if parts.len() != 4 || parts[0] != kind { return Err(bad("签名协议版本无效")); }
    let key = owner.key(parts[1])?;
    let payload = URL_SAFE_NO_PAD.decode(parts[2]).map_err(|_| bad("非法签名载荷编码"))?;
    let sig = URL_SAFE_NO_PAD.decode(parts[3]).map_err(|_| bad("非法签名编码"))?;
    if payload.is_empty() || payload.len() > 4096 || sig.len() != 64 ||
        URL_SAFE_NO_PAD.encode(&payload) != parts[2] ||
        URL_SAFE_NO_PAD.encode(&sig) != parts[3] {
        return Err(bad("非规范化签名载荷"));
    }
    let signed = format!("{}.{}.{}", parts[0], parts[1], parts[2]);
    UnparsedPublicKey::new(&ED25519, key).verify(signed.as_bytes(), &sig)
        .map_err(|_| bad("所有者 Ed25519 签名校验失败"))?;
    let json = std::str::from_utf8(&payload).map_err(|_| bad("签名载荷不是 UTF-8"))?;
    let claims: T = serde_json::from_str(json).map_err(|_| bad("签名载荷字段无效"))?;
    if serde_json::to_string(&claims)? != json { return Err(bad("非规范化 JSON 许可证")); }
    Ok(claims)
}
fn check_ids(tenant: &str, installation: &str, a: &str, b: &str) -> LauncherResult<()> {
    if !exact_uuid(tenant) || !exact_uuid(installation) || tenant != a || installation != b {
        return Err(bad("租户或安装实例与所有者签名不匹配"));
    }
    Ok(())
}
fn verify_status(
    proof: &str, owner: &TrustedOwner, tenant: &str, installation: &str,
    current_ms: i64, previous_ms: i64
) -> LauncherResult<StatusClaims> {
    let value: StatusClaims = verify_signed(proof, "RHR1", owner)?;
    check_ids(tenant, installation, &value.tenant_id, &value.installation_id)?;
    if value.schema_version != 1 || !matches!(value.status.as_str(),
        "ACTIVE" | "SUSPENDED" | "REVOKED" | "EXPIRED" | "REPLACED") {
        return Err(bad("签名授权状态无效"));
    }
    let observed = time_ms(&value.observed_at)?;
    let expires = time_ms(&value.expires_at)?;
    if observed > current_ms.saturating_add(SKEW_MS) || current_ms >= expires ||
        expires <= observed || expires - observed > MAX_OFFLINE_MS ||
        current_ms.saturating_add(SKEW_MS) < previous_ms {
        return Err(bad("签名状态已过期、超出72小时或时钟回拨"));
    }
    Ok(value)
}
fn verify_lease(
    token: &str, owner: &TrustedOwner, tenant: &str, installation: &str,
    current_ms: i64, epoch: u64, previous_ms: i64
) -> LauncherResult<LeaseClaims> {
    let v: LeaseClaims = verify_signed(token, "RHL1", owner)?;
    check_ids(tenant, installation, &v.tenant_id, &v.installation_id)?;
    if v.schema_version != 1 || !matches!(v.mode.as_str(), "online" | "offline") ||
        !exact_uuid(&v.lease_id) || v.revocation_epoch < epoch ||
        v.seat_limit.is_some_and(|s| s == 0 || s > i32::MAX as u32) ||
        v.max_installations == 0 || v.max_installations > 100_000 ||
        v.modules.is_empty() || v.modules.len() > 64 ||
        v.modules.iter().any(|m| m.is_empty() || m.len() > 32 ||
            !m.as_bytes()[0].is_ascii_lowercase() ||
            !m.bytes().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == b'_' || c == b'-')) ||
        v.modules.windows(2).any(|w| w[0] >= w[1]) {
        return Err(bad("许可证授权字段、配额或撤销代际无效"));
    }
    let issued = time_ms(&v.issued_at)?;
    let not_before = time_ms(&v.not_before)?;
    let expires = time_ms(&v.expires_at)?;
    let entitlement_expires = time_ms(&v.entitlement_expires_at)?;
    if current_ms.saturating_add(SKEW_MS) < issued || current_ms < not_before ||
        current_ms >= expires || issued > not_before || expires <= not_before ||
        expires <= issued || expires - issued > MAX_OFFLINE_MS ||
        expires > entitlement_expires || current_ms.saturating_add(SKEW_MS) < previous_ms {
        return Err(bad("许可证未生效、超期、合同已到期或系统时间回拨"));
    }
    Ok(v)
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
pub struct LocalInstallation {
    pub tenant_id: String,
    pub installation_id: String,
    pub organization: String,
    pub application_id: Option<String>,
    pub replacement_of: Option<String>,
}
#[derive(Clone, Debug, Serialize, Deserialize, Default)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
struct ActivationRecord {
    token: Option<String>,
    status_proof: Option<String>,
    trusted_time_ms: i64,
    revocation_epoch: u64,
    last_online_at: Option<String>,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all="camelCase")]
pub struct LicenseOverview {
    pub status: String,
    pub detail: String,
    pub tenant_id: Option<String>,
    pub installation_id: Option<String>,
    pub organization: Option<String>,
    pub application_id: Option<String>,
    pub expiry: Option<String>,
    pub seat_limit: Option<u32>,
    pub modules: Vec<String>,
    pub pending_replacement: bool,
    pub publisher_configured: bool,
    pub local_verification_only: bool,
}
#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
pub struct SignedActivationFile {
    pub schema_version: u8,
    pub tenant_id: String,
    pub installation_id: String,
    pub token: String,
    pub status_proof: String,
}
#[derive(Debug, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
struct IssuedLease {
    token: String,
    status_proof: String,
    #[allow(dead_code)]
    expires_at: String,
    #[allow(dead_code)]
    lease_id: String,
}
#[derive(Debug, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
struct ApplicationResponse {
    application_id: String,
    status: String,
}
#[derive(Debug, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
struct ChallengeResponse { nonce: String, expires_at: String }
#[derive(Debug, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
struct StatusResponse { status_proof: String }

pub struct LicenseManager {
    paths: AppPaths,
    owner: Option<TrustedOwner>,
}
impl LicenseManager {
    pub fn new(paths: &AppPaths, resource_dir: &Path) -> Self {
        Self {
            paths: paths.clone(),
            owner: TrustedOwner::from_bundled_resources(resource_dir).ok()
        }
    }
    fn owner(&self) -> LauncherResult<&TrustedOwner> {
        self.owner.as_ref().filter(|o| o.publisher_ready())
            .ok_or_else(|| bad("正式发行包尚未预置可用的所有者公钥，请联系软件所有者"))
    }
    fn identity_path(&self) -> PathBuf { self.paths.config_dir.join("license/installation.json") }
    fn record_path(&self) -> PathBuf { self.paths.config_dir.join("license/record.json") }
    fn private_key_path(&self) -> PathBuf { self.paths.secrets_dir.join("license/installation.pk8") }
    fn identity(&self) -> LauncherResult<LocalInstallation> {
        let bytes = fs::read(self.identity_path())?;
        if bytes.len() > 2048 { return Err(bad("安装身份文件异常")); }
        let id: LocalInstallation = serde_json::from_slice(&bytes)?;
        if !exact_uuid(&id.tenant_id) || !exact_uuid(&id.installation_id) {
            return Err(bad("本机安装身份已损坏"));
        }
        Ok(id)
    }
    fn record(&self) -> LauncherResult<ActivationRecord> {
        if !self.record_path().exists() { return Ok(ActivationRecord::default()); }
        let bytes = fs::read(self.record_path())?;
        if bytes.len() > 20000 { return Err(bad("本机许可证记录过大")); }
        serde_json::from_slice(&bytes).map_err(LauncherError::from)
    }
    fn pair(&self) -> LauncherResult<Ed25519KeyPair> {
        let bytes = fs::read(self.private_key_path())?;
        Ed25519KeyPair::from_pkcs8(&bytes)
            .map_err(|_| bad("安装私钥不存在或已损坏，禁止自动更换设备密钥"))
    }
    pub fn is_active(&self) -> bool { self.verified().is_ok() }
    fn verified(&self) -> LauncherResult<(LeaseClaims, StatusClaims, LocalInstallation)> {
        let owner = self.owner()?;
        let identity = self.identity()?;
        let record = self.record()?;
        let t = now();
        let status = verify_status(record.status_proof.as_deref().unwrap_or(""), owner,
            &identity.tenant_id, &identity.installation_id, t, record.trusted_time_ms)?;
        if status.revocation_epoch < record.revocation_epoch || status.status != "ACTIVE" {
            return Err(bad("工作室授权已被暂停、撤销、替换或状态已过期"));
        }
        let lease = verify_lease(record.token.as_deref().unwrap_or(""), owner,
            &identity.tenant_id, &identity.installation_id, t,
            status.revocation_epoch.max(record.revocation_epoch), record.trusted_time_ms)?;
        Ok((lease, status, identity))
    }
    pub fn overview(&self) -> LicenseOverview {
        let publisher_configured = self.owner.as_ref().is_some_and(TrustedOwner::publisher_ready);
        let mut result = LicenseOverview {
            status: "UNLICENSED".into(), detail: "需向软件所有者申请许可".into(),
            tenant_id: None, installation_id: None, organization: None,
            application_id: None, expiry: None, seat_limit: None, modules: vec![],
            pending_replacement: false, publisher_configured, local_verification_only: true,
        };
        let id = match self.identity() { Ok(id) => id, Err(_) => return result };
        result.tenant_id = Some(id.tenant_id.clone());
        result.installation_id = Some(id.installation_id.clone());
        result.organization = Some(id.organization.clone());
        result.application_id = id.application_id.clone();
        result.pending_replacement = id.replacement_of.is_some();
        result.status = "PENDING".into();
        result.detail = if id.replacement_of.is_some() {
            "等待软件所有者审核换机请求".into()
        } else { "等待软件所有者审核申请".into() };
        if let Ok(record) = self.record() {
            if let Some(token) = &record.token {
                if let Some(owner) = self.owner.as_ref() {
                    if let Ok(claims) = verify_signed::<LeaseClaims>(token, "RHL1", owner) {
                        result.expiry = Some(claims.expires_at);
                        result.seat_limit = claims.seat_limit;
                        result.modules = claims.modules;
                    }
                }
                result.status = "INVALID".into();
                result.detail = "许可证无效或已过期；业务访问不应放行".into();
                if let (Some(status_token), Some(owner)) = (&record.status_proof, &self.owner) {
                    if let Ok(s) = verify_signed::<StatusClaims>(status_token, "RHR1", owner) {
                        if matches!(s.status.as_str(), "REVOKED" | "SUSPENDED" | "REPLACED" | "EXPIRED") {
                            result.status = s.status;
                        } else if let Some(exp) = &result.expiry {
                            if time_ms(exp).is_ok_and(|ms| now() >= ms) {
                                result.status = "EXPIRED".into();
                            }
                        }
                    }
                }
            }
        }
        if let Ok((claims, _, _)) = self.verified() {
            result.status = "ACTIVE".into();
            result.expiry = Some(claims.expires_at);
            result.seat_limit = claims.seat_limit;
            result.modules = claims.modules;
            result.detail = "签名许可已在本机校验；需同时完成 Java 服务端授权同步".into();
            if claims.mode == "offline" {
                result.status = "GRACE".into();
                result.detail = "所有者离线签名许可证有效，最迟在指定截止时间到期".into();
            }
            if let Ok(record) = self.record() {
                if let Some(at) = record.last_online_at {
                    if time_ms(&at).is_ok_and(|t| now() - t > 5 * 60 * 1000) {
                        result.status = "GRACE".into();
                        result.detail = "授权中心暂未刷新，凭先前批准的短期许可离线使用至截止时间".into();
                    }
                }
            }
        }
        result
    }
    fn store_record(&self, record: &ActivationRecord) -> LauncherResult<()> {
        private_atomic(&self.record_path(), &serde_json::to_vec(record)?)
    }
    fn store_identity(&self, id: &LocalInstallation) -> LauncherResult<()> {
        private_atomic(&self.identity_path(), &serde_json::to_vec(id)?)
    }
    fn provision(&self, tenant: String, organization: String, replacement_of: Option<String>)
        -> LauncherResult<LocalInstallation> {
        self.owner()?;
        if self.identity_path().exists() || self.private_key_path().exists() {
            return Err(bad("安装身份已存在，禁止自行重建或重置设备授权"));
        }
        if !exact_uuid(&tenant) || organization.trim().chars().count() < 2 ||
            organization.chars().count() > 120 || organization.chars().any(char::is_control) {
            return Err(bad("租户编号或工作室名称无效"));
        }
        let rng = SystemRandom::new();
        let pkcs8 = Ed25519KeyPair::generate_pkcs8(&rng)
            .map_err(|_| bad("安全安装密钥生成失败"))?;
        let id = LocalInstallation {
            tenant_id: tenant, installation_id: Uuid::new_v4().to_string(),
            organization: organization.trim().into(), application_id: None, replacement_of
        };
        private_atomic(&self.private_key_path(), pkcs8.as_ref())?;
        self.store_identity(&id)?;
        Ok(id)
    }
    pub fn begin_application(&self, organization: String) -> LauncherResult<LocalInstallation> {
        // An incomplete publisher bundle must not create an orphan identity
        // that cannot subsequently be submitted to its approval authority.
        self.owner()?.endpoint("/license/v1/applications")?;
        self.provision(Uuid::new_v4().to_string(), organization, None)
    }
    pub fn prepare_replacement(&self, tenant: String, previous: String, organization: String)
        -> LauncherResult<String> {
        if !exact_uuid(&previous) { return Err(bad("原安装实例编号无效")); }
        let id = self.provision(tenant, organization, Some(previous.clone()))?;
        let request = serde_json::json!({
            "tenantId": id.tenant_id,
            "oldInstallationId": previous,
            "newInstallationId": id.installation_id,
            "newPublicKeyPem": self.public_key_pem()?,
            "proof": self.sign_enrollment()?
        });
        serde_json::to_string_pretty(&request).map_err(LauncherError::from)
    }
    fn public_key_pem(&self) -> LauncherResult<String> {
        const SPKI: [u8; 12] = [0x30, 0x2a, 0x30, 0x05, 0x06, 0x03,
            0x2b, 0x65, 0x70, 0x03, 0x21, 0x00];
        let mut der = SPKI.to_vec();
        der.extend_from_slice(self.pair()?.public_key().as_ref());
        Ok(format!("-----BEGIN PUBLIC KEY-----\n{}\n-----END PUBLIC KEY-----\n",
            STANDARD.encode(der)))
    }
    fn sign_enrollment(&self) -> LauncherResult<String> {
        let id = self.identity()?;
        Ok(URL_SAFE_NO_PAD.encode(self.pair()?.sign(
            format!("RHL1-ENROLL.{}.{}", id.tenant_id, id.installation_id).as_bytes()).as_ref()))
    }
    fn post<B: Serialize, R: for<'de> Deserialize<'de>>(
        &self, path: &str, request: &B
    ) -> LauncherResult<R> {
        let endpoint = self.owner()?.endpoint(path)?;
        // Avoid any unverified TLS stack or executable shell interpolation.
        // The bundled owner URL is HTTPS and curl validates the system CA.
        // On Windows use the OS-provided curl.exe; no third-party ASR/video API.
        let executable = if cfg!(windows) { "curl.exe" } else { "curl" };
        let mut child = Command::new(executable)
            // --disable must be the first argument: ~/.curlrc could
            // otherwise silently inject --insecure or follow redirects.
            .args(["--disable", "--silent", "--show-error", "--fail", "--proto", "=https",
                "--tlsv1.2",
                "--max-redirs", "0", "--connect-timeout", "3", "--max-time", "8",
                "--max-filesize", "32768", "--header", "Content-Type: application/json",
                "--header", "Accept: application/json", "--request", "POST",
                "--data-binary", "@-", "--url", &endpoint])
            .stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::null())
            .spawn().map_err(|_| bad("缺少 HTTPS 客户端 curl，不能连接所有者"))?;
        let body = serde_json::to_vec(request)?;
        if body.len() > 16384 { return Err(bad("授权请求超出长度限制")); }
        if let Some(mut stdin) = child.stdin.take() {
            stdin.write_all(&body).map_err(|_| bad("发送授权申请失败"))?;
        }
        let output = child.wait_with_output()
            .map_err(|_| bad("授权中心网络请求失败"))?;
        if !output.status.success() {
            return Err(bad("授权中心拒绝请求；请检查审批状态或联系所有者"));
        }
        if output.stdout.len() > 32768 { return Err(bad("授权中心响应长度过大")); }
        serde_json::from_slice(&output.stdout).map_err(|_| bad("授权中心返回的数据格式无效"))
    }
    pub fn submit_application(&self) -> LauncherResult<String> {
        let mut id = self.identity()?;
        if id.replacement_of.is_some() { return Err(bad("换机必须由软件所有者人工审批")); }
        if let Some(existing) = id.application_id { return Ok(existing); }
        let response: ApplicationResponse = self.post("/license/v1/applications", &serde_json::json!({
            "tenantId": id.tenant_id, "installationId": id.installation_id,
            "installationPublicKeyPem": self.public_key_pem()?,
            "possessionSignature": self.sign_enrollment()?,
            "organization": id.organization
        }))?;
        if response.status != "PENDING" || !exact_uuid(&response.application_id) {
            return Err(bad("授权中心未返回有效的待审批申请"));
        }
        id.application_id = Some(response.application_id.clone());
        self.store_identity(&id)?;
        Ok(response.application_id)
    }
    pub fn sync_online(&self) -> LauncherResult<LicenseOverview> {
        let id = self.identity()?;
        let c: ChallengeResponse = self.post("/license/v1/challenges", &serde_json::json!({
            "tenantId": id.tenant_id, "installationId": id.installation_id
        }))?;
        if URL_SAFE_NO_PAD.decode(&c.nonce).is_err() || c.nonce.len() != 43 ||
            time_ms(&c.expires_at)? < now() {
            return Err(bad("授权中心返回无效挑战"));
        }
        let proof = URL_SAFE_NO_PAD.encode(self.pair()?.sign(format!(
            "RHL1-RENEW.{}.{}.{}", c.nonce, id.tenant_id, id.installation_id
        ).as_bytes()).as_ref());
        let issued: IssuedLease = self.post("/license/v1/leases/online", &serde_json::json!({
            "tenantId": id.tenant_id, "installationId": id.installation_id,
            "nonce": c.nonce, "possessionSignature": proof
        }))?;
        self.accept_signed(&issued.token, &issued.status_proof, true)?;
        Ok(self.overview())
    }
    pub fn sync_status(&self) -> LauncherResult<LicenseOverview> {
        let id = self.identity()?;
        let s: StatusResponse = self.post("/license/v1/status", &serde_json::json!({
            "tenantId": id.tenant_id, "installationId": id.installation_id
        }))?;
        let mut record = self.record()?;
        let previous = record.trusted_time_ms;
        let status = verify_status(&s.status_proof, self.owner()?, &id.tenant_id,
            &id.installation_id, now(), previous)?;
        if status.revocation_epoch < record.revocation_epoch ||
            time_ms(&status.observed_at)? < record.trusted_time_ms {
            return Err(bad("授权服务器签名状态发生时间或撤销代际回退"));
        }
        record.trusted_time_ms = record.trusted_time_ms.max(time_ms(&status.observed_at)?);
        record.revocation_epoch = record.revocation_epoch.max(status.revocation_epoch);
        record.status_proof = Some(s.status_proof);
        record.last_online_at = Some(Utc::now().to_rfc3339_opts(SecondsFormat::Millis, true));
        self.store_record(&record)?;
        Ok(self.overview())
    }
    pub fn import_offline_bytes(&self, bytes: &[u8]) -> LauncherResult<LicenseOverview> {
        if bytes.len() > 20000 { return Err(bad("离线许可证文件过大")); }
        let file: SignedActivationFile = serde_json::from_slice(bytes)?;
        if file.schema_version != 1 { return Err(bad("离线许可证文件版本无效")); }
        let id = self.identity()?;
        check_ids(&id.tenant_id, &id.installation_id, &file.tenant_id, &file.installation_id)?;
        self.accept_signed(&file.token, &file.status_proof, false)?;
        Ok(self.overview())
    }
    fn accept_signed(&self, token: &str, proof: &str, online: bool) -> LauncherResult<()> {
        let id = self.identity()?;
        let old = self.record()?;
        let status = verify_status(proof, self.owner()?, &id.tenant_id, &id.installation_id,
            now(), old.trusted_time_ms)?;
        if status.status != "ACTIVE" || status.revocation_epoch < old.revocation_epoch ||
            time_ms(&status.observed_at)? < old.trusted_time_ms {
            return Err(bad("工作室未经批准或已被吊销，禁止激活"));
        }
        let lease = verify_lease(token, self.owner()?, &id.tenant_id, &id.installation_id, now(),
            status.revocation_epoch.max(old.revocation_epoch), old.trusted_time_ms)?;
        if !online && lease.mode != "offline" { return Err(bad("手动导入必须为所有者签发的离线许可证")); }
        if online && lease.mode != "online" { return Err(bad("在线续期不是在线签发的许可证")); }
        let record = ActivationRecord {
            token: Some(token.into()), status_proof: Some(proof.into()),
            trusted_time_ms: old.trusted_time_ms.max(time_ms(&status.observed_at)?)
                .max(time_ms(&lease.issued_at)?),
            revocation_epoch: status.revocation_epoch.max(old.revocation_epoch),
            last_online_at: if online { Some(Utc::now().to_rfc3339_opts(SecondsFormat::Millis, true)) }
                else { old.last_online_at }
        };
        self.store_record(&record)?;
        Ok(())
    }
    pub fn exported_handoff(&self) -> LauncherResult<Vec<u8>> {
        let (_, _, id) = self.verified()?;
        let record = self.record()?;
        let value = SignedActivationFile {
            schema_version: 1, tenant_id: id.tenant_id, installation_id: id.installation_id,
            token: record.token.ok_or_else(|| bad("许可证缺失"))?,
            status_proof: record.status_proof.ok_or_else(|| bad("状态签名缺失"))?
        };
        serde_json::to_vec(&value).map_err(LauncherError::from)
    }
    /// Strictly signed, NON-SECRET activation data delivered read-only to
    /// the Java server. Never put private installation PKCS8 into the mount.
    pub fn prepare_runtime_handoff(&self) -> LauncherResult<()> {
        let bytes = self.exported_handoff()?;
        let folder = self.paths.config_dir.join("license-runtime");
        fs::create_dir_all(&folder)?;
        #[cfg(unix)] {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&folder, fs::Permissions::from_mode(0o755))?;
        }
        let path = folder.join("activation.json");
        if fs::symlink_metadata(&path).is_ok_and(|meta| meta.file_type().is_symlink()) {
            return Err(bad("授权启动材料不能是符号链接"));
        }
        let tmp = folder.join(format!("activation-{}.tmp", Uuid::new_v4()));
        write_private(&tmp, &bytes)?;
        #[cfg(unix)] {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&tmp, fs::Permissions::from_mode(0o644))?;
        }
        fs::rename(&tmp, path)?;
        Ok(())
    }
    pub fn runtime_environment(&self) -> LauncherResult<String> {
        let owner = self.owner()?;
        let path = self.paths.config_dir.join("license-runtime");
        let host_path = path.to_string_lossy().replace('\\', "/");
        if host_path.contains('\n') || host_path.contains('\r') {
            return Err(bad("授权目录路径无效"));
        }
        Ok(format!(
            "REHAB_LICENSE_ENFORCE={}\nREHAB_LICENSE_TRUSTED_PUBLIC_KEYS={}\nREHAB_LICENSE_OWNER_STATUS_URL={}\nREHAB_LICENSE_RUNTIME_DIR={}\n",
            cfg!(feature = "license-required"), owner.java_pinned_keyring(),
            owner.java_status_url(), host_path
        ))
    }
}

fn private_atomic(path: &Path, bytes: &[u8]) -> LauncherResult<()> {
    if let Ok(meta) = fs::symlink_metadata(path) {
        if meta.file_type().is_symlink() { return Err(bad("拒绝写入符号链接许可证")); }
    }
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
        #[cfg(unix)] {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(parent, fs::Permissions::from_mode(0o700))?;
        }
    }
    let temp = path.with_extension(format!("tmp-{}", Uuid::new_v4()));
    write_private(&temp, bytes)?;
    if let Err(error) = fs::rename(&temp, path) {
        let _ = fs::remove_file(&temp);
        return Err(LauncherError::from(error));
    }
    Ok(())
}

#[cfg(test)]
#[path = "license_tests.rs"]
mod tests;
