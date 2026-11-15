# 2026-10-29 工作室内部版空库安装记录

用户已明确旧数据库全部为测试数据，允许直接新装并打开另一套空库。本次使用独立内部配置，不恢复旧资料、不清空旧卷、不扩大旧账号权限。不是正式商业发布，也不是三系统完整临床验收。

## 源码与构建前验证

- 内部隔离配置提交：`f26b62314f997ccbe5f58e34bbe1f3592af3c4c0`，已推送既有分支。
- 默认及studio-internal Rust各58通过/1原有真实Docker演练忽略；运行资源Node76/76。内部标识、目录、卷、镜像及端口隔离均有回归，默认正式配置未改变。
- 隔离后端构建成功，JAR SHA-256：`193061f8cef42870aa8f57a7dbeca500cb853e23fabc9e48e05c03fb336300e1`；不是复用现用业务JAR。
- 内部前端首次构建成功，1014文件；脱敏初始化快照重新生成，001–019基线及020–036实际执行，并验证空库恢复。尚不能将这些结果当作安装已完成。

## 安装前新增安全公告

生产依赖审计发现qs 6.16.0涉及中等级GHSA-6pq8-jhm2-2vcw。按[维护者公告](https://github.com/ljharb/qs/security/advisories/GHSA-6pq8-jhm2-2vcw)最小更新至6.16.1，同时统一直接依赖与覆盖版本。现有传输使用默认数组格式，未因此宣称系统存在已证实可利用的服务端崩溃路径。

锁文件仅修改qs相关条目；包管理器机械删除的19个Linux glibc/musl元数据块已恢复，未做无关升级。冻结安装通过。追加3项合成回归覆盖两种comma编码分支、不可调用toString、空原型对象、原默认传输的0/负数/小数/中文。管理端最终100/100，生产依赖审计无已知漏洞；需要按修补后的最终提交重新构建，不能包装修补前dist。

## 安装和验收状态

安装目标：`/Applications/康复管理系统内部版.app`；新数据目录为`~/Library/Application Support/com.saberaltriayi.rehab.internal/`，Compose项目rehab-studio-internal，默认127.0.0.1:18080/18443。原目录、旧卷及已有应用保留。

本记录建立时尚未完成最终安装/启动。后续须追加：冻结提交与前端凭据、运行资源完整性、实际安装包校验值、安装目录签名/可运行性、四服务健康、严格TLS访问、零患者和36条迁移账本、正常登录/初始密码更改与关闭AI。密码仅在私有运行目录与启动器显示，不写入本文或Git。

运动评估和HEP仍须单独验证授权、独立空库/工作区与回传；不能仅以四个主系统服务健康宣称全部完成。旧安装文件仍待新版安装验收后移入废纸篓，不删除数据或必要回滚版本。

## 本次最终实际结果

- 最终制品源码：`584f096e6117796263bd26a2305d42a901fc05ed`，安全修补后重新构建内部前端1014文件，凭据匹配此SHA。类型检查通过；100项前端测试及生产依赖审计通过。
- [常规CI40739841789](https://github.com/SaberAltriaYi/rehab-management-system/actions/runs/40739841789)六项全部成功：backend/frontend/business-integration/mobile/deployment/powershell。正式桌面工作流不作为内部安装批准，也未放宽商业门槛。
- 真实随机隔离项目`rehab-desktop-e2e-a396e11cf0fb`验证通过：四服务、UI/proxy、严格TLS、匿名拒绝，patient|migration=0|36。脚本已清理自己的容器/随机卷，未动旧卷。
- 首次未显式签名的应用未通过资源签名校验，未安装该产物。使用`APPLE_SIGNING_IDENTITY=-`重新打包完成本机ad-hoc签名；最终应用与安装后应用`codesign --verify --deep --strict`均成功。没有冒充Developer ID或公证；Apple公证未执行。
- 实际安装并启动`/Applications/康复管理系统内部版.app`，应用标识`com.saberaltriayi.rehab.internal`。本机生成独立私有配置和初始凭据，16位管理员临时密码文件为0600；密码未输出到聊天、报告或Git。
- 最终DMG：`desktop/build/installers/studio-internal-20261029/康复管理系统内部版_1.0.0_aarch64.dmg`，SHA-256 `74c085105d9bfc9c00fda7ed52da7d6e5390481ee9e6127d79bf3e68d445e65a`，同目录有SHA256SUMS.txt。这是本机Apple Silicon内部包，未生成Windows或Intel新包，没有公开Release。
- 实际新项目`rehab-studio-internal`的mysql/redis/server/admin全部healthy；18080/18443仅绑定127.0.0.1，数据库和后端无主机发布端口，AI关闭，镜像及四卷独立。1044个安装运行资源逐项哈希匹配；安装后`.installed`是本机元数据，不能用“纯分发资源目录”校验误判其额外存在为制品损坏。
- 新库实查：rehab_patient物理记录0，internal_schema_history记录36。HTTPS用本机证书严格校验返回200；未登录患者接口返回业务401且无data。不读患者行，不调用AI，不以健康检查代替正常登录。
- 两份精确旧DMG已经用macOS trash移入`/Users/saber/.Trash/`，原路径不存在，可从废纸篓恢复。未删除旧.app、测试数据库、附件、证书、备份、Git历史或其他项目；新运行服务不依赖这两份旧DMG。

## 尚未完成

本次完成的是**新主系统的空库内部安装与启动**，不是整个S1–S5上线完成。正常管理员首次登录/修改密码由用户在本机安全页面完成，不能为自动化注入会话或绕过证书/验证码。运动评估及HEP尚未改接新库/新身份，仍需单独部署与授权闭环；CRM/ERP等可选候选模块不因本次默认主系统打包而自动启用。源代码中已知的运动收件箱RR/签署并发与完整业务/临床验收仍按检查点推进。实际患者正式使用前保留这些门槛。
