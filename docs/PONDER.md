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

With Create installed in its supported profile, **Producing mahjong supplies**
and **Dyeing tiles and point sticks** cover the automated workshop. These two
guides appear on the printing plate, incomplete box and three survival dyes.
They share the same plugin and isolated worlds as the table tutorials. See
[Create workshop](CREATE.md).

## Implementation

`common/src/main/java/top/skyeyefast/mchjong/compat/ponder` contains the shared
client plugin and storyboards. The loader client entry points register the
plugin after checking that Ponder is loaded. Ponder owns indexing and reloads.
Both loader descriptors declare it as optional; gameplay runs independently.

The scenes manipulate furniture and equipment in Ponder's isolated display
world, using MChjong's existing models and item data. Ponder's structure
backup restores each scene on replay. The structure generator in
`common/src/ponderData` uses Minecraft's native NBT encoder; each loader runs its
own `generatePonder` task before resource processing. Both loader JARs contain the same generated
`assets/mchjong/ponder/table.nbt` resource.
Both loaders also generate a taller `workshop.nbt` template, keeping
the press, mixer and deployer inside Ponder's render and replay bounds.

The compile-only dependencies expose the official Ponder API. The `withPonder`
Gradle property adds Ponder and its declared dependencies to development runs.
The release artifacts contain MChjong's integration code and tutorial resources.

## Development and validation

Select the affected loader's tutorial check:

```text
gradlew.bat :fabric:runSmokeClient -PwithPonder=true -PsmokePonder=true --console=plain
gradlew.bat :forge:runSmokeClient -PwithPonder=true -PsmokePonder=true --console=plain
```

Use one representative loader for shared storyboard edits. Registration or loader API
changes require the corresponding loader check. Choose captures with
`-PsmokeScreenshots` as described in [Verification](VERIFICATION.md#scope-and-screenshots).

For interactive development, add `-PwithPonder=true` to `:fabric:runClient` or
`:forge:runClient`.

For tutorial-only changes, add `-PsmokePonder=true` to the installed-Ponder smoke
commands to run the existing registration, language, replay and visual checks
without repeating gameplay tests. A recipe viewer selected with
`-PrecipeBrowser=jei` or `-PrecipeBrowser=emi` is also checked. Add
`-PwithCreate=true` on either loader to include both workshop scenes and their five
item entries. This profile checks twelve item entries and five storyboards.
Use `-PsmokeBrowser=true -PrecipeBrowser=jei` or `emi` for recipe-viewer-only
checks, without repeating Ponder playback or unrelated gameplay scenarios.

The installed-Ponder checks validate all seven item entries, three storyboards,
representative Latin/CJK text, plugin reload, full playback, replay restoration and native
Ponder screens. They run after the same gameplay checks as the ordinary smoke.
Evidence is saved in `fabric/build/smoke/ponder-evidence` and
`forge/build/smoke/ponder-evidence`; each contains `PASS.txt`,
`ponder-checks.txt` and any explicitly requested screenshots under `screenshots`.
Ordinary smoke evidence remains in each loader's `build/smoke/evidence`.
Installed-Ponder runs use `fabric/build/smoke/ponder-run` for the Fabric game directory;
base-client runs use `fabric/build/smoke/run`. Each profile keeps its own settings,
resource-pack selection, logs and test worlds.

`PonderStructureTest` verifies the generated structure's palette, bounds,
unique block positions and four seats. Resource contract tests validate
translation key parity and reject duplicate keys.

## Compatibility

With Create installed, the printing plate, incomplete box and survival dyes
offer two shared workshop tutorials covering production and dyeing. Both loaders
include the taller `workshop.nbt` structure and all four translations. Use
`-PwithCreate=true -PsmokeCreate=true` on either loader's `runSmokeClient` task
for the focused production, selected recipe viewer and tutorial checks.

The current build profile uses Ponder 1.0.92 for Minecraft 1.20.1 on Fabric and
Forge. Dependency versions are selected in `gradle.properties`. Use the
loader-specific Ponder artifact for the same Minecraft release.

See [Compatibility](COMPATIBILITY.md) for the Create and recipe-viewer profiles.
