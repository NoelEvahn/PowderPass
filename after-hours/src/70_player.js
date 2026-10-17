// ───────────────────────── renderer, post-processing, playback, UI ─────────────────────────
const TEST = /[?&#]test/.test(location.href) || window.__AH_TEST;
if (TEST) document.documentElement.classList.add('test');
const canvas = document.getElementById('c');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.NoToneMapping;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(40, 16 / 9, 0.03, 400);
scene.add(camera);

const composer = new EffectComposer(renderer);
composer.addPass(new RenderPass(scene, camera));
const bloom = new UnrealBloomPass(new THREE.Vector2(512, 288), 0.55, 0.45, 1.0);
composer.addPass(bloom);
const gradePass = new ShaderPass({
  uniforms: {
    tDiffuse: { value: null }, uTime: { value: 0 }, uFade: { value: 0 }, uVig: { value: 0.32 },
    uTint: { value: new THREE.Vector3(1, 1, 1) }, uSat: { value: 1.08 }, uCon: { value: 1.04 }, uAb: { value: 0 }, uFlash: { value: 0 },
    uRes: { value: new THREE.Vector2(1280, 720) },
  },
  vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
  fragmentShader: `
    uniform sampler2D tDiffuse; uniform float uTime, uFade, uVig, uSat, uCon, uAb, uFlash; uniform vec3 uTint; uniform vec2 uRes; varying vec2 vUv;
    float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898,78.233))) * 43758.5453); }
    void main(){
      vec2 d = vUv - 0.5;
      vec3 col;
      if (uAb > 0.0) { col.r = texture2D(tDiffuse, vUv + d * uAb * 0.02).r; col.g = texture2D(tDiffuse, vUv).g; col.b = texture2D(tDiffuse, vUv - d * uAb * 0.02).b; }
      else col = texture2D(tDiffuse, vUv).rgb;
      col *= uTint;
      float l = dot(col, vec3(0.299, 0.587, 0.114));
      col = mix(vec3(l), col, uSat);
      col = (col - 0.5) * uCon + 0.5;
      float v = smoothstep(0.85, 0.2, length(d * vec2(1.0, 0.75)) * (1.0 + uVig));
      col *= mix(1.0, v, 0.85);
      col += (h(vUv * uRes + fract(uTime) * 91.0) - 0.5) * 0.025;
      col = mix(col, vec3(1.0, 0.98, 0.95), uFlash);
      col *= 1.0 - uFade;
      gl_FragColor = vec4(col, 1.0);
    }`,
});
composer.addPass(gradePass);
composer.addPass(new OutputPass());

// ── sizing: keep a 16:9 frame, letterboxed ──
const stage = document.getElementById('stage');
let quality = 1, pixelCap = Math.min(window.devicePixelRatio || 1, 1.75);
const isMobile = /Mobi|Android|iPhone|iPad/i.test(navigator.userAgent);
if (isMobile) pixelCap = Math.min(pixelCap, 1.25);
function layout() {
  const app = document.getElementById('app');
  const cs = getComputedStyle(app);
  const aw = app.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
  const ah = app.clientHeight - parseFloat(cs.paddingTop) - parseFloat(cs.paddingBottom);
  let w = aw, h = (aw * 9) / 16;
  if (h > ah) { h = ah; w = (ah * 16) / 9; }
  w = Math.floor(w); h = Math.floor(h);
  stage.style.width = w + 'px'; stage.style.height = h + 'px'; stage.style.maxWidth = 'none'; stage.style.maxHeight = 'none';
  const pr = TEST ? 1 : pixelCap * quality;
  renderer.setPixelRatio(pr);
  renderer.setSize(w, h, false);
  composer.setPixelRatio(pr);
  composer.setSize(w, h);
  bloom.resolution.set(w * pr * 0.5, h * pr * 0.5);
  gradePass.uniforms.uRes.value.set(w * pr, h * pr);
  camera.aspect = 16 / 9; camera.updateProjectionMatrix();
}
window.addEventListener('resize', layout);

// ── film clock ──
const Film = { t: 0, playing: false, started: false, duration: 0, lastFrameT: 0, ended: false };
function filmNow() {
  if (!Film.playing) return Film.t;
  const now = Audio.clock();
  return Film.anchorFilm + (now - Film.anchorClock);
}
function play() {
  if (Film.t >= Film.duration - 0.05) Film.t = 0;
  Audio.resume();
  Film.anchorFilm = Film.t; Film.anchorClock = Audio.clock();
  Film.playing = true; Film.ended = false;
  Audio.start(Film.t);
  updateUI(true);
}
function pause() {
  Film.t = filmNow(); Film.playing = false;
  Audio.stop();
  updateUI(true);
}
function seek(t) {
  Film.t = clamp(t, 0, Film.duration);
  World.discontinuity = true;
  if (Film.playing) { Film.anchorFilm = Film.t; Film.anchorClock = Audio.clock(); Audio.start(Film.t); }
  renderAt(Film.t, 0);
  updateUI(true);
}

// ── render one frame of the film at time t ──
const World = { discontinuity: true };
let _lastRenderT = -1;
function renderAt(t, dt) {
  const disc = World.discontinuity || Math.abs(t - _lastRenderT) > 0.25 || t < _lastRenderT;
  World.discontinuity = false;
  _lastRenderT = t;
  const info = Story.update(t, disc ? 0 : dt);
  gradePass.uniforms.uTime.value = t;
  gradePass.uniforms.uFade.value = info.fade || 0;
  gradePass.uniforms.uFlash.value = info.flash || 0;
  gradePass.uniforms.uAb.value = info.ab || 0;
  gradePass.uniforms.uVig.value = info.vig ?? 0.32;
  gradePass.uniforms.uSat.value = info.sat ?? 1.08;
  const tint = info.tint || [1, 1, 1];
  gradePass.uniforms.uTint.value.set(tint[0], tint[1], tint[2]);
  bloom.strength = info.bloom ?? 0.55;
  bloom.threshold = info.bloomTh ?? 1.0;
  composer.render();
  Overlay.update(t);
}

// ── main loop with adaptive quality ──
let _prev = performance.now(), _acc = 0, _frames = 0, _slow = 0;
function loop() {
  requestAnimationFrame(loop);
  const now = performance.now(); const realDt = Math.min(0.1, (now - _prev) / 1000); _prev = now;
  if (!Film.started) return;
  let t = filmNow();
  if (t >= Film.duration) { t = Film.duration; if (Film.playing) { Film.t = t; Film.playing = false; Film.ended = true; Audio.stop(); updateUI(true); showUI(); } }
  if (Film.playing || World.discontinuity) {
    const dt = Film.playing ? Math.max(0, t - Film.lastFrameT) : 0;
    Film.lastFrameT = t;
    renderAt(t, dt);
    if (Film.playing) Film.t = t;
  }
  updateUI(false);
  // adaptive resolution
  _acc += realDt; _frames++;
  if (_acc > 2) {
    const avg = _acc / _frames;
    if (avg > 1 / 26 && quality > 0.55) { _slow++; if (_slow >= 1) { quality = Math.max(0.55, quality - 0.15); layout(); _slow = 0; } }
    else if (avg < 1 / 55 && quality < 1) { quality = Math.min(1, quality + 0.1); layout(); }
    _acc = 0; _frames = 0;
  }
}

// ── UI ──
const $ = (id) => document.getElementById(id);
const ICON = {
  play: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 4l15 8-15 8z"/></svg>',
  pause: '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="5" y="4" width="5" height="16" rx="1.2"/><rect x="14" y="4" width="5" height="16" rx="1.2"/></svg>',
  vol: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9v6h4l5 4V5L8 9z"/><path d="M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12"/></svg>',
  mute: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9v6h4l5 4V5L8 9z"/><path d="M17 9l5 6M22 9l-5 6"/></svg>',
};
const fmt = (s) => { s = Math.max(0, Math.floor(s)); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); };
let _uiTimer = 0, _lastUI = 0, subsOn = true;
function showUI() {
  const ui = $('ui'); ui.classList.remove('hide');
  clearTimeout(_uiTimer);
  _uiTimer = setTimeout(() => { if (Film.playing && $('chapters').hidden) ui.classList.add('hide'); }, 2600);
}
function updateUI(force) {
  const now = performance.now();
  if (!force && now - _lastUI < 120) return;
  _lastUI = now;
  const t = Film.playing ? filmNow() : Film.t;
  $('b-play').innerHTML = Film.playing ? ICON.pause : ICON.play;
  $('b-play').setAttribute('aria-label', Film.playing ? 'Pause' : 'Play');
  $('b-mute').innerHTML = Audio.muted ? ICON.mute : ICON.vol;
  $('b-mute').setAttribute('aria-label', Audio.muted ? 'Unmute' : 'Mute');
  $('b-cc').classList.toggle('cc-off', !subsOn);
  $('time').textContent = fmt(t) + ' / ' + fmt(Film.duration);
  const f = clamp(t / Film.duration);
  document.querySelector('#seek .fill').style.width = f * 100 + '%';
  document.querySelector('#seek .knob').style.left = f * 100 + '%';
  $('seek').setAttribute('aria-valuenow', Math.floor(t)); $('seek').setAttribute('aria-valuemax', Math.floor(Film.duration));
  const ch = Story.chapterAt(t);
  $('chname').textContent = ch ? ch.name : '';
  document.querySelectorAll('#chapters button').forEach((b, i) => b.classList.toggle('on', Story.chapters[i] === ch));
}
function setupUI() {
  const ui = $('ui'); ui.hidden = false;
  const seekEl = $('seek');
  for (const ch of Story.chapters) {
    if (ch.t > 0) { const m = document.createElement('div'); m.className = 'mark'; m.style.left = (ch.t / Film.duration) * 100 + '%'; seekEl.appendChild(m); }
    const b = document.createElement('button');
    b.innerHTML = `<span>${fmt(ch.t)}</span>${ch.name}`;
    b.addEventListener('click', (e) => { e.stopPropagation(); seek(ch.t + 0.01); $('chapters').hidden = true; if (!Film.playing) play(); showUI(); });
    $('chapters').appendChild(b);
  }
  $('b-play').addEventListener('click', (e) => { e.stopPropagation(); Film.playing ? pause() : play(); showUI(); });
  $('b-restart').addEventListener('click', (e) => { e.stopPropagation(); seek(0); if (!Film.playing) play(); showUI(); });
  $('b-mute').addEventListener('click', (e) => { e.stopPropagation(); Audio.setMuted(!Audio.muted); updateUI(true); showUI(); });
  $('b-cc').addEventListener('click', (e) => { e.stopPropagation(); subsOn = !subsOn; Overlay.update(Film.t, true); updateUI(true); showUI(); });
  $('b-ch').addEventListener('click', (e) => { e.stopPropagation(); $('chapters').hidden = !$('chapters').hidden; showUI(); });
  $('b-fs').addEventListener('click', (e) => {
    e.stopPropagation();
    const el = $('app');
    try {
      if (document.fullscreenElement || document.webkitFullscreenElement) (document.exitFullscreen || document.webkitExitFullscreen).call(document);
      else { const req = el.requestFullscreen || el.webkitRequestFullscreen; if (req) { const p = req.call(el); if (p && p.catch) p.catch(() => {}); } }
    } catch (err) { /* fullscreen unavailable */ }
    try { screen.orientation && screen.orientation.lock && screen.orientation.lock('landscape').catch(() => {}); } catch (err) { /* ignore */ }
    showUI();
  });
  // seeking by drag
  let dragging = false;
  const seekFromEvent = (e) => { const r = seekEl.getBoundingClientRect(); seek(clamp((e.clientX - r.left) / r.width) * Film.duration); };
  seekEl.addEventListener('pointerdown', (e) => { e.stopPropagation(); dragging = true; seekEl.setPointerCapture(e.pointerId); seekFromEvent(e); showUI(); });
  seekEl.addEventListener('pointermove', (e) => { if (dragging) { seekFromEvent(e); showUI(); } });
  seekEl.addEventListener('pointerup', () => { dragging = false; });
  seekEl.addEventListener('click', (e) => e.stopPropagation());
  seekEl.addEventListener('keydown', (e) => { if (e.key === 'ArrowRight') { seek(Film.t + 5); e.preventDefault(); } if (e.key === 'ArrowLeft') { seek(Film.t - 5); e.preventDefault(); } });
  ui.addEventListener('click', (e) => e.stopPropagation());
  stage.addEventListener('pointermove', (e) => { if (e.pointerType === 'mouse') showUI(); });
  stage.addEventListener('click', () => {
    if (!Film.started) return;
    if (!$('chapters').hidden) { $('chapters').hidden = true; return; }
    if ($('ui').classList.contains('hide')) { showUI(); return; }
    Film.playing ? pause() : play(); showUI();
  });
  window.addEventListener('keydown', (e) => {
    if (!Film.started) { if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); begin(); } return; }
    if (e.key === ' ' || e.key === 'k') { e.preventDefault(); Film.playing ? pause() : play(); }
    else if (e.key === 'ArrowRight' && e.target === document.body) seek((Film.playing ? filmNow() : Film.t) + 5);
    else if (e.key === 'ArrowLeft' && e.target === document.body) seek((Film.playing ? filmNow() : Film.t) - 5);
    else if (e.key === 'm') { Audio.setMuted(!Audio.muted); }
    else if (e.key === 'c') { subsOn = !subsOn; Overlay.update(Film.t, true); }
    else if (e.key === 'f') $('b-fs').click();
    else return;
    updateUI(true); showUI();
  });
  document.addEventListener('visibilitychange', () => { if (document.hidden && Film.playing) pause(); });
}

