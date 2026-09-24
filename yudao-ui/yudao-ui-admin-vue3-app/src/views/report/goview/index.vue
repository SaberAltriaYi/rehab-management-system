<template>
  <ContentWrap title="报表项目（安全模式）">
    <el-alert
      title="仅开放本人项目元数据管理。外部大屏设计器、任意 SQL 数据集和积木报表尚未通过安全验收，不在本页提供。"
      type="warning"
      :closable="false"
      class="mb-15px"
    />
    <el-button type="primary" @click="openForm()" v-if="canCreate">
      新建项目
    </el-button>
    <el-table v-loading="loading" :data="projects" class="mt-15px">
      <el-table-column prop="id" label="项目编号" width="120" />
      <el-table-column prop="name" label="项目名称" min-width="220" />
      <el-table-column label="状态" width="100">
        <template #default="scope">{{ scope.row.status === 0 ? '启用' : '未发布' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="190" />
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button link type="primary" @click="openForm(scope.row)" v-if="canUpdate">
            重命名
          </el-button>
          <el-button link type="danger" @click="remove(scope.row.id)" v-if="canDelete">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="pageNo"
      v-model:limit="pageSize"
      :total="total"
      @pagination="loadProjects"
    />
  </ContentWrap>
  <Dialog v-model="dialogVisible" :title="editingId === null ? '新建项目' : '重命名项目'">
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px" @submit.prevent>
      <el-form-item label="项目名称" prop="name">
        <el-input v-model="formData.name" maxlength="255" show-word-limit placeholder="请输入项目名称" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { GoViewProjectApi, type GoViewProjectVO } from '@/api/report/goview/project'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useUserStoreWithOut } from '@/store/modules/user'

defineOptions({ name: 'GoView' })

const message = useMessage()
const userStore = useUserStoreWithOut()
// 后端超级管理员权限允许访问，不依赖未预置的报表按钮菜单。其他用户仍按按钮权限收口。
const isSuperAdmin = computed(() => userStore.getRoles.includes('super_admin'))
const canCreate = computed(() => isSuperAdmin.value || hasPermission(['report:go-view-project:create']))
const canUpdate = computed(() => isSuperAdmin.value || hasPermission(['report:go-view-project:update']))
const canDelete = computed(() => isSuperAdmin.value || hasPermission(['report:go-view-project:delete']))
const loading = ref(false)
const saving = ref(false)
const projects = ref<GoViewProjectVO[]>([])
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref()
const formData = reactive({ name: '' })
const rules = { name: [{ required: true, whitespace: true, message: '请输入项目名称', trigger: 'blur' }] }

const loadProjects = async () => {
  loading.value = true
  try {
    const data = await GoViewProjectApi.getMyPage({ pageNo: pageNo.value, pageSize: pageSize.value })
    projects.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (project?: GoViewProjectVO) => {
  editingId.value = project?.id ?? null
  formData.name = project?.name ?? ''
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const save = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const name = formData.name.trim()
    if (editingId.value === null) {
      await GoViewProjectApi.create(name)
    } else {
      await GoViewProjectApi.rename(editingId.value, name)
    }
    dialogVisible.value = false
    message.success('保存成功')
    await loadProjects()
  } finally {
    saving.value = false
  }
}
const remove = async (id: number) => {
  try {
    await message.delConfirm()
    await GoViewProjectApi.remove(id)
    message.success('删除成功')
    await loadProjects()
  } catch {
    // 用户取消删除时不显示错误。
  }
}

onMounted(loadProjects)
</script>
