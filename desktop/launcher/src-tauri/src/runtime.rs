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
    normalize_public_web_permissions(&staging)?;
    verify_runtime(&staging)?;
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

// A hardened host umask or macOS DMG packing can make bundled web asset
// directories 0700, which unprivileged Nginx cannot traverse once installed.
// Normalize ONLY public web assets; do not widen secrets, backups or DB files.
fn normalize_public_web_permissions(root: &Path) -> LauncherResult<()> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        for item in WalkDir::new(root.join("admin/web")) {
            let item = item.map_err(|error| LauncherError::Io(error.into()))?;
            let mode = if item.file_type().is_dir() {
                0o755
            } else if item.file_type().is_file() {
                0o644
            } else {
                return Err(LauncherError::RuntimeInvalid(
                    "前端静态资源包含符号链接或特殊文件".to_owned(),
                ));
            };
            fs::set_permissions(item.path(), fs::Permissions::from_mode(mode))?;
        }
    }
    #[cfg(not(unix))]
    {
        let _ = root;
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
    fn dmgs_hardened_umask_is_fixed_only_for_public_nginx_web_assets() {
        use std::os::unix::fs::PermissionsExt;
        let tmp = tempdir().unwrap();
        let source = tmp.path().join("bundle");
        let target = tmp.path().join("installed");
        fs::create_dir_all(source.join("admin/web/assets")).unwrap();
        fs::write(source.join("admin/web/assets/main.js"), b"synthetic JS").unwrap();
        fs::write(source.join("private-secret"), b"synthetic secret").unwrap();
        for dir in ["admin/web", "admin/web/assets"] {
            fs::set_permissions(source.join(dir), fs::Permissions::from_mode(0o700)).unwrap();
        }
        for file in ["admin/web/assets/main.js", "private-secret"] {
            fs::set_permissions(source.join(file), fs::Permissions::from_mode(0o600)).unwrap();
        }
        copy_tree(&source, &target).unwrap();
        normalize_public_web_permissions(&target).unwrap();
        for dir in ["admin/web", "admin/web/assets"] {
            assert_eq!(fs::metadata(target.join(dir)).unwrap().permissions().mode() & 0o777,
                0o755);
        }
        assert_eq!(fs::metadata(target.join("admin/web/assets/main.js"))
            .unwrap().permissions().mode() & 0o777, 0o644);
        assert_eq!(fs::metadata(target.join("private-secret"))
            .unwrap().permissions().mode() & 0o777, 0o600);
    }
}
