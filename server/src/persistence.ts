import fs from 'node:fs';
import path from 'node:path';

export interface Profile { name: string; knobs: number; runs: number; best: number; wins: number; updated: number }

/** Small JSON-file profile store (knobs, run stats). Writes are debounced and atomic. */
export class ProfileStore {
  private data: Record<string, Profile> = {};
  private timer: NodeJS.Timeout | null = null;
  constructor(private file: string | null) {
    if (file && fs.existsSync(file)) {
      try { this.data = JSON.parse(fs.readFileSync(file, 'utf8')); } catch (e) { console.warn('[profiles] could not read store, starting fresh:', (e as Error).message); }
    }
  }
  get(id: string, name: string): Profile {
    let p = this.data[id];
    if (!p) { p = this.data[id] = { name, knobs: 0, runs: 0, best: 0, wins: 0, updated: Date.now() }; this.save(); }
    if (name && p.name !== name) { p.name = name; this.save(); }
    return p;
  }
  recordRun(id: string, door: number, won: boolean, gold: number) {
    const p = this.data[id];
    if (!p) return 0;
    const knobs = Math.floor(door / 2) + Math.floor(gold / 20) + (won ? 100 : 0);
    p.runs++; p.best = Math.max(p.best, door); if (won) p.wins++; p.knobs += knobs; p.updated = Date.now();
    this.save();
    return knobs;
  }
  private save() {
    if (!this.file) return;
    if (this.timer) return;
    this.timer = setTimeout(() => {
      this.timer = null;
      try {
        fs.mkdirSync(path.dirname(this.file!), { recursive: true });
        const tmp = this.file + '.tmp';
        fs.writeFileSync(tmp, JSON.stringify(this.data));
        fs.renameSync(tmp, this.file!);
      } catch (e) { console.error('[profiles] save failed', e); }
    }, 1000);
  }
  flush() { if (this.timer) { clearTimeout(this.timer); this.timer = null; } if (this.file) { try { fs.mkdirSync(path.dirname(this.file), { recursive: true }); fs.writeFileSync(this.file, JSON.stringify(this.data)); } catch { /* ignore */ } } }
}
