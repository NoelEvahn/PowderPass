# Backrooms — a seven-level dimension for Paper 1.21.11

Backrooms adds a separate, server-side-only alternate reality to a Paper 1.21.11 server. It is built from seven generated worlds, display-entity props and creatures, and sounds and light effects. Players need no mods or resource packs. The worlds never touch the Overworld, the Nether or the End.

| | |
|---|---|
| Server | Paper **1.21.11**, Java 21 |
| Plugin JAR | `build/libs/Backrooms-1.0.0.jar` |
| Dependencies | none at runtime; no NMS, no packets, no resource pack |
| Worlds | `backrooms_level0`, `backrooms_poolrooms`, `backrooms_level1` … `backrooms_level5` |

---

## Installation

1. Put `Backrooms-1.0.0.jar` in `plugins/` and start the server once. The seven worlds are created on first start; each takes a few hundred milliseconds.
2. Edit `plugins/Backrooms/config.yml` if you want to, then restart the server or run `/backrooms reload`.
3. Enter with `/backrooms enter` (this needs `backrooms.use`, which everyone has by default) or `/backrooms enter <player>`.

> Use `/backrooms reload` rather than `/reload` or plugin managers. The worlds keep a reference to the generator, so hot-swapping the JAR is not supported.

**Building from source:** run `./gradlew build` (or `gradle build`). The API comes from `repo.papermc.io`. If that host is unreachable, pass a local Maven mirror with `-PlocalPaperRepo=/path/to/repo`. The JAR in this branch was built that way, against a Paper API 1.21.11 compiled from PaperMC's `ver/1.21.11` source.

---

## How players experience it

- **Getting in:** `/backrooms enter` (it can be turned off, and limited to certain worlds). Admins can also send players in. Each entry lands in a different part of Level 0, so players don't all share one spot. The place they left from is saved as their **return point**.
- **Moving between levels:** every level contains physical structures that lead elsewhere. Each district (about 180–220 blocks square) is guaranteed to hold one of each of its level's required transitions. Nearby transitions also give a recognisable cue: a low beacon hum, or wind for exits.
- **Getting out:** players have to *find* an exit. `/backrooms leave` is admin-only by default. The exits are:
  - the EXIT door in Level 0
  - the sunlit arch in the Poolrooms
  - the FIRE EXIT in Level 4
  - the revolving door in the Level 5 lobby

  Dying also returns the player to their return point (`death.mode: RETURN`).
- **Surviving:**
  - Generated barrels hold limited supplies the first time they are opened: Almond Water, rations, batteries, flashlights, and damp notes that hint at the transitions.
  - **Sanity** drains in the dark and near creatures. It recovers in light, in safe rooms and near other players.
  - Low sanity brings whispers, a heartbeat, nausea and private hallucinations.
  - The **Flashlight** casts a moving light that only clients see.
  - Answering a ringing phone gets a whispered direction and distance to the nearest way onward. Radios carry warnings about the creatures.

### Transition map

| From | Structure (trigger) | To |
|---|---|---|
| Level 0 | Concrete stairwell pit (walk down) | Level 1 |
| Level 0 | Cold tiled pillar (crouch against it, or stand there for 3 s) | Poolrooms |
| Level 0 | Free-standing door with green EXIT sign (walk through) | **Exit** (about 35% of districts) |
| Poolrooms | Black drain at the bottom of a 7-deep pool | Level 0 |
| Poolrooms | Grey STAFF ONLY booth | Level 1 |
| Poolrooms | Sunlit arch | **Exit** (about 30% of districts) |
| Level 1 | MAINTENANCE stairwell | Level 2 |
| Level 1 | Yellow wallpaper doorway | Level 0 |
| Level 1 | Freight elevator (stand inside for 2 s) | Level 4 (about 70% of districts) |
| Level 2 | Hatch with ladder shaft | Level 3 |
| Level 2 | Stairs up toward daylight | Level 1 |
| Level 3 (upper floor) | Office lift (stand inside for 2 s) | Level 4 |
| Level 3 (lower floor) | Copper vent booth | Level 2 |
| Level 4 | Elevator lobby (stand inside for 2 s) | Level 5 |
| Level 4 | Basement stairs | Level 3 |
| Level 4 | Copper FIRE EXIT door | **Exit** (about 40% of districts) |
| Level 5 | Grand lobby revolving door | **Exit** (about 60% of districts) |
| Level 5 | Old brass elevator (stand inside for 2 s) | Level 4 |
| Level 5 | Wardrobe in Room 000 | Level 0 |

