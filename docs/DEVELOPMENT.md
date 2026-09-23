# Building and contributing

[Documentation](README.md) · [Project overview](../README.md)

## Building from source

Use JDK 25 for the Minecraft 26.1.2 development profile.

```bash
git clone https://github.com/SkyEye-FAST/mchjong.git
cd mchjong
```

Check out `compat/26.1.2` to build the Fabric and NeoForge profiles. Minecraft,
loader, mappings, and Java versions are selected in `gradle.properties`.

### Fabric

```bash
./gradlew :fabric:build
./gradlew :fabric:runClient   # optional Fabric development client
./gradlew :fabric:runServer   # optional dedicated server
```

### NeoForge

```bash
./gradlew :neoforge:build
./gradlew :neoforge:runClient   # optional development client
./gradlew :neoforge:runServer   # optional dedicated server
```

To build all loaders and run the shared checks:

```bash
./gradlew buildAll
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

See [Build download mirrors](BUILD_MIRRORS.md) for the configured Fabric and
NeoForge repositories and the NeoForge client asset mirror environment variable.

## Validation

Choose checks for the changed behavior. Use `./gradlew buildAll --warning-mode fail`
for broad changes and releases. See [Compatibility and verification](COMPATIBILITY.md)
for loader smoke tests and dependency profiles. Shared visual changes require
fresh screenshots from both loaders.

## Contributor guides

Read [the contributor instructions](../AGENTS.md) before editing.
[Architecture](ARCHITECTURE.md) describes module ownership and shared contracts;
[Interface style](UI_STYLE.md) covers controls, layout and accessibility.
Follow [Assets](ASSETS.md) and [Audio](AUDIO.md) when editing resource generators.

## Automated builds

[Snapshot CI](https://github.com/SkyEye-FAST/mchjong/actions/workflows/snapshot.yml)
runs `buildAll` on branch pushes and pull requests and uploads separate Fabric
and NeoForge snapshot artifacts.

[Release](https://github.com/SkyEye-FAST/mchjong/actions/workflows/release.yml)
validates the tag against the development version, extracts notes from
[the changelog](../CHANGELOG.md), and builds and publishes release artifacts.
Follow the versioning and signed-commit requirements in the contributor instructions.
