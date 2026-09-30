import router from '@/router'

declare global {
  interface Window {
    _hmt: Array<[string, string]>
  }
}

// 用于 router push
window._hmt = window._hmt || []
// HM_ID
const HM_ID = import.meta.env.VITE_APP_BAIDU_CODE
;(function () {
  // 有值的时候，才开启
  if (!HM_ID) {
    return
  }
  const hm = document.createElement('script')
  hm.src = 'https://hm.baidu.com/hm.js?' + HM_ID
  const s = document.getElementsByTagName('script')[0]
  if (s?.parentNode) {
    s.parentNode.insertBefore(hm, s)
  } else {
    document.head.appendChild(hm)
  }
})()

router.afterEach(function (to) {
  if (!HM_ID) {
    return
  }
  window._hmt.push(['_trackPageview', to.fullPath])
})
