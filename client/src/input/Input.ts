import { settings } from '../settings';

export type Action = 'interact' | 'light' | 'use' | 'drop' | 'beat' | 'menu' | 'slot0' | 'slot1' | 'slot2' | 'slot3' | 'slot4' | 'slot5' | 'spectateNext' | 'crouchToggle' | 'unhide';

/**
 * Unified input: keyboard + mouse (pointer lock) and touch (virtual joystick, swipe-to-look, buttons).
 */
export class Input {
  keys = new Set<string>();
  lookDX = 0;
  lookDY = 0;
  moveX = 0;
  moveY = 0;
  sprint = false;
  crouch = false;
  touch: boolean;
  enabled = false;
  private listeners = new Set<(a: Action) => void>();
  private joyId: number | null = null;
  private joyStart = { x: 0, y: 0 };
  private lookId: number | null = null;
  private lookLast = { x: 0, y: 0 };
  private crouchToggled = false;
  private sprintToggled = false;
  joyEl!: HTMLElement;
  joyKnob!: HTMLElement;

  constructor(private canvas: HTMLCanvasElement, private overlay: HTMLElement) {
    this.touch = matchMedia('(pointer: coarse)').matches || 'ontouchstart' in window;
    window.addEventListener('keydown', (e) => this.onKey(e, true));
    window.addEventListener('keyup', (e) => this.onKey(e, false));
    window.addEventListener('blur', () => this.keys.clear());
    document.addEventListener('mousemove', (e) => {
      if (!this.enabled || document.pointerLockElement !== canvas) return;
      this.lookDX += e.movementX;
      this.lookDY += e.movementY;
    });
    canvas.addEventListener('mousedown', (e) => {
      if (!this.enabled) return;
      if (document.pointerLockElement !== canvas && !this.touch) { this.lock(); return; }
      if (e.button === 0) this.emit('use');
      if (e.button === 2) this.emit('interact');
    });
    canvas.addEventListener('contextmenu', (e) => e.preventDefault());
    if (this.touch) this.setupTouch();
  }

  lock() {
    if (this.touch) return;
    const p = this.canvas.requestPointerLock?.() as unknown as Promise<void> | undefined;
    p?.catch?.(() => undefined);
  }
  unlock() { if (document.pointerLockElement) document.exitPointerLock(); }
  get locked() { return document.pointerLockElement === this.canvas; }

  on(fn: (a: Action) => void) { this.listeners.add(fn); return () => this.listeners.delete(fn); }
  emit(a: Action) { for (const l of this.listeners) l(a); }

  private onKey(e: KeyboardEvent, down: boolean) {
    const t = e.target as HTMLElement;
    if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA')) return;
    const k = e.code;
    if (down) this.keys.add(k); else this.keys.delete(k);
    if (!down || e.repeat) return;
    if (k === 'Escape') this.emit('menu');
    if (!this.enabled) return;
    switch (k) {
      case 'KeyE': this.emit('interact'); break;
      case 'KeyF': this.emit('light'); break;
      case 'KeyG': this.emit('drop'); break;
      case 'KeyQ': this.emit('use'); break;
      case 'Space': this.emit('beat'); e.preventDefault(); break;
      case 'KeyC': this.emit('crouchToggle'); break;
      case 'Digit1': case 'Digit2': case 'Digit3': case 'Digit4': case 'Digit5': case 'Digit6':
        this.emit(('slot' + (Number(k.slice(5)) - 1)) as Action); break;
      case 'Tab': this.emit('spectateNext'); e.preventDefault(); break;
    }
  }

  /** Per-frame polling of continuous inputs (keyboard + joystick). */
  poll() {
    if (!this.touch || this.joyId === null) {
      const k = this.keys;
      const kx = (k.has('KeyD') || k.has('ArrowRight') ? 1 : 0) - (k.has('KeyA') || k.has('ArrowLeft') ? 1 : 0);
      const ky = (k.has('KeyW') || k.has('ArrowUp') ? 1 : 0) - (k.has('KeyS') || k.has('ArrowDown') ? 1 : 0);
      if (kx || ky || !this.touch) { this.moveX = kx; this.moveY = ky; }
    }
    if (!this.touch) {
      this.sprint = this.keys.has('ShiftLeft') || this.keys.has('ShiftRight');
      this.crouch = this.keys.has('ControlLeft') || this.keys.has('ControlRight') || this.crouchToggled;
    } else {
      this.crouch = this.crouchToggled;
      this.sprint = this.sprintToggled || Math.hypot(this.moveX, this.moveY) > 0.95;
    }
  }

