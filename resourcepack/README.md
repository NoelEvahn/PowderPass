# PowderPass resource pack (optional)

Adds custom 3D models for the plugin's items. The plugin works without it; players
just see the vanilla base items instead.

| Item model id          | What it is                                                        |
|------------------------|-------------------------------------------------------------------|
| `powderpass:volleyball`| 3D yellow/blue/white volleyball, 8 px across (player-head sized)   |
| `powderpass:whistle`   | 3D metal referee whistle with mouthpiece, window and lanyard ring |
| `powderpass:hands`     | Flat 16x16 icon of two forearms joined for a bump pass             |

Targets Minecraft Java 1.21.11 (resource pack format 75). Models are registered through
`assets/powderpass/items/*.json`, which the plugin selects via the `item_model` component.

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

## Host it

Upload the zip anywhere that serves it over a plain, direct HTTP(S) download link
(your own web server, S3/R2 bucket, GitHub release asset, ...). Pages that return HTML
(e.g. a Google Drive preview) will not work. Re-upload and update the SHA-1 whenever
the pack changes, otherwise clients may keep a stale cached copy.

## Wire it up

`server.properties`:

```properties
resource-pack=https://example.com/PowderPass-pack.zip
resource-pack-sha1=<output of sha1sum>
require-resource-pack=false
resource-pack-prompt=PowderPass volleyball models
```

Set `require-resource-pack=true` to kick players who decline it. Restart the server
(or use a plugin that sends the pack) after changing these.

Plugin `config.yml` (the pack is only used when this is on):

```yaml
resource-pack:
  enabled: true
```

The `ball-model`, `whistle-model` and `hands-model` keys already default to the three ids above.
Newly created items pick up the models; hand out fresh items after enabling.
