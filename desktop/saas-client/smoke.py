"""Synthetic native SaaS connector checks; does not claim clinical server validation."""
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from urllib.request import ProxyHandler,build_opener
from urllib.error import URLError
from playwright.sync_api import sync_playwright,expect


def main():
    with tempfile.TemporaryDirectory(prefix='saas-client-synthetic-') as folder:
        root=Path(folder)/'state'
        with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
        exe=os.environ.get('CLIENT_EXECUTABLE')
        command=[exe] if exe else [sys.executable,str(Path(__file__).with_name('app.py'))]
        command+=['--state-dir',str(root),'--port',str(port),'--no-browser','--handoff']
        server=subprocess.Popen(command,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        base=f'http://127.0.0.1:{port}'
        try:
            opener=build_opener(ProxyHandler({}))
            for _ in range(150):
                if server.poll() is not None:raise RuntimeError('CLIENT_START_FAILED')
                try:
                    with opener.open(base+'/',timeout=.2) as r:
                        if r.status==200 and (root/'launch.json').exists():break
                except (URLError,TimeoutError):pass
                time.sleep(.1)
            else:raise RuntimeError('CLIENT_START_TIMEOUT')
            launch=json.loads((root/'launch.json').read_text(encoding='utf-8'))['url']
            with sync_playwright() as p:
                browser=p.chromium.launch(headless=True,args=['--no-proxy-server'])
                page=browser.new_page(viewport={'width':1100,'height':850})
                page.route('**/*',lambda r:r.continue_() if r.request.url.startswith(base+'/') else r.abort())
                errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
                page.goto(launch)
                expect(page.locator('#notice')).to_contain_text('请填写有效')
                assert page.url==base+'/'
                page.locator('#url').fill('http://clinic.invalid/')
                page.locator('button[type=submit]').click()
                expect(page.locator('#notice')).to_contain_text('有效 HTTPS')
                expect(page.locator('#open')).to_be_hidden()
                page.locator('#url').fill('https://clinic.invalid/login')
                page.locator('button[type=submit]').click()
                expect(page.locator('#open')).to_have_attribute('href','https://clinic.invalid/login')
                saved=json.loads((root/'settings.json').read_text(encoding='utf-8'))
                assert saved=={'url':'https://clinic.invalid/login'}
                assert page.evaluate('localStorage.length')==0
                assert not errors,errors
                page.locator('#quit').click()
                expect(page.locator('#notice')).to_contain_text('已关闭')
                browser.close()
            server.wait(timeout=15)
            assert server.returncode==0
            print(json.dumps({'status':'passed','checks':['bootstrap','HTTPS-only','URL-only persistence','no browser token storage','clean shutdown']}))
        finally:
            if server.poll() is None:
                server.terminate()
                try:server.wait(timeout=10)
                except subprocess.TimeoutExpired:server.kill();server.wait()


if __name__=='__main__':main()
