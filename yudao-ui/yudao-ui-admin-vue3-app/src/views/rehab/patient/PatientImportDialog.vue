<template>
  <Dialog v-model="dialogVisible" title="批量导入患者" width="720px">
    <template v-if="!result">
      <el-alert
        title="重复患者会自动跳过，不会覆盖已有档案。患者编号优先查重，其次按姓名和手机号查重。"
        type="info"
        :closable="false"
        class="mb-16px"
      />
      <el-upload
        ref="uploadRef"
        v-model:file-list="fileList"
        :action="importUrl"
        :auto-upload="false"
        :before-upload="beforeUpload"
        :disabled="formLoading"
        :headers="uploadHeaders"
        :limit="1"
        :on-error="submitFormError"
        :on-exceed="handleExceed"
        :on-success="submitFormSuccess"
        accept=".xlsx,.xls"
        drag
      >
        <Icon icon="ep:upload-filled" :size="42" />
        <div class="el-upload__text">将患者 Excel 拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip text-center">
            仅支持 xls、xlsx，文件不超过 5 MB，单次最多 2000 行。
            <el-link type="primary" :underline="false" @click.stop="downloadTemplate">
              下载导入模板
            </el-link>
          </div>
        </template>
      </el-upload>
    </template>

    <template v-else>
      <el-result
        :icon="result.failureCount > 0 ? 'warning' : 'success'"
        :title="result.failureCount > 0 ? '导入完成，部分行需处理' : '患者导入完成'"
      >
        <template #sub-title>
          共 {{ result.totalCount }} 行：新建 {{ result.createdCount }}，重复跳过
          {{ result.skippedCount }}，失败 {{ result.failureCount }}。
        </template>
      </el-result>
      <el-alert
        v-if="result.skippedPatients?.length"
        :title="`重复跳过：${result.skippedPatients.join('、')}`"
        type="info"
        :closable="false"
        class="mb-12px"
      />
      <el-table v-if="result.failures?.length" :data="result.failures" stripe max-height="260">
        <el-table-column prop="rowNumber" label="Excel 行号" width="105" />
        <el-table-column prop="patientIdentity" label="患者编号/姓名" min-width="150" />
        <el-table-column prop="reason" label="失败原因" min-width="300" />
      </el-table>
    </template>

    <template #footer>
      <template v-if="!result">
        <el-button type="primary" :loading="formLoading" @click="submitForm">开始导入</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
      </template>
      <template v-else>
        <el-button
          v-if="result.failureExcelBase64"
          type="warning"
          plain
          @click="downloadFailures"
        >
          <Icon icon="ep:download" />下载失败明细
        </el-button>
        <el-button type="primary" @click="closeResult">完成</el-button>
      </template>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { UploadFile, UploadFiles, UploadInstance, UploadProps, UploadUserFile } from 'element-plus'
import { getAccessToken, getTenantId } from '@/utils/auth'
import download from '@/utils/download'
import { downloadRehabPatientImportTemplate, RehabPatientImportResult } from '@/api/rehab/patient'

defineOptions({ name: 'RehabPatientImportDialog' })

const message = useMessage()
const dialogVisible = ref(false)
const formLoading = ref(false)
const uploadRef = ref<UploadInstance>()
const fileList = ref<UploadUserFile[]>([])
const result = ref<RehabPatientImportResult>()
const uploadHeaders = ref<Record<string, string | number>>()
const importUrl =
  import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL + '/rehab/patient/import'

const open = () => {
  dialogVisible.value = true
  formLoading.value = false
  fileList.value = []
  result.value = undefined
  nextTick(() => uploadRef.value?.clearFiles())
}
defineExpose({ open })

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  if (file.size > 5 * 1024 * 1024) {
    message.error('导入文件不能超过 5 MB')
    return false
  }
  return true
}

const submitForm = () => {
  if (!fileList.value.length) {
    message.error('请先选择患者 Excel 文件')
    return
  }
  uploadHeaders.value = {
    Authorization: 'Bearer ' + getAccessToken(),
    'tenant-id': getTenantId()
  }
  formLoading.value = true
  uploadRef.value?.submit()
}

const submitFormSuccess = (response: any, _file: UploadFile, _files: UploadFiles) => {
  formLoading.value = false
  if (response.code !== 0) {
    message.error(response.msg || '导入失败')
    uploadRef.value?.clearFiles()
    return
  }
  result.value = response.data
}

const submitFormError = () => {
  formLoading.value = false
  message.error('上传失败，请检查文件格式和网络后重试')
}

const handleExceed = () => message.error('每次只能上传一个文件')

const downloadTemplate = async () => {
  const data = await downloadRehabPatientImportTemplate()
  download.excel(data, '患者批量导入模板.xlsx')
}

const downloadFailures = () => {
  if (!result.value?.failureExcelBase64) return
  const bytes = window.atob(result.value.failureExcelBase64)
  const buffer = new Uint8Array(bytes.length)
  for (let index = 0; index < bytes.length; index++) buffer[index] = bytes.charCodeAt(index)
  download.excel(new Blob([buffer]), '患者导入失败明细.xlsx')
}

const emits = defineEmits<{ success: [] }>()
const closeResult = () => {
  dialogVisible.value = false
  emits('success')
}
</script>
