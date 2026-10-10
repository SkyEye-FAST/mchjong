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
for dependency profiles and [Verification](VERIFICATION.md) for focused loader,
integration and visual checks. Choose one representative loader for shared behavior; add another only when
its API or runtime path is affected. Request screenshots only for the changed state.

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
runs on `main`, validates the tag against the development version, extracts notes
from [the changelog](../CHANGELOG.md), and builds the reviewed compatibility
revision selected by `main`'s `.github/compat-26.1.2-ref` alongside the other
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
Each verified artifact is published sequentially in this order: 1.21.1
Fabric/Quilt, NeoForge, Forge; 1.20.1 Fabric/Quilt, Forge; then 26.1.2
Fabric/Quilt, NeoForge. Fabric/Quilt uploads list Fabric API as a required
dependency on both platforms.

Use [Verification](VERIFICATION.md) for checks and record their exact scope before
advancing a release pin. The [compatibility tables](../README.md#compatibility)
describe supported adapters, not a substitute for runtime acceptance.

### Synchronizing a batch with Git

Keep one worktree per branch and finish the main batch before starting a port.
Use the same committed main SHA for both ports; do not advance main until both
have been reviewed and tested. No synchronization script or additional state file
is needed: each port's latest synchronization merge records its previous main
source as the second parent.

Apply the difference between successive **main sources**, rather than an ordinary
merge of the complete main tree. The return merges deliberately preserve main's
profile, so the ordinary merge base can be a port commit; using that tree as the
patch base would reintroduce unrelated Minecraft and loader differences.

In each clean port worktree, inspect the previous merge and prepare the update
with Git Bash (or another Bash shell):

```bash
git log --first-parent --merges -1 --format='%H %P'
source=$(git rev-parse main)
previous=$(git log --first-parent --merges -1 --format='%P' | cut -d' ' -f2)
mkdir -p build
git diff --binary --full-index "$previous" "$source" -- . ':(exclude).github/compat-*-ref' > build/compat.patch
git merge --no-ff --no-commit -s ours "$source"
git apply --3way --index build/compat.patch
```

The `ours` merge records the parent relationship; the patch supplies the actual
shared changes. Resolve patch conflicts for that port's APIs, review the staged
diff, and retain its Java target, loaders and dependencies. If the patch is empty,
there is no code to apply. To abandon preparation, use `git merge --abort`.
Run the focused runtime checks and `buildAll --warning-mode fail` with the port's
JDK before creating its signed merge commit:

```bash
git add <resolved-files>
git commit -S -m "chore(compat): synchronize port with main"
git verify-commit HEAD
```

After both ports pass, return to the clean main worktree. Record both verified
tips in one merge, preserving main's file tree and updating the two release pins
in that same commit:

```bash
git merge --no-ff --no-commit -s ours compat/1.20.1 compat/26.1.2
git rev-parse compat/1.20.1 > .github/compat-1.20.1-ref
git rev-parse compat/26.1.2 > .github/compat-26.1.2-ref
git add .github/compat-1.20.1-ref .github/compat-26.1.2-ref
git commit -S -m "chore(compat): record verified port synchronization"
git verify-commit HEAD
git push origin main compat/1.20.1 compat/26.1.2
```

The return commit changes only the pins; its parents make main descend from both
verified port tips. For the next batch, use the ports' second parents again,
excluding the release-pin files from the patch as above.
