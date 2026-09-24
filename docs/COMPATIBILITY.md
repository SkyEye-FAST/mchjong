# Compatibility and dependencies

## Development profile

The [README compatibility tables](../README.md#compatibility) list loader
artifacts and shipped optional adapters across all three version lines. The
dependency pins and commands below describe the active 1.21.1 profile.

Artifacts follow their Minecraft build profile. `main` targets 1.21.1 with
Java 21, Fabric Loader 0.19.5 and Fabric API 0.116.17+1.21.1, Forge 52.1.16,
or NeoForge 21.1.250. Compatibility branches produce their own version-scoped
artifacts.

Runtime results and their exact scope are recorded in
[Verification](VERIFICATION.md#cross-version-acceptance). The README tables list
supported adapters, while each compatibility branch pins its own dependencies.

JEI profiles cover Fabric and NeoForge 26.1.2; Fabric, Forge and NeoForge
1.21.1; and Fabric and Forge 1.20.1. EMI covers Fabric and NeoForge 1.21.1 and
Fabric and Forge 1.20.1. REI covers Fabric and NeoForge 1.21.1 and 26.1.2, plus
Fabric and Forge 1.20.1. Optional Ponder tutorials cover Fabric and NeoForge
1.21.1 and both 1.20.1 loaders.

Branch synchronization and release artifact selection are documented in
[Development](DEVELOPMENT.md#version-synchronization-and-artifacts).

## Optional integrations

| Optional viewer | Pinned version | Development runtime |
| --- | --- | --- |
| JEI | 19.56.0.441 | `-PrecipeBrowser=jei`, with MezzConfig 0.5.6 |
| EMI | 1.1.24+1.21.1 | `-PrecipeBrowser=emi` |
| REI | 16.0.799 | `-PrecipeBrowser=rei` |
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

The `compat/1.20.1` artifacts provide the same workshop with Create 6.0.8 on
Forge and Create Fabric 6.0.8.1 on Fabric. Production code targets Java 17.
Native machine transaction checks, installed recipe viewers and four-language
Ponder playback pass on both loaders; both also pass the Create-absent client
profile. The release pin selects the validated compatibility batch.

### Maid players

The 26.1.2 branch supports pinned Touhou Little Maid and Orihime test builds on
NeoForge and Fabric. Its [compatibility guide](https://github.com/SkyEye-FAST/mchjong/blob/compat/26.1.2/docs/COMPATIBILITY.md)
records the exact upstream release assets, SHA-256 digests and Forge Config API
Port dependency. These are optional prereleases, not bundled parts of MChjong.

The 1.20.1 compatibility branch provides the same task through Touhou Little
Maid 1.5.3-forge+mc1.20.1 on Forge and Orihime
0.8.2-forge1.5.3+mc1.20.1 on Fabric. Its own compatibility guide records the
Java 17 runtime dependencies and loader-specific verification commands.

The 1.21.1 profile integrates Touhou Little Maid 1.5.3-neoforge+mc1.21.1 on
NeoForge and Touhou Little Maid: Orihime 0.8.2-neo1.5.3+mc1.21.1 on Fabric.
The Fabric runtime also uses Forge Config API Port 21.1.3. Install the matching
mod on the server and clients. See [Maid players](ROOMS.md#maid-players) for
recruitment, seating and cleanup.

Both integrations share `compat/maid` and the existing server-owned training AI.
The maid APIs are compile-only; `-PwithMaid=true` supplies the matching development
runtime. See [Maid verification](VERIFICATION.md#maid-integration).

## Verification and data contracts

Commands, test ownership and acceptance evidence are maintained in
[Verification](VERIFICATION.md). The finite recipe catalogue and component
identity rules are documented in [Supply data contracts](SUPPLIES.md).

## Integration APIs

* [JEI setup for Minecraft 1.21 and 1.21.1](https://github.com/mezz/JustEnoughItems/wiki/Getting-Started-%5BMinecraft-1.21-and-1.21.1%5D)
* [JEI 1.21.1 public API](https://github.com/mezz/JustEnoughItems/tree/1.21.1/CommonApi/src/main/java/mezz/jei/api)
* [JEI Forge 1.21.1 artifact](https://maven.blamejared.com/mezz/jei/jei-1.21.1-forge/19.56.0.441/)
* [EMI 1.21 profile and dependencies](https://github.com/emilyploszaj/emi/tree/1.21)
* [EMI public API](https://github.com/emilyploszaj/emi/tree/1.21/xplat/src/main/java/dev/emi/emi/api)
