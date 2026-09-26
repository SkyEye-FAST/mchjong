# Minecraft 1.20.1 port boundaries

`main` owns feature development; this branch adapts shared changes to Fabric
and Forge on Minecraft 1.20.1. Quilt consumes the matching Fabric artifact.
Build with JDK 21; production classes target Java 17. Fabric publishes a remapped
JAR, and Forge a reobfuscated JAR through ModDevGradle's legacy Forge profile.

The version boundary consists of native ItemStack NBT, recipe serializers,
payload buffers, loader registration and client APIs. Fabric and Forge share
gameplay, rendering and item transformations through `common`. Keep the engine
independent of Minecraft. [Supply data contracts](SUPPLIES.md) define NBT keys,
canonical defaults, container slots and recipe identity.

Optional mod versions and runtime dependencies belong in
[Compatibility](COMPATIBILITY.md). Shared viewer plugins use loader-specific
discovery; Create inventory access uses Forge capabilities or Fabric transactions.
Neither adapter duplicates the shared supply transformations.

Build commands and release-pin updates belong in [Development](DEVELOPMENT.md).
Reusable focused validation commands belong in [Verification](VERIFICATION.md).
