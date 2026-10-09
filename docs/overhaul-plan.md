# Powder Pass Overhaul — task ledger

Spec: `docs/superpowers/specs/2026-10-08-powder-pass-overhaul-design.md`. Branch `claude/powder-pass-overhaul`. Update this table with every commit.

| Task | What | Check | Status |
|---|---|---|---|
| T0 | Probe tool, plan, spec | — | done |
| A1 | Humans −10 % height, shorter neck, body-scale-aware seat math, eye height follows | `tools/r15/a1.js` | done |
| A2 | Sole grounding: hotel ground floors in FLOORS, upper-floor y fix, SURF sampler, per-foot slope conform | `tools/r15/a2.js` | done |
| A3 | Seat anchors (bar stools, benches, café) + occupancy | `tools/r15/a3.js` | done |
| B1 | 25 km/h tuck with hysteresis + physics gate | `tools/r15/b1.js` | done |
| B2 | Skate push-off pose + pole plants | `tools/r15/b1.js` | done |
| C1 | First person = real avatar, head hidden, independent look | `tools/r15/c1.js` | done |
| D1–D6 | Hotel: entrance, elevator, lobby/breakfast/bar clutter, lamps, upstairs rail, suite | `tools/r15/d1.js` | done (M: upstairs barrier not present in v30 — rounds 10–11 already rebuilt it; re-checked by QA shots) |
| E1–E3 | Café rebuild + Mia + in-world ordering, Pro Shop, gondola pad | `tools/r15/e2.js` | done |
| E4 | Global architecture / prop / collision audit | `tools/r15/e4.js`, `e4z.js` | done (17 small trim-junction z-fights left: rail/post tops, under 0.2 m² each) |
| F1–F3 | Sun/snow, sky/moon/clouds, weather states | `tools/r15/f1.js` | done |
| G1–G3 | NPC framework, nav graph, staff/guests/café customers | `tools/r15/g1.js` | done (LIFE module: 0.5 m nav grids from real solids + A* with string pulling; 7 staff at posts, 11 guests with hour/weather/energy schedules, kids follow a grown-up, 4 café customers through Mia's order queue) |
| H1–H2 | Ski school lessons, wildlife | `tools/r15/h1.js` | done (Coach Kim + 4 students: 12-step lesson on the Magic Carpet / Snowdrop, skill-based speed and falls, 9:00–16:00, none in blizzards; 4 instanced bird flocks on pine tops / snow, flee and resettle; 2 deer herds graze, wander, bolt, steer round trees, distance LOD) |
| I1 | Fog, streaming, LOD, metrics | | todo |
| J1 | Full QA, docs, publish | | todo |
