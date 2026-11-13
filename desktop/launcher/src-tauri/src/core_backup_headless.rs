//! Bounded, non-destructive backup entrypoint for a local supervised upgrade.
//! No arbitrary paths, SQL, restore, migration, credentials or flags.
use crate::{config::AppPaths, service::LauncherService};
use std::{
    fs, path::{Path, PathBuf},
    io::{self, BufReader, Read, Write}, iter,
    process::{Command, Stdio},
    thread, time::Duration
};
use age::{Decryptor, secrecy::SecretString};
use sha2::{Sha256, Digest};

pub fn run() -> Result<(), ()> {
    let paths = AppPaths::platform_default().map_err(|_| ())?;
    // Refuse creation of a new installation: this helper is backup-only.
    if !paths.settings_path().is_file()
        || !paths.env_path().is_file()
        || !paths.version_path().is_file()
        || !paths.secrets_dir.join("backup.passphrase").is_file()
    {
        return Err(());
    }
    let resources = Path::new("/Applications/康复管理系统内部版.app/Contents/Resources");
    if !resources.is_dir() { return Err(()); }
    let resources = fs::canonicalize(resources).map_err(|_| ())?;
    let mut service = LauncherService::new(paths, resources).map_err(|_| ())?;
    service.create_backup().map_err(|_| ())?;
    Ok(())
}

fn latest_manifest(backups: &Path) -> Result<PathBuf, ()> {
    let mut files: Vec<PathBuf> = fs::read_dir(backups).map_err(|_| ())?
        .filter_map(|entry| entry.ok())
        .map(|entry| entry.path())
        .filter(|path| path.extension().is_some_and(|e| e == "json"))
        .collect();
    files.sort_by_key(|path| fs::metadata(path).and_then(|m| m.modified()).ok());
    files.pop().ok_or(())
}

fn verify_ciphertext(dir: &Path, filename: &str, expected: &str, passphrase: &str) -> Result<(), ()> {
    let name = Path::new(filename);
    if name.components().count() != 1 || !filename.starts_with("rehab-") ||
        !filename.ends_with(".age") || expected.len() != 64 ||
        !expected.bytes().all(|c| c.is_ascii_hexdigit()) {
        return Err(());
    }
    let path = dir.join(name);
    let meta = fs::symlink_metadata(&path).map_err(|_| ())?;
    if !meta.file_type().is_file() || meta.len() == 0 { return Err(()); }
    let mut hasher = Sha256::new();
    let mut hashed = BufReader::new(fs::File::open(&path).map_err(|_| ())?);
    let mut block = [0u8; 64 * 1024];
    loop {
        let count = hashed.read(&mut block).map_err(|_| ())?;
        if count == 0 { break; }
        hasher.update(&block[..count]);
    }
    if format!("{:x}", hasher.finalize()) != expected.to_ascii_lowercase() { return Err(()); }
    let encrypted = BufReader::new(fs::File::open(&path).map_err(|_| ())?);
    let decryptor = Decryptor::new_buffered(encrypted).map_err(|_| ())?;
    let identity = age::scrypt::Identity::new(SecretString::from(passphrase.to_owned()));
    let mut decrypted = decryptor.decrypt(iter::once(&identity as &dyn age::Identity))
        .map_err(|_| ())?;
    // Stream through the full authenticated ciphertext with bounded memory.
    // No cleartext data ever goes to disk, stdout, logs or patient APIs.
    let len = io::copy(&mut decrypted, &mut io::sink()).map_err(|_| ())?;
    if len == 0 { return Err(()); }
    Ok(())
}

pub fn verify_latest() -> Result<(), ()> {
    let paths = AppPaths::platform_default().map_err(|_| ())?;
    let manifest_path = latest_manifest(&paths.backups_dir)?;
    let data: serde_json::Value = serde_json::from_slice(
        &fs::read(&manifest_path).map_err(|_| ())?).map_err(|_| ())?;
    if data.get("encryption").and_then(|value| value.as_str()) != Some("age-scrypt")
        || data.get("containsPatientData").and_then(|value| value.as_bool()) != Some(true) {
        return Err(());
    }
    let passphrase = fs::read_to_string(paths.secrets_dir.join("backup.passphrase"))
        .map_err(|_| ())?;
    let directory = manifest_path.parent().ok_or(())?;
    for (file_key, checksum_key) in [
        ("database", "databaseSha256"), ("attachments", "attachmentsSha256")
    ] {
        verify_ciphertext(
            directory,
            data.get(file_key).and_then(|value| value.as_str()).ok_or(())?,
            data.get(checksum_key).and_then(|value| value.as_str()).ok_or(())?,
            passphrase.trim()
        )?;
    }
    Ok(())
}

const DOCKER: &str = "/usr/local/bin/docker";
// This constant is ONLY for a throwaway MySQL container, never a live secret.
const SYNTHETIC_ROOT_PASSWORD: &str = "synthetic-recovery-ephemeral";

struct DisposableDatabase { name: String }
impl Drop for DisposableDatabase {
    fn drop(&mut self) {
        // Stop exactly the random, single-purpose network-less tmpfs container.
        let _ = Command::new(DOCKER).args(["stop", "--time", "3", &self.name])
            .stdout(Stdio::null()).stderr(Stdio::null()).status();
    }
}

