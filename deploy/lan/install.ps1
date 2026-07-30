$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = (Resolve-Path (Join-Path $ScriptDir "../..")).Path
$InternalDir = Join-Path $ProjectDir "deploy/internal"
$EnvFile = Join-Path $InternalDir ".env"
$ComposeFile = Join-Path $InternalDir "docker-compose.yml"
$MarkerFile = Join-Path $ScriptDir ".installed"
$FirstLoginFile = Join-Path $ScriptDir "FIRST_LOGIN.txt"
$RequestedIp = if ($args.Count -gt 0) { $args[0] } else { $null }

function Fail([string]$Message) {
    throw "FAIL: $Message"
}

function New-RandomHex([int]$Bytes) {
    $buffer = [byte[]]::new($Bytes)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
    return ([Convert]::ToHexString($buffer)).ToLowerInvariant()
}

function Get-LanIPv4 {
    if ($RequestedIp) {
        return $RequestedIp
    }
    $candidate = Get-NetIPConfiguration |
        Where-Object { $_.IPv4DefaultGateway -and $_.IPv4Address } |
        ForEach-Object { $_.IPv4Address.IPAddress } |
        Where-Object { $_ -notlike "169.254.*" -and $_ -ne "127.0.0.1" } |
        Select-Object -First 1
    if (-not $candidate) {
        Fail "无法识别局域网 IPv4；请执行 .\install.ps1 192.168.x.x"
    }
    return $candidate
}

function Invoke-Compose {
    & docker compose --env-file $EnvFile -f $ComposeFile @args
    if ($LASTEXITCODE -ne 0) {
        Fail "Docker Compose 命令失败"
    }
}

function Wait-MySql {
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        & docker compose --env-file $EnvFile -f $ComposeFile exec -T mysql sh -c `
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin ping -h 127.0.0.1 -uroot --silent' 2>$null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 2
    }
    Fail "MySQL 在 180 秒内未就绪"
}

function Wait-Https([string]$IpAddress) {
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & curl.exe --fail --silent --insecure "https://${IpAddress}:8443/" *> $null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 2
    }
    Fail "管理端在 120 秒内未就绪"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail "请先安装并启动 Docker Desktop"
}
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) { Fail "Docker Compose 不可用" }

if (-not (Test-Path (Join-Path $ProjectDir "yudao-server/target/yudao-server.jar"))) {
    Fail "发布包缺少 yudao-server.jar"
}
if (-not (Test-Path (Join-Path $ProjectDir "yudao-ui/yudao-ui-admin-vue3-app/dist-internal/index.html"))) {
    Fail "发布包缺少前端 dist-internal"
}

$BindAddress = Get-LanIPv4
$parsedIp = $null
if (-not [System.Net.IPAddress]::TryParse($BindAddress, [ref]$parsedIp) -or
    $parsedIp.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
    $BindAddress -eq "0.0.0.0") {
    Fail "BIND_ADDRESS 必须是明确的局域网 IPv4"
}

if (-not (Test-Path $EnvFile)) {
    $dbPassword = New-RandomHex 24
    $rootPassword = New-RandomHex 24
    $redisPassword = New-RandomHex 24
    $envLines = @(
        "COMPOSE_PROJECT_NAME=rehab-lan",
        "TZ=Asia/Shanghai",
        "BIND_ADDRESS=$BindAddress",
        "LAN_HOSTNAME=rehab.local",
        "APP_PORT=8080",
        "TLS_PORT=8443",
        "DB_NAME=ruoyi-vue-pro",
        "DB_USERNAME=yudao",
        "DB_PASSWORD=$dbPassword",
        "MYSQL_ROOT_PASSWORD=$rootPassword",
        "REDIS_PASSWORD=$redisPassword",
        "BACKUP_KEY_FILE=deploy/internal/secrets/backup.key",
        "JAVA_OPTS=-Xms256m -Xmx768m -Djava.security.egd=file:/dev/./urandom -Dsun.io.useCanonCaches=false"
    )
    [IO.File]::WriteAllLines($EnvFile, $envLines, [Text.UTF8Encoding]::new($false))
} else {
    $bindLine = Get-Content $EnvFile | Where-Object { $_ -like "BIND_ADDRESS=*" } | Select-Object -First 1
    $BindAddress = $bindLine.Substring("BIND_ADDRESS=".Length)
}

$SecretsDir = Join-Path $InternalDir "secrets"
New-Item -ItemType Directory -Force -Path $SecretsDir | Out-Null
$BackupKey = Join-Path $SecretsDir "backup.key"
if (-not (Test-Path $BackupKey)) {
    $keyBytes = [byte[]]::new(48)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($keyBytes)
    [IO.File]::WriteAllText($BackupKey, [Convert]::ToBase64String($keyBytes), [Text.UTF8Encoding]::new($false))
}

$CertDir = Join-Path $InternalDir "certs"
New-Item -ItemType Directory -Force -Path $CertDir | Out-Null
if (-not (Test-Path (Join-Path $CertDir "server.crt"))) {
    $tlsScript = Join-Path $ScriptDir "container-generate-tls.sh"
    & docker run --rm `
        -e "TLS_IP=$BindAddress" `
        -e "TLS_HOSTNAME=rehab.local" `
        --mount "type=bind,source=$CertDir,target=/certs" `
        --mount "type=bind,source=$tlsScript,target=/work/generate-tls.sh,readonly" `
        alpine:3.22.1 sh -c "apk add --no-cache openssl >/dev/null && sh /work/generate-tls.sh"
    if ($LASTEXITCODE -ne 0) { Fail "生成局域网 TLS 证书失败" }
}

