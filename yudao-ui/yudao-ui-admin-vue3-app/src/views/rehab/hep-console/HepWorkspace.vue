<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import request from '@/config/axios'
import { useUserStore } from '@/store/modules/user'

type Entry = Record<string, any>
type Section = 'patients' | 'library' | 'prescription' | 'risks' | 'feedback' |
  'templates' | 'overview' | 'bindings' | 'membership' | 'approval' | 'mapping' |
  'plans' | 'sessions' | 'compliance' | 'sync'
const user=useUserStore()
const route=useRoute()
const allowed=(p:string)=>user.getPermissions.has(p) || user.getPermissions.has('*:*:*')
const canTreat=computed(()=>allowed('rehab:hep:prescription:create'))
const canAdmin=computed(()=>allowed('rehab:hep:integration:admin'))
const canReview=computed(()=>allowed('rehab:hep:feedback:review'))
const canIssueCore=computed(()=>allowed('rehab:hep:prescription:issue'))
const canApproveAction=computed(()=>allowed('rehab:hep:exercise:review'))
const tabs=computed(()=>[
  ...(canTreat.value?[{key:'patients',label:'我的患者'},{key:'library',label:'动作库'},
    {key:'prescription',label:'开方与下发'},{key:'templates',label:'训练模板'}]:[]),
  ...(canReview.value?[{key:'risks',label:'风险审核'},{key:'feedback',label:'患者反馈'}]:[]),
  ...(canAdmin.value?[{key:'overview',label:'机构总览'},{key:'bindings',label:'患者绑定'},
    {key:'membership',label:'训练权益'},{key:'approval',label:'动作审核'},{key:'mapping',label:'动作映射'},
    {key:'plans',label:'计划管理'},{key:'sessions',label:'训练打卡'},{key:'compliance',label:'依从性'},{key:'sync',label:'同步状态'}]:[])
] as {key:Section;label:string}[])
const active=ref<Section>('patients')
const pending=ref(false)
const error=ref('')
const records=ref<Entry[]>([])
const patients=ref<Entry[]>([])
const exercises=ref<Entry[]>([])
const drafts=ref<Entry[]>([])
const adminActions=ref<Entry[]>([])
const mappings=ref<Entry[]>([])
const metrics=ref<Entry>({})
const syncData=ref<Entry>({})
const daysWindow=ref(7)
const draftPrescriptionId=ref('')
const clinician={corePatientId:'',coreEpisodeId:'',coreExerciseId:'',startDate:new Date().toISOString().slice(0,10),
  sets:2,reps:10,allowedPain:3,code:''}
const clinical=ref({...clinician})
const prescription=ref({patientId:'',corePrescriptionId:''})
const newAction=ref({name:'',instructions:'',precautions:'',contraindications:'',painStopRules:'疼痛加重时停止',
  defaultSets:2,defaultReps:10})
const binding=ref({externalPatientId:'',externalEpisodeId:'',authSubject:'',therapistSubject:'',membershipStatus:'ACTIVE'})
const mapping=ref({coreExerciseId:'',hepExerciseId:''})
const reviewDecision=ref('FOLLOW_UP')
const reviewNote=ref('')
const membership=ref({patientId:'',status:'ACTIVE'})
const exerciseTemplate=ref({name:'',exerciseId:'',sets:3,reps:10})
const templateIssue=ref({templateId:'',patientId:'',externalEpisodeId:'',date:new Date().toISOString().slice(0,10)})

const labels:Record<string,string>={patientId:'患者',corePatientId:'Core 患者',coreEpisodeId:'疗程',therapistId:'治疗师',
  name:'动作名称',id:'编号',status:'状态',createdAt:'创建时间',startDate:'开始日期',version:'版本',
  planId:'训练计划',membershipStatus:'会员状态',exerciseDrafts:'待审核动作',activePlans:'在用计划',
  riskOpen:'风险待办',feedbackPending:'反馈待办',completedToday:'今日打卡',patients:'服务患者',
  exercises:'已审核动作',templates:'模板',syncBacklog:'待同步',kind:'异常类别',severity:'风险等级',
  category:'反馈类型',note:'备注',reviewStatus:'审核状态',completionPercent:'完成率 %',
  scheduled:'计划次数',completed:'已完成',startedAt:'开始时间',completedAt:'完成时间'}
