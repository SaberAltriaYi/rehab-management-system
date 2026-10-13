use crate::config::{write_private, AppPaths};
use crate::error::{LauncherError, LauncherResult};
use crate::model::APP_VERSION;
use sha2::{Digest, Sha256};
use std::fs;
use std::path::{Path, PathBuf};
use walkdir::WalkDir;

pub fn locate_bundled_runtime(resource_dir: &Path) -> LauncherResult<PathBuf> {
    let candidates = [
        resource_dir.join("runtime").join(APP_VERSION),
        resource_dir
            .join("resources")
            .join("runtime")
            .join(APP_VERSION),
    ];
    candidates
        .into_iter()
        .find(|candidate| candidate.join("runtime-manifest.sha256").is_file())
        .ok_or_else(|| {
            LauncherError::RuntimeInvalid(format!(
                "未在 {} 中找到 V{} 运行资源",
                resource_dir.display(),
                APP_VERSION
            ))
        })
}

pub fn ensure_runtime(source: &Path, paths: &AppPaths) -> LauncherResult<()> {
    verify_runtime(source)?;
    let installed_marker = paths.runtime_dir.join(".installed");
    if installed_marker.exists() {
        verify_runtime(&paths.runtime_dir)?;
        // Repair only the publicly served copy, not private runtime secrets.
        normalize_public_web_permissions(&paths.runtime_dir)?;
        return Ok(());
    }
    let staging = paths
        .data_dir
        .join("runtime")
        .join(format!(".{APP_VERSION}.staging"));
    if staging.exists() {
        fs::remove_dir_all(&staging)?;
    }
    copy_tree(source, &staging)?;
    verify_runtime(&staging)?;
    normalize_public_web_permissions(&staging)?;
    if paths.runtime_dir.exists() {
        fs::remove_dir_all(&paths.runtime_dir)?;
    }
    fs::rename(&staging, &paths.runtime_dir)?;
    write_private(&installed_marker, b"installed\n")
}

pub fn verify_runtime(root: &Path) -> LauncherResult<()> {
    let manifest_path = root.join("runtime-manifest.sha256");
    let manifest = fs::read_to_string(&manifest_path)
        .map_err(|_| LauncherError::RuntimeInvalid(format!("缺少 {}", manifest_path.display())))?;
    let required = [
        "docker-compose.yml",
        "server/Dockerfile",
        "server/yudao-server.jar",
        "admin/Dockerfile",
        "admin/nginx.conf.template",
        "admin/web/index.html",
        "sql/desktop-bootstrap.sql",
        "sql/migrations.manifest",
        "VERSION.json",
        "LICENSE",
    ];
    for relative in required {
        if !root.join(relative).is_file() {
            return Err(LauncherError::RuntimeInvalid(format!("缺少 {relative}")));
        }
    }
    for line in manifest.lines().filter(|line| !line.trim().is_empty()) {
        let Some((expected, relative)) = line.split_once("  ") else {
            return Err(LauncherError::RuntimeInvalid("校验清单格式错误".to_owned()));
        };
        if relative == "runtime-manifest.sha256" {
            continue;
        }
        let path = root.join(relative);
        let actual = sha256_file(&path)?;
        if actual != expected {
            return Err(LauncherError::RuntimeInvalid(format!(
                "{relative} 校验失败"
            )));
        }
    }
    Ok(())
}

fn copy_tree(source: &Path, destination: &Path) -> LauncherResult<()> {
    for entry in WalkDir::new(source) {
        let entry = entry.map_err(|error| LauncherError::Io(error.into()))?;
        let relative = entry
            .path()
            .strip_prefix(source)
            .map_err(|error| LauncherError::Internal(error.to_string()))?;
        let target = destination.join(relative);
        if entry.file_type().is_dir() {
            fs::create_dir_all(&target)?;
        } else if entry.file_type().is_file() {
            if let Some(parent) = target.parent() {
                fs::create_dir_all(parent)?;
            }
            fs::copy(entry.path(), &target)?;
        } else {
            return Err(LauncherError::RuntimeInvalid(format!(
                "运行资源不允许符号链接或特殊文件：{}",
                relative.display()
            )));
        }
    }
    Ok(())
}

