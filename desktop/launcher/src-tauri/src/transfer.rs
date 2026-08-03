use crate::error::{LauncherError, LauncherResult};
use age::secrecy::SecretString;
use age::{Decryptor, Encryptor};
use chrono::Utc;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::{self, BufReader, Read, Write};
use std::iter;
use std::path::{Path, PathBuf};

const TRANSFER_MAGIC: &[u8] = b"REHAB-TRANSFER-V1\n";
const TRANSFER_FORMAT_VERSION: u32 = 1;
const MAX_MANIFEST_BYTES: u64 = 64 * 1024;
pub const IMPORT_CONFIRMATION: &str = "覆盖导入全部数据";

#[derive(Clone, Debug, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct TransferManifest {
    pub format_version: u32,
    pub application_version: String,
    pub created_at: String,
    pub database_sha256: String,
    pub attachments_sha256: String,
    pub includes: Vec<String>,
    pub excludes: Vec<String>,
}

impl TransferManifest {
    fn new(application_version: &str, database: &Path, attachments: &Path) -> LauncherResult<Self> {
        Ok(Self {
            format_version: TRANSFER_FORMAT_VERSION,
            application_version: application_version.to_owned(),
            created_at: Utc::now().to_rfc3339(),
            database_sha256: sha256_file(database)?,
            attachments_sha256: sha256_file(attachments)?,
            includes: vec![
                "完整业务数据库（含系统用户与权限）".to_owned(),
                "康复附件与评估报告".to_owned(),
            ],
            excludes: vec![
                "目标设备数据库与 Redis 运行密码".to_owned(),
                "目标设备 TLS 私钥与证书".to_owned(),
                "目标设备端口及网络配置".to_owned(),
                "日志、缓存与本机备份".to_owned(),
            ],
        })
    }
}

pub fn validate_transfer_password(password: &str) -> LauncherResult<()> {
    let length = password.chars().count();
    if !(12..=128).contains(&length) {
        return Err(LauncherError::InvalidConfig(
            "迁移包密码必须为 12 至 128 个字符".to_owned(),
        ));
    }
    Ok(())
}

pub fn create_transfer_package(
    database: &Path,
    attachments: &Path,
    destination: &Path,
    password: &str,
    application_version: &str,
) -> LauncherResult<TransferManifest> {
    validate_transfer_password(password)?;
    if !database.is_file() || !attachments.is_file() {
        return Err(LauncherError::InvalidConfig(
            "迁移包源数据文件不完整".to_owned(),
        ));
    }
    if let Some(parent) = destination.parent() {
        fs::create_dir_all(parent)?;
    }
    let manifest = TransferManifest::new(application_version, database, attachments)?;
    let manifest_bytes = serde_json::to_vec(&manifest)?;
    let partial = partial_path(destination);
    let result: LauncherResult<()> = (|| {
        let output = File::create(&partial)?;
        let encryptor = Encryptor::with_user_passphrase(SecretString::from(password.to_owned()));
        let mut writer = encryptor
            .wrap_output(output)
            .map_err(|error| LauncherError::Internal(format!("创建迁移包加密流失败：{error}")))?;
        writer.write_all(TRANSFER_MAGIC)?;
        write_sized_bytes(&mut writer, &manifest_bytes)?;
        write_sized_file(&mut writer, database)?;
        write_sized_file(&mut writer, attachments)?;
        writer
            .finish()
            .map_err(|error| LauncherError::Internal(format!("完成迁移包加密失败：{error}")))?;
        fs::rename(&partial, destination)?;
        Ok(())
    })();
    if result.is_err() {
        let _ = fs::remove_file(&partial);
        let _ = fs::remove_file(destination);
    }
    result?;
    Ok(manifest)
}

pub fn extract_transfer_package(
    source: &Path,
    staging_dir: &Path,
    password: &str,
) -> LauncherResult<(TransferManifest, PathBuf, PathBuf)> {
    validate_transfer_password(password)?;
    if !source.is_file() {
        return Err(LauncherError::InvalidConfig("迁移包不存在".to_owned()));
    }
    fs::create_dir_all(staging_dir)?;
    let database = staging_dir.join("database.sql");
    let attachments = staging_dir.join("attachments.tar.gz");
    let result = (|| {
        let encrypted = BufReader::new(File::open(source)?);
        let decryptor = Decryptor::new_buffered(encrypted)
            .map_err(|_| LauncherError::InvalidConfig("迁移包格式错误或已损坏".to_owned()))?;
        let identity = age::scrypt::Identity::new(SecretString::from(password.to_owned()));
        let mut reader = decryptor
            .decrypt(iter::once(&identity as &dyn age::Identity))
            .map_err(|_| LauncherError::InvalidConfig("迁移包密码错误或文件已损坏".to_owned()))?;

        let mut magic = vec![0_u8; TRANSFER_MAGIC.len()];
        reader
            .read_exact(&mut magic)
            .map_err(|_| LauncherError::InvalidConfig("迁移包内容不完整".to_owned()))?;
        if magic != TRANSFER_MAGIC {
            return Err(LauncherError::InvalidConfig(
                "不支持的迁移包格式".to_owned(),
            ));
        }
        let manifest_bytes = read_sized_bytes(&mut reader, MAX_MANIFEST_BYTES)?;
        let manifest: TransferManifest = serde_json::from_slice(&manifest_bytes)?;
        if manifest.format_version != TRANSFER_FORMAT_VERSION {
            return Err(LauncherError::InvalidConfig(format!(
                "迁移包数据格式版本 {} 不受支持",
                manifest.format_version
            )));
        }
        read_sized_file(&mut reader, &database)?;
        read_sized_file(&mut reader, &attachments)?;
        let mut trailing = [0_u8; 1];
        if reader.read(&mut trailing)? != 0 {
            return Err(LauncherError::InvalidConfig(
                "迁移包包含无法识别的附加内容".to_owned(),
            ));
        }
        if sha256_file(&database)? != manifest.database_sha256
            || sha256_file(&attachments)? != manifest.attachments_sha256
        {
            return Err(LauncherError::InvalidConfig(
                "迁移包数据校验失败，文件可能已损坏".to_owned(),
            ));
        }
        Ok(manifest)
    })();
    match result {
        Ok(manifest) => Ok((manifest, database, attachments)),
        Err(error) => {
            let _ = fs::remove_file(database);
            let _ = fs::remove_file(attachments);
            Err(error)
        }
    }
}

