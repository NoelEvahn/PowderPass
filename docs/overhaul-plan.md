# Powder Pass — Complete Overhaul: plan + status

Source: "Powder Pass Complete Overhaul — Master Prompt" (24 sections). Base: v30 (branch `claude/peaceful-meitner-3psfer`, identical to the live artifact).
Work branch: `claude/powder-pass-overhaul`. This file is the running source of truth; update the status column with every commit.

## Constraints (from HANDOFF + prompt)
- One file `powder-pass.html` (Three.js r128 inlined). No new deps, no backend.
- **Editor keys must stay stable**: add new parts at the END of builders or at runtime in pp-game; run `keycheck` after world changes. Removing a part shifts later sibling keys → prefer hiding/zero-scaling + removing collision for an erroneous part when it sits mid-builder, or remove only trailing parts.
- Save format `powderpass.save` stays compatible; new fields get defaults in `loadSave`.
- Keybinds unchanged. Desktop + mobile.
- Test after each subsystem: `tools/smoke.js` + the relevant round test + `tools/probe.js` QA.

## Phases (dependency order)
| # | Phase | Prompt § | Status |
|---|---|---|---|
| A | Tooling: persistent probe (`tools/probe.js`), audits | 24 | done |
| B | Geometry/collision cleanup: hotel door bars, lobby/breakfast/bar panels & slabs, lamps, upstairs railing, elevator symmetry, Pro Shop chimney + z-fighting, gondola blue pad, global prop audit | 8, 9, 11, 13, 14, 19 | todo |
| C | Characters: −10 % height, shorter necks, foot grounding (all floors), seated anchors | 3, 4, 15, 22 | todo |
| D | Skiing anim: 25 km/h tuck w/ hysteresis, skate push-off, pole plants, blends | 1 | todo |
| E | First person = same rig: full body, camera pitch/yaw independent of body heading, head culled | 2 | todo |
| F | Lighting/snow: sun −10 %, snow glare, real local lights; sky/clouds/moon; weather states + wind snow | 5, 6, 7 | todo |
| G | Buildings: suite floor plan, Lodge Café rebuild + Mia + in-world ordering | 10, 12 | todo |
| H | NPCs: role/state machine, nav graph + avoidance, staff posts, guests' routines, café customers | 16, 17 | todo |
| I | Ski school: instructor + students lesson state machine on the Magic Carpet | 18 | todo |
| J | Wildlife: birds + deer | 20 | todo |
| K | Fog/chunk streaming, culling, sim LOD, quality settings, metrics | 21 | todo |
| L | Full QA pass, changelog, HANDOFF/README update, publish | 23, 24 | todo |

## Notes / decisions
- Screenshots from the prompt aren't available in this session; defects are located by the written observations + probe QA shots.
