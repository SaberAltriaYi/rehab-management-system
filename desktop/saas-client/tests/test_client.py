import importlib.util
import json
import threading
from pathlib import Path
from urllib.request import ProxyHandler, Request, build_opener
from urllib.error import HTTPError
import pytest
spec=importlib.util.spec_from_file_location('client',Path(__file__).parents[1]/'app.py')
client=importlib.util.module_from_spec(spec);spec.loader.exec_module(client)

@pytest.mark.parametrize('url',['http://example.org','https://u:p@example.org','javascript:alert(1)',
 'https://example.org/?token=x','https://example.org/#x','https://example.org/\n','https://example.org:bad', 'https://example.org\\@evil.invalid'])
def test_bad_urls(url):
    with pytest.raises(ValueError):client.safe_url(url)

def test_https_url():
    assert client.safe_url('https://localhost:51146/login')=='https://localhost:51146/login'

def test_private_connector(tmp_path):
    server,state=client.create_server(tmp_path)
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    base=f'http://127.0.0.1:{server.server_port}'
    opener=build_opener(ProxyHandler({}))
    def post(path,body,token='',origin=None):
        headers={'Content-Type':'application/json','Authorization':'Bearer '+token}
        if origin:headers['Origin']=origin
        req=Request(base+path,data=json.dumps(body).encode(),headers=headers)
        try:
            with opener.open(req) as r:return r.status,json.load(r)
        except HTTPError as e:return e.code,json.load(e)
    try:
        assert post('/api/settings',{'url':'https://clinic.invalid'})[0]==401
        assert post('/bootstrap',{'code':state['code']},origin='https://evil.invalid')[0]==403
        status,grant=post('/bootstrap',{'code':state['code']});assert status==200
        assert post('/bootstrap',{'code':state['code']})[0]==401
        assert post('/api/settings',{'url':'http://clinic.invalid'},grant['token'])[0]==400
        assert post('/api/settings',{'url':'https://clinic.invalid/login'},grant['token'])[0]==200
        saved=(tmp_path/'settings.json').read_text()
        assert grant['token'] not in saved and state['code'] not in saved
        assert json.loads(saved)=={'url':'https://clinic.invalid/login'}
    finally:server.shutdown();server.server_close();thread.join()
