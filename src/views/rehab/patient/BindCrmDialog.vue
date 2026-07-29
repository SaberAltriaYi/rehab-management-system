<template>
  <Dialog v-model="dialogVisible" title="CRM 绑定" width="560px">
    <el-alert
      v-if="bindingInfo"
      :title="`当前状态：${bindingInfo.bindStatus || 'unbound'}${bindingInfo.crmCustomerId ? `，CRM客户：${bindingInfo.crmCustomerName || '-'}（ID: ${bindingInfo.crmCustomerId}）` : ''}`"
      type="info"
      :closable="false"
      class="mb-12px"
    />

    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item label="患者" prop="patientName">
        <el-input v-model="formData.patientName" disabled />
      </el-form-item>
      <el-form-item label="CRM 客户" prop="crmCustomerId">
        <el-select
          v-model="formData.crmCustomerId"
          filterable
          clearable
          class="!w-full"
          placeholder="请选择 CRM 客户"
        >
          <el-option
            v-for="item in customerOptions"
            :key="item.id"
            :label="`${item.name || '未命名'}（ID: ${item.id}${item.mobile ? ` / ${item.mobile}` : ''}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-text type="info">如列表无目标客户，请先在 CRM 模块创建客户，再回到此处绑定。</el-text>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="formData.syncMessage" type="textarea" :rows="2" placeholder="可填写同步备注" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button
        v-if="bindingInfo?.bindStatus && bindingInfo.bindStatus !== 'unbound'"
        type="danger"
        plain
        :loading="formLoading"
        @click="handleUnbind"
      >
        解绑
      </el-button>
      <el-button type="primary" :loading="formLoading" @click="handleBind">绑 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import {
  bindRehabPatientCrm,
  checkRehabPatientCrmConflict,
  getRehabPatientCrmBinding,
  RehabPatientCrmBindingVO,
  unbindRehabPatientCrm
} from '@/api/rehab/patient'
import { getCustomerSimpleList } from '@/api/crm/customer'
import { FormRules } from 'element-plus'

defineOptions({ name: 'RehabBindCrmDialog' })

const message = useMessage()

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const bindingInfo = ref<RehabPatientCrmBindingVO>()
const customerOptions = ref<Array<{ id: number; name?: string; mobile?: string }>>([])

const formData = ref({
  patientId: undefined as number | undefined,
  patientName: '',
  crmCustomerId: undefined as number | undefined,
  syncMessage: ''
})

const formRules = reactive<FormRules>({
  crmCustomerId: [{ required: true, message: '请输入 CRM 客户 ID', trigger: 'change' }]
})

const loadBinding = async () => {
  if (!formData.value.patientId) return
  bindingInfo.value = await getRehabPatientCrmBinding(formData.value.patientId)
}

const loadCustomers = async () => {
  try {
    const data = await getCustomerSimpleList()
    customerOptions.value = Array.isArray(data) ? data : []
  } catch {
    customerOptions.value = []
    message.warning('CRM 客户列表读取失败，请确认 CRM 模块已启用且有权限')
  }
}

const open = async (data: { patientId: number; patientName: string }) => {
  dialogVisible.value = true
  formData.value = {
    patientId: data.patientId,
    patientName: data.patientName,
    crmCustomerId: undefined,
    syncMessage: ''
  }
  await Promise.all([loadBinding(), loadCustomers()])
  if (bindingInfo.value?.crmCustomerId) {
    formData.value.crmCustomerId = bindingInfo.value.crmCustomerId
  }
}

defineExpose({ open })

const emit = defineEmits(['success'])

const handleBind = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate()
  if (!valid) return

  formLoading.value = true
  try {
    const conflict = await checkRehabPatientCrmConflict({
      patientId: formData.value.patientId,
      crmCustomerId: formData.value.crmCustomerId!
    })
    if (conflict?.conflict) {
      message.warning('检测到 CRM 绑定冲突，系统将标记为 conflict')
    }

    await bindRehabPatientCrm({
      patientId: formData.value.patientId!,
      crmCustomerId: formData.value.crmCustomerId!,
      bindSource: 'manual',
      syncStatus: 'manual',
      syncMessage: formData.value.syncMessage
    })
    message.success('绑定成功')
    await loadBinding()
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const handleUnbind = async () => {
  if (!formData.value.patientId) return
  await message.confirm('确认解绑当前 CRM 关联吗？')
  formLoading.value = true
  try {
    await unbindRehabPatientCrm({
      patientId: formData.value.patientId,
      remark: '手动解绑'
    })
    message.success('解绑成功')
    await loadBinding()
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
