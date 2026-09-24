<template>
  <view v-if="visible" class="captcha-overlay" @tap.self="close">
    <view class="captcha-panel" @tap.stop>
      <view class="panel-heading">
        <view><text class="panel-title">安全验证</text><text class="panel-subtitle">拖动滑块完成拼图</text></view>
        <text class="close-button" @tap="close">×</text>
      </view>
      <view id="captcha-area" class="challenge-area">
        <image v-if="captcha" class="challenge-background" :src="`data:image/png;base64,${captcha.originalImageBase64}`" mode="scaleToFill" />
        <image v-if="captcha" class="challenge-piece" :src="`data:image/png;base64,${captcha.jigsawImageBase64}`" :style="pieceStyle" mode="scaleToFill" />
        <view v-if="loading" class="loading-cover">正在获取验证图片…</view>
      </view>
      <view id="captcha-track" class="captcha-track">
        <text v-if="dragX < 4 && !checking" class="track-hint">按住滑块向右拖动</text>
        <view
          id="captcha-knob"
          class="captcha-knob"
          :class="{ dragging: dragging, disabled: loading || checking }"
          :style="{ left: `${dragX}px` }"
          @touchstart.stop="onTouchStart"
          @touchmove.stop.prevent="onTouchMove"
          @touchend.stop="onTouchEnd"
          @touchcancel.stop="onTouchCancel"
        ><text>{{ checking ? '…' : '➜' }}</text></view>
      </view>
      <view class="captcha-footer">
        <text class="captcha-error">{{ errorMessage }}</text>
        <text class="refresh-button" @tap="refresh">换一张</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, ref, watch } from 'vue'
import { apiRaw } from '@/lib/api'
import { encryptCaptcha } from '@/lib/captcha-crypto'

interface CaptchaData { originalImageBase64: string; jigsawImageBase64: string; token: string; secretKey: string }
interface CaptchaResponse { repCode: string; repMsg?: string; repData?: CaptchaData }

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ close: []; verified: [captchaVerification: string] }>()
const instance = getCurrentInstance()
const captcha = ref<CaptchaData | null>(null)
const loading = ref(false)
const checking = ref(false)
const dragging = ref(false)
const dragX = ref(0)
const areaWidthPx = ref(0)
const areaHeightPx = ref(0)
const trackWidthPx = ref(0)
const knobWidthPx = ref(47)
const startClientX = ref(0)
const startDragX = ref(0)
const errorMessage = ref('')

const pieceWidthPx = computed(() => areaWidthPx.value > 0 ? areaWidthPx.value * 47 / 310 : 0)
const pieceStyle = computed(() => ({
  left: `${Math.min(dragX.value, Math.max(0, areaWidthPx.value - pieceWidthPx.value))}px`,
  width: `${pieceWidthPx.value}px`,
  height: `${areaHeightPx.value}px`,
}))

watch(() => props.visible, (isVisible) => {
  if (isVisible) {
    dragX.value = 0
    errorMessage.value = ''
    void loadCaptcha()
  }
})

function measure(): void {
  const query = uni.createSelectorQuery().in(instance?.proxy as any)
  query.select('#captcha-area').boundingClientRect((rect: any) => {
    if (rect) { areaWidthPx.value = rect.width || 0; areaHeightPx.value = rect.height || 0 }
  })
  query.select('#captcha-track').boundingClientRect((rect: any) => { if (rect) trackWidthPx.value = rect.width || 0 })
  query.select('#captcha-knob').boundingClientRect((rect: any) => { if (rect) knobWidthPx.value = rect.width || knobWidthPx.value })
  query.exec()
}

async function loadCaptcha(): Promise<void> {
  loading.value = true
  checking.value = false
  captcha.value = null
  dragX.value = 0
  errorMessage.value = ''
  try {
    const response = await apiRaw<CaptchaResponse>('/admin-api/system/captcha/get', {
      method: 'POST', auth: false, data: { captchaType: 'blockPuzzle' },
    })
    if (response.repCode !== '0000' || !response.repData) throw new Error(response.repMsg || '验证码暂不可用。')
    captcha.value = response.repData
    setTimeout(measure, 60)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '获取验证码失败，请重试。'
  } finally {
    loading.value = false
  }
}

