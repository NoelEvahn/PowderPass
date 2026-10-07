# Powder Pass

Mobile-first ski-resort game built on `Ski_game_assets.html`. Design plan: https://claude.ai/artifact/VyHNfviXkJ7YJ6mBDaFykG

## Start here (new chat handoff)
- **Play:** https://claude.ai/artifact/WgdGTKpWU2SoYSF59Fzfp7 · **Plan + 2D map (v0.6):** https://claude.ai/artifact/VyHNfviXkJ7YJ6mBDaFykG
- **Done:** Phases 0–6 (all planned phases) + developer editor (✎ button). **Next:** real-phone playtest (frame rate in a night snowstorm on Balanced/Low), then backlog: West Bowl (beacon + 20 medals), Resort Championship (28), rails/boxes grind scoring.
- **Rules:** mobile first; no real-money purchases; third-person camera skiing, first-person walking (V toggles); gondolas for long/tall lifts, chairs for short ones, never mixed; coins only from runs, quests, helping and selling (no pickups).
- **Open decisions:** the plan unlocks Summit Outfitters at 3 medals; not applied (it holds jackets you need for warmth). Summit Hut is back at the summit, so it is naturally behind the Crest Lift (10). Gathering pays ~730 coins/h vs the ~600 target (tune after playtesting).
- **To continue:** attach `powder-pass.html`, this README and the `tools/` folder. The HTML is edited directly now (the old phase0–4b patch scripts are gone); `tools/` holds the headless tests.

## Files
| File | What |
|---|---|
| `powder-pass.html` | The game. Open it in a browser. Edit it directly. |
| `tools/lib.js` | Shared headless boot (Chromium, software WebGL), error capture, `check()`. |
| `tools/smoke.js` | Boot, no errors, save round-trip, screenshot. |
| `tools/quest-test.js` | Phase 5 end to end: all 5 chapters, mitten request, lost items, cocoa, guiding Leo (and leaving him behind), lessons gate trick points, board quest, locked lift keeps your ticket, save round-trip, new day, corrupt save. |
| `tools/visual-p5.js` | Phone-size screenshots: Summit Hut outside/inside, Ski School, Rex, fallen skier, lost item, Leo, board marker, portrait. |
| `tools/p6-test.js` | Clock + midnight rollover, night-only sleep, hotel check-in (random free room, only your door opens, no double check-in), elevator, free breakfast once a day, cabin doors, ski payouts + repeat taper, lights on at night / off at noon, clear glass, save. |
| `tools/p8-test.js` | Boarding needs a gate pass, finish line off the lift base, Alpine Cabin locked/bought/doors open, shuttle + season pass, West Bowl gate, Championship offer, trail skiers + lift riders, screenshots (top exit, lift base, Alpine Cabin, café). |
| `tools/p7-test.js` | Weather: 60-day forecast variety + repeatable, storm peak (snow, fog 130 m, colder), goggles, graphics levels, clear day, settings sheet, audio starts on a tap, settings saved. |
| `tools/cabin-test.js` | Cabin tiers differ, stash store/take, morning goods + wake perks, own hot tub warms you, greenhouse pods (6 trees, one each), stash survives reload. |
| `tools/editor-test.js` | Editor: select, move/rotate/scale (collision box + street light follow), delete + undo, copy/paste/duplicate, group/ungroup, add from the asset list, object search, label rename, NPC path, real tap select, Save → reload the saved HTML and every edit is back, second save byte-identical. |
| `tools/editor2-test.js` | Editor round 2: gates tagged to trails, moving a start gate moves the trail start (length, skier path, run detection, undo), Pine Ribbon start moves the Summit Express skiers' route, an added gate links to the nearest trail end, box select picks individual parts, a part moves alone and saves under its path key, terrain brush (raise/lower/smooth/level, undo, trees follow, mesh = grid), real mouse paint, Save → reload restores terrain/gate link/part, second save identical. |
| `tools/playbot.js` | Autopilot skier: skis every trail (or the ids given) with the real A/D/W/S controls, logs time, top speed, crashes, off-trail frames and the result card, phone screenshots `bot-<trail>-*.png`. |
| `tools/trailcheck.js` | Trail sanity: flat / uphill stretches, trees and collision boxes in each corridor, total drop. |
| `tools/gatesurvey.js` | Screenshot of every trail start / finish gate (contact sheet `gates-sheet.png`). |
| `tools/keycheck.js` | Asset-key stability: `dump <html> <json>` then `cmp a.json b.json`: flags keys whose asset changed (saved editor edits would land on the wrong object) and assets whose ground moved. Run it after any world-generation change. |
| `tools/journey.js` | New-player first day: follows the quest tracker, presses F, screenshots each step. |
| `tools/liftjourney.js` | Ticket booth → gate → walk into a gondola → ride → get off at the top, with screenshots. |
| `tools/trickbot.js` | Air Line kickers: straight air, 360, 360 + grab, 540, backflip; logs air time, height, what scored. |
| `tools/racebot.js` | Rita's race twice: prize once a day, her pace follows your best time. |
| `tools/nighttour.js` | Dusk / night / storm screenshots of village, hotel, lift base, piste and top. |
| `tools/shots.js` | Screenshots from named viewpoints: `node tools/shots.js '[["name", x, z, lookX, lookZ, pitch]]' 390x780`. |
| `tools/topview.js` | Overhead shot of any spot: `node tools/topview.js x z radius name`. |

