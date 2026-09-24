# MChjong

[![Snapshot CI](https://github.com/SkyEye-FAST/mchjong/actions/workflows/snapshot.yml/badge.svg?branch=compat%2F1.20.1)](https://github.com/SkyEye-FAST/mchjong/actions/workflows/snapshot.yml)
[![Release](https://github.com/SkyEye-FAST/mchjong/actions/workflows/release.yml/badge.svg)](https://github.com/SkyEye-FAST/mchjong/actions/workflows/release.yml)

MChjong brings playable mahjong tables into Minecraft, with shared gameplay and
presentation across its supported Fabric, Forge and NeoForge builds.

MChjong supports **three- and four-player riichi mahjong**, featuring
Mahjong Soul, Tenhou, M.League, JPML A and WRC presets with configurable table
options. See [Rules and presets](docs/RULES.md) for details.

## Features

- Ordinary tables with physical tile handling and automatic adjudication, plus upgradeable automatic tables
- Craftable wooden furniture, removable cloth in 16 colors, sixteen tile materials and physical point sticks
- In-world tile interaction, seated and immersive views, keyboard controls and narrated actions
- Configurable rooms, invitations, hand visibility, time controls and training bots
- Animated play and settlement panels with winning hands, scores and final standings
- Private replay archives, step-by-step playback and Tenhou JSON export
- English, Japanese, Simplified Chinese and Traditional Chinese localization
- Resource-pack tile designs and recorded voices
- Optional Create production lines with efficient cutting, printing, dyeing and packing

## Installation

Download the artifact for your Minecraft version and loader from
[Releases](https://github.com/SkyEye-FAST/mchjong/releases), then place the mod JAR
in your Minecraft `mods` folder. Fabric also requires Fabric API.

Development builds are available as loader-specific artifacts from successful
[Snapshot CI runs](https://github.com/SkyEye-FAST/mchjong/actions/workflows/snapshot.yml).
For local builds, see [Building and contributing](docs/DEVELOPMENT.md).

## Compatibility

Current build profiles support these Minecraft versions and loaders:

| Minecraft | Development branch | Java runtime | Loader artifacts | Quilt |
| --- | --- | --- | --- | --- |
| 1.21.1 | `main` | 21 | Fabric, Forge, NeoForge | Uses the Fabric artifact |
| 1.20.1 | `compat/1.20.1` | 17 | Fabric, Forge | Uses the Fabric artifact |
| 26.1.2 | `compat/26.1.2` | 25 | Fabric, NeoForge | Uses the Fabric artifact |

Optional integrations are scoped to the loader named in each cell:

| Integration | 1.21.1 | 1.20.1 | 26.1.2 |
| --- | --- | --- | --- |
| JEI supply recipes | Fabric, Forge, NeoForge | Fabric, Forge | Fabric, NeoForge |
| EMI supply recipes | Fabric, NeoForge | Fabric, Forge | — |
| REI | Fabric, NeoForge: catalogue and component identity | Fabric, Forge: supply recipes | Fabric, NeoForge: catalogue and component identity |
| Ponder tutorials | Fabric, NeoForge | Fabric, Forge | — |
| Create workshop | NeoForge | Fabric, Forge | — |
| Touhou Little Maid players | Fabric, NeoForge | Fabric, Forge | Fabric, NeoForge |

The table describes shipped adapters; installed-mod and gameplay validation are
recorded separately in [Compatibility and verification](docs/COMPATIBILITY.md).
REI's catalogue-only profiles expose item variants and screen exclusion zones.
Fabric uses Create Fabric for Create and Touhou Little Maid: Orihime for Touhou
Little Maid; these ports share their original mod's row. Install the build for
your Minecraft version and loader, not the original mod alongside its port.
An em dash marks a profile without that integration. Quilt reuses Fabric adapters
when the corresponding dependency also supports Quilt.

The current build profile targets **Minecraft 1.20.1** and **Java 17**, using
Fabric Loader 0.19.5 with Fabric API or Forge 47.4.23. Build with JDK 21.

The optional [Create workshop](docs/CREATE.md) supports Create 6.0.8 on Forge
and Create Fabric 6.0.8.1, with shared JEI/EMI displays and Ponder tutorials.

Optional Ponder tutorials cover placement, equipment and seated play. This profile
targets Ponder 1.0.92 with its declared dependencies. Optional recipe
viewers provide component-aware supply recipes. See
[Compatibility and verification](docs/COMPATIBILITY.md) for dependency profiles
and validation coverage.

On 1.20.1, Touhou Little Maid on Forge and Touhou Little Maid: Orihime on
Fabric add a Mahjong task for maids. During work hours, a maid can take an empty
stool at her owner's table and play as a computer opponent.

## Getting started

Craft a table, prepare a complete tile set in a mahjong box, and equip the table
with the box and a cloth. Ordinary tables also require dice and point sticks.
Place stools around the table, sit down, and choose the player count and rule
preset in the lobby.

Follow the [Playing guide](docs/PLAYING.md) for controls and table operation, and
[Survival equipment and recipes](docs/SURVIVAL.md) for crafting and setup.
On a Ponder enabled build, hover over a table or supply item and hold the key
shown in its tooltip to open an animated guide.

## Documentation

- **Players:** [Playing guide](docs/PLAYING.md), [equipment and recipes](docs/SURVIVAL.md),
  [rules](docs/RULES.md), [rooms and permissions](docs/ROOMS.md), [replays](docs/REPLAYS.md)
- **Resource creators:** [Tile assets](docs/ASSETS.md), [audio and voice packs](docs/AUDIO.md)
- **Contributors:** [Building and contributing](docs/DEVELOPMENT.md),
  [architecture](docs/ARCHITECTURE.md), [interface style](docs/UI_STYLE.md),
  [compatibility](docs/COMPATIBILITY.md), [verification](docs/VERIFICATION.md),
  [supply data contracts](docs/SUPPLIES.md)

The [documentation index](docs/README.md) lists all guides and technical references.
Release history is maintained in [CHANGELOG.md](CHANGELOG.md).

## Credits

The built-in tile presets include Kansai and Kanto designs. Kansai includes the
seasons and botanical flowers; Kanto includes the seasons and civil flowers. Built-in
artwork and license notices are documented in [the artwork notice](presets/tile_faces/NOTICE.md)
and [docs/ASSETS.md](docs/ASSETS.md).

## License

MChjong's code is licensed under the [Apache License 2.0](LICENSE).
Third-party artwork retains its separately documented rights and notices.
