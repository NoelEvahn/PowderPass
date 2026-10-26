# PowderPass — realistic volleyball for Paper 1.21.11

A complete, multiplayer volleyball game for **Paper 1.21.11**. It works with the vanilla client: no mods and no resource pack are needed. An optional resource pack adds custom held-item models and custom sound effects for the players who have it.

- **Real ball physics.** The plugin simulates gravity, quadratic air drag (including the drag crisis), Magnus lift from topspin and backspin, the float-serve "knuckle" wobble, and bounces with spin and friction. Physics is sub-stepped every tick against real block shapes.
- **A real net.** It has a mesh, a top tape (net-cord rolls), antennas, side bands and posts. A mass-spring simulation animates the net when the ball or a player hits it.
- **Every touch.** You can bump, dig, dive, set, quick set, spike, tip, use a standing "down ball" and block. Serves include the float, standing topspin, jump topspin, jump float and underhand serve.
- **Skill-based input.** Contact quality comes from timing and position, and it drives power and accuracy. You aim with your view, and an aim marker shows the target. Lag compensation and input buffering keep the game fair at real-world ping.
- **Positions.** Players pick a role: setter, outside hitter, middle blocker, opposite or libero. 6v6 lineups follow the 5-1 system, and smaller teams use a shortened template. The libero wears a contrasting jersey and swaps in for a back-row middle each rally. Rotation spot and role show above heads and in the sidebar, teams wear coloured jerseys, and players no longer push each other.
- **Proper matches, 1v1 through 6v6.** Matches use rally-point scoring, sets, and a deciding set to 15. Teams switch sides, including at 8 points in the deciding set. The match tracks rotations, side-outs, timeouts and a referee with undo.
- **Toggleable rules.** Rules are enforced automatically, and each one can be switched on or off: foot fault, net touch, centre line, four hits, double contact, antenna, reaching over, back-row attack, serve block, serve screening, dropped toss, ceiling/objects and the serve clock.
- **Courts in one command.** Courts are built from presets or custom sizes. The plugin places the floor and keeps an undo copy. Lines, padded posts, antennas, the net, a referee stand, team benches and a scorer's table are display entities, so removing a court restores the world.
- **Live HUD and scoreboards.** Each player gets a sidebar, a boss bar serve clock and action-bar hit grades. You can place physical **TextDisplay scoreboards** in the world and link them to any court.
- **A ball made of block displays.** The in-world ball is a voxel sphere of `BlockDisplay`s painted as a classic 18-panel volleyball (6 groups of 3 yellow/white/blue strips), so everyone sees the same ball with or without the pack. Spin is visible as it rotates.
- **Custom items and menus.** The volleyball is a sunflower, the referee whistle an iron nugget and the "hands" leather. Chest menus cover the rules editor and the referee panel. An in-game guide book is also included.
- **Sound for every action.** Each touch, serve, bounce, net and antenna contact has its own sound, as do dives, approach jumps and missed swings. The whistle, countdown and crowd (cheer, "ooh" on net cords, applause, match win) are covered too. Players with the pack hear custom sounds and everyone else hears mapped vanilla sounds, decided per player.

---

## Install

1. Build it with `mvn package`, or use a release jar. Put `PowderPass-1.0.0.jar` in `plugins/`.
2. Start the server. Edit `plugins/PowderPass/config.yml` if you like, then run `/vb reload`.
3. Optional: offer the resource pack (see [`resourcepack/README.md`](resourcepack/README.md)). Nothing has to be switched on: players without it automatically get vanilla items and sounds.

Requirements: Paper 1.21.11 and Java 21.

## Quick start (60 seconds)

```
/vb court create main              # stand where the centre should be, face along the court
/vb scoreboard create board main   # places a live scoreboard 2 blocks in front of you
/vb join main red                  # players join a team (red/blue) or just /vb join
/vb rules main                     # optional: format and rule menu
/vb start main                     # host starts the match
```

To practise alone, run `/vb practice`. It gives you a ball and the Hands item, and loose balls on any court are called in or out.

## Controls

Hold the **Volleyball Hands** item, which fills your hotbar during a match. Click when the ball reaches your hands; better timing gives more power and accuracy. The action bar grades every touch as **PERFECT / GOOD / OK / POOR**.

| Input | On the ground | In the air |
|---|---|---|
| Left-click | **Bump** (ball at waist) or **down ball** (ball above head) | **Spike** |
| Right-click | **Set** (high) | **Block** (at the net) / jump set |
| Sneak + left-click | **Dig / dive** (lunges forward) | **Tip** (soft shot over the block) |
| Sneak + right-click | **Quick set** (low, fast) | — |
| Sprint + jump near the net | Approach jump (extra height) | |

**Aiming.** Face the direction you want the ball to go:
- Looking **down** sends it short, and looking **ahead** sends it long.
- Looking **up at the ball** uses a standard distance (a normal pass or set).
- A coloured ring on the floor shows the target. Toggle it with `/vb aim`.

**Serving.** Hold the ball and wait for the whistle.

