# Ponder tutorials

Ponder adds animated, client-side guides to MChjong. Install the matching Ponder
release and its declared dependencies, hover over a supported item in an
inventory, and hold the key displayed by Ponder. Its **Mahjong** category also
groups the entries. Each player chooses this optional guide independently.

## Guides

**Placing a mahjong table** demonstrates the compact 3 x 3 footprint and the
four stool positions. It is available on ordinary tables, automatic tables and
stools.

**Preparing the table** shows a complete boxed set, internal two-box storage,
cloth placement, point sticks and equipment handling. It is available on both
tables, boxes, tiles, cloth and point sticks.

**Taking your seat** explains seating, rule selection, readying up, manual and
automatic handling, discarding and leaving the seat. It is available on both
tables and stools.

All guides provide English, Japanese, Simplified Chinese and Traditional Chinese
text, keyframes, replay and Ponder's standard reading and camera controls.

## Implementation

`common/src/main/java/top/skyeyefast/mchjong/compat/ponder` contains the shared
client plugin and storyboards. The loader client entry points register the
plugin after checking that Ponder is loaded. Ponder owns indexing and reloads.
Both loader descriptors declare it as optional; gameplay runs independently.

The scenes manipulate furniture and equipment in Ponder's isolated display
world, using MChjong's existing models and component types. Ponder's structure
backup restores each scene on replay. The structure generator in
`src/ponderData` uses Minecraft's native NBT encoder and runs as `generatePonder`
before resource processing. Both loader JARs contain the same generated
`assets/mchjong/ponder/table.nbt` resource.

The compile-only dependencies expose the official Ponder API. The `withPonder`
Gradle property adds Ponder and its declared dependencies to development runs.
The release artifacts contain MChjong's integration code and tutorial resources.

## Development and validation

Run the full build and the two ordinary client smoke tests:

```text
gradlew.bat buildAll --warning-mode fail
gradlew.bat :runSmokeClient --console=plain
gradlew.bat :neoforge:runSmokeClient --console=plain
```

Enable Ponder for the corresponding client checks:

```text
gradlew.bat :runSmokeClient -PwithPonder=true --console=plain
gradlew.bat :neoforge:runSmokeClient -PwithPonder=true --console=plain
```

For interactive development, add `-PwithPonder=true` to `:runClient` or
`:neoforge:runClient`.

The installed-Ponder checks validate all seven item entries, three storyboards,
four languages, plugin reload, full playback, replay restoration and native
Ponder screens. They run after the same gameplay checks as the ordinary smoke.
Evidence is saved in `build/smoke/ponder-evidence` and
`neoforge/build/smoke/ponder-evidence`; each contains `PASS.txt`,
`ponder-checks.txt` and language-specific screenshots under `screenshots`.
Ordinary smoke evidence remains in each loader's `build/smoke/evidence`.
Installed-Ponder runs use `build/smoke/ponder-run` for their game directory;
base-client runs use `build/smoke/run`. Each profile keeps its own settings,
resource-pack selection, logs and test worlds.

`PonderStructureTest` verifies the generated structure's palette, bounds,
unique block positions and four seats. Resource contract tests validate
translation key parity and reject duplicate keys.

## Compatibility

The current build profile uses Ponder 1.0.87 for Minecraft 1.21.1 on Fabric and
NeoForge. Dependency versions are selected in `gradle.properties`. Use the
loader-specific Ponder artifact for the same Minecraft release.
