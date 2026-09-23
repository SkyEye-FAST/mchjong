# Building and contributing

[Documentation](README.md) · [Project overview](../README.md)

## Building from source

Use JDK 21 to build the Minecraft 1.20.1 profile, which targets Java 17.

```bash
git clone https://github.com/SkyEye-FAST/mchjong.git
cd mchjong
```

Check out `compat/1.20.1` to build the Fabric and Forge profiles. Minecraft,
loader, mappings, and Java versions are selected in `gradle.properties`.

### Fabric

```bash
./gradlew :fabric:build
./gradlew :fabric:runClient   # optional Fabric development client
./gradlew :fabric:runServer   # optional dedicated server
```

### Forge

```bash
./gradlew :forge:build
./gradlew :forge:runClient   # optional development client
./gradlew :forge:runServer   # optional dedicated server
```

To build all loaders and run the shared checks:

```bash
./gradlew buildAll
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

See [Build download mirrors](BUILD_MIRRORS.md) for the configured dependency
repositories and client asset mirror.

## Validation

Choose checks for the changed behavior. Use `./gradlew buildAll --warning-mode fail`
for broad changes and releases. See [Compatibility and verification](COMPATIBILITY.md)
for loader smoke tests and dependency profiles, and [Ponder integration](PONDER.md)
for tutorial validation. Shared visual changes require fresh screenshots from both loaders.

## Contributor guides

Read [the contributor instructions](../AGENTS.md) before editing.
[Architecture](ARCHITECTURE.md) describes module ownership and shared contracts;
[Interface style](UI_STYLE.md) covers controls, layout and accessibility.
Follow [Assets](ASSETS.md) and [Audio](AUDIO.md) when editing resource generators.

## Automated builds

[Snapshot CI](https://github.com/SkyEye-FAST/mchjong/actions/workflows/snapshot.yml)
runs `buildAll` on branch pushes and pull requests and uploads separate Fabric
and Forge snapshot artifacts.

[Release](https://github.com/SkyEye-FAST/mchjong/actions/workflows/release.yml)
validates the tag against the development version, extracts notes from
[the changelog](../CHANGELOG.md), and builds and publishes release artifacts.
Follow the versioning and signed-commit requirements in the contributor instructions.
