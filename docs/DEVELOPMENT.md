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
for dependency profiles and [Verification](VERIFICATION.md) for focused loader,
tutorial and visual checks. Shared visual changes require fresh screenshots from
the affected loaders.

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
runs on `main`, validates the tag against the development version, extracts notes
from [the changelog](../CHANGELOG.md), and builds the reviewed compatibility
revision selected by `main`'s `.github/compat-1.20.1-ref` alongside the other
version profiles. This branch's snapshot workflow builds only its two loaders.
Follow the versioning and signed-commit requirements in the contributor instructions.

## Version synchronization and artifacts

`main` is the sole feature development line and targets Minecraft 1.21.1 / Java 21.
Synchronize into `compat/1.20.1` and `compat/26.1.2` only when explicitly
requested, preserving shared gameplay while adapting actual Minecraft and loader
API boundaries.
The 1.20.1 port builds Fabric and Forge with a Java 17 runtime; the 26.1.2 port
builds Fabric and NeoForge with Java 25. Quilt consumes the matching Fabric JAR.

Synchronize resources, documentation and formatting configuration with code.
Keep each branch's dependency versions, module set and single-version snapshot
workflow. Original optional mods and cross-loader ports share one README row;
name the concrete distributions below the table. Pin usable upstream prereleases
to an exact build and verify their checksums and installed/absent behavior before
marking them supported. A source branch alone does not establish compatibility.

The release workflow on `main` builds its three loader artifacts, both 1.20.1
artifacts from `.github/compat-1.20.1-ref`, and both 26.1.2 artifacts from
`.github/compat-26.1.2-ref`. Update each pin after reviewing and validating a port
batch. All checkouts receive the same mod version; filenames include Minecraft
version and loader. A main commit does not automatically advance the pins.
Tags and publication belong to `main`; port snapshots build only their own version.

Use [Verification](VERIFICATION.md) for checks and record their exact scope before
advancing a release pin. The [compatibility tables](../README.md#compatibility)
describe supported adapters, not a substitute for runtime acceptance.
