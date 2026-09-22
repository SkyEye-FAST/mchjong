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
checks pass. This is bootstrap coverage; multiplayer and complete gameplay acceptance
are still pending.

```text
./gradlew :fabric:remapJar :forge:reobfJar
./gradlew :forge:runSmokeClient
./gradlew :art:test --tests '*AssetContractTest'
```

The port uses 1.20.1 native ItemStack NBT, recipe serializers and payload buffers.
Fabric and Forge share these implementations through `common`.

## Recipe viewers

The shared JEI and EMI adapters compile against JEI 15.59.0.212 and EMI
1.1.24+1.20.1 for both loaders. Their APIs are compile-only; the optional
`-PrecipeBrowser=jei` and `-PrecipeBrowser=emi` development profiles load the
corresponding runtime. The default `none` profile loads neither viewer.

Forge uses ModDevGradle's native dependency remapping configurations. The adapters
use 1.20.1 recipe objects and native NBT identity, preserving the existing shared
recipe examples and server-authorized crafting behavior.

Both Forge viewer profiles pass the bootstrap smoke. Fabric loads each viewer in
a local world: JEI registers the supply recipes and displays its inventory overlay;
EMI displays the material variants and the oak table crafting recipe. These checks
do not yet cover recipe transfer or inventory transactions.

## Remaining port work

- Adapt the existing shared Fabric smoke sources to the 1.20.1 APIs and validate
  client/world/network interactions on both loaders.
- Complete JEI recipe-page and both viewers' inventory interaction checks; port
  REI and Ponder using their actual 1.20.1 artifacts. REI and Ponder adapters are
  currently excluded, and the inherited Ponder development profile is not supported.
- Advance the main workflow's reviewed compatibility pin after stable port batches.
  Main snapshot and release assembly already collects five artifacts.
- Validate Quilt with the corresponding Fabric JAR and the Java 17 runtime.

Do not publish this bootstrap milestone as complete compatibility support.