---

## The seven levels

**Level 0 — The Lobby.** Mono-yellow wallpaper, stained mustard carpet and buzzing froglight panels.
- Large noise patches drift between four wallpaper styles.
- There are failing-light dark sectors and low-ceiling crawl sectors.
- Pillar halls with ten-block ceilings break up the rooms.
- Some walls are abnormally thick; stub walls make claustrophobic rooms; murky puddles mark the damp areas.
- Whole regions sometimes repeat their neighbour exactly.
- Landmarks: the red room, a desk with a ringing phone, and a circle of chairs.

**The Poolrooms.** White and grid tile, turquoise water (warm-ocean water colour), and walkways flush with the pools.
- Shallow wading rooms are joined by channels.
- Deep pools always have a submerged stairway and underwater lights.
- Towering tiled chambers rise 15 blocks.
- Animated caustic reflections drift across the ceiling.
- Also: a working water slide, an impossible staircase, and an "infinite" shaft.

**Level 1 — Habitable Zone.** Large concrete halls in three sub-styles, with painted floor lines and sparse fluorescent light.
- Warehouses with rack rows, loading docks and puddles.
- **Supply caches** give better loot.
- **Safe rooms** (warm light, wooden floor, chalked "SAFE") are never entered by creatures, and sanity recovers there.

**Level 2 — Pipe Dreams.** Narrow 2–3 wide service tunnels carved from rock (a different topology from the room levels).
- Basalt and hot copper pipes run in the walls and overhead.
- Iron grates sit over glowing magma; hanging lanterns and red emergency torches light the way.
- There are utility rooms and rare tall junction halls.
- Hot zones cause hunger and ash.
- **Steam-vented dead ends hiss before they scald.**

**Level 3 — Electrical Station.** Two stacked floors, each with its own maze.
- A guaranteed service ladder in every region links the floors.
- Double-height atriums have railed catwalks around a vibrating generator.
- Switch rooms have blinking switchboards; machine rooms are lined with equipment.
- Fenced transformer yards are a **telegraphed shock hazard**.
- Turbines, piston pumps, cable trays and hazard-striped walls fill the halls.

**Level 4 — Abandoned Office.** Open-plan regions of 1.5-block cubicles with checkered carpet tiles.
- Glass meeting rooms, break rooms with counters and a vending machine, private offices, and copier rooms with sliding scan bars.
- **Windows that lie:** sealed pockets behind glass show a sunny skyline, a black void with one distant light, a flooded room, or a miniature office with a figure watching you.
- CRT monitors flicker messages, sometimes your name.
- It is calm here.

**Level 5 — Terror Hotel.** Diagonal damask wallpaper in three colour wings over dark-oak wainscoting, with red runner carpet and gold trim.
- **Maze dead ends become numbered guest rooms behind real doors**, each with a bed, wardrobe, nightstand and lamp.
- Checkered ballrooms have swaying chandeliers and a piano that plays itself.
- Dining halls are laid with tables and candelabras.
- There is a grey service wing, a reception with a bell and a key board, and grandfather clocks with swinging pendulums.
- Faceless dancers vanish when you approach. Soul-valley ambience fills the halls.

---

## Creatures and anomalies

None of these is a renamed vanilla mob. Each is a coordinated group of display entities with smooth interpolated motion and limb animation, moved by a bounded A* search over real blocks. Creatures that can be hit have a real `Interaction` hitbox, and damage follows the attacker's attack-damage attribute and cooldown.