fn write_sized_bytes(writer: &mut impl Write, bytes: &[u8]) -> LauncherResult<()> {
    writer.write_all(&(bytes.len() as u64).to_be_bytes())?;
    writer.write_all(bytes)?;
    Ok(())
}

fn write_sized_file(writer: &mut impl Write, path: &Path) -> LauncherResult<()> {
    let size = fs::metadata(path)?.len();
    writer.write_all(&size.to_be_bytes())?;
    io::copy(&mut BufReader::new(File::open(path)?), writer)?;
    Ok(())
}

fn read_length(reader: &mut impl Read) -> LauncherResult<u64> {
    let mut bytes = [0_u8; 8];
    reader
        .read_exact(&mut bytes)
        .map_err(|_| LauncherError::InvalidConfig("迁移包内容不完整".to_owned()))?;
    Ok(u64::from_be_bytes(bytes))
}

fn read_sized_bytes(reader: &mut impl Read, maximum: u64) -> LauncherResult<Vec<u8>> {
    let length = read_length(reader)?;
    if length > maximum {
        return Err(LauncherError::InvalidConfig(
            "迁移包清单大小异常".to_owned(),
        ));
    }
    let mut bytes = vec![0_u8; length as usize];
    reader
        .read_exact(&mut bytes)
        .map_err(|_| LauncherError::InvalidConfig("迁移包内容不完整".to_owned()))?;
    Ok(bytes)
}

fn read_sized_file(reader: &mut impl Read, destination: &Path) -> LauncherResult<()> {
    let length = read_length(reader)?;
    let mut limited = reader.take(length);
    let mut output = File::create(destination)?;
    let copied = io::copy(&mut limited, &mut output)?;
    if copied != length {
        return Err(LauncherError::InvalidConfig("迁移包内容不完整".to_owned()));
    }
    output.sync_all()?;
    Ok(())
}

fn sha256_file(path: &Path) -> LauncherResult<String> {
    let mut input = BufReader::new(File::open(path)?);
    let mut digest = Sha256::new();
    io::copy(&mut input, &mut digest)?;
    Ok(format!("{:x}", digest.finalize()))
}

fn partial_path(destination: &Path) -> PathBuf {
    PathBuf::from(format!("{}.partial", destination.display()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn encrypted_transfer_round_trip_preserves_data_and_manifest_contract() {
        let dir = tempdir().unwrap();
        let database = dir.path().join("database.sql");
        let attachments = dir.path().join("attachments.tar.gz");
        let package = dir.path().join("store.rehab-transfer");
        fs::write(&database, b"database with users and permissions").unwrap();
        fs::write(&attachments, b"attachment archive").unwrap();

        let manifest = create_transfer_package(
            &database,
            &attachments,
            &package,
            "correct-horse-battery",
            "1.0.0",
        )
        .unwrap();
        let output = dir.path().join("output");
        let (restored, restored_database, restored_attachments) =
            extract_transfer_package(&package, &output, "correct-horse-battery").unwrap();

        assert_eq!(manifest, restored);
        assert_eq!(
            fs::read(restored_database).unwrap(),
            fs::read(database).unwrap()
        );
        assert_eq!(
            fs::read(restored_attachments).unwrap(),
            fs::read(attachments).unwrap()
        );
        assert!(restored.excludes.iter().any(|item| item.contains("TLS")));
        assert!(restored
            .excludes
            .iter()
            .any(|item| item.contains("运行密码")));
    }

    #[test]
    fn wrong_password_does_not_leave_plaintext_files() {
        let dir = tempdir().unwrap();
        let database = dir.path().join("database.sql");
        let attachments = dir.path().join("attachments.tar.gz");
        let package = dir.path().join("store.rehab-transfer");
        fs::write(&database, b"database").unwrap();
        fs::write(&attachments, b"attachments").unwrap();
        create_transfer_package(
            &database,
            &attachments,
            &package,
            "correct-horse-battery",
            "1.0.0",
        )
        .unwrap();
        let output = dir.path().join("output");

        assert!(extract_transfer_package(&package, &output, "incorrect-password").is_err());
        assert!(!output.join("database.sql").exists());
        assert!(!output.join("attachments.tar.gz").exists());
    }

    #[test]
    fn transfer_password_has_a_minimum_length() {
        assert!(validate_transfer_password("short").is_err());
        assert!(validate_transfer_password("twelve-chars").is_ok());
    }
}
