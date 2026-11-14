// Real Chrome click-test of the first-install UX with a synthetic Tauri IPC.
// No real Owner requests, signers, clinic data, patient data or device keys.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { spawn } from 'node:child_process'
import { createServer } from 'node:http'
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const dir = dirname(fileURLToPath(import.meta.url))
const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const html = `<!doctype html><html lang="zh"><head>
<meta charset="utf-8"><link href="/main.css" rel="stylesheet"></head>
<body><div id="app"></div>
<script>
window.syntheticCalls = [];
window.syntheticOverview = {
  appVersion:'1.0.0',dockerState:'healthy',dockerDetail:'合成 Docker',
  services:[],accessUrl:'https://127.0.0.1:8443',dataDirectory:'合成目录',
  lastBackupAt:null,lastError:null,operation:null,ready:true,
  license:{status:'UNLICENSED',detail:'需向软件所有者申请许可',
    tenantId:null,installationId:null,organization:null,applicationId:null,
    expiry:null,seatLimit:null,modules:[],pendingReplacement:false,
    publisherConfigured:false,ownerEndpointConfigured:false,localVerificationOnly:true},
  releaseRequiresLicense:true
};
window.__TAURI_INTERNALS__ = {
  invoke:async name => {
    window.syntheticCalls.push(name);
    if(name==='get_overview') return structuredClone(window.syntheticOverview);
    if(name==='get_settings') return {bindAddress:'127.0.0.1',httpPort:8080,httpsPort:8443,lanEnabled:false};
    throw Error('Real authorization commands must never run in this synthetic test');
  }
};
</script>
<script src="/main.js"></script>
<script>
(async () => {
 const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
 const until = async predicate => {
   for(let i=0;i<125;i++){if(predicate())return true;await delay(80)}
   throw Error('launcher UI did not reach expected state');
 };
 const button = action => document.querySelector('button[data-action="'+action+'"]');
 const result = {};
 try {
   await until(() => button('license-help') && document.querySelector('[role=status]')?.textContent.includes('正式签名发行包'));
   result.untrustedBeginDisabled=button('license-begin').disabled;
   result.untrustedSubmitDisabled=button('license-submit').disabled;
   result.untrustedOfflineImportDisabled=button('license-import').disabled;
   result.helpEnabled=!button('license-help').disabled;
   result.visibleReason=document.querySelector('[role=status]').textContent.includes('正式签名发行包');
   result.autoExpanded=!!document.querySelector('#license-onboarding-steps');
   result.firstStepHighlighted=document.querySelector('[aria-current=step]')?.textContent.includes('获取可信安装包')===true;
   button('license-help').click();
   result.manualCloseWorks=!document.querySelector('#license-onboarding-steps');
   button('license-help').click();
   result.helpClicked=!!document.querySelector('#license-onboarding-steps');
   result.stepsVisible=document.querySelectorAll('#license-onboarding-steps li').length===5;
   await delay(5300);
   result.helpSurvivesAutoRefresh=!!document.querySelector('#license-onboarding-steps');
   window.syntheticOverview.license.publisherConfigured=true;
   window.syntheticOverview.license.ownerEndpointConfigured=true;
   await until(() => !button('license-begin').disabled);
   result.trustedBeginEnabled=true;
   result.secondStepHighlighted=document.querySelector('[aria-current=step]')?.textContent.includes('登记这台设备')===true;
   result.trustedSubmitStillDisabled=button('license-submit').disabled;
   button('license-begin').click();
   result.beginDialogActuallyOpens=document.querySelector('#license-begin-dialog').open;
   document.querySelector('#license-begin-dialog').close();
   window.syntheticOverview.license.tenantId='synthetic-tenant';
   await delay(100);
   result.missingDeviceIdentityStillBlocksSubmit=button('license-submit').disabled;
   window.syntheticOverview.license.installationId='synthetic-install';
   await until(() => !button('license-submit').disabled);
   result.enrolledSubmitEnabled=true;
   result.thirdStepHighlighted=document.querySelector('[aria-current=step]')?.textContent.includes('提交授权申请')===true;
   result.renewDisabledBeforeSubmit=button('license-renew').disabled;
   window.syntheticOverview.license.applicationId='synthetic-application';
   await until(() => !button('license-renew').disabled);
   result.submittedRenewEnabled=true;
   result.fourthStepHighlighted=document.querySelector('[aria-current=step]')?.textContent.includes('Owner 审核')===true;
   window.syntheticOverview.license.status='REVOKED';
   await until(() => document.querySelector('[role=status]')?.textContent.includes('不允许使用'));
   result.revokedExplainedNotActivated=button('open').disabled;
   window.syntheticOverview.license.status='ACTIVE';
   await until(() => document.querySelector('[role=status]')?.textContent.includes('Core服务端授权状态'));
   result.fifthStepRequiresCore=document.querySelector('[aria-current=step]')?.textContent.includes('验证后进入系统')===true;
   result.activeAllowsLocalOpen=button('open').disabled===false;
   result.noMutatingTauriIPC=window.syntheticCalls.every(name =>
     name==='get_overview'||name==='get_settings');
 } catch(error) {result.error=String(error)}
 document.body.dataset.launcherOnboardingTest=JSON.stringify(result);
})();
</script></body></html>`