| Type | Levels | Class | Behaviour and counterplay |
|---|---|---|---|
| **Smiler** | 0, 1, 5 | threat | Floating grin and eyes, found only in the dark. Freezes while watched; stare for about 4 s and it dissolves. Creeps forward when you look away and are in darkness. Light at ≥11 keeps it at bay. A giggle warns you when it is close. |
| **Faceling** | 0, Pool, 1, 4, 5 | sighting | Tall and faceless, with long-limbed walk animation. Stalks at 12–20 blocks and peeks around corners. Leaves when stared at. Harmless, but drains sanity. Also appears as **private hallucinations**: only the affected player can see them. |
| **Hound** | 1, 2, 3 | threat (killable, 30 HP) | Quadruped with swinging legs and an opening jaw. **Hunts by sound** (sprinting is loud, sneaking almost silent) and by sight. Growls and lowers its head for about 1 s before a lunge. Flees when badly hurt. |
| **Live Current** | 3 | environmental | Knot of crackling arcs that wanders the halls and makes nearby lights flicker. Shocks anyone within about 2 blocks. Cannot be killed (punching it shocks you). |
| **Concierge** | 5 | stalker | Headless bellhop. Moves only while nobody is watching, in silent jumps. Its touch "checks you in" to a distant guest room. Ring a reception bell to dismiss it. |
| **Drifter** | Poolrooms | threat (20 HP) | Pale undulating shape in deep water. Bubbles warn you. It drags swimmers down; leave the water or strike it. |

Spawning is deliberately rare:
- Each player gets an attempt every 20 s, with chances per level.
- Caps apply per player (2) and per level (8).
- Creatures never spawn in safe rooms or in plain sight (Facelings excepted).
- Each type has its own placement rules (Smilers in the dark, Drifters in deep water).

## Environmental events

`flicker`, `blackout` (raises spawn chances), `lights_out` (lights die one by one toward you), `footsteps`, `knocking`, `machinery`, `steam_burst` (telegraphed, Level 2), `power_surge` (telegraphed; standing water becomes live, Level 3), `pool_echo`, `phone_ring`, `waltz` (a detuned harp waltz), `elevator_ding`, `deja_vu` (a hallucination), `hum_shift`.

All light effects are **client-side only** (`sendBlockChange`). The world is never modified, so a crash can never leave lights broken.

---

## Commands

| Command | Permission | Notes |
|---|---|---|
| `/backrooms help` | `backrooms.use` | Player and admin help |
| `/backrooms enter [player]` | `backrooms.use` (self, if `entry.allow-self-enter`) · `backrooms.admin.teleport` (others) | Safe entry into Level 0; saves the return point |
| `/backrooms leave [player]` | `backrooms.admin.teleport` (or self, if `entry.allow-self-leave`) | Back to the saved return point or the fallback |
| `/backrooms level <0\|poolrooms\|1\|2\|3\|4\|5> [player]` | `backrooms.admin.teleport` | Admin spawn if set, otherwise the safe cell nearest the origin |
| `/backrooms levels` | `backrooms.use` | The seven levels and their status |
| `/backrooms setspawn [level]` | `backrooms.admin.teleport` | Stand in the level; the spot is validated as safe |
| `/backrooms status` | `backrooms.admin.debug` | Worlds, players, loaded chunks, threats per type, display counts, generator info, average tick cost |
| `/backrooms entity list` | `backrooms.admin.entities` | Creature catalogue |
| `/backrooms entity spawn <type> [amount]` | `backrooms.admin.entities` | Uses the natural placement rules, with a fallback in front of you |
| `/backrooms entity clear [radius\|level [level]]` | `backrooms.admin.entities` | Clearing a whole level needs `/backrooms confirm` within 15 s |
| `/backrooms event list` | `backrooms.admin.events` | |
| `/backrooms event trigger <event> [level]` | `backrooms.admin.events` | For yourself, or for every player in the named level |
| `/backrooms reload` | `backrooms.admin.reload` | Restarts services without unloading worlds or duplicating schedulers |
| `/backrooms debug <on\|off>` | `backrooms.admin.debug` | Action-bar overlay (level, cell, zone, nearest transition, light, sanity) plus a console log |
| `/backrooms give <almond_water\|flashlight\|battery\|ration\|note> [player]` | `backrooms.admin` | For testing |
| `/backrooms confirm` | — | Confirms a pending destructive action |

Commands work from the console wherever a target player or level is given. Alias: `/br`.

