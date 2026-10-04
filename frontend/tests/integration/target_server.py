import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

# 仅监听环回地址，提供无账号和外部依赖的独立联调目标。
records = {}
requests = []
lock = threading.Lock()

class Handler(BaseHTTPRequestHandler):
    def reply(self, status, data):
        content = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def do_GET(self):
        path = urlparse(self.path).path
        with lock:
            if path == '/__test__/state':
                return self.reply(200, {'requests': requests, 'records': list(records.values())})
            requests.append({'method': 'GET', 'path': self.path})
            if path in ('/records', '/legacy-records'):
                return self.reply(200, {'records': list(records.values()), 'count': len(records)})
            if path.startswith('/records/'):
                record = records.get(path.split('/')[-1])
                return self.reply(200 if record else 404, record or {'error': 'record not found'})
            self.reply(404, {'error': 'unknown route'})

    def do_POST(self):
        path = urlparse(self.path).path
        try:
            payload = json.loads(self.rfile.read(int(self.headers.get('Content-Length', '0'))) or b'{}')
        except (ValueError, TypeError):
            return self.reply(400, {'error': 'invalid JSON'})
        with lock:
            requests.append({'method': 'POST', 'path': self.path, 'body': payload})
            if path != '/records':
                return self.reply(404, {'error': 'unknown route'})
            if not isinstance(payload, dict) or not payload.get('title'):
                return self.reply(400, {'error': 'title is required'})
            record = {'id': str(9223372036854700000 + len(records)), 'title': payload['title']}
            records[record['id']] = record
            self.reply(200, record)

if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1', 18770), Handler).serve_forever()
