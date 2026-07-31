use crate::error::{LauncherError, LauncherResult};
use fs2::FileExt;
use std::fs::{self, File, OpenOptions};
use std::path::Path;

pub struct InstanceLock {
    file: File,
}

impl InstanceLock {
    pub fn acquire(data_dir: &Path) -> LauncherResult<Self> {
        fs::create_dir_all(data_dir)?;
        let file = OpenOptions::new()
            .create(true)
            .read(true)
            .write(true)
            .open(data_dir.join("launcher.lock"))?;
        file.try_lock_exclusive()
            .map_err(|_| LauncherError::AlreadyRunning)?;
        Ok(Self { file })
    }
}

impl Drop for InstanceLock {
    fn drop(&mut self) {
        let _ = self.file.unlock();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn second_instance_is_rejected_and_lock_is_reusable() {
        let dir = tempdir().unwrap();
        let first = InstanceLock::acquire(dir.path()).unwrap();
        assert!(matches!(
            InstanceLock::acquire(dir.path()),
            Err(LauncherError::AlreadyRunning)
        ));
        drop(first);
        InstanceLock::acquire(dir.path()).unwrap();
    }
}