**Permission defaults:**

| Permission | Default |
|---|---|
| `backrooms.use` | everyone |
| `backrooms.admin` and its children (`.teleport`, `.entities`, `.events`, `.debug`, `.reload`, plus `backrooms.bypass.commands`) | op |
| `backrooms.bypass.protection` | nobody (build inside the Backrooms) |

---

## Configuration reference (`config.yml`)

| Key | Meaning |
|---|---|
| `seed` | Base seed; `0` picks a random one once. **Locked**: stored in `data/worlds.yml` |
| `world-prefix` | World folder prefix |
| `levels.<level>.enabled` | Load this level's world. A disabled level's transitions show "the way is sealed". Players left in a disabled level are sent home when they join |
| `levels.<level>.biome` | Biome key for ambient particles, sounds and water colour |
| `levels.<level>.generation.*` | **Locked** per level when its world is created: hall/chamber/warehouse/open-plan/ballroom chances, dark zones, exit chances, landmark chances, decoration density, loop chance (extra maze connections), and more |
| `entry.allow-self-enter` / `allow-self-leave` / `allowed-worlds` / `spread-radius` / `title` | Entry rules |
| `return.fallback-world` | Used when a return point is missing or its world is gone |
| `death.mode` | `RETURN`, `LEVEL0` or `STAY`. `death.keep-inventory` keeps items and XP for deaths inside |
| `restrictions.*` | Block protection, ender pearls, chorus fruit, portals, beds, music suppression, `blocked-commands` (e.g. `home`, `spawn`, `tpa`, `warp`, `back`) |
| `transitions.*` | Arrival cooldown, dwell/intent times, arrival protection |
| `displays.*` | Activation radius (chunks), `max-entities-per-chunk`, `max-entities-per-world`, chunks per tick, animation interval, view range, hazards on/off |
| `creatures.*` | Tick interval, spawn attempt period, caps, despawn distance, `level-chance`, per-type `enabled`/`damage`/`health`/`weight` per level |
| `events.*` | Events on/off, check period, chance, ambience, flicker radius |
| `sanity.*` | Sanity on/off; optional action-bar readout |
| `items.*` | Flashlight battery length |
| `messages.*` | Every player-facing message, in MiniMessage format |

**Locked values** (the seed and `generation.*`) are snapshotted the first time a level's world is created. Later edits only affect levels whose worlds do not exist yet, so existing worlds never develop seams. The snapshot lives in `plugins/Backrooms/data/worlds.yml`. Writes are atomic and the previous version is kept as `.bak`. **Back this file up with your worlds.**

---

## Architecture notes

- **Dimension:** seven server-created worlds with the NORMAL environment and custom `ChunkGenerator`s.
  - All vanilla stages are disabled: noise, caves, decorations, structures and mob spawning.
  - The worlds use fixed midnight, no weather, no mob spawning, a random tick speed of 0 and no fire spread.
  - Levels are sealed with bedrock top and bottom, so there is no void or sky.
  - Paper cannot add new client-side dimension types; players experience the seven worlds as one reality because the plugin moves them with controlled teleports.
- **Generation:** a pure function of (seed, coordinates), so it is thread-safe and seamless across chunks.
  - **Cells** are organised into **regions**. Each region is a random spanning tree, and neighbouring regions always share 1–3 open edges. As a result, every cell is reachable and no exit can be sealed off.
  - Regions are grouped into **districts**, each of which receives the level's guaranteed transitions.
  - Zones come from smooth noise sampled per cell, so style borders fall on cell borders and never cut through a room.
  - Merged regions form halls, chambers, warehouses, open-plan offices and ballrooms.
  - All water is contained still source water whose surface is flush with the floor, and flowing is additionally cancelled.
- **Display subsystem:**
  - About 100 authored models: BlockDisplay, ItemDisplay, TextDisplay and Interaction.
  - Animation: spin, sway, bob, jitter, slide, orbit, blink and flicker, sent with display interpolation.
  - Scripted behaviours: elevator doors, CRT messages, a ringing phone, vanishing dancers, a gramophone, a piano.
  - Emitters, telegraphed hazards, and interactions (bell, phone, radio, vending machine).
  - Props are derived from the layout, so they are **never saved**. They are spawned near players within per-chunk and per-world budgets, spread across ticks, and removed when out of range, when the chunk unloads, and on disable.
  - Every entity is non-persistent and tagged in its PDC; tagged leftovers are swept when chunks load.
  - Props that need collision get invisible barrier blocks from the generator. Lamps get invisible light blocks.
