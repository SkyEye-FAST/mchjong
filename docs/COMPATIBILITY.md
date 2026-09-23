# Compatibility and verification

## Development profile

Release artifacts follow their Minecraft build profile. The current recipe
viewer adapters target Minecraft 1.21.1 with Java 21, Fabric Loader 0.19.5 and
Fabric API 0.116.17+1.21.1, or NeoForge 21.1.250. Forge 1.21.1 pins Forge 52.1.16.
Dedicated release artifacts and validation coverage are provided for each
supported Minecraft version.

| Minecraft | Loader artifacts | Validation scope |
| --- | --- | --- |
| 1.21.1 | Fabric, Forge, NeoForge | Forge has dedicated loader bootstrap checks; full gameplay acceptance remains loader-specific |
| 1.20.1 | Fabric and Forge on `compat/1.20.1` | Shared integrated-server/client gameplay and Ponder smokes pass |
| 1.21.1 and 1.20.1 | Quilt consumes the corresponding Fabric artifact | Quilt Loader 0.30.1 loads both packaged JARs to the title screen, using Java 21 and Java 17 respectively |

Viewer integrations are available across the supported loaders: JEI is available
on Fabric, Forge and NeoForge 1.21.1, while EMI is available on Fabric and
NeoForge 1.21.1. On Minecraft 1.20.1, JEI, EMI and REI are available on Fabric
and Forge. Optional Ponder tutorials cover Fabric and NeoForge 1.21.1 and both
1.20.1 loaders.

## Version synchronization and artifacts

`main` is the sole feature development line. Synchronize stable batches into
`compat/1.20.1`, resolve only the Minecraft and loader adaptation differences, then
validate the port. Engine and shared gameplay changes travel through this Git
history rather than a separate feature implementation.

The release workflow on `main` builds its three loader artifacts and
the two 1.20.1 artifacts from the exact commit in `.github/compat-1.20.1-ref`.
Snapshot workflows build single-version development artifacts for their respective
branch profile. Update that pin only after reviewing and validating a stable port batch. Both
checkouts receive the same mod version during assembly; the filenames include
Minecraft version and loader. A main commit does not automatically advance the
compatibility pin. Review feature parity and outstanding port acceptance before
publishing a release. Quilt uses the corresponding Fabric JAR.

The 1.20.1 port uses native ItemStack NBT, recipe serializers, packet buffers and
client APIs. JEI 15.59.0.212, EMI 1.1.24+1.20.1, REI 12.0.684 and Ponder 1.0.92
have loader-specific development profiles; optional dependencies stay outside
MChjong's release bundles. The port's `docs/PORT_1.20.1.md` records its validation.

## Optional integrations

| Optional viewer | Pinned version | Development runtime |
| --- | --- | --- |
| JEI | 19.56.0.441 | `-PrecipeBrowser=jei`, with MezzConfig 0.5.6 |
| EMI | 1.1.24+1.21.1 | `-PrecipeBrowser=emi` |
| Base installation | Current build profile | `-PrecipeBrowser=none` (default) |

All dependency versions live in `gradle.properties`. Viewer API dependencies are
compile-only. Each optional profile adds its viewer to the development runtime;
distributed MChjong jars contain the MChjong adapters and the shared engine.

## Recipe and component coverage

The default catalogue uses `MahjongCatalog` on both loaders. Empty and complete
136-tile cases are consecutive. Blank tiles in all sixteen materials and both mahjong dyes precede blank,
100, 1,000, 5,000 and 10,000 point-stick denominations. Tables and automatic tables list
every wood species. Tile faces, including
flowers, share the blank-tile item and are printed through the box menu.

Recipe identity distinguishes wood, material, back color, face preset, face, red markings,
denomination and complete container components. A custom outer item name is
cosmetic for lookup; crafting still applies the source recipe's name-preservation
rules. Examples are built from the recipes in the currently loaded datapack.
Every cycling ingredient is accepted only when the source recipe produces the
displayed output with identical components and count.