const statuses:Record<string,string>={PENDING:'待处理',OPEN:'待审核',APPROVED:'已审核',ACTIVE:'正常',
  DRAFT:'草稿',COMPLETED:'完成',ISSUED:'已下发',PAUSED:'暂停',EXPIRED:'过期',REVIEWED:'已复核',
  IN_PROGRESS:'执行中',SYNCED:'已同步',RETRY:'等待重试',DEAD:'投递失败'}
const format=(v:any)=>v===undefined||v===null||v===''?'—':statuses[String(v)] || String(v)
const cols=computed(()=>({
 patients:['patientId','membershipStatus'],library:['name','id','instructions','default_sets','default_reps'],
 risks:['patientId','kind','severity','status','createdAt'],
 feedback:['patientId','category','note','reviewStatus'],
 approval:['name','id','status','creator','createdAt'],
 plans:['patientId','id','startDate','version','status'],
 sessions:['patientId','planId','status','startedAt','completedAt'],
 compliance:['patientId','scheduled','completed','completionPercent']
} as Record<string,string[]>)[active.value] || [])

async function get(path:string):Promise<any>{
 return request.get({url:'/rehab/hep-workspace/'+path})
}
async function post(path:string,data:Entry={}):Promise<any>{
 return request.post({url:'/rehab/hep-workspace/'+path,data})
}
async function put(path:string,data:Entry):Promise<any>{
 return request.put({url:'/rehab/hep-workspace/'+path,data})
}
async function load(){
 if(!tabs.value.some(t=>t.key===active.value))return
 pending.value=true;error.value=''
 try{
  const tab=active.value
  records.value=[]
  if(tab==='patients'||tab==='library'||tab==='risks'||tab==='feedback'||tab==='templates'){
    records.value=await get('therapist/'+(tab==='library'?'exercises':tab))
    if(tab==='library')drafts.value=await get('therapist/drafts')
    if(tab==='templates')exercises.value=await get('therapist/exercises')
  }
  else if(tab==='overview')metrics.value=await get('admin/overview')
  else if(tab==='sync')syncData.value=await get('admin/sync')
  else if(tab==='mapping'){
    const [current,approved]=await Promise.all([get('admin/mappings'),get('admin/exercises')])
    mappings.value=current;adminActions.value=approved
  }
  else if(tab==='prescription'){
    const [p,e]=await Promise.all([get('therapist/patients'),get('therapist/exercises')])
    patients.value=p;exercises.value=e
    if(!prescription.value.patientId && p.length)prescription.value.patientId=p[0].patientId
  }
  else if(tab==='bindings'||tab==='membership')records.value=await get('admin/patients')
  else if(tab==='approval')adminActions.value=await get('admin/exercises')
  else if(tab==='compliance')records.value=await get('admin/compliance?days='+daysWindow.value)
  else records.value=await get('admin/'+tab)
 }catch(e){error.value=e instanceof Error?e.message:'请求失败，请核对 HEP 服务或账号权限'}
 finally{pending.value=false}
}
async function switchTab(tab:Section){active.value=tab;await load()}
async function act(path:string,payload:Entry={},method:'POST'|'PUT'='POST'){
 pending.value=true;error.value=''
 try{
  const out=method==='POST'?await post(path,payload):await put(path,payload)
  ElMessage.success('操作已保存')
  await load()
  return out
 }catch(e){error.value=e instanceof Error?e.message:'操作失败，请检查角色、租户和处方状态';return null}
 finally{pending.value=false}
}
async function createAction(){
 if(!newAction.value.name.trim())return ElMessage.warning('请输入动作名称')
 await act('therapist/exercises',newAction.value)
}
async function uploadMedia(exerciseId:string,event:Event){
 const file=(event.target as HTMLInputElement).files?.[0]
 if(!file)return
 if(file.size>16*1024*1024)return ElMessage.warning('单个文件不能超过 16MB')
 if(!['image/png','image/jpeg','video/mp4','video/webm'].includes(file.type))
   return ElMessage.warning('仅支持 PNG、JPG、MP4、WebM 文件')
 const data=new FormData()
 data.append('file',file)
 pending.value=true
 try{
  await request.upload({url:'/rehab/hep-workspace/therapist/exercises/'+exerciseId+'/media',data})
  ElMessage.success('动作媒体已上传；机构管理员审核动作后供患者使用')
  await load()
 }catch(e){error.value=e instanceof Error?e.message:'上传失败'}
 finally{pending.value=false}
}
async function createCoreExercise(){
 const payload={exerciseCode:'HEP-'+Date.now(),name:newAction.value.name,
  instructionText:newAction.value.instructions,contraindications:newAction.value.contraindications,
  licenseStatus:'PENDING'}
  try{
    const id=await request.post({url:'/rehab/hep/exercises',data:payload}) as number
    clinical.value.coreExerciseId=String(id)
    ElMessage.success('Core 动作草稿已创建，须由授权审核人批准后开方')
  }catch(e){error.value=e instanceof Error?e.message:'创建 Core 动作失败'}
}
async function reviewCoreExercise(){
 if(!/^[1-9]\d*$/.test(clinical.value.coreExerciseId))return
 await request.post({url:'/rehab/hep/exercises/'+clinical.value.coreExerciseId+'/review'})
 ElMessage.success('Core 动作已审核')
}
async function createCorePrescription(){
 const p=clinical.value
 if(![p.corePatientId,p.coreEpisodeId,p.coreExerciseId].every(x=>/^[1-9]\d*$/.test(x)))
   return ElMessage.warning('请填写合法 Core 患者、疗程和审核动作 ID')
 if(!p.startDate || p.sets<1 || p.reps<1)return ElMessage.warning('请填写训练日期和剂量')
 try{
  const id=await request.post({url:'/rehab/hep/prescriptions',data:{
    patientId:Number(p.corePatientId),episodeId:Number(p.coreEpisodeId),startDate:p.startDate,
    endDate:p.startDate,items:[{exerciseId:Number(p.coreExerciseId),trainingDayNo:1,groupCode:'A',
      dosageJson:JSON.stringify({sets:p.sets,reps:p.reps,allowedPain:p.allowedPain})}]
  }}) as number
  draftPrescriptionId.value=String(id)
  prescription.value.corePrescriptionId=String(id)
  ElMessage.success('Core 处方草稿已创建，待治疗师审核签发')
 }catch(e){error.value=e instanceof Error?e.message:'Core 处方创建失败'}
}
async function issueCorePrescription(){
 if(!/^[1-9]\d*$/.test(draftPrescriptionId.value))return
 try{
  await ElMessageBox.confirm('确认患者、疗程、动作与剂量已由负责治疗师核对？签发后不可直接覆盖。','临床处方签发确认',{type:'warning'})
  await request.post({url:'/rehab/hep/prescriptions/'+draftPrescriptionId.value+'/issue'})
  ElMessage.success('Core 处方已签发，现在可以导入 HEP')
 }catch(e){if(e!=='cancel')error.value=e instanceof Error?e.message:'处方签发失败'}
}
async function importCore(){
 if(!prescription.value.patientId || !/^[1-9]\d*$/.test(prescription.value.corePrescriptionId))
   return ElMessage.warning('请选择已绑定的 HEP 患者并输入已签发的 Core 处方编号')
 const result=await act('therapist/import-core',{patientId:prescription.value.patientId,
   corePrescriptionId:Number(prescription.value.corePrescriptionId)})
 if(result?.planId)ElMessage.success('HEP 已导入计划 '+String(result.planId).slice(0,8))
}
async function createTemplate(){
 const v=exerciseTemplate.value
 if(!v.name.trim()||!v.exerciseId)return ElMessage.warning('请选择审核动作并填写模板名称')
 await act('therapist/templates',{name:v.name,days:[{
   dayCode:'A',exercises:[{exerciseId:v.exerciseId,sets:v.sets,reps:v.reps}]
 }]})
}
async function issueFromTemplate(){
 const v=templateIssue.value
 if(!v.templateId||!v.patientId||!v.externalEpisodeId||!v.date)
   return ElMessage.warning('请先填写患者、疗程、模板和日期')
 await act('therapist/templates/'+v.templateId+'/issue',{
   patientId:v.patientId,externalEpisodeId:v.externalEpisodeId,schedule:{A:v.date}
 })
}
async function approve(row:Entry){
 await ElMessageBox.confirm('确认动作已完成审核，批准机构用于训练？','动作审核',{type:'warning'})
 await act('admin/exercises/'+row.id+'/approve')
}
async function review(kind:'risks'|'feedback',row:Entry){
 await ElMessageBox.confirm('此操作记录治疗师审核意见，请确认已检查风险和患者信息。','人工审核',{type:'warning'})
 await act('therapist/'+kind+'/'+row.id+'/review',{decision:reviewDecision.value,note:reviewNote.value||'已人工审核'})
}
onMounted(()=>{
 const id=route.query.patientId
 if(typeof id==='string' && /^[1-9]\d*$/.test(id)){
  clinical.value.corePatientId=id;binding.value.externalPatientId=id
 }
 if(tabs.value.length){active.value=tabs.value[0].key;void load()}
})
</script>
<template>
  <ContentWrap title="HEP 家庭康复 · 一体化工作台">
    <p class="hep-note">当前页面直接使用 Rehab Core 登录及租户权限，数据由 Core 安全转接至 HEP。患者继续在自己的训练端打卡，不允许治疗师代替患者填写完成记录。</p>
    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" class="mb-12px"/>
    <el-alert v-if="tabs.length===0" title="当前账号未获得 HEP 权限，请机构管理员授予治疗师或 HEP 管理权限。" type="warning" :closable="false"/>
    <el-tabs v-else v-model="active" @tab-change="(value)=>switchTab(value as Section)">
      <el-tab-pane v-for="tab in tabs" :key="tab.key" :name="tab.key" :label="tab.label"/>
    </el-tabs>
    <div v-if="tabs.length" class="hep-head">
      <span>{{tabs.find(t=>t.key===active)?.label}}</span>
      <el-button :loading="pending" @click="load">刷新</el-button>
    </div>
    <el-row v-if="active==='overview'&&canAdmin" :gutter="12">
      <el-col v-for="(value,key) in metrics" :key="String(key)" :xs="12" :sm="8" :md="6">
        <el-card shadow="never" class="mb-12px"><div class="hep-note">{{labels[String(key)]||key}}</div><h2>{{value}}</h2></el-card>
      </el-col>
    </el-row>
    <template v-if="active==='prescription'&&canTreat">
      <el-card shadow="never" header="一、创建 Core 临床处方（由负责治疗师签发）" class="mb-12px">
        <el-form label-width="120px">
          <el-form-item label="Core 患者 ID"><el-input v-model="clinical.corePatientId" placeholder="从患者详情进入可自动带入"/></el-form-item>
          <el-form-item label="当前疗程 ID"><el-input v-model="clinical.coreEpisodeId"/></el-form-item>
          <el-form-item label="Core 已审核动作"><el-input v-model="clinical.coreExerciseId" placeholder="由动作审核人批准后使用"/></el-form-item>
          <el-form-item label="训练日期"><el-date-picker v-model="clinical.startDate" type="date" value-format="YYYY-MM-DD"/></el-form-item>
          <el-form-item label="剂量">
            <el-input-number v-model="clinical.sets" :min="1" :max="20"/><span class="mx-8px">组 ×</span>
            <el-input-number v-model="clinical.reps" :min="1" :max="1000"/><span class="ml-8px">次</span>
          </el-form-item>
          <el-form-item label="疼痛限制"><el-input-number v-model="clinical.allowedPain" :min="0" :max="10"/></el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="pending" @click="createCorePrescription">创建处方草稿</el-button>
            <el-button v-if="canIssueCore" type="success" :disabled="!draftPrescriptionId" @click="issueCorePrescription">审核并签发</el-button>
            <el-tag v-if="draftPrescriptionId">草稿：{{draftPrescriptionId}}</el-tag>
          </el-form-item>
        </el-form>
      </el-card>
      <el-card shadow="never" header="二、从 Core 签发处方导入 HEP" class="mb-12px">
        <p class="hep-note">导入前须由机构管理员建立动作映射。相同处方版本重复导入不会创建第二份计划。</p>
        <el-form label-width="120px">
          <el-form-item label="已绑定患者">
            <el-select v-model="prescription.patientId" filterable placeholder="选择已分配给我的 HEP 患者" style="width:100%">
              <el-option v-for="p in patients" :key="p.patientId" :value="p.patientId" :label="'患者 '+p.patientId+' · '+format(p.membershipStatus)"/>
            </el-select>
          </el-form-item>
          <el-form-item label="Core 处方 ID"><el-input v-model="prescription.corePrescriptionId" placeholder="已签发的 Core 处方编号"/></el-form-item>
          <el-form-item><el-button type="primary" :loading="pending" @click="importCore">导入已签发处方</el-button></el-form-item>
        </el-form>
      </el-card>
    </template>
    <el-card v-if="active==='library'&&canTreat" shadow="never" header="治疗师创建动作草稿" class="mb-12px">
      <el-form label-width="115px">
        <el-form-item label="动作名称"><el-input v-model="newAction.name"/></el-form-item>
        <el-form-item label="教学说明"><el-input v-model="newAction.instructions" type="textarea" :rows="2"/></el-form-item>
        <el-form-item label="注意事项"><el-input v-model="newAction.precautions" type="textarea"/></el-form-item>
        <el-form-item label="禁忌证"><el-input v-model="newAction.contraindications"/></el-form-item>
        <el-form-item label="默认剂量">
          <el-input-number v-model="newAction.defaultSets" :min="1" :max="20"/>
          <span class="mx-8px">组 ×</span>
          <el-input-number v-model="newAction.defaultReps" :min="1" :max="1000"/>
        </el-form-item>
        <el-form-item><el-button type="primary" :loading="pending" @click="createAction">创建 HEP 动作草稿</el-button>
          <el-button v-if="allowed('rehab:hep:exercise:create')" @click="createCoreExercise">同步创建 Core 动作草稿</el-button>
          <el-button v-if="canApproveAction && clinical.coreExerciseId" @click="reviewCoreExercise">审核 Core 动作</el-button>
        </el-form-item>
      </el-form>
      <el-divider content-position="left">我的草稿／教学视频上传</el-divider>
      <el-table :data="drafts" stripe>
        <el-table-column prop="name" label="草稿动作" min-width="160"/>
        <el-table-column prop="id" label="动作编号" min-width="190"/>
        <el-table-column label="上传图片或教学视频" min-width="280">
          <template #default="{row}">
            <input type="file" accept="image/jpeg,image/png,video/mp4,video/webm"
              :disabled="pending" @change="uploadMedia(String(row.id),$event)"/>
          </template>
        </el-table-column>
      </el-table>
      <p class="hep-note">通过 Core 安全上传接口存入 HEP 私有媒体目录，单文件不超过 16MB；机构管理员审核动作后才能用于患者处方。</p>
    </el-card>
    <el-card v-if="active==='templates'&&canTreat" shadow="never" header="治疗师训练模板">
      <p class="hep-note">从已审核的 HEP 动作创建常用 A 日训练模板，机构审核后方可下发。</p>
      <el-form label-width="120px">
        <el-form-item label="模板名称"><el-input v-model="exerciseTemplate.name"/></el-form-item>
        <el-form-item label="动作"><el-select v-model="exerciseTemplate.exerciseId" filterable style="width:100%" placeholder="选择审核动作">
          <el-option v-for="row in exercises" :key="row.id" :value="row.id" :label="row.name"/>
        </el-select></el-form-item>
        <el-form-item label="训练剂量"><el-input-number v-model="exerciseTemplate.sets" :min="1" :max="20"/>
          <span class="mx-8px">组 ×</span><el-input-number v-model="exerciseTemplate.reps" :min="1" :max="1000"/><span class="ml-8px">次</span></el-form-item>
        <el-form-item><el-button type="primary" :loading="pending" @click="createTemplate">保存模板草稿</el-button></el-form-item>
      </el-form>
      <el-table :data="records" stripe>
        <el-table-column prop="name" label="模板名称"/><el-table-column prop="version" label="版本"/>
        <el-table-column label="状态"><template #default="{row}">{{format(row.status)}}</template></el-table-column>
        <el-table-column label="操作" width="120"><template #default="{row}">
          <el-button v-if="row.status==='APPROVED'" type="primary" link @click="templateIssue.templateId=row.id">选择下发</el-button>
        </template></el-table-column>
      </el-table>
      <el-divider content-position="left">下发已审核模板</el-divider>
      <el-form label-width="120px">
        <el-form-item label="模板 ID"><el-input v-model="templateIssue.templateId" readonly/></el-form-item>
        <el-form-item label="HEP 患者 ID"><el-input v-model="templateIssue.patientId"/></el-form-item>
        <el-form-item label="Core 疗程 ID"><el-input v-model="templateIssue.externalEpisodeId"/></el-form-item>
        <el-form-item label="训练日期"><el-date-picker v-model="templateIssue.date" type="date" value-format="YYYY-MM-DD"/></el-form-item>
        <el-form-item><el-button type="primary" :loading="pending" @click="issueFromTemplate">下发模板</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card v-if="active==='bindings'&&canAdmin" shadow="never" header="绑定 Core 已存在患者" class="mb-12px">
      <p class="hep-note">患者、疗程、治疗师与患者会员账号必须在 Core 中有效；本页面不会创建新患者或重设密码。</p>
      <el-form label-width="135px">
        <el-form-item label="Core 患者 ID"><el-input v-model="binding.externalPatientId"/></el-form-item>
        <el-form-item label="Core 当前疗程"><el-input v-model="binding.externalEpisodeId"/></el-form-item>
        <el-form-item label="患者会员用户 ID"><el-input v-model="binding.authSubject"/></el-form-item>
        <el-form-item label="治疗师用户 ID"><el-input v-model="binding.therapistSubject"/></el-form-item>
        <el-form-item label="服务状态"><el-select v-model="binding.membershipStatus">
          <el-option value="ACTIVE" label="已开通"/><el-option value="PAUSED" label="暂停"/><el-option value="EXPIRED" label="已到期"/>
        </el-select></el-form-item>
        <el-form-item><el-button type="primary" :loading="pending" @click="act('admin/bind',binding)">校验并绑定</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card v-if="active==='membership'&&canAdmin" shadow="never" header="家庭训练准入">
      <p class="hep-note">仅变更 HEP 训练使用资格，不直接修改 Core 会员主数据。</p>
      <el-form label-width="120px">
        <el-form-item label="HEP 患者">
          <el-select v-model="membership.patientId" style="width:100%" filterable placeholder="选择患者">
            <el-option v-for="row in records" :key="row.patientId" :value="row.patientId"
              :label="'患者 '+row.patientId+' · '+format(row.membershipStatus)"/>
          </el-select>
        </el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="membership.status">
          <el-radio label="ACTIVE">正常</el-radio><el-radio label="PAUSED">暂停</el-radio>
          <el-radio label="EXPIRED">到期</el-radio>
        </el-radio-group></el-form-item>
        <el-form-item><el-button type="primary" :loading="pending" :disabled="!membership.patientId"
          @click="act('admin/membership/'+membership.patientId,{status:membership.status},'PUT')">更新训练资格</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card v-if="active==='mapping'&&canAdmin" shadow="never" header="Core ↔ HEP 动作映射">
      <p class="hep-note">需要人工确认动作一致性；系统不会按相似名称自行替换临床动作。</p>
      <el-form label-width="110px">
        <el-form-item label="Core 动作 ID"><el-input v-model="mapping.coreExerciseId"/></el-form-item>
        <el-form-item label="已审核 HEP 动作">
          <el-select v-model="mapping.hepExerciseId" filterable style="width:100%" placeholder="请选择">
            <el-option v-for="row in adminActions.filter(x=>x.status==='APPROVED')" :key="row.id" :value="row.id" :label="row.name+' / '+row.id"/>
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" :disabled="!mapping.coreExerciseId||!mapping.hepExerciseId" @click="act('admin/mappings',mapping)">保存映射</el-button>
          <el-button @click="load">刷新映射</el-button></el-form-item>
      </el-form>
      <el-table :data="mappings"><el-table-column prop="coreExerciseId" label="Core 动作"/><el-table-column prop="hepExerciseId" label="HEP 动作"/></el-table>
    </el-card>
    <el-card v-if="active==='sync'&&canAdmin" shadow="never" header="事件回传状态">
      <el-table :data="syncData.queue||[]"><el-table-column prop="sync_status" label="同步状态"/><el-table-column prop="total" label="数量"/></el-table>
    </el-card>
    <el-card v-if="active==='approval'&&canAdmin" shadow="never" header="机构动作审核">
      <el-table :data="adminActions" stripe>
        <el-table-column prop="name" label="动作"/><el-table-column prop="status" label="状态">
          <template #default="{row}">{{format(row.status)}}</template></el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{row}"><el-button v-if="row.status==='DRAFT'&&canApproveAction" type="primary" link @click="approve(row)">审核通过</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card v-if="active==='patients'&&canTreat" shadow="never" header="已分配的 HEP 患者">
      <el-table :data="records" stripe><el-table-column prop="patientId" label="患者 ID"/>
        <el-table-column label="服务状态"><template #default="{row}">{{format(row.membershipStatus)}}</template></el-table-column>
        <el-table-column label="操作" width="130"><template #default="{row}">
          <el-button type="primary" link @click="prescription.patientId=row.patientId;switchTab('prescription')">开方／导入</el-button>
        </template></el-table-column></el-table>
    </el-card>
    <el-card v-if="active==='risks'||active==='feedback'" shadow="never">
      <p class="hep-note">审核仅针对当前治疗师负责的患者；不是自动诊断。</p>
      <div class="hep-review">
        <el-select v-model="reviewDecision"><el-option value="FOLLOW_UP" label="需要随访"/>
          <el-option value="ACKNOWLEDGED" label="已知悉"/><el-option value="PLAN_ADJUSTMENT" label="调整处方"/></el-select>
        <el-input v-model="reviewNote" placeholder="填写审核意见" style="max-width:420px"/>
      </div>
      <el-table :data="records" stripe>
        <el-table-column prop="patientId" label="患者"/><el-table-column :prop="active==='risks'?'kind':'category'" label="类型"/>
        <el-table-column :prop="active==='risks'?'severity':'note'" label="说明"/>
        <el-table-column label="处理" width="105"><template #default="{row}">
          <el-button v-if="canReview&&(active==='risks'?row.status==='OPEN':row.reviewStatus==='PENDING')" type="primary" link
            @click="review(active as 'risks'|'feedback',row)">人工审核</el-button>
        </template></el-table-column>
      </el-table>
    </el-card>
    <el-card v-if="['plans','sessions','compliance'].includes(active)&&canAdmin" shadow="never">
      <div v-if="active==='compliance'" class="mb-12px">
        <el-radio-group v-model="daysWindow" @change="load"><el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button></el-radio-group>
      </div>
      <el-table :data="records" stripe><el-table-column v-for="key in cols" :key="key" :prop="key" :label="labels[key]||key" min-width="130">
        <template #default="{row}">{{format(row[key])}}</template>
      </el-table-column></el-table>
    </el-card>
  </ContentWrap>
</template>
<style scoped>
.hep-note{color:var(--el-text-color-secondary);line-height:1.7;font-size:13px;margin:2px 0 15px}
.hep-head{display:flex;align-items:center;justify-content:space-between;font-weight:600;margin:8px 0 14px}
.hep-review{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:14px}
</style>