- **Persistence:** the return point, the "inside" flag (used to detect stranded players) and sanity live in each player's PDC. World metadata and admin spawns live in `data/worlds.yml`. Worlds are never deleted or regenerated.
- **Threading:** Bukkit calls happen on the main thread. Chunk preparation for teleports uses `getChunkAtAsync`. Generator block states are pre-resolved at startup. Spawn writes are snapshotted on the main thread and written asynchronously.
- **No NMS and no packets.** Client-side light effects use the stable `Player#sendBlockChange` API, and private hallucinations use `Entity#setVisibleByDefault` and `Player#showEntity`.

## Testing

`gradle test` runs 58 automated tests. All of them pass on this build.

- **Generation (43 tests across all 7 levels):**
  - Determinism across instances and chunk order, and seed sensitivity.
  - **Flood-fill reachability** of every safe cell and every transition trigger. This paints real chunks into memory and walks them the way a player would (walking, stepping, dropping, swimming and climbing ladders) across three 3×3-region windows per level.
  - Water is contained and still.
  - Bedrock shell.
  - Guaranteed transitions in every district.
  - The safe-spot search lands on standable, trigger-free ground.
- **Plugin integration on MockBukkit (12 tests):**
  - The seven worlds are created, and locked snapshots survive config changes and reload.
  - Permission gating and tab completion.
  - Enter saves the return point, re-entering keeps it, the console can make a player leave, and the flag is cleared.
  - Players arrive in different places.
  - Protection and blocked commands apply only inside.
  - A stairwell transition takes a player to Level 1, with an arrival cooldown.
  - Creature hitbox damage and death, and confirmed level-wide clearing.
  - Decorations spawn near players, are non-persistent, and despawn when the player leaves.
  - Disabling the plugin removes every runtime entity.
- **Models and states (3 tests):**
  - Every decoration id the generators emit builds into a model (100 models).
  - Display density per chunk stays within budget.
  - 121 of the 128 generator block states are validated by a real block-data parser. The other 7 (iron-bar connections, ladder facing, lantern hanging, lightning-rod facing) are standard vanilla properties that the mock cannot parse.

### Not verified in a real game: please check in-game

No Paper server binary could be downloaded in the build environment, so nothing has been run on a live server yet. Checklist:

1. **First start:** the seven worlds are created without errors, and `/backrooms status` shows them. Restart: the same terrain, return points kept, no duplicate props.
2. **Visual pass, one `/backrooms level <x>` per level:**
   - Palettes, lighting levels and the look of the props.
   - That prop orientation (front = +Z, yaw), **text display facing** and the wall offsets line up with the walls.
3. **Animations:** fan, turbine and clock spin direction and smoothness; elevator doors opening near you and closing when you step inside; chandelier and pendulum sway; dancers vanishing.
4. **Each transition:** trigger feel, the noclip effect, the arrival spot, and no immediate bounce back. The Poolrooms slide push. Seam intent (crouch).
5. **Creature behaviour and fairness:**
   - Smiler freezes when looked at and is repelled by light.
   - Hound sound tracking, telegraph timing, lunge and damage.
   - Concierge jumps only while unobserved; the reception bell dismisses it.
   - Drifter grab and release.
   - Live Current shocks.
   - That clicking `Interaction` hitboxes works with real clients.
6. **Client-side effects:** that a `LIGHT` block sent by `sendBlockChange` relights on real clients (flashlight, flicker, blackout, lights out).
7. **Hazards:** steam vents and transformers telegraph before hurting; boiler heat.
8. **Death in each `death.mode`;** joining after a level was disabled (stranded recovery); ender pearl and chorus blocking; blocked commands.
9. **Performance with several players:** watch `/backrooms status` tick costs, then tune `displays.activation-radius-chunks` and the entity caps if needed.
