"""SaaS browser connector, not an offline server or Enterprise activation client."""
from __future__ import annotations
import argparse
import hmac
import json
import os
import secrets
import threading
import time
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

HTML = r"""<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Rehab SaaS Client DEV</title><style>
*{box-sizing:border-box}body{margin:0;background:#f1f5f3;color:#193d36;font:15px/1.7 system-ui,sans-serif}main{max-width:780px;margin:8vh auto;padding:36px}header{font-size:12px;letter-spacing:3px;color:#537e6c}h1{font-size:36px;line-height:1.3;margin:18px 0}.card{background:white;border:1px solid #d7e3db;border-radius:16px;padding:30px;margin-top:30px}input{width:100%;padding:13px;border:1px solid #b8cec0;border-radius:8px;font:inherit;margin:10px 0 16px}button,a.action{display:inline-block;border:0;background:#1c6951;color:white;padding:11px 18px;border-radius:8px;text-decoration:none;font:inherit;cursor:pointer}button.secondary{background:#e8efe9;color:#365b48;margin-left:8px}small,.muted{color:#748c7c}.badge{display:inline-block;background:#f2e7ca;color:#785d21;padding:5px 12px;border-radius:30px;font-size:11px}#notice{min-height:26px;overflow-wrap:anywhere}a[hidden]{display:none}footer{margin-top:25px;font-size:12px}code{word-break:break-all}</style>
<main><header>REHAB · CLOUD WORKSPACE</header><h1>连接你的康复管理平台</h1><span class="badge">DEV · 未签名开发客户端</span><p class="muted">账号、机构、角色和订阅由 SaaS 服务器统一管理。无需许可证、激活码或本地授权。</p><section class="card"><h2>平台地址</h2><p>请输入管理员提供的 HTTPS 地址。请核对域名；登录由系统浏览器及服务器完成。</p><form id="settings"><label for="url">HTTPS 地址</label><input id="url" type="url" placeholder="https://your-clinic.example/" required maxlength="2048"><button type="submit">保存地址</button><button type="button" class="secondary" id="quit">退出连接器</button></form><p id="notice" role="status">正在验证本机启动会话…</p><a id="open" class="action" hidden target="_blank" rel="noopener noreferrer">在浏览器中打开平台 ↗</a></section><footer>此安装包是 SaaS 连接客户端，不包含本机数据库、Java 服务或离线病历。后端与管理端单独部署。客户端仅保存平台地址，不接收登录密码或临床数据，也不跳过 TLS 验证。正式发布签名与临床验收尚未完成。</footer></main>
<script>
let token='';const $=id=>document.getElementById(id);const notice=t=>$('notice').textContent=t;
async function api(path,body){const r=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(body)});const data=await r.json();if(!r.ok)throw Error(data.error||'请求未完成');return data}
function show(url){$('url').value=url;$('open').hidden=!url;if(url)$('open').href=url;notice(url?'地址已保存。请确认域名后打开平台，服务器可用性以实际登录为准。':'请填写有效 HTTPS 平台地址。')}
const code=new URLSearchParams(location.hash.slice(1)).get('launch');history.replaceState(null,'',location.pathname);
(async()=>{try{if(!code)throw Error('启动会话缺失，请重新打开客户端。');const d=await api('/bootstrap',{code});token=d.token;show(d.url)}catch(e){notice(e.message)}})();
$('settings').onsubmit=async e=>{e.preventDefault();try{const d=await api('/api/settings',{url:$('url').value});show(d.url)}catch(e){$('open').hidden=true;notice(e.message)}};
$('quit').onclick=async()=>{try{await api('/api/quit',{});token='';$('open').hidden=true;notice('连接器已关闭。平台浏览器会话请在平台内退出。')}catch(e){notice(e.message)}};
</script></html>"""


def safe_url(value):
    if not isinstance(value,str) or not value or len(value)>2048 or any(ord(x)<33 for x in value) or '\\' in value:
        raise ValueError('HTTPS_ADDRESS_REQUIRED')
    parsed=urlsplit(value)
    if (parsed.scheme!='https' or not parsed.hostname or parsed.username is not None
            or parsed.password is not None or parsed.query or parsed.fragment):
        raise ValueError('HTTPS_ADDRESS_REQUIRED_NO_CREDENTIALS_OR_QUERY')
    _=parsed.port
    return urlunsplit(parsed)


def state_root():
    if os.name=='nt':return Path(os.environ['LOCALAPPDATA'])/'RehabSaaSClient-DEV'
    return Path.home()/'Library/Application Support/RehabSaaSClient-DEV'


