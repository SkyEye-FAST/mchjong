# Compatibility and verification

## Development profile

Release artifacts follow their Minecraft build profile. The current recipe
viewer adapters target Minecraft 1.20.1 with Java 17, Fabric Loader 0.19.5 and
Fabric API 0.92.12+1.20.1, or Forge 47.4.23. Builds use JDK 21 and target Java 17.
See [PORT_1.20.1.md](PORT_1.20.1.md) for synchronization with the feature mainline.

| Minecraft | Loader artifacts | Validation scope |
| --- | --- | --- |
| 1.20.1 | Fabric and Forge on `compat/1.20.1` | Shared integrated-server/client gameplay and Ponder smokes pass |
| 1.20.1 | Quilt consumes the Fabric artifact | Quilt Loader 0.30.1 loads the packaged JAR on Java 17 to the title screen |

The optional viewer and Ponder profiles below apply to Fabric and Forge 1.20.1.

## Optional integrations

| Optional viewer | Pinned version | Development runtime |
| --- | --- | --- |
| JEI | 15.59.0.212 | `-PrecipeBrowser=jei` |
| EMI | 1.1.24+1.20.1 | `-PrecipeBrowser=emi` |
| REI | 12.0.684 | `-PrecipeBrowser=rei`, with Cloth Config and Architectury |
| Ponder | 1.0.92 | `-PwithPonder=true` |
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
gradlew.bat :forge:runSmokeClient --console=plain
gradlew.bat :forge:runSmokeServer --console=plain
gradlew.bat :fabric:runSmokeClient -PrecipeBrowser=jei --console=plain
gradlew.bat :forge:runSmokeClient -PrecipeBrowser=jei --console=plain
gradlew.bat :fabric:runSmokeClient -PrecipeBrowser=emi --console=plain
gradlew.bat :forge:runSmokeClient -PrecipeBrowser=emi --console=plain
```

`--no-parallel --max-workers=2` bounds Gradle workers. The profiles use separate
`smoke/evidence`, `smoke/jei-evidence` and `smoke/emi-evidence` directories under
each loader's build directory, with corresponding isolated run directories.
Each successful run writes a fresh `PASS.txt` and `browser-checks.txt`; check
their timestamps against the run log and inspect the new screenshots.

Both client commands run the shared gameplay checks. Add `-PsmokeBootstrap=true`
to Forge for the focused registration, renderer and native NBT bootstrap check.
The server command checks the dedicated launcher and settings path; integrated
server runs exercise worlds and player transactions.

`RecipeBrowserDataSmoke` checks the finite examples and cycling alternatives in
the integrated server world, including retention of spare tiles and point sticks.
The viewer smoke queries component-preserving back dyeing and table upgrades,
checks catalogue order and denominations, and captures one recipe page and one
server-backed case at 320 x 240. Settings use Simplified Chinese and one English
layout; held items and deposits use representative screenshots.

Source/API review and Java compilation alone do not establish installed-viewer,
absent-viewer, dedicated-server or visual acceptance. Use the fresh completion
markers and screenshots for the specific loader and profile being checked.

`gradlew.bat :fabric:runSmokeServer :forge:runSmokeServer --console=plain` exercises
the dedicated-server bootstrap with Minecraft's `--initSettings` mode in
isolated build directories. The same optional-viewer profile flag applies.
This covers server-side entrypoint loading and settings initialization; world
simulation and player transactions are exercised by the integrated-server smoke.
The generated EULA setting retains Minecraft's default value.

Validation results are recorded from completed runs rather than API compilation.
Fabric with JEI and Forge with EMI pass the full shared smoke. Both loaders also
pass the installed-Ponder profile. REI has bootstrap and catalog inspection
coverage. Automated verification covers individual viewer profiles, recipe queries,
catalog inspection, and gameplay integration. EMI's development mode reports its
own synthetic `emi:brewing/` recipes as absent from the vanilla recipe manager;
the MChjong recipe checks pass without EMI recipe diagnostics for MChjong entries.

## Integration APIs

* [JEI 1.20.1 public API](https://github.com/mezz/JustEnoughItems/tree/1.20.1/CommonApi/src/main/java/mezz/jei/api)
* [EMI 1.20.1 source and dependencies](https://github.com/emilyploszaj/emi/tree/1.20.1)
