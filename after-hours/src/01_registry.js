// ───────────────────────── story registries (filled by env + chapter files) ─────────────────────────
const SHOTS = [];      // {t0, t1, env, seq, cam(t, lt) -> {p, t, fov, roll}, grade}
const LINES = [];      // {t, d, who, text}
const CHAPTERS = [];   // {t, name}
const SEQ = {};        // seq name -> fn(t, env, shot)
const SFX = [];        // {t, name, opts} — consumed by audio
const BUILDERS = [];   // env builder fns
const A = {};          // actors: cx, cc, gpt
const TITLES = [];     // {t0, t1, sub}

