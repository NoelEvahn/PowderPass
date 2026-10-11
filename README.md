# PowderPass

A geology-first Overworld world preset for **Minecraft Java 1.21.11 (Fabric)**. Plate tectonics build the ranges, rainfall-driven drainage and a stream-power erosion model carve the valleys, sediment builds floodplains, fans and deltas, and rare uncanny landforms and bounded working volcanoes punctuate the map. It uses vanilla blocks only, and generated terrain never exceeds **Y = 700**.

> ## Status — read this first
>
> | Part | State |
> |---|---|
> | **Generation core** (`core/`, pure Java, no Minecraft dependency) | Implemented, built, and tested: 13 automated tests, diagnostic maps, benchmarks. Everything under *Validation* was measured on the core. |
> | **Fabric mod layer** (`fabric/`) | Fully written, but **not compiled and not run**. This build environment's network policy blocks `maven.fabricmc.net`, `meta.fabricmc.net`, and Mojang's `piston-meta` / `piston-data` / `libraries.minecraft.net`, so Loom cannot fetch Minecraft or Fabric. No mod JAR has been produced and no dedicated server has been started. |
> | **In-game QA** | None performed. There are **no in-game screenshots**. All images in `docs/maps` are generator diagnostics rendered offline from the core. |
>
> The vanilla API calls in `fabric/` were checked against Fabric API's own `1.21.11` sources (Mojang mappings) wherever a reference existed. A few signatures could not be verified. They are listed under *Known limitations*. Expect a short compile-fix pass on the first real build.

## Contents

