<template>
  <el-card shadow="never" class="mb-12px">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">多节段伸展解析（MSE Analysis Engine）</span>
        <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-10px"
      title="流程：无上肢参与躯体后伸 → 单腿站立后伸 → 俯卧撑 → 腰部固定主动旋转/伸展 → 腰部固定被动旋转/伸展 / 俯卧位肘支撑旋转/伸展。"
    />

    <el-alert
      v-if="model.mse_analysis.summary.stop_and_treat_pain"
      :closable="false"
      type="error"
      class="mb-10px"
      title="当前解析在疼痛性结果处停止，建议优先处理疼痛后再继续。"
    />

    <el-descriptions border :column="2" size="small" class="mb-10px">
      <el-descriptions-item label="当前流程节点">{{ flowSnapshot.activeNodeLabel }}</el-descriptions-item>
      <el-descriptions-item label="当前分流建议">{{ flowSnapshot.nextStepLabel }}</el-descriptions-item>
      <el-descriptions-item label="上半身伸展流程">{{ model.mse_analysis.summary.upper_body_extension_flow_needed ? '需要' : '暂不需要' }}</el-descriptions-item>
      <el-descriptions-item label="下半身伸展流程">{{ model.mse_analysis.summary.lower_body_extension_flow_needed ? '需要' : '暂不需要' }}</el-descriptions-item>
      <el-descriptions-item label="当前判断方向" :span="2">{{ flowSnapshot.summaryHint }}</el-descriptions-item>
    </el-descriptions>

    <el-form :model="model" label-width="200px">
      <el-collapse>
        <el-collapse-item title="A. Breakout 基础状态" name="base">
          <el-form-item label="Breakout 状态">
            <el-radio-group v-model="model.breakout_status" @change="emitChange">
              <el-radio label="not_started">未开始</el-radio>
              <el-radio label="in_progress">进行中</el-radio>
              <el-radio label="completed">已完成</el-radio>
              <el-radio label="skipped">暂不分解</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="Top Tier 建议原因">
            <el-input v-model="model.breakout_reason_from_top_tier" type="textarea" :rows="2" readonly />
          </el-form-item>
          <el-form-item label="Breakout 备注">
            <el-input v-model="model.breakout_note" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          <el-form-item label="需人工复核">
            <el-switch v-model="model.needs_manual_review" @change="emitChange" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="B. 无上肢参与的躯体后伸" name="node-1">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('trunk_extension_without_upper_extremity') }"
            :disabled="!isNodeEnabled('trunk_extension_without_upper_extremity')"
          >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`目的：${nodeMeta.trunk_extension_without_upper_extremity.purpose}`"
              :description="`说明：${nodeMeta.trunk_extension_without_upper_extremity.instructions}；附加信息：${nodeMeta.trunk_extension_without_upper_extremity.clinical_notes}`"
            />
            <el-form-item label="结果分类">
              <el-radio-group
                v-model="model.mse_analysis.spinal_extension_flow.trunk_extension_without_upper_extremity.result_type"
                @change="emitChange"
              >
                <el-radio label="FN">FN</el-radio>
                <el-radio label="FP">FP</el-radio>
                <el-radio label="DN">DN</el-radio>
                <el-radio label="DP">DP</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch v-model="model.mse_analysis.spinal_extension_flow.trunk_extension_without_upper_extremity.pain_present" @change="emitChange" />
            </el-form-item>
            <el-form-item label="节点备注">
              <el-input v-model="model.mse_analysis.spinal_extension_flow.trunk_extension_without_upper_extremity.note" type="textarea" :rows="2" @input="emitChange" />
            </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="C. 单腿站立躯体后伸" name="node-2">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('single_leg_stance_trunk_extension') }"
            :disabled="!isNodeEnabled('single_leg_stance_trunk_extension')"
          >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`目的：${nodeMeta.single_leg_stance_trunk_extension.purpose}`"
              :description="`说明：${nodeMeta.single_leg_stance_trunk_extension.instructions}；附加信息：${nodeMeta.single_leg_stance_trunk_extension.clinical_notes}`"
            />
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="左侧结果">
                  <el-radio-group v-model="model.mse_analysis.spinal_extension_flow.single_leg_stance_trunk_extension.left_result" @change="emitChange">
                    <el-radio label="FN">FN</el-radio>
                    <el-radio label="FP">FP</el-radio>
                    <el-radio label="DN">DN</el-radio>
                    <el-radio label="DP">DP</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="右侧结果">
                  <el-radio-group v-model="model.mse_analysis.spinal_extension_flow.single_leg_stance_trunk_extension.right_result" @change="emitChange">
                    <el-radio label="FN">FN</el-radio>
                    <el-radio label="FP">FP</el-radio>
                    <el-radio label="DN">DN</el-radio>
                    <el-radio label="DP">DP</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="双侧汇总">
              <el-input v-model="model.mse_analysis.spinal_extension_flow.single_leg_stance_trunk_extension.bilateral_summary" readonly />
            </el-form-item>
            <el-form-item label="节点备注">
              <el-input v-model="model.mse_analysis.spinal_extension_flow.single_leg_stance_trunk_extension.note" type="textarea" :rows="2" @input="emitChange" />
            </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="D. 俯卧撑（非负重伸展）" name="node-3">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('prone_press_up') }"
            :disabled="!isNodeEnabled('prone_press_up')"
          >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`目的：${nodeMeta.prone_press_up.purpose}`"
              :description="`说明：${nodeMeta.prone_press_up.instructions}；附加信息：${nodeMeta.prone_press_up.clinical_notes}`"
            />
            <el-form-item label="结果分类">
              <el-radio-group v-model="model.mse_analysis.spinal_extension_flow.prone_press_up.result_type" @change="emitChange">
                <el-radio label="FN">FN</el-radio>
                <el-radio label="FP">FP</el-radio>
                <el-radio label="DN">DN</el-radio>
                <el-radio label="DP">DP</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="是否使用骨盆垫">
              <el-switch v-model="model.mse_analysis.spinal_extension_flow.prone_press_up.used_pad" @change="emitChange" />
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch v-model="model.mse_analysis.spinal_extension_flow.prone_press_up.pain_present" @change="emitChange" />
            </el-form-item>
            <el-form-item label="节点备注">
              <el-input v-model="model.mse_analysis.spinal_extension_flow.prone_press_up.note" type="textarea" :rows="2" @input="emitChange" />
            </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="E. 腰部固定（内旋）主动旋转/伸展" name="node-4">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('lumbar_fixed_internal_rotation_active_extension_rotation') }"
            :disabled="!isNodeEnabled('lumbar_fixed_internal_rotation_active_extension_rotation')"
          >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`目的：${nodeMeta.lumbar_fixed_internal_rotation_active_extension_rotation.purpose}`"
              :description="`说明：${nodeMeta.lumbar_fixed_internal_rotation_active_extension_rotation.instructions}；附加信息：${nodeMeta.lumbar_fixed_internal_rotation_active_extension_rotation.clinical_notes}`"
            />
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="左侧结果">
                  <el-radio-group
                    v-model="model.mse_analysis.spinal_extension_flow.lumbar_fixed_internal_rotation_active_extension_rotation.left_result"
                    @change="emitChange"
                  >
                    <el-radio label="FN">FN</el-radio>
                    <el-radio label="FP">FP</el-radio>
                    <el-radio label="DN">DN</el-radio>
                    <el-radio label="DP">DP</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="右侧结果">
                  <el-radio-group
                    v-model="model.mse_analysis.spinal_extension_flow.lumbar_fixed_internal_rotation_active_extension_rotation.right_result"
                    @change="emitChange"
                  >
                    <el-radio label="FN">FN</el-radio>
                    <el-radio label="FP">FP</el-radio>
                    <el-radio label="DN">DN</el-radio>
                    <el-radio label="DP">DP</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="综合结果">
              <el-radio-group
                v-model="model.mse_analysis.spinal_extension_flow.lumbar_fixed_internal_rotation_active_extension_rotation.result_type"
                @change="emitChange"
              >
                <el-radio label="FN">FN</el-radio>
                <el-radio label="FP">FP</el-radio>
                <el-radio label="DN">DN</el-radio>
                <el-radio label="DP">DP</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch
                v-model="model.mse_analysis.spinal_extension_flow.lumbar_fixed_internal_rotation_active_extension_rotation.pain_present"
                @change="emitChange"
              />
            </el-form-item>
            <el-form-item label="节点备注">
              <el-input
                v-model="model.mse_analysis.spinal_extension_flow.lumbar_fixed_internal_rotation_active_extension_rotation.note"
                type="textarea"
                :rows="2"
                @input="emitChange"
              />
            </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="F. 腰部固定（内旋）被动旋转/伸展" name="node-5">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('lumbar_fixed_internal_rotation_passive_extension_rotation') }"
            :disabled="!isNodeEnabled('lumbar_fixed_internal_rotation_passive_extension_rotation')"
          >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`目的：${nodeMeta.lumbar_fixed_internal_rotation_passive_extension_rotation.purpose}`"
              :description="`说明：${nodeMeta.lumbar_fixed_internal_rotation_passive_extension_rotation.instructions}；附加信息：${nodeMeta.lumbar_fixed_internal_rotation_passive_extension_rotation.clinical_notes}`"
            />
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="左侧结果">
                  <el-radio-group
                    v-model="model.mse_analysis.spinal_extension_flow.lumbar_fixed_internal_rotation_passive_extension_rotation.left_result"
                    @change="emitChange"
                  >
                    <el-radio label="FN">FN</el-radio>
                    <el-radio label="FP">FP</el-radio>
                    <el-radio label="DN">DN</el-radio>
                    <el-radio label="DP">DP</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="右侧结果">
                  <el-radio-group
                    v-model="model.mse_analysis.spinal_extension_flow.lumbar_fixed_internal_rotation_passive_extension_rotation.right_result"
                    @change="emitChange"
                  >
                    <el-radio label="FN">FN</el-radio>
                    <el-radio label="FP">FP</el-radio>
                    <el-radio label="DN">DN</el-radio>
                    <el-radio label="DP">DP</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="结果类型">
              <el-radio-group
                v-model="model.mse_analysis.spinal_extension_flow.lumbar_fixed_internal_rotation_passive_extension_rotation.result_type"
                @change="emitChange"
              >
                <el-radio label="FN">FN</el-radio>
                <el-radio label="FP">FP</el-radio>
                <el-radio label="DP">DP</el-radio>
                <el-radio label="unilateral_DN">单侧DN</el-radio>
                <el-radio label="bilateral_DN">双侧DN</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch
                v-model="model.mse_analysis.spinal_extension_flow.lumbar_fixed_internal_rotation_passive_extension_rotation.pain_present"
                @change="emitChange"
              />
            </el-form-item>
            <el-form-item label="节点备注">
              <el-input
                v-model="model.mse_analysis.spinal_extension_flow.lumbar_fixed_internal_rotation_passive_extension_rotation.note"
                type="textarea"
                :rows="2"
                @input="emitChange"
              />
            </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="G. 俯卧位肘支撑旋转/伸展" name="node-6">
          <fieldset
            class="flow-node-fieldset"
            :class="{ 'flow-node-disabled': !isNodeEnabled('prone_elbow_supported_extension_rotation') }"
            :disabled="!isNodeEnabled('prone_elbow_supported_extension_rotation')"
          >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`目的：${nodeMeta.prone_elbow_supported_extension_rotation.purpose}`"
              :description="`说明：${nodeMeta.prone_elbow_supported_extension_rotation.instructions}；附加信息：${nodeMeta.prone_elbow_supported_extension_rotation.clinical_notes}`"
            />
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="左侧结果">
                  <el-radio-group
                    v-model="model.mse_analysis.spinal_extension_flow.prone_elbow_supported_extension_rotation.left_result"
                    @change="emitChange"
                  >
                    <el-radio label="FN">FN</el-radio>
                    <el-radio label="FP">FP</el-radio>
                    <el-radio label="DN">DN</el-radio>
                    <el-radio label="DP">DP</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="右侧结果">
                  <el-radio-group
                    v-model="model.mse_analysis.spinal_extension_flow.prone_elbow_supported_extension_rotation.right_result"
                    @change="emitChange"
                  >
                    <el-radio label="FN">FN</el-radio>
                    <el-radio label="FP">FP</el-radio>
                    <el-radio label="DN">DN</el-radio>
                    <el-radio label="DP">DP</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="结果类型">
              <el-radio-group
                v-model="model.mse_analysis.spinal_extension_flow.prone_elbow_supported_extension_rotation.result_type"
                @change="emitChange"
              >
                <el-radio label="bilateral_FN">双侧FN</el-radio>
                <el-radio label="unilateral_DN">单侧DN</el-radio>
                <el-radio label="bilateral_DN">双侧DN</el-radio>
                <el-radio label="FP">FP</el-radio>
                <el-radio label="DP">DP</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch
                v-model="model.mse_analysis.spinal_extension_flow.prone_elbow_supported_extension_rotation.pain_present"
                @change="emitChange"
              />
            </el-form-item>
            <el-form-item label="节点备注">
              <el-input
                v-model="model.mse_analysis.spinal_extension_flow.prone_elbow_supported_extension_rotation.note"
                type="textarea"
                :rows="2"
                @input="emitChange"
              />
            </el-form-item>
          </fieldset>
        </el-collapse-item>

        <el-collapse-item title="H. 下半身伸展流程（规则驱动）" name="lower-flow">
          <div
            v-for="node in lowerFlowNodes"
            :key="node.code"
            class="mb-16px p-10px border border-[var(--el-border-color)] rounded-6px"
            :class="{ 'flow-node-disabled': !isNodeEnabled(node.code) && !hasMseNodeValue(model, node.code) }"
          >
            <fieldset
              class="flow-node-fieldset"
              :disabled="!isNodeEnabled(node.code) && !hasMseNodeValue(model, node.code)"
            >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`${node.rule.node_name_zh}：${node.rule.purpose}`"
              :description="`说明：${node.rule.instructions}；附加信息：${node.rule.clinical_notes}；下一步规则：${node.rule.next_step_rules}`"
            />
            <el-form-item label="结果分类">
              <el-radio-group
                :model-value="mseNodeValue(node.code, 'result_type')"
                @change="(value) => updateMseNodeField(node.code, 'result_type', value)"
              >
                <el-radio v-for="option in node.rule.result_options" :key="option" :label="option">
                  {{ mseResultOptionLabel(option) }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch
                :model-value="Boolean(mseNodeValue(node.code, 'pain_present'))"
                @change="(value) => updateMseNodeField(node.code, 'pain_present', value)"
              />
            </el-form-item>
            <el-form-item v-if="node.code === 'prone_passive_hip_extension'" label="主动/被动差值(%)">
              <el-input-number
                :model-value="mseNodeValue(node.code, 'gap_percent')"
                :min="0"
                :max="100"
                :step="1"
                class="!w-full"
                @change="(value) => updateMseNodeField(node.code, 'gap_percent', value)"
              />
            </el-form-item>
            <el-form-item label="节点备注">
              <el-input
                :model-value="mseNodeValue(node.code, 'note')"
                type="textarea"
                :rows="2"
                @input="(value) => updateMseNodeField(node.code, 'note', value)"
              />
            </el-form-item>
            </fieldset>
          </div>
        </el-collapse-item>

        <el-collapse-item title="I. 上半身伸展流程（规则驱动）" name="upper-flow">
          <div
            v-for="node in upperFlowNodes"
            :key="node.code"
            class="mb-16px p-10px border border-[var(--el-border-color)] rounded-6px"
            :class="{ 'flow-node-disabled': !isNodeEnabled(node.code) && !hasMseNodeValue(model, node.code) }"
          >
            <fieldset
              class="flow-node-fieldset"
              :disabled="!isNodeEnabled(node.code) && !hasMseNodeValue(model, node.code)"
            >
            <el-alert
              :closable="false"
              type="info"
              class="mb-10px"
              :title="`${node.rule.node_name_zh}：${node.rule.purpose}`"
              :description="`说明：${node.rule.instructions}；附加信息：${node.rule.clinical_notes}；下一步规则：${node.rule.next_step_rules}`"
            />
            <el-form-item label="结果分类">
              <el-radio-group
                :model-value="mseNodeValue(node.code, 'result_type')"
                @change="(value) => updateMseNodeField(node.code, 'result_type', value)"
              >
                <el-radio v-for="option in node.rule.result_options" :key="option" :label="option">
                  {{ mseResultOptionLabel(option) }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="疼痛标记">
              <el-switch
                :model-value="Boolean(mseNodeValue(node.code, 'pain_present'))"
                @change="(value) => updateMseNodeField(node.code, 'pain_present', value)"
              />
            </el-form-item>
            <el-form-item label="节点备注">
              <el-input
                :model-value="mseNodeValue(node.code, 'note')"
                type="textarea"
                :rows="2"
                @input="(value) => updateMseNodeField(node.code, 'note', value)"
              />
            </el-form-item>
            </fieldset>
          </div>
        </el-collapse-item>

        <el-collapse-item title="J. 自动判断方向" name="direction">
          <el-form-item label="初步方向（自动）">
            <el-checkbox-group v-model="model.breakout_preliminary_direction" @change="emitChange">
              <el-checkbox label="更偏活动度限制" />
              <el-checkbox label="更偏髋伸展不足" />
              <el-checkbox label="更偏胸椎伸展不足" />
              <el-checkbox label="更偏腰盆控制问题" />
              <el-checkbox label="更偏肩带/上肢参与不足" />
              <el-checkbox label="更偏运动控制问题" />
              <el-checkbox label="更偏疼痛主导" />
              <el-checkbox label="需结合其他模式综合判断" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="主要限制链条">
            <el-checkbox-group v-model="model.primary_restriction_chain" @change="emitChange">
              <el-checkbox label="足踝-下肢支撑链" />
              <el-checkbox label="髋前侧-骨盆链" />
              <el-checkbox label="腰椎-骨盆链" />
              <el-checkbox label="胸椎-肩带链" />
              <el-checkbox label="过头模式链" />
              <el-checkbox label="暂不明确" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="主要控制障碍链条">
            <el-checkbox-group v-model="model.primary_control_deficit_chain" @change="emitChange">
              <el-checkbox label="LPHC控制不足" />
              <el-checkbox label="髋主导不足" />
              <el-checkbox label="伸展模式控制不足" />
              <el-checkbox label="左右对称控制不足" />
              <el-checkbox label="躯干控制不足" />
              <el-checkbox label="暂不明确" />
            </el-checkbox-group>
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="K. Breakout 汇总" name="summary">
          <el-form-item label="流程总结（自动）">
            <el-input v-model="model.mse_analysis.summary.summary_text" type="textarea" :rows="3" readonly />
          </el-form-item>
          <el-form-item label="Breakout 汇总文本">
            <el-input v-model="model.breakout_summary_text" type="textarea" :rows="3" @input="emitChange" />
          </el-form-item>
          <el-form-item label="功能学意义提示">
            <el-input v-model="model.clinical_meaning_hint" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          <el-form-item label="训练取向提示">
            <el-input v-model="model.training_direction_hint" type="textarea" :rows="2" @input="emitChange" />
          </el-form-item>
          <el-form-item label="后续流程目标">
            <el-tag
              v-for="target in model.mse_analysis.summary.next_flow_targets"
              :key="target"
              size="small"
              class="mr-8px"
              type="info"
            >
              {{ target }}
            </el-tag>
            <span v-if="!model.mse_analysis.summary.next_flow_targets.length" class="text-12px color-[var(--el-text-color-secondary)]">
              暂无
            </span>
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="复评优先级">
                <el-radio-group v-model="model.reassessment_priority" @change="emitChange">
                  <el-radio label="low">low</el-radio>
                  <el-radio label="medium">medium</el-radio>
                  <el-radio label="high">high</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="暂停/转介提示">
                <el-radio-group v-model="model.pause_or_referral_hint" @change="emitChange">
                  <el-radio label="无需">无需</el-radio>
                  <el-radio label="建议优先人工复核">建议优先人工复核</el-radio>
                  <el-radio label="建议结合进一步医学评估">建议结合进一步医学评估</el-radio>
                  <el-radio label="建议暂缓推进训练">建议暂缓推进训练</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <div class="mt-8px">
            <el-button type="success" @click="markCompleted">标记为已完成</el-button>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-form>
  </el-card>
</template>

<script lang="ts" setup>
import { computed, reactive, watch } from 'vue'
import { buildDefaultMseBreakout, SfmaMseBreakout } from '@/views/rehab/assessment/config/sfmaConfig'
import {
  getMseNodeRule,
  hasMseNodeValue as hasMseNodeValueInEngine,
  MSE_LOWER_NODE_ORDER,
  MSE_SPINAL_NODE_ORDER,
  MSE_UPPER_NODE_ORDER,
  MseFlowNodeCode,
  runMseAnalysisFlowEngine
} from './mseAnalysisFlowEngine'

const props = defineProps<{ modelValue?: SfmaMseBreakout }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: SfmaMseBreakout): void
  (e: 'change', value: SfmaMseBreakout): void
}>()

