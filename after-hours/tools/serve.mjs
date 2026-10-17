// Local static server for testing: serves after-hours/ and maps CDN three.js URLs to a local copy.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const root = path.resolve(process.argv[2] || '.');
const threeDir = path.resolve(process.argv[3] || 'node_modules/three');
const port = +(process.argv[4] || 8123);
const types = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.png': 'image/png', '.json': 'application/json' };
http.createServer((req, res) => {
  let u = decodeURIComponent(req.url.split('?')[0]);
  let file;
  if (u.startsWith('/three/')) file = path.join(threeDir, u.slice(7));
  else file = path.join(root, u === '/' ? 'index.html' : u);
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); res.end('nf'); return; }
    res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream' });
    res.end(data);
  });
}).listen(port, () => console.log('serving', root, 'on', port));