test('Chrome: first-run help auto-opens, survives refresh, registration only when trusted issuer and HTTPS Owner exist',
  { skip: !existsSync(chrome), timeout: 70_000 }, async () => {
    const tmp = mkdtempSync(join(tmpdir(), 'rehab-first-install-synthetic-'))
    const bundle = join(tmp, 'bundle')
    await build({
      entryPoints: [join(dir, '..', 'src', 'main.ts')], outdir: bundle,
      bundle: true, format: 'iife', platform: 'browser', target: 'es2022',
      logLevel: 'silent'
    })
    const server = createServer((req, res) => {
      if (req.url === '/main.js') {
        res.setHeader('content-type', 'application/javascript')
        res.end(readFileSync(join(bundle, 'main.js')))
      } else if (req.url === '/main.css') {
        res.setHeader('content-type', 'text/css')
        res.end(readFileSync(join(bundle, 'main.css')))
      } else {
        res.setHeader('content-type', 'text/html; charset=utf-8')
        res.end(html)
      }
    })
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    const url = 'http://127.0.0.1:' + server.address().port + '/'
    const profile = join(tmp, 'chrome-profile')
    const child = spawn(chrome, [
      '--headless=new', '--no-sandbox', '--disable-gpu', '--disable-extensions',
      '--disable-background-networking', '--disable-dev-shm-usage',
      '--no-first-run', '--remote-debugging-port=0',
      '--user-data-dir=' + profile, url
    ], { stdio: 'ignore' })
    let socket
    try {
      let port
      for (let i=0;i<80;i++) {
        const file=join(profile,'DevToolsActivePort')
        if (existsSync(file)) {port=Number(readFileSync(file,'utf8').split('\n')[0]);break}
        await sleep(100)
      }
      assert.ok(Number.isSafeInteger(port) && port>0,'isolated Chrome failed to launch')
      let target
      for (let i=0;i<80;i++) {
        const tabs=await fetch('http://127.0.0.1:'+port+'/json/list').then(r=>r.json())
        target=tabs.find(tab=>tab.type==='page'&&tab.url===url)
        if (target) break
        await sleep(100)
      }
      assert.ok(target?.webSocketDebuggerUrl,'browser test page unavailable')
      socket=new WebSocket(target.webSocketDebuggerUrl)
      await new Promise((resolve,reject)=>{
        socket.addEventListener('open',resolve,{once:true})
        socket.addEventListener('error',reject,{once:true})
      })
      let seq=0
      const pending=new Map()
      socket.addEventListener('message',event=>{
        const msg=JSON.parse(event.data)
        if(pending.has(msg.id)){pending.get(msg.id)(msg);pending.delete(msg.id)}
      })
      async function evaluate(expression){
        const id=++seq
        const answer=new Promise(resolve=>pending.set(id,resolve))
        socket.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression,returnByValue:true}}))
        const msg=await answer
        if(msg.error||msg.result?.exceptionDetails)throw Error('synthetic Chrome evaluation failed')
        return msg.result?.result?.value
      }
      let output
      for(let i=0;i<600;i++){
        const value=await evaluate('document.body?.dataset?.launcherOnboardingTest')
        if(value){output=JSON.parse(value);break}
        await sleep(100)
      }
      assert.ok(output,'browser did not finish onboarding interaction test')
      assert.equal(output.error,undefined,output.error)
      for(const [name,ok] of Object.entries(output))
        assert.equal(ok,true, name+' failed')
    } finally {
      socket?.close()
      child.kill('SIGTERM')
      await new Promise(resolve=>server.close(resolve))
      await sleep(300)
      rmSync(tmp,{recursive:true,force:true})
    }
  })
