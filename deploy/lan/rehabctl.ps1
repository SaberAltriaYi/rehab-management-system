$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = (Resolve-Path (Join-Path $ScriptDir "../..")).Path
$EnvFile = Join-Path $ProjectDir "deploy/internal/.env"
$ComposeFile = Join-Path $ProjectDir "deploy/internal/docker-compose.yml"
$Action = if ($args.Count -gt 0) { $args[0] } else { "status" }

if ($Action -eq "install") {
    & (Join-Path $ScriptDir "install.ps1") @($args | Select-Object -Skip 1)
    exit $LASTEXITCODE
}
if (-not (Test-Path $EnvFile)) {
    throw "尚未安装，请先执行 .\install.ps1"
}

function Invoke-Compose {
    & docker compose --env-file $EnvFile -f $ComposeFile @args
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose 命令失败" }
}

switch ($Action) {
    "start"   { Invoke-Compose up -d }
    "stop"    { Invoke-Compose stop }
    "restart" { Invoke-Compose restart }
    "status"  { Invoke-Compose ps }
    "logs"    {
        $service = if ($args.Count -gt 1) { $args[1] } else { "server" }
        Invoke-Compose logs --since=30m $service
    }
    "update"  {
        Invoke-Compose build --pull server admin
        Invoke-Compose up -d
    }
    "backup"  {
        if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
            throw "Windows 加密备份需要启用 WSL2 与 Docker Desktop WSL 集成"
        }
        $linuxPath = (& wsl.exe wslpath -a $ProjectDir).Trim()
        & wsl.exe bash -lc "cd '$linuxPath' && ./deploy/internal/backup.sh"
        if ($LASTEXITCODE -ne 0) { throw "备份失败" }
    }
    "restore" {
        if ($args.Count -lt 2) { throw "用法：.\rehabctl.ps1 restore backups/rehab-时间" }
        if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
            throw "Windows 恢复需要启用 WSL2 与 Docker Desktop WSL 集成"
        }
        $linuxPath = (& wsl.exe wslpath -a $ProjectDir).Trim()
        $backupPath = $args[1]
        & wsl.exe bash -lc "cd '$linuxPath' && CONFIRM_RESTORE=RESTORE-REHAB-INTERNAL ./deploy/internal/restore.sh '$backupPath'"
        if ($LASTEXITCODE -ne 0) { throw "恢复失败" }
    }
    default {
        throw "用法：.\rehabctl.ps1 start|stop|restart|status|logs|update|backup|restore"
    }
}
