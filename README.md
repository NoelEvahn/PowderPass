# ⛷ PowderPass

**A physics-driven alpine resort for Paper 1.21.11. No mods, no resource pack.**

PowderPass turns snowy mountains into a working ski resort:

- **Real skiing and snowboarding physics.** Carve, skid, tuck, jump, spin, flip and grab down your own terrain.
- **An animated skier built from Block Displays.** It leans into turns, folds into a tuck, plants its poles and wears your skin.
- **Chairlifts made entirely of display entities.** Towers, sagging haul rope, rotating bullwheels, and swinging chairs with safety bars that drop and lift. You ride them for real.
- **Slalom racing** with sub-tick timing, split deltas, leaderboards on a floating board, and a **ghost of the course record** racing beside you.
- **PowderPass lift tickets**: a non-transferable item sold at a display-built ticket kiosk.
- **20 badges**, unlockable suits, a resort menu and lifetime stats.

---

## How it plays

### Skiing
Craft skis or a snowboard, stand on snow, and **right-click**. You click in, and to everyone else your
sitting player model is replaced by an animated skier wearing your face and your chosen suit.

| Input | On the ground | In the air |
|---|---|---|
| Mouse | Steer: your skis follow your camera | Look around |
| A / D | Carve harder | Spin |
| W | Pole / skate on flats | Front flip |
| S | Brake and skid (look sideways for a hockey stop) | Back flip |
| Shift | Tuck: less drag, wider turns | Grab |
| Space | Hold to load, release to pop | |
| F | Step out of your gear | |

Players who prefer boat-style controls can switch to **KEYS** steering (A/D turn the skis, camera free)
in `/pp` → Locker.

**How the physics works:**
- Blocky terrain is blended into a smooth heightfield, so staircases of blocks and snow layers ski like real slopes.
- Genuine cliffs stay sharp, so you launch off them.
- Gravity acts along the slope. Edge grip scrubs off sideways speed: a clean carve converts it back into forward speed, a skid throws it away as spray.
- Drag and friction set a natural top speed of around 110–140 km/h on steep pistes.
- **Surfaces matter:**
  - Groomed snow is fast and grippy.
  - Powder is draggy and throws spectacular plumes.
  - Ice is very fast with almost no edge hold.
  - Anything else grinds you to a halt, and if you crawl over it too long you step out.

**Landing rules:**
- Land with your skis lined up with your direction of travel (switch landings count).
- Finish your rotation.
- Don't land too hard, or you **wipe out**: your skis cartwheel away ("yard sale"), you tumble, and any combo is lost.
- Tricks chain into combos with a growing multiplier, e.g. *Switch Backflip 720 Mute Grab +1,840 x1.5*.
- Keys you're already holding at takeoff are ignored until you release them, so carving or poling never turns into an accidental spin.

### Chairlifts
- Stand on the **LOAD HERE** line at the base station. A chair scoops you up and the safety bar comes down.
- The chair swings as it rounds the bullwheel and clunks over each tower. The action bar counts down to the summit.
- You're dropped off at the top platform. If you're still clicked into skis when you board, they go on your shoulder.
- Optional: riders can board at the summit and ride down.
- Optional: lifts can require a valid PowderPass.

### Racing
- Cross a course's **START** gate on skis and the clock starts.
- Pass every red/blue gate in order. A boss bar shows time, gates and your split versus the record.
- Missed gates cost a penalty (or disqualify you, if configured).
- Cross the **FINISH** arch to set your time.
- If the course has a record, a glowing **ghost** of that run (visible only to you) races alongside.
- Beat it and your run becomes the new ghost.

---

## Install and build

- **Requires:** Paper **1.21.11** and Java 21.
- **Install:** drop `PowderPass-1.0.0.jar` into `plugins/` and start the server.

```bash
./gradlew build          # jar in build/libs/, runs the 50 unit tests
```

The build pulls `paper-api` from `repo.papermc.io`. It also checks `mavenLocal()` first, so a locally built
paper-api works offline.

## Commands (`/powderpass`, aliases `/pp`, `/ski`)

| Command | What it does | Permission |
|---|---|---|
| `/pp` | Resort menu: stats, badges, locker, courses, lifts, pass, help | `powderpass.menu` |
| `/pp stats [player]` | Lifetime stats | — / `powderpass.stats.others` |
| `/pp unclip` | Step out of your gear | — |
| `/pp give <player> <skis\|snowboard> [timber\|alpine\|glacier\|avalanche]` | Give gear | `powderpass.admin.give` |
| `/pp give <player> pass [day\|season]` | Give a lift pass | `powderpass.admin.give` |
| `/pp lift create <name>` | Survey a lift: stand on the **base** platform | `powderpass.admin.lift` |
| `/pp lift top` | Stand on the **summit** platform: builds the lift | `powderpass.admin.lift` |
| `/pp lift list \| info \| rebuild \| remove \| cancel` | Manage lifts | `powderpass.admin.lift` |
| `/pp lift set <name> <speed\|spacing\|seats\|style\|pass> <value>` | Tune a lift (styles: CLASSIC, ALPINE, MODERN, GLACIER) | `powderpass.admin.lift` |
| `/pp course create <name>` | Start gate where you stand, facing downhill | `powderpass.admin.course` |
| `/pp course gate <name>` | Add a slalom gate (alternates red/blue) | `powderpass.admin.course` |
| `/pp course finish <name>` | Set the finish arch | `powderpass.admin.course` |
| `/pp course undo \| reset \| remove \| list` | Manage courses | `powderpass.admin.course` |
| `/pp kiosk create \| remove` | Place or remove a ticket kiosk | `powderpass.admin.kiosk` |
| `/pp reload` | Reload config and messages, rebuild structures | `powderpass.admin.reload` |

