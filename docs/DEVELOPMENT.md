# Building and contributing

[Documentation](README.md) · [Project overview](../README.md)

## Building from source

Use JDK 21 for the current development profile.

```bash
git clone https://github.com/SkyEye-FAST/mchjong.git
cd mchjong
```

The default `main` branch contains Fabric, Forge and NeoForge support. Minecraft,
loader, mappings, and Java versions are selected by the build profile in
`gradle.properties`.

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

See [Build download mirrors](BUILD_MIRRORS.md) for the configured Fabric and
NeoForge repositories and the NeoForge client asset mirror environment variable.

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
runs `buildAll` on branch pushes and pull requests and uploads separate Fabric,
Forge and NeoForge snapshot artifacts.

[Release](https://github.com/SkyEye-FAST/mchjong/actions/workflows/release.yml)
validates the tag against the development version, extracts notes from
[the changelog](../CHANGELOG.md), builds and publishes release artifacts to
GitHub Releases, Modrinth and CurseForge. GitHub Releases keeps the extracted
notes; Modrinth and CurseForge receive a copy with relative and reference links
expanded to absolute URLs. Before the first release, add the
`MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` repository secrets in GitHub Actions.
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
Each verified artifact is published sequentially in this order: 1.21.1
Fabric/Quilt, NeoForge, Forge; 1.20.1 Fabric/Quilt, Forge; then 26.1.2
Fabric/Quilt, NeoForge. Fabric/Quilt uploads list Fabric API as a required
dependency on both platforms.

Use [Verification](VERIFICATION.md) for checks and record their exact scope before
advancing a release pin. The [compatibility tables](../README.md#compatibility)
describe supported adapters, not a substitute for runtime acceptance.
