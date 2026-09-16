# MCjhong

Mahjong mod for Minecraft 1.21.1.

## Mod Information

- **Mod ID**: `mchjong`
- **Mod Name**: `MCjhong`
- **Target Minecraft Version**: `1.21.1`
- **Java Requirement**: `Java 21` (LTS)
- **Base Package**: `top.skyeyefast.mchjong`
- **Author**: `SkyEye_FAST`
- **License**: Apache-2.0

---

## Branch Structure

The loader branches retain separate release histories. Both use the same
multi-project checkout: the root project builds Fabric, and `neoforge` builds
NeoForge from the shared `common`, `engine` and `art` sources.

| Branch | Mod Loader | Build Plugin | Mappings |
|---|---|---|---|
| `main` | - | Repository baseline and documentation | - |
| `fabric/1.21.1` | **Fabric** 1.21.1 | Fabric Loom (`net.fabricmc.fabric-loom-remap`) | Mojang Official Mappings |
| `neoforge/1.21.1` | **NeoForge** 1.21.1 | NeoForge ModDevGradle (`net.neoforged.moddev`) | Parchment Mappings |

---

## Development Guide

### 1. Prerequisites
- **JDK**: Java 21+ (e.g., Zulu JDK 21, Eclipse Temurin 21, Microsoft OpenJDK 21)
- **IDE**: IntelliJ IDEA (recommended) or VS Code

### 2. Switching Branches
```bash
# Switch to Fabric 1.21.1 environment
git checkout fabric/1.21.1

# Switch to NeoForge 1.21.1 environment
git checkout neoforge/1.21.1
```

### 3. Building and Running

#### Fabric (`fabric/1.21.1`)
```bash
# Build mod JAR
./gradlew build

# Run client for debugging
./gradlew runClient

# Run dedicated server for debugging
./gradlew runServer
```

#### NeoForge (`neoforge/1.21.1`)
```bash
# Build mod JAR
./gradlew :neoforge:build

# Run client for debugging
./gradlew :neoforge:runClient

# Run dedicated server for debugging
./gradlew :neoforge:runServer
```

On Windows, use `gradlew.bat` instead of `./gradlew`. Run `gradlew.bat buildAll`
to test the shared modules and build both loaders. Fabric output is under
`build/libs`; NeoForge output is under `neoforge/build/libs`.

### 4. Tile artwork and optional backs

Default tile backs are solid teal. To add a diamond pattern, enable
**MCjhong: Patterned tile backs** in Minecraft's resource-pack screen. The pack is
bundled but disabled by default, and disabling it restores the solid backs.
Names and descriptions are available in all four supported languages.

The tile faces use CC0 vector artwork rasterized during the build. Source
attribution, resource-pack customization and the verification commands are in
[docs/ASSETS.md](docs/ASSETS.md). The mod does not download artwork at runtime.
