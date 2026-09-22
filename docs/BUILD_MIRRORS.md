# Build download mirrors

The loader subprojects use the following download sources:

| Downloads | Source | Configuration |
| --- | --- | --- |
| General Maven dependencies and Gradle plugins | Aliyun | Root `build.gradle` and `settings.gradle` |
| Fabric Loom plugin, Loader and Fabric API | Hanbings Fabric Maven mirror | `settings.gradle` and `loom_fabric_repository` |
| Fabric client assets | BMCLAPI | `loom_resources_base` |
| Minecraft libraries | Official repositories | Loader plugin defaults |
| Minecraft version manifest for both loaders | BMCLAPI | `loom_version_manifests` and `neoForge.neoFormRuntime.launcherManifestUrl` |
| NeoForge main module, including userdev | BMCLAPI | Exclusive module repository in `neoforge/build.gradle` |
| NeoForge test framework and build tooling | Official repositories | ModDevGradle and `neoforge/build.gradle` |
| Forge runtime and ForgeGradle | Official Forge Maven | `forge/build.gradle` and `settings.gradle` |

Version manifests can contain official URLs for individual version metadata,
game JARs, mappings and asset indexes. Selecting a manifest mirror does not
rewrite those embedded URLs. Optional mod integrations retain their own Maven
repositories.

ForgeGradle 7 runs Minecraft Mavenizer on a Java 25 toolchain. Gradle provisions
that build toolchain through the Foojay resolver when it is not installed; the
Minecraft 1.21.1 mod and development client still target Java 21. Forge's Mavenizer
and Slime Launcher are separate Java processes, so any local proxy configuration
must also reach those processes. Proxy credentials and machine-specific settings
belong outside the repository.

## NeoForge client assets

NeoForm Runtime supports the `NFRT_ASSET_REPOSITORY` environment variable for
the client asset object repository. Set it before starting Gradle or the IDE.
For PowerShell:

```powershell
$env:NFRT_ASSET_REPOSITORY = 'https://bmclapi2.bangbang93.com/assets/'
./gradlew.bat :neoforge:downloadAssets --console=plain
```

For Bash:

```sh
NFRT_ASSET_REPOSITORY=https://bmclapi2.bangbang93.com/assets/ ./gradlew :neoforge:downloadAssets --console=plain
```

The same environment variable applies to `:neoforge:runClient` and
`:neoforge:runSmokeClient`. Fabric reads its asset mirror directly from
`gradle.properties`.

## Verification and sources

Use JDK 21 and the checked-in Gradle wrapper:

```sh
./gradlew buildAll --warning-mode fail
./gradlew :fabric:downloadAssets :neoforge:downloadAssets --console=plain
```

Existing caches can satisfy downloads without contacting a mirror. A successful
cached build verifies configuration compatibility, not a complete fresh download.
Mirrors can lag upstream releases; inspect the exact version and artifact before
changing a repository URL. BMCLAPI's Maven mirror does not cover every development
dependency, so its NeoForge routing is limited to the main module.

References:

* [Fabric Wiki: development setup and mirrors](https://wiki.fabricmc.net/zh_cn:tutorial:setup)
* [Loom mirror properties](https://github.com/FabricMC/fabric-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/util/MirrorUtil.java)
* [BMCLAPI documentation](https://bmclapidoc.bangbang93.com/)
* [ModDevGradle launcher manifest property](https://github.com/neoforged/ModDevGradle/blob/main/src/main/java/net/neoforged/nfrtgradle/NeoFormRuntimeExtension.java)
* [NeoForm Runtime asset repository environment variable](https://github.com/neoforged/NeoFormRuntime/blob/main/src/main/java/net/neoforged/neoform/runtime/cli/DownloadAssetsCommand.java)