function onTouchStart(event: any): void {
  if (loading.value || checking.value || !captcha.value) return
  const touch = event.touches?.[0]
  if (!touch) return
  dragging.value = true
  startClientX.value = Number(touch.clientX) || 0
  startDragX.value = dragX.value
}

function onTouchMove(event: any): void {
  if (!dragging.value) return
  const touch = event.touches?.[0]
  if (!touch) return
  const maxX = Math.max(0, trackWidthPx.value - knobWidthPx.value)
  const nextX = startDragX.value + (Number(touch.clientX) || 0) - startClientX.value
  dragX.value = Math.max(0, Math.min(maxX, nextX))
}

async function onTouchEnd(): Promise<void> {
  if (!dragging.value) return
  dragging.value = false
  if (!captcha.value || areaWidthPx.value <= 0 || dragX.value < 2) return
  checking.value = true
  errorMessage.value = ''
  const point = { x: dragX.value * 310 / areaWidthPx.value, y: 5.0 }
  try {
    const response = await apiRaw<CaptchaResponse>('/admin-api/system/captcha/check', {
      method: 'POST', auth: false,
      data: {
        captchaType: 'blockPuzzle',
        token: captcha.value.token,
        pointJson: encryptCaptcha(JSON.stringify(point), captcha.value.secretKey),
      },
    })
    if (response.repCode !== '0000') throw new Error(response.repMsg || '拼图位置不正确，请再试一次。')
    const captchaVerification = encryptCaptcha(
      `${captcha.value.token}---${JSON.stringify(point)}`,
      captcha.value.secretKey,
    )
    emit('verified', captchaVerification)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '验证失败，请再试一次。'
    await loadCaptcha()
  } finally {
    checking.value = false
  }
}

function onTouchCancel(): void { dragging.value = false; dragX.value = 0 }
function refresh(): void { if (!loading.value && !checking.value) void loadCaptcha() }
function close(): void { emit('close') }
</script>

<style scoped>
.captcha-overlay { position: fixed; z-index: 9999; inset: 0; display: flex; align-items: center; justify-content: center; padding: 30rpx; background: rgba(13,23,39,.52); }
.captcha-panel { width: 100%; max-width: 680rpx; padding: 32rpx; border-radius: 28rpx; background: #fff; box-shadow: 0 30rpx 90rpx rgba(0,0,0,.18); }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 24rpx; }
.panel-title, .panel-subtitle { display: block; }
.panel-title { color: #182230; font-size: 34rpx; font-weight: 700; }
.panel-subtitle { margin-top: 8rpx; color: #768196; font-size: 24rpx; }
.close-button { padding: 0 8rpx; color: #7b8493; font-size: 48rpx; line-height: 1; }
.challenge-area { position: relative; width: 100%; height: 310rpx; overflow: hidden; border-radius: 16rpx; background: #edf1f7; }
.challenge-background, .challenge-piece { position: absolute; top: 0; left: 0; height: 100%; }
.challenge-background { width: 100%; }
.challenge-piece { z-index: 2; }
.loading-cover { position: absolute; z-index: 3; inset: 0; display: flex; align-items: center; justify-content: center; background: rgba(245,247,251,.88); color: #596579; }
.captcha-track { position: relative; height: 88rpx; margin-top: 24rpx; overflow: hidden; border-radius: 16rpx; background: #edf1f7; }
.track-hint { position: absolute; top: 50%; left: 50%; color: #8b94a2; font-size: 23rpx; transform: translate(-50%,-50%); white-space: nowrap; }
.captcha-knob { position: absolute; z-index: 1; top: 0; display: flex; width: 94rpx; height: 88rpx; align-items: center; justify-content: center; border: 1rpx solid #d9e3f2; border-radius: 16rpx; background: #fff; box-shadow: 0 5rpx 16rpx rgba(34,74,135,.12); color: #276bdb; font-size: 36rpx; touch-action: none; }
.captcha-knob.dragging { background: #f4f8ff; }
.captcha-knob.disabled { opacity: .72; }
.captcha-footer { display: flex; min-height: 52rpx; align-items: center; justify-content: space-between; margin-top: 14rpx; }
.captcha-error { flex: 1; color: #b42318; font-size: 22rpx; line-height: 1.4; }
.refresh-button { padding: 8rpx 4rpx 8rpx 18rpx; color: #276bdb; font-size: 23rpx; }
</style>
