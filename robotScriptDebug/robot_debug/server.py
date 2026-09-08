from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
from .port30001 import Elite30001

BASE=Path(__file__).parent.parent; DEVICE=Elite30001()
class Handler(BaseHTTPRequestHandler):
    def _json(self, obj, code=200):
        b=json.dumps(obj,ensure_ascii=False).encode(); self.send_response(code)
        self.send_header('Content-Type','application/json; charset=utf-8'); self.send_header('Content-Length',str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_GET(self):
        if self.path=='/api/state': return self._json(DEVICE.snapshot())
        if self.path=='/': data=(BASE/'web'/'index.html').read_bytes(); self.send_response(200); self.send_header('Content-Type','text/html; charset=utf-8'); self.send_header('Content-Length',str(len(data))); self.end_headers(); self.wfile.write(data); return
        self.send_error(404)
    def do_POST(self):
        n=int(self.headers.get('Content-Length',0)); d=json.loads(self.rfile.read(n) or b'{}')
        try:
            if self.path=='/api/connect': DEVICE.connect(d['host'], int(d.get('port',30001))); return self._json(DEVICE.snapshot())
            if self.path=='/api/disconnect': DEVICE.close(); return self._json(DEVICE.snapshot())
            if self.path=='/api/script': DEVICE.send_script(d['script']); return self._json({'ok':True})
            self.send_error(404)
        except Exception as e: self._json({'error':str(e)},400)
    def log_message(self,*a): pass
def main(): ThreadingHTTPServer(('127.0.0.1',8765),Handler).serve_forever()