- [Installation](#installation-once-built)
- [Building](#building)
- [Configuration](#configuration)
- [Commands](#commands)
- [Architecture](#architecture)
- [Volcanoes](#volcanoes)
- [Validation results](#validation-results)
- [Showcase](#showcase-seeds-and-coordinates)
- [Known limitations](#known-limitations)
- [Versions](#versions)

## Installation (once built)

1. Install Fabric Loader **0.19.5+** for Minecraft **1.21.11**, plus **Fabric API 0.141.6+1.21.11**. Java **21** is required.
2. Put `powderpass-<version>+mc1.21.11.jar` in `mods/`.
3. **Singleplayer:** Create World → More → World Type → **PowderPass**.
4. **Dedicated server:**
   - Set `level-type=powderpass\:powderpass` in `server.properties` **before the world is first created**.
   - Install the mod on the server.
   - Players should also install it. The dimension type and custom biomes are data-driven and synced through vanilla registry sync, and the chunk generator and biome source are server-only, so an unmodded client *might* be able to join. **That has not been tested; don't rely on it.**
5. **New worlds only.** PowderPass never regenerates existing chunks.
   - Adding it to an existing world does nothing to that world.
   - Switching an existing world's generator would give hard seams between old and new chunks. That isn't supported.

## Building

```bash
./gradlew build                                        # mod jar -> fabric/build/libs (needs Fabric + Mojang hosts)
./gradlew :core:test -Dpowderpass.coreOnly=true        # core tests only (works offline except Maven Central)
./gradlew :core:exportMaps -Dpowderpass.coreOnly=true -Pseed=12345 -Pcx=0 -Pcz=0 -Psize=8192
python3 tools/gen_resources.py                         # regenerate biome/tag/preset JSON
```

The Gradle wrapper (9.7.1) and the plugin/dependency versions match FabricMC's official `fabric-example-mod` `1.21.11` branch.

## Configuration

The config file is `config/powderpass.json`. A complete example is in [`docs/powderpass.example.json`](docs/powderpass.example.json).

### `new_world` — geography settings

- Read **only when a world is created**. The values are then stored in that world's level data (the serialized chunk generator) and never re-read. Editing the config cannot change future chunks of an existing world.
- Settings:
  - **Terrain:** terrain cap (≤ 700), sea level, horizontal scale, mountain frequency, flatland bias.
  - **Water and erosion:** river density, erosion strength, erosion iterations, glaciation.
  - **Surface:** biome size, vegetation density, slab detail.
  - **Features:** anomaly frequency, volcano frequency, vanilla structures.
- `generator_version` is stored too, so later algorithm changes can be detected.

### `runtime` — reloadable with `/powderpass reload`

- Volcano activity on/off, destructive volcanism, and ambient effects.
- Maximum concurrent eruptions, lava placements per tick, and the eruption lava budget.
- Phase durations (dormancy, unrest, eruption, cooling).
- The inhabited-time threshold used for build protection.
- The simulation cache budget (MB) and the volcano scan radius.

## Commands

All commands are under `/powderpass` and are operator-only.

| Command | Purpose |
|---|---|
| `inspect` | Geology, climate, erosion, water and watershed at your position, including the full downstream chain to the sea or a terminal basin. |
| `locate volcano [active]` | Nearest volcano, from deterministic metadata. |
| `locate anomaly <salt_flat\|impact_crater\|deep_pit\|fault_escarpment\|circular_basin\|monolith\|barren_flat>` | Nearest anomaly of that type. |
| `locate landform <plain\|mountain_valley\|hill_transition\|fault_scarp\|large_river\|lake\|glacial_valley\|rock_slope\|delta>` | Bounded column-sample search. Never generates chunks. |
| `volcano status` / `erupt` / `calm` | Inspect the nearest volcano, trigger a bounded test eruption, or stop an eruption. |
| `export <size>` | Background diagnostic map export to `config/powderpass-maps`. |
| `settings`, `cache`, `reload` | Show persisted settings, show cache statistics, reload the runtime config. |

## Architecture

The pipeline runs in this logical order. Every stage is a pure function of (seed, settings, x, z).

1. **Tectonics** (`geo/Tectonics`)
   - Plates are cells of a domain-warped jittered Voronoi diagram, each with a crust type and a motion vector.
   - Boundaries are explicit clipped edge segments, each carrying convergence and shear.
   - Every boundary effect is evaluated as a distance to these segments, and crust type uses a softmax over seed distances. This keeps fields continuous: no seams at triple junctions.
2. **Geology** (`geo/Geology`)
   - Convergent boundaries make uplift belts (a broad highland plus a spine, with along-strike saddles). Continent–ocean boundaries also make coastal ranges and volcanic arcs.
   - Divergent boundaries make rifts with raised shoulders. Transform boundaries make linear troughs with offset ridges.
   - Also included: rare intraplate old ranges, plateaus with escarpments, and fault blocks with strata throw plus a few linear scarps.
   - The result is classified into provinces: young or old orogen, shield, sedimentary basin, plateau, volcanic, rift, karst, coastal lowland, ocean.
   - Each province has a bedrock resistance, and alternating hard and soft strata change resistance with depth. That produces benches, cliff bands, and caprocks during erosion.
3. **Climate** (`climate/Climate`)
   - Temperature has a lapse rate with altitude.
   - Precipitation includes maritime influence, orographic lift, and **rain shadows**, computed by integrating the geological elevation upwind along a slowly varying prevailing wind.
4. **Overlays** (`volcano/`, `anomaly/`)
   - Volcanic edifices and anomalies modify the *pre-erosion* surface, so drainage and erosion respond to them: craters collect water, flats become terminal sinks, and cones develop radial gullies.
5. **Drainage domains** (`hydro/Domains`) — the seamless-world solution:
   - **Ownership.** The plane is split into ~768-block domains (gently warped Voronoi). Every column has exactly one owner.
   - **Routing.** Domains form a drainage graph using routing keys from a **bounded priority-flood over the domain graph**: a minimax spill level to the sea or the window rim, with hop count as tie-break, computed in a fixed 9×9-domain window per domain. Low passes are preferred spillways. A domain only routes to a neighbour with a strictly smaller key, so the graph is **acyclic by construction**.
   - **Terminal basins.** Arid local depressions stay terminal (endorheic) basins.
   - **Passes.** Water crosses a domain border only at a canonical **pass**: position, water level, discharge, and a straight-but-sinuous canonical channel. These are functions of the domain pair alone, so both domains derive identical values without generating or reading each other.
   - **Divides.** Borders are divides pinned to a canonical boundary height. Each side blends to it within 28 blocks.
   - **Bounds.** Long-distance routing is bounded: discharge accumulates over at most 96 upstream domains. Outlet levels follow the strictly descending chain to the sea or a terminal basin.
6. **Erosion** (`hydro/DomainSim`) — per domain on an 8-block grid. These processes are actually simulated:
   - **Routing:** epsilon priority-flood with noise-weighted epsilon, D8 steepest-descent receivers, and precipitation-weighted drainage area.
   - **Depressions:** hybrid least-cost (Dijkstra) breaching keeps shallow ponds and genuine deep basins as lakes, and breaches the rest.
   - **Fluvial incision:** implicit **stream-power** law. Erodibility comes from the resistance of the rock layer exposed at the current elevation; young orogens also get uplift.
   - **Sediment:** transport-capacity deposition builds alluvium, fans, and lake infill. Sediment reaching the sea builds shelves and delta lobes. Sediment is conserved within a domain; what leaves through a pass is counted as exported, not handed to the neighbour.
   - **Hillslopes:** thermal (talus) relaxation with a resistance-dependent critical slope, plus creep.
   - **Ancient glaciation:** ice flux above an equilibrium-line altitude erodes ∝ √flux and widens valley floors. This produces U-shaped valleys, overdeepenings and tarns, hanging tributaries, and fjords where glaciers reach the sea.
7. **Rivers** (`hydro/RiverNet`) — polylines follow simulated receivers.
   - Confluences and pass endpoints are exact.
   - Water levels come from the routed surface and never rise downstream.
   - Appearance reconstructions, not simulations: Chaikin smoothing, meanders on low-gradient reaches, rare oxbows, braided gravel bars, and delta distributaries.
8. **Terrain** (`terrain/TerrainSampler`, `ColumnGrid`, `ChunkComposer`)
   - Bicubic reconstruction of the eroded grid → border blend → pass notches (elongated water gaps) and channels → pinned flat cores → continuous multi-segment channel carving → lakes and sea → detail → soft elevation envelope and the hard cap.
   - **Bank containment:** a dry column next to water is raised to the water's top, or one block higher next to rivers, so stepped reaches can overflow after fluid ticks without leaking.
   - Materials: strata whose hardness matches the erosion model, sediments, biome soils, sealed beds, and water-aware noise caves.
   - River step blocks get fluid ticks via post-processing.
9. **Biomes** (`biome/BiomeClassifier`)
   - 30 custom biomes plus vanilla oceans, rivers, deep dark, and lush/dripstone caves.
   - Inputs: temperature with lapse rate, precipitation with rain shadow, elevation, slope at the 32-block scale, river proximity and discharge, wetness, province, and overlays.
10. **Decoration** (`deco/`) — procedural trees (14 species shapes, including wind-shaped krummholz), bushes, boulders, fallen logs, ground cover, snow, and rock-slope slabs.
    - **Order-independent:** a candidate is kept only if no higher-priority candidate overlaps it. The rule reads terrain functions only, never placed blocks.
    - Leaves get correct vanilla `distance` values, so they don't decay.
11. **Vanilla**
    - Supplies ores and other biome features, structures (biome tags restrict them to lower-terrain biomes), lighting, heightmaps, and mob spawning.
    - Ores are not rescaled: the deep crust stays vanilla stone, so ore-hosting rock is where vanilla expects it.

The **height** setup is a dimension type with `min_y = -64` and `height = 832`, so the build limit is Y 767. Generated terrain is capped at Y 700 (lower if configured) with a smooth envelope that starts around Y 516. Vegetation and snow stay below the cap, and the Nether and End are unchanged.

## Volcanoes

- **Placement**
  - Generated types: shield, strato, cinder, caldera, and eroded-extinct.
  - Most sit on tectonic belts: arcs above convergent margins, and rifts.
  - Hotspot chains age along plate motion; only their youngest member can be active.
  - Eruption-capable volcanoes get a breached vent and lava grooves down the flank. The grooves descend monotonically and end in levee-rimmed lava-field basins.
- **Lifecycle (persisted per world):** dormant → unrest → eruption → cooling → dormant, plus extinct.
  - Time advances **only while the area is loaded**, so there is no catch-up burst.
  - Unrest uses smoke particles and lava sounds.
  - Eruptions release a **finite budget** of real lava sources along the grooves, at most N placements per tick, with a cap on concurrent eruptions.
  - Cooling turns only lava blocks inside the tracked footprint into basalt, smooth basalt, magma, or blackstone.
- **Build protection (default)** — lava is placed only when every chunk of the eruption zone:
  - is loaded,
  - has no recorded player block break or use,
  - and has an inhabited time below the threshold. This is the fallback for changes we can't observe, such as explosions or other mods.
  - On top of that, no flammable block may be near the lava path.
  - A runaway guard samples channel margins and basin rims. If it finds stray lava, it aborts the eruption and cools everything at once.
  - If containment can't be established, the eruption runs as ambient effects only.
- **Destructive mode** (`destructiveVolcanism`) skips the zone checks. In that mode, vanilla lava and fire can destroy builds.
- **Status:** none of the runtime behaviour has been tested in game. The lifecycle state machine, path generation, and persistence encoding are unit-tested in the core.

## Validation results

### Environment and tests

- **Hardware:** container with 4 vCPU (Intel Xeon @ 2.80 GHz) and 15 GB RAM, OpenJDK 21.
- **Tests:** 13 core tests, all passing (`./gradlew :core:test`). They cover:

| Requirement | Test and result |
|---|---|
| Determinism | Identical blocks across fresh worlds, reversed request order, cache eviction under a tiny budget, concurrent generation on 4 threads with shuffled order, and negative coordinates. Decoration is identical regardless of chunk order. |
| Seams | Height steps across domain borders: p99 **1.31** blocks per block vs **1.53** in the interior (1,559 border pairs). Chunk halo columns agree exactly. |
| Drainage | All routed cells strictly descend on the filled surface. The domain graph strictly descends in routing key (no cycles). River surfaces never rise downstream. Pass levels and positions match exactly on both sides (15 passes). |
| Cap and containment | No column above the cap. 5,676 water columns in 106 river and lake chunks: **0** water sides exposed to air. |
| Erosion effect | Same seed with and without erosion, per domain: 7–31% of cells incised > 2 blocks, maximum incision 13–55 blocks, deposition present. Volumes and maps are in `docs/reports`. |
| Volcano | Full lifecycle with no catch-up while unloaded. Cooling waits for tracked lava. Lava path floors never rise; release points stay ≤ cap. Position packing round-trips. |

### Measured on generator output

**Landform proportions** (8 km window, seed 12345, documented classification in `MapExport.classify`):

| Class | Measured | Target |
|---|---|---|
| Plains | 32.6% | 30% |
| Hills | 28.7% | 30% |
| Mountains / highlands | 25.5% | 25% |
| Plateau / escarpment | 8.8% | 10% |
| Unusual | 4.3% | 5% |

Over 32 km on 3 seeds, measured on the pre-erosion surface, mountains come out lower (18–22%).

**Drainage:** in 625-domain samples, 15–38% of inland drainage ends in 1–4 arid terminal basins. Mean chains are 2.4–4.4 domain hops long.

**Performance** (core only: compose and decorate, 4 threads; Minecraft lighting and saving not included):

| Measure | Result |
|---|---|
| Cold throughput, including domain simulations | 70–148 chunks/s |
| Warm throughput | 220–330 chunks/s |
| Compose time | median 9–13 ms, p95 21–30 ms |
| Decoration time | median 4–6 ms |
| First chunk in a new area | ~3–5 s stall, from the routing window plus simulation |
| Domain simulation memory | ~1 MB each, in a bounded cache |

No FPS or TPS numbers are claimed: nothing was measured in game.

## Showcase seeds and coordinates

These coordinates were **found and numerically checked in the core generator's output** (`core/src/test/.../VerifyShowcase.java`, results in `docs/reports/showcase_*.txt`). **None has been visited in game.**

| Feature | Seed 12345 | Seed 987654321 | Seed 42 |
|---|---|---|---|
| Spawn (computed) | -136 77 152 (boreal forest) | -40 114 56 | -40 140 -136 |
| River-carved mountain valley | 1888 239 -928 (incised 36, sides +68) | 2880 187 1824 | -1152 193 192 |
| Broad plain (relief over 200×200) | -576 72 -160 (13 blocks) | -1152 71 1536 (10) | -1184 77 1312 (14) |
| Hilly biome transition | -32 220 -32 | -32 137 -32 | -64 148 -64 |
| Slab-detailed rock slope | 92 155 -17 (88 slabs in chunk) | 83 67 -17 (12) | -30 218 14 (36) |
| Fault escarpment (step) | -3190 5254 (27 blocks) | -13506 -15173 (47) | -19358 1254 (64) |
| Working volcano | shield 6819 4220; strato -470 -9589 | strato -809 -1843 | strato 354 4837 |
| Perfectly flat basin | salt flat -6609 3350 (Y 74 everywhere within 0.6 R) | -36214 -6385 (Y 70) | -5917 1694 (Y 65) |
| Deep crater | impact -11631 2041 (depth 45); pit -1682 2633 (floor Y -40) | impact -3352 -11245 (48) | impact 7414 10979 (249) |

Maps in [`docs/maps`](docs/maps):

| Files | What they show |
|---|---|
| `elevation_*`, `elevation_*_noerosion` | Hillshade with and without erosion |
| `erosion_*` | Red = incised, blue = deposited |
| `drainage_*` | Drainage area plus extracted rivers |
| `geology_*` | Provinces and fault traces |
| `precipitation_*`, `biomes_*`, `slope_*`, `features_*` | Their respective layers |
| `blocks_*` | Top block per column after composition and decoration: spawn area, stratovolcano, valley, salt flat, crater |

## Known limitations

### Not built or run
- The Fabric layer has not been built or run, so no mod JAR is delivered (see Status).
- These signatures could not be verified against a reference: `ChunkGenerator.createBiomes(RandomState, Blender, StructureManager, ChunkAccess)`, `createStructures(...)` (assumed 6-parameter form), `applyBiomeDecoration`, `spawnOriginalMobs` internals, and particle/sound overloads.
- Operator checks use reflection because 1.21.11 replaced permission levels with permission sets. If no known shape works, the commands become console-only.
- World spawn is applied once via `/setworldspawn`.

### Hydrology approximations
- Sediment is conserved per domain, but not passed between domains: inflow sediment is not modelled.
- Discharge saturates beyond 96 upstream domains.
- Routing keys come from a bounded 9×9-domain window. A few non-arid domains can therefore end as terminal basins.
- Meanders, oxbows, braided bars, delta distributaries, and the canonical pass channels are geometric reconstructions, not simulations.
- Rivers are stepped water. A thin film of flowing water spills over each step after fluid ticks, and banks are raised to contain it.
- Where a river enters a lake at a higher level, that film may spread up to 7 blocks onto the lake shore.
- Caves are dry: there are no aquifers. They are kept 7+ blocks under any water.
- Some straight channel segments remain on pinned flats and transform-fault troughs.

### Content
- **Vanilla features** in biome JSON (ores, geodes, springs, dungeons) run as usual. Dungeons (`monster_room`) are always present: biome feature lists are static data, so the "vanilla structures" switch covers only structures.
- **Vanilla-feature determinism:** vanilla features keep vanilla's own behaviour, for example overlapping ore veins can depend on placement order. PowderPass's own blocks are order-independent.
- **High mountains:** terrain above vanilla's ore ranges (Y ≈ 384) has no ores.
- **Block ids:** blocks added in 1.21.5 (`bush`, `short_dry_grass`, `leaf_litter`, `wildflowers`, …) are used with fallbacks if absent.
- **Biome JSON:** sets only `minecraft:visual/sky_color` among environment attributes, so fog colours are default.

### Volcano runtime
- Build protection is conservative but not perfect. Changes made without block break or use events (pistons, explosions, other mods) are covered only by the inhabited-time fallback.
- Cooling converts any lava within 4 blocks of tracked points, which could include player-placed lava inside an eruption zone.

### Untested in game
- Lighting at heights up to Y 700, fog, mob spawning, structures, distant-terrain mods, and performance under real server load.

## Versions

| Component | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 |
| Fabric Loom | 1.18-SNAPSHOT (`net.fabricmc.fabric-loom-remap`) |
| Fabric API | 0.141.6+1.21.11 |
| Mappings | Official Mojang |
| Java | 21 |
| Gradle | 9.7.1 (wrapper) |
| JUnit | 5.11.4 (core tests) |

Versions were taken from FabricMC's `fabric-example-mod` `1.21.11` branch, and API usage was cross-checked against `FabricMC/fabric` branch `1.21.11`.

The license is MIT (see `LICENSE`). Inspired by the scale of large-terrain packs such as JJThunder To The Max. This is an original implementation with no shared code or assets.
