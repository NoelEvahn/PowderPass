# PowderPass resource pack (optional)

Adds 3D held-item models and custom volleyball sound effects for the plugin. **The pack is
optional.** The plugin works fully without it: players who do not have the pack see the normal
vanilla items and hear the normal vanilla sounds, automatically, with no configuration needed.

## What it changes

### Held-item models

The plugin does not use the `item_model` component. Its items are plain vanilla materials tagged
with the first string (index 0) of the `minecraft:custom_model_data` component. The pack overrides
exactly those three vanilla item definitions with a `minecraft:select` on that string:

| Vanilla item   | `custom_model_data` strings[0] | Model                                                              |
|----------------|--------------------------------|--------------------------------------------------------------------|
| `sunflower`    | `powderpass:volleyball`        | 3D yellow/blue/white volleyball, 8 px across (player-head sized)   |
| `iron_nugget`  | `powderpass:whistle`           | 3D metal referee whistle with mouthpiece, window and lanyard ring  |
| `leather`      | `powderpass:hands`             | Flat 16x16 icon of two forearms joined for a bump pass             |

Files: `assets/minecraft/items/{sunflower,iron_nugget,leather}.json`. Each has a single case for
the string above and a `fallback` that is the unmodified vanilla model, so a normal sunflower,
iron nugget or piece of leather (anything without the tag) still looks vanilla for pack users too.
The in-world ball is built from block displays by the plugin and does not need the pack; the
volleyball model is only used for the held/inventory item.

### Custom sounds

`assets/powderpass/sounds.json` registers these events (namespace `powderpass`). Each has a
subtitle (`assets/powderpass/lang/en_us.json`, keys `subtitles.powderpass.<event>`). All files are
mono OGG Vorbis (44.1 kHz) so they play positionally.

| Group    | Events (variants)                                                                                          |
|----------|------------------------------------------------------------------------------------------------------------|
| Ball     | `ball.bump`(3) `ball.dig`(3) `ball.set`(3) `ball.spike`(3) `ball.spike_perfect`(2) `ball.tip`(3) `ball.serve`(3) `ball.serve_jump`(3) `ball.block`(3) `ball.body`(3) `ball.floor`(3) `ball.sand`(3) `ball.wall`(3) `ball.toss`(2) |
| Net      | `net.hit`(3) `net.tape`(3) `net.antenna`(2)                                                                |
| Whistle  | `whistle.short`(1) `whistle.long`(1)                                                                       |
| Crowd    | `crowd.cheer`(2) `crowd.applause`(2) `crowd.ooh`(2) `crowd.win`(1)                                         |
| UI       | `ui.point`(1) `ui.countdown`(1) `ui.go`(1)                                                                 |
| Player   | `player.jump`(3) `player.dive`(2) `player.whiff`(3)                                                        |

The sounds are synthesised procedurally (no recordings, no licensing concerns). The generator
is not part of the repository; the `.ogg` files are the source of truth.

## Which players get what (pack detection)

The plugin decides per player whether to use the pack-specific items and sounds, so players
without the pack never get silent or invisible custom content. Detection is controlled in the
plugin `config.yml`:

```yaml
resource-pack:
  mode: auto        # auto | everyone | nobody
  url: ""           # optional: direct download link of PowderPass-pack.zip
  sha1: ""          # optional: SHA-1 of that zip (see Build)
  required: false   # optional: kick players who decline the pack
```

* `auto` (default): uses the client's resource-pack status reported to the server. Players whose
  client reports the pack as successfully loaded get the pack experience; everyone else gets
  vanilla items and vanilla sounds.
* `everyone`: treat every player as having the pack (use when the pack is forced through
  `server.properties` or a proxy).
* `nobody`: never use pack features; vanilla items and sounds for all.
* If `url` (and ideally `sha1`) are set, the plugin sends the pack to players itself when they
  join. `required: true` kicks players who decline it; leave it `false` to keep the pack optional.
* Players who installed the pack manually (client-side, so the server never reports a status)
  can switch it on or off for themselves with `/vb pack on` and `/vb pack off`.

Because a tagged item is just a vanilla sunflower/iron nugget/leather with
`custom_model_data`, a player without the pack simply sees the vanilla item.

## Build

```sh
./build.sh
```

This writes `../target/PowderPass-pack.zip` and prints its SHA-1. To do it by hand, zip the
**contents** of this folder so that `pack.mcmeta` sits at the zip root (not inside a
sub-folder):

```sh
cd resourcepack
zip -X -r ../target/PowderPass-pack.zip pack.mcmeta pack.png assets
```

Compute the hash (a rebuild changes the SHA-1 because zip stores timestamps):

```sh
sha1sum ../target/PowderPass-pack.zip | cut -d' ' -f1
```

Targets Minecraft Java 1.21.11 (resource pack format 75).

## Host it

Upload the zip anywhere that serves it over a plain, direct HTTP(S) download link
(your own web server, S3/R2 bucket, GitHub release asset, ...). Pages that return HTML
(e.g. a Google Drive preview) will not work. Re-upload and update the SHA-1 whenever
the pack changes, otherwise clients may keep a stale cached copy.

## Wire it up

Either let the plugin send it (set `resource-pack.url`, `sha1`, and optionally `required` in
`config.yml`, with `mode: auto`), or let the server do it in `server.properties`:

```properties
resource-pack=https://example.com/PowderPass-pack.zip
resource-pack-sha1=<output of sha1sum>
require-resource-pack=false
resource-pack-prompt=PowderPass volleyball models and sounds
```

and set `resource-pack.mode: everyone` (or leave `auto`, which picks up the status the client
reports). Restart the server after changing `server.properties`. Newly created items carry the
`custom_model_data` tag, so hand out fresh items after enabling.