### Player permissions
These default to everyone: `powderpass.ski`, `powderpass.menu`, `powderpass.lift.ride`,
`powderpass.race`, `powderpass.pass.buy`.

### Operator permissions
- `powderpass.admin` includes every admin permission above.
- `powderpass.bypass.pass` lets a player ride lifts without a pass.

## Gear recipes

| Tier | Skis (`M_M / MCM / M_M`) and snowboard (`_M_ / MCM / _M_`) |
|---|---|
| Timber | M = any planks, C = string |
| Alpine | M = iron ingot, C = Timber gear of the same type |
| Glacier | M = diamond, C = Alpine gear |
| Avalanche | Smithing table: netherite upgrade template + Glacier gear + netherite ingot |

Each tier is faster and grippier than the last.

## A 10-minute tour (operator)

1. Find or build a snowy slope. Snow blocks or snow layers work best; ice runs are fast.
2. `/pp give @s skis glacier`, stand on snow, right-click. Ski down: hold Shift on the steeps, press Space to jump off a ledge, tap D in the air to spin.
3. **Build a lift:**
   - At the bottom of the slope, stand on flat ground and run `/pp lift create Express`.
   - Walk or fly to the top (the whole line must be within view distance) and run `/pp lift top`.
   - Towers, rope, stations and chairs appear.
   - Press **F** to step out of your skis, or keep them on (they're shouldered automatically). Then stand on the **LOAD HERE** marker.
4. **Build a course:**
   - At the top, face downhill and run `/pp course create Glacier`.
   - Ski down, stopping every 10–15 blocks to run `/pp course gate Glacier`, offsetting left and right.
   - At the bottom, run `/pp course finish Glacier`.
   - Ride the lift up, race through the START gate, then race again against your own ghost.
5. **Sell tickets:**
   - Run `/pp kiosk create` near the base station.
   - Run `/pp lift set Express pass true`.
   - Players right-click the kiosk to buy a Day Pass.

## Configuration
Everything lives in `config.yml`, with comments, and is validated and clamped on load:
- Physics constants for skis and the snowboard, surface friction and grip, and tier multipliers.
- Visuals and smoothing, and the lift defaults.
- Course rules, and pass prices and durations.

All messages are MiniMessage strings in `messages.yml`.

## Engineering notes
- **Pure, unit-tested core.** The physics, terrain smoothing, trick judging, combos, gate geometry, race timing, ghost codec, lift layout (towers, sag, clearance, zones, speed profile) and the skier's skeletal pose model have no server dependencies. They are covered by **50 JUnit tests**: terminal speeds, carving vs skidding, cliff launches, landings, walls, gate crossings with sub-tick precision, and loop continuity.
- **Smooth motion without NMS.**
  - Riders sit on an invisible display "anchor" that is teleported each tick with client-side teleport interpolation.
  - Every body part rides the same anchor, so the whole skier moves as one rigid, interpolated unit.
  - Poses are pushed as transformation-matrix updates, only when they change.
  - Rider input comes from Paper's `Player#getCurrentInput()`.
- **No leaks or orphans.**
  - Every spawned entity is non-persistent and tagged.
  - Structures spawn lazily only while players are nearby and their chunks are loaded, and are swept on enable, disable and chunk load.
  - Riders are dismounted before the server saves them, so vehicles never get serialised.
  - Lifts rescue their riders on reload, removal or shutdown.
  - Invisibility is a short self-refreshing effect, so a crash never leaves anyone invisible.
- **Exploit-safe.**
  - GUIs cancel every click and drag.
  - Passes are bound to their buyer's UUID and expire.
  - Purchases are all-or-nothing.
  - Gear and passes can't be used as ingredients in vanilla recipes.
  - Upgrades verify the input by its PDC tag.
  - Unloaded chunks read as walls and are never loaded by the physics.
- **Async IO.** Profiles are preloaded during async pre-login. YAML is serialised on the main thread and written atomically on a dedicated IO thread.

## Needs in-game verification
Automated tests cover the logic, but these depend on the live client:
- **Camera height while skiing.** `visuals.rider-offset` defaults to 0.6, the vanilla player vehicle attachment.
- **Skin-head orientation on the animated skier.** If heads face backwards on some client, set `visuals.head-yaw-offset: 180`.
- **Overall feel.** Physics constants are tunable live with `/pp reload`.
