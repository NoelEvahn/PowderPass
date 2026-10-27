import type { QualityName } from './render/Renderer';
import { detectQuality } from './render/Renderer';

export interface Settings {
  name: string;
  sensitivity: number;
  fov: number;
  quality: QualityName;
  master: number;
  music: number;
  invertY: boolean;
  subtitles: boolean;
  showFps: boolean;
  touchLook: number;
}

const KEY = 'doors-hotel-settings-v1';
const defaults = (): Settings => ({ name: '', sensitivity: 1, fov: 75, quality: detectQuality(), master: 0.8, music: 0.6, invertY: false, subtitles: true, showFps: false, touchLook: 1 });

function read(): Settings {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...defaults(), ...JSON.parse(raw) };
  } catch { /* storage unavailable (private mode) */ }
  return defaults();
}

export const settings: Settings = read();
export function saveSettings() {
  try { localStorage.setItem(KEY, JSON.stringify(settings)); } catch { /* ignore */ }
}

export function profileId(): string {
  try {
    let id = localStorage.getItem('doors-profile');
    if (!id) { id = crypto.randomUUID?.() ?? String(Math.random()).slice(2); localStorage.setItem('doors-profile', id); }
    return id;
  } catch { return 'anon-' + Math.floor(Math.random() * 1e9); }
}
