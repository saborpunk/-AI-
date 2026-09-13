import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';

// 固定文件白名单，开发服务器不能读取项目配置、密钥或任意磁盘文件。
const files = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/index.html', ['index.html', 'text/html; charset=utf-8']],
  ['/app.js', ['app.js', 'text/javascript; charset=utf-8']],
  ['/styles.css', ['styles.css', 'text/css; charset=utf-8']],
]);
createServer(async (request, response) => {
  if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405).end(); return; }
  const path = new URL(request.url, 'http://127.0.0.1:5173').pathname;
  if (path === '/favicon.ico') { response.writeHead(204).end(); return; }
  const file = files.get(path);
  if (!file) { response.writeHead(404).end('Not found'); return; }
  try {
    const body = await readFile(new URL(file[0], import.meta.url));
    response.writeHead(200, {
      'Content-Type': file[1], 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
      'Content-Security-Policy': "default-src 'self'; connect-src http://127.0.0.1:8080; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
    });
    response.end(request.method === 'HEAD' ? undefined : body);
  } catch { response.writeHead(500).end('Unable to read frontend file'); }
}).listen(5173, '127.0.0.1', () => console.log('Frontend ready: http://127.0.0.1:5173'));
