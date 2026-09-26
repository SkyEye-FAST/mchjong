# Build download mirrors

The loader subprojects use the following download sources:

| Downloads | Source | Configuration |
| --- | --- | --- |
| General Maven dependencies and Gradle plugins | Aliyun | Root `build.gradle` and `settings.gradle` |
| Fabric Loom plugin, Loader and Fabric API | Hanbings Fabric Maven mirror | `settings.gradle` and `loom_fabric_repository` |
| Fabric client assets | BMCLAPI | `loom_resources_base` |
| Minecraft libraries | Official repositories | Loader plugin defaults |
| Minecraft version manifest for Fabric Loom | BMCLAPI | `loom_version_manifests` |
| Forge runtime | BMCLAPI Forge Maven mirror | Exclusive module repository in `forge/build.gradle` |
| Legacy Forge build tooling | ModDevGradle repositories | `settings.gradle` and `forge/build.gradle` |

Version manifests can contain official URLs for individual version metadata,
game JARs, mappings and asset indexes. Selecting a manifest mirror does not
rewrite those embedded URLs. Optional mod integrations retain their own Maven
repositories.
NeoForge dependency groups are excluded from the general Maven mirror so their
build tooling resolves through the loader's official repositories.

Local builds use the sources configured in `gradle.properties`.

Proxy credentials and machine-specific settings belong outside the repository.

## Verification and sources

Use JDK 21 and the checked-in Gradle wrapper:

```sh
./gradlew buildAll --warning-mode fail
./gradlew :fabric:downloadAssets --console=plain
```

Existing caches can satisfy downloads without contacting a mirror. A successful
cached build verifies configuration compatibility, not a complete fresh download.
Mirrors can lag upstream releases; inspect the exact version and artifact before
changing a repository URL.

## Upstream resources

* [Fabric Wiki: development setup and mirrors](https://wiki.fabricmc.net/zh_cn:tutorial:setup)
* [Loom mirror properties](https://github.com/FabricMC/fabric-loom/blob/dev/1.17/src/main/java/net/fabricmc/loom/util/MirrorUtil.java)
* [BMCLAPI documentation](https://bmclapidoc.bangbang93.com/)
