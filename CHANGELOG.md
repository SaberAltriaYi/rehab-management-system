<!-- Copyright (c) 2026 [软件著作权人名称]. -->

# Changelog

All notable product changes are recorded here. Product versions use semantic versioning; the human-readable software version is V1.0.

## [1.0.0] - 2026-07-31

### Added

- Patient records, episodes, therapist assignment and rehabilitation workflow management.
- Structured static, NASM-CES, SFMA, FMS, YBT, body-composition and specialist assessments.
- Assessment quality control, risk classification, reports, plans, tasks, check-ins, progress, reassessment, follow-up, notifications and audit records.
- DOCX/PDF report generation and internal operations dashboards.
- Signed one-click LAN deployment for macOS, Linux, Windows and Docker-compatible NAS hosts.
- Software-copyright preparation inventory, ownership notices and sensitive-material gate.

### Changed

- Unified the formal software name as “运动康复评估与业务管理系统”, short name as “康复管理系统”, and version as V1.0 / 1.0.0.
- Removed product-facing Yudao promotional text while retaining upstream attribution and MIT licenses.
- Replaced outdated rehabilitation module placeholder descriptions with the delivered feature scope.
- Removed tracked demo credentials, API encryption sample keys, statistics identifiers and map keys from product configuration.

### Security

- AI remains disabled in V1.0.
- Deployment creates random infrastructure secrets and a random temporary administrator password locally.
- MySQL, Redis and the Java service are not exposed directly to member devices.
- Repository and release checks reject private keys, credentials, backups, first-login files and runtime test output.

### Known boundaries

- V1.0 is intended for trusted internal LAN use.
- There is no independently delivered appointment-calendar module.
- Final copyright owner, completion date, first-publication status and contributor assignments require manual confirmation.
