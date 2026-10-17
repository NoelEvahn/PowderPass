// Concatenates src/*.js (sorted) into the page template.
// Outputs: index.html (standalone page) and dist/artifact.html (body-only, for hosted artifact).
import fs from 'node:fs';
import path from 'node:path';
const dir = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const files = fs.readdirSync(path.join(dir, 'src')).filter((f) => f.endsWith('.js')).sort();
const src = files.map((f) => `// ==== ${f} ====\n` + fs.readFileSync(path.join(dir, 'src', f), 'utf8')).join('\n');
const tpl = fs.readFileSync(path.join(dir, 'tools', 'template.html'), 'utf8');
const page = tpl.replace('/*__SRC__*/', () => src);
fs.mkdirSync(path.join(dir, 'dist'), { recursive: true });
fs.writeFileSync(path.join(dir, 'dist', 'artifact.html'), page);
const headEnd = page.indexOf('</style>') + '</style>'.length;
const full = `<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n${page.slice(0, headEnd)}\n</head>\n<body>\n${page.slice(headEnd)}\n</body>\n</html>\n`;
fs.writeFileSync(path.join(dir, 'index.html'), full);
console.log('built', files.length, 'files,', (full.length / 1024).toFixed(1), 'KB');
