// IndexedDB persistence for worlds: metadata + RLE-compressed modified chunks.
const DB_NAME = 'powdercraft';
const DB_VER = 1;

function open() {
  return new Promise((resolve, reject) => {
    if (!window.indexedDB) return reject(new Error('IndexedDB unavailable'));
    const req = indexedDB.open(DB_NAME, DB_VER);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('worlds')) db.createObjectStore('worlds', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('chunks')) db.createObjectStore('chunks');
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

let dbPromise = null;
function db() {
  if (!dbPromise) dbPromise = open();
  return dbPromise;
}

function tx(store, mode, fn) {
  return db().then((d) => new Promise((resolve, reject) => {
    const t = d.transaction(store, mode);
    const s = t.objectStore(store);
    const r = fn(s);
    t.oncomplete = () => resolve(r && r.result !== undefined ? r.result : r);
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error);
  }));
}

export function rleEncode(arr) {
  const out = [];
  let i = 0;
  while (i < arr.length) {
    const v = arr[i];
    let n = 1;
    while (i + n < arr.length && arr[i + n] === v && n < 65535) n++;
    out.push(v, n & 255, n >> 8);
    i += n;
  }
  return new Uint8Array(out);
}
export function rleDecode(buf, len) {
  const out = new Uint8Array(len);
  let o = 0;
  for (let i = 0; i < buf.length; i += 3) {
    const v = buf[i], n = buf[i + 1] | (buf[i + 2] << 8);
    out.fill(v, o, o + n);
    o += n;
  }
  return out;
}

export async function listWorlds() {
  try {
    const all = await tx('worlds', 'readonly', (s) => s.getAll());
    return (all || []).sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0));
  } catch {
    return [];
  }
}

export async function saveWorldMeta(meta) {
  try { await tx('worlds', 'readwrite', (s) => s.put(meta)); } catch (e) { console.warn('save meta failed', e); }
}

export async function saveChunks(worldId, chunks) {
  const entries = Object.entries(chunks).filter(([, c]) => c.dirtySave);
  if (!entries.length) return;
  try {
    await tx('chunks', 'readwrite', (s) => {
      for (const [k, c] of entries) {
        s.put({ b: rleEncode(c.blocks), m: rleEncode(c.meta) }, worldId + '|' + k);
        c.dirtySave = false;
      }
    });
  } catch (e) { console.warn('save chunks failed', e); }
}

export async function loadChunks(worldId, len) {
  const out = {};
  try {
    const d = await db();
    await new Promise((resolve, reject) => {
      const t = d.transaction('chunks', 'readonly');
      const range = IDBKeyRange.bound(worldId + '|', worldId + '|￿');
      const req = t.objectStore('chunks').openCursor(range);
      req.onsuccess = () => {
        const cur = req.result;
        if (!cur) return resolve();
        const key = cur.key.slice(worldId.length + 1);
        out[key] = { blocks: rleDecode(cur.value.b, len), meta: rleDecode(cur.value.m, len), dirtySave: false };
        cur.continue();
      };
      req.onerror = () => reject(req.error);
    });
  } catch (e) { console.warn('load chunks failed', e); }
  return out;
}

export async function deleteWorld(worldId) {
  try {
    await tx('worlds', 'readwrite', (s) => s.delete(worldId));
    await tx('chunks', 'readwrite', (s) => s.delete(IDBKeyRange.bound(worldId + '|', worldId + '|￿')));
  } catch (e) { console.warn('delete failed', e); }
}
