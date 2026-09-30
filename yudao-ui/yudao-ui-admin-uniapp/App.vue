<template>
  <slot />
</template>

<script>
export default {
  onLaunch() {
    console.log('rehab-admin-mini launch')
  }
}
</script>

<style>
page {
  background: #f5f7fb;
  color: #1f2937;
  font-size: 28rpx;
}
.card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.06);
}
.btn-primary {
  background: #0f766e;
  color: #ffffff;
}
.btn-plain {
  background: #eef2ff;
  color: #1e293b;
}
</style>
