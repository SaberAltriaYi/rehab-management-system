; Copyright (c) 2026 杨玺龙
; 数据只能由启动器危险区的精确确认流程删除。
; 即使用户在 Tauri 标准卸载器中选择删除应用数据，也强制保留配置、备份和业务卷。
!macro NSIS_HOOK_PREUNINSTALL
  StrCpy $DeleteAppDataCheckboxState 0
!macroend
