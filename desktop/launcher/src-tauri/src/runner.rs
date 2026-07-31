use crate::error::{LauncherError, LauncherResult};
use std::ffi::OsString;
use std::fs::File;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

#[derive(Clone, Debug, Default)]
pub struct CommandOutput {
    pub success: bool,
    pub stdout: String,
    pub stderr: String,
}

pub trait CommandRunner: Send + Sync {
    fn exists(&self, program: &Path) -> bool;
    fn run(
        &self,
        program: &Path,
        args: &[OsString],
        cwd: Option<&Path>,
    ) -> LauncherResult<CommandOutput>;
    fn run_to_file(
        &self,
        program: &Path,
        args: &[OsString],
        cwd: Option<&Path>,
        destination: &Path,
    ) -> LauncherResult<CommandOutput>;
}

#[derive(Default)]
pub struct ProcessCommandRunner;

impl CommandRunner for ProcessCommandRunner {
    fn exists(&self, program: &Path) -> bool {
        if program.components().count() > 1 {
            return program.is_file();
        }
        std::env::var_os("PATH")
            .map(|paths| {
                std::env::split_paths(&paths).any(|path| executable_file(&path.join(program)))
            })
            .unwrap_or(false)
    }

    fn run(
        &self,
        program: &Path,
        args: &[OsString],
        cwd: Option<&Path>,
    ) -> LauncherResult<CommandOutput> {
        let mut command = Command::new(program);
        command.args(args);
        if let Some(cwd) = cwd {
            command.current_dir(cwd);
        }
        let output = command.output().map_err(|error| {
            LauncherError::CommandFailed(format!("无法执行 {}：{error}", program.display()))
        })?;
        Ok(CommandOutput {
            success: output.status.success(),
            stdout: String::from_utf8_lossy(&output.stdout).into_owned(),
            stderr: String::from_utf8_lossy(&output.stderr).into_owned(),
        })
    }

    fn run_to_file(
        &self,
        program: &Path,
        args: &[OsString],
        cwd: Option<&Path>,
        destination: &Path,
    ) -> LauncherResult<CommandOutput> {
        let file = File::create(destination)?;
        let mut command = Command::new(program);
        command
            .args(args)
            .stdout(Stdio::from(file))
            .stderr(Stdio::piped());
        if let Some(cwd) = cwd {
            command.current_dir(cwd);
        }
        let output = command.output().map_err(|error| {
            LauncherError::CommandFailed(format!("无法执行 {}：{error}", program.display()))
        })?;
        Ok(CommandOutput {
            success: output.status.success(),
            stdout: String::new(),
            stderr: String::from_utf8_lossy(&output.stderr).into_owned(),
        })
    }
}

fn executable_file(path: &Path) -> bool {
    path.is_file()
}

pub fn docker_candidates() -> Vec<PathBuf> {
    let mut candidates = vec![PathBuf::from(if cfg!(windows) {
        "docker.exe"
    } else {
        "docker"
    })];
    if cfg!(target_os = "macos") {
        candidates.extend([
            PathBuf::from("/Applications/Docker.app/Contents/Resources/bin/docker"),
            PathBuf::from("/opt/homebrew/bin/docker"),
            PathBuf::from("/usr/local/bin/docker"),
        ]);
    }
    if cfg!(windows) {
        candidates.push(PathBuf::from(
            r"C:\Program Files\Docker\Docker\resources\bin\docker.exe",
        ));
    }
    candidates
}

pub fn find_docker(runner: &dyn CommandRunner) -> LauncherResult<PathBuf> {
    docker_candidates()
        .into_iter()
        .find(|candidate| runner.exists(candidate))
        .ok_or(LauncherError::DockerMissing)
}

#[cfg(test)]
pub mod test_support {
    use super::*;
    use std::collections::VecDeque;
    use std::io::Write;
    use std::sync::Mutex;

    #[derive(Default)]
    pub struct MockRunner {
        pub exists: bool,
        pub outputs: Mutex<VecDeque<CommandOutput>>,
        pub calls: Mutex<Vec<Vec<OsString>>>,
    }

    impl MockRunner {
        pub fn with_outputs(exists: bool, outputs: Vec<CommandOutput>) -> Self {
            Self {
                exists,
                outputs: Mutex::new(outputs.into()),
                calls: Mutex::new(Vec::new()),
            }
        }
    }

    impl CommandRunner for MockRunner {
        fn exists(&self, _program: &Path) -> bool {
            self.exists
        }

        fn run(
            &self,
            program: &Path,
            args: &[OsString],
            _cwd: Option<&Path>,
        ) -> LauncherResult<CommandOutput> {
            let mut call = vec![program.as_os_str().to_owned()];
            call.extend_from_slice(args);
            self.calls.lock().unwrap().push(call);
            self.outputs
                .lock()
                .unwrap()
                .pop_front()
                .ok_or_else(|| LauncherError::Internal("mock output 不足".to_owned()))
        }

        fn run_to_file(
            &self,
            program: &Path,
            args: &[OsString],
            _cwd: Option<&Path>,
            destination: &Path,
        ) -> LauncherResult<CommandOutput> {
            let output = self.run(program, args, None)?;
            File::create(destination)?.write_all(output.stdout.as_bytes())?;
            Ok(output)
        }
    }
}
