# Compatibility and verification

## Minecraft 26.1.2 profile

The `compat/26.1.2` branch builds Fabric and NeoForge artifacts with JDK 25.
Fabric requires Loader 0.19.5 or newer and Fabric API 0.155.3+26.1.2. NeoForge
requires 26.1.2.109 or newer. Versions are pinned in `gradle.properties`.

The shared engine, gameplay, menus, rendering and translations follow `main`.
The port adapts Minecraft and loader APIs at their boundaries. Snapshot CI builds
both loader artifacts from this branch. A release uses the reviewed port revision
pinned by `main` and gives both JARs the release version.

## Optional recipe viewer

JEI 29.40.0.102 with MezzConfig 0.6.3 is the installed development profile on
both loaders. Run client checks with `-PrecipeBrowser=jei`; the default profile
uses `-PrecipeBrowser=none`. The adapter and shared engine are packaged in
MChjong, while the optional viewer itself remains a separate dependency.

The catalogue includes complete and empty 136-tile boxes, blank tiles in all
sixteen materials, mahjong dyes, point sticks, and wooden table variants. Recipe
identities preserve wood, material, tile face, color, markings, denomination and
container contents. The integrated-server checks validate examples against the
loaded recipes and inspect inventory transfers and box transactions.

## Verification

Use the checked-in Gradle wrapper with JDK 25:

```text
./gradlew buildAll --warning-mode fail --console=plain
./gradlew :fabric:runSmokeClient --console=plain
./gradlew :neoforge:runSmokeClient --console=plain
./gradlew :fabric:runSmokeClient -PsmokePalette=true --console=plain
./gradlew :neoforge:runSmokeClient -PsmokePalette=true --console=plain
./gradlew :fabric:runSmokeClient -PsmokeManual=true --console=plain
./gradlew :neoforge:runSmokeClient -PsmokeManual=true --console=plain
./gradlew :fabric:runSmokeClient -PsmokeBrowser=true -PrecipeBrowser=jei --console=plain
./gradlew :neoforge:runSmokeClient -PsmokeBrowser=true -PrecipeBrowser=jei --console=plain
```

Smoke profiles write screenshots and PASS markers under each loader's
`build/smoke/<profile>-evidence`. Check their timestamps and inspect the fresh
captures for the changed workflow. `buildAll` includes the engine, generated
assets and data, and loader tests; client runs exercise the integrated server,
packets, inventory and rendering.

The JDK 25 build, full Fabric and NeoForge client smokes, palette captures,
physical manual-table handling, and installed-JEI browser smokes have passed
on this profile. The captured tile materials, dyes, furniture, dice, localized
controls and physical drags render correctly on both loaders.

The Fabric and NeoForge dedicated-server launchers can be checked with
`./gradlew :fabric:runSmokeServer :neoforge:runSmokeServer --console=plain`.
This starts each server in its isolated settings directory.
