//! Ignored real-Docker migration/recovery test. Never targets an installed app.
use super::*;
use crate::config::{write_environment, RuntimeSecrets};
use crate::docker::context_for;
use crate::runner::{CommandOutput, ProcessCommandRunner};
use age::Decryptor;
use std::{
    env,
    ffi::OsString,
    fs::{self, File},
    io::{self, BufReader},
    iter,
    path::{Path, PathBuf},
    sync::Arc,
};
use tempfile::tempdir;

struct IsolatedRunner {
    project: String,
    override_file: PathBuf,
    real: ProcessCommandRunner,
}

impl IsolatedRunner {
    fn scoped(&self, args: &[OsString]) -> LauncherResult<Vec<OsString>> {
        if args.len() < 7
            || args[0] != "compose"
            || args[1] != "--project-name"
            || args[2] != crate::model::COMPOSE_PROJECT
            || args[3] != "--env-file"
            || args[5] != "--file"
        {
            return Err(LauncherError::InvalidConfig(
                "拒绝非合成项目 Docker 指令".into(),
            ));
        }
        let mut result = args.to_vec();
        result[2] = self.project.clone().into();
        result.insert(7, "--file".into());
        result.insert(8, self.override_file.as_os_str().to_owned());
        Ok(result)
    }
}
impl CommandRunner for IsolatedRunner {
    fn exists(&self, program: &Path) -> bool {
        self.real.exists(program)
    }
    fn run(
        &self,
        program: &Path,
        args: &[OsString],
        cwd: Option<&Path>,
    ) -> LauncherResult<CommandOutput> {
        self.real.run(program, &self.scoped(args)?, cwd)
    }
    fn run_to_file(
        &self,
        program: &Path,
        args: &[OsString],
        cwd: Option<&Path>,
        dest: &Path,
    ) -> LauncherResult<CommandOutput> {
        self.real
            .run_to_file(program, &self.scoped(args)?, cwd, dest)
    }
}
fn required(key: &str) -> String {
    env::var(key).unwrap_or_else(|_| panic!("isolated harness missing {key}"))
}
fn decrypt(source: &Path, destination: &Path, passphrase: &str) {
    let encrypted = BufReader::new(File::open(source).unwrap());
    let decryptor = Decryptor::new_buffered(encrypted).unwrap();
    let identity = age::scrypt::Identity::new(SecretString::from(passphrase.to_owned()));
    let mut reader = decryptor
        .decrypt(iter::once(&identity as &dyn age::Identity))
        .unwrap();
    let mut output = File::create(destination).unwrap();
    io::copy(&mut reader, &mut output).unwrap();
    output.sync_all().unwrap();
}

