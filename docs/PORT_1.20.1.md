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

## Remaining port work

- Adapt the existing shared Fabric smoke sources to the 1.20.1 APIs and validate
  client/world/network interactions on both loaders.
- Port and verify optional JEI, EMI, REI and Ponder integrations using their actual
  1.20.1 artifacts. The current base JARs exclude these integration classes; the
  inherited optional development profiles are not yet supported.
- Update CI and release assembly to collect the three main artifacts and the two
  artifacts from a reviewed, pinned compatibility commit.
- Validate Quilt with the corresponding Fabric JAR and the Java 17 runtime.

Do not publish this bootstrap milestone as complete compatibility support.
