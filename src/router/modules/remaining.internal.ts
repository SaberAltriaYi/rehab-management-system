import { Layout } from '@/utils/routerHelper'

const { t } = useI18n()

/**
 * 工作室内部生产版的固定路由。
 *
 * 数据库菜单仍由 routerHelper 动态加载；这里只保留登录、错误页、个人资料、
 * 康复业务与当前启用的 CRM/Infra 详情页。这样 AI、BPM、商城、支付和 IoT
 * 等已停用模块不会因为上游固定路由而被编译进发布产物。
 */
const remainingRouter: AppRouteRecordRaw[] = [
  {
    path: '/redirect',
    component: Layout,
    name: 'Redirect',
    children: [
      {
        path: '/redirect/:path(.*)',
        name: 'Redirect',
        component: () => import('@/views/Redirect/Redirect.vue'),
        meta: {}
      }
    ],
    meta: { hidden: true, noTagsView: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/index',
    name: 'Home',
    meta: {},
    children: [
      {
        path: 'index',
        component: () => import('@/views/Home/Index.vue'),
        name: 'Index',
        meta: {
          title: t('router.home'),
          icon: 'ep:home-filled',
          noCache: false,
          affix: true
        }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    name: 'UserInfo',
    meta: { hidden: true },
    children: [
      {
        path: 'profile',
        component: () => import('@/views/Profile/Index.vue'),
        name: 'Profile',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:user',
          title: t('common.profile')
        }
      },
      {
        path: 'notify-message',
        component: () => import('@/views/system/notify/my/index.vue'),
        name: 'MyNotifyMessage',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:message',
          title: '我的站内信'
        }
      }
    ]
  },
  {
    path: '/dict',
    component: Layout,
    name: 'dict',
    meta: { hidden: true },
    children: [
      {
        path: 'type/data/:dictType',
        component: () => import('@/views/system/dict/data/index.vue'),
        name: 'SystemDictData',
        meta: {
          title: '字典数据',
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          activeMenu: '/system/dict'
        }
      }
    ]
  },
  {
    path: '/rehab',
    component: Layout,
    name: 'rehab',
    meta: { hidden: true },
    children: [
      {
        path: 'patient/detail/:id',
        component: () => import('@/views/rehab/patient/detail/index.vue'),
        name: 'RehabPatientDetail',
        meta: {
          title: '患者详情',
          noCache: true,
          hidden: true,
          canTo: true,
          activeMenu: '/rehab/patient'
        }
      },
      {
        path: 'assessment/create',
        component: () => import('@/views/rehab/assessment/create/index.vue'),
        name: 'RehabAssessmentCreate',
        meta: {
          title: '新建评估',
          noCache: true,
          hidden: true,
          canTo: true,
          activeMenu: '/rehab/assessment'
        }
      },
      {
        path: 'assessment/edit/:id',
        component: () => import('@/views/rehab/assessment/create/index.vue'),
        name: 'RehabAssessmentEdit',
        meta: {
          title: '编辑评估',
          noCache: true,
          hidden: true,
          canTo: true,
          activeMenu: '/rehab/assessment'
        }
      },
      {
        path: 'assessment/detail/:id',
        component: () => import('@/views/rehab/assessment/detail/index.vue'),
        name: 'RehabAssessmentDetail',
        meta: {
          title: '评估详情',
          noCache: true,
          hidden: true,
          canTo: true,
          activeMenu: '/rehab/assessment'
        }
      },
      {
        path: 'plan/create',
        component: () => import('@/views/rehab/plan/create/index.vue'),
        name: 'RehabPlanCreate',
        meta: {
          title: '新建计划',
          noCache: true,
          hidden: true,
          canTo: true,
          activeMenu: '/rehab/plan'
        }
      },
      {
        path: 'plan/edit/:id',
        component: () => import('@/views/rehab/plan/edit/index.vue'),
        name: 'RehabPlanEdit',
        meta: {
          title: '编辑计划',
          noCache: true,
          hidden: true,
          canTo: true,
          activeMenu: '/rehab/plan'
        }
      },
      {
        path: 'plan/detail/:id',
        component: () => import('@/views/rehab/plan/detail/index.vue'),
        name: 'RehabPlanDetail',
        meta: {
          title: '计划详情',
          noCache: true,
          hidden: true,
          canTo: true,
          activeMenu: '/rehab/plan'
        }
      }
    ]
  },
  {
    path: '/codegen',
    component: Layout,
    name: 'CodegenEdit',
    meta: { hidden: true },
    children: [
      {
        path: 'edit',
        component: () => import('@/views/infra/codegen/EditTable.vue'),
        name: 'InfraCodegenEditTable',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: '修改生成配置',
          activeMenu: 'infra/codegen/index'
        }
      }
    ]
  },
  {
    path: '/job',
    component: Layout,
    name: 'JobL',
    meta: { hidden: true },
    children: [
      {
        path: 'job-log',
        component: () => import('@/views/infra/job/logger/index.vue'),
        name: 'InfraJobLog',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: '调度日志',
          activeMenu: 'infra/job/index'
        }
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/Login/Login.vue'),
    name: 'Login',
    meta: {
      hidden: true,
      title: t('router.login'),
      noTagsView: true
    }
  },
  {
    path: '/403',
    component: () => import('@/views/Error/403.vue'),
    name: 'NoAccess',
    meta: { hidden: true, title: '403', noTagsView: true }
  },
  {
    path: '/404',
    component: () => import('@/views/Error/404.vue'),
    name: 'NoFound',
    meta: { hidden: true, title: '404', noTagsView: true }
  },
  {
    path: '/500',
    component: () => import('@/views/Error/500.vue'),
    name: 'Error',
    meta: { hidden: true, title: '500', noTagsView: true }
  },
  {
    path: '/crm',
    component: Layout,
    name: 'CrmCenter',
    meta: { hidden: true },
    children: [
      {
        path: 'clue/detail/:id',
        name: 'CrmClueDetail',
        meta: {
          title: '线索详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/clue'
        },
        component: () => import('@/views/crm/clue/detail/index.vue')
      },
      {
        path: 'customer/detail/:id',
        name: 'CrmCustomerDetail',
        meta: {
          title: '客户详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/customer'
        },
        component: () => import('@/views/crm/customer/detail/index.vue')
      },
      {
        path: 'business/detail/:id',
        name: 'CrmBusinessDetail',
        meta: {
          title: '商机详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/business'
        },
        component: () => import('@/views/crm/business/detail/index.vue')
      },
      {
        path: 'contract/detail/:id',
        name: 'CrmContractDetail',
        meta: {
          title: '合同详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/contract'
        },
        component: () => import('@/views/crm/contract/detail/index.vue')
      },
      {
        path: 'receivable-plan/detail/:id',
        name: 'CrmReceivablePlanDetail',
        meta: {
          title: '回款计划详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/receivable-plan'
        },
        component: () => import('@/views/crm/receivable/plan/detail/index.vue')
      },
      {
        path: 'receivable/detail/:id',
        name: 'CrmReceivableDetail',
        meta: {
          title: '回款详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/receivable'
        },
        component: () => import('@/views/crm/receivable/detail/index.vue')
      },
      {
        path: 'contact/detail/:id',
        name: 'CrmContactDetail',
        meta: {
          title: '联系人详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/contact'
        },
        component: () => import('@/views/crm/contact/detail/index.vue')
      },
      {
        path: 'product/detail/:id',
        name: 'CrmProductDetail',
        meta: {
          title: '产品详情',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/product'
        },
        component: () => import('@/views/crm/product/detail/index.vue')
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/Error/404.vue'),
    name: '',
    meta: {
      title: '404',
      hidden: true,
      breadcrumb: false
    }
  }
]

export default remainingRouter
