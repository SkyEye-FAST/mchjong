# Compatibility and dependencies

## Minecraft 1.20.1 profile

`compat/1.20.1` builds Fabric and Forge artifacts with JDK 21; mod classes and
the Minecraft runtime target Java 17. The pinned profile uses Fabric Loader
0.19.5, Fabric API 0.92.12+1.20.1 and Forge 47.4.23. Quilt consumes the Fabric JAR.
The [README tables](../README.md#compatibility) summarize all three version lines.

Branch synchronization and release pinning are described in
[Development](DEVELOPMENT.md#version-synchronization-and-artifacts).
For native NBT and version boundaries, see [Supply data contracts](SUPPLIES.md)
and the [port guide](PORT_1.20.1.md).

## Optional integrations

| Integration | Pinned distribution | Development profile |
| --- | --- | --- |
| JEI | 15.59.0.212, Fabric or Forge | `-PrecipeBrowser=jei` |
| EMI | 1.1.24+1.20.1, Fabric or Forge | `-PrecipeBrowser=emi` |
| REI | 12.0.684, with Cloth Config and Architectury | `-PrecipeBrowser=rei` |
| Ponder | 1.0.92 | `-PwithPonder=true` |
| Create | Forge 6.0.8 / Create Fabric 6.0.8.1 | `-PwithCreate=true` |
| Touhou Little Maid | Forge 1.5.3 / Orihime 0.8.2-forge1.5.3+mc1.20.1 | `-PwithMaid=true` |
| Base installation | No optional runtime | `-PrecipeBrowser=none` |

Exact coordinates are in `gradle.properties`. Optional APIs are compile-only;
development flags add the matching runtime. MChjong bundles its own adapters
and shared engine, not the optional mods. Cross-loader distributions share their
parent integration's table row.

### Recipe viewers and Create

JEI, EMI and REI expose the same finite supply recipe examples on both loaders.
Recipe-relevant identity includes complete native NBT and container contents;
see [Supply data contracts](SUPPLIES.md#recipe-catalogue-and-identity).

### Patchouli handbook

The 1.20.1 Fabric and Forge profiles use Patchouli 1.20.1-85, supplied to
development runs with `-PwithPatchouli=true`. The public client API opens
`mchjong:guide`; book definitions and localized content are shared resources.
The dependency is compile-only and is not bundled. See the
[handbook controls](PLAYING.md#in-game-handbook) and
[focused checks](VERIFICATION.md#patchouli-handbook).

### Create workshop

The [Create workshop](CREATE.md) reuses shared printing, dyeing, marking and
packing operations. Forge uses item capabilities and strict-NBT ingredients;
Fabric uses transactional storage and Fabric API's strict-NBT ingredients.
The matching Create distribution is installed on the server and clients.
The integration adds animated JEI/EMI/REI workshop examples and Ponder tutorials.

### Maid players

Forge uses `1.5.3-forge+mc1.20.1`; Fabric uses Orihime
`0.8.2-forge1.5.3+mc1.20.1`. The Fabric development profile also resolves Forge
Config API Port 8.0.3, Cardinal Components 5.2.3, Porting Lib 2.3.8+1.20.1,
Reach Entity Attributes 2.4.0 and Nashorn 15.4, including libraries normally
nested in Orihime's release JAR. Install the matching mod and its dependencies
on the server and clients. [Rooms](ROOMS.md#maid-players) explains recruitment,
seating, saved bindings and cleanup.

## Verification

Commands, test ownership, baseline results and current acceptance are recorded
in [Verification and evidence](VERIFICATION.md). Installed-mod validation is
separate from compilation and base-client acceptance.

## Integration APIs

* [JEI 1.20.1 public API](https://github.com/mezz/JustEnoughItems/tree/1.20.1/CommonApi/src/main/java/mezz/jei/api)
* [EMI 1.20.1 source](https://github.com/emilyploszaj/emi/tree/1.20.1)
* [Orihime 1.20.1 source](https://github.com/Sh1roCu/TouhouLittleMaid-Orihime/tree/1.20.1)
