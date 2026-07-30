<template>
  <el-card shadow="never" class="assessment-form-shell">
    <template #header>
      <div class="flex items-center justify-between">
        <span class="font-bold">NASM-CES 评估表单</span>
        <el-tag size="small" type="success">完整录入</el-tag>
      </div>
    </template>

    <el-alert
      :closable="false"
      type="info"
      class="mb-12px"
      title="按 NASM-CES 文档录入：过渡动作、动态动作、上肢戴维斯、LESS 与结果汇总。"
    />

    <el-card shadow="never" class="mb-12px">
      <template #header>基础信息区</template>
      <el-form :model="localData.basic_info" label-width="95px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="姓名">
              <el-input v-model="localData.basic_info.name" placeholder="姓名" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="年龄">
              <el-input-number v-model="localData.basic_info.age" :min="0" :max="120" class="!w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="评估日期">
              <el-date-picker
                v-model="localData.basic_info.assessment_date"
                value-format="YYYY-MM-DD"
                type="date"
                class="!w-full"
                placeholder="请选择日期"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="评估人">
              <el-input v-model="localData.basic_info.assessor" placeholder="评估人" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="本次重点">
              <el-input v-model="localData.basic_info.focus" placeholder="例如：LPHC 稳定与膝控制" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="综合备注">
          <el-input v-model="localData.basic_info.summary_note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-collapse v-model="activePanels" class="mb-12px">
      <el-collapse-item name="transition" title="过渡动作评估">
        <el-card shadow="never" class="mb-12px action-card">
          <template #header>1) 过顶深蹲 ×5</template>
          <el-row :gutter="12" class="mb-10px">
            <el-col :span="6">
              <el-checkbox v-model="localData.transition_assessments.overhead_squat.modification.heel_elevated">
                抬高足跟
              </el-checkbox>
            </el-col>
            <el-col :span="6">
              <el-checkbox v-model="localData.transition_assessments.overhead_squat.modification.hands_on_hips">
                双手扶腰
              </el-checkbox>
            </el-col>
            <el-col :span="12">
              <el-input v-model="localData.transition_assessments.overhead_squat.modification.note" placeholder="动作变式备注" clearable />
            </el-col>
          </el-row>

          <el-divider content-position="left">前面观 - 足</el-divider>
          <div v-for="item in OVERHEAD_SQUAT_FRONT_FEET" :key="`ohs-front-feet-${item.key}`" class="bilateral-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-select v-model="localData.transition_assessments.overhead_squat.front_view.feet[item.key].left" clearable placeholder="左侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-left-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-select v-model="localData.transition_assessments.overhead_squat.front_view.feet[item.key].right" clearable placeholder="右侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-right-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-checkbox v-model="localData.transition_assessments.overhead_squat.front_view.feet[item.key].overall">整体异常</el-checkbox>
            <el-input v-model="localData.transition_assessments.overhead_squat.front_view.feet[item.key].note" placeholder="备注" clearable />
          </div>

          <el-divider content-position="left">前面观 - 膝</el-divider>
          <div v-for="item in OVERHEAD_SQUAT_FRONT_KNEE" :key="`ohs-front-knee-${item.key}`" class="bilateral-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-select v-model="localData.transition_assessments.overhead_squat.front_view.knee[item.key].left" clearable placeholder="左侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-left-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-select v-model="localData.transition_assessments.overhead_squat.front_view.knee[item.key].right" clearable placeholder="右侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-right-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-checkbox v-model="localData.transition_assessments.overhead_squat.front_view.knee[item.key].overall">整体异常</el-checkbox>
            <el-input v-model="localData.transition_assessments.overhead_squat.front_view.knee[item.key].note" placeholder="备注" clearable />
          </div>

          <el-divider content-position="left">侧面观 - LPHC</el-divider>
          <div v-for="item in OVERHEAD_SQUAT_LATERAL_LPHC" :key="`ohs-lateral-lphc-${item.key}`" class="bilateral-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-select v-model="localData.transition_assessments.overhead_squat.lateral_view.lphc[item.key].left" clearable placeholder="左侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-left-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-select v-model="localData.transition_assessments.overhead_squat.lateral_view.lphc[item.key].right" clearable placeholder="右侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-right-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-checkbox v-model="localData.transition_assessments.overhead_squat.lateral_view.lphc[item.key].overall">整体异常</el-checkbox>
            <el-input v-model="localData.transition_assessments.overhead_squat.lateral_view.lphc[item.key].note" placeholder="备注" clearable />
          </div>

          <el-divider content-position="left">侧面观 - 肩</el-divider>
          <div v-for="item in OVERHEAD_SQUAT_LATERAL_SHOULDER" :key="`ohs-lateral-shoulder-${item.key}`" class="bilateral-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-select v-model="localData.transition_assessments.overhead_squat.lateral_view.shoulder[item.key].left" clearable placeholder="左侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-left-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-select v-model="localData.transition_assessments.overhead_squat.lateral_view.shoulder[item.key].right" clearable placeholder="右侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-right-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-checkbox v-model="localData.transition_assessments.overhead_squat.lateral_view.shoulder[item.key].overall">整体异常</el-checkbox>
            <el-input v-model="localData.transition_assessments.overhead_squat.lateral_view.shoulder[item.key].note" placeholder="备注" clearable />
          </div>

          <el-divider content-position="left">后面观 - 足</el-divider>
          <div v-for="item in OVERHEAD_SQUAT_POSTERIOR_FEET" :key="`ohs-posterior-feet-${item.key}`" class="bilateral-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-select v-model="localData.transition_assessments.overhead_squat.posterior_view.feet[item.key].left" clearable placeholder="左侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-left-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-select v-model="localData.transition_assessments.overhead_squat.posterior_view.feet[item.key].right" clearable placeholder="右侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-right-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-checkbox v-model="localData.transition_assessments.overhead_squat.posterior_view.feet[item.key].overall">整体异常</el-checkbox>
            <el-input v-model="localData.transition_assessments.overhead_squat.posterior_view.feet[item.key].note" placeholder="备注" clearable />
          </div>

          <el-divider content-position="left">后面观 - LPHC</el-divider>
          <div v-for="item in OVERHEAD_SQUAT_POSTERIOR_LPHC" :key="`ohs-posterior-lphc-${item.key}`" class="bilateral-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-select v-model="localData.transition_assessments.overhead_squat.posterior_view.lphc[item.key].left" clearable placeholder="左侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-left-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-select v-model="localData.transition_assessments.overhead_squat.posterior_view.lphc[item.key].right" clearable placeholder="右侧">
              <el-option v-for="opt in item.options" :key="`${item.key}-right-${opt.value}`" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-checkbox v-model="localData.transition_assessments.overhead_squat.posterior_view.lphc[item.key].overall">整体异常</el-checkbox>
            <el-input v-model="localData.transition_assessments.overhead_squat.posterior_view.lphc[item.key].note" placeholder="备注" clearable />
          </div>
        </el-card>

        <el-card shadow="never" class="mb-12px action-card">
          <template #header>2) 单腿蹲评估 ×5</template>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-card shadow="never" class="support-card">
                <template #header>左侧支撑（left_support）</template>
                <div v-for="item in SINGLE_LEG_SQUAT_ITEMS" :key="`single-left-${item.key}`" class="binary-row">
                  <div class="obs-label">{{ item.label }}</div>
                  <el-switch v-model="localData.transition_assessments.single_leg_squat.left_support[item.key].present" inline-prompt active-text="是" inactive-text="否" />
                  <el-input v-model="localData.transition_assessments.single_leg_squat.left_support[item.key].note" placeholder="备注" clearable />
                </div>
                <el-input
                  v-model="localData.transition_assessments.single_leg_squat.left_support.note"
                  type="textarea"
                  :rows="2"
                  placeholder="左侧支撑备注"
                />
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never" class="support-card">
                <template #header>右侧支撑（right_support）</template>
                <div v-for="item in SINGLE_LEG_SQUAT_ITEMS" :key="`single-right-${item.key}`" class="binary-row">
                  <div class="obs-label">{{ item.label }}</div>
                  <el-switch v-model="localData.transition_assessments.single_leg_squat.right_support[item.key].present" inline-prompt active-text="是" inactive-text="否" />
                  <el-input v-model="localData.transition_assessments.single_leg_squat.right_support[item.key].note" placeholder="备注" clearable />
                </div>
                <el-input
                  v-model="localData.transition_assessments.single_leg_squat.right_support.note"
                  type="textarea"
                  :rows="2"
                  placeholder="右侧支撑备注"
                />
              </el-card>
            </el-col>
          </el-row>
        </el-card>

        <el-card shadow="never" class="mb-12px action-card">
          <template #header>3) 俯卧撑 ×10</template>
          <el-row :gutter="12" class="mb-10px">
            <el-col :span="6">
              <el-checkbox v-model="localData.transition_assessments.push_up.variation.kneeling_push_up">跪姿俯卧撑</el-checkbox>
            </el-col>
            <el-col :span="6">
              <el-checkbox v-model="localData.transition_assessments.push_up.variation.standing_cable_press">站姿绳索推</el-checkbox>
            </el-col>
          </el-row>

          <el-divider content-position="left">LPHC</el-divider>
          <div v-for="item in PUSH_UP_LPHC_ITEMS" :key="`pushup-lphc-${item.key}`" class="binary-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.transition_assessments.push_up.full_view.lphc[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-input v-model="localData.transition_assessments.push_up.full_view.lphc[item.key].note" placeholder="备注" clearable />
          </div>

          <el-divider content-position="left">肩</el-divider>
          <div v-for="item in PUSH_UP_SHOULDER_ITEMS" :key="`pushup-shoulder-${item.key}`" class="binary-row binary-row-wide">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.transition_assessments.push_up.full_view.shoulder[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.push_up.full_view.shoulder[item.key].left">左侧</el-checkbox>
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.push_up.full_view.shoulder[item.key].right">右侧</el-checkbox>
            <el-input v-model="localData.transition_assessments.push_up.full_view.shoulder[item.key].note" placeholder="备注" clearable />
          </div>

          <el-divider content-position="left">头/颈椎</el-divider>
          <div v-for="item in PUSH_UP_HEAD_ITEMS" :key="`pushup-head-${item.key}`" class="binary-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.transition_assessments.push_up.full_view.head_cervical[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-input v-model="localData.transition_assessments.push_up.full_view.head_cervical[item.key].note" placeholder="备注" clearable />
          </div>
        </el-card>

        <el-card shadow="never" class="mb-12px action-card">
          <template #header>4) 站立划船 ×10</template>
          <div v-for="item in STANDING_ROW_ITEMS" :key="`standing-row-${item.key}`" class="binary-row binary-row-wide">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.transition_assessments.standing_row.full_view[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-input v-model="localData.transition_assessments.standing_row.full_view[item.key].note" placeholder="备注" clearable />
          </div>
          <el-input v-model="localData.transition_assessments.standing_row.variation_note" placeholder="动作变式备注" clearable />
        </el-card>

        <el-card shadow="never" class="mb-12px action-card">
          <template #header>5) 站立哑铃过头举 ×10</template>
          <div v-for="item in STANDING_PRESS_ITEMS" :key="`standing-press-${item.key}`" class="binary-row binary-row-wide">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.transition_assessments.standing_dumbbell_overhead_press.full_view[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.standing_dumbbell_overhead_press.full_view[item.key].left">左侧</el-checkbox>
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.standing_dumbbell_overhead_press.full_view[item.key].right">右侧</el-checkbox>
            <el-input v-model="localData.transition_assessments.standing_dumbbell_overhead_press.full_view[item.key].note" placeholder="备注" clearable />
          </div>
        </el-card>

        <el-card shadow="never" class="mb-12px action-card">
          <template #header>6) 上肢过渡评估</template>
          <el-divider content-position="left">6.1 肩水平外展测试</el-divider>
          <div v-for="item in UE_HORIZONTAL_ABDUCTION_ITEMS" :key="`ue-horizontal-${item.key}`" class="binary-row binary-row-wide">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.transition_assessments.upper_extremity_transition.horizontal_abduction_test.full_view[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.upper_extremity_transition.horizontal_abduction_test.full_view[item.key].left">左侧</el-checkbox>
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.upper_extremity_transition.horizontal_abduction_test.full_view[item.key].right">右侧</el-checkbox>
            <el-input v-model="localData.transition_assessments.upper_extremity_transition.horizontal_abduction_test.full_view[item.key].note" placeholder="备注" clearable />
          </div>

          <el-divider content-position="left">6.2 肩旋转测试</el-divider>
          <div v-for="item in UE_ROTATION_SHOULDER_ITEMS" :key="`ue-rotation-shoulder-${item.key}`" class="binary-row binary-row-wide">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.transition_assessments.upper_extremity_transition.rotation_test.shoulder[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.upper_extremity_transition.rotation_test.shoulder[item.key].left">左侧</el-checkbox>
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.upper_extremity_transition.rotation_test.shoulder[item.key].right">右侧</el-checkbox>
            <el-input v-model="localData.transition_assessments.upper_extremity_transition.rotation_test.shoulder[item.key].note" placeholder="备注" clearable />
          </div>
          <div v-for="item in UE_ROTATION_HUMERUS_ITEMS" :key="`ue-rotation-humerus-${item.key}`" class="binary-row binary-row-wide">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.transition_assessments.upper_extremity_transition.rotation_test.humerus[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.upper_extremity_transition.rotation_test.humerus[item.key].left">左侧</el-checkbox>
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.upper_extremity_transition.rotation_test.humerus[item.key].right">右侧</el-checkbox>
            <el-input v-model="localData.transition_assessments.upper_extremity_transition.rotation_test.humerus[item.key].note" placeholder="备注" clearable />
          </div>
          <el-row :gutter="12" class="mb-10px">
            <el-col :span="8">
              <el-form-item label="与墙角度(°)">
                <el-input-number
                  v-model="localData.transition_assessments.upper_extremity_transition.rotation_test.humerus_wall_angle_deg"
                  :min="0"
                  :max="180"
                  class="!w-full"
                />
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-alert
                :closable="false"
                type="warning"
                title="TODO：后续接规则判断（文档提示：与墙壁角度大于20°可视为异常趋势）。"
              />
            </el-col>
          </el-row>
          <el-input
            v-model="localData.transition_assessments.upper_extremity_transition.rotation_test.note"
            type="textarea"
            :rows="2"
            placeholder="肩旋转测试备注"
          />

          <el-divider content-position="left">6.3 肩屈曲测试</el-divider>
          <div v-for="item in UE_FLEXION_ITEMS" :key="`ue-flexion-${item.key}`" class="binary-row binary-row-wide">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.transition_assessments.upper_extremity_transition.flexion_test.full_view[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.upper_extremity_transition.flexion_test.full_view[item.key].left">左侧</el-checkbox>
            <el-checkbox v-if="item.hasSide" v-model="localData.transition_assessments.upper_extremity_transition.flexion_test.full_view[item.key].right">右侧</el-checkbox>
            <el-input v-model="localData.transition_assessments.upper_extremity_transition.flexion_test.full_view[item.key].note" placeholder="备注" clearable />
          </div>
        </el-card>

        <el-card shadow="never" class="action-card">
          <template #header>7) 星行平衡偏移测试</template>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-card shadow="never" class="support-card">
                <template #header>左侧支撑</template>
                <div v-for="item in STAR_EXCURSION_CONTROLS" :key="`star-left-${item.key}`" class="control-row">
                  <div class="obs-label">{{ item.label }}</div>
                  <el-select v-model="localData.transition_assessments.star_excursion_balance_deviation_test.left_side[item.key]" class="!w-full">
                    <el-option v-for="opt in item.options" :key="`${item.key}-left-${opt.value}`" :label="opt.label" :value="opt.value" />
                  </el-select>
                </div>
                <el-input
                  v-model="localData.transition_assessments.star_excursion_balance_deviation_test.left_side.note"
                  type="textarea"
                  :rows="2"
                  placeholder="左侧备注"
                />
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never" class="support-card">
                <template #header>右侧支撑</template>
                <div v-for="item in STAR_EXCURSION_CONTROLS" :key="`star-right-${item.key}`" class="control-row">
                  <div class="obs-label">{{ item.label }}</div>
                  <el-select v-model="localData.transition_assessments.star_excursion_balance_deviation_test.right_side[item.key]" class="!w-full">
                    <el-option v-for="opt in item.options" :key="`${item.key}-right-${opt.value}`" :label="opt.label" :value="opt.value" />
                  </el-select>
                </div>
                <el-input
                  v-model="localData.transition_assessments.star_excursion_balance_deviation_test.right_side.note"
                  type="textarea"
                  :rows="2"
                  placeholder="右侧备注"
                />
              </el-card>
            </el-col>
          </el-row>
        </el-card>
      </el-collapse-item>

      <el-collapse-item name="dynamic" title="动态动作评估">
        <el-card shadow="never" class="mb-12px action-card">
          <template #header>1) 步态分析</template>
          <div v-for="item in GAIT_ANALYSIS_ITEMS" :key="`gait-${item.key}`" class="binary-row binary-row-wide">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.dynamic_assessments.gait_analysis[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-checkbox v-if="item.hasSide" v-model="localData.dynamic_assessments.gait_analysis[item.key].left">左侧</el-checkbox>
            <el-checkbox v-if="item.hasSide" v-model="localData.dynamic_assessments.gait_analysis[item.key].right">右侧</el-checkbox>
            <el-input v-model="localData.dynamic_assessments.gait_analysis[item.key].note" placeholder="备注" clearable />
          </div>
        </el-card>

        <el-card shadow="never" class="action-card">
          <template #header>2) 团身跳评估（10s）</template>
          <el-divider content-position="left">A. 膝和大腿动作</el-divider>
          <div v-for="item in TUCK_JUMP_KNEE_THIGH_ITEMS" :key="`tuck-knee-${item.key}`" class="binary-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.dynamic_assessments.tuck_jump_assessment.categories.knee_thigh_action[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-input v-model="localData.dynamic_assessments.tuck_jump_assessment.categories.knee_thigh_action[item.key].note" placeholder="备注" clearable />
          </div>

          <el-divider content-position="left">B. 落地时足的位置</el-divider>
          <div v-for="item in TUCK_JUMP_FOOT_LANDING_ITEMS" :key="`tuck-foot-${item.key}`" class="binary-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.dynamic_assessments.tuck_jump_assessment.categories.foot_landing_position[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-input v-model="localData.dynamic_assessments.tuck_jump_assessment.categories.foot_landing_position[item.key].note" placeholder="备注" clearable />
          </div>

          <el-divider content-position="left">C. 快速伸缩复合训练技术</el-divider>
          <div v-for="item in TUCK_JUMP_PLYO_TECHNIQUE_ITEMS" :key="`tuck-plyo-${item.key}`" class="binary-row">
            <div class="obs-label">{{ item.label }}</div>
            <el-switch v-model="localData.dynamic_assessments.tuck_jump_assessment.categories.plyometric_technique[item.key].present" inline-prompt active-text="是" inactive-text="否" />
            <el-input v-model="localData.dynamic_assessments.tuck_jump_assessment.categories.plyometric_technique[item.key].note" placeholder="备注" clearable />
          </div>

          <el-row :gutter="12" class="mt-10px">
            <el-col :span="8">
              <el-form-item label="合计发现数">
                <el-input-number
                  v-model="localData.dynamic_assessments.tuck_jump_assessment.total_findings_count"
                  :min="0"
                  :max="50"
                  class="!w-full"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="时长(秒)">
                <el-input-number v-model="localData.dynamic_assessments.tuck_jump_assessment.duration_sec" :min="1" :max="120" class="!w-full" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-card>
      </el-collapse-item>

      <el-collapse-item name="davies" title="上肢戴维斯测试">
        <el-card shadow="never" class="action-card">
          <template #header>
            <div class="flex items-center justify-between">
              <span>Upper Extremity Davies Test</span>
              <el-button size="small" type="primary" plain @click="handleAddDaviesTrial">新增 Trial</el-button>
            </div>
          </template>

          <el-table :data="localData.upper_extremity_davies_test.trials" border size="small">
            <el-table-column label="Trial" width="80">
              <template #default="scope">
                <el-input-number v-model="scope.row.trial_no" :min="1" :max="99" class="!w-full" />
              </template>
            </el-table-column>
            <el-table-column label="距离(inch)" width="130">
              <template #default="scope">
                <el-input-number v-model="scope.row.point_distance_inch" :min="0" :max="200" class="!w-full" />
              </template>
            </el-table-column>
            <el-table-column label="距离(cm)" width="130">
              <template #default="scope">
                <el-input-number v-model="scope.row.point_distance_cm" :min="0" :max="500" class="!w-full" />
              </template>
            </el-table-column>
            <el-table-column label="时长(秒)" width="120">
              <template #default="scope">
                <el-input-number v-model="scope.row.duration_sec" :min="1" :max="300" class="!w-full" />
              </template>
            </el-table-column>
            <el-table-column label="次数" width="120">
              <template #default="scope">
                <el-input-number v-model="scope.row.repetition_count" :min="0" :max="500" class="!w-full" />
              </template>
            </el-table-column>
            <el-table-column label="质量备注" min-width="220">
              <template #default="scope">
                <el-input v-model="scope.row.repetition_quality_note" placeholder="动作质量备注" clearable />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="scope">
                <el-button link type="danger" :disabled="localData.upper_extremity_davies_test.trials.length <= 1" @click="handleRemoveDaviesTrial(scope.$index)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="summary-tip">总次数（自动汇总）：{{ localData.upper_extremity_davies_test.total_repetition_count ?? '-' }}</div>
        </el-card>
      </el-collapse-item>

      <el-collapse-item name="less" title="落地错误评估系统测试（LESS）">
        <el-card shadow="never" class="action-card">
          <template #header>
            <div class="flex items-center justify-between">
              <span>LESS 评分录入</span>
              <el-button size="small" @click="handleEstimateLessScore">根据当前选择估算总分</el-button>
            </div>
          </template>

          <el-table :data="LESS_ITEMS_CONFIG" border size="small">
            <el-table-column label="评估项目" min-width="320">
              <template #default="scope">
                <div>{{ scope.row.label }}</div>
              </template>
            </el-table-column>
            <el-table-column label="结果" width="220">
              <template #default="scope">
                <el-select v-model="localData.less_test.items[scope.row.key].value" clearable class="!w-full" placeholder="请选择">
                  <el-option v-for="opt in scope.row.options" :key="`${scope.row.key}-${opt.value}`" :label="opt.label" :value="opt.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="220">
              <template #default="scope">
                <el-input v-model="localData.less_test.items[scope.row.key].note" placeholder="备注" clearable />
              </template>
            </el-table-column>
          </el-table>

          <el-row :gutter="12" class="mt-10px">
            <el-col :span="8">
              <el-form-item label="LESS 总分">
                <el-input-number v-model="localData.less_test.less_total_score" :min="0" :max="100" class="!w-full" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-card>
      </el-collapse-item>

      <el-collapse-item name="summary" title="评估结果汇总">
        <el-card shadow="never" class="action-card">
          <template #header>过渡动作评估汇总（手工）</template>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="头颈"><el-input v-model="localData.summary.transition_summary.head_neck" clearable /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="肩"><el-input v-model="localData.summary.transition_summary.shoulder" clearable /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="肘"><el-input v-model="localData.summary.transition_summary.elbow" clearable /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="LPHC"><el-input v-model="localData.summary.transition_summary.lphc" clearable /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="膝"><el-input v-model="localData.summary.transition_summary.knee" clearable /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="踝足"><el-input v-model="localData.summary.transition_summary.ankle_foot" clearable /></el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="动态动作汇总备注">
            <el-input v-model="localData.summary.dynamic_summary_note" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="总体 CES 汇总">
            <el-input v-model="localData.summary.overall_ces_summary" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="综合备注">
            <el-input v-model="localData.notes.general_note" type="textarea" :rows="2" />
          </el-form-item>
        </el-card>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script lang="ts" setup>
import { ElMessage } from 'element-plus'
import { isEqual } from 'lodash-es'
import { reactive, ref, watch } from 'vue'
import {
  buildDefaultNasmCesFormData,
  countTuckJumpFindings,
  estimateLessScore,
  GAIT_ANALYSIS_ITEMS,
  LESS_ITEMS_CONFIG,
  mergeNasmCesFormData,
  OVERHEAD_SQUAT_FRONT_FEET,
  OVERHEAD_SQUAT_FRONT_KNEE,
  OVERHEAD_SQUAT_LATERAL_LPHC,
  OVERHEAD_SQUAT_LATERAL_SHOULDER,
  OVERHEAD_SQUAT_POSTERIOR_FEET,
  OVERHEAD_SQUAT_POSTERIOR_LPHC,
  PUSH_UP_HEAD_ITEMS,
  PUSH_UP_LPHC_ITEMS,
  PUSH_UP_SHOULDER_ITEMS,
  SINGLE_LEG_SQUAT_ITEMS,
  STANDING_PRESS_ITEMS,
  STANDING_ROW_ITEMS,
  STAR_EXCURSION_CONTROLS,
  sumDaviesRepetitions,
  TUCK_JUMP_FOOT_LANDING_ITEMS,
  TUCK_JUMP_KNEE_THIGH_ITEMS,
  TUCK_JUMP_PLYO_TECHNIQUE_ITEMS,
  UE_FLEXION_ITEMS,
  UE_HORIZONTAL_ABDUCTION_ITEMS,
  UE_ROTATION_HUMERUS_ITEMS,
  UE_ROTATION_SHOULDER_ITEMS,
  type NasmCesFormData,
  type NasmDaviesTrial
} from '@/views/rehab/assessment/config/nasmCesConfig'

interface AssessmentBaseInfoSnapshot {
  name?: string
  age?: number | null
  assessment_date?: string
  assessor?: string
  focus?: string
}

const props = defineProps<{
  modelValue?: Record<string, any>
  assessmentBaseInfo?: AssessmentBaseInfoSnapshot
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', value: Record<string, any>): void
}>()

const activePanels = ref(['transition', 'dynamic', 'davies', 'less', 'summary'])
const localData = reactive<NasmCesFormData>(buildDefaultNasmCesFormData())

const deepClone = (value: any) => JSON.parse(JSON.stringify(value))

const setReactiveObject = (target: Record<string, any>, value: Record<string, any>) => {
  Object.keys(target).forEach((key) => delete target[key])
  Object.assign(target, value)
}

const applyBaseInfoSnapshot = () => {
  const snapshot = props.assessmentBaseInfo || {}
  const basic = localData.basic_info
  if (!basic.name && snapshot.name) {
    basic.name = snapshot.name
  }
  if ((basic.age === null || basic.age === undefined) && snapshot.age != null) {
    basic.age = snapshot.age
  }
  if (!basic.assessment_date && snapshot.assessment_date) {
    basic.assessment_date = snapshot.assessment_date
  }
  if (!basic.assessor && snapshot.assessor) {
    basic.assessor = snapshot.assessor
  }
  if (!basic.focus && snapshot.focus) {
    basic.focus = snapshot.focus
  }
}

const syncDerivedValues = () => {
  localData.upper_extremity_davies_test.total_repetition_count = sumDaviesRepetitions(
    localData.upper_extremity_davies_test.trials
  )
  localData.dynamic_assessments.tuck_jump_assessment.total_findings_count = countTuckJumpFindings(
    localData.dynamic_assessments.tuck_jump_assessment.categories
  )
}

const resetLocalData = (value?: Record<string, any>) => {
  const merged = mergeNasmCesFormData(value)
  setReactiveObject(localData as unknown as Record<string, any>, merged as unknown as Record<string, any>)
  applyBaseInfoSnapshot()
  syncDerivedValues()
}

const handleAddDaviesTrial = () => {
  const list = localData.upper_extremity_davies_test.trials
  const nextNo = (list[list.length - 1]?.trial_no || list.length) + 1
  const trial: NasmDaviesTrial = {
    trial_no: nextNo,
    point_distance_inch: 36,
    point_distance_cm: 91.44,
    duration_sec: 15,
    repetition_count: null,
    repetition_quality_note: ''
  }
  list.push(trial)
}

const handleRemoveDaviesTrial = (index: number) => {
  if (localData.upper_extremity_davies_test.trials.length <= 1) {
    return
  }
  localData.upper_extremity_davies_test.trials.splice(index, 1)
}

const handleEstimateLessScore = () => {
  localData.less_test.less_total_score = estimateLessScore(localData.less_test.items)
  ElMessage.success('已根据当前已填项估算 LESS 总分（可手工修改）')
}

const getFormData = () => {
  const payload = deepClone(localData)
  delete payload.ces_summary
  delete payload.action_summaries
  delete payload.risk_precheck
  delete payload.report_mapping
  return payload
}

watch(
  () => props.modelValue,
  (value) => {
    // 子组件 emit 后，父组件会把相同数据立即回传。相同内容不再重置本地
    // reactive 对象，避免 ElForm 与内部 ElTable 进入递归更新。
    if (isEqual(value || {}, getFormData())) {
      return
    }
    resetLocalData(value || {})
  },
  { immediate: true }
)

watch(
  () => props.assessmentBaseInfo,
  () => {
    applyBaseInfoSnapshot()
  },
  { immediate: true, deep: true }
)

watch(
  localData,
  () => {
    syncDerivedValues()
    const payload = getFormData()
    if (!isEqual(props.modelValue || {}, payload)) {
      emit('update:modelValue', payload)
    }
    emit('change', payload)
  },
  { deep: true }
)

const validate = async () => {
  if (!localData.basic_info.assessment_date) {
    ElMessage.warning('建议填写 NASM-CES 评估日期，便于后续汇总追溯')
  }
  return true
}

const reset = () => {
  resetLocalData(buildDefaultNasmCesFormData() as unknown as Record<string, any>)
  emit('update:modelValue', getFormData())
  emit('change', getFormData())
}

defineExpose({ validate, getFormData, reset })
</script>

<style scoped>
.assessment-form-shell {
  border: 1px solid var(--el-border-color-light);
}

.action-card {
  margin-bottom: 12px;
}

.support-card {
  min-height: 240px;
}

.obs-label {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.bilateral-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr 0.9fr 1.2fr;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.binary-row {
  display: grid;
  grid-template-columns: 1.4fr 0.7fr 1.4fr;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.binary-row-wide {
  grid-template-columns: 1.1fr 0.6fr 0.6fr 0.6fr 1.2fr;
}

.control-row {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.summary-tip {
  margin-top: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
