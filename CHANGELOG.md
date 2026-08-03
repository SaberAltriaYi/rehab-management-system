<!-- Copyright (c) 2026 杨玺龙. -->

# Changelog

All notable product changes are recorded here. Product versions use semantic versioning; the human-readable software version is V1.0.

## [Unreleased]

### Added

- Add Excel patient import template, duplicate-safe batch import, failure workbook and full patient-list export.
- Generate 17-page V4.1 DOCX/PDF assessment reports with patient metadata and raw module values only; interpretation, risk and training-advice sections remain blank for professional review.
- Add simplified patient course attendance by patient, active plan, training date and optional note without changing task completion or progress.
- Add visual metric icons to the rehabilitation workspace, home dashboard and operations dashboard.
- Add encrypted full-store transfer packages and automatic destination backup before full database/attachment replacement.
- Add desktop launcher backend entry and secure built-in super-administrator username/password settings.

### Fixed

- Split the desktop administrator password generator from 48-character infrastructure-secret generation.
- Generate a 16-character temporary administrator password compatible with the login and password-change contract.
- Allow affected `desktop-v1.0.0-preview.1` installations to log in with their existing 48-character temporary password and then change it, without reinstalling or deleting Docker volumes.
- Escape all assessment report HTML values and keep machine secrets, TLS material, logs, caches and backups out of cross-device transfer packages.

## [1.0.0] - 2026-07-31

### Added

- Patient records, episodes, therapist assignment and rehabilitation workflow management.
- Structured posture, mobility, movement-screening, balance, body-composition and specialist assessments.
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
- Copyright owner 杨玺龙, sole developer status, completion date 2026-07-30, non-employment status and unpublished status are recorded.
- SFMA, FMS and NASM-CES method content has no confirmed authorization or other use basis and is excluded from the software-copyright claim and identification materials.
