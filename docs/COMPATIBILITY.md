# Compatibility and verification

## Development profile

Release artifacts follow their Minecraft build profile. The current recipe
viewer adapters target Minecraft 1.21.1 with Java 21, Fabric Loader 0.19.5 and
Fabric API 0.116.17+1.21.1, or NeoForge 21.1.250. The wider version roadmap remains
mainstream releases from 1.20.1 onward; each release line requires its own
compiled artifact and verification.

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
136-tile cases are consecutive. Blank tiles in all six materials and both mahjong dyes precede blank,
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

The source reviewed for NEI is TheCBProject's archived branch targeting Minecraft
1.12.2 and Forge 14.23.5.2768. Its target belongs to that legacy profile. The
1.21.1 integration described here is specifically JEI and EMI.

## Verification commands

Use the repository wrapper with JDK 21:

```text
gradlew.bat buildAll --warning-mode fail --console=plain
gradlew.bat :fabric:runSmokeClient --console=plain
gradlew.bat :neoforge:runSmokeClient --console=plain
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

`RecipeBrowserDataSmoke` checks the finite examples and cycling alternatives in
the integrated server world, including retention of spare tiles and point sticks.
The viewer smoke queries component-preserving back dyeing and table upgrades,
checks catalogue order and denominations, and captures one recipe page and one
server-backed case at 320 x 240. Settings use Simplified Chinese and one English
layout; held items and deposits use representative screenshots.

Source/API review and Java compilation alone do not establish installed-viewer,
absent-viewer, dedicated-server or visual acceptance. Use the fresh completion
markers and screenshots for the specific loader and profile being checked.

`gradlew.bat :runSmokeServer :neoforge:runSmokeServer --console=plain` exercises
the dedicated-server bootstrap with Minecraft's `--initSettings` mode in
isolated build directories. The same optional-viewer profile flag applies.
This covers server-side entrypoint loading and settings initialization; world
simulation and player transactions are exercised by the integrated-server smoke.
The generated EULA setting retains Minecraft's default value.

Validation results are recorded from completed runs rather than API compilation.
Simultaneous installation of multiple viewers and additional Minecraft profiles
require their own runtime acceptance.

## Upstream API references

* [JEI setup for Minecraft 1.21 and 1.21.1](https://github.com/mezz/JustEnoughItems/wiki/Getting-Started-%5BMinecraft-1.21-and-1.21.1%5D)
* [JEI 1.21.1 public API](https://github.com/mezz/JustEnoughItems/tree/1.21.1/CommonApi/src/main/java/mezz/jei/api)
* [EMI 1.21 profile and dependencies](https://github.com/emilyploszaj/emi/tree/1.21)
* [EMI public API](https://github.com/emilyploszaj/emi/tree/1.21/xplat/src/main/java/dev/emi/emi/api)
* [NEI target profile](https://github.com/TheCBProject/NotEnoughItems/blob/master/build.properties)