const deepClone = (value: any) => JSON.parse(JSON.stringify(value || {}))

const model = reactive<SfmaMseBreakout>(buildDefaultMseBreakout())

const nodeMeta = {
  trunk_extension_without_upper_extremity: getMseNodeRule('trunk_extension_without_upper_extremity'),
  single_leg_stance_trunk_extension: getMseNodeRule('single_leg_stance_trunk_extension'),
  prone_press_up: getMseNodeRule('prone_press_up'),
  lumbar_fixed_internal_rotation_active_extension_rotation: getMseNodeRule('lumbar_fixed_internal_rotation_active_extension_rotation'),
  lumbar_fixed_internal_rotation_passive_extension_rotation: getMseNodeRule('lumbar_fixed_internal_rotation_passive_extension_rotation'),
  prone_elbow_supported_extension_rotation: getMseNodeRule('prone_elbow_supported_extension_rotation')
}

const normalizeBeforeEmit = (value: SfmaMseBreakout) => {
  const payload: SfmaMseBreakout = {
    ...buildDefaultMseBreakout(),
    ...deepClone(value)
  }
  const flowState = runMseAnalysisFlowEngine(payload)
  payload.mse_analysis = flowState.analysis

  if (!payload.breakout_preliminary_direction.length) {
    const directions: string[] = []
    if (flowState.analysis.summary.thoracic_extension_issue) directions.push('更偏胸椎伸展不足')
    if (flowState.analysis.summary.lumbar_extension_issue) directions.push('更偏腰盆控制问题')
    if (flowState.analysis.summary.weight_bearing_stability_issue) directions.push('更偏运动控制问题')
    if (flowState.analysis.summary.pain_dominant) directions.push('更偏疼痛主导')
    if (!directions.length) directions.push('需结合其他模式综合判断')
    payload.breakout_preliminary_direction = directions as any
  }
  if (!payload.primary_restriction_chain.length) {
    const chains: string[] = []
    if (flowState.analysis.summary.thoracic_extension_issue) chains.push('胸椎-肩带链')
    if (flowState.analysis.summary.lumbar_extension_issue) chains.push('腰椎-骨盆链')
    if (!chains.length) chains.push('暂不明确')
    payload.primary_restriction_chain = chains as any
  }
  if (!payload.primary_control_deficit_chain.length) {
    const controls: string[] = []
    if (flowState.analysis.summary.weight_bearing_stability_issue) controls.push('伸展模式控制不足')
    if (flowState.analysis.summary.thoracic_extension_issue || flowState.analysis.summary.lumbar_extension_issue) {
      controls.push('躯干控制不足')
    }
    if (!controls.length) controls.push('暂不明确')
    payload.primary_control_deficit_chain = controls as any
  }
  if (!payload.breakout_summary_text) payload.breakout_summary_text = flowState.analysis.summary.summary_text
  if (!payload.clinical_meaning_hint) payload.clinical_meaning_hint = flowState.analysis.summary.summary_text

  if (!payload.training_direction_hint) {
    if (flowState.analysis.summary.pain_dominant) {
      payload.training_direction_hint = '建议优先疼痛管理与人工复核，再决定伸展推进策略。'
    } else if (flowState.analysis.summary.weight_bearing_stability_issue) {
      payload.training_direction_hint = '建议优先负重下躯干伸展稳定与运动控制训练。'
    } else if (flowState.analysis.summary.thoracic_extension_issue || flowState.analysis.summary.lumbar_extension_issue) {
      payload.training_direction_hint = '建议优先伸展相关活动度与分节控制联合训练。'
    }
  }

  if (!payload.pause_or_referral_hint && flowState.analysis.summary.stop_and_treat_pain) {
    payload.pause_or_referral_hint = '建议优先人工复核'
  }

  const anyNodeInput =
    MSE_SPINAL_NODE_ORDER.some((code) => hasMseNodeValueInEngine(payload, code)) ||
    MSE_LOWER_NODE_ORDER.some((code) => hasMseNodeValueInEngine(payload, code)) ||
    MSE_UPPER_NODE_ORDER.some((code) => hasMseNodeValueInEngine(payload, code))

  if (payload.breakout_status === 'not_started' && anyNodeInput) {
    payload.breakout_status = 'in_progress'
  }
  if (flowState.stopAndTreatPain && payload.breakout_status !== 'completed' && payload.breakout_status !== 'skipped') {
    payload.breakout_status = 'in_progress'
  }
  if (flowState.analysis.summary.manual_review_required) {
    payload.needs_manual_review = true
  }
  return payload
}

