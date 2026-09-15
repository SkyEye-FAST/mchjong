# mchjong

Mahjong mod for Minecraft 1.21.1.

## Mod Information

- **Mod ID**: `mchjong`
- **Mod Name**: `mchjong`
- **Target Minecraft Version**: `1.21.1`
- **Java Requirement**: `Java 21` (LTS)
- **Base Package**: `top.skyeyefast.mchjong`
- **Author**: `SkyEye_FAST`
- **License**: Apache-2.0

---

## Branch Structure

This repository uses a multi-branch workflow to maintain separate development environments for each mod loader:

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
./gradlew build

# Run client for debugging
./gradlew runClient

# Run dedicated server for debugging
./gradlew runServer
```
