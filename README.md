# MCjhong

MCjhong is a Minecraft mod that adds a seated, in-world riichi mahjong table for
three or four players. It supports both Fabric and NeoForge.

## Features

- Placeable mahjong tables, stools, and tiles
- Three- and four-player riichi mahjong gameplay
- In-world drawing, discarding, melds, and action previews
- Shared gameplay and client presentation across both loaders
- English, Japanese, Simplified Chinese, and Traditional Chinese localization
- Optional patterned tile-back resource pack

## Compatibility

The compatibility target covers mainstream Minecraft releases from 1.20.1
onward. Release artifacts are version-specific because Minecraft and loader APIs
change between release lines; use the artifact built for your Minecraft version.

The current development profile is:

| Platform | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21 or newer |
| Fabric Loader | 0.16.10 or newer, plus Fabric API |
| NeoForge | 21.1.250 or newer |

## Installation

Build the JAR for your loader as described below, then copy it to the
Minecraft `mods` folder:

- Fabric: `build/libs/mchjong-fabric-*.jar`
- NeoForge: `neoforge/build/libs/mchjong-neoforge-*.jar`

Install the matching loader and its required dependencies before launching the
game. Fabric installations also require Fabric API.

## Building from source

Requires JDK 21 or newer.

```bash
git clone https://github.com/SkyEye-FAST/mchjong.git
cd mchjong
```

The default `main` branch contains both Fabric and NeoForge support. Minecraft,
loader, mappings, and Java versions are selected by the build profile in
`gradle.properties`.

### Fabric

```bash
./gradlew build
./gradlew runClient   # optional development client
./gradlew runServer   # optional dedicated server
```

### NeoForge

```bash
./gradlew :neoforge:build
./gradlew :neoforge:runClient   # optional development client
./gradlew :neoforge:runServer   # optional dedicated server
```

To build both loaders and run the shared checks:

```bash
./gradlew buildAll
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Optional resource pack

Tile backs are solid teal by default. Enable **MCjhong: Patterned tile backs**
under **Options > Resource Packs** to use the built-in diamond pattern. Disable
the pack to restore the default backs. See [docs/ASSETS.md](docs/ASSETS.md) for
customization and asset details.

## Credits

Tile faces are generated from [FluffyStuff's riichi-mahjong-tiles](https://github.com/FluffyStuff/riichi-mahjong-tiles),
released under CC0. See [NOTICE](NOTICE) and [docs/ASSETS.md](docs/ASSETS.md)
for attribution and the asset generation contract.

## License

MCjhong is licensed under the [Apache License 2.0](LICENSE).
