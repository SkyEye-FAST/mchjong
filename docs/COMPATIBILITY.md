# Compatibility and verification

## Minecraft 26.1.2 profile

The `compat/26.1.2` branch builds Fabric and NeoForge artifacts with JDK 25.
Fabric requires Loader 0.19.5 or newer and Fabric API 0.155.3+26.1.2. NeoForge
requires 26.1.2.109 or newer. Versions are pinned in `gradle.properties`.

The shared engine, gameplay, menus, rendering and translations follow `main`.
The port adapts Minecraft and loader APIs at their boundaries. Snapshot CI builds
both loader artifacts from this branch. A release uses the reviewed port revision
pinned by `main` and gives both JARs the release version.

The [README compatibility tables](../README.md#compatibility) cover all three
version lines. Quilt consumes the matching Fabric artifact; its 26.1.2 runtime
validation is separate from Fabric acceptance.

## Optional recipe viewers

JEI 29.40.0.102 with MezzConfig 0.6.3 is the installed development profile on
both loaders. Run client checks with `-PrecipeBrowser=jei`; the default profile
uses `-PrecipeBrowser=none`. The adapter and shared engine are packaged in
MChjong, while the optional viewer itself remains a separate dependency.

REI 26.1.819 provides catalogue entries, component identity and screen exclusion
zones on both loaders. This is a catalogue adapter, distinct from JEI's custom
recipe pages and installed-viewer smoke profile.

The upstream Create, Create Fabric, Ponder, EMI, Touhou Little Maid and Orihime
projects do not publish a matching 26.1.2 release in their current Modrinth
version lists. Their adapters and dependencies are excluded from this build
profile. Independent ports such as Create Fly are separate integration targets;
this profile does not claim compatibility with them.

Create, Ponder, EMI and maid adapters are omitted from this profile because
their matching released dependencies are not available for the selected
Minecraft/loader versions. Their absence does not remove shared supply
transformations, companion identity or server-side rule behavior.

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
a release. Each port branch keeps its own dependency versions and validation
guide, while the engine, item transformations and player-facing behavior follow
the reviewed main revision. Preserve the 26.1.2 render-state, special-model,
input-event and registry APIs when adapting shared changes.

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

The batch synchronized from `main` at `a5d6852` includes paused unattended
matches, the last-player leave decision, supply-aware rule selection, dice and
wall presentation, supply transformations, localization and formatting policy.
Its 26.1.2 adaptation also uses native string-tag recipe ingredients for the
wooden slabs in the mahjong box recipe.

On 2026-09-24, `buildAll --warning-mode fail` passed. Full Fabric and NeoForge
client checks with JEI each produced fresh `PASS.txt`, `survival-checks.txt` and
`browser-checks.txt` under `build/smoke/jei-evidence`. The runs cover live control
packets, pause/rejoin decisions, ordinary-table play, rule selection, resource
reloads and installed recipe pages. Fresh leave-decision and small-window rule
screenshots were inspected on both loaders. This batch does not establish
Quilt runtime acceptance or rerun every optional dependency profile.

Use the checked-in Gradle wrapper with JDK 25 (`gradlew.bat` on Windows):

```text
./gradlew buildAll --warning-mode fail --console=plain
./gradlew :fabric:runSmokeClient --console=plain
./gradlew :neoforge:runSmokeClient --console=plain
./gradlew :fabric:runSmokeClient -PsmokePalette=true --console=plain
./gradlew :neoforge:runSmokeClient -PsmokePalette=true --console=plain
./gradlew :fabric:runSmokeClient -PsmokeManual=true --console=plain
./gradlew :neoforge:runSmokeClient -PsmokeManual=true --console=plain
./gradlew :fabric:runSmokeClient -PsmokeBrowser=true -PrecipeBrowser=jei --console=plain
./gradlew :neoforge:runSmokeClient -PsmokeBrowser=true -PrecipeBrowser=jei --console=plain
```

Smoke profiles write screenshots and PASS markers under each loader's
`build/smoke/<profile>-evidence`. Check their timestamps and inspect the fresh
captures for the changed workflow. `buildAll` includes the engine, generated
assets and data, and loader tests; client runs exercise the integrated server,
packets, inventory and rendering.

The baseline port at `b5d4847` records a passing JDK 25 build, full Fabric and
NeoForge client smokes, palette captures, physical manual-table handling and
installed-JEI browser smokes. Run the owning checks again when synchronizing changed flows;
baseline screenshots are not visual acceptance of a later revision.

The Fabric and NeoForge dedicated-server launchers can be checked with
`./gradlew :fabric:runSmokeServer :neoforge:runSmokeServer --console=plain`.
This starts each server in its isolated settings directory.
