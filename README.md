# PowderPass — volleyball for Paper 1.21.11

Multiplayer volleyball with real ball physics. Works on the vanilla client; an optional pack adds a 3D ball model and custom sounds.

- **Physics:** gravity, air drag, Magnus lift from spin, float-serve wobble, spin-aware bounces.
- **Matches 1v1–6v6:** rally scoring, sets, side switches, toggleable rules (`/vb rules`).
- **Animated net**, line calls (red/green line flash, ball mark, IN/OUT), live sidebar, boss bar and world scoreboards.
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

You play bare-handed. Everything is **left-click**; **sneak** picks the alternative. Aim with your view (look down = short, up = long).

| Situation | Left-click | Sneak + left-click |
|---|---|---|
| Ground, ball low | Bump | Dig / dive |
| Ground, ball above head | Set | Quick set |
| In the air | Spike (or block, at the net vs. an attack) | Tip |

**Serving** (you hold the ball after the whistle):

| Serve | How |
|---|---|
| Underhand | Sneak + left-click while holding |
| Float | Left-click to toss, then left-click standing |
| Topspin | Toss, then sneak + left-click standing |
| Jump serve | Toss, jump, left-click in the air |
| Banana | Toss, jump, sneak + left-click in the air (curves sideways) |

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
