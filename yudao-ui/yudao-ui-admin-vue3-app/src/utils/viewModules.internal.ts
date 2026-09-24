/**
 * 工作室内部版的动态页面白名单。
 *
 * 显式编译康复、CRM 与已验证的少量 BPM、ERP、Member 页面。其余 AI、
 * 第三方报表/流程设计器、商城、支付、物联网等模块不进入内部产物。
 */
export const viewModules = import.meta.glob([
  // 已有后端和隔离 DDL 验收的机构运营页面；其余第三方设计器仍不编译。
  '../views/bpm/category/**/*.{vue,tsx}',
  '../views/bpm/task/todo/**/*.{vue,tsx}',
  '../views/bpm/task/done/**/*.{vue,tsx}',
  '../views/erp/product/**/*.{vue,tsx}',
  '../views/erp/stock/**/*.{vue,tsx}',
  '../views/member/config/**/*.{vue,tsx}',
  '../views/member/signin/**/*.{vue,tsx}',
  '../views/member/user/**/*.{vue,tsx}',
  '../views/report/goview/**/*.{vue,tsx}',
  '../views/crm/**/*.{vue,tsx}',
  '../views/infra/**/*.{vue,tsx}',
  '!../views/infra/build/**/*.{vue,tsx}',
  '../views/rehab/**/*.{vue,tsx}',
  '!../views/rehab/ai-*/**/*.{vue,tsx}',
  '../views/system/**/*.{vue,tsx}'
])
