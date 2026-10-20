// Keyboard/mouse with pointer lock, and touch controls (joystick, look-drag, buttons).
export class Input {
  constructor(canvas, game) {
    this.canvas = canvas;
    this.game = game;
    this.keys = new Set();
    this.pressed = new Set();
    this.mouseDX = 0;
    this.mouseDY = 0;
    this.mouseL = false;
    this.mouseR = false;
    this.mouseLPressed = false;
    this.mouseRPressed = false;
    this.wheel = 0;
    this.isTouch = matchMedia('(pointer: coarse)').matches || 'ontouchstart' in window;
    this.touch = { moveX: 0, moveY: 0, lookX: 0, lookY: 0, jump: false, sneak: false, attack: false, use: false, attackPressed: false, usePressed: false, sprint: false };
    this.enabled = false;
    this.bindKeyboard();
    this.bindMouse();
    if (this.isTouch) this.bindTouch();
  }

  get locked() { return document.pointerLockElement === this.canvas; }

  requestLock() {
    if (this.isTouch) return;
    if (!this.locked) {
      try {
        const p = this.canvas.requestPointerLock({ unadjustedMovement: true });
        if (p && p.catch) p.catch(() => this.canvas.requestPointerLock());
      } catch { this.canvas.requestPointerLock(); }
    }
  }

  bindKeyboard() {
    addEventListener('keydown', (e) => {
      if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT')) return;
      if (!this.keys.has(e.code)) this.pressed.add(e.code);
      this.keys.add(e.code);
      if (['Space', 'ArrowUp', 'ArrowDown', 'Tab', 'F3', 'F5', 'F1'].includes(e.code)) e.preventDefault();
      if (e.ctrlKey && ['KeyW', 'KeyS', 'KeyD'].includes(e.code)) e.preventDefault();
      this.game.onKey(e.code, e);
    });
    addEventListener('keyup', (e) => this.keys.delete(e.code));
    addEventListener('blur', () => { this.keys.clear(); this.mouseL = this.mouseR = false; });
  }

  bindMouse() {
    const c = this.canvas;
    c.addEventListener('mousedown', (e) => {
      if (this.isTouch) return;
      if (!this.locked) { if (this.enabled) this.requestLock(); return; }
      if (e.button === 0) { this.mouseL = true; this.mouseLPressed = true; }
      if (e.button === 2) { this.mouseR = true; this.mouseRPressed = true; }
      if (e.button === 1) { this.game.pickBlock(); e.preventDefault(); }
    });
    addEventListener('mouseup', (e) => {
      if (e.button === 0) this.mouseL = false;
      if (e.button === 2) this.mouseR = false;
    });
    addEventListener('mousemove', (e) => {
      if (!this.locked) return;
      this.mouseDX += e.movementX;
      this.mouseDY += e.movementY;
    });
    c.addEventListener('contextmenu', (e) => e.preventDefault());
    addEventListener('wheel', (e) => {
      if (!this.locked) return;
      this.wheel += Math.sign(e.deltaY);
    }, { passive: true });
    document.addEventListener('pointerlockchange', () => {
      if (!this.locked && this.enabled && !this.isTouch) this.game.onPointerUnlock();
    });
  }