// ── overlays: subtitles, titles, chapter labels ──
const Overlay = {
  _sub: null,
  update(t, force) {
    const line = subsOn ? Story.lineAt(t) : null;
    const key = line ? line.t + line.text : '';
    if (force || key !== this._sub) {
      this._sub = key;
      const el = $('subs');
      if (line) {
        const name = { codex: 'CODEX', claude: 'CLAUDE CODE', gpt: 'CHATGPT', sys: '' }[line.who];
        el.innerHTML = `<span class="line">${name ? `<span class="who ${line.who}">${name}</span>` : ''}${line.text}</span>`;
        el.style.opacity = 1;
      } else el.style.opacity = 0;
    }
    const tc = Story.titleAt(t);
    $('title').style.opacity = tc.o;
    if (tc.sub !== undefined && $('title-sub').textContent !== tc.sub) $('title-sub').textContent = tc.sub;
    const ch = Story.chapterCard(t);
    const ce = $('chapter');
    ce.style.opacity = ch.o;
    if (ch.html && ce.dataset.k !== ch.html) { ce.innerHTML = ch.html; ce.dataset.k = ch.html; }
  },
};

// ── boot ──
async function boot() {
  layout();
  try { await Promise.race([Promise.all([document.fonts.load(`700 20px ${FONT_MONO}`), document.fonts.load(`800 20px ${FONT_UI}`)]), new Promise((r) => setTimeout(r, 2500))]); } catch (e) { /* fonts optional */ }
  const _t0 = performance.now();
  Story.build(scene);
  if (TEST) console.log('build ms', Math.round(performance.now() - _t0));
  Film.duration = Story.duration;
  Audio.init(Story);
  setupUI();
  // pre-warm: render one frame in every set so shaders compile now, not mid-film
  Film.started = true;
  $('note').textContent = 'Preparing scenes…';
  const warm = [2, 8, 20, 30, 45, 62, 85, 95, 106, 125, 138, 147, 162];
  for (let i = 0; i < warm.length; i++) {
    World.discontinuity = true; renderAt(warm[i], 0);
    if (!TEST) await new Promise((r) => setTimeout(r, 0));
  }
  World.discontinuity = true;
  renderAt(3.0, 0); // poster frame behind the Play button
  if (TEST) console.log('first frame ms', Math.round(performance.now() - _t0));
  Film.started = false;
  $('title').style.opacity = 0; $('subs').style.opacity = 0; $('chapter').style.opacity = 0;
  window.__ahReady = true;
  $('note').textContent = 'Sound on · ' + fmt(Film.duration) + ' · Subtitles on';
  const pb = $('playbig'); pb.disabled = false; pb.focus({ preventScroll: true });
  pb.addEventListener('click', (e) => { e.stopPropagation(); begin(); });
  requestAnimationFrame(loop);
}
function begin() {
  if (Film.started) return;
  Film.started = true;
  $('start').hidden = true;
  Film.t = 0; Film.lastFrameT = 0; World.discontinuity = true;
  play(); showUI();
}

window.__AH = {
  renderAt: (t) => { Film.started = true; World.discontinuity = true; renderAt(t, 0); },
  step: (t, dt) => { renderAt(t, dt); },
  get duration() { return Story.duration; }, Film, Story, Audio, seek, play, pause, renderer, scene, camera,
};
boot().catch((e) => { console.error(e); window.__ahErr && window.__ahErr('The film could not start: ' + e.message); });