fn sql_check(container: &str, sql: &str, database: Option<&str>) -> Result<String, ()> {
    let mut cmd = Command::new(DOCKER);
    cmd.args(["exec", container, "mysql", "-uroot",
        "-psynthetic-recovery-ephemeral", "--batch", "--skip-column-names"]);
    if let Some(database) = database { cmd.arg(database); }
    let output = cmd.arg("--execute").arg(sql)
        .stdin(Stdio::null()).stderr(Stdio::null()).output().map_err(|_| ())?;
    if !output.status.success() || output.stdout.len() > 2048 { return Err(()); }
    String::from_utf8(output.stdout).map_err(|_| ())
}

fn import_sql<R: Read>(container: &str, db: &str, source: &mut R) -> Result<(), ()> {
    let mut child = Command::new(DOCKER)
        .args(["exec", "-i", container, "mysql", "-uroot",
            "-psynthetic-recovery-ephemeral", db])
        .stdin(Stdio::piped()).stdout(Stdio::null())
        .stderr(Stdio::null()).spawn().map_err(|_| ())?;
    let result = (|| {
        let mut input = child.stdin.take().ok_or(())?;
        io::copy(source, &mut input).map_err(|_| ())?;
        input.flush().map_err(|_| ())?;
        drop(input);
        if child.wait().map_err(|_| ())?.success() { Ok(()) } else { Err(()) }
    })();
    if result.is_err() { let _ = child.kill(); let _ = child.wait(); }
    result
}

fn fresh_disposable_db() -> Result<DisposableDatabase, ()> {
    if !Path::new(DOCKER).is_file() { return Err(()); }
    let name = format!("rehab-p0-backup-check-{}", uuid::Uuid::new_v4().simple());
    let password_env = format!("MYSQL_ROOT_PASSWORD={SYNTHETIC_ROOT_PASSWORD}");
    let status = Command::new(DOCKER).args([
        "run", "--rm", "-d", "--name", &name, "--network", "none",
        "--tmpfs", "/var/lib/mysql:rw,size=768m", "--pids-limit", "256",
        "-e", &password_env, "mysql:8.4.10"
    ]).stdout(Stdio::null()).stderr(Stdio::null()).status().map_err(|_| ())?;
    if !status.success() { return Err(()); }
    let disposable = DisposableDatabase { name };
    let mut ready = false;
    for _ in 0..50 {
        if sql_check(&disposable.name, "SELECT 1", None).is_ok() {
            ready = true; break;
        }
        thread::sleep(Duration::from_millis(500));
    }
    if !ready { return Err(()); }
    sql_check(&disposable.name, "CREATE DATABASE restored_core CHARACTER SET utf8mb4", None)?;
    Ok(disposable)
}

pub fn verify_restore() -> Result<(), ()> {
    // A current, fully authenticated backup must pass the no-plaintext
    // streaming decryption check before any isolated database is started.
    verify_latest()?;
    let paths = AppPaths::platform_default().map_err(|_| ())?;
    let manifest = latest_manifest(&paths.backups_dir)?;
    let data: serde_json::Value = serde_json::from_slice(
        &fs::read(&manifest).map_err(|_| ())?).map_err(|_| ())?;
    let filename = data.get("database").and_then(|v| v.as_str()).ok_or(())?;
    if !filename.starts_with("rehab-") || !filename.ends_with(".sql.age") ||
        Path::new(filename).components().count() != 1 { return Err(()); }
    let passphrase = fs::read_to_string(paths.secrets_dir.join("backup.passphrase"))
        .map_err(|_| ())?;
    let cipher = BufReader::new(fs::File::open(paths.backups_dir.join(filename))
        .map_err(|_| ())?);
    let decryptor = Decryptor::new_buffered(cipher).map_err(|_| ())?;
    let identity = age::scrypt::Identity::new(SecretString::from(passphrase.trim().to_owned()));
    let mut sql_stream = decryptor.decrypt(iter::once(&identity as &dyn age::Identity))
        .map_err(|_| ())?;

    let disposable = fresh_disposable_db()?;
    // The original Core database is only READ into the encrypted snapshot.
    // This restoration reads ciphertext and streams plaintext directly to
    // a disconnected, randomly named Docker tmpfs without a host mount.
    import_sql(&disposable.name, "restored_core", &mut sql_stream)?;
    let ledger = sql_check(&disposable.name,
        "SELECT CONCAT(MAX(version),':',COUNT(*)) FROM internal_schema_history",
        Some("restored_core"))?;
    if ledger.trim() != "036:36" { return Err(()); }

    let root = Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors().nth(3).ok_or(())?.join("sql/mysql");
    for name in [
        "rehab-saas-platform-v2.sql", "rehab-saas-onboarding-v2.sql",
        "rehab-saas-provisioning-v3.sql", "rehab-saas-entitlement-sync-v4.sql",
        "rehab-hep-core-delivery-v1.sql"
    ] {
        let mut file = BufReader::new(fs::File::open(root.join(name)).map_err(|_| ())?);
        import_sql(&disposable.name, "restored_core", &mut file)?;
    }
    let checks = sql_check(&disposable.name, r#"SELECT CONCAT(
      (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema=DATABASE() AND table_name='saas_provisioning_requests'),':',
      (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema=DATABASE() AND table_name='saas_provisioning_requests'
       AND column_name='entitlement_revision'),':',
      (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema=DATABASE()
       AND (table_name LIKE 'rehab_hep_%' OR table_name='rehab_patient_app_invitation')),':',
      (SELECT COUNT(*) FROM system_menu WHERE id BETWEEN 98900 AND 98909))"#,
      Some("restored_core"))?;
    if checks.trim() != "1:1:11:10" { return Err(()); }
    Ok(())
}