Tests: `NODE_PATH=$(npm root -g) node tools/smoke.js` (and `quest-test.js`, `visual-p5.js`). Output in `tools/out/`. Headless frames take 3–13 s (software WebGL), so a full quest test is ~8 min. The pre-Phase-5 tests (ski, score, lift, park, econ, shop) were lost with the old container and have not been rebuilt.
For the Artifact copy, strip the `<!doctype html><html><head>`, `</head><body>` and `</body></html>` wrapper lines.
**Before republishing from the local file:** read the live artifact first and copy its `<script type="application/json" id="pp-edits">` block into `powder-pass.html` (edits saved in the editor live only in the published page; a plain republish would wipe them).

## URL flags
- `?dev=1` (or `#dev`): the original asset gallery, orbit camera and buttons.

## Phase 0 status (done)
- Mountain stretched 2× (248 × 476 m) from a `TRAILS` table (13 trails incl. connectors) and `LIFT_LINES` (A built, B–E and the carpet reserved).
- Heights baked once to a 0.75 m grid; terrain drawn as 98 culled chunks; runtime height lookups sample the grid.
- Slope props remapped to the new length and skipped where they'd land on a trail or lift line.
- Forest: 640 trees from 4 instanced variants (was one 600-tree merge: build time 4.8 s → 2.1 s).
- Boots straight into the village in first-person walk mode, with a loading screen.
- Save `powderpass.save` v1 in localStorage: coins, medals, day, position, ticket, camera prefs. Autosaves every 10 s and when the tab hides.

## Phase 1 status (done)
- Touch: floating stick on the left 45% of the screen, drag elsewhere to look; JUMP / GRAB / BRAKE buttons while skiing.
- Keys: WASD/arrows, Space (hold + release) jump, Shift grab, **V** camera, **K** skis on/off, F interact.
- Skiing: slope gravity, carve vs skid grip, groomed vs powder friction, tuck, charged jumps, airtime, spins/flips/grabs controlled with the stick (in the air), landing checks, crashes (tumble / side fall, get up), tree and building collisions.
- Cameras: third-person chase while skiing, first-person while walking; V toggles each, saved. Third-person walking avatar.
- Skis come off automatically when you ski into the Lift A queue.
- `SK.events.on('trick' | 'crash', fn)` for Phase 2 scoring.
- Measured: blue run top speed ~53 km/h, tuck faster; a 360 off a flat jump gets ~1.2 s air.

## Phase 2 status (done)
- A RUN starts when you ski onto a trail (not a connector) and banks when you reach its end. Skis off, or leaving the trail for 3 s, cancels it.
- Points: distance 0.9/m, speed 10/s above 45 km/h and 25/s above 65, tricks (50 + 60/s air, spins 120/200/320, flips 250, grab 50 per 0.5 s, +25% clean landing, combo x1-x5), speed traps 50/100/200 at 45/60/75 km/h, tree close calls in Fir Glades 30.
- Coins = score / 25 x trail multiplier (green 1, blue 1.5, black 2.5, backcountry 4). A crash only resets the combo.
- Medals: bronze / silver / gold at 35% / 65% / 100% of the trail's gold target (length x 5/7/10/14 per 100 m), runs started at the top only. Best score + tier per trail saved (`State.best`).
- Start/finish gates on the new trails (not where trails merge), speed-trap gates on Pine Ribbon and Eagle Chute.
- Lift ticket costs 10 coins; free if you have fewer than 10, so you can't get stuck.
- All numbers live in `SCORE` (phase2.py). Events: `trick`, `crash`, `runstart`, `runend`, `trap`.
- Measured: steering bot on Pine Ribbon: plain run ~19 coins, tuck ~22, with 360+grab tricks ~72. Scripted example run = 1,043 points = 63 coins.