$projectNameLine = Get-Content $EnvFile | Where-Object { $_ -like "COMPOSE_PROJECT_NAME=*" } | Select-Object -First 1
$projectName = if ($projectNameLine) { $projectNameLine.Substring("COMPOSE_PROJECT_NAME=".Length) } else { "rehab-lan" }
if (-not (Test-Path $MarkerFile)) {
    $volumes = & docker volume ls --format "{{.Name}}"
    if ($volumes -contains "${projectName}_mysql-data") {
        Fail "发现已有数据库卷但缺少安装标记；请先备份并按恢复流程接管"
    }
}

Write-Host "构建适配当前 CPU 架构的管理端和后端镜像..."
Invoke-Compose build --pull server admin
Invoke-Compose up -d mysql redis
Wait-MySql

if (-not (Test-Path $MarkerFile)) {
    # 登录接口当前限制密码为 4–16 字符；16 个十六进制字符提供 64 位临时随机熵。
    $adminPassword = New-RandomHex 8
    if ($adminPassword.Length -lt 12 -or $adminPassword.Length -gt 16) {
        Fail "生成的管理员密码不符合登录接口长度限制"
    }
    $bcryptOutput = ($adminPassword + "`n") |
        & docker run --rm -i --entrypoint java rehab-internal-server:latest `
            -Dloader.main=cn.iocoder.yudao.server.PasswordHashCli `
            -cp /app/app.jar org.springframework.boot.loader.PropertiesLauncher
    $adminHash = ($bcryptOutput | Select-Object -Last 1).Trim()
    if (-not $adminHash) { Fail "生成管理员 BCrypt 密码失败" }

    $sql = "UPDATE system_users SET password='$adminHash', updater='lan-installer', update_time=NOW() WHERE tenant_id=1 AND username='admin' AND deleted=b'0';"
    $sql | & docker compose --env-file $EnvFile -f $ComposeFile exec -T mysql sh -c `
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE" --batch --raw'
    if ($LASTEXITCODE -ne 0) { Fail "写入随机管理员密码失败" }

    $caHash = (Get-FileHash -Algorithm SHA256 (Join-Path $CertDir "ca.crt")).Hash.ToLowerInvariant()
    $firstLogin = @(
        "康复管理系统首次登录信息",
        "",
        "访问地址：https://${BindAddress}:8443",
        "备用主机名：https://rehab.local:8443（需路由器 DNS 或设备 hosts 支持）",
        "CA 下载：http://${BindAddress}:8080/ca.crt",
        "CA 文件 SHA-256：$caHash",
        "租户：工作室内部",
        "用户名：admin",
        "临时随机密码：$adminPassword",
        "",
        "首次登录后请立即修改密码，并删除本文件。"
    )
    [IO.File]::WriteAllLines($FirstLoginFile, $firstLogin, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllLines($MarkerFile, @(
        "installed_at=$([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))",
        "bind_address=$BindAddress",
        "compose_project=$projectName"
    ), [Text.UTF8Encoding]::new($false))
}

Invoke-Compose up -d server admin
Wait-Https $BindAddress

Write-Host ""
Write-Host "PASS: 康复管理系统局域网部署完成"
Write-Host "访问地址：https://${BindAddress}:8443"
Write-Host "CA 下载：http://${BindAddress}:8080/ca.crt"
if (Test-Path $FirstLoginFile) {
    Write-Host "首次登录信息：$FirstLoginFile"
}
