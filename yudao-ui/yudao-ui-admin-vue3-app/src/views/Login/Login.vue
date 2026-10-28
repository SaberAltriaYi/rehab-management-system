<template>
  <div
    :class="prefixCls"
    class="relative h-[100%] lt-md:px-10px lt-sm:px-10px lt-xl:px-10px lt-xl:px-10px"
  >
    <div class="relative mx-auto h-full flex">
      <div
        :class="`${prefixCls}__left flex-1 bg-gray-500 bg-opacity-20 relative p-30px lt-xl:hidden overflow-x-hidden overflow-y-auto`"
      >
        <!-- 左上角的 logo + 系统标题 -->
        <div class="relative flex items-center text-white">
          <img alt="" class="mr-10px h-48px w-48px" src="@/assets/imgs/logo.png" />
          <span class="text-20px font-bold">
            {{ underlineToHump(appStore.getTitle) }}
            <small class="ml-6px text-12px font-normal">{{ softwareVersion }}</small>
          </span>
        </div>
        <!-- 左边的背景图 + 欢迎语 -->
        <div class="h-[calc(100%-60px)] flex items-center justify-center">
          <TransitionGroup
            appear
            enter-active-class="animate__animated animate__bounceInLeft"
            tag="div"
          >
            <img key="1" alt="" class="w-350px" src="@/assets/svgs/login-box-bg.svg" />
            <div key="2" class="text-3xl text-white">{{ t('login.welcome') }}</div>
            <div key="3" class="mt-5 text-14px font-normal text-white">
              {{ t('login.message') }}
            </div>
          </TransitionGroup>
        </div>
      </div>
      <div
        class="relative flex-1 p-30px dark:bg-[var(--login-bg-color)] lt-sm:p-10px overflow-x-hidden overflow-y-auto"
      >
        <!-- 右上角的主题、语言选择 -->
        <div
          class="flex items-center justify-between at-2xl:justify-end at-xl:justify-end"
          style="color: var(--el-text-color-primary);"
        >
          <div class="flex items-center at-2xl:hidden at-xl:hidden">
            <img alt="" class="mr-10px h-48px w-48px" src="@/assets/imgs/logo.png" />
            <span class="text-20px font-bold">
              {{ underlineToHump(appStore.getTitle) }}
              <small class="ml-6px text-12px font-normal">{{ softwareVersion }}</small>
            </span>
          </div>
          <div class="flex items-center justify-end space-x-10px h-48px">
            <ThemeSwitch />
            <LocaleDropdown />
          </div>
        </div>
        <!-- 右边的登录界面 -->
        <Transition appear enter-active-class="animate__animated animate__bounceInRight">
          <div
            class="m-auto h-[calc(100%-60px)] w-[100%] flex items-center at-2xl:max-w-500px at-lg:max-w-500px at-md:max-w-500px at-xl:max-w-500px"
          >
            <div class="auth-panel m-auto h-auto w-full p-20px lt-xl:(rounded-3xl light:bg-white)">
              <div class="entry-switcher" role="tablist" aria-label="入口选择">
                <el-button :type="activeMode === 'system' ? 'primary' : 'default'" @click="selectMode('system')">
                  主系统登录
                </el-button>
                <el-button :type="activeMode === 'assessment' ? 'primary' : 'default'" @click="selectMode('assessment')">
                  运动评估登录
                </el-button>
              </div>
              <div v-if="activeMode === 'register'" class="registration-card">
                <div class="registration-choice">
                  <h2>选择注册方式</h2>
                  <p>个人账号用于登录已获授权的机构；机构账号需要先提交申请并等待平台审核。</p>
                  <el-button type="primary" plain class="w-full" @click="selectRegistration('personal')">注册新账号</el-button>
                  <el-button type="success" plain class="w-full" @click="selectRegistration('organization')">申请机构账号</el-button>
                </div>
                <RegisterForm v-if="registrationKind === 'personal'" @back-login="selectMode('system')" />
                <RegistrationForm v-else-if="registrationKind === 'organization'" @back-login="selectMode('system')" />
                <el-button v-if="registrationKind" link type="primary" class="registration-back" @click="selectMode('register')">返回注册方式</el-button>
              </div>
              <LoginForm
                v-else
                class="login-card"
                :default-redirect="activeMode === 'assessment' ? '/rehab/assessment' : '/index'"
              />
              <p v-if="activeMode !== 'register'" class="entry-hint">
                {{ activeMode === 'assessment' ? '登录后进入运动评估工作台，权限仍由机构账号控制。' : '机构员工使用统一账号登录主系统。' }}
              </p>
              <el-button v-if="activeMode !== 'register'" link type="primary" class="register-entry" @click="selectMode('register')">
                注册新账号 / 申请机构账号
              </el-button>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { computed, ref } from 'vue'