  bindTouch() {
    document.body.classList.add('touch');
    const t = this.touch;
    const joy = document.getElementById('joystick');
    const knob = document.getElementById('joyKnob');
    let joyId = null, joyCX = 0, joyCY = 0;
    const lookTouches = new Map(); // id -> {x,y,startX,startY,start,moved}
    const R = 55;

    const onJoy = (x, y) => {
      let dx = x - joyCX, dy = y - joyCY;
      const l = Math.hypot(dx, dy);
      if (l > R) { dx = (dx / l) * R; dy = (dy / l) * R; }
      knob.style.transform = `translate(${dx}px, ${dy}px)`;
      t.moveX = dx / R;
      t.moveY = -dy / R;
      t.sprint = l > R * 1.25 && -dy / R > 0.8;
    };

    joy.addEventListener('touchstart', (e) => {
      e.preventDefault();
      const touch = e.changedTouches[0];
      joyId = touch.identifier;
      const r = joy.getBoundingClientRect();
      joyCX = r.left + r.width / 2; joyCY = r.top + r.height / 2;
      onJoy(touch.clientX, touch.clientY);
    }, { passive: false });

    const canvas = this.canvas;
    canvas.addEventListener('touchstart', (e) => {
      e.preventDefault();
      if (!this.enabled) return;
      for (const touch of e.changedTouches) {
        lookTouches.set(touch.identifier, { x: touch.clientX, y: touch.clientY, sx: touch.clientX, sy: touch.clientY, start: performance.now(), moved: false, breaking: false });
      }
    }, { passive: false });

    addEventListener('touchmove', (e) => {
      for (const touch of e.changedTouches) {
        if (touch.identifier === joyId) { onJoy(touch.clientX, touch.clientY); continue; }
        const lt = lookTouches.get(touch.identifier);
        if (!lt) continue;
        const dx = touch.clientX - lt.x, dy = touch.clientY - lt.y;
        t.lookX += dx;
        t.lookY += dy;
        lt.x = touch.clientX; lt.y = touch.clientY;
        if (Math.hypot(touch.clientX - lt.sx, touch.clientY - lt.sy) > 12) lt.moved = true;
      }
    }, { passive: false });

    const end = (e) => {
      for (const touch of e.changedTouches) {
        if (touch.identifier === joyId) {
          joyId = null; t.moveX = 0; t.moveY = 0; t.sprint = false;
          knob.style.transform = '';
          continue;
        }
        const lt = lookTouches.get(touch.identifier);
        if (!lt) continue;
        lookTouches.delete(touch.identifier);
        // short tap without movement = use/place (or attack a mob)
        if (!lt.moved && !lt.breaking && performance.now() - lt.start < 300 && this.enabled) {
          this.tapX = touch.clientX; this.tapY = touch.clientY;
          t.usePressed = true;
          t.tapAttack = true;
        }
      }
      if (lookTouches.size === 0) t.attack = false;
    };
    addEventListener('touchend', end);
    addEventListener('touchcancel', end);

    // long-press to break
    setInterval(() => {
      let breaking = false;
      for (const lt of lookTouches.values()) {
        if (!lt.moved && performance.now() - lt.start > 280) { lt.breaking = true; }
        if (lt.breaking) breaking = true;
      }
      t.attack = breaking;
    }, 50);

    const btn = (id, down, up) => {
      const el = document.getElementById(id);
      if (!el) return;
      el.addEventListener('touchstart', (e) => { e.preventDefault(); e.stopPropagation(); el.classList.add('down'); down(); }, { passive: false });
      el.addEventListener('touchend', (e) => { e.preventDefault(); el.classList.remove('down'); if (up) up(); }, { passive: false });
      el.addEventListener('touchcancel', () => { el.classList.remove('down'); if (up) up(); });
    };
    btn('btnJump', () => { t.jump = true; t.jumpPressed = true; }, () => { t.jump = false; });
    btn('btnSneak', () => { t.sneakToggle = !t.sneakToggle; t.sneak = t.sneakToggle; document.getElementById('btnSneak').classList.toggle('on', t.sneak); });
    btn('btnFly', () => { t.toggleFly = true; });
    btn('btnDown', () => { t.flyDown = true; }, () => { t.flyDown = false; });
    btn('btnInv', () => this.game.ui.toggleInventory());
    btn('btnCam', () => this.game.cycleCamera());
    btn('btnPause', () => this.game.pause());
    btn('btnDrop', () => { t.drop = true; });
    btn('btnAttack', () => { t.attack = true; t.attackPressed = true; t.btnAttack = true; }, () => { t.attack = false; t.btnAttack = false; });
    btn('btnSprint', () => { t.sprintToggle = !t.sprintToggle; document.getElementById('btnSprint').classList.toggle('on', t.sprintToggle); });
  }

  // Produce the per-frame control state and reset one-shot events.
  poll(settings) {
    const k = this.keys;
    const p = this.pressed;
    const t = this.touch;
    const sens = settings.sensitivity * 0.0022;
    const tsens = settings.sensitivity * 0.0055;
    const s = {
      moveX: 0, moveY: 0, jump: false, sneak: false, sprint: false,
      lookX: this.mouseDX * sens + t.lookX * tsens,
      lookY: this.mouseDY * sens * (settings.invertY ? -1 : 1) + t.lookY * tsens,
      attack: this.mouseL || t.attack,
      use: this.mouseR,
      attackPressed: this.mouseLPressed || !!t.attackPressed,
      usePressed: this.mouseRPressed || !!t.usePressed,
      jumpPressed: p.has('Space') || !!t.jumpPressed,
      forwardPressed: p.has('KeyW') || p.has('ArrowUp'),
      hotbar: null,
      scroll: this.wheel,
      drop: p.has('KeyQ') || !!t.drop,
      dropAll: p.has('KeyQ') && (k.has('ControlLeft') || k.has('ControlRight')),
      toggleFly: !!t.toggleFly,
      isTouch: this.isTouch,
      tapAttack: !!t.tapAttack,
    };
    if (this.enabled) {
      if (k.has('KeyW') || k.has('ArrowUp')) s.moveY += 1;
      if (k.has('KeyS') || k.has('ArrowDown')) s.moveY -= 1;
      if (k.has('KeyA') || k.has('ArrowLeft')) s.moveX -= 1;
      if (k.has('KeyD') || k.has('ArrowRight')) s.moveX += 1;
      s.jump = k.has('Space') || t.jump;
      s.sneak = k.has('ShiftLeft') || k.has('ShiftRight') || t.sneak || !!t.flyDown;
      s.sprint = k.has('ControlLeft') || k.has('ControlRight') || t.sprint || !!t.sprintToggle;
      s.moveX += t.moveX;
      s.moveY += t.moveY;
      for (let i = 1; i <= 9; i++) if (p.has('Digit' + i)) s.hotbar = i - 1;
    } else {
      s.lookX = s.lookY = 0;
      s.attack = s.use = s.attackPressed = s.usePressed = s.jumpPressed = s.drop = false;
      s.scroll = 0;
    }
    this.mouseDX = this.mouseDY = 0;
    this.mouseLPressed = this.mouseRPressed = false;
    this.wheel = 0;
    t.lookX = t.lookY = 0;
    t.attackPressed = t.usePressed = t.jumpPressed = t.toggleFly = t.drop = t.tapAttack = false;
    p.clear();
    return s;
  }
}
