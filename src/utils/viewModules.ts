/**
 * 内部康复版仅编译实际开放的后台页面。
 *
 * BPM 表单设计器和 Infra 可视化表单构建器依赖已停止维护的 wangEditor 4，
 * AI 页面也按内部版发布策略关闭。将允许范围写成静态 glob 可以确保这些源码
 * 不会进入生产产物，而不只是依赖菜单权限做运行时隐藏。
 */
export const viewModules = import.meta.glob([
  '../views/crm/**/*.{vue,tsx}',
  '../views/infra/**/*.{vue,tsx}',
  '!../views/infra/build/**/*.{vue,tsx}',
  '../views/rehab/**/*.{vue,tsx}',
  '!../views/rehab/ai-*/**/*.{vue,tsx}',
  '../views/system/**/*.{vue,tsx}'
])
