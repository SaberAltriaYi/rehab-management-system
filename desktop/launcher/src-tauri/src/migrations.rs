//! 桌面版数据库增量迁移（020+）。
//!
//! 语义与 `deploy/internal/migrate.sh apply` 一致：
//! - 001-019 是不可重放的初始化基线，只能由脱敏快照登记，升级时绝不执行；
//! - 数据库账本中的每个版本都必须出现在本发布清单中且校验和一致，否则拒绝（包括“数据来自更新版本”）；
//! - 待执行列表在任何写入之前完整计算；每个脚本实际执行成功后才登记（`installed_by=desktop-upgrade`）。
//!
//! 旧版桌面快照只执行并登记了 001-019，没有 020+ 的表，因此桌面端不需要 `adopt`：
//! 只要账本通过校验，就按顺序执行缺失的增量迁移。

use crate::error::{LauncherError, LauncherResult};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};

/// 初始化基线的最后一个版本。
pub const BASELINE_THROUGH: u32 = 19;
/// 运行资源中的迁移清单（构建时由 `deploy/internal/migrations.manifest` 生成）。
pub const MANIFEST_RELATIVE: &str = "sql/migrations.manifest";
/// 运行资源中的增量迁移脚本目录，文件名为 `<三位版本>.sql`。
pub const MIGRATIONS_DIR: &str = "sql/migrations";
pub const INSTALLED_BY: &str = "desktop-upgrade";
pub const LEDGER_EXISTS_QUERY: &str = "SELECT COUNT(*) FROM information_schema.tables \
     WHERE table_schema = DATABASE() AND table_name = 'internal_schema_history';";
pub const HISTORY_QUERY: &str =
    "SELECT version, checksum FROM internal_schema_history ORDER BY version;";

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Migration {
    pub version: String,
    pub checksum: String,
    pub script_path: String,
    pub description: String,
}

impl Migration {
    fn number(&self) -> u32 {
        self.version.parse().unwrap_or(0)
    }

    pub fn is_baseline(&self) -> bool {
        self.number() <= BASELINE_THROUGH
    }
}

fn invalid(message: impl Into<String>) -> LauncherError {
    LauncherError::RuntimeInvalid(message.into())
}

fn is_version(value: &str) -> bool {
    value.len() == 3 && value.bytes().all(|byte| byte.is_ascii_digit())
}

