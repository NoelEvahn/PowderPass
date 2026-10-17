// ───────────────────────── monitor content: IDE, tests, chat ─────────────────────────
function makeScreenTex(w, h) { const t = canvasTex(w, h); t.userData.key = ''; return t; }

const THEMES = {
  oa: { bg: '#0d1316', panel: '#121b1f', line: '#1f2c31', text: '#d6e2e6', dim: '#6f8790', acc: '#5ff2cf', kw: '#ff8fb1', str: '#ffd27a', fn: '#8ab4ff', com: '#5d7680', num: '#c8a6ff', ok: '#5ff2a0', bad: '#ff6b6b', name: 'Codex' },
  an: { bg: '#1b130f', panel: '#231914', line: '#33251d', text: '#f2e6d8', dim: '#a08672', acc: '#ffb36e', kw: '#f08a5a', str: '#c9e09a', fn: '#ffd59a', com: '#8c7462', num: '#e8b0d8', ok: '#9fe39a', bad: '#ff7a6a', name: 'Claude Code' },
};
const KW = new Set(['public', 'void', 'if', 'return', 'for', 'int', 'new', 'def', 'assert', 'import', 'from', 'class', 'private', 'static', 'final', 'instanceof', 'not', 'and', 'in', 'true', 'false', 'null', 'None']);
function drawCodeLine(c, x, y, str, th, maxChars) {
  const s = maxChars === undefined ? str : str.slice(0, maxChars);
  const re = /(\s+|"[^"]*"?|#.*$|\/\/.*$|@\w+|\d+|\w+|[^\s\w])/g;
  let m, cx = x;
  while ((m = re.exec(s))) {
    const tok = m[0];
    let col = th.text;
    if (tok.startsWith('"')) col = th.str;
    else if (tok.startsWith('#') || tok.startsWith('//')) col = th.com;
    else if (tok.startsWith('@')) col = th.acc;
    else if (/^\d+$/.test(tok)) col = th.num;
    else if (KW.has(tok)) col = th.kw;
    else if (/^[A-Z]/.test(tok)) col = th.fn;
    c.fillStyle = col; c.fillText(tok, cx, y);
    cx += c.measureText(tok).width;
  }
  return cx;
}
// state: { req, file, code:[...], typed:chars, hl:{line, color}, term:[{text,color}], chat:[{who,text}], banner }
function drawIDE(tex, theme, st, t) {
  const c = tex.userData.ctx, W = tex.userData.canvas.width, H = tex.userData.canvas.height, th = THEMES[theme];
  c.fillStyle = th.bg; c.fillRect(0, 0, W, H);
  // title bar
  c.fillStyle = th.panel; c.fillRect(0, 0, W, 34);
  ['#ff6b6b', '#ffd27a', '#5ff2a0'].forEach((col, i) => { c.fillStyle = col; c.beginPath(); c.arc(20 + i * 20, 17, 6, 0, TAU); c.fill(); });
  c.font = `600 16px ${FONT_MONO}`; c.fillStyle = th.dim; c.fillText(`${th.name} — ${st.file || ''}`, 90, 23);
  // left: request card
  const LW = 280;
  c.fillStyle = th.panel; c.fillRect(0, 34, LW, H - 34);
  c.fillStyle = th.acc; c.font = `700 15px ${FONT_UI}`; c.fillText('INCOMING REQUEST', 18, 64);
  if (st.req) {
    rrect(c, 14, 76, LW - 28, 170, 12); c.fillStyle = th.line; c.fill();
    c.fillStyle = th.dim; c.font = `700 14px ${FONT_UI}`; c.fillText('👤 user', 28, 100);
    c.fillStyle = th.text; c.font = `700 19px ${FONT_UI}`;
    wrapText(c, st.req, 28, 128, LW - 50, 25, 5);
  }
  // chat bubbles (ChatGPT etc.)
  let cy = 266;
  for (const m of st.chat || []) {
    const isG = m.who === 'gpt';
    c.font = `700 13px ${FONT_UI}`; c.fillStyle = isG ? '#8ff0c9' : th.acc; c.fillText(isG ? 'ChatGPT' : m.who, 22, cy);
    c.font = `600 15px ${FONT_UI}`;
    const lines = wrapLines(c, m.text, LW - 56);
    rrect(c, 14, cy + 6, LW - 28, 12 + lines.length * 20, 10); c.fillStyle = isG ? 'rgba(16,163,127,0.25)' : th.line; c.fill();
    c.fillStyle = th.text; lines.forEach((ln, i) => c.fillText(ln, 26, cy + 26 + i * 20));
    cy += 30 + lines.length * 20 + 6;
    if (cy > H - 30) break;
  }
  // editor
  const ex = LW + 16, ey = 52;
  c.font = `500 21px ${FONT_MONO}`;
  let remaining = st.typed ?? Infinity;
  const lh = 31;
  const termH = st.term ? 170 : 0;
  (st.code || []).forEach((ln, i) => {
    const y = ey + 24 + i * lh;
    if (y > H - termH - 8) return;
    if (st.hl && st.hl.line === i) { c.fillStyle = st.hl.color; c.fillRect(ex - 6, y - 19, W - ex, lh); }
    c.fillStyle = th.dim; c.fillText(String(i + 1).padStart(2, ' '), ex, y);
    if (remaining <= 0) return;
    const n = Math.min(remaining, ln.length);
    const endX = drawCodeLine(c, ex + 40, y, ln, th, n);
    remaining -= ln.length + 1;
    if (remaining <= 0 && Math.floor(t * 3) % 2 === 0) { c.fillStyle = th.acc; c.fillRect(endX + 1, y - 17, 10, 22); }
  });
  // terminal
  if (st.term) {
    const ty = H - termH;
    c.fillStyle = '#05080a'; if (theme === 'an') c.fillStyle = '#0f0907'; c.fillRect(LW, ty, W - LW, termH);
    c.fillStyle = th.dim; c.font = `700 13px ${FONT_UI}`; c.fillText('TERMINAL', LW + 16, ty + 22);
    c.font = `600 18px ${FONT_MONO}`;
    st.term.slice(-5).forEach((l, i) => { c.fillStyle = l.color || th.text; c.fillText(l.text, LW + 16, ty + 50 + i * 25); });
  }
  if (st.banner) {
    const b = st.banner;
    c.fillStyle = b.bg; rrect(c, W / 2 - 230 + LW / 2, H / 2 - 50, 460, 100, 18); c.fill();
    c.fillStyle = '#fff'; c.font = `800 34px ${FONT_UI}`; c.textAlign = 'center'; c.fillText(b.text, W / 2 + LW / 2, H / 2 + 12); c.textAlign = 'left';
  }
  tex.needsUpdate = true;
}
function wrapLines(c, text, maxW) {
  const words = text.split(' '); const lines = []; let cur = '';
  for (const w of words) { const test = cur ? cur + ' ' + w : w; if (c.measureText(test).width > maxW && cur) { lines.push(cur); cur = w; } else cur = test; }
  if (cur) lines.push(cur);
  return lines;
}
function wrapText(c, text, x, y, maxW, lh, maxLines = 99) { wrapLines(c, text, maxW).slice(0, maxLines).forEach((l, i) => c.fillText(l, x, y + i * lh)); }

