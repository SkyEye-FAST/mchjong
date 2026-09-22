# Minecraft 1.20.1 port

`main` remains the only feature development branch. `compat/1.20.1` adapts stable
main batches to Fabric and Forge. Keep the engine and gameplay changes synchronized
through Git; version-specific edits belong at the Minecraft and loader boundaries.
Quilt uses the Fabric artifact.

## Current validation

The Fabric remapped JAR and Forge reobfuscated JAR build with the checked-in wrapper
and JDK 21. Main mod classes target Java 17. Fabric and Forge 47.4.23 reach the title screen;
the bootstrap checks registration, the item renderer, the additional model, native
NBT default normalization and generated recipe output. The generated asset contract
checks pass. Both loaders also pass the shared integrated-server/client gameplay
smoke: inventory authorization, native NBT, placement and destruction, randomized
seating, private hands, control packets, rules, replay export and four-language UI.
The recipe contract checks 724 examples and 10,804 ingredient alternatives.

```text
./gradlew :fabric:remapJar :forge:reobfJar
./gradlew :forge:runSmokeClient
./gradlew :forge:runSmokeClient -PsmokeBootstrap=true
./gradlew :art:test --tests '*AssetContractTest'
```

The port uses 1.20.1 native ItemStack NBT, recipe serializers and payload buffers.
Fabric and Forge share these implementations through `common`.

## Recipe viewers

The shared adapters compile against JEI 15.59.0.212, EMI 1.1.24+1.20.1 and
REI 12.0.684 for both loaders. Their APIs are compile-only; the optional
`-PrecipeBrowser=jei`, `-PrecipeBrowser=emi` and `-PrecipeBrowser=rei` development
profiles load the corresponding runtime. The default `none` profile loads no viewer.
The REI profile includes Cloth Config and Architectury, while Forge discovers the
shared plugin through a loader-specific annotation entry point.

Forge uses ModDevGradle's native dependency remapping configurations. The adapters
use 1.20.1 recipe objects and native NBT identity, preserving the existing shared
recipe examples and server-authorized crafting behavior.

All three Forge viewer profiles pass the bootstrap smoke. Fabric loads each viewer in
a local world: JEI registers the supply recipes and displays its inventory overlay;
EMI displays the material variants and the oak table crafting recipe. REI displays
the shared catalog's material and color variants using native NBT comparison.
Fabric with JEI and Forge with EMI also pass the full shared smoke, including
real inventory transfers, printing and dyeing, recipe lookups, catalog order,
denominations, and recipe-page and small-container screenshots. Viewer autofill
buttons and simultaneous installation of multiple viewers are outside this smoke.

## Ponder and Quilt

Ponder 1.0.92 is available with `-PwithPonder=true` on both loaders. Both profiles
pass seven entries, three storyboards, four languages, reload, playback and replay
restoration. Normal and small-window screenshots are saved under each loader's
`build/smoke/ponder-evidence`; see [PONDER.md](PONDER.md).

Quilt Loader 0.30.1 loads the Fabric release JAR on Java 17 and reaches the title
screen. Gameplay and packet transactions are covered by the Fabric and Forge
integrated-server smokes.

Release tags and publication belong to `main`. Its pinned compatibility revision
supplies both 1.20.1 JARs to the five-artifact snapshot and release workflows.
Advance that reviewed pin after stable port batches; a port batch need not follow
every individual main commit.
