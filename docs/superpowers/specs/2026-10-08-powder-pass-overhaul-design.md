# Powder Pass — Complete Overhaul: Design Spec

- Date: 2026-10-08 · Base: v30 (`claude/peaceful-meitner-3psfer`, identical to the live artifact) · Work branch: `claude/powder-pass-overhaul`
- Source requirements: "Powder Pass — Complete Ski Game Overhaul" master prompt (§1–§24). Section numbers below (§n) refer to it.
- Status of work: `docs/overhaul-plan.md` (task ledger).

## 1. Goals
Deliver the improved existing game (one file, `powder-pass.html`), not a prototype. Every claim is backed by a headless check (Playwright + SwiftShader) or a QA screenshot.

| Area | Done means |
|---|---|
| Characters | All humans ~10 % shorter, shorter necks, soles on the surface everywhere (±3 cm hard floors, −10..+3 cm snow), hips on seats |
| First person | The same person() rig as third person; head hidden; camera at the rig's eyes; look pitch/yaw never rotates hips, legs or skis |
| Skiing | Tuck at ≥ 25 km/h with hysteresis; alternating skate push-off below it on flat ground while accelerating; pole plants |
| Buildings | Hotel door / lobby / breakfast / bar / upstairs / elevator / suite defects removed; Lodge Café rebuilt with Mia + in-world ordering; Pro Shop chimney + z-fighting fixed; gondola blue pad gone |
| NPCs | Role/state machine with schedules, nav graph + avoidance, staff stay at posts, guests do varied routines, café customers queue/sit/leave |
| Ski school | Instructor-led lessons on the Magic Carpet with a real state sequence |
| World | Sun −10 %, less snow glare, moon, richer clouds, weather states with wind-driven snow, birds + deer |
| Performance | Fog, sector streaming (visual only), culling, sim LOD; measured before/after |

## 2. Architecture constraints (from the code, see HANDOFF.md §3–4)
- Script blocks: `pp-world` (world gen IIFE, exports `SK`), `pp-game` (`ppBoot()`: loop, player, economy, NPC ticks, editor).
- **Editor keys** are build-order index paths. New geometry goes at the end of a builder, or at runtime in pp-game. Removing a mid-builder part only changes merged-mesh contents (B merges by colour), but removing a whole colour bucket or a group shifts keys: verify every world change with `tools/keycheck.js` (must report 0 changed).
- Collision: `SK.SOLIDS` boxes `{x,z,a,hw,hd,top?,bot?,off}`; walk floors `SK.FLOORS` rects (`y0/y1`, ramp axis); terrain `SK.groundY` (includes park ramps).
- People: `person(o)` = procedural group rig (pelvis → torso → neck → head; shoulder → elbow → hand → pole; per-foot ski/boot group + two-bone leg IK). Avatars (`AVS` skier, `AVW` walker) wrap a person scaled 0.5 with a hip pivot.
- Save: `powderpass.save`, sanitised in `loadSave`; new fields always get defaults.

## 3. Design by subsystem

### 3.1 Body scale + neck (§3)
- Single constant `HUMAN_K = 0.9` applied once in `person()` to the root group (uniform: keeps proportions, IK lengths, attachments). Neck: neck group lowered (head offset 0.26 → 0.14 rig units) and neck cylinder shortened; the collar loft stays so the jacket still closes under the chin.
- Kid/fem scale already exist (root 0.8 / 0.95); seat height math in poses (`seatH`, `pelY`) is in rig units, so it must divide by the person's total scale (`HUMAN_K × kid/fem × wrapper scale`). Fix: `person()` exposes `g.userData.bodyK` and the seat poses use `seatH / bodyK`.
- Consumers that assume the old height: first-person eye height (`hy = 1.62`, walk eye height in `W.step`), third-person camera targets, seat anchors (`fy + seatH`), lift seat offsets, collider radius (unchanged 0.4 m, fine), carried items. Each moves to a value derived from the avatar's measured eye height (`SK.EYE`).

### 3.2 Foot grounding (§4, §22)
Root causes found by measurement (v30):
1. Hotel ground floors are not in `FLOORS`, so the walker stands on terrain (y ≈ 0) while the visible floor top is ≈ 0.23–0.29 → boots sunk.
2. Upper-floor `FLOORS` values (`UP`, `UP2`) sit 0.23 below the slab tops.
3. Snow mesh sits 6 cm above `groundY` (intended soft sink, keep but bound it).
4. On slopes the walker's feet stay level to the body → uphill sole buried up to 20 cm.

Design:
- `SURF` walk-surface sampler: for a world (x, z, yRef) returns the top of the highest walkable visible surface below yRef + step height, built lazily per 2 m bin from flat up-facing triangles of building/floor meshes (precomputed per mesh, point-in-triangle per query). `W.step` and the avatar offset use `max(groundY, SURF)`; FLOORS remain the authority where they exist and are corrected (+0.23).
- Per-foot conform for people on foot: each foot group is raised to the surface under it and pitched to the local slope; the existing two-bone leg IK follows automatically. Skis keep their slope alignment (already correct).
- Test: sole-vs-surface over 10 indoor/outdoor spots, both avatars + NPCs.