fn is_checksum(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

fn is_safe_script_path(value: &str) -> bool {
    let Some(name) = value
        .strip_prefix("sql/mysql/")
        .or_else(|| value.strip_prefix("deploy/internal/"))
    else {
        return false;
    };
    name.ends_with(".sql")
        && !name.contains("..")
        && name
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-'))
}

fn is_safe_description(value: &str) -> bool {
    !value.trim().is_empty()
        && value.chars().count() <= 200
        && !value
            .chars()
            .any(|character| matches!(character, '\'' | '\\' | '|' | '`') || character.is_control())
}

/// 解析并校验迁移清单：`version|sha256|script_path|description`。
pub fn parse_manifest(text: &str) -> LauncherResult<Vec<Migration>> {
    let mut rows = Vec::new();
    let mut previous = 0u32;
    for line in text.lines() {
        let line = line.trim_end_matches('\r');
        if line.trim().is_empty() || line.starts_with('#') {
            continue;
        }
        let fields: Vec<&str> = line.split('|').collect();
        if fields.len() != 4 {
            return Err(invalid(format!("迁移清单格式错误：{line}")));
        }
        let (version, checksum, script_path, description) =
            (fields[0], fields[1], fields[2], fields[3]);
        if !is_version(version) {
            return Err(invalid(format!("迁移清单版本非法：{version}")));
        }
        let number: u32 = version.parse().unwrap_or(0);
        if number <= previous {
            return Err(invalid(format!("迁移清单版本重复或乱序：{version}")));
        }
        previous = number;
        if !is_checksum(checksum) {
            return Err(invalid(format!("迁移清单校验和非法：{version}")));
        }
        if !is_safe_script_path(script_path) {
            return Err(invalid(format!("迁移清单路径非法：{version}")));
        }
        if !is_safe_description(description) {
            return Err(invalid(format!("迁移清单说明非法：{version}")));
        }
        rows.push(Migration {
            version: version.to_owned(),
            checksum: checksum.to_owned(),
            script_path: script_path.to_owned(),
            description: description.to_owned(),
        });
    }
    let baseline: Vec<&str> = rows
        .iter()
        .filter(|row| row.is_baseline())
        .map(|row| row.version.as_str())
        .collect();
    let expected: Vec<String> = (1..=BASELINE_THROUGH)
        .map(|value| format!("{value:03}"))
        .collect();
    if baseline != expected.iter().map(String::as_str).collect::<Vec<_>>() {
        return Err(invalid("迁移清单中的初始化基线必须恰好为 001-019"));
    }
    Ok(rows)
}

/// 解析 `mysql --batch --skip-column-names` 输出的账本（制表符分隔）。
pub fn parse_history(stdout: &str) -> LauncherResult<Vec<(String, String)>> {
    let mut rows = Vec::new();
    for line in stdout.lines() {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        let mut parts = line.split_whitespace();
        let (Some(version), Some(checksum), None) = (parts.next(), parts.next(), parts.next())
        else {
            return Err(LauncherError::CommandFailed(
                "数据库迁移账本格式异常，已停止且未修改数据库".to_owned(),
            ));
        };
        if !is_version(version) || !is_checksum(checksum) {
            return Err(LauncherError::CommandFailed(
                "数据库迁移账本含非法版本或校验和，已停止且未修改数据库".to_owned(),
            ));
        }
        rows.push((version.to_owned(), checksum.to_owned()));
    }
    Ok(rows)
}

/// 在任何写入之前计算待执行的增量迁移。任何不一致都失败且不修改数据库。
pub fn pending_migrations(
    manifest: &[Migration],
    history: &[(String, String)],
) -> LauncherResult<Vec<Migration>> {
    let known: BTreeMap<&str, &Migration> = manifest
        .iter()
        .map(|row| (row.version.as_str(), row))
        .collect();
    let recorded: BTreeMap<&str, &str> = history
        .iter()
        .map(|(version, checksum)| (version.as_str(), checksum.as_str()))
        .collect();
    for (version, checksum) in &recorded {
        let Some(row) = known.get(version) else {
            return Err(LauncherError::CommandFailed(format!(
                "数据库包含本版本不认识的迁移 {version}（数据可能来自更新版本），已停止且未修改数据库；请安装相同或更高版本"
            )));
        };
        if row.checksum != *checksum {
            return Err(LauncherError::CommandFailed(format!(
                "数据库迁移 {version} 校验和与本版本不一致，已停止且未修改数据库"
            )));
        }
    }
    if let Some(missing) = manifest
        .iter()
        .find(|row| row.is_baseline() && !recorded.contains_key(row.version.as_str()))
    {
        return Err(LauncherError::CommandFailed(format!(
            "迁移账本缺少初始化基线 {}，不能自动升级；请先人工核验数据库与备份",
            missing.version
        )));
    }
    let pending: Vec<Migration> = manifest
        .iter()
        .filter(|row| !row.is_baseline() && !recorded.contains_key(row.version.as_str()))
        .cloned()
        .collect();
    if let Some(first) = pending.first() {
        if let Some(later) = recorded
            .keys()
            .find(|version| version.parse::<u32>().unwrap_or(0) > first.number())
        {
            return Err(LauncherError::CommandFailed(format!(
                "迁移账本不连续：{later} 已登记但更早的 {} 未登记，已停止且未修改数据库",
                first.version
            )));
        }
    }
    Ok(pending)
}

/// 运行资源中某个增量迁移脚本的位置，并复核其 SHA-256 与清单一致。
pub fn verified_script(runtime_root: &Path, migration: &Migration) -> LauncherResult<PathBuf> {
    if migration.is_baseline() {
        return Err(invalid(format!(
            "禁止执行初始化迁移：{}",
            migration.version
        )));
    }
    let path = runtime_root
        .join(MIGRATIONS_DIR)
        .join(format!("{}.sql", migration.version));
    let bytes = fs::read(&path)
        .map_err(|_| invalid(format!("运行资源缺少增量迁移脚本 {}", migration.version)))?;
    let actual = format!("{:x}", Sha256::digest(&bytes));
    if actual != migration.checksum {
        return Err(invalid(format!(
            "增量迁移脚本 {} 校验失败",
            migration.version
        )));
    }
    Ok(path)
}

/// 容器内临时路径，只由三位数字版本构成，不含任何外部输入。
pub fn container_script_path(migration: &Migration) -> String {
    format!("/tmp/rehab-migration-{}.sql", migration.version)
}

/// 登记 SQL。所有字段均已通过清单白名单校验（数字、十六进制、受限路径和不含引号的说明）。
pub fn ledger_insert_sql(migration: &Migration, execution_ms: u128) -> String {
    format!(
        "INSERT INTO internal_schema_history(version, checksum, script_path, description, installed_by, baseline, execution_ms) \
         VALUES ('{}','{}','{}','{}','{}',b'0',{});",
        migration.version,
        migration.checksum,
        migration.script_path,
        migration.description,
        INSTALLED_BY,
        execution_ms
    )
}

#[cfg(test)]
pub mod tests {
    use super::*;
    use tempfile::tempdir;

    pub fn checksum_of(content: &str) -> String {
        format!("{:x}", Sha256::digest(content.as_bytes()))
    }

    pub fn manifest_text(extra: &[(&str, &str)]) -> String {
        let mut text = String::from("# version|sha256|path|description\n");
        for version in 1..=BASELINE_THROUGH {
            text.push_str(&format!(
                "{version:03}|{}|sql/mysql/base-{version:03}.sql|基线 {version:03}\n",
                "a".repeat(64)
            ));
        }
        for (version, content) in extra {
            text.push_str(&format!(
                "{version}|{}|sql/mysql/inc-{version}.sql|增量 {version}\n",
                checksum_of(content)
            ));
        }
        text
    }

    pub fn baseline_history() -> Vec<(String, String)> {
        (1..=BASELINE_THROUGH)
            .map(|version| (format!("{version:03}"), "a".repeat(64)))
            .collect()
    }

    fn versions(rows: &[Migration]) -> Vec<&str> {
        rows.iter().map(|row| row.version.as_str()).collect()
    }

    #[test]
    fn fresh_snapshot_with_full_ledger_needs_nothing() {
        let manifest = parse_manifest(&manifest_text(&[("020", "a"), ("024", "b")])).unwrap();
        let mut history = baseline_history();
        history.push(("020".to_owned(), checksum_of("a")));
        history.push(("024".to_owned(), checksum_of("b")));
        assert!(pending_migrations(&manifest, &history).unwrap().is_empty());
    }

    #[test]
    fn old_desktop_ledger_gets_every_incremental_migration_in_order() {
        let manifest =
            parse_manifest(&manifest_text(&[("020", "a"), ("021", "b"), ("025", "c")])).unwrap();
        let pending = pending_migrations(&manifest, &baseline_history()).unwrap();
        assert_eq!(versions(&pending), ["020", "021", "025"]);
    }

    #[test]
    fn partially_upgraded_ledger_continues_from_the_next_version() {
        let manifest =
            parse_manifest(&manifest_text(&[("020", "a"), ("021", "b"), ("025", "c")])).unwrap();
        let mut history = baseline_history();
        history.push(("020".to_owned(), checksum_of("a")));
        let pending = pending_migrations(&manifest, &history).unwrap();
        assert_eq!(versions(&pending), ["021", "025"]);
    }

    #[test]
    fn database_from_a_newer_release_is_rejected_before_any_write() {
        let manifest = parse_manifest(&manifest_text(&[("020", "a")])).unwrap();
        let mut history = baseline_history();
        history.push(("020".to_owned(), checksum_of("a")));
        history.push(("026".to_owned(), "b".repeat(64)));
        let error = pending_migrations(&manifest, &history)
            .unwrap_err()
            .to_string();
        assert!(error.contains("不认识的迁移 026"), "{error}");
    }

    #[test]
    fn checksum_drift_missing_baseline_and_gaps_are_rejected() {
        let manifest = parse_manifest(&manifest_text(&[("020", "a"), ("021", "b")])).unwrap();

        let mut drift = baseline_history();
        drift.push(("020".to_owned(), "f".repeat(64)));
        assert!(pending_migrations(&manifest, &drift)
            .unwrap_err()
            .to_string()
            .contains("校验和"));

        let mut missing = baseline_history();
        missing.retain(|(version, _)| version != "015");
        assert!(pending_migrations(&manifest, &missing)
            .unwrap_err()
            .to_string()
            .contains("初始化基线 015"));

        let mut gap = baseline_history();
        gap.push(("021".to_owned(), checksum_of("b")));
        assert!(pending_migrations(&manifest, &gap)
            .unwrap_err()
            .to_string()
            .contains("不连续"));
    }

    #[test]
    fn manifest_rejects_unsafe_rows() {
        let base = manifest_text(&[]);
        for bad in [
            "020|zz|sql/mysql/a.sql|x\n",
            &format!("020|{}|../etc/passwd.sql|x\n", "a".repeat(64)),
            &format!(
                "020|{}|sql/mysql/a.sql|x'; DROP TABLE t; --\n",
                "a".repeat(64)
            ),
            &format!("019|{}|sql/mysql/a.sql|dup\n", "a".repeat(64)),
            &format!("20|{}|sql/mysql/a.sql|short\n", "a".repeat(64)),
        ] {
            assert!(parse_manifest(&format!("{base}{bad}")).is_err(), "{bad}");
        }
        assert!(parse_manifest("020|x|y|z\n").is_err());
    }

    #[test]
    fn script_checksum_is_verified_and_baseline_is_never_executable() {
        let dir = tempdir().unwrap();
        fs::create_dir_all(dir.path().join(MIGRATIONS_DIR)).unwrap();
        let manifest =
            parse_manifest(&manifest_text(&[("024", "CREATE TABLE t(id INT);")])).unwrap();
        let migration = manifest.last().unwrap();
        assert!(verified_script(dir.path(), migration).is_err());
        fs::write(
            dir.path().join(MIGRATIONS_DIR).join("024.sql"),
            "CREATE TABLE t(id INT);",
        )
        .unwrap();
        assert!(verified_script(dir.path(), migration).is_ok());
        fs::write(
            dir.path().join(MIGRATIONS_DIR).join("024.sql"),
            "DROP TABLE t;",
        )
        .unwrap();
        assert!(verified_script(dir.path(), migration).is_err());
        assert!(verified_script(dir.path(), &manifest[0]).is_err());
    }

    #[test]
    fn ledger_row_marks_actual_execution() {
        let manifest = parse_manifest(&manifest_text(&[("025", "x")])).unwrap();
        let sql = ledger_insert_sql(manifest.last().unwrap(), 12);
        assert!(sql.contains("'025'"));
        assert!(sql.contains("'desktop-upgrade',b'0',12"));
        assert_eq!(
            container_script_path(manifest.last().unwrap()),
            "/tmp/rehab-migration-025.sql"
        );
    }

    #[test]
    fn repository_manifest_is_accepted() {
        let manifest = parse_manifest(include_str!(
            "../../../../deploy/internal/migrations.manifest"
        ))
        .unwrap();
        assert!(manifest.len() > BASELINE_THROUGH as usize);
        // 旧版桌面快照只登记 001-019：升级时应按顺序执行清单中全部 020+。
        let old_desktop: Vec<(String, String)> = manifest
            .iter()
            .filter(|row| row.is_baseline())
            .map(|row| (row.version.clone(), row.checksum.clone()))
            .collect();
        let pending = pending_migrations(&manifest, &old_desktop).unwrap();
        assert_eq!(pending.len(), manifest.len() - BASELINE_THROUGH as usize);
        assert_eq!(pending[0].version, "020");
    }

    #[test]
    fn history_parser_accepts_batch_output_and_rejects_garbage() {
        let rows = parse_history(&format!(
            "001\t{}\n020\t{}\n",
            "a".repeat(64),
            "b".repeat(64)
        ))
        .unwrap();
        assert_eq!(rows.len(), 2);
        assert!(parse_history("001\n").is_err());
        assert!(parse_history("abc\tdef\n").is_err());
    }
}
