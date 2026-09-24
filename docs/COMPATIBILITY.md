# Compatibility and dependencies

## Minecraft 26.1.2 profile

`compat/26.1.2` builds Fabric and NeoForge artifacts with JDK 25. Fabric requires
Loader 0.19.3 or newer and Fabric API; the development profile pins Loader 0.19.5
and Fabric API 0.155.3+26.1.2. NeoForge requires 26.1.2.109 or newer.
Quilt Loader 0.30.1 consumes the packaged Fabric artifact without a separate mod
module. Its Fabric compatibility layer reports 0.19.3, matching the runtime floor.

The [README tables](../README.md#compatibility) cover all three version lines.
Branch synchronization, Java boundaries and release pins are documented in
[Development](DEVELOPMENT.md#version-synchronization-and-artifacts).

## Optional integrations

| Integration | Pinned distribution | Development profile |
| --- | --- | --- |
| JEI | 29.40.0.102, with MezzConfig 0.6.3 | `-PrecipeBrowser=jei` |
| REI | 26.1.819, catalogue/identity adapter | Compile-only API; install separately |
| Touhou Little Maid | NeoForge 2.0.0 / Orihime 1.0.0, pinned test builds below | `-PwithMaid=true` |
| Base installation | No optional runtime | `-PrecipeBrowser=none` |

JEI provides custom supply recipe pages on both loaders. REI exposes catalogue
entries, component identity and screen exclusion zones. Its catalogue adapter is
distinct from a custom recipe-page implementation. REI's compile classpath uses
Architectury 20.0.6 and Basic Math 0.6.1.

Optional APIs remain compile-only. MChjong packages its own adapters and shared
engine; optional mods are installed separately. Current integration scope is
listed in the README, rather than inferred from an upstream source branch.

## Maid test builds

The following upstream prereleases were tested together with the existing maid
fixture. Original and Fabric-port distributions occupy the same compatibility
row; install only the distribution for your loader, on the server and clients.

| Loader | Distribution | Exact upstream build |
| --- | --- | --- |
| Fabric | Touhou Little Maid: Orihime `1.0.0-neo2.0.0+mc26.1.2` | [26.1.2-snapshot-2026-08-24-21-30-49](https://github.com/Sh1roCu/TouhouLittleMaid-Orihime/releases/tag/26.1.2-snapshot-2026-08-24-21-30-49) |
| NeoForge | Touhou Little Maid `2.0.0-neoforge+mc26.1.2-snapshot` | [snapshot-2026-08-24-14-04-03](https://github.com/TouhouLittleMaid/TouhouLittleMaid-26.1/releases/tag/snapshot-2026-08-24-14-04-03) |

Fabric also requires Forge Config API Port 26.1.4. Gradle resolves the exact
GitHub release assets through restricted Ivy repositories and verifies their
SHA-256 digests before compiling. The pins in `gradle.properties` are:

```text
Orihime: 9503be53f46a01c31ee5fd9bc2fc64f72b02db9860d4433eb0f5de9cbb086dd6
Touhou Little Maid: b0105bb9b1fd02234024bc9e371a77dfe1631bf795cb98fd7fc7198a52e2fe29
```

These are prerelease dependencies, not a claim that every later snapshot is
compatible. Update a pin only after API review and installed/absent-profile
verification. [Rooms](ROOMS.md#maid-players) covers player operation;
[Architecture](ARCHITECTURE.md) covers persistent attachment boundaries.

## Verification and data contracts

[Verification](VERIFICATION.md#recorded-acceptance) records the tested revisions,
Quilt artifact checksum, runtime commands and evidence. [Supply data contracts](SUPPLIES.md)
defines components, persistence and recipe identity. Compilation alone does not
establish installed-viewer, Quilt or visual acceptance.