The finite crafting displays cover eight-stick marking batches, every table
wood, and all sixteen back-dye colors on representative tiles, stools, cloth, boxes, red fives and flowers.
Case back-dye examples cover each material with blue backs: a completed set,
or 144 blanks with four 1,000-point sticks in the separate stick compartment.
The ordinary four-color mahjong-dye recipe uses the viewers' vanilla crafting category.
Dye inputs cycle through colors only when crafting yields the exact same output.
Arbitrarily rearranged, mixed-material or specially
named container contents retain exact identities; their survival operations are
governed by the same server recipes, while these particular container layouts
are outside the pre-enumerated viewer examples.

## Verification commands

Use the repository wrapper with JDK 21:

```text
gradlew.bat buildAll --warning-mode fail --console=plain
gradlew.bat :fabric:runSmokeClient --console=plain
gradlew.bat :neoforge:runSmokeClient --console=plain
gradlew.bat :forge:runSmokeClient --console=plain
gradlew.bat :forge:runSmokeClient -PrecipeBrowser=jei --console=plain
gradlew.bat :forge:runSmokeServer --console=plain
gradlew.bat :fabric:runSmokeClient -PrecipeBrowser=jei --console=plain
gradlew.bat :neoforge:runSmokeClient -PrecipeBrowser=jei --console=plain
gradlew.bat :fabric:runSmokeClient -PrecipeBrowser=emi --console=plain
gradlew.bat :neoforge:runSmokeClient -PrecipeBrowser=emi --console=plain
```

`--no-parallel --max-workers=2` bounds Gradle workers. The profiles use separate
`smoke/evidence`, `smoke/jei-evidence` and `smoke/emi-evidence` directories under
each loader's build directory, with corresponding isolated run directories.
Each successful run writes a fresh `PASS.txt` and `browser-checks.txt`; check
their timestamps against the run log and inspect the new screenshots.

Forge's client command is a focused bootstrap check. It verifies the registered
table, shared custom item renderers and additional riichi-stick model, and writes
`forge/build/smoke/bootstrap-evidence/PASS.txt` with a title-screen capture
(`jei-bootstrap-evidence` for the installed JEI profile). It does
not establish multiplayer, inventory or gameplay acceptance. The Forge server
command checks the dedicated launcher and settings path; it does not start a world.

`RecipeBrowserDataSmoke` checks the finite examples and cycling alternatives in
the integrated server world, including retention of spare tiles and point sticks.
The viewer smoke queries component-preserving back dyeing and table upgrades,
checks catalogue order and denominations, and captures one recipe page and one
server-backed case at 320 x 240. Settings use Simplified Chinese and one English
layout; held items and deposits use representative screenshots.

Source/API review and Java compilation alone do not establish installed-viewer,
absent-viewer, dedicated-server or visual acceptance. Use the fresh completion
markers and screenshots for the specific loader and profile being checked.

`gradlew.bat :fabric:runSmokeServer :neoforge:runSmokeServer --console=plain` exercises
the dedicated-server bootstrap with Minecraft's `--initSettings` mode in
isolated build directories. The same optional-viewer profile flag applies.
This covers server-side entrypoint loading and settings initialization; world
simulation and player transactions are exercised by the integrated-server smoke.
The generated EULA setting retains Minecraft's default value.

Validation results are recorded from completed runs rather than API compilation.
Simultaneous installation of multiple viewers and additional Minecraft profiles
require their own runtime acceptance.

## Integration APIs

* [JEI setup for Minecraft 1.21 and 1.21.1](https://github.com/mezz/JustEnoughItems/wiki/Getting-Started-%5BMinecraft-1.21-and-1.21.1%5D)
* [JEI 1.21.1 public API](https://github.com/mezz/JustEnoughItems/tree/1.21.1/CommonApi/src/main/java/mezz/jei/api)
* [JEI Forge 1.21.1 artifact](https://maven.blamejared.com/mezz/jei/jei-1.21.1-forge/19.56.0.441/)
* [EMI 1.21 profile and dependencies](https://github.com/emilyploszaj/emi/tree/1.21)
* [EMI public API](https://github.com/emilyploszaj/emi/tree/1.21/xplat/src/main/java/dev/emi/emi/api)
