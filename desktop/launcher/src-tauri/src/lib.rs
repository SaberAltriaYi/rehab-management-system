mod config;
mod docker;
mod error;
mod model;
mod runner;
mod runtime;
mod service;
mod single_instance;

use config::AppPaths;
use model::{LauncherOverview, LauncherSettings};
use service::LauncherService;
use std::sync::Mutex;
use tauri::{Manager, State};
use tauri_plugin_opener::OpenerExt;

struct LauncherState(Mutex<LauncherService>);

#[tauri::command]
fn get_overview(state: State<'_, LauncherState>) -> Result<LauncherOverview, String> {
    state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())
        .map(|mut service| service.overview())
}

#[tauri::command]
fn get_settings(state: State<'_, LauncherState>) -> Result<LauncherSettings, String> {
    state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())?
        .settings()
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn save_settings(
    state: State<'_, LauncherState>,
    settings: LauncherSettings,
) -> Result<LauncherOverview, String> {
    let mut service = state.0.lock().map_err(|_| "启动器状态锁异常".to_owned())?;
    service
        .save_settings(settings)
        .map_err(|error| error.to_string())?;
    Ok(service.overview())
}

#[tauri::command]
fn start_services(state: State<'_, LauncherState>) -> Result<LauncherOverview, String> {
    state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())?
        .start()
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn stop_services(state: State<'_, LauncherState>) -> Result<LauncherOverview, String> {
    state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())?
        .stop()
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn restart_services(state: State<'_, LauncherState>) -> Result<LauncherOverview, String> {
    state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())?
        .restart()
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn create_backup(state: State<'_, LauncherState>) -> Result<LauncherOverview, String> {
    state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())?
        .create_backup()
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn read_logs(state: State<'_, LauncherState>) -> Result<String, String> {
    state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())?
        .logs()
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn get_diagnostics(state: State<'_, LauncherState>) -> Result<String, String> {
    Ok(state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())?
        .diagnostics())
}

#[tauri::command]
fn acknowledge_initial_password(
    state: State<'_, LauncherState>,
) -> Result<LauncherOverview, String> {
    state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())?
        .acknowledge_initial_password()
        .map_err(|error| error.to_string())
}

#[tauri::command]
fn open_system(
    app: tauri::AppHandle,
    state: State<'_, LauncherState>,
) -> Result<LauncherOverview, String> {
    let mut service = state.0.lock().map_err(|_| "启动器状态锁异常".to_owned())?;
    let overview = service.overview();
    if !overview.ready {
        return Err("服务尚未全部健康，暂不能打开管理系统。".to_owned());
    }
    app.opener()
        .open_url(&overview.access_url, None::<&str>)
        .map_err(|error| format!("无法打开系统浏览器：{error}"))?;
    Ok(overview)
}

#[tauri::command]
fn open_data_directory(
    app: tauri::AppHandle,
    state: State<'_, LauncherState>,
) -> Result<(), String> {
    let path = state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())?
        .overview()
        .data_directory;
    app.opener()
        .open_path(path, None::<&str>)
        .map_err(|error| format!("无法打开数据目录：{error}"))
}

#[tauri::command]
fn delete_all_data(
    state: State<'_, LauncherState>,
    confirmation: String,
) -> Result<LauncherOverview, String> {
    state
        .0
        .lock()
        .map_err(|_| "启动器状态锁异常".to_owned())?
        .delete_all_data(&confirmation)
        .map_err(|error| error.to_string())
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let fallback = AppPaths::platform_default()?;
            let data_dir = app
                .path()
                .app_data_dir()
                .unwrap_or_else(|_| fallback.data_dir.clone());
            let resource_dir = app.path().resource_dir()?;
            let service = LauncherService::new(AppPaths::from_data_dir(data_dir), resource_dir)
                .map_err(|error| Box::<dyn std::error::Error>::from(error.to_string()))?;
            app.manage(LauncherState(Mutex::new(service)));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_overview,
            get_settings,
            save_settings,
            start_services,
            stop_services,
            restart_services,
            open_system,
            create_backup,
            read_logs,
            open_data_directory,
            get_diagnostics,
            acknowledge_initial_password,
            delete_all_data
        ])
        .run(tauri::generate_context!())
        .expect("康复管理系统启动器运行失败");
}
