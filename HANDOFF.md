# Powder Pass: project handoff

Paste this file (or attach it with the zip) as the first message of a new chat.

## 1. What it is
- **Powder Pass**: a mobile-first 3D ski-resort game in **one HTML file** (`powder-pass.html`, ~1.3 MB, ~7,100 lines). It uses Three.js r128, inlined.
- **Live artifact:** https://claude.ai/artifact/WgdGTKpWU2SoYSF59Fzfp7
  - Current version: **v23** (round 7). Shared "anyone with the link".
  - Capabilities: `artifact` (the in-game editor saves by republishing itself) and `downloads` (a fallback that saves the file instead).
- **Project files** (zip: `powder-pass-project.zip`):
  - `powder-pass.html`: the game.
  - `README.md`: a feature log for every round of work.
  - `HANDOFF.md`: this file.
  - `tools/`: headless Playwright tests and bots, plus `keys-baseline.json`.

## 2. How to work with me (user preferences)
- Replies are concise and structured: headings, bullets, tables. No filler.
- Analyse requirements and architecture before large implementations.
- Don't rewrite unrelated code. Preserve existing features. Aim for production quality with error handling.
- Point out conflicting instructions. Don't assume missing info; ask.
- End long replies with **Context Status** (Health: Good / Getting Large / Recommend New Chat, plus a reason).
- Never put model identifiers in files or artifacts.

## 3. File layout (script blocks inside powder-pass.html)
| id | role |
|---|---|
| `pp-css` | all CSS (HUD, sheets, map, editor, `#pad` touch buttons; `body.skiing` / `body.walkjump` / `body.ed` classes) |
| `pp-pre` | captures the static markup in `PP_PRE` (used when the editor republishes the page) |
| `pp-edits` | JSON of saved editor edits: `{v, obj:{key:{p,q,s,del}}, add:[...], terrain, names...}` |
| `pp-three` | Three.js r128 |
| `pp-world` | world/asset IIFE: terrain heightfield `HG` (0.75 m grid), trails, lifts, buildings, hotel, people builders. Exports `globalThis.SK` |
| `pp-game` | `ppBoot()`: game loop, player, skiing, economy, quests, hotel, editor, map, and everything below |

## 4. Key architecture
- **Global hub:** `SK`. Notable members:
  - `SK.groundY(wx, wz)` includes park ramp surfaces. `SK.groundRaw` is terrain only; the editor's snapping uses it.
  - `SK.SOLIDS`: collision boxes `{x, z, a, hw, hd, top?, bot?, off}`. `SK.FLOORS`: walkable floor rects.
  - Per-frame and headless stepping: `SK.TICK` (per-frame functions), `SK.play(n, dt)` (headless step), `SK.sim`.
  - Events and toasts: `SK.events.on/emit` (runstart, runend, trick, crash, grind, checkin), `SK.toast`, `SK.sfx(name)` (coin, quest, land, crash, lock, door, ding, click).
  - Hotel: `SK.HOTEL` (`.HX` holds floors, buffet, elev, shaft, beds) and `SK.P6` (hotel/time: `setTime`, `ride`, `op`, `target`).
  - Other systems: `SK.ED` (editor), `SK.MAP`, `SK.RACE`, `SK.SLALOM`.
  - Round 6 systems: `SK.PARK`, `SK.GRIND`, `SK.VM`, `SK.ELEV`, `SK.BF`.
- **Walking:** `W.step(dt)`.
  - State on `W`: `W.x/y/z`, `vx/vz`, `yaw/pitch`, `W.bp` (stride phase).
  - Jump: `W.jy/W.jv`. Seated at breakfast: `W.sit`. On a lift: `W.ride`.
- **Skiing:** `PL.ski(dt)` runs `phys` → `collide` → `land/launch` → `pose` → `camSki`.
  - On a rail, the `GRIND` branch replaces physics.
  - Input comes from `inp()` (keys plus touch `IN`).
- **People:** `person(opts)` builds them; `npc(builder, opts, x, z, ry)` places them.
  - Builders: `SK.PEOPLE.lady/man/woman/teen/boy/girl/staff/instructor`.
  - Animation: `g.userData.play(name, force)`; poses are a `switch(nm)` in the people code (Walking, Sprinting, Sit on chair, Eating, Snacking, Jump, Hockey stop…).
- **Economy:** `State` (save key `powderpass.save`), `ITEMS`, `eat(k)`, `ECON.nearby/act` (a wrapper chain; `p6Target` is the hotel layer), `openSheet(kind, id, at)`.
- **Editor asset keys:** index paths under the resort group (`r.N`, parts `r.N/i/j`). Saved edits depend on them.
  - **Any world-gen change must keep keys stable.** Add new things at runtime in pp-game (as the guests, elevator cab and slalom gates are) instead of inserting into world gen.

