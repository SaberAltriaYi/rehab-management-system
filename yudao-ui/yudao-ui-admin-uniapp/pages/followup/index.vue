<template>
  <view style="padding:24rpx">
    <view class="card">
      <textarea v-model="form.content" placeholder="输入随访备注" style="height:160rpx;border:1rpx solid #e2e8f0;border-radius:10rpx;padding:12rpx"></textarea>
      <picker mode="selector" :range="visibilityList" @change="onVisibilityChange">
        <view style="margin-top:10rpx;color:#64748b">可见性：{{ form.visibilityType }}</view>
      </picker>
      <button style="margin-top:12rpx" class="btn-primary" @click="submit">保存备注</button>
    </view>

    <view class="card" v-for="item in list" :key="item.id">
      <view>{{ item.content }}</view>
      <view style="font-size:24rpx;color:#64748b;margin-top:6rpx">{{ item.noteType }} | {{ item.visibilityType }} | {{ item.createTime }}</view>
    </view>
    <button class="btn-plain" @click="loadMore">加载更多</button>
  </view>
</template>

<script>
import { createFollowup, getFollowupPage } from '../../api/followup'

export default {
  data() {
    return {
      patientId: null,
      episodeId: null,
      pageNo: 1,
      pageSize: 10,
      list: [],
      visibilityList: ['internal', 'patient_visible'],
      form: {
        noteType: 'followup',
        visibilityType: 'internal',
        content: ''
      }
    }
  },
  onLoad(query) {
    this.patientId = Number(query.patientId)
    this.fetchPage(true)
  },
  methods: {
    onVisibilityChange(e) {
      this.form.visibilityType = this.visibilityList[e.detail.value]
    },
    async submit() {
      if (!this.form.content) {
        uni.showToast({ title: '请填写备注', icon: 'none' })
        return
      }
      await createFollowup({
        patientId: this.patientId,
        episodeId: this.episodeId,
        noteType: this.form.noteType,
        visibilityType: this.form.visibilityType,
        content: this.form.content
      })
      uni.showToast({ title: '已保存', icon: 'success' })
      this.form.content = ''
      this.pageNo = 1
      this.fetchPage(true)
    },
    loadMore() {
      this.pageNo += 1
      this.fetchPage(false)
    },
    async fetchPage(reset) {
      const page = await getFollowupPage({ patientId: this.patientId, pageNo: this.pageNo, pageSize: this.pageSize })
      const rows = page.list || []
      this.list = reset ? rows : this.list.concat(rows)
    }
  }
}
</script>
