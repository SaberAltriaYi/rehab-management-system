#!/usr/bin/env python3
"""Real Chrome CDP end-to-end checks of synthetic, no-login Mode A UI."""
from __future__ import annotations
import asyncio
import base64
import json
import os
import pathlib
import subprocess
import tempfile
import time
from urllib.request import urlopen
import websockets

CHROME='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
URL='http://127.0.0.1:17630/'
EVID=pathlib.Path('/Users/saber/my-project/rehab/validation-evidence/qa-20261112-modea-ui')
EVID.mkdir(parents=True,exist_ok=True,mode=0o700)

async def run():
    results={}; network=[]; js_errors=[]; checks=0
    with tempfile.TemporaryDirectory(prefix='rehab-mode-a-browser-') as directory:
        profile=pathlib.Path(directory)/'profile';profile.mkdir(mode=0o700)
        cmd=[CHROME,'--headless=new','--no-first-run','--disable-extensions',
             '--disable-background-networking','--no-default-browser-check',
             '--remote-debugging-port=0','--remote-allow-origins=*',
             '--user-data-dir='+str(profile),'about:blank']
        proc=subprocess.Popen(cmd,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:
            file=profile/'DevToolsActivePort'
            for _ in range(100):
                if file.exists():break
                if proc.poll() is not None:raise RuntimeError('Chrome exited before CDP became ready')
                await asyncio.sleep(.1)
            else:raise RuntimeError('Chrome CDP port not created')
            port=int(file.read_text().splitlines()[0])
            pages=json.load(urlopen(f'http://127.0.0.1:{port}/json/list',timeout=5))
            wsurl=next(x['webSocketDebuggerUrl'] for x in pages if x['type']=='page')
            async with websockets.connect(wsurl,max_size=12000000) as ws:
                counter=0
                async def call(method,params=None):
                    nonlocal counter
                    counter+=1;number=counter
                    await ws.send(json.dumps({'id':number,'method':method,'params':params or {}}))
                    while True:
                        data=json.loads(await asyncio.wait_for(ws.recv(),12))
                        if data.get('method')=='Network.requestWillBeSent':
                            network.append(data.get('params',{}).get('request',{}).get('url',''))
                        if data.get('method')=='Runtime.exceptionThrown':
                            js_errors.append(data.get('params',{}).get('exceptionDetails',{}).get('text','exception'))
                        if data.get('id')==number:
                            if 'error' in data:raise RuntimeError('CDP '+method+': '+str(data['error'])[:160])
                            return data.get('result',{})
                async def evaluate(script):
                    answer=await call('Runtime.evaluate',{'expression':script,'awaitPromise':True,'returnByValue':True})
                    if answer.get('exceptionDetails'):
                        raise RuntimeError('JavaScript evaluation failure '+str(answer['exceptionDetails'])[:170])
                    return answer.get('result',{}).get('value')
                async def click(selector):
                    source=f'''(() => {{ const el=document.querySelector({json.dumps(selector)});if(!el)throw new Error('MISSING_SELECTOR');el.click();return true; }})()'''
                    return await evaluate(source)
                async def screenshot(name):
                    item=await call('Page.captureScreenshot',{'format':'png','captureBeyondViewport':False})
                    payload=base64.b64decode(item['data'])
                    p=EVID/name;p.write_bytes(payload);p.chmod(0o600)
                    assert len(payload)>5000,(name,len(payload))
                    return len(payload)
                await call('Page.enable');await call('Runtime.enable');await call('Network.enable')
                await call('Emulation.setDeviceMetricsOverride',
                           {'width':1440,'height':900,'deviceScaleFactor':1,'mobile':False})
                await call('Page.navigate',{'url':URL})
                for _ in range(90):
                    if await evaluate("Boolean(document.querySelector('#roleSelect'))"):break
                    await asyncio.sleep(.1)
                else:raise RuntimeError('homepage did not render')
                assert '康复工作台' in await evaluate("document.querySelector('h1').textContent")
                assert '模式 A · DEMO' in await evaluate('document.body.innerText')
                checks+=2
                results['desktopScreenshotBytes']=await screenshot('dashboard-desktop.png')
                # All 15 navigation items must render under the QA persona.
                menu=('dashboard','patients','episodes','assessments','reports','plans','reassessment',
                      'hep','patientHome','risks','motion','owner','ops','audit','guide')
                for view in menu:
                    await click(f'[data-view="{view}"]')
                    heading=await evaluate("document.querySelector('h1')?.textContent||''")
                    assert heading and '模拟权限边界' not in heading, (view,heading)
                    checks+=1
                results['qaNavigationRoutes']=len(menu)
                # Create one synthetic patient through a real form submit.
                await click('[data-view="patients"]')
                await click('[data-action="add-patient"]')
                await evaluate("(() => {const f=document.querySelector('#patientForm');f.elements.namedItem('name').value='演示新增成人';f.elements.namedItem('age').value='39';f.elements.namedItem('goal').value='下肢协调练习';f.requestSubmit();return true})()")
                assert '演示新增成人' in await evaluate('document.body.innerText')
                checks+=1
                # Same session can create an episode for the new object.
                await click('[data-view="episodes"]')
                await click('[data-action="add-episode"]')
                await evaluate("(() => {const f=document.querySelector('#episodeForm');f.elements.namedItem('goal').value='合成疗程检查';f.requestSubmit();return true})()")
                assert '合成疗程检查' in await evaluate('document.body.innerText')
                checks+=1
                # FMS interaction, assessment draft, and report draft are local only.
                await click('[data-view="assessments"]')
                await click('[data-action="change-fms"]')
                await click('[data-action="save-assessment"]')
                assert '已保存至本页内存' in await evaluate('document.body.innerText')
                await click('[data-view="reports"]')
                await click('[data-action="generate-report"]')
                assert '未签发草稿' not in await evaluate('document.querySelector("h1").innerText')
                assert await evaluate('document.querySelectorAll("tbody tr").length')>=2
                checks+=3
                # HEP: add a synthetic exercise, create a draft (never sign).
                await click('[data-view="hep"]')
                await click('[data-action="add-action"]')
                await evaluate("(() => {const f=document.querySelector('#actionForm');f.elements.namedItem('name').value='模拟髋伸展动作';f.requestSubmit();return true})()")
                assert '模拟髋伸展动作' in await evaluate('document.body.innerText')
                await click('[data-action="add-prescription"]')
                assert '未签发草稿' in await evaluate('document.body.innerText')
                checks+=2
                # Switching to a patient is purely a simulated visual restriction.
                await evaluate("(() => {const s=document.querySelector('#roleSelect');s.value='patient';s.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
                await click('[data-view="patients"]')
                assert '当前模拟身份没有此界面权限' in await evaluate('document.body.innerText')
                checks+=1
                await click('[data-view="patientHome"]')
                await click('[data-action="start-session"]')
                await evaluate("(() => {const f=document.querySelector('#checkinForm');f.elements.namedItem('pain').value='8';f.elements.namedItem('rpe').value='7';f.requestSubmit();return true})()")
                assert '8/10' in await evaluate('document.body.innerText')
                checks+=1
                # Restore QA identity and inspect synthetic pain alert.
                await evaluate("(() => {const s=document.querySelector('#roleSelect');s.value='qa';s.dispatchEvent(new Event('change',{bubbles:true}));return true})()")
                await click('[data-view="risks"]')
                assert '模拟高疼痛反馈' in await evaluate('document.body.innerText')
                checks+=1
                # Simulated risk handling remains in memory only.
                await click('[data-action="handle-risk"]')
                assert '模拟已处理' in await evaluate('document.body.innerText')
                checks+=1
                # Owner approval is a mock status change, not a license issuance.
                await click('[data-view="owner"]')
                await click('[data-action="add-org"]')
                await evaluate("(() => {const f=document.querySelector('#orgForm');f.elements.namedItem('name').value='虚构模拟工作室D';f.requestSubmit();return true})()")
                assert '虚构模拟工作室D' in await evaluate('document.body.innerText')
                await click('[data-action="approve-org"]')
                assert '模拟已批准' in await evaluate('document.body.innerText')
                checks+=2
                # Plan form and patient search have actual local interactions.
                await click('[data-view="plans"]')
                await click('[data-action="add-plan"]')
                await evaluate("(() => {const f=document.querySelector('#planForm');f.elements.namedItem('name').value='模拟专项康复计划';f.requestSubmit();return true})()")
                assert '模拟专项康复计划' in await evaluate('document.body.innerText')
                checks+=1
                await click('[data-view="patients"]')
                await evaluate("(() => {const f=document.querySelector('#patientSearch');f.value='演示新增成人';f.dispatchEvent(new Event('input',{bubbles:true}));return true})()")
                assert await evaluate('document.querySelectorAll("tbody tr").length')==1
                checks+=1
                # Ensure reset does not persist user input.
                await click('[data-action="reset"]')
                await click('[data-view="patients"]')
                assert '演示新增成人' not in await evaluate('document.body.innerText')
                assert await evaluate('document.querySelectorAll("tbody tr").length')==3
                checks+=2
                await click('[data-view="dashboard"]')
                # Mobile screenshot and horizontal overflow review.
                await call('Emulation.setDeviceMetricsOverride',{'width':390,'height':844,'deviceScaleFactor':1,'mobile':True})
                await asyncio.sleep(.2)
                widths=await evaluate('({innerWidth:window.innerWidth,scrollWidth:document.documentElement.scrollWidth})')
                results['mobileViewport']=widths
                assert widths['scrollWidth']<=widths['innerWidth']+1,widths
                checks+=1
                results['mobileScreenshotBytes']=await screenshot('dashboard-mobile.png')
                # Never leave the independent static origin for API traffic.
                bad=[u for u in network if u.startswith(('http://','https://')) and not u.startswith(URL)]
                results['unexpectedNetworkUrls']=bad[:10]
                results['scriptErrors']=js_errors
                assert not bad,bad
                assert not js_errors,js_errors
                checks+=2
        finally:
            proc.terminate()
            try:proc.wait(timeout=5)
            except subprocess.TimeoutExpired:proc.kill()
    results.update({'checksPassed':checks,'status':'PASS','scope':'synthetic localhost only, no real clinical or auth flows',
                    'noRealBackendCalls':True,'noLiveDatabaseWrites':True})
    evidence=EVID/'browser-smoke-results.json'
    evidence.write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n');evidence.chmod(0o600)
    print('MODE_A_CHROME_E2E=PASS')
    print('CHECKS_PASSED',checks,'ROUTES',results['qaNavigationRoutes'])
    print('MOBILE_VIEWPORT',results['mobileViewport'])
    print('REAL_API_NETWORK_REQUESTS',len(results['unexpectedNetworkUrls']),'JS_ERRORS',len(results['scriptErrors']))
    print('EVIDENCE',evidence)

if __name__=='__main__':asyncio.run(run())
