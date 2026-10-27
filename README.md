# PowderPass — volleyball for Paper 1.21.11

Multiplayer volleyball with real ball physics. Works on the vanilla client; an optional pack adds a 3D ball model and custom sounds.

- **Physics:** gravity, air drag, Magnus lift from spin, float-serve wobble, spin-aware bounces.
- **Matches 1v1–6v6:** rally scoring, sets, side switches, toggleable rules (`/vb rules`).
- **Animated net**, line calls (red/green line flash, ball mark, IN/OUT), live sidebar and world scoreboards.
- **Pepper:** a no-rules drill, solo, pairs or circle.
- **Sittable benches.**

## Install

1. `mvn package` → put `target/PowderPass-1.0.0.jar` in `plugins/`. Needs Paper 1.21.11 and Java 21.
2. Optional: the resource pack in [`resourcepack/`](resourcepack/README.md). If players use it, set `custom-sounds: true` in `config.yml`.

## Quick start

```
/vb court create main              # stand at the centre, face along the court
/vb scoreboard create board main   # optional world scoreboard
/vb join main red                  # or just /vb join
/vb start main
```

## Controls

Just **click** when the ball is near you — the right touch is picked for you.

| Input | Does |
|---|---|
| Click | Pass (low ball), set (ball above head), spike (while jumping), block (jumping at the net vs. an attack) |
| Sneak + click | Dive for low balls · quick set · tip while jumping |

Passes and sets aim themselves (rule `aim-assist`); look where you want spikes and serves to go.

**Serving:** click to toss, click again to hit. Jump before hitting for a jump serve; hold sneak for topspin (standing) or banana (jumping). Sneak + click without tossing = underhand.

## Commands

| Command | Permission |
|---|---|
| `/vb join [court] [red\|blue]`, `/vb leave` | `powderpass.play` |
| `/vb ball [clear]` — get / clear a practice ball | `powderpass.practice` |
| `/vb pepper [join <player>\|leave]` | `powderpass.practice` |
| `/vb start\|stop [court]`, `/vb rules [court]` | `powderpass.host` |
| `/vb court create\|remove\|list`, `/vb scoreboard create\|remove`, `/vb reload` | `powderpass.admin` |

## Building and testing

`mvn package` builds the jar and runs the unit tests.
