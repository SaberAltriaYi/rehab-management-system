<template>
  <slot />
</template>

<script>
export default {
  onLaunch() {
    console.log('rehab-patient-mini launch')
  }
}
</script>

<style>
page {
  background: #f8fafc;
  color: #0f172a;
  font-size: 28rpx;
}
.card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 8rpx 20rpx rgba(15, 23, 42, 0.05);
}
.btn-primary {
  background: #0f766e;
  color: #ffffff;
}
.btn-plain {
  background: #e2e8f0;
  color: #1e293b;
}
</style>