#[test]
#[ignore = "explicit synthetic Compose harness required"]
fn partial_ddl_then_explicit_restore_on_real_synthetic_docker() {
    assert_eq!(
        required("REHAB_LAUNCHER_E2E_GUARD"),
        "SYNTHETIC_TMPFS_ONLY_V1"
    );
    let project = required("REHAB_LAUNCHER_E2E_PROJECT");
    let suffix = project.strip_prefix("rehab-ephemeral-").unwrap_or("");
    assert!(suffix.len() == 12 && suffix.bytes().all(|c| c.is_ascii_hexdigit()));
    let override_file = PathBuf::from(required("REHAB_LAUNCHER_E2E_OVERRIDE"));
    assert!(override_file.is_file());
    assert!(override_file
        .parent()
        .and_then(|p| p.file_name())
        .and_then(|n| n.to_str())
        .is_some_and(|n| n.starts_with("rehab-ephemeral-e2e.")));
    let runtime = PathBuf::from(required("REHAB_LAUNCHER_E2E_RUNTIME"));
    assert!(
        runtime.join("docker-compose.yml").is_file() && runtime.join(MANIFEST_RELATIVE).is_file()
    );

    let private = tempdir().unwrap();
    let paths = AppPaths::from_data_dir(private.path().join("launcher-test-only"));
    let runner = Arc::new(IsolatedRunner {
        project,
        override_file,
        real: ProcessCommandRunner,
    });
    let service =
        LauncherService::with_runner(paths.clone(), private.path().join("unused-bundle"), runner)
            .unwrap();
    let passphrase = fs::read_to_string(paths.secrets_dir.join("backup.passphrase")).unwrap();
    let secrets = RuntimeSecrets {
        db_password: required("DB_PASSWORD"),
        mysql_root_password: required("MYSQL_ROOT_PASSWORD"),
        redis_password: required("REDIS_PASSWORD"),
        backup_passphrase: passphrase.trim().into(),
    };
    write_environment(&paths, &LauncherSettings::default(), &secrets).unwrap();

    // Reuse packaged 001-019 and 020..026 checksums, but deliberately make
    // 021 invalid with its own matching checksum, in this private copy ONLY.
    let migration_dir = paths.runtime_dir.join(crate::migrations::MIGRATIONS_DIR);
    fs::create_dir_all(&migration_dir).unwrap();
    let bad_sql = b"INTENTIONALLY INVALID SQL STATEMENT IN SYNTHETIC TEST;\n";
    let manifest = fs::read_to_string(runtime.join(MANIFEST_RELATIVE)).unwrap();
    let updated = manifest
        .lines()
        .map(|line| {
            if line.starts_with("021|") {
                let cols: Vec<_> = line.split('|').collect();
                format!("021|{:x}|{}|{}", Sha256::digest(bad_sql), cols[2], cols[3])
            } else {
                line.to_owned()
            }
        })
        .collect::<Vec<_>>()
        .join("\n")
        + "\n";
    fs::write(paths.runtime_dir.join(MANIFEST_RELATIVE), updated).unwrap();
    for version in ["020", "022", "023", "024", "025", "026"] {
        fs::copy(
            runtime.join(format!("sql/migrations/{version}.sql")),
            migration_dir.join(format!("{version}.sql")),
        )
        .unwrap();
    }
    fs::write(migration_dir.join("021.sql"), bad_sql).unwrap();
    let ctx = context_for(PathBuf::from("docker"), &runtime, paths.env_path());
    assert_eq!(
        service
            .mysql_query(&ctx, "SELECT COUNT(*) FROM internal_schema_history;")
            .unwrap()
            .trim(),
        "19"
    );
    assert_eq!(
        service
            .mysql_query(&ctx, "SELECT marker FROM synthetic_restore_probe;")
            .unwrap()
            .trim(),
        "SYNTHETIC_BEFORE_UPGRADE"
    );

    let error = service.upgrade_database(&ctx).unwrap_err().to_string();
    assert!(
        error.contains("021") && error.contains("账本未登记"),
        "{error}"
    );
    assert_eq!(service.mysql_query(&ctx, "SELECT GROUP_CONCAT(version ORDER BY version) FROM internal_schema_history WHERE version >= '020';").unwrap().trim(), "020");
    assert_eq!(service.mysql_query(&ctx, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='report_go_view_project';").unwrap().trim(), "1");
    println!("PASS: actual launcher backed up and executed 020; failed 021 not registered");

    let manifest = fs::read_dir(&paths.backups_dir)
        .unwrap()
        .map(|entry| entry.unwrap().path())
        .find(|p| p.extension().is_some_and(|ext| ext == "json"))
        .unwrap();
    verify_backup_before_migration(&manifest).unwrap();
    let meta: serde_json::Value = serde_json::from_slice(&fs::read(&manifest).unwrap()).unwrap();
    let sql_age = manifest
        .parent()
        .unwrap()
        .join(meta["database"].as_str().unwrap());
    let attachment_age = manifest
        .parent()
        .unwrap()
        .join(meta["attachments"].as_str().unwrap());
    let stage = private.path().join("decrypted-test-only");
    fs::create_dir(&stage).unwrap();
    let sql = stage.join("database.sql");
    let attachments = stage.join("attachments.tar.gz");
    decrypt(&sql_age, &sql, &secrets.backup_passphrase);
    decrypt(&attachment_age, &attachments, &secrets.backup_passphrase);
    assert!(fs::metadata(&sql).unwrap().len() > 100);
    assert!(fs::metadata(&attachments).unwrap().len() > 20);
    println!(
        "PASS: SQL and attachment encrypted backups actually decrypt with test-only passphrase"
    );

    // Simulate synthetic post-failure divergence so a no-op restore CANNOT pass.
    service
        .mysql_query(
            &ctx,
            "UPDATE synthetic_restore_probe SET marker='SYNTHETIC_AFTER_FAILED_UPGRADE';",
        )
        .unwrap();
    service.run_compose_os(&ctx, vec![
        "exec".into(), "--no-TTY".into(), "server".into(), "sh".into(),
        "-c".into(),
        "printf 'SYNTHETIC_ATTACHMENT_AFTER_FAILED_UPGRADE\\n' > /app/data/rehab/synthetic-attachment.txt".into()
    ]).unwrap();

    // An explicitly authorized operation for this synthetic project only.
    // A production database upgrade currently does NOT automatically restore.
    service
        .restore_transfer_sources(&ctx, &sql, &attachments)
        .unwrap();
    assert_eq!(
        service
            .mysql_query(&ctx, "SELECT COUNT(*) FROM internal_schema_history;")
            .unwrap()
            .trim(),
        "19"
    );
    assert_eq!(service.mysql_query(&ctx, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='report_go_view_project';").unwrap().trim(), "0");
    assert_eq!(
        service
            .mysql_query(&ctx, "SELECT marker FROM synthetic_restore_probe;")
            .unwrap()
            .trim(),
        "SYNTHETIC_BEFORE_UPGRADE"
    );
    let states = read_service_states(service.runner.as_ref(), &ctx).unwrap();
    assert!(states.len() == 4 && states.iter().all(|s| s.state == "healthy"));
    assert!(sql_age.exists() && attachment_age.exists());
    println!("PASS: explicit synthetic recovery restored ledger 19, original SQL row, and 4 healthy services");

    // From the recovered 19-version snapshot run all seven ORIGINAL packaged
    // migrations via the real launcher method, not a shell imitation.
    fs::write(
        paths.runtime_dir.join(MANIFEST_RELATIVE),
        fs::read(runtime.join(MANIFEST_RELATIVE)).unwrap(),
    )
    .unwrap();
    fs::copy(
        runtime.join("sql/migrations/021.sql"),
        migration_dir.join("021.sql"),
    )
    .unwrap();
    let count = service
        .upgrade_database(&ctx)
        .expect("real seven-migration launcher upgrade");
    assert_eq!(count, 7);
    assert_eq!(
        service
            .mysql_query(&ctx, "SELECT COUNT(*) FROM internal_schema_history;")
            .unwrap()
            .trim(),
        "26"
    );
    assert_eq!(
        service.mysql_query(&ctx, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='rehab_tenant_license_binding';")
            .unwrap().trim(), "1");
    assert_eq!(
        service.mysql_query(&ctx, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'rehab_motion_%';")
            .unwrap().trim(), "11");
    assert_eq!(
        service
            .mysql_query(&ctx, "SELECT marker FROM synthetic_restore_probe;")
            .unwrap()
            .trim(),
        "SYNTHETIC_BEFORE_UPGRADE"
    );
    let states = read_service_states(service.runner.as_ref(), &ctx).unwrap();
    assert!(states.len() == 4 && states.iter().all(|s| s.state == "healthy"));
    assert_eq!(
        fs::read_dir(&paths.backups_dir)
            .unwrap()
            .filter(|entry| entry
                .as_ref()
                .unwrap()
                .path()
                .extension()
                .is_some_and(|ext| ext == "json"))
            .count(),
        2,
        "separate encrypted backup manifests required before each DDL attempt"
    );
    println!("PASS: real launcher completed packaged 020-026, ledger 26, motion/license tables and healthy services");
}