| Action | Result |
|---|---|
| Right-click | Toss |
| Sprint, then right-click | Jump toss (higher, in front of you) |
| Left-click the ball while standing | **Float serve** (no spin, wobbles in the air) |
| Left-click the ball in the air | **Jump topspin serve** (fast, dips) |
| Sneak while hitting | Swaps the spin type (standing topspin / jump float) |
| Left-click without tossing | **Underhand serve** (easy) |

## Commands

| Command | Permission | Description |
|---|---|---|
| `/vb help`, `/vb guide` | — | Help and the in-game guide book |
| `/vb join [court] [red\|blue] [position]` | `powderpass.play` | Join a court lobby; the court defaults to the nearest one |
| `/vb role [setter\|outside\|middle\|opposite\|libero\|any]` | — | Pick your playing position for the next match |
| `/vb leave` | — | Leave the lobby or match. Your inventory is restored |
| `/vb practice` | `powderpass.practice` | Practice kit (ball + hands) |
| `/vb ball spawn\|clear` | `powderpass.practice` | Spawn or clear your free-play balls |
| `/vb aim` | — | Toggle the aim marker |
| `/vb pack [on\|off\|auto\|send]` | — | Custom sounds if you installed the pack yourself; `send` downloads it when the server hosts it |
| `/vb timeout` | — | Call a timeout for your team, between rallies |
| `/vb start [court]` | `powderpass.host` | Start a match with the lobby; teams auto-balance |
| `/vb stop [court]` | `powderpass.host` | Stop a match |
| `/vb rules [court]` | `powderpass.host` | Rules and format GUI |
| `/vb rules <court> <rule\|option> <value>` | `powderpass.host` | Set one rule, e.g. `foot-fault off` or `points-per-set 21` |
| `/vb referee [court] [player]` | `powderpass.referee` | Appoint a referee, who gets the whistle |
| `/vb status [court]` | — | Lobby and score information |
| `/vb court create <name> [indoor\|women\|beach\|mini\|duel] [nofloor]` | `powderpass.admin` | Build a court |
| `/vb court create <name> custom <length> <width> <net>` | `powderpass.admin` | Custom size |
| `/vb court remove <name> [keepfloor]` | `powderpass.admin` | Remove a court and restore the original floor |
| `/vb court list\|info\|tp\|net\|respawn\|floor` | `powderpass.admin` | Manage courts. `net <court> <height>` changes the net height |
| `/vb scoreboard create <name> [court]` | `powderpass.admin` | Place a world scoreboard |
| `/vb scoreboard link\|move\|scale\|billboard\|remove\|list` | `powderpass.admin` | Manage scoreboards |
| `/vb give <player> <volleyball\|whistle\|hands>` | `powderpass.admin` | Custom items |
| `/vb reload` | `powderpass.admin` | Reload config.yml |
| `/vb debug stats\|launch\|dump\|cleanup` | `powderpass.admin` | Performance stats, a test launcher, a JSON dump of a court's displays (for offline previews), and stray-entity cleanup |

Aliases: `/vb`, `/volleyball`, `/powderpass`.

### Court presets

| Preset | Size | Net | Notes |
|---|---|---|---|
| `indoor` | 18 × 9 m | 2.43 m | FIVB men's |
| `women` | 18 × 9 m | 2.24 m | FIVB women's |
| `beach` | 16 × 8 m | 2.43 m | Sand floor, no attack line |
| `mini` | 12 × 6 m | 2.15 m | 2v2 – 4v4 |
| `duel` | 9 × 4.5 m | 2.00 m | 1v1 |

Courts align to the block grid along the direction you face. A 3 m free zone is floored around them.

## Rules

All rules are on by default and can be toggled per court with `/vb rules`. Rules can also be changed live from the referee menu.

| Rule | Fault when… |
|---|---|
| `foot-fault` | The server touches the end line or court when hitting. Jump serves use the take-off spot |
| `net-touch` | A player touches the net between the antennas during a rally |
| `center-line` | A player is fully across the centre line |
| `four-hits` | A team makes more than three contacts. The block does not count |
| `double-contact` | The same player touches twice in a row. A block does not count, and 1-player teams are exempt |
| `antenna` | The ball touches an antenna or crosses outside them |
| `reach-over` | A player attacks the ball on the opponent's side. Blocking is allowed |
| `back-row-attack` | A back-row player attacks above the net from the front zone, or blocks (6v6) |
| `serve-block` | A player blocks or attacks the serve |
| `serving-team-touch` | The serving team touches its own serve before it crosses |
| `toss-drop` | The service toss falls untouched |
| `ceiling-out` | The ball touches walls, ceiling, posts or other objects |
| `serve-delay` | The server does not serve within the serve clock (8 s) |
| `libero` | The libero blocks, or attacks a ball entirely above the net |
| `rotation-order` | 6v6 positional fault: at the serve a front-row player is not nearer the net than their back-row partner, or a row is out of left-to-right order. Off by default; the "All rules on" button enables it |

