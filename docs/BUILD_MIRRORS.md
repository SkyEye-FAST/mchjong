# Build download sources

The loader subprojects use the following download sources:

| Downloads | Source | Configuration |
| --- | --- | --- |
| General Maven dependencies and Gradle plugins | Maven Central and Gradle Plugin Portal | Root `build.gradle` and `settings.gradle` |
| Fabric Loom plugin, Loader and Fabric API | Official Fabric Maven | `settings.gradle` and Loom defaults |
| Fabric client assets | Mojang | Loom defaults |
| Minecraft libraries | Official repositories | Loader plugin defaults |
| Minecraft version manifests | Mojang | Loader plugin defaults |
| NeoForge main module, including userdev | Official NeoForge Maven | Exclusive module repository in `neoforge/build.gradle` |
| NeoForge test framework and build tooling | Official repositories | ModDevGradle and `neoforge/build.gradle` |
| Forge runtime and ForgeGradle | Official Forge Maven | `forge/build.gradle` and `settings.gradle` |

Local builds and CI share these sources. Optional mod integrations use their
publishers' Maven repositories. Machine-specific download overrides belong in
the user's Gradle configuration or environment.

ForgeGradle 7 runs Minecraft Mavenizer on a Java 25 toolchain. Gradle provisions
that build toolchain through the Foojay resolver when it is not installed; the
Minecraft 1.21.1 mod and development client still target Java 21. Forge's Mavenizer
and Slime Launcher are separate Java processes, so any local proxy configuration
must also reach those processes. Proxy credentials and machine-specific settings
belong outside the repository.

## Optional local asset mirror

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
`:neoforge:runSmokeClient`. Fabric supports `loom_resources_base` in the user's
Gradle properties. These local overrides do not change CI download sources.

## Verification and sources

Use JDK 21 and the checked-in Gradle wrapper:

```sh
./gradlew buildAll --warning-mode fail
./gradlew :fabric:downloadAssets :neoforge:downloadAssets --console=plain
```

Existing caches can satisfy downloads without contacting a mirror. A successful
cached build verifies configuration compatibility, not a complete fresh download.
Check the exact artifact and version when diagnosing a repository failure.

## Upstream resources

* [Fabric Wiki: development setup and mirrors](https://wiki.fabricmc.net/zh_cn:tutorial:setup)
* [Loom mirror properties](https://github.com/FabricMC/fabric-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/util/MirrorUtil.java)
* [BMCLAPI documentation](https://bmclapidoc.bangbang93.com/)
* [ModDevGradle launcher manifest property](https://github.com/neoforged/ModDevGradle/blob/main/src/main/java/net/neoforged/nfrtgradle/NeoFormRuntimeExtension.java)
* [NeoForm Runtime asset repository environment variable](https://github.com/neoforged/NeoFormRuntime/blob/main/src/main/java/net/neoforged/neoform/runtime/cli/DownloadAssetsCommand.java)
