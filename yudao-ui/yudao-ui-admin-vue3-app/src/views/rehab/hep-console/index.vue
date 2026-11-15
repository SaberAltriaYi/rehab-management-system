<script setup lang="ts">
import { ref,onMounted } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/config/axios'
import HepWorkspace from './HepWorkspace.vue'
type EventRow={event_id:string;event_type:string;episode_id:number;session_id:string;review_status:string;created_at:string}
const route=useRoute()
const patientId=ref('')
const rows=ref<EventRow[]>([])
const error=ref('')
const pending=ref(false)
const summary=ref<{periodDays:number;events:{eventType:string;total:number}[];pendingClinicalReview:number;interpretation:string}|null>(null)
const eventNames:Record<string,string>={HEP_SESSION_COMPLETED:'家庭训练完成',HEP_RISK_REPORTED:'风险上报',
 HEP_RISK_REVIEWED:'风险已审核',HEP_FEEDBACK_REVIEWED:'反馈已审核'}
const names:Record<string,string>={PENDING:'待审核',REVIEWED:'已复核'}
async function loadEvents(){
 const id=patientId.value.trim()
 if(!/^[1-9]\d{0,15}$/.test(id)){error.value='请输入有效 Core 患者编号';return}
 pending.value=true;error.value=''
 try{
  const [events,snapshot]=await Promise.all([
   request.get({url:'/rehab/hep-bridge/patients/'+id+'/events'}),
   request.get({url:'/rehab/hep-bridge/patients/'+id+'/summary'})
  ])
  rows.value=events as EventRow[];summary.value=snapshot as typeof summary.value
 }catch(e){error.value=e instanceof Error?e.message:'患者不属于当前负责治疗师，或数据不可用'}
 finally{pending.value=false}
}
async function review(row:EventRow){
 if(row.review_status!=='PENDING')return
 try{
  await request.post({url:'/rehab/hep-bridge/patients/'+patientId.value.trim()+'/events/'+encodeURIComponent(row.event_id)+'/review'})
  await loadEvents()
 }catch(e){error.value=e instanceof Error?e.message:'复核失败'}
}
onMounted(()=>{
 const id=route.query.patientId
 if(typeof id==='string'&&/^[1-9]\d{0,15}$/.test(id)){patientId.value=id;void loadEvents()}
})
</script>
<template>
  <HepWorkspace/>
  <ContentWrap title="HEP 执行回传与随访复评参考">
    <p class="help">这里展示 HEP 真实投递到 Core 的执行及风险事件，保留治疗师人工审核。打卡只能由患者在本人训练端执行，治疗师在本页面开方、管理、查看和审核。</p>
    <el-input v-model="patientId" placeholder="Core 患者 ID" style="max-width:300px"/>
    <el-button type="primary" :loading="pending" class="ml-12px" @click="loadEvents">查看患者记录</el-button>
    <el-alert v-if="error" class="mt-12px" type="warning" :title="error" :closable="false" show-icon/>
    <el-row v-if="summary" :gutter="12" class="mt-16px">
      <el-col :xs="12" :sm="8" :md="6"><el-card shadow="never">近 {{summary.periodDays}} 天</el-card></el-col>
      <el-col :xs="12" :sm="8" :md="6"><el-card shadow="never">待复核 {{summary.pendingClinicalReview}} 条</el-card></el-col>
      <el-col v-for="evt in summary.events" :key="evt.eventType" :xs="12" :sm="8" :md="6">
        <el-card shadow="never">{{eventNames[evt.eventType]||evt.eventType}}：{{evt.total}}</el-card>
      </el-col>
    </el-row>
    <el-table :data="rows" stripe class="mt-16px">
      <el-table-column label="事件类型" min-width="160"><template #default="{row}">{{eventNames[row.event_type]||row.event_type}}</template></el-table-column>
      <el-table-column prop="episode_id" label="疗程" width="95"/>
      <el-table-column prop="session_id" label="会话" min-width="210"/>
      <el-table-column label="临床复核" width="130"><template #default="{row}">{{names[row.review_status]||row.review_status}}</template></el-table-column>
      <el-table-column label="操作" width="120"><template #default="{row}">
        <el-button v-if="row.review_status==='PENDING'" type="primary" link
          v-hasPermi="['rehab:hep:feedback:review']" @click="review(row)">标记已复核</el-button>
      </template></el-table-column>
    </el-table>
    <p class="help">随访和复评结论仍需治疗师在主系统签署，事件统计不自动形成医学诊断。</p>
  </ContentWrap>
</template>
<style scoped>.help{color:var(--el-text-color-secondary);font-size:13px;line-height:1.7}</style>