const emitModel = () => {
  const payload = normalizeBeforeEmit(model)
  Object.assign(model, payload)
  emit('update:modelValue', payload)
  emit('change', payload)
}

const syncFromProps = (value?: SfmaMseBreakout) => {
  const merged = {
    ...buildDefaultMseBreakout(),
    ...(value || {})
  }
  Object.assign(model, normalizeBeforeEmit(merged))
}

watch(
  () => props.modelValue,
  (value) => syncFromProps(value),
  { immediate: true, deep: true }
)

const statusText = computed(() => {
  const map: Record<string, string> = {
    not_started: '未开始',
    in_progress: '进行中',
    completed: '已完成',
    skipped: '暂不分解'
  }
  return map[model.breakout_status] || '未开始'
})

const statusTagType = computed(() => {
  if (model.breakout_status === 'completed') return 'success'
  if (model.breakout_status === 'in_progress') return 'warning'
  if (model.breakout_status === 'skipped') return 'info'
  return 'info'
})

const currentFlowState = computed(() => runMseAnalysisFlowEngine(model))

const isNodeEnabled = (nodeCode: MseFlowNodeCode) => currentFlowState.value.enabledNodes.has(nodeCode)

const hasMseNodeValue = (payload: SfmaMseBreakout, nodeCode: MseFlowNodeCode) => {
  return hasMseNodeValueInEngine(payload, nodeCode)
}

