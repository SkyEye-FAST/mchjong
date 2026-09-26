# Build download sources

The loader subprojects use the following download sources:

| Downloads | Source | Configuration |
| --- | --- | --- |
| General Maven dependencies and Gradle plugins | Maven Central and Gradle Plugin Portal | Root `build.gradle` and `settings.gradle` |
| Fabric Loom plugin, Loader and Fabric API | Official Fabric Maven | `settings.gradle` and Loom defaults |
| Fabric client assets | Mojang | Loom defaults |
| Minecraft libraries | Official repositories | Loader plugin defaults |
| Minecraft version manifests | Mojang | Loader plugin defaults |
| Forge runtime | Official Forge Maven | Exclusive module repository in `forge/build.gradle` |
| Legacy Forge build tooling | ModDevGradle repositories | `settings.gradle` and `forge/build.gradle` |

Local builds and CI share these sources. Optional mod integrations use their
publishers' Maven repositories. Machine-specific download overrides belong in
the user's Gradle configuration or environment.

Fabric supports `loom_resources_base` in the user's Gradle properties for a local
asset mirror. These local overrides do not change CI download sources.

## Verification and sources

Use JDK 21 and the checked-in Gradle wrapper:

```sh
./gradlew buildAll --warning-mode fail
./gradlew :fabric:downloadAssets --console=plain
```

Existing caches can satisfy downloads without contacting a mirror. A successful
cached build verifies configuration compatibility, not a complete fresh download.
Check the exact artifact and version when diagnosing a repository failure.

## Upstream resources

* [Fabric Wiki: development setup and mirrors](https://wiki.fabricmc.net/zh_cn:tutorial:setup)
* [Loom mirror properties](https://github.com/FabricMC/fabric-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/util/MirrorUtil.java)
* [BMCLAPI documentation](https://bmclapidoc.bangbang93.com/)
