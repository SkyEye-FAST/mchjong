# Compatibility and verification

## Development profile

Artifacts follow their Minecraft build profile. `main` targets 1.21.1 with
Java 21, Fabric Loader 0.19.5 and Fabric API 0.116.17+1.21.1, Forge 52.1.16,
or NeoForge 21.1.250. Compatibility branches produce their own version-scoped
artifacts.

| Minecraft | Loader artifacts | Validation scope |
| --- | --- | --- |
| 26.1.2 | Fabric and NeoForge on `compat/26.1.2` | JDK 25 build, full integrated client smokes, palette captures, physical manual-table handling and installed JEI checks pass on both loaders |
| 1.21.1 | Fabric, Forge, NeoForge | Forge has dedicated loader bootstrap checks; full gameplay acceptance remains loader-specific |
| 1.20.1 | Fabric and Forge on `compat/1.20.1` | Shared integrated-server/client gameplay and Ponder smokes pass |
| 1.21.1 and 1.20.1 | Quilt consumes the corresponding Fabric artifact | Quilt Loader 0.30.1 loads both packaged JARs to the title screen, using Java 21 and Java 17 respectively |

JEI profiles cover Fabric and NeoForge 26.1.2; Fabric, Forge and NeoForge
1.21.1; and Fabric and Forge 1.20.1. EMI covers Fabric and NeoForge 1.21.1.
On 1.20.1, EMI and REI cover Fabric and Forge. Optional Ponder tutorials cover
Fabric and NeoForge 1.21.1 and both 1.20.1 loaders.

## Version synchronization and artifacts

`main` is the sole feature development line. Synchronize stable batches into
`compat/1.20.1` and `compat/26.1.2`, adapt Minecraft and loader APIs, then
validate each port. Engine and shared gameplay changes travel through Git history.

The release workflow on `main` builds its three loader artifacts, the two
1.20.1 artifacts from `.github/compat-1.20.1-ref`, and the two 26.1.2 artifacts
from `.github/compat-26.1.2-ref`.
Snapshot workflows build single-version development artifacts for their respective
branch profile. Update each pin after reviewing and validating a stable port batch. All
checkouts receive the same mod version during assembly; the filenames include
Minecraft version and loader. A main commit does not automatically advance the
compatibility pins. Review feature parity and port acceptance before publishing
a release. Quilt uses the validated 1.21.1 and 1.20.1 Fabric JARs.

The 1.20.1 port uses native ItemStack NBT, recipe serializers, packet buffers and
client APIs. JEI 15.59.0.212, EMI 1.1.24+1.20.1, REI 12.0.684 and Ponder 1.0.92
have loader-specific development profiles; optional dependencies stay outside
MChjong's release bundles. Each port branch records its validation in its own
compatibility guide.

## Optional integrations

| Optional viewer | Pinned version | Development runtime |
| --- | --- | --- |
| JEI | 19.56.0.441 | `-PrecipeBrowser=jei`, with MezzConfig 0.5.6 |
| EMI | 1.1.24+1.21.1 | `-PrecipeBrowser=emi` |
| Base installation | Current build profile | `-PrecipeBrowser=none` (default) |

All dependency versions live in `gradle.properties`. Viewer API dependencies are
compile-only. Each optional profile adds its viewer to the development runtime;
distributed MChjong jars contain the MChjong adapters and the shared engine.

Forge selects JEI from the shared viewer flag and otherwise stays on its base
profile, so configuring Forge does not prevent an EMI run on another loader.
`-PforgeRecipeBrowser=none` or `jei` can override its profile explicitly.

### Create workshop

Minecraft 1.21.1 NeoForge integrates Create 6.0.10 as an optional server/client
dependency. `-PwithCreate=true` supplies it to development runs. Mechanical
cutting, mixing, printing, red-five application and packing reuse the shared
item transformations. The reusable printing plate keeps its face preset in the
existing component. See [Create workshop](CREATE.md) for the production line,
recipe viewers and focused validation commands.

### Maid players

The 1.21.1 profile integrates Touhou Little Maid 1.5.3-neoforge+mc1.21.1 on
NeoForge and Touhou Little Maid: Orihime 0.8.2-neo1.5.3+mc1.21.1 on Fabric.
The Fabric runtime also uses Forge Config API Port 21.1.3. Install the matching
maid mod on the server and clients, then select **Mahjong** in the maid's task
selector. Sit at an equipped table with an empty stool and keep the maid nearby
within her work area. During work hours she approaches the stool and joins the room.
Choose the player count before recruiting maids; the room host can fill remaining
places with training bots and adjust each maid's difficulty with the bot controls.

Maids retain their names and move with their assigned seats. Their saved task data
restores the physical mount after a world reload. Changing tasks, dismissing a maid
from the room, or removing her stool releases the physical seat. During an active
match a training bot continues the vacated place. Room dismissal returns the maid
to Idle; select Mahjong again to recruit her for another room.

Both integrations share `compat/maid` and the existing server-owned training AI.
The maid APIs are compile-only; `-PwithMaid=true` supplies the matching development
runtime. Run the focused checks with:

```text
gradlew.bat :fabric:runSmokeClient -PwithMaid=true --console=plain
gradlew.bat :neoforge:runSmokeClient -PwithMaid=true --console=plain
```

Each run writes fresh completion markers and normal/small-window captures to
the loader's `build/smoke/maid-evidence`. The shared fixture checks task discovery,
real brain-driven seating, saved entity recovery, assigned mounts, legal computer
play and cleanup after a task change.

## Recipe and component coverage

The default catalogue uses `MahjongCatalog` on both loaders. Empty and complete
144-tile cases are consecutive. Blank tiles in all sixteen materials and both
mahjong dyes precede blank, 100, 1,000, 5,000 and 10,000 point-stick
denominations. Tables and automatic tables list every wood species. Tile faces,
including flowers, share the blank-tile item and are printed through the box menu.

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
The ordinary five-color mahjong-dye recipe uses the viewers' vanilla crafting category.
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