const mseNodeValue = (nodeCode: MseFlowNodeCode, field: string) => {
  const rule = getMseNodeRule(nodeCode)
  const node = (model.mse_analysis as any)?.[rule.flow_group]?.[nodeCode] || {}
  return node[field]
}

const updateMseNodeField = (nodeCode: MseFlowNodeCode, field: string, value: any) => {
  const next = deepClone(model)
  const rule = getMseNodeRule(nodeCode)
  const group = ((next.mse_analysis as any)[rule.flow_group] = (next.mse_analysis as any)[rule.flow_group] || {})
  const node = (group[nodeCode] = group[nodeCode] || {})
  node[field] = value
  Object.assign(model, normalizeBeforeEmit(next))
  emitModel()
}

const mseResultOptionLabel = (option: string) => {
  const map: Record<string, string> = {
    FN: 'FN',
    FP: 'FP',
    DN: 'DN',
    DP: 'DP',
    bilateral_FN: '双侧FN',
    unilateral_DN: '单侧DN',
    bilateral_DN: '双侧DN',
    fn_gap_gt_25: '被动较主动提升>25%',
    partial_improvement: '肩屈曲仅轻微改善'
  }
  return map[option] || option
}

const lowerFlowNodes = computed(() =>
  MSE_LOWER_NODE_ORDER.map((code) => ({
    code,
    rule: getMseNodeRule(code)
  }))
)

const upperFlowNodes = computed(() =>
  MSE_UPPER_NODE_ORDER.map((code) => ({
    code,
    rule: getMseNodeRule(code)
  }))
)

const flowSnapshot = computed(() => {
  const state = currentFlowState.value
  const activeNodeLabel = state.currentNode ? getMseNodeRule(state.currentNode).node_name_zh : '流程已完成或待开始'
  return {
    activeNodeLabel,
    nextStepLabel: state.nextStep || '待录入',
    summaryHint: state.analysis.summary.summary_text || '待生成'
  }
})

const emitChange = () => emitModel()

const markCompleted = () => {
  const next = deepClone(model)
  next.breakout_status = 'completed'
  Object.assign(model, normalizeBeforeEmit(next))
  emitModel()
}

const validate = async () => true
const getFormData = () => deepClone(model)
const reset = () => {
  Object.assign(model, normalizeBeforeEmit(buildDefaultMseBreakout()))
  emitModel()
}

defineExpose({ validate, getFormData, reset })
</script>

<style scoped>
.flow-node-fieldset {
  border: 0;
  margin: 0;
  min-inline-size: 0;
  padding: 0;
}

.flow-node-disabled {
  opacity: 0.55;
}
</style>