**Format options:**
- `sets-to-win`: 1, 2 = best of 3, 3 = best of 5.
- Points: `points-per-set` and `deciding-set-points`.
- Set end: `win-by` and `point-cap`.
- Timing: `serve-time-seconds`, `rally-end-delay-seconds`, `timeouts-per-set` and `timeout-seconds`.
- Toggles: `switch-sides`, `auto-position` and `referee-whistle`. With `referee-whistle` on, the serve waits for the referee's whistle.

The rules menu has presets for **Quick** (1 set to 15), **Standard** (best of 3), **Official** (best of 5) and **Beach** (to 21). It also has a **Casual** rule set for learning.

## Referee

`/vb referee <court>` gives the whistle.
- **Right-click** blows the whistle, which allows the serve when `referee-whistle` is on.
- **Sneak + right-click** opens the referee panel. From there you can pause or resume, replay the rally, award a point, undo the last point, edit live rules or end the match.

The match host gets a whistle automatically.

## How it works within Minecraft's limits

- **The ball** is a voxel sphere split into coloured cuboids (29 block displays for the default classic style at `ball.detail: 12`; the `caps` style at 10 uses 13). One display is the root and the others ride it as passengers, so a single interpolated teleport moves the whole ball smoothly. Spin rotates every part's transformation around the ball centre, refreshed every other tick and interpolated on the client. The root casts a shadow that helps players judge where the ball will land.
- **Physics** runs on the server only. Every tick is split into adaptive sub-steps (up to 32), so fast spikes never tunnel through the net, players or blocks. The ball collides with the real collision shapes of blocks, such as slabs, fences and barriers.
- **Input.** Minecraft only reports clicks, not arm movement. A click therefore *arms* the chosen technique for a few ticks (input buffering). The server then compares the ball's path with the ideal contact point of that technique. That path is rewound by the player's ping for lag compensation, and the distance becomes the contact quality.
- **Animations.** Players can't be posed by the server, so touches play the arm-swing animation (one or both arms), sounds and particles. Blocks lift both arms.
- **Trajectories.** Passes, sets and attacks are solved with the same physics model the live ball uses (bisection over launch speed or angle). Aim therefore matches what actually happens, and only contact quality adds error. Float serves wobble on purpose.
- **Net.** The net is about 150 thin block displays driven by a mass-spring membrane. Only segments that moved are re-sent, so a still net costs nothing.
- **Safety.** Courts and scoreboards keep their chunks loaded with plugin chunk tickets. All display entities are non-persistent and tagged, so a crash never leaves junk behind, and `/vb debug cleanup` exists anyway. Inventories are saved to disk before a match and restored afterwards, even after a crash.

Performance was measured on a live 1.21.11 server with bots. A 6v6 match with 12 players took about 0.6 ms per tick of plugin time, with an animated net and an active rally. Use `/vb debug stats` for a per-subsystem breakdown.

## Configuration

`config.yml` is fully commented. Highlights:
- `physics.*`: gravity, drag coefficients and the drag-crisis speed, Magnus lift, spin decay, knuckle strength, and restitution and friction for the floor, walls, net, tape and antennas.
- `hits.*`: per-technique reach, power, spin, error and aim curves.
- `controls.*`: input buffer, lag compensation, approach-jump boost, dive lunge and the aim marker.
- `court-defaults.*`: floor materials, net mesh resolution and view range.
- `match-defaults.*`: format and rules used for new courts.
- `teams` and `hud`: team names and colours, and HUD toggles.
- `sounds.*`: every sound has a `vanilla` entry and a `pack` entry (any sound event key, volume and pitch).
- `resource-pack.*`: `mode` (`auto` uses the client's pack status plus `/vb pack on`, `everyone`, `nobody`), and optionally `url`/`sha1`/`required` so the plugin offers the pack itself.
- `ball.*`: `style` (`classic` 18-panel or `caps`), `detail` (6 to 14 voxels across), panel `colors`, size and interpolation.
- `court-defaults.furniture`: referee stand, benches and scorer's table on or off. `net-columns`/`net-rows` set the mesh density.
- `teams.libero-color`: the libero's jersey colour.

## Resource pack (optional)

| | Without the pack | With the pack |
|---|---|---|
| Ball in the world | Block-display sphere | Same block-display sphere |
| Volleyball item | Sunflower | 3D volleyball model |
| Whistle item | Iron nugget | Referee whistle model |
| Hands item | Leather | Forearm "hands" icon |
| Sounds | Mapped vanilla sounds | Custom sounds (hits, bounces, net, whistle, crowd, sneakers) |

The plugin's items carry a `custom_model_data` tag, and the pack only replaces tagged items, so ordinary sunflowers, nuggets and leather stay vanilla. Build the zip with `resourcepack/build.sh`.

## Building and testing

```
mvn package          # builds target/PowderPass-1.0.0.jar and runs the unit tests
```

Unit tests cover the physics, the trajectory solver, the net simulation and the scoring rules. The plugin was also tested end-to-end on a Paper 1.21.11 server with mineflayer bots. Those tests covered court building, practice, every touch type, blocks, serves and jump serves, 1v1, 2v2 and 6v6 matches, rule faults, the referee GUI, timeouts and inventory restoration.