def create_server(root,port=0):
    root.mkdir(parents=True,exist_ok=True,mode=0o700)
    if root.is_symlink():raise ValueError('PRIVATE_DIRECTORY_REQUIRED')
    path=root/'settings.json'
    if path.is_symlink():raise ValueError('INVALID_SETTINGS_FILE')
    address=''
    if path.exists():
        if path.stat().st_size>4096:raise ValueError('SETTINGS_TOO_LARGE')
        address=safe_url(json.loads(path.read_text(encoding='utf-8'))['url'])
    state={'url':address,'code':secrets.token_urlsafe(40),'token':secrets.token_urlsafe(40),
           'expires':time.monotonic()+90,'used':False}
    lock=threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def setup(self):
            super().setup();self.connection.settimeout(5)
        def respond(self,status,value,html=False):
            body=value.encode('utf-8') if html else json.dumps(value).encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type','text/html; charset=utf-8' if html else 'application/json')
            self.send_header('Content-Length',str(len(body)))
            self.send_header('Cache-Control','no-store')
            self.send_header('X-Content-Type-Options','nosniff')
            self.send_header('X-Frame-Options','DENY')
            self.send_header('Referrer-Policy','no-referrer')
            self.send_header('Content-Security-Policy',"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; form-action 'none'; frame-ancestors 'none'; base-uri 'none'")
            self.end_headers();self.wfile.write(body)
        def boundary(self):
            expected='127.0.0.1:'+str(self.server.server_port)
            return self.headers.get('Host')==expected and self.headers.get('Origin','http://'+expected)=='http://'+expected
        def do_GET(self):
            if not self.boundary():return self.respond(403,{'error':'ORIGIN_REJECTED'})
            if self.path=='/':return self.respond(200,HTML,True)
            return self.respond(404,{'error':'NOT_FOUND'})
        def do_POST(self):
            if not self.boundary():return self.respond(403,{'error':'ORIGIN_REJECTED'})
            try:
                size=int(self.headers.get('Content-Length','-1'))
                if not 0<=size<=4096:raise ValueError('INVALID_BODY_SIZE')
                value=json.loads(self.rfile.read(size))
                if not isinstance(value,dict):raise ValueError('INVALID_BODY')
                if self.path=='/bootstrap':
                    code=value.get('code','')
                    with lock:
                        if (not isinstance(code,str) or len(code)>128 or state['used'] or
                            time.monotonic()>state['expires'] or not hmac.compare_digest(code.encode(),state['code'].encode())):
                            return self.respond(401,{'error':'启动会话过期或已使用，请重启客户端。'})
                        state['used']=True
                    return self.respond(200,{'token':state['token'],'url':state['url']})
                token=self.headers.get('Authorization','')
                if not hmac.compare_digest(token.encode(),('Bearer '+state['token']).encode()):
                    return self.respond(401,{'error':'本机会话无效，请重新启动。'})
                if self.path=='/api/settings':
                    address=safe_url(value.get('url'))
                    temp=root/('settings-'+secrets.token_hex(8)+'.tmp')
                    fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
                    with os.fdopen(fd,'w',encoding='utf-8') as f:json.dump({'url':address},f)
                    os.replace(temp,path);state['url']=address
                    return self.respond(200,{'url':address})
                if self.path=='/api/quit':
                    self.respond(200,{'stopped':True})
                    threading.Thread(target=self.server.shutdown,daemon=True).start();return
                return self.respond(404,{'error':'NOT_FOUND'})
            except (ValueError,TypeError,KeyError):
                return self.respond(400,{'error':'请输入不包含凭据、查询参数或片段的有效 HTTPS 地址。'})
    return ThreadingHTTPServer(('127.0.0.1',port),Handler),state


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--state-dir',type=Path)
    parser.add_argument('--port',type=int,default=0)
    parser.add_argument('--no-browser',action='store_true')
    parser.add_argument('--handoff',action='store_true',help='Private single-use launch file for headless testing')
    args=parser.parse_args()
    if args.handoff and not (args.state_dir and args.no_browser):parser.error('--handoff requires explicit test state and --no-browser')
    root=args.state_dir or state_root()
    server,state=create_server(root,args.port)
    url=f"http://127.0.0.1:{server.server_port}/#launch={state['code']}"
    handoff=None
    if args.handoff:
        handoff=root/'launch.json'
        fd=os.open(handoff,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w',encoding='utf-8') as f:json.dump({'url':url},f)
    if not args.no_browser:
        opener=threading.Timer(.7,lambda:webbrowser.open(url));opener.daemon=True;opener.start()
    try:server.serve_forever()
    finally:
        server.server_close()
        if handoff is not None:handoff.unlink(missing_ok=True)


if __name__=='__main__':main()
