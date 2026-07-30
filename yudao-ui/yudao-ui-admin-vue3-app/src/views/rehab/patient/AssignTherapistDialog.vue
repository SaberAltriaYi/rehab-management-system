<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item label="患者" prop="patientName">
        <el-input v-model="formData.patientName" disabled />
      </el-form-item>
      <el-form-item v-if="mode === 'assign'" label="角色类型" prop="roleType">
        <el-radio-group v-model="formData.roleType">
          <el-radio label="primary">主责</el-radio>
          <el-radio label="collaborator">协作</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="mode === 'transfer'" label="当前主责">
        <el-input v-model="formData.fromTherapistName" disabled />
      </el-form-item>
      <el-form-item :label="mode === 'assign' ? '治疗师' : '转交给'" prop="therapistUserId">
        <el-select v-model="formData.therapistUserId" class="!w-full" filterable>
          <el-option
            v-for="user in therapistOptions"
            :key="user.id"
            :label="`${user.nickname}(${user.id})`"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="mode === 'assign' ? '分配原因' : '转交原因'">
        <el-input v-model="formData.reason" placeholder="请输入原因" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="formData.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submit">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { assignRehabTherapist, transferRehabTherapist } from '@/api/rehab/patient'
import * as UserApi from '@/api/system/user'
import { FormRules } from 'element-plus'

defineOptions({ name: 'RehabAssignTherapistDialog' })

const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formRef = ref()
const mode = ref<'assign' | 'transfer'>('assign')

const formData = ref({
  patientId: undefined as number | undefined,
  patientName: '',
  roleType: 'primary',
  therapistUserId: undefined as number | undefined,
  reason: '',
  remark: '',
  fromTherapistUserId: undefined as number | undefined,
  fromTherapistName: ''
})

const therapistOptions = ref<UserApi.UserVO[]>([])

const formRules = reactive<FormRules>({
  therapistUserId: [{ required: true, message: '请选择治疗师', trigger: 'change' }],
  roleType: [{ required: true, message: '请选择角色类型', trigger: 'change' }]
})

const loadTherapists = async () => {
  const users = await UserApi.getSimpleUserList()
  therapistOptions.value = users || []
}

const open = async (
  data: {
    patientId: number
    patientName: string
    fromTherapistUserId?: number
    fromTherapistName?: string
  },
  type: 'assign' | 'transfer'
) => {
  dialogVisible.value = true
  mode.value = type
  dialogTitle.value = type === 'assign' ? '分配治疗师' : '转交治疗师'
  formData.value = {
    patientId: data.patientId,
    patientName: data.patientName,
    roleType: 'primary',
    therapistUserId: undefined,
    reason: '',
    remark: '',
    fromTherapistUserId: data.fromTherapistUserId,
    fromTherapistName: data.fromTherapistName || ''
  }
  await loadTherapists()
}

defineExpose({ open })

const emit = defineEmits(['success'])

const submit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate()
  if (!valid) return

  formLoading.value = true
  try {
    if (mode.value === 'assign') {
      await assignRehabTherapist({
        patientId: formData.value.patientId!,
        therapistUserId: formData.value.therapistUserId!,
        roleType: formData.value.roleType as 'primary' | 'collaborator',
        assignReason: formData.value.reason,
        remark: formData.value.remark
      })
      message.success('分配成功')
    } else {
      await transferRehabTherapist({
        patientId: formData.value.patientId!,
        fromTherapistUserId: formData.value.fromTherapistUserId,
        toTherapistUserId: formData.value.therapistUserId!,
        reason: formData.value.reason,
        remark: formData.value.remark
      })
      message.success('转交成功')
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
