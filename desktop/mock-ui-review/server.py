#!/usr/bin/env python3
"""Strictly loopback static server for synthetic UI review; never talks to Core."""
from __future__ import annotations
import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
STATIC = {
    '/': ('index.html', 'text/html; charset=utf-8'),
    '/index.html': ('index.html', 'text/html; charset=utf-8'),
    '/app.js': ('app.js', 'text/javascript; charset=utf-8'),
    '/styles.css': ('styles.css', 'text/css; charset=utf-8'),
    '/favicon.svg': ('favicon.svg', 'image/svg+xml'),
}
CSP = ("default-src 'none'; script-src 'self'; style-src 'self'; "
       "img-src 'self' data:; connect-src 'none'; font-src 'none'; "
       "frame-ancestors 'none'; frame-src 'none'; object-src 'none'; "
       "base-uri 'none'; form-action 'none'")

class Handler(BaseHTTPRequestHandler):
    server_version = 'RehabSyntheticUI/1'
    def _headers(self, kind: str, size: int, status: int=200):
        self.send_response(status)
        for k,v in {
            'Content-Type': kind, 'Content-Length': str(size),
            'Cache-Control': 'no-store, private, max-age=0',
            'Content-Security-Policy': CSP,
            'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY',
            'Referrer-Policy': 'no-referrer',
            'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
            'Cross-Origin-Resource-Policy': 'same-origin',
        }.items(): self.send_header(k,v)
        self.end_headers()
    def _serve(self, head=False):
        path=urlsplit(self.path).path
        if path=='/health':
            data=json.dumps({'status':'ok','mode':'synthetic-ui-only','backend':False}).encode()
            kind='application/json; charset=utf-8'; code=200
        elif path in STATIC:
            file,kind=STATIC[path]; data=(ROOT/file).read_bytes(); code=200
        else:
            data=b'Not found';kind='text/plain; charset=utf-8';code=404
        self._headers(kind,len(data),code)
        if not head:self.wfile.write(data)
    def do_GET(self):self._serve()
    def do_HEAD(self):self._serve(head=True)
    def _reject(self):
        data=b'Synthetic UI is read-only; no backend mutations'
        self._headers('text/plain; charset=utf-8',len(data),405)
        self.wfile.write(data)
    do_POST=do_PUT=do_PATCH=do_DELETE=do_OPTIONS=_reject
    def log_message(self,fmt,*args):
        # Only HTTP path/status; no request bodies or secrets.
        print('ModeA', self.address_string(),fmt % args,flush=True)

def main():
    parser=argparse.ArgumentParser(description='Local synthetic rehab UI; localhost only')
    parser.add_argument('--port',type=int,default=17630)
    args=parser.parse_args()
    if not 1024<=args.port<=65535:parser.error('port must be 1024..65535')
    server=ThreadingHTTPServer(('127.0.0.1',args.port),Handler)
    print(f'MODE_A_READY http://127.0.0.1:{args.port}/',flush=True)
    try:server.serve_forever(poll_interval=.15)
    except KeyboardInterrupt:pass
    finally:server.server_close()

if __name__=='__main__':main()
