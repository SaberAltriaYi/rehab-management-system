/**
 * 工作室内部版的动态页面白名单。
 *
 * 只编译数据库当前启用的四类业务页面，避免已停用的 AI、BPM、商城、
 * 支付、物联网等模块及其依赖进入生产静态资源。
 */
export const viewModules = import.meta.glob([
  '../views/crm/**/*.{vue,tsx}',
  '../views/infra/**/*.{vue,tsx}',
  '!../views/infra/build/**/*.{vue,tsx}',
  '../views/rehab/**/*.{vue,tsx}',
  '!../views/rehab/ai-*/**/*.{vue,tsx}',
  '../views/system/**/*.{vue,tsx}'
])
