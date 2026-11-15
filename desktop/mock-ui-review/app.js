/* Rehab Mode A: browser-memory synthetic data only. NO fetch, XHR, WebSocket,
   EventSource, localStorage, cookies, real users, tokens or external services. */
'use strict';

const ROLE_LABELS = {
  qa: 'QA 全功能预览', admin: '模拟机构管理员', therapist: '模拟治疗师',
  clerk: '模拟前台文员', patient: '模拟患者', owner: '模拟 Owner'
};
const ACCESS = {
  dashboard: ['qa','admin','therapist','clerk','owner'],
  patients: ['qa','admin','therapist','clerk'],
  episodes: ['qa','admin','therapist','clerk'],
  assessments: ['qa','admin','therapist'],
  reports: ['qa','admin','therapist'],
  plans: ['qa','admin','therapist'],
  reassessment: ['qa','admin','therapist'],
  hep: ['qa','admin','therapist'],
  patientHome: ['qa','patient'],
  risks: ['qa','admin','therapist'],
  motion: ['qa','admin','therapist'],
  owner: ['qa','owner'],
  ops: ['qa','admin'],
  audit: ['qa','admin','owner'],
  guide: ['qa','admin','therapist','clerk','patient','owner'],
};
const NAV = [
  { title:'工作空间', items:[
    ['dashboard','总览工作台','01'], ['patients','患者档案','02'],
    ['episodes','疗程管理','03'], ['assessments','初评 · FMS','04'],
    ['reports','报告中心','05'], ['plans','康复计划','06'], ['reassessment','复评追踪','07']]},
  { title:'训练与评估', items:[
    ['hep','HEP 治疗师端','08'], ['patientHome','HEP 患者端','09'],
    ['motion','动作评估样例','10'], ['risks','风险反馈','11']]},
  { title:'机构管理', items:[
    ['owner','机构审批演示','12'], ['ops','运营看板','13'],
    ['audit','模拟操作日志','14'], ['guide','测试指南','?']]},
];
const FMS = ['深蹲','跨栏步','直线弓箭步','肩部灵活性','主动直腿抬高','躯干稳定俯卧撑','旋转稳定性'];
const nowLabel = '2026-11-12';
const INITIAL = () => ({
  patients:[
    {id:'DEMO-P001',name:'模拟成人甲',age:32,sex:'男',tenant:'DEMO-T001',status:'在治',therapist:'模拟治疗师 A',goal:'下肢力量与动作控制'},
    {id:'DEMO-P002',name:'模拟成人乙',age:41,sex:'女',tenant:'DEMO-T001',status:'随访',therapist:'模拟治疗师 B',goal:'肩部活动度'},
    {id:'DEMO-P003',name:'模拟成人丙',age:56,sex:'男',tenant:'DEMO-T002',status:'初评',therapist:'模拟治疗师 C',goal:'步态平衡'}],
  episodes:[
    {id:'DEMO-E001',patient:'DEMO-P001',goal:'动作控制与深蹲质量改善',stage:'训练中',date:'2026-11-04'},
    {id:'DEMO-E002',patient:'DEMO-P002',goal:'改善肩关节灵活性',stage:'复评待办',date:'2026-11-01'}],
  scores:[2,2,2,2,3,2,2], assessmentSaved:false,
  reports:[{id:'DEMO-R001',patient:'DEMO-P001',kind:'初评报告',status:'草稿',date:nowLabel}],
  plans:[{id:'DEMO-PL001',patient:'DEMO-P001',name:'基础动作控制计划',visits:'每周 2 次',status:'草稿',goal:'下肢稳定性'}],
  actions:[
    {id:'DEMO-M001',name:'椅子辅助深蹲',dose:'3 组 × 10 次',type:'下肢'},
    {id:'DEMO-M002',name:'弹力带划船',dose:'3 组 × 12 次',type:'上肢'},
    {id:'DEMO-M003',name:'站姿单腿平衡',dose:'每侧 3 × 20 秒',type:'平衡'},
    {id:'DEMO-M004',name:'肩胛控制练习',dose:'3 组 × 10 次',type:'肩部'}],
  templates:[{id:'DEMO-TP001',name:'基础平衡训练模板',days:'每周 3 天',status:'演示'}],
  prescriptions:[{id:'DEMO-RX001',patient:'DEMO-P001',name:'家庭基础训练',status:'未签发草稿',exercises:3}],
  sessions:[{id:'DEMO-S001',patient:'DEMO-P001',date:'2026-11-11',pain:3,rpe:4,done:3}],
  alerts:[{id:'DEMO-AL001',patient:'DEMO-P001',title:'模拟疼痛风险待复核',detail:'上次训练后疼痛评分 7/10（纯演示）',status:'待处理',level:'高'}],
  trials:[{id:'DEMO-MOT001',name:'模拟深蹲试次',quality:'可用候选',rom:'膝关节 90°（虚构值）',status:'未采纳'},
          {id:'DEMO-MOT002',name:'模拟步行试次',quality:'待同步',rom:'髋关节 31°（虚构值）',status:'未采纳'}],
  organizations:[{id:'DEMO-T001',name:'演示康复工作室',status:'模拟已批准',contact:'演示联系人'},
                 {id:'DEMO-T002',name:'演示健康管理机构',status:'模拟待审批',contact:'虚构负责人'}],
  audit:[{id:'DEMO-AUD001',event:'创建演示工作区与合成数据',actor:'系统模拟器',time:'10:00'}]
});
let data = INITIAL();
let actor = 'qa', activeView = 'dashboard', selectedPatient = 'DEMO-P001';
let search = '', modalKind = '', toastText = '', activeSession = false, reviewedTrial = '';
let lastNo = 100;
const pages = new Set(NAV.flatMap(g => g.items.map(item => item[0])));
const $ = selector => document.querySelector(selector);
const esc = value => String(value ?? '').replace(/[&<>"']/g, ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const randomId = prefix => `DEMO-${prefix}${String(++lastNo).padStart(3,'0')}`;
const count = (kind) => data[kind].length;
const patientName = id => data.patients.find(p => p.id === id)?.name ?? '虚构对象（未知）';
const countRisk = () => data.alerts.filter(a => a.status === '待处理').length;
const allowed = view => (ACCESS[view] || []).includes(actor);
function logEvent(event){data.audit.unshift({id:randomId('AUD'),event,actor:ROLE_LABELS[actor],time:new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})});}
function toast(message){toastText=message;const n=$('#toast');if(n){n.textContent=message;n.hidden=false;} setTimeout(()=>{if(toastText===message){const el=$('#toast');if(el)el.hidden=true;}},3400)}
function navButton(id,name,code){return `<button class="nav-item ${id===activeView?'active':''}" data-view="${id}" type="button"><span class="nav-icon">${code}</span><span>${name}</span></button>`}
const pills = (label,type='grey') => `<span class="pill ${type}">${esc(label)}</span>`;
const field = (label,name,value='',type='text',extra='') => `<label class="field">${esc(label)}<input name="${esc(name)}" type="${type}" value="${esc(value)}" ${extra} required></label>`;
const action = (label,code,klass='mini') => `<button class="btn ${klass}" data-action="${code}" type="button">${esc(label)}</button>`;
const openBtn = (label,view,klass='') => `<button class="btn ${klass}" type="button" data-view="${view}">${esc(label)}</button>`;
function screen(title,sub,body,buttons='',label='SYNTHETIC WORKSPACE'){
 return `<header class="heading"><div><div class="eyebrow">${esc(label)}</div><h1 class="page-title">${esc(title)}</h1><p class="page-sub">${esc(sub)}</p></div><div class="buttons">${buttons}</div></header>${body}`;
}
function table(headers,rows){return `<div class="table-wrap"><table><thead><tr>${headers.map(x=>`<th>${esc(x)}</th>`).join('')}</tr></thead><tbody>${rows.join('') || `<tr><td colspan="${headers.length}" class="empty">暂无模拟记录</td></tr>`}</tbody></table></div>`}
function dashboard(){
 let stat=[['模拟患者',count('patients'),'虚构档案'],['进行中疗程',count('episodes'),'使用模拟ID'],['待处理风险',countRisk(),'仅本地提醒'],['HEP草稿',count('prescriptions'),'未签发']];
 let metrics=`<div class="metrics">${stat.map(([n,v,t])=>`<section class="card metric"><div class="label">${n}<span class="badge">DEMO</span></div><div class="value">${v.toString().padStart(2,'0')}</div><div class="trend">${t}</div></section>`).join('')}</div>`;
 let quick=[['patients','患者档案','查看、搜索与创建'],['assessments','初评 FMS','七项评分草稿'],['plans','康复计划','动作与剂量'],['hep','HEP 工作台','动作与模拟处方'],['patientHome','患者训练','疼痛 / RPE / 打卡'],['reports','报告草稿','非正式签发']];
 let recent=data.audit.slice(0,5).map(a=>`<li><div><b>${esc(a.event)}</b><div class="muted">${esc(a.actor)} · ${esc(a.id)}</div></div><small>${esc(a.time)}</small></li>`).join('');
 return screen('康复工作台','欢迎进入独立的模式 A。所有数据仅在本页内存中演示，任何操作都不会触达正式服务。',
 metrics+`<div class="cols-2"><section class="card card-pad"><h2 class="card-title">快捷入口 <span class="card-note">常用业务 · 即点即用</span></h2><div class="quick-grid">${quick.map(([id,n,tip])=>`<button data-view="${id}" class="quick" type="button"><strong>${n}</strong><small>${tip}</small></button>`).join('')}</div></section><section class="card card-pad"><h2 class="card-title">最近模拟操作 <span class="card-note">非正式审计</span></h2><ul class="list">${recent}</ul></section></div><div class="footer-note">模式 A 只验收 UI 导航与模拟交互；正式身份、签名、数据持久化及临床真实性不属于本页通过条件。</div>`,openBtn('查看 MVP 边界','guide'));
}
function patientRows(){
 const q=search.trim().toLowerCase();
 return data.patients.filter(p=>[p.name,p.id,p.goal].some(v=>v.toLowerCase().includes(q))).map(p=>`<tr><td class="tid">${esc(p.id)}</td><td><div class="name">${esc(p.name)}</div><div class="subline">${p.age} 岁 · ${p.sex}</div></td><td>${esc(p.therapist)}</td><td>${esc(p.goal)}</td><td>${pills(p.status,p.status==='在治'?'green':'amber')}</td><td><button class="btn mini" data-action="select-patient" data-id="${esc(p.id)}">查看详情</button></td></tr>`).join('');
}
function patients(){
 const p=data.patients.find(x=>x.id===selectedPatient) || data.patients[0];
 let details=p?`<div class="detail-hero"><div class="avatar">${esc(p.name.slice(-1))}</div><div><strong>${esc(p.name)} · 模拟成年人</strong><div class="meta">${esc(p.id)} · ${esc(p.tenant)} · ${esc(p.therapist)}</div></div><div class="buttons m-auto">${pills(p.status,'green')}</div></div><div class="tag-row mt12"><span class="tag">运动康复</span><span class="tag">${esc(p.goal)}</span><span class="tag">无真实个人信息</span></div>`:'';
 return screen('患者档案','查看、筛选和新增虚构成人患者；不会连接真实患者主数据库。',
 `<section class="card card-pad"><div class="card-title">模拟患者列表 <span class="card-note">共 ${data.patients.length} 条</span></div><div class="search-bar"><input id="patientSearch" class="search" placeholder="输入模拟编号、姓名或目标搜索" aria-label="搜索模拟患者" value="${esc(search)}"></div><div id="patientTable">${table(['模拟患者ID','患者','治疗师','训练目标','状态','操作'],patientRows()?[patientRows()]:[])}</div></section><section class="card card-pad mt16"><h2 class="card-title">已选患者 · 信息摘要</h2>${details}</section>`,action('新建模拟患者','add-patient','primary'));
}
function episodes(){
 let rows=data.episodes.map(e=>`<tr><td class="tid">${esc(e.id)}</td><td>${esc(patientName(e.patient))}</td><td>${esc(e.goal)}</td><td>${esc(e.date)}</td><td>${pills(e.stage,e.stage==='训练中'?'green':'amber')}</td></tr>`);
 return screen('疗程管理','在同一模拟患者下建立多个疗程，检查关联、状态和列表展示。',`<section class="card card-pad"><h2 class="card-title">Episode 列表 <span class="card-note">仅演示</span></h2>${table(['疗程ID','模拟患者','治疗目标','开始日期','状态'],rows)}</section><div class="rule mt17">模拟疗程不具有真实诊疗记录或病历法律效力。正式 Episode 新建与权限控制需要模式 B 单独验收。</div>`,action('新建模拟疗程','add-episode','primary'));
}
function assessments(){
 const total=data.scores.reduce((a,b)=>a+b,0);
 let scores=FMS.map((name,i)=>`<div class="score"><div><strong>${i+1}. ${name}</strong><small>0–3 分 · 此值为虚构演示</small></div><button class="score-button" data-action="change-fms" data-index="${i}" aria-label="调整${name}评分">${data.scores[i]}/3</button></div>`).join('');
 return screen('临床初评 · FMS','点击分数按钮循环调整七项模拟评分，再保存草稿；不提供诊断建议。',
 `<div class="cols-2"><section class="card card-pad"><h2 class="card-title">FMS 七项检查 · DEMO-A001 <span class="card-note">模拟成人甲</span></h2><div class="scores">${scores}</div><div class="rule mt16">FMS 评分需由有资格的专业人员依据对应版本标准操作。此页面仅演示表单交互，不宣称临床信效度。</div></section><section class="stack"><section class="card card-pad"><h2 class="card-title">评分草稿摘要</h2><div class="mini-kpi">${total} <small>/ 21 分（模拟）</small></div><p class="page-sub">${data.assessmentSaved?'已保存至本页内存':'尚未执行本次模拟保存'}</p><div class="tag-row mt18">${pills('临床审核未进行','amber')}${pills('无正式签名','grey')}</div></section><section class="card card-pad"><h2 class="card-title">下一步</h2><p class="section-intro">保存后可到“报告中心”生成未签发草稿，再进入训练计划。所有内容刷新后重置。</p>${openBtn('打开报告草稿','reports')}</section></section></div>`,action('保存模拟初评草稿','save-assessment','primary'));
}
function reports(){
 let rows=data.reports.map(r=>`<tr><td class="tid">${esc(r.id)}</td><td>${esc(patientName(r.patient))}</td><td>${esc(r.kind)}</td><td>${esc(r.date)}</td><td>${pills(r.status,'amber')}</td><td><button class="btn mini" data-action="preview-report" data-id="${esc(r.id)}">预览草稿</button></td></tr>`);
 return screen('报告中心','查看虚构报告草稿；生成、预览不等于复核、签发或导出真实临床报告。',
 `<div class="rule warning">正式临床签发、版本锁定、PDF 法律文书与治疗师审核均不可在模式 A 执行。此处仅显示“未签发草稿”。</div><section class="card card-pad"><h2 class="card-title">演示报告列表</h2>${table(['报告ID','患者','类型','日期','状态','操作'],rows)}</section>`,action('生成模拟初评草稿','generate-report','primary'));
}
function plans(){
 const rows=data.plans.map(p=>`<tr><td class="tid">${esc(p.id)}</td><td>${esc(patientName(p.patient))}</td><td><b>${esc(p.name)}</b><div class="subline">${esc(p.goal)}</div></td><td>${esc(p.visits)}</td><td>${pills(p.status,'amber')}</td></tr>`);
 return screen('康复训练计划','从模拟评估过渡到训练计划和家庭处方，支持新增演示计划草稿。',
 `<div class="steps"><div class="step"><div class="num">STEP 01</div><strong>初评</strong><small>模拟 FMS 数据</small></div><div class="step"><div class="num">STEP 02</div><strong>目标</strong><small>训练目标拟定</small></div><div class="step"><div class="num">STEP 03</div><strong>剂量</strong><small>动作与次数</small></div><div class="step"><div class="num">STEP 04</div><strong>复评</strong><small>示例趋势对照</small></div></div><section class="card card-pad mt16"><h2 class="card-title">模拟训练计划</h2>${table(['计划ID','患者','计划与目标','频次','状态'],rows)}</section>`,action('新增模拟计划','add-plan','primary'));
}
function reassessment(){
 const vals=[['总分示例','13/21','15/21'],['下肢稳定性','需要改善','模拟提升'],['疼痛 NRS','4/10','2/10'],['HEP 依从性','初评无数据','演示 75%']];
 return screen('复评追踪','将同一模拟患者的初评、训练计划和复评草稿串联；趋势值纯属虚构。',
 `<section class="card card-pad"><h2 class="card-title">DEMO-P001 · 初评/复评对照 <span class="card-note">模拟比较，不用于治疗决策</span></h2>${table(['指标','初评（模拟）','复评（模拟）'],vals.map(([a,b,c])=>`<tr><td class="name">${a}</td><td>${b}</td><td>${pills(c,'green')}</td></tr>`))}</section><div class="rule mt16">真实复评应沿用可靠测量协议，治疗师审核，记录版本和来源，并经身份授权签发。</div>`,openBtn('查看初评','assessments'));
}
function hep(){
 const moves=data.actions.map(a=>`<div class="exercise"><div class="glyph">M</div><div class="info"><strong>${esc(a.name)}</strong><small>${esc(a.id)} · ${esc(a.type)} · ${esc(a.dose)}</small></div>${pills('可用于演示','green')}</div>`).join('');
 let rx=data.prescriptions.map(r=>`<li><div><b>${esc(r.name)}</b><div class="muted">${esc(r.id)} · ${esc(patientName(r.patient))} · ${r.exercises} 项演示动作</div></div>${pills(r.status,'amber')}</li>`).join('');
 return screen('HEP 家庭康复 · 治疗师工作台','演示动作库、训练模板与处方草稿；不下发患者、不使用正式电子签名。',
 `<div class="rule warning">这个页面只测试 UI 和模拟数据。正式 Core 037 的 HEP 接口、签名 JWT 和处方下发仍须通过独立的模式 B 验收。</div><div class="cols-2"><section class="card card-pad"><h2 class="card-title">动作库（演示） <span class="card-note">${data.actions.length} 项</span></h2><div class="stack gap9">${moves}</div><div class="form-actions">${action('添加演示动作','add-action')}</div></section><div class="stack"><section class="card card-pad"><h2 class="card-title">训练模板</h2><ul class="list">${data.templates.map(t=>`<li><div><b>${esc(t.name)}</b><div class="muted">${esc(t.days)} · ${esc(t.id)}</div></div>${pills(t.status,'purple')}</li>`).join('')}</ul><div class="form-actions">${action('新增模拟模板','add-template')}</div></section><section class="card card-pad"><h2 class="card-title">HEP 处方草稿</h2><ul class="list">${rx}</ul><div class="form-actions">${action('新建处方草稿','add-prescription')}</div></section></div></div>`,openBtn('切换患者训练界面','patientHome'));
}
function patientHome(){
 let sessions=data.sessions.filter(s=>s.patient==='DEMO-P001');
 let exercise=data.actions.slice(0,3).map((a,i)=>`<div class="exercise"><div class="glyph">${i+1}</div><div class="info"><strong>${esc(a.name)}</strong><small>${esc(a.dose)}</small></div>${pills(activeSession?'正在训练':'待开始',activeSession?'green':'grey')}</div>`).join('');
 return screen('今日家庭训练','模拟患者 · DEMO-P001。点击“开始训练”，记录疼痛与 RPE 后提交本地打卡。',
 `<div class="rule warning">这里是**患者界面原型**：不存在真实患者登录、视频播放、医疗提醒或数据回传。高疼痛只会触发页面内的模拟风险标签。</div><div class="cols-2"><section class="card card-pad"><h2 class="card-title">今日处方 · 家庭基础训练 ${pills('模拟未签发','amber')}</h2><div class="stack gap9">${exercise}</div><div class="form-actions">${activeSession?pills('训练已开始','green'):action('开始模拟训练','start-session','primary')}</div></section><section class="card card-pad"><h2 class="card-title">完成本次训练</h2>${activeSession?`<form id="checkinForm"><div class="form-grid">${field('训练前疼痛（0-10）','pain','2','number','min="0" max="10" step="1"')}${field('主观用力 RPE（1-10）','rpe','5','number','min="1" max="10" step="1"')}</div><div class="form-actions"><button class="btn primary" type="submit">提交模拟打卡</button></div></form>`:`<div class="empty"><h3>尚未开始</h3><p>开始后可填写 0–10 疼痛评分及 1–10 RPE。</p></div>`}</section></div><section class="card card-pad mt16"><h2 class="card-title">历史演示记录</h2>${table(['记录ID','日期','完成动作','疼痛','RPE'],sessions.map(s=>`<tr><td class="tid">${esc(s.id)}</td><td>${esc(s.date)}</td><td>${s.done} 项</td><td>${s.pain}/10</td><td>${s.rpe}/10</td></tr>`))}</section>`,openBtn('查看风险模拟','risks'));
}
function risks(){
 let a=data.alerts.map(a=>`<div class="risk"><div><strong>${esc(a.title)} · ${esc(patientName(a.patient))}</strong><small>${esc(a.id)} · ${esc(a.detail)}</small></div><div class="buttons">${pills(a.status,a.status==='待处理'?'red':'green')}${a.status==='待处理'?`<button class="btn mini" data-action="handle-risk" data-id="${esc(a.id)}">标记模拟处理</button>`:''}</div></div>`).join('');
 return screen('风险反馈','模拟疼痛/RPE 异常与治疗师处理，无法替代临床高危评估或真实提醒。',
 `<div class="rule danger">发生真实急性症状或高风险异常时，应执行机构已建立的医疗处置与转诊流程；此演示不会发送任何预警。</div><section class="card card-pad"><h2 class="card-title">演示风险列表 <span class="card-note">待处理 ${countRisk()}</span></h2><div class="risk-list">${a || '<p class="empty">暂无演示风险</p>'}</div></section>`,openBtn('患者训练打卡','patientHome'));
}
function motion(){
 let rows=data.trials.map(t=>`<tr><td class="tid">${esc(t.id)}</td><td>${esc(t.name)}</td><td>${pills(t.quality,'amber')}</td><td>${esc(t.rom)}</td><td><button class="btn mini" data-action="review-motion" data-id="${esc(t.id)}">查看示例</button></td></tr>`);
 let chosen=data.trials.find(t=>t.id===reviewedTrial);
 return screen('动作评估 · 示例工作区','以冻结的虚构试次展示 ROM、质量状态和来源占位；不调用 OpenCap 或 OpenSim。',
 `<section class="card card-pad"><h2 class="card-title">模拟试次</h2>${table(['试次ID','动作','数据质量','演示指标','操作'],rows)}</section>${chosen?`<section class="card card-pad mt16"><h2 class="card-title">试次详情 · ${esc(chosen.id)}</h2><div class="tag-row">${pills('无真实视频','grey')}${pills('无有效动力学数据','amber')}</div><p class="section-intro mt16">${esc(chosen.name)}：${esc(chosen.rom)}。此数值完全虚构，不能用于医学判断。</p></section>`:''}`,openBtn('查看初评','assessments'));
}
function owner(){
 let rows=data.organizations.map(o=>`<tr><td class="tid">${esc(o.id)}</td><td class="name">${esc(o.name)}</td><td>${esc(o.contact)}</td><td>${pills(o.status,o.status.includes('批准')?'green':'amber')}</td><td>${o.status.includes('待审批')?`<button class="btn mini" data-action="approve-org" data-id="${esc(o.id)}">模拟批准</button>`:'—'}</td></tr>`);
 return screen('机构入驻 / Owner 演示','演示机构申请和批准按钮，不调用真正的 Owner 控制台或许可证服务。',
 `<div class="rule danger">“模拟批准”仅更改当前页面的虚构机构状态，不会创建真实租户、授权模块、MFA 账号、密钥或许可证。</div><section class="card card-pad"><h2 class="card-title">机构申请队列</h2>${table(['模拟机构ID','机构','联系人','状态','操作'],rows)}</section>`,action('新增虚构机构','add-org','primary'));
}
function ops(){
 let n=data.patients.length, e=data.episodes.length, s=data.sessions.length;
 return screen('运营与工作量','统计仅计算本页虚构对象；可通过创建患者、疗程及训练打卡观察数字更新。',
 `<div class="metrics"><section class="card metric"><div class="label">患者主档</div><div class="value">${n}</div><div class="trend">页面内存</div></section><section class="card metric"><div class="label">疗程总数</div><div class="value">${e}</div><div class="trend">页面内存</div></section><section class="card metric"><div class="label">HEP打卡</div><div class="value">${s}</div><div class="trend">页面内存</div></section><section class="card metric"><div class="label">操作事件</div><div class="value">${data.audit.length}</div><div class="trend">非正式审计</div></section></div><div class="cols-equal"><section class="card card-pad"><h2 class="card-title">模拟阶段分布</h2><div class="bars"><div class="bar"><label>在治患者</label><progress class="progress" max="100" value="${Math.round(data.patients.filter(p=>p.status==='在治').length/Math.max(n,1)*100)}"></progress></div><div class="bar"><label>草稿报告</label><progress class="progress" max="100" value="${Math.min(data.reports.length*20,100)}"></progress></div><div class="bar"><label>已记录训练</label><progress class="progress" max="100" value="${Math.min(s*18,100)}"></progress></div></div></section><section class="card card-pad"><h2 class="card-title">数据使用说明</h2><p class="section-intro">看板所有数字只来自演示内存，无法用于正式管理决策、结算或机构经营考核。</p>${openBtn('查看模拟审计','audit')}</section></div>`);
}
function audit(){
 const rows=data.audit.map(a=>`<div class="audit-row"><span class="n">模拟</span><div><b>${esc(a.event)}</b><div class="subline">${esc(a.actor)} · ${esc(a.id)}</div></div><span class="t">${esc(a.time)}</span></div>`).join('');
 return screen('模拟操作日志','展示本次浏览器操作生成的本地事件；不可作为后端安全审计或签署依据。',
 `<section class="card card-pad"><h2 class="card-title">本页事件流 <span class="card-note">仅内存、可清空</span></h2>${rows}</section>`,action('重置全部模拟数据','reset','danger'));
}
function guide(){
 let area=[['患者 / 疗程','创建成人模拟对象、关联疗程','patients'],['评估 / 报告','七项FMS评分、报告草稿','assessments'],['计划 / HEP','动作、模板、草稿处方','hep'],['患者端 / 风险','训练打卡、疼痛RPE提醒','patientHome'],['Owner / 运营','机构申请与统计演示','owner']];
 return screen('模式 A · 手动测试指南','先验证界面及交互，再进入真实身份和后端集成测试；不得混用两套通过标准。',
 `<div class="cols-equal"><section class="card card-pad"><h2 class="card-title">MVP 核心检查</h2><ul class="list">${area.map(([n,t,id])=>`<li><div><b>${n}</b><div class="muted">${t}</div></div>${openBtn('进入',''+id,'mini')}</li>`).join('')}</ul></section><section class="card card-pad"><h2 class="card-title">必须另行进行的真实验收</h2><ul class="list">${['真实身份 OAuth2、JWT/JWKS 与权限隔离','真实处方与报告复核/签名','Core 037→HEP 业务接口双向联调','OpenCap真实Session/研究资料与数据效度','微信小程序真机、私有媒体与患者授权','备份恢复、数据迁移和审计不可变更'].map(x=>`<li><div><b>${x}</b><div class="muted">模式 A 不计入原始用例通过数</div></div>${pills('模式 B','grey')}</li>`).join('')}</ul></section></div><div class="rule mt16">审查结论：306 条原始测试中没有完全相同的用例名称、操作步骤或判定标准；已将共同准备动作归并为48组执行路径，保留306条独立断言。模式A界面适用122项，不表示122项已完成。</div>`,action('重置模拟数据','reset'));
}
const views={dashboard,patients,episodes,assessments,reports,plans,reassessment,hep,patientHome,risks,motion,owner,ops,audit,guide};
const titleFor=id => NAV.flatMap(g=>g.items).find(i=>i[0]===id)?.[1]||'工作台';
function modal(){
 if(!modalKind)return '';
 const patientsOptions=data.patients.map(p=>`<option value="${esc(p.id)}" ${p.id===selectedPatient?'selected':''}>${esc(p.name)}（${esc(p.id)}）</option>`).join('');
 let title='',html='',formId='';
 if(modalKind==='patient'){
  title='创建模拟患者';formId='patientForm';html=`<div class="form-grid">${field('虚构姓名','name','模拟成人丁')}${field('年龄（18-99）','age','35','number','min="18" max="99"')}${field('康复目标','goal','日常动作能力训练')}${field('模拟治疗师','therapist','模拟治疗师 A')}</div>`;
 }else if(modalKind==='episode'){
  title='新建演示疗程';formId='episodeForm';html=`<div class="form-grid"><label class="field">选择虚构患者<select name="patient" required>${patientsOptions}</select></label>${field('疗程目标','goal','动作控制改善')}</div>`;
 }else if(modalKind==='plan'){
  title='新建康复计划草稿';formId='planForm';html=`<div class="form-grid"><label class="field">模拟患者<select name="patient" required>${patientsOptions}</select></label>${field('计划名称','name','基础体能与协调训练')}${field('剂量/频次','visits','每周 2 次')}</div>`;
 }else if(modalKind==='action'){
  title='新增演示动作';formId='actionForm';html=`<div class="form-grid">${field('动作名称','name','臀桥练习')}${field('训练剂量','dose','3 组 × 12 次')}</div>`;
 }else if(modalKind==='org'){
  title='新建虚构机构申请';formId='orgForm';html=`<div class="form-grid">${field('机构名称','name','演示康复新机构')}${field('虚构联系人','contact','模拟联系人')}</div>`;
 }else if(modalKind==='preview'){
  title='模拟临床报告草稿（不可签发）';html=`<div class="notice"><h2>初评草稿 · DEMO-R001</h2><p>模拟成人甲 / DEMO-P001 / 初评FMS ${data.scores.reduce((a,b)=>a+b,0)}/21（虚构评分）。<br>训练目标：下肢稳定性。<br>报告状态：未审核、未签发，不得用于真实临床。</p></div>`;
 }
 return `<div class="modal-bg" id="modalBackdrop"><div class="modal" role="dialog" aria-modal="true" aria-labelledby="modalTitle"><header class="modal-header"><h2 id="modalTitle">${title}</h2><button class="btn mini" data-action="close-modal" type="button">关闭</button></header><div class="modal-body">${formId?`<form id="${formId}">${html}<div class="form-actions"><button type="button" class="btn" data-action="close-modal">取消</button><button type="submit" class="btn primary">保存到演示内存</button></div></form>`:html}</div></div></div>`;
}
function render(){
 const actual=pages.has(activeView)?activeView:'dashboard';
 let nav=NAV.map(g=>`<div class="nav-group"><div class="nav-title">${g.title}</div><div class="nav-items">${g.items.map(([id,name,code])=>navButton(id,name,code)).join('')}</div></div>`).join('');
 let currentRole=ROLE_LABELS[actor];
 let body=allowed(actual)?views[actual]():screen('模拟权限边界','这是模式 A 的前端权限展示，不代表后端真实 403 验收。',`<div class="card forbidden"><h2>当前模拟身份没有此界面权限</h2><p>${esc(currentRole)} 不能访问“${esc(titleFor(actual))}”。系统没有发送任何 API 请求；如需检查界面，请切换顶部身份为「QA 全功能预览」。真正的身份与租户隔离仍由正式测试验证。</p><div class="buttons mt22">${openBtn('返回模拟主页','guide','primary')}</div></div>`,'','SYNTHETIC 403');
 $('#app').innerHTML=`<div class="shell"><aside class="sidebar"><div class="logo"><div class="mark">R</div><div><div class="brand">Rehab Studio</div><div class="subbrand">WORKSPACE · UI REVIEW</div></div></div>${nav}<div class="sidebar-footer"><span class="online-dot"></span>模拟服务 · 仅本机<br>不连接正式后端 / 不保存临床数据</div></aside><div class="main"><header class="topbar"><div class="crumb">康复工作室 / 界面测试 / <strong>${esc(titleFor(actual))}</strong></div><div class="top-controls"><span class="context">当前模拟身份</span><select class="role-select" id="roleSelect" aria-label="切换模拟身份">${Object.entries(ROLE_LABELS).map(([id,label])=>`<option value="${id}" ${id===actor?'selected':''}>${label}</option>`).join('')}</select><button class="btn mini" data-action="reset">重置数据</button></div></header><div class="mode-stripe"><span class="mode-chip">模式 A · DEMO</span><span><b>纯模拟数据，非正式系统。</b> 直接进入主页；不进行身份验证、真实签发、数据持久化或外部 API 调用。</span></div><div class="workspace">${body}<p class="footer-note">数据全部虚构并保留在页面内存。更换/刷新网页后自动重置。模式A演示成功不能记作原306项真实业务验收通过。</p></div></div></div>${modal()}<div class="toast" id="toast" hidden aria-live="polite"></div>`;
}
function go(view){if(!pages.has(view))return;activeView=view;search='';modalKind='';location.hash=view;render();window.scrollTo(0,0)}
function setModal(kind){modalKind=kind;render()}
function act(name,id,index){
 switch(name){
  case 'close-modal':setModal('');return;
  case 'add-patient':setModal('patient');return;
  case 'add-episode':setModal('episode');return;
  case 'add-plan':setModal('plan');return;
  case 'add-action':setModal('action');return;
  case 'add-org':setModal('org');return;
  case 'select-patient':selectedPatient=id;logEvent(`查看虚构患者 ${id}`);break;
  case 'change-fms':data.scores[Number(index)]=(data.scores[Number(index)]+1)%4;break;
  case 'save-assessment':data.assessmentSaved=true;logEvent('保存模拟FMS评估草稿');break;
  case 'generate-report':data.reports.unshift({id:randomId('R'),patient:selectedPatient,kind:'初评报告',status:'草稿',date:nowLabel});logEvent('生成未签发模拟报告草稿');break;
  case 'preview-report':setModal('preview');return;
  case 'add-template':data.templates.push({id:randomId('TP'),name:'模拟功能性训练模板',days:'每周 2 天',status:'演示'});logEvent('新增模拟训练模板');break;
  case 'add-prescription':data.prescriptions.unshift({id:randomId('RX'),patient:selectedPatient,name:'HEP 家庭训练草稿',status:'未签发草稿',exercises:3});logEvent('新建模拟HEP处方草稿');break;
  case 'start-session':activeSession=true;logEvent('开始模拟患者训练');break;
  case 'handle-risk':{
   const item=data.alerts.find(a=>a.id===id);if(item){item.status='模拟已处理';logEvent('标记模拟风险已处理 '+id)}break;}
  case 'review-motion':reviewedTrial=id;logEvent('查看虚构OpenCap试次 '+id);break;
  case 'approve-org':{
   const item=data.organizations.find(o=>o.id===id);if(item){item.status='模拟已批准';logEvent('批准虚构机构申请 '+id)}break;}
  case 'reset':data=INITIAL();actor='qa';activeView='dashboard';selectedPatient='DEMO-P001';search='';modalKind='';activeSession=false;reviewedTrial='';lastNo=100;location.hash='dashboard';render();toast('已重置为初始虚构数据');return;
  default:return;
 }
 render();toast('模拟操作已更新（仅内存，不写入正式系统）');
}
function onSubmit(ev){
 if(!(ev.target instanceof HTMLFormElement))return;
 const form=ev.target;
 const ok=['patientForm','episodeForm','planForm','actionForm','orgForm','checkinForm'].includes(form.id);
 if(!ok)return;
 ev.preventDefault();
 if(!form.reportValidity())return;
 const params=Object.fromEntries(new FormData(form));
 const txt=(key,max=80)=>String(params[key]??'').trim().slice(0,max);
 if(form.id==='patientForm'){
  const age=Number(params.age);if(!Number.isInteger(age)||age<18||age>99){toast('演示患者必须为18–99岁的虚构成年人');return;}
  const p={id:randomId('P'),name:txt('name',32),age,sex:'未设置',tenant:'DEMO-T001',status:'初评',therapist:txt('therapist'),goal:txt('goal')};
  data.patients.unshift(p);selectedPatient=p.id;logEvent('创建虚构成年患者 '+p.id);
 } else if(form.id==='episodeForm'){
  const id=txt('patient');if(!data.patients.some(p=>p.id===id))return;
  data.episodes.unshift({id:randomId('E'),patient:id,goal:txt('goal'),stage:'初评',date:nowLabel});logEvent('新建虚构疗程');
 } else if(form.id==='planForm'){
  const id=txt('patient');if(!data.patients.some(p=>p.id===id))return;
  data.plans.unshift({id:randomId('PL'),patient:id,name:txt('name'),visits:txt('visits'),status:'草稿',goal:'虚构目标'});logEvent('创建模拟康复计划草稿');
 } else if(form.id==='actionForm'){
  data.actions.push({id:randomId('M'),name:txt('name'),dose:txt('dose'),type:'演示'});logEvent('添加模拟动作');
 } else if(form.id==='orgForm'){
  data.organizations.unshift({id:randomId('T'),name:txt('name'),contact:txt('contact'),status:'模拟待审批'});logEvent('提交虚构机构申请');
 } else if(form.id==='checkinForm'){
  const pain=Number(params.pain),rpe=Number(params.rpe);
  if(!Number.isInteger(pain)||pain<0||pain>10||!Number.isInteger(rpe)||rpe<1||rpe>10){toast('疼痛需要 0–10 整数，RPE 需要 1–10 整数');return;}
  const s={id:randomId('S'),patient:'DEMO-P001',date:nowLabel,pain,rpe,done:3};data.sessions.unshift(s);activeSession=false;
  if(pain>=7)data.alerts.unshift({id:randomId('AL'),patient:'DEMO-P001',title:'模拟高疼痛反馈',detail:`刚才演示打卡疼痛 ${pain}/10，需人工复核（未发送）`,status:'待处理',level:'高'});
  logEvent('完成模拟训练打卡 '+s.id);
 }
 modalKind='';render();toast('已保存至浏览器内存（不持久化、不发送）');
}
document.addEventListener('click',event=>{
 const t=event.target.closest('[data-view], [data-action]');if(!t)return;
 if(t.hasAttribute('data-view')){go(t.dataset.view);return;}
 act(t.dataset.action,t.dataset.id,t.dataset.index);
});
document.addEventListener('input',event=>{
 if(event.target.id==='patientSearch'){
  search=event.target.value;
  const panel=$('#patientTable');
  if(panel)panel.innerHTML=table(['模拟患者ID','患者','治疗师','训练目标','状态','操作'],patientRows()?[patientRows()]:[]);
 }
});
document.addEventListener('change',event=>{
 if(event.target.id==='roleSelect'){
  if(Object.hasOwn(ROLE_LABELS,event.target.value)){actor=event.target.value;modalKind='';render();toast(`已切换为${ROLE_LABELS[actor]}（仅模拟可见性）`)}
 }
});
document.addEventListener('submit',onSubmit);
window.addEventListener('hashchange',()=>{const v=location.hash.slice(1);if(pages.has(v)&&v!==activeView){activeView=v;render()}});
window.addEventListener('keydown',event=>{if(event.key==='Escape'&&modalKind){modalKind='';render()}});
const initialHash=location.hash.slice(1);if(pages.has(initialHash))activeView=initialHash;
render();
