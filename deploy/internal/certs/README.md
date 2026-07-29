# 内部 HTTPS 证书目录

运行 `deploy/internal/generate-tls.sh` 生成内部 CA 与服务器证书。除本文件外，本目录内容均被
Git 忽略。将 `ca.crt` 安装到获准成员设备的系统信任存储，不得分发 `ca.key` 或
`server.key`。