  toggleCrouch() { this.crouchToggled = !this.crouchToggled; }

  consumeLook(): [number, number] {
    const s = settings.sensitivity * (this.touch ? 0.0055 * settings.touchLook : 0.0022);
    const dx = this.lookDX * s, dy = this.lookDY * s * (settings.invertY ? -1 : 1);
    this.lookDX = 0; this.lookDY = 0;
    return [dx, dy];
  }

  private setupTouch() {
    const ov = this.overlay;
    this.joyEl = ov.querySelector('#joy') as HTMLElement;
    this.joyKnob = ov.querySelector('#joyKnob') as HTMLElement;
    const opts = { passive: false } as AddEventListenerOptions;
    const area = ov.querySelector('#touchArea') as HTMLElement;
    area.addEventListener('touchstart', (e) => {
      if (!this.enabled) return;
      for (const t of Array.from(e.changedTouches)) {
        if (t.clientX < window.innerWidth * 0.42 && this.joyId === null) {
          this.joyId = t.identifier; this.joyStart = { x: t.clientX, y: t.clientY };
          this.joyEl.style.display = 'block';
          this.joyEl.style.left = `${t.clientX - 60}px`; this.joyEl.style.top = `${t.clientY - 60}px`;
        } else if (this.lookId === null) {
          this.lookId = t.identifier; this.lookLast = { x: t.clientX, y: t.clientY };
        }
      }
      e.preventDefault();
    }, opts);
    area.addEventListener('touchmove', (e) => {
      for (const t of Array.from(e.changedTouches)) {
        if (t.identifier === this.joyId) {
          let dx = t.clientX - this.joyStart.x, dy = t.clientY - this.joyStart.y;
          const L = Math.hypot(dx, dy), max = 55;
          if (L > max) { dx *= max / L; dy *= max / L; }
          this.moveX = dx / max; this.moveY = -dy / max;
          this.joyKnob.style.transform = `translate(${dx}px, ${dy}px)`;
        } else if (t.identifier === this.lookId) {
          this.lookDX += (t.clientX - this.lookLast.x);
          this.lookDY += (t.clientY - this.lookLast.y);
          this.lookLast = { x: t.clientX, y: t.clientY };
        }
      }
      e.preventDefault();
    }, opts);
    const end = (e: TouchEvent) => {
      for (const t of Array.from(e.changedTouches)) {
        if (t.identifier === this.joyId) { this.joyId = null; this.moveX = 0; this.moveY = 0; this.joyEl.style.display = 'none'; this.joyKnob.style.transform = ''; }
        if (t.identifier === this.lookId) this.lookId = null;
      }
    };
    area.addEventListener('touchend', end);
    area.addEventListener('touchcancel', end);
    const bind = (id: string, fn: () => void) => {
      const el = ov.querySelector('#' + id) as HTMLElement | null;
      el?.addEventListener('touchstart', (e) => { e.preventDefault(); e.stopPropagation(); fn(); el.classList.add('pressed'); }, opts);
      el?.addEventListener('touchend', () => el.classList.remove('pressed'));
    };
    bind('tInteract', () => this.emit('interact'));
    bind('tCrouch', () => { this.toggleCrouch(); (ov.querySelector('#tCrouch') as HTMLElement).classList.toggle('on', this.crouchToggled); });
    bind('tSprint', () => { this.sprintToggled = !this.sprintToggled; (ov.querySelector('#tSprint') as HTMLElement).classList.toggle('on', this.sprintToggled); });
    bind('tLight', () => this.emit('light'));
    bind('tUse', () => this.emit('use'));
    bind('tBeat', () => this.emit('beat'));
    bind('tMenu', () => this.emit('menu'));
  }
}