/// Runtime archives extracted under umask 077 can contain 0600/0700 web
/// assets. The Nginx worker must read these after installation; only chmod
/// the verified installed copy, never the source bundle or private files.
fn normalize_public_web_permissions(root: &Path) -> LauncherResult<()> {
    let web = root.join("admin/web");
    for entry in WalkDir::new(&web) {
        let entry = entry.map_err(|error| LauncherError::Io(error.into()))?;
        if !entry.file_type().is_dir() && !entry.file_type().is_file() {
            return Err(LauncherError::RuntimeInvalid(
                "公开网页资源包含符号链接或特殊文件，拒绝修改权限".to_owned(),
            ));
        }
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mode = if entry.file_type().is_dir() {
                0o755
            } else {
                0o644
            };
            fs::set_permissions(entry.path(), fs::Permissions::from_mode(mode))?;
        }
    }
    Ok(())
}

fn sha256_file(path: &Path) -> LauncherResult<String> {
    let bytes = fs::read(path).map_err(|_| {
        LauncherError::RuntimeInvalid(format!("清单引用文件不存在：{}", path.display()))
    })?;
    Ok(format!("{:x}", Sha256::digest(bytes)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn a_changed_runtime_file_fails_integrity_check() {
        let dir = tempdir().unwrap();
        fs::write(dir.path().join("runtime-manifest.sha256"), "00  missing\n").unwrap();
        assert!(verify_runtime(dir.path()).is_err());
    }

    #[cfg(unix)]
    #[test]
    fn extracted_runtime_web_is_readable_without_changing_source_or_secrets() {
        use std::os::unix::fs::PermissionsExt;
        let source = tempdir().unwrap();
        let destination = tempdir().unwrap();
        let web = source.path().join("admin/web/assets");
        fs::create_dir_all(&web).unwrap();
        fs::create_dir_all(source.path().join("secrets")).unwrap();
        fs::write(source.path().join("admin/web/index.html"), b"synthetic web").unwrap();
        fs::write(web.join("main.js"), b"synthetic script").unwrap();
        fs::write(
            source.path().join("secrets/backup.passphrase"),
            b"synthetic secret",
        )
        .unwrap();
        // Model a private source secret explicitly, independent of test-runner umask.
        fs::set_permissions(
            source.path().join("secrets/backup.passphrase"),
            fs::Permissions::from_mode(0o600),
        ).unwrap();
        fs::set_permissions(
            source.path().join("admin/web"),
            fs::Permissions::from_mode(0o700),
        )
        .unwrap();
        fs::set_permissions(&web, fs::Permissions::from_mode(0o700)).unwrap();
        fs::set_permissions(
            source.path().join("admin/web/index.html"),
            fs::Permissions::from_mode(0o600),
        )
        .unwrap();
        fs::set_permissions(web.join("main.js"), fs::Permissions::from_mode(0o600)).unwrap();

        let installed = destination.path().join("runtime-copy");
        copy_tree(source.path(), &installed).unwrap();
        normalize_public_web_permissions(&installed).unwrap();
        let mode = |path: &Path| fs::metadata(path).unwrap().permissions().mode() & 0o777;
        assert_eq!(mode(&installed.join("admin/web")), 0o755);
        assert_eq!(mode(&installed.join("admin/web/assets")), 0o755);
        assert_eq!(mode(&installed.join("admin/web/index.html")), 0o644);
        assert_eq!(mode(&installed.join("admin/web/assets/main.js")), 0o644);
        assert_eq!(mode(&installed.join("secrets/backup.passphrase")), 0o600);
        assert_eq!(mode(&source.path().join("admin/web/index.html")), 0o600);
        assert_eq!(
            fs::read(installed.join("admin/web/index.html")).unwrap(),
            b"synthetic web"
        );
    }
}