## Phase 3 status (done)
- Lifts: five, built from `LIFT_LINES` at any angle (`buildLift2(R, x, z, L, {ry, id, name})` builds in the lift's own frame inside a rotated group). One carrier type per lift: gondola if the line is >= 160 m long or rises >= 40 m, chairs otherwise. A, D, E gondolas; B, C chairs.
- Speeds 1.5x (cable 4.5 m/s, station crawl 0.825 m/s). Measured bottom-to-top: A 69 s, B 33, C 39, D 61, E 50.
- One ticket for every lift (`SK.LIFTX` facade); 10 coins. Skis come off in any lift queue.
- **Skip ride** button (or X) while riding: fades and drops you at the exit of the station the carrier is heading for, facing away from the station.
- Carriers further than 230 m from the camera are hidden.
- Magic Carpet (1.8 m/s) next to Snowdrop. Air Line park: four 2.4 m snow kickers carved into Timberline's terrain (1.3-1.6 s air).
- Building shells with labels: Pro Shop, Village Mart, Trading Post, Quest Board, Greenhouse, Summit Outfitters, Patrol Hut, Summit Hut, Vending. Lift and trail-start labels. Labels fade when you are within ~9 m.
- Physics: airborne when the ground drops 2 cm below the skis in one 1/120 s step (was 18 cm), slope read just behind the skis, so lips launch you.

## Phase 4 status (done)
- **Meters:** Energy and Warmth (E/W pills). Energy drains with movement and climbing; Warmth drains outside (more at altitude and in snowfall, less with a better jacket) and refills indoors, in gondolas, at campfires and hot pools. Low Energy slows you; low Warmth drains Energy. Both at 0 → Patrol rescue: back to the village, up to 50 coins.
- **Gathering:** daily seeded spots: logs (axe swings 3/2/1), pine cones, berries, clear ice (needs an ice pick), 6 cacao a day at the Greenhouse. 1 per pick. Bag 10 / 20 / 40 slots (packs). Bag button or **I**.
- **Selling:** Trading Post, prices change ±25% each day (arrows show up or down). Wood Shed: 5 logs → firewood (55). Cabin stove: jam, wreaths, cocoa mix.
- **Shops:** Pro Shop (skis: carver grip, twin-tip spins, powder, racer), Summit Outfitters (packs, axes, ice pick, helmet; goggles/beacon "coming later"), Village Mart + café (food, fondue: x1.2 run score), Summit Hut, Vending. Gear is owned for good; equip any time. Jackets, boots and colours too.
- **Days:** sleep at the hotel (50, free if broke) or in your cabin → next day: new spots, new prices, meters full. Cabins 3,000–30,000 (stove + free bed).
- All data in `ITEMS`, `GEAR`, `SHOPS`, `CABINS`, `STOVE`, `NODE_N` (phase4.py). Gathering-only income ~730 coins/h after the hotel (target was ~600, tune after playtesting).

## Walk-in shops (Phase 4b, done)
- **Main Street** (village, east of the hotel): Pro Shop, Summit Outfitters, Village Mart and Trading Post stand side by side facing a paved plaza with lamps, benches and planters. The Summit Hut stays at the summit as the mountain food stop.
- Each shop has its own build: Pro Shop = chalet (steep roof, glass storefront, half-timbering, flower boxes); Outfitters = stone base, timber walls, side gables, chimney; Mart = flat roof, parapet, sign band, striped awning, glass front; Trading Post = log cabin with a porch and barrels.
- Inside, each is laid out for its purpose: ski wall, jacket rail, pack table, axe pegboard / jacket hooks, boot rack, helmet stand, glass case / fridge, grocery shelves, deli case, cocoa machine / produce bins + live price board / food counter + stove. A clerk stands behind each counter.
- Price tags and the hover card are HTML overlays (sharp at any screen density, sized for phones; the card stays clear of the HUD and buttons). Look at an item, then press **F**, tap the card button, or tap the item. Buys of 500+ need a second press. Trading Post bins sell the whole stack.
- Inside a store the camera is always first person; stores count as indoors.
- Rendering: the auto-resolution scaler could only step down on 60 Hz screens (it waited for < 13 ms frames); it now recovers at < 20 ms, drops only below ~33 fps, never below 1.25x. Place labels are drawn at 2x.
- Still a menu: Bag, cabins, hotel, Wood Shed, Greenhouse, Lodge Café, vending machine.

## Phase 5 status (done)
- **Medal locks:** Lodge Lift 3, West Ridge 6, Crest Lift 10, Eagle Lift 15 (A and the Magic Carpet always open). A locked gate stays shut and keeps your ticket; the kiosk says how many medals you need. Medals = trail tiers + 2 per story chapter.
- **Story (5 chapters, chain automatically):** 1 First Tracks (Coach Bo, Magic Carpet, ski Snowdrop) 100 · 2 Lodge Supply Run (3 cacao to Chef Mia) 250 · 3 Patrol Trainee (Rex, help 3 fallen skiers) 400 · 4 Fire for the Hotel (4 firewood) 400 · 5 Crown of the Crest (10 medals, gold on Crest Couloir) 1,000 + Gold Edition skis (never sold; equip from the Bag).
- **Requests (one-off):** lost mitten (hotel guest, 60), berries (Greenhouse, 50), clear ice (café, 90, after ch 2), jam (hotel kitchen, 120, after ch 4). Accept and hand over inside the hotel / café / greenhouse sheets.
- **Daily board:** 3 a day from the plan's 10 templates, seeded by day, only ones you can do (e.g. black-trail rescues need chapter 3 and an open black trail). Active automatically, paid on completion.
- **Helping (re-seeded each morning on trails you can reach):** 4 fallen skiers (40/60/100/150 by grade, F when stopped next to them, works on skis), 3 lost items (Hotel Lost & Found, 60–150), cocoa for 4 people in the Lift A queue (25 + tip), guide Leo down Snowdrop once a day (80; he follows your tracks at max 4.5 m/s and gives up if you get 25 m ahead).
- **Ski School lessons:** 540 (300), backflip (450), front flip (450), 720 (800, needs 540). One at a time; passed by landing the trick. Until learned, 540/720 pay as a 360 and flips pay no flip bonus (a toast says so, once a minute).
- **UI:** Quests button / **J** (log with Track buttons, helping, lessons, lift locks), tracker card under the HUD (step, distance, arrow; tap opens the log; hidden during runs on screens under 1000 px), markers: ! new, ? hand in, red + fallen skier, blue star lost item. Completion banner.
- **Save:** `State.q` in the same v1 save (sanitised on load; garbage falls back to a fresh quest state).
- **Summit Hut** moved to Main Street (north side of the plaza, facing the other shops), village prices (granola 10, sandwich 24, drink 35, cocoa 12, soup 25, warmers 18). A plain Warming Hut stands at the old summit spot so you can still warm up there.
- **Fixed:** after a reload, resource nodes, Trading Post prices and the jacket colour came from day 1 / defaults (they were set up before the save loaded).
- Code: one block, `POWDER PASS: quests, helping skiers, Ski School, medal locks (Phase 5)`, data in `CH`, `REQ`, `LESSONS`, `DAILY`, `LOCKS`, `QP`. Test hooks on `SK.QX`.

## Polish pass after Phase 5 (playtest feedback)
- **Shops spread over the map:** Main Street keeps the Village Mart and Trading Post. Pro Shop moved next to the Patrol Hut (40, −225), Summit Outfitters beside the Mountain Lodge (60, −90), Summit Hut back at the summit (−8, −356) with mountain prices. Each has a flat pad. The free lots hold an ice rink and a decorated village tree.
- Street lamps lean over the paving; the berry bush and a planter that blocked the rink gate moved.
- **Magic Carpet** rebuilt: no more blue blocks; blue skirt with yellow trim, ribs that move up the belt, arched hoops with a glass strip, handrailed entry / exit mats with chevrons, stop post, operator booth.
- **Greenhouse** rebuilt as a 12 × 8 m walk-in glasshouse (brick knee wall, tiled floor, glass gable roof, heater, lamps). Six cacao trees with pods on the trunk; each has one ripe (orange) pod a day: walk up and press F. Fern stands at her potting bench (berry request, today's count). It counts as indoors.
- **Shop prices:** at most 5 small tags, only for items in front of you within 5 m, never overlapping, kept off the HUD; ✓ for owned / equipped; the item you look at gets a yellow tag. Its card is docked at the bottom of the screen.
- **Cabins have tiers:** stash 10 / 20 / 40 / 80 (shared by every cabin you own), goods left in the stash each morning (2 logs → logs, berries, cones, cacao), stove recipes (Stone Hut: jam + wreath), Chalet and A-frame: hot tub (owners warm up fastest there) and breakfast (score x1.2 for 10 min when you wake there), A-frame: waxed skis (+8% top speed for 15 min). The cabin sheet lists the perks before you buy.
- Cabins show their tier outside: lanterns, woodpile, window boxes, skis by the door, string lights, steaming hot tub. Chimney smoke is live (all lodges, A-frames, Patrol Hut) instead of frozen white balls.
- **Sky:** gradient dome (pale haze at the horizon matching the fog, deep blue overhead, soft sun glow) instead of the banded wall.
- Toasts drop below the quest tracker.

## Day/night, lighting, hotel (second playtest)
- **Clock:** 24 h = 20 real minutes (`CLOCK_RATE`), shown in the HUD. Midnight starts a new day (resources, prices, quests, cabin goods). Sleep only 20:00–05:00 (cabin bed, or your hotel room bed); you wake at 07:00 with full meters.
- **Sky / light:** sun moves east to west; dusk colours, starry night, dim moonlight with shadows; fog and ambient follow. Windows glow warm at night; fake beams and dust only after dark.
- **Real lights:** every lamp, floodlight, shop / hotel / greenhouse fixture, cabin lantern and the campfire is in `SK.LIGHTS`. A fixed pool (6 point lights + 2 shadow-casting spot lights for the nearest street / flood lamps) is handed to the nearest fixtures every 0.4 s, so the shader never recompiles. Indoor lights stay on by day at low power.
- **Glass:** one clear treatment for every window (pane 16% opacity, hotel-style frames, no painted streaks); shop windows were opaque before.
- **Locked doors:** cabins for sale have real hinged doors, solid until you own the cabin ("Locked. The Log Cabin is for sale…"). Hotel rooms are locked except the one on your key.
- **Hotel:** north wing + second floor. Ground: lobby, reception, bar, new breakfast hall (buffet, 5 tables, door to the plaza). Floor 1: rooms 101–109. Floor 2: suites 201, 202, 208, rooms 205–207. Stairs to floor 1, elevator to all floors (behind reception). Check-in at the desk: normal 50 / suite 100, about 45% of rooms are taken each night (seeded), a random free room is assigned, key card shown; check-out 11:00 next day. Free breakfast 6–11 am, once a day (+45 Energy, +25 Warmth).
- **Skiing pays more:** coins = score / 14 × trail multiplier (green 1, blue 2, black 3.5, backcountry 5) × clean run 1.15 × from the top 1.1 × repeat taper (each repeat of the same trail that day −10%, floor 50%). The plan's worked Pine Ribbon run pays 189 (was 63).
- **Hot tub:** cedar deck, open basin with tiled walls, seat ring, clear water, rim, steps, cover, towel; only a faint wisp of steam. Chimney smoke is thinner.
- **Greenhouse:** sits on a flattened plot with a gravel border and stepping stones.

## Phase 6 status (done)
- **Weather:** one seeded forecast per day (`forecast(day)`: clear 35% / cloudy 25% / snowing 25% / snowstorm 15%) peaking at a seeded hour and easing ±5 h around it. Drives the existing snowfall (now an amount 0–1), wind on flakes and powder, overcast sky (no stars, no sun glow), sun strength, fog (storm: ~130 m view), and cold (storm up to x1.7 Warmth drain). HUD shows ☀ ☁ ❄ ❄❄; the Settings sheet shows today's forecast and peak hour; a toast warns when a storm hits.
- **Storm goggles** (450, Summit Outfitters glass case): ~3.5x visibility in a storm.
- **Sound** (WebAudio, no files; starts on the first tap): wind (louder in storms and at speed, quiet indoors), ski swish by speed, soft crisp footsteps (muted taps indoors), muffled landing / crash pats, lift carriers whooshing and clacking past (panned left/right), a low motor hum near stations, crowd murmur and the odd voice near people, coin / quest / locked-door / check-in sounds.
- **Haptics:** landings, crashes, coins, storm warning; every vibrate goes through the Vibration setting.
- **Settings (⚙):** sound, vibration, graphics High / Balanced / Low (lights 6/4/2, lamp shadows only on High, sun shadow map 2048/1024/512, snowfall cap 100/60/35%, resolution 2/1.5/1, light dust off on Low). Applied the moment you tap. Phones default to Balanced.
- Not measured: real-phone frame rate (headless rendering only).

## Finishing pass (third playtest)
- **Summit Express top:** level plateau from the station exit to the Pine Ribbon start gate (at the station's height); station pads blend over 14–16 m, so no snow bank at any lift top.
- **Finish arches** of trails that end at a lift base sit 16 m up the trail (the run finishes there too), so nothing blocks lift entrances.
- **Gate pass:** at the bottom of every lift you can only board after your ticket opened the gate (the pass lasts 90 s and one ride). The loading area says "Go through the ticket gate to board".
- **Crowd:** 4 idle walkers left (was 13). 12 Summit Express skiers ski the run, line up at the gate, it opens for them, they shuffle through to the queue and board (gondola boarding and unloading now uses the ski shuffle animation). Trail skiers carve down every other trail; riders sit on chairs / stand in cabins on Lodge Lift, West Ridge, Crest and Eagle (counts follow the graphics setting).
- **Alpine Cabin** (was the mountain hotel): moved to the ridge between Timberline and Crest Couloir (−52, −203), a private tier-5 cabin for 60,000. Locked doors until bought. Perks: season pass (free lift tickets), private shuttle from the cabin sheet (village or the top of any lift you have opened), stash 150, morning goods (6 logs, 5 berries, 6 cones, 4 cacao, 3 ice), breakfast x1.2 for 20 min, waxed skis for 30 min. The Lodge Café keeps its old spot in a new log chalet.
- **Avalanche beacon** (3,000, Summit Outfitters). West Bowl: ski patrol turns you back unless you have 20 medals and the beacon.
- **Resort Championship** (Quest Board, 28 medals): Crest Couloir, Eagle Chute and Mogul Ridge each under par time (length / 9 m/s), 3,000 coins.
- Not built: rail / box grind scoring (needs new physics).

## Sound + editor pass (fourth playtest)
- **Sound:** snow hiss under the skis is softer and lower (max 0.1, was 0.28); wind bed about half (0.014 + 0.08 × storm); crowd murmur, voices, station motors and passing carriers quieter. Riding a lift now has machinery: a low drive-motor hum (louder near the stations), cable whir, a clatter of sheave clacks each time the grip crosses a tower (every 4.5–7.5 s), the odd cabin creak; wind is muffled inside a gondola.
- **Lift bottom stations** (all lifts except the Summit Express) are cut into the slope at the lowest point of their pad, so no snow embankment is heaped under them.
- **Editor (✎ under the settings gear):** free orbit camera (drag orbit, pinch zoom, 2 fingers / right-drag pan, WASD/QE on desktop).
  - Select by tap; **Multi** to add/remove; **Box select** drags a rectangle; **Same type** selects every copy of the asset nearby; **Parent ▲** selects the containing group (e.g. a whole lift line's stations/towers).
  - **Move** tool: drag a selected object (ground snap keeps its height above the snow); arrows nudge 0.5 m; ▲/▼ height; ⟲/⟳ 15°; −/+ Size (Size: ALL/X/Y/Z for resize along one axis); Reset reverts to the original.
  - Copy / Paste (at the camera target) / Duplicate / Delete / Undo (80 steps). Group / Ungroup are saved (a tap on one member selects the group).
  - **☰ Objects:** searchable list of every asset, place label and NPC, nearest first; tap to fly there. **＋ Add:** searchable list of every gallery asset (plus text labels), placed at the camera target with its collision box.
  - **Labels:** select one to rename it. **NPCs:** tap one to see its route (walker area, Summit Express loop, current trail, lift line); *Set new path* → tap the ground to drop waypoints (walking or skiing) → Done; *Original route* undoes it; Delete hides the NPC.
  - Collision boxes, building floors and street lights an asset owns move / scale / vanish with it (a box belongs to the smallest asset covering it). Not moved: interaction spots (shop doors, lift boarding, quest points), snow-free zones, terrain pads. Lift carriers and the terrain are not selectable.
  - **Save** regenerates this page with a `pp-edits` JSON block and publishes it as the new artifact version (needs edit access; the page reloads with the edits). Viewers without edit access get a download prompt for the edited `powder-pass.html`; the local file downloads it directly. Shortcuts: Del, Ctrl+C/V/D/Z/S, Ctrl+G / Ctrl+Shift+G, [ ] rotate, , . size, PgUp/PgDn height, M move, B box, T select, Esc.
  - **Parts:** *Box select* always picks individual pieces (every sub-asset of a composite, even ones that belong to a group); the **Parts** toggle makes plain taps do the same. Piece keys are `unitKey/childIndex/...` (e.g. `r.34.0/0`). A piece moves on its own; the asset's collision boxes stay put.
  - **Gates ↔ trails:** every start and finish gate knows its trail. Move one and the trail's end follows (same offset): trail skiers, the run start/finish line, the trail length / par / gold target and the Summit Express skiers' route (Pine Ribbon start) all change. A gate you added (Add → Start gate, or a copy) is free until you select it and tap **Link to nearest trail end** (prefers ends whose own gate was deleted); **Unlink** undoes it. Saved with the gate (`lk`). Not changed: the groomed snow corridor and the piste poles baked into the terrain (reshape with the terrain tool).
  - **Terrain tool:** Raise / Lower / Smooth / Level (flatten to where you first touch) / Restore (bring the original ground back); brush 2–30 m, power 0.3–3×; **Paint/Look** toggle (Look = drags move the camera, for touch). Edits the same 0.75 m height grid the ground, skiing, NPCs and trees use (tree roots follow; buildings don't: select them → **Ground ⤓**). Undo per stroke. Saved as per-row cm deltas (`terrain` in `pp-edits`, ~1 byte/cell touched). Stays 4 cells away from the map edge. Snow prints/berms are a separate layer and are not reshaped.
  - Asset keys are index paths under the resort group (`r.85`), so an edit stays attached as long as the world generator places assets in the same order. Changing world code that inserts assets earlier in the build can shift keys: re-check saved edits after such changes.

## Autonomous playtest pass
Found by the bots / screenshots above and fixed:
- **Trails went uphill:** Mogul Ridge (+15 m) and Fir Glades (+17 m) climbed the outer valley ridge, Meadow had a 4 m hump, Skyline / Timberline small rises. Now: every trail is graded at terrain bake (`trailGrades()`: a non-increasing profile halfway between cutting bumps and filling dips, blended across the corridor, flats left flat), and Mogul Ridge / Fir Glades are re-routed along the flank, starting just below their lift-top stations (they used to start 20 m uphill of them, on the station pad). Fir Glades keeps a clear 1.8 m line through its trees. Asset keys checked: only the moved start gates changed.
- **Medals were out of reach:** no clean run earned even bronze (e.g. Eagle 433 vs gold 2,500). Gold is now 1.5× / 3× / 4.2× / 6× trail length for green / blue / black / backcountry (min 50): a clean steady run ≈ bronze, tricks and speed bring silver and gold. "From the top" counts if you start in the first 25 m (was 15 %, too tight on short trails starting on a flat pad).
- **Lodge Line** started on the Lodge Café deck: start moved 7 m down.
- **Orbit / editor camera** could not look at anything above 70 m (the crest is ~110 m): limit raised to 140 m.
- **Phone HUD:** toasts sit below the quest card and left of the button column; the run score moved below the HUD rows; the day chip is compact on narrow screens ("D1 22:00").
- **First minute:** you spawn with an open view of the Magic Carpet and lifts (was facing a railing); the welcome toast points to Coach Bo like the quest card (it said "buy a ticket").
- **Ticket gate:** while walking through after the scan it said "Gate locked - you need a ticket": now "Gate open - walk through".
- **Gondola ride:** you start looking out of the side window (was staring at the grey door panel).

New:
- **Trail map (M / Map button):** hill-shaded resort, trails by difficulty with your medals, lifts (locked ones dashed with the medals needed), place names (your editor renames included), you and the quest target. Drag, pinch / wheel zoom, ◎ re-centres, tap a trail for length, medal targets, your best score and time. The 3D view pauses while it is open.
- **Run times:** the result card shows your time and best time (best times count from the top only); the map shows them too.
- **Rita's daily race:** Rita waits beside the Pine Ribbon start gate. Talk to her on foot, skis on during the 5 s countdown, beat her to the finish for 150 coins (once a day; rematches for fun). Her pace is 21 s until you have a best time, then your best + 1 s.
- **Slalom course on Pine Ribbon:** 11 giant-slalom gates alternate ±2.6 m down the run (built at runtime, so editor keys are unaffected; rebuilt if the editor moves the ribbon's gates). Between the poles = +25 (panel turns green), missed = grey; all of them = +100 "Clean slalom". Shows as "Slalom gates" on the result card. The old decorative slalom sets are hidden. Bot: 10/11 gates at 50 km/h → silver.
- **Small hops never throw you:** landing after less than 0.5 s of air (which scores nothing) forgives ski angle and points the skis where you are going. Before, steering through an accidental kicker hop on Timberline spun you into a crash.
- **Discoverability:** a once-a-day toast when you get near Rita; the map shows 🏁 Race at her spot and, for Pine Ribbon, the slalom course and race.
- `tools/soak.js [minutes] [seed]`: random play for N minutes of game time (ski, walk, lifts, shops, sheets, map, editor, time and weather jumps), fails on any page error or a broken save.
- `SK.play(n, dt)`: headless simulation step (everything a frame does except drawing) for bots and tests.

## Characters, footprints, falls, map (fifth playtest)
- **Boots:** snow boots have a lugged rubber sole, toe cap, heel counter, welt stripe, laces with eyelets, a fleece cuff and a pull tab; ski boots have toe/heel lugs in the bindings, a two-tone shell, instep buckle, side stripes and a power strap. Clothing: glove gauntlets + knuckle seam, sleeve cuffs, centre zip pull, shoulder yoke, chest pocket, thigh cargo pocket, knee patches.
- **Footprints follow the feet:** the walk / sprint cycle fires `footDown` the moment a foot lands; NPC prints are stamped at that foot (so they line up with walking ~0.5 m or sprinting ~1 m strides). Your avatar's legs play faster or slower to match your ground speed (`userData.rate`) and in third person its feet make the prints. In first person one stride (`W.bp`, ~0.65 m slow, 1.3 m walking, 2 m sprinting) drives the boot print, the head bob and the step sound together. Distance-based prints remain only as a fallback.
- **Falls are quicker to the ground:** Trip and fall / Catch an edge stumble 0.35 s and are down by 0.65 s (3.4 s total, was 5.2); Falling sideways is down by 0.5 s (2.6 s total, was 4.4); the crash tumble starts rolling at once (eased out, not in). Your own crash: 2.8 s tumble / 2.6 s fall (was 3.2 / 3.9).
- **Map revamp:** a framed map sheet with elevation tint, north-west relief shading, 10 m contours (50 m bolder), hatched rock, pine glyphs with shadows, roofs with shadows; trails with a dark edge, groomed centre line or ungroomed dots, and name pills with the difficulty symbol (● ■ ◆ ◆◆) and your medals; lifts with towers, stations and name pills (🔒 + medals when locked); summit marker, pulsing quest star and "you" ring, compass and scale bar; legend with real symbols.

## Park, jumping, breakfast, elevator (sixth playtest)
- **Jump on foot:** Space (touch: the yellow JUMP button now shows while walking). A ~0.7 m hop; no footprints in the air and a dent where you land. Works on ramps and indoors.
- **Ramps are real surfaces:** the kicker, wood ramp, quarter pipe and fun box add their exact shape to the ground (`SK.PARK`, wrapped into `SK.groundY`), so skis launch off the lip and you can walk up them. Their old box hitboxes are gone. Shapes follow the objects' live transforms, so editor moves/rotations/scales work.
- **Grinding:** land on (or ride onto) the grind rail, kink rail or fun box within ~45° of its line at over 2 m/s. Skis go sideways and you slide with light friction (the kink pulls you down); Space pops off. "Rail / Kink / Box slide 0.9 s +105" scores into the run combo, or pays park coins outside a run. `grind` event.
- **Eating and drinking:** using any food or drink plays bites or sips in your hands in first person (the food shrinks each bite), or a hand-to-mouth "Snacking" pose in third person.
- **Breakfast for real:** 6 to 11 am, take a plate (far right of the buffet), tap stations to add eggs, bacon, pancakes, croissant, fruit, juice or coffee (up to 5, shown on the plate), sit at a free chair, and eat item by item. Each item gives its own Energy/Warmth (5 items is about +45 / +20). Tap to stand up when done. Once a day.
- **Livelier hall:** an archway from the lobby with a BREAKFAST sign, tablecloths, runners, flowers and pendant lamps, station signs, a menu board, plants, art. Five guests sit eating and a chef works the buffet (runtime NPCs, so editor keys are unchanged).
- **Working elevator:** a cab (mirror, handrail, light, button panel) rides the shaft at up to 1.5 m/s. Sliding doors on every floor are solid when closed. Call it, step in, choose a floor, the doors close, it rides, dings and opens. Doors re-open if you stand in the doorway. `SK.ELEV`. The old fade teleport stays as `SK.P6.ride` for tests.
- Tests: `tools/p13-test.js` (jump, ramp profile, standing on the kicker, rail grind, sip animation, elevator call/ride/exit) and `tools/p13-shots.js` (screenshots). `p6-test` now runs the plate breakfast.

## Lights, doors, living hall, real falls, winter nights (seventh round)
- **Lighting redone:** every lamp is a spot cone that matches its visible beam (ceiling and street lamps straight down, floodlights forward-down), so light lands in a cone on the floor/snow and never on the ceiling above. Local lights no longer reach faces turned away from them (toon shader patch), which stopped light leaking through walls; the 2 nearest cones cast shadows (High and Balanced). Pool: 8 cones (Balanced 6, Low 4) + 1 campfire glow. Outdoor lamps are gentler on white snow.
- **Real cabin doors:** the A-frames and the Alpine Cabin use their model's own door leaf on a hinge (the A-frame no longer shows an ajar model door beside a fake one; the Alpine entrance is now closed and locked until you own it, then swings open for you).
- **Breakfast hall:** diners walk in from the lobby archway from 6 am (busiest 7:20 to 9:40), take a free seat via the gaps between tables, sit facing the table, eat with a plate in front of them, then walk out; the hall is empty after 11. The chef stands at the west end of the buffet (was inside the north wall) and only works breakfast hours. Your own plate now sits on your half of the table.
- **Fallen skiers are real:** no more pre-placed people lying on the slopes. Trail skiers sometimes crash mid-run (tumble, then lie on their back) and become the patrol targets; at most 2 down at once. Help them up and they wave and ski on; left alone, they get up by themselves after about 4 minutes.
- **Winter nights:** light fades slowly from 16:30 and it is fully dark by 18:00 (dawn 6:00 to 7:30), with a purple dusk. **Northern lights** ripple over the northern sky after dark (stronger some nights, hidden by cloud).
- **Warmth drains faster:** about 10 minutes from full to empty outdoors (was ~18), 35 % quicker at night.
- **Animations:** the walking jump uses an in-air Hop pose that follows the real jump (no second lift, so no "double jump") plus a short landing crouch. Eating and drinking now layer on top of what you are doing (only the right arm moves), and you can jump while eating.
- **Camera lock:** press Ctrl (on its own) to lock the camera: the mouse steers it without dragging (pointer lock where allowed), a crosshair marks the middle of the screen and you face where you look in third person. Ctrl again or Esc unlocks; opening a sheet or the map unlocks too.
- **HUD:** sheets dock to the left (bottom sheet on phones) with a coloured header per kind (bag, quests, shop, settings, cabin, place, elevator); toasts sit on the left and are green for good news, red for problems, blue for info; the side buttons each have their own colour; the controls hint moves to the bottom-left on desktop; run results dock to the right.
- Tests: `tools/r7-test.js` covers all of the above; `tools/shot.js` takes ad-hoc screenshots (`node tools/shot.js "[[name, x, y|null, z, yaw, pitch, minutes, setupJs]]"`). `p6`, `p7` and `quest-test` were updated for the cone pool and real fallen skiers.

## South Range, real terrain, physical elevator, HUD rebuild (eighth round)
- **South Range:** the terrain grid now runs to z 320, so the village sits at the bottom of a V. The new expert range rises from the valley floor (z 104) to a crest at about +115 m (South Peak). Land first, runs second:
  - Hotel Face (open double black), Knife Ridge (ridgeline on a real bench), The Ledges (three 1.6–3 m rock drops with a bypass), Deep Timber (dense forest, narrow tree gaps), South Bowl (treeless high bowl) into Bowl Gully (moguls), Southside Park (three terraces cut into the slope: two terrain step-down kickers, a middle kicker, rail, box, kink rail, kicker ramp) and a Mid Traverse linking every run.
  - Lifts: South Chair, Bowl Chair, Park Chair (chairs, no gondola). Built at the end of world generation so the editor's asset keys for the rest of the resort don't move.
- **Run bench (every trail):** each run is levelled across its width to its graded profile, and beyond the edges the land is cut / filled with smooth creases so it never rises or falls faster than ~30°. Steepness down the fall line is unchanged. Mogul Ridge and Bowl Gully get real moguls. Runs that brushed a lift-station cut (Mogul Ridge, Eagle Chute, Fir Glades, Deep Timber, Hotel Face) were rerouted. `tools/audit-trails.js` walks centre → edges → shoulders of every run.
- **Distant landscape:** the vertical skirt round the grid is replaced by one continuous outer mesh: it starts under the grid edge, falls into a valley, then rises into a far range 300–700 m out. The five backdrop peaks moved from the summit crest (where they hung over the Summit Hut with open bases) onto that far range with buried bases.
- **Props clear of buildings:** a footprint registry of every structure; hand-placed bushes, snow piles, boulders and dead trees move to the nearest clear spot, and the forest keeps 3.2 m clear (the Village Mart bush is gone). `tools/audit-props.js`. The vending machine now stands on the Eagle top-station pad. `tools/audit-found.js` checks foundations.
- **Hot tub:** rebuilt as one clean module on a square cedar deck clear of the house (planks no longer stick out of the round deck, the cover lies flat, steel hoops instead of stray rods).
- **Lighting:** lamps are soft warm pools (full penumbra, decay 2, warm tints, low intensity); the fake glow disc on the snow under street lamps is gone.
- **Elevator:** outer doors fill the real opening on every floor (they were 0.25 m short upstairs) with a header track and sill; doors open / close in 0.42 s with easing and a short arrival pause. Physical controls: a ▲ call button by each door (lights while the cab is coming) and labelled G / 1 / 2 buttons in the cab you look at and press. No floor sheet.
- **Lobby:** the slat wall that stood loose across the breakfast archway is now two framed slat panels either side of an open arch; the TV sits on the right panel.
- **HUD:** the side buttons are one square icon grid top-right (SVG icons; the ⚙ / ✎ emoji rendered as blank squares on some devices); the controls hint and toasts live in a content-sized dock (bottom-left on desktop, top-left on touch) – the hint used to stretch top-to-bottom because two rules set both `top` and `bottom`. The editor only exists with `#dev`; the asset gallery moved to `#gallery`.
- Tools: `tools/view.js` (orbit-camera QA shots), `tools/shot.js` (player-level shots).

## Not yet
- Terrain distance LOD; real-phone frame-rate check (only tested in headless software rendering). Third person is still forced off indoors, so seated eating is first person. Breakfast diners and fallen skiers ignore collisions with the player.