import { underlineToHump } from '@/utils'

import { useDesign } from '@/hooks/web/useDesign'
import { useAppStore } from '@/store/modules/app'
import { ThemeSwitch } from '@/layout/components/ThemeSwitch'
import { LocaleDropdown } from '@/layout/components/LocaleDropdown'

import { LoginForm, RegisterForm } from './components'
import { LoginStateEnum, useLoginState } from './components/useLogin'
import RegistrationForm from '@/views/saas/RegistrationForm.vue'

defineOptions({ name: 'Login' })

const { t } = useI18n()
const appStore = useAppStore()
const { getPrefixCls } = useDesign()
const softwareVersion = import.meta.env.VITE_APP_VERSION || 'V1.0'
const prefixCls = getPrefixCls('login')
const { currentRoute, replace } = useRouter()
type EntryMode = 'system' | 'assessment' | 'register'
const activeMode = computed<EntryMode>(() => {
  if (currentRoute.value.query.mode === 'register') return 'register'
  if (currentRoute.value.query.entry === 'assessment' || currentRoute.value.query.redirect === '/rehab/assessment') return 'assessment'
  return 'system'
})
const registrationKind = ref<'personal' | 'organization' | ''>('')
const { setLoginState } = useLoginState()

function selectMode(mode: EntryMode) {
  setLoginState(LoginStateEnum.LOGIN)
  if (mode === 'register') {
    registrationKind.value = ''
    replace({ path: '/login', query: { mode: 'register' } })
    return
  }
  replace({
    path: '/login',
    query: { entry: mode, redirect: mode === 'assessment' ? '/rehab/assessment' : '/index' }
  })
}

function selectRegistration(kind: 'personal' | 'organization') {
  registrationKind.value = kind
  setLoginState(kind === 'personal' ? LoginStateEnum.REGISTER : LoginStateEnum.LOGIN)
}
</script>

<style lang="scss" scoped>
$prefix-cls: #{$namespace}-login;

.#{$prefix-cls} {
  overflow: auto;

  &__left {
    &::before {
      position: absolute;
      top: 0;
      left: 0;
      z-index: -1;
      width: 100%;
      height: 100%;
      background-image: url('@/assets/svgs/login-bg.svg');
      background-position: center;
      background-repeat: no-repeat;
      content: '';
    }
  }
}
</style>

.auth-panel {
  max-width: 520px;
  border-radius: 24px;
  background: color-mix(in srgb, var(--el-bg-color) 94%, transparent);
  box-shadow: 0 20px 60px rgb(26 65 59 / 10%);
}

.entry-switcher { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-bottom: 22px; }
.entry-switcher .el-button { margin-left: 0; min-height: 40px; white-space: normal; }
.login-card { padding: 0 !important; }
.registration-card { padding: 4px 2px; }
.registration-choice { margin-bottom: 18px; text-align: center; }
.registration-choice h2 { margin: 4px 0 8px; color: var(--el-text-color-primary); }
.registration-choice p { margin: 0 0 14px; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
.registration-choice .el-button { margin: 6px 0 0; }
.registration-back, .register-entry { display: block; margin: 10px auto 0; }
.entry-hint { margin: 14px 0 0; color: var(--el-text-color-secondary); font-size: 13px; text-align: center; }
@media (max-width: 520px) { .entry-switcher { grid-template-columns: 1fr; } }

<style lang="scss">
.dark .login-form {
  .el-divider__text {
    background-color: var(--login-bg-color);
  }

  .el-card {
    background-color: var(--login-bg-color);
  }
}
</style>
