# PowderPass resource pack (optional)

Adds two things for players who have it:

- **The ball** - a 3D 18-panel volleyball model. The plugin's ball is a player head tagged with the
  `powderpass:volleyball` custom model data; this pack replaces only that head (other heads stay vanilla).
  Players without the pack see the volleyball player-head texture.
- **Sounds** - custom sound effects for every touch, bounce, the net, the whistle and the crowd.
  Turn them on with `custom-sounds: true` in the plugin's `config.yml` once everyone uses the pack
  (e.g. with `require-resource-pack=true` in `server.properties`); otherwise players hear vanilla sounds.

## Install

1. Run `./build.sh` (or zip the *contents* of this folder: `pack.mcmeta` must be at the root of the zip).
2. Host the zip and set `resource-pack=<url>` and `resource-pack-sha1=<sha1>` in `server.properties`.
3. Set `custom-sounds: true` in `plugins/PowderPass/config.yml` and run `/vb reload`.

Format: Minecraft Java 1.21.11 (pack format 75).
