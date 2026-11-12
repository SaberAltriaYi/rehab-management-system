/** Explain a missing HEP deployment without hiding real HTTP failures. */
export function hepLoadError(error: unknown): string {
  const message = error instanceof Error ? error.message : 'HEP 请求失败'
  const status = message.match(/HTTP\s*(404|503)\b/i)?.[1]
  return status
    ? `HEP 接口暂不可用（HTTP ${status}）。请确认 Core 037 已正式部署、桥接已启用，并完成机构角色授权；菜单可见不代表服务已就绪。`
    : message
}