// direct-message window used for the evening escalation
function drawDM(tex, theme, msgs, typing, title) {
  const c = tex.userData.ctx, W = tex.userData.canvas.width, H = tex.userData.canvas.height, th = THEMES[theme];
  c.fillStyle = th.bg; c.fillRect(0, 0, W, H);
  c.fillStyle = th.panel; c.fillRect(0, 0, W, 44);
  c.fillStyle = th.text; c.font = `700 26px ${FONT_UI}`; c.fillText(title, 24, 31);
  c.fillStyle = th.panel; c.fillRect(0, H - 64, W, 64); c.fillStyle = th.line; rrect(c, 20, H - 50, W - 40, 38, 19); c.fill();
  let y = 96;
  for (const m of msgs) {
    const mine = m.mine;
    c.font = `700 30px ${FONT_UI}`;
    const lines = wrapLines(c, m.text, W * 0.66);
    const bw = Math.max(...lines.map((l) => c.measureText(l).width)) + 40, bh = 22 + lines.length * 36;
    const bx = mine ? W - bw - 16 : 16;
    rrect(c, bx, y, bw, bh, 12); c.fillStyle = mine ? (theme === 'oa' ? '#1d5a50' : '#7a3f20') : th.line; c.fill();
    c.fillStyle = th.text; lines.forEach((l, i) => c.fillText(l, bx + 20, y + 38 + i * 36));
    c.font = `700 18px ${FONT_UI}`; c.fillStyle = th.dim; c.fillText(m.from, bx + 6, y - 8);
    y += bh + 40;
  }
  if (typing) { c.fillStyle = th.dim; c.font = `600 20px ${FONT_UI}`; c.fillText(typing, 40, H - 24); }
  tex.needsUpdate = true;
}