### 3.3 Seats (§15)
Seat anchors become data (`{x,y,z,yaw,h,kind}`) per furniture item; the seat pose solves hip height from `h` in body units (3.1). Bar stools and boot-room benches currently miss their seats → anchors re-derived from the furniture geometry. Occupancy/reservation via a shared `SEATS` registry used by player + NPCs.

### 3.4 Skiing motion (§1)
- `TUCK_ON = 25 km/h (6.94 m/s)`, `TUCK_OFF = 23 km/h`, pose blend over ~0.35 s. Physics drag reduction follows the same gate.
- New pose `Skate`: alternating push (one ski yaws out ~0.35 rad and slides back, weight shifts, opposite pole plants), phase driven by distance travelled, used when forward input, speed < TUCK_ON and slope grade < ~8 %. Skate propulsion in `phys` extended up to TUCK_ON with falloff. Steeper slopes → existing Carving/gliding.
- Pole plant on carve transitions; poles stay in the hands (pole group already parented to hand).

### 3.5 First person (§2)
- Remove the VM skis/mittens rig from use (keep plate/food). Show `AVS`/`AVW` in first person with the head group (+hat, hair, face) hidden via a per-avatar `fp` flag; outlines and shadows unchanged (same materials).
- Camera at the avatar's eye position each frame (after pose), plus look yaw/pitch. Body yaw = heading (`PL.hd` skiing, walk facing walking). Limited torso/head aim toward look (±0.5 rad), never hips/skis.
- Near plane 0.05–0.08; camera pushed 0.1 m forward of the eye centre so the collar and shoulders don't clip.
- Camera switch keeps pose state (same avatar object both modes).

### 3.6 Lighting, sky, weather (§5–7)
- Sun intensity × 0.9; snow material: lower spec/glare band, slightly lower exposure/tone curve on bright end; snow keeps contours via AO + toon bands.
- Moon: disc + glow on the opposite celestial arc, drives a dim blue directional at night.
- Clouds: more cloud layers with density from weather; tint by time of day.
- Weather states: clear, partly cloudy, overcast, light/moderate/heavy snow, blizzard, foggy morning, windy. A state machine per day (seeded, extends `forecast`) interpolates sky, light, fog, snowfall amount, wind (`SNPFX.wind`), ambient sound. NPC/lesson behaviour reads `WX.now`.

### 3.7 Buildings (§8–14, §19)
Each defect: locate the source line, remove or rebuild, keycheck, QA shot before/after, collision walk test.
- Hotel entrance: boot dryers on the inner glass wall overlap the door opening → remove; centre leaves; closed doors get solids (`off` when open).
- Elevator: symmetric piers, consistent jambs on every floor, room-105 door moved off the elevator surround.
- Lobby / breakfast / bar: remove stray panels/slabs/plates (identified via QA), lamps mounted to walls or ceilings only.
- Upstairs: replace solid brown barrier + slat clutter near the elevator with a clean glass/steel guardrail.
- Suite: re-plan into bedroom / lounge / enclosed bathroom with a door.
- Lodge Café: new builder appended (old one hidden, keys kept), counter, barista station, displays, seating, real collision; Mia staff NPC; in-world item pickup/purchase.
- Pro Shop: chimney through the roof correctly, coplanar carpet/floor layers separated, stray shelf panels removed.
- Gondola: remove blue boarding pad; prompt by proximity.

### 3.8 NPCs (§16–18)
- `ACT` agent framework: each NPC = `{role, needs, schedule, goal, path, state}`; states: walk, queue, ride (lift/carpet/elevator), ski (trail follower with steering), sit, eat, work (staff idle loop at post), chat (group).
- Nav: waypoint graph (outdoor paths + building portals + indoor nodes) with A*; local avoidance (separation from other agents + SOLIDS).
- Staff stay within a post radius during shifts. Guests pick activities by time/weather/fatigue.
- Ski school: lesson state machine (assemble → listen → stance → push-off → wedge → turns → carpet queue → ride → regroup → descend one by one → feedback → disperse), students with skill values.

### 3.9 Wildlife (§20)
Birds (instanced, flocking, perch points on roofs/trees, flee radius), deer (forest zones, graze/walk/flee, grounded via groundY, avoid SOLIDS). Pooled; simulation throttled by distance.

### 3.10 Performance (§21)
Measure first (`tools/perf.js`). Fog distance tied to quality + weather; sector grid (64 m) toggles visibility of static decoration beyond fog + buffer; collision, lifts and NPC state never unload; foliage instancing where it doesn't break editor keys; NPC sim LOD by distance.

## 4. Testing
- Persistent probe (`tools/probe.js`): boot once, run snippets, shots; render paused between snippets.
- Per task: a check script in `tools/r15/` that runs standalone or via the probe; red on v30, green after.
- Regression set after each phase: `smoke`, `p6`, `p13`, `r10`, `r11`, `r13`, `keycheck` (0 changed), `soak 5`.

## 5. Phase order
A characters + grounding → B ski motion → C first person → D hotel geometry → E café/shop/gondola/global audit → F lighting/sky/weather → G NPC framework + nav → H ski school + wildlife → I performance → J QA, docs, publish.
