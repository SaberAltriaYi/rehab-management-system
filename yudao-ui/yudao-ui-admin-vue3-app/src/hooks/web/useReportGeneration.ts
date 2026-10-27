// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
import { onScopeDispose, ref } from 'vue'
import { useMessage } from './useMessage'
import { reportGenerationIntents } from '@/api/rehab/report'
import { ReportIntentError, reportAssessmentId, reportIntentScope } from '@/utils/reportIntent'

export function useReportGeneration() {
  const message = useMessage()
  let active = true
  const busyIds = ref(new Set<string>())
  const stateTick = ref(0)
  const unsubscribe = reportIntentScope.subscribe(() => {
    busyIds.value = new Set()
    stateTick.value++
  })
  onScopeDispose(() => {
    active = false
    unsubscribe()
  })
  const isGeneratingReport = (id: number | string) => {
    try {
      return busyIds.value.has(reportAssessmentId(id))
    } catch {
      return false
    }
  }
  const hasUncertainReport = (id: number | string) => {
    void stateTick.value
    return reportGenerationIntents.hasUncertain(id)
  }
  async function generateReport(id: number | string): Promise<boolean> {
    let stamp: ReturnType<typeof reportGenerationIntents.prepare> | undefined
    let key = ''
    try {
      stamp = reportGenerationIntents.prepare()
      key = reportAssessmentId(id)
      if (busyIds.value.has(key)) return false
      busyIds.value.add(key)
      const result = await reportGenerationIntents.run({ assessmentId: key }, async (state) => {
        const text = state.legacy
          ? '当前或上次后台未提供安全重试。请先核对报告中心；确认继续会生成新的草稿修订，不能保证与旧请求去重。'
          : state.retry
            ? '上次结果未确认，本次将使用原编号确认原请求，不按后来修改的评估重新生成；如需新修订，请先核对报告中心。'
            : '确认基于当前评估生成新的报告草稿吗？若刷新页面前曾超时，请先核对报告中心；生成后仍需原审核/签发流程。'
        try {
          await message.confirm(text)
          return true
        } catch (error) {
          if (error === 'cancel' || error === 'close') return false
          throw error
        }
      })
      if (!active || !result || !reportGenerationIntents.isCurrent(stamp)) return false
      message.success(
        result.legacy
          ? '报告生成完成（旧后台模式，请勿重复提交）'
          : result.retried
            ? '原请求结果已确认'
            : '报告草稿已生成'
      )
      return true
    } catch (error) {
      if (!active || (stamp && !reportGenerationIntents.isCurrent(stamp))) return false
      if (error instanceof ReportIntentError) message.warning(error.message)
      else message.warning('操作未确认，请先核对报告中心后重试')
      return false
    } finally {
      if (stamp && reportGenerationIntents.isCurrent(stamp)) busyIds.value.delete(key)
      stateTick.value++
    }
  }
  async function resetPendingReport(id: number | string) {
    let stamp: ReturnType<typeof reportGenerationIntents.prepare> | undefined
    let key = ''
    try {
      stamp = reportGenerationIntents.prepare()
      key = reportAssessmentId(id)
      if (busyIds.value.has(key)) return
      busyIds.value.add(key)
      await message.confirm(
        '请先核对报告中心。确认仅清除本机待确认编号？旧报告不删除；原请求如已执行，下一次生成可能新增另一份草稿修订。'
      )
      if (!active || !reportGenerationIntents.isCurrent(stamp)) return
      reportGenerationIntents.forgetUncertain(key)
      message.info('本机等待编号已重置；下一次明确生成将使用新编号，旧报告保留')
    } catch (error) {
      if (!active || (stamp && !reportGenerationIntents.isCurrent(stamp))) return
      if (error === 'cancel' || error === 'close') return
      message.warning(
        error instanceof ReportIntentError ? error.message : '操作未确认，请先核对报告中心'
      )
    } finally {
      if (stamp && reportGenerationIntents.isCurrent(stamp)) busyIds.value.delete(key)
      stateTick.value++
    }
  }
  return { generateReport, isGeneratingReport, hasUncertainReport, resetPendingReport }
}
