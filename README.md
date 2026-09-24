# MChjong

[![Snapshot CI](https://github.com/SkyEye-FAST/mchjong/actions/workflows/snapshot.yml/badge.svg?branch=main)](https://github.com/SkyEye-FAST/mchjong/actions/workflows/snapshot.yml)
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

| Minecraft | Java | Loader artifacts |
| --- | --- | --- |
| 26.1.2 | 25 | Fabric, NeoForge |
| 1.21.1 | 21 or newer | Fabric, Forge, NeoForge |
| 1.20.1 | 17 | Fabric, Forge |

The 26.1.2 development builds come from `compat/26.1.2`; 1.20.1 builds come
from `compat/1.20.1`. Quilt has been validated with the 1.21.1 and 1.20.1
Fabric artifacts. The 1.21.1 profile requires Fabric Loader 0.16.10 or newer
with Fabric API, Forge 52.1.16 or newer, or NeoForge 21.1.250 or newer.

On 1.21.1 and 1.20.1, optional Ponder tutorials cover placement, equipment and
seated play. Optional recipe viewers provide component-aware supply recipes. See
[Compatibility and verification](docs/COMPATIBILITY.md) for dependency profiles
and validation coverage.

On Minecraft 1.21.1 NeoForge, the optional [Create workshop](docs/CREATE.md)
automates supply production without opening the mahjong box menu.

On 1.21.1, Touhou Little Maid on NeoForge and Touhou Little Maid: Orihime on
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
  [compatibility and verification](docs/COMPATIBILITY.md)

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
