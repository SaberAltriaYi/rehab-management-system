<script lang="ts">
export default {
  onLaunch() {},
}
</script>

<style>
page {
  min-height: 100%;
  background: #f5f6fa;
  color: #182230;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang TC", "Noto Sans CJK TC", sans-serif;
  font-size: 28rpx;
  box-sizing: border-box;
}
view, text, button, input, textarea, image { box-sizing: border-box; }
button::after { border: 0; }
.page-shell { min-height: 100vh; padding: 28rpx 30rpx 56rpx; }
.muted { color: #768196; }
.card {
  margin-bottom: 22rpx;
  padding: 28rpx;
  border: 1rpx solid #e8ecf3;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 12rpx 36rpx rgba(28, 47, 78, 0.045);
}
.section-title { margin: 4rpx 0 20rpx; color: #182230; font-size: 32rpx; font-weight: 700; }
.primary-button {
  display: flex; align-items: center; justify-content: center; min-height: 90rpx;
  margin: 16rpx 0 0; border-radius: 18rpx; background: #276bdb; color: #fff;
  font-size: 29rpx; font-weight: 650;
}
.secondary-button {
  display: flex; align-items: center; justify-content: center; min-height: 82rpx;
  margin: 14rpx 0 0; border: 1rpx solid #dce5f2; border-radius: 16rpx;
  background: #fff; color: #245cae; font-size: 27rpx;
}
.notice-box {
  padding: 20rpx 22rpx; border: 1rpx solid #f3dfaa; border-radius: 18rpx;
  background: #fff8e7; color: #765715; line-height: 1.65;
}
.error-text { color: #b42318; line-height: 1.5; }
</style>