## 5. Procedures
**Run tests** (needs Node with Playwright and Chromium; `tools/lib.js` boots the file headless). Run at most about 3 browsers in parallel, or loads time out.
```
node tools/smoke.js          # boot + basics
node tools/editor-test.js; node tools/editor2-test.js
node tools/p6-test.js        # hotel, clock, breakfast plate flow
node tools/p7-test.js; node tools/p8-test.js; node tools/quest-test.js; node tools/cabin-test.js
node tools/p13-test.js       # jump, ramps, grind, eating anim, elevator
node tools/r7-test.js        # spot lights, model doors, diners, real falls, dusk/aurora, warmth, hop, camera lock, HUD
node tools/soak.js 10 7      # 10 min random play, fails on any page error
node tools/p13-shots.js      # screenshots -> tools/out/
node tools/shot.js "[['name', x, y|null, z, yaw, pitch, minutes, 'setupJs']]"   # ad-hoc shots -> tools/out/s-name.png
```
**Key stability check** (after any world change):
```
node tools/keycheck.js dump powder-pass.html /tmp/k.json
node tools/keycheck.js cmp tools/keys-baseline.json /tmp/k.json   # must say "0 changed"
```
("ground moved" lines near ramps are expected: groundY now includes ramp surfaces.)

**Publish** (keeps the same URL):
1. `Artifact read` the URL with `path: index.html`. If its version is newer than v23, the user saved editor edits: merge its `pp-edits` block into the local file first.
2. Strip the skeleton lines:
   `grep -v -x '<!doctype html><html><head>\|</head><body>\|</body></html>' powder-pass.html > powder-pass-artifact.html`
3. Publish with `url: https://claude.ai/artifact/WgdGTKpWU2SoYSF59Fzfp7`. Omit `capabilities` so they carry forward.

## 6. Feature history (all done)
1. **Earlier phases:** resort world, skiing physics, tricks, lifts and gondola, trails with medals, shops, bag, quests (story chapters), cabins, greenhouse, weather, day/night, hotel with rooms and keys, sound, and the developer editor.
2. **Editor round 2:** part selection, box select, terrain shaping tool, start gates linked to trails (NPC paths follow).
3. **Autonomous playtest:**
   - Fixes: rerouted the Mogul, Glades and Lodge trails; reachable gold times; HUD fixes.
   - Additions: trail map (M), run times, Rita's daily race, slalom on Pine Ribbon, soak test.
4. **Characters:** detailed boots and clothing; footprints synced to walk and sprint; shorter falls; map redesigned.
5. **Round 6 (v22):**
   - Jumping on foot, plus a touch JUMP button while walking.
   - Ramps use their real shapes; their box hitboxes were removed.
   - Rail, kink and box grinding with scoring.
   - Eat and drink animations: first-person viewmodel, third-person Snacking pose.
   - Breakfast plate flow (take a plate → pick up to 5 items → sit → eat item by item).
   - Redesigned breakfast hall with an archway from the lobby, guests and a chef.
   - Working elevator: cab, sliding doors, call and ride.
   - Bug fixed: editor snapping now uses terrain-only height.

6. **Round 7 (v23):**
   - Lights: 8 downward spot cones (`SK.P6.SPS`, kinds/angles in `SPOT_K`) + 1 campfire point (`PTS`); toon shader patch `RE_Direct_ToonLocal` stops local lights on back faces (no leaks).
   - Doors: A-frame (`afLeaf`) and Alpine (`alpLeaf`, hung on `g.userData.body` to keep keys) use the model leaf; Alpine is in `SK.CDOORS` as id `alpine`.
   - Breakfast: diners pool in `SK.BF.guests` (`st`: out/in/sit/eat/leave), routes via table-side columns; chef visible 5:45-11:15.
   - Fallen skiers: `fallTick` in pp-game crashes trail skiers (`SK.P8.TSK`) into `FALLEN` slots (max `FALL_MAX` = 2); `State.q.help.sk` only holds today's real falls.
   - Sky: `skyFor` winter curve (dark by 18:00), aurora in the sky shader (`aur`, `time` uniforms). Warmth drain -0.16/s base.
   - Anims: `Hop` / `Land` poses; `userData.snack` overlays eating on any pose. Camera lock: `SK.CAM`, `SK.camLock(on)`, Ctrl key.
   - HUD: CSS block "round 7 HUD" at the end of pp-css; `#sheet[data-kind]` colours; toast classes good/bad/info.

## 7. Known limits / ideas not done
- Third person is forced off indoors, so seated eating is first person only.
- No terrain LOD. Frame rate has only been checked in headless software rendering, never on a real phone.
- NPC skiers don't use the park ramps or rails.
- Diners and fallen skiers don't collide with the player. Pointer lock may be refused inside some sandboxed frames; the camera lock then steers from plain mouse movement.
