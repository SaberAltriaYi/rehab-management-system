<template>
  <Dialog v-model="dialogVisible" title="分配工作室岗位">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="员工账号">
        <el-input v-model="formData.username" disabled />
      </el-form-item>
      <el-form-item label="员工姓名">
        <el-input v-model="formData.nickname" disabled />
      </el-form-item>
      <el-form-item label="岗位角色" prop="roleCode">
        <el-select v-model="formData.roleCode" class="w-full" placeholder="请选择岗位角色">
          <el-option label="康复治疗师" value="tenant_rehab_therapist" />
        </el-select>
      </el-form-item>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="临床审核人暂不开放机构管理员自助分配"
        description="临床审核人拥有报告审批和动作评估签署权限，须先完成专业资质核验，再由系统所有者单独授权。"
      />
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'UserAssignRehabRoleForm' })

const message = useMessage()
const emit = defineEmits(['success'])
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const formData = ref({
  userId: 0,
  username: '',
  nickname: '',
  roleCode: 'tenant_rehab_therapist' as const
})
const rules = {
  roleCode: [{ required: true, message: '请选择岗位角色', trigger: 'change' }]
}

const open = (row: UserApi.UserVO) => {
  formData.value = {
    userId: row.id,
    username: row.username,
    nickname: row.nickname,
    roleCode: 'tenant_rehab_therapist'
  }
  dialogVisible.value = true
}
defineExpose({ open })

const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  formLoading.value = true
  try {
    await UserApi.assignRehabStaffRole({
      userId: formData.value.userId,
      roleCode: formData.value.roleCode
    })
    message.success('员工岗位角色已更新')
    dialogVisible.value = false
    emit('success', true)
  } finally {
    formLoading.value = false
  }
}
</script>
