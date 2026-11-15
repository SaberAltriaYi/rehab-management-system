import sys,threading,unittest
from pathlib import Path
from http.server import ThreadingHTTPServer
from urllib.request import Request,urlopen
from urllib.error import HTTPError
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from server import Handler
class ModeAHostTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.s=ThreadingHTTPServer(('127.0.0.1',0),Handler)
  cls.port=cls.s.server_address[1]
  cls.t=threading.Thread(target=cls.s.serve_forever,daemon=True);cls.t.start()
 @classmethod
 def tearDownClass(cls):cls.s.shutdown();cls.s.server_close()
 def request(self,path='/',method='GET'):
  return urlopen(Request(f'http://127.0.0.1:{self.port}{path}',method=method),timeout=3)
 def test_homepage_no_login_and_csp(self):
  with self.request() as r:
   t=r.read().decode()
   self.assertEqual(r.status,200)
   self.assertIn('模式 A',t)
   self.assertIn('模拟数据',t)
   self.assertIn("connect-src 'none'",r.headers['Content-Security-Policy'])
 def test_mutation_methods_denied(self):
  for method in ('POST','PUT','PATCH','DELETE','OPTIONS'):
   with self.subTest(method=method),self.assertRaises(HTTPError) as err:self.request('/admin-api/patient',method)
   self.assertEqual(err.exception.code,405)
 def test_no_proxy_and_no_private_source_files(self):
  for path in ('/admin-api/rehab/patient/page','/.env','/README.md','/../../etc/passwd'):
   with self.subTest(path=path),self.assertRaises(HTTPError) as err:self.request(path)
   self.assertEqual(err.exception.code,404)
 def test_asset_allowlist_and_health(self):
  for path in ('/app.js','/styles.css','/favicon.svg','/health'):
   with self.subTest(path=path),self.request(path) as r:self.assertEqual(r.status,200)
  with self.request('/health') as r:
   self.assertIn('"backend": false',r.read().decode())
   self.assertEqual(r.headers['Cache-Control'],'no-store, private, max-age=0')
 def test_head_no_cors(self):
  with self.request('/app.js','HEAD') as r:
   self.assertEqual(r.status,200)
   self.assertIsNone(r.headers.get('Access-Control-Allow-Origin'))
if __name__=='__main__':unittest.main()
