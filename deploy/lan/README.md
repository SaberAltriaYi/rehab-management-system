# 运动康复评估与业务管理系统 V1.0：局域网一键部署

本目录用于把运动康复评估与业务管理系统（简称“康复管理系统”）V1.0 部署到一台中央主机。手机、平板和电脑不安装业务客户端，只需与主机
处于同一可信 Wi-Fi/局域网并使用浏览器访问。

支持的部署主机：

- macOS Apple Silicon / Intel；
- Windows 10/11 + Docker Desktop；
- Linux x86_64 / arm64；
- 支持 Docker Compose 的 NAS。

## 一键安装

macOS / Linux：

```bash
chmod +x install.sh rehabctl.sh
./install.sh
```

无法自动识别正确网卡时：

```bash
./install.sh 192.168.1.10
```

Windows PowerShell：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\install.ps1
```

安装器会：

1. 检测局域网 IPv4；
2. 生成互不相同的 MySQL、Redis 和备份密钥；
3. 生成包含局域网 IP、`rehab.local` 的内部 HTTPS 证书；
4. 构建适配当前 CPU 的 Docker 镜像；
5. 创建空业务数据库并执行 001–019 迁移；
6. 清理演示数据并关闭 AI 和未交付模块；
7. 生成符合登录接口限制的 16 位临时随机管理员密码，不使用 `admin123`；
8. 完成数据库检查与 HTTPS 冒烟测试。

首次登录信息只写入 `deploy/lan/FIRST_LOGIN.txt`。修改密码后立即删除该文件。

## 成员设备访问

1. 在成员设备打开 `http://主机IP:8080/ca.crt`；
2. 与主机安装界面显示的 CA 文件 SHA-256 核对；
3. 将 CA 安装到设备系统信任存储；
4. 访问 `https://主机IP:8443`。

证书同时包含 `rehab.local`，但该名称必须由路由器局域网 DNS 或各设备 hosts 解析到主机 IP。
在未配置统一 DNS 时，IP 地址是保证可用的入口。

必须为中央主机设置 DHCP 地址保留或静态 IP，否则主机 IP 变化后需要重新生成证书。

## 日常命令

macOS / Linux：

```bash
./rehabctl.sh status
./rehabctl.sh start
./rehabctl.sh stop
./rehabctl.sh logs
./rehabctl.sh check
./rehabctl.sh backup
./rehabctl.sh update
```

Windows 使用同名 `.ps1`。Windows 的加密备份和恢复命令需要 WSL2 与 Docker Desktop WSL 集成。

## 校验 GitHub Release

Release 同时提供压缩包、`.sha256` 和 Sigstore 签名 bundle。先执行 SHA-256 校验，再用
[Cosign](https://docs.sigstore.dev/cosign/system_config/installation/) 验证签名：

```bash
shasum -a 256 -c sports-rehab-management-system-1.0.0.tar.gz.sha256
cosign verify-blob \
  --bundle sports-rehab-management-system-1.0.0.tar.gz.sigstore.json \
  --certificate-identity-regexp \
  'https://github.com/SaberAltriaYi/rehab-management-system/.github/workflows/release.yml@refs/tags/v1.0.0' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  sports-rehab-management-system-1.0.0.tar.gz
```

只有校验通过后才解压和运行安装器。

## 安全边界

- 不得在路由器设置公网端口转发；
- MySQL、Redis、Java 后端不映射主机端口；
- 一人一号，日常不得共用管理员；
- AI 保持关闭；
- 备份文件和 `backup.key` 分开存放；
- `deploy/internal/.env`、证书私钥和首次登录文件不得提交 GitHub。
