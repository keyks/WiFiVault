/* 临时冒烟脚本：模拟 WiFiVault 服务端接口，验证前端页面与 API 契约。用完即删。 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const WEB = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'web');
const PORT = 8899;

const ITEMS = [
  { name: 'Camera', path: 'Camera', type: 'dir', size: 0, mtime: Date.now(), mime: '' },
  { name: 'trip.mp4', path: 'trip.mp4', type: 'video', size: 128 * 1024 * 1024, mtime: Date.now(), mime: 'video/mp4' },
  { name: 'beach.jpg', path: 'beach.jpg', type: 'image', size: 2_400_000, mtime: Date.now(), mime: 'image/jpeg' },
  { name: 'song.mp3', path: 'song.mp3', type: 'audio', size: 5_600_000, mtime: Date.now(), mime: 'audio/mpeg' },
  { name: 'notes.txt', path: 'notes.txt', type: 'text', size: 1200, mtime: Date.now(), mime: 'text/plain' },
  { name: 'manual.pdf', path: 'manual.pdf', type: 'pdf', size: 800_000, mtime: Date.now(), mime: 'application/pdf' },
  { name: 'archive.zip', path: 'archive.zip', type: 'other', size: 33_000_000, mtime: Date.now(), mime: 'application/zip' }
];

const MIME = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'application/javascript; charset=utf-8', '.svg': 'image/svg+xml' };

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const p = url.pathname;

  const send = (code, body, type) => {
    res.writeHead(code, { 'Content-Type': type || 'text/plain; charset=utf-8' });
    res.end(body);
  };

  if (p === '/api/libraries') return send(200, JSON.stringify({ name: 'WiFiVault', libraries: [{ id: 'demo', name: 'DCIM' }] }), 'application/json');
  if (p === '/api/list') return send(200, JSON.stringify({ lib: 'demo', libName: 'DCIM', path: url.searchParams.get('path') || '', items: ITEMS }), 'application/json');
  if (p === '/api/subtitle') return send(404, 'no subtitle');
  if (p === '/api/thumb') return send(200, Buffer.from('89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000a49444154789c6300010000050001','hex'), 'image/png');

  let file = p === '/' ? 'index.html' : p.replace(/^\//, '');
  file = path.basename(file);
  const full = path.join(WEB, file);
  if (fs.existsSync(full)) {
    return send(200, fs.readFileSync(full), MIME[path.extname(full)] || 'application/octet-stream');
  }
  send(404, 'not found');
}).listen(PORT, () => console.log('smoke server on ' + PORT));
