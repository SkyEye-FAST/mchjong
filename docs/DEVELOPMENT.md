# Development

Use JDK 25 to develop on this Minecraft 26.1.2 branch. Run the commands below
from the repository root with its checked-in Gradle wrapper.
On Windows, use `gradlew.bat` in place
of `./gradlew`. Read [AGENTS.md](../AGENTS.md) before making changes.

## Build and run

Build a distributable Fabric JAR with:

```sh
./gradlew :fabric:assemble --warning-mode fail
```

The output is written to `fabric/build/libs`. To test the mod interactively, run
`:fabric:runClient` with the same Gradle wrapper; use `:fabric:runServer` for a
development server. Replace the `fabric` task prefix with `neoforge` to work on
NeoForge. Each loader writes artifacts under its own
`build/libs` directory. The `build` task also runs the project's checks, whereas
`assemble` is the packaging task.

When working on an optional integration, add its dependency to the development
runtime with the corresponding Gradle property. Recipe viewers use
`-PrecipeBrowser=jei` or `-PrecipeBrowser=rei`. Maid integrations use
`-PwithMaid=true`; Fabric's Mod Menu integration uses `-PwithModMenu=true`.
Check the [integration table](../README.md#optional-integrations)
for loader availability. Patchouli uses `-PwithPatchouli=true`. Dependency versions are set in
[gradle.properties](../gradle.properties), not in these command-line properties.

## Source organization

| Module | Responsibility |
| --- | --- |
| `engine` | Rules, scoring adapters, game and room state, bot decisions and replay records; no Minecraft dependency |
| `common` | Shared Minecraft items, worlds, networking, menus, rendering and localization |
| `fabric`, `neoforge` | Platform registration and lifecycle adapters |
| `art` | Asset and server-data generators |

Hand-analysis changes start at the engine's analyzer adapters. The algorithms and
scoring definitions for MCR and Taiwanese mahjong are maintained in
[mcr-mahjong](https://github.com/SkyEye-FAST/mcr-mahjong) and
[taiwan-mahjong](https://github.com/SkyEye-FAST/taiwan-mahjong), respectively.
MChjong integrates those libraries with its own game progression, room settings
and physical tables. Consult the library documentation for scoring behavior
instead of maintaining another copy here.

Client actions are requests, not authority to change a game or inventory. The
server must identify the sender and validate the table, permissions and current
decision before applying a request. For inventory requests, validate the complete
operation before moving items. A transformation changes only its intended
components and must preserve the item's other components and stored contents.

Private saves are not network views. Before serializing a live view, remove any
information its recipient is not allowed to see, including future wall identities
and random seeds. Hints and computer opponents use only the player's own hand and
public information, even when room settings reveal other hands to human players.
Completed replay records may include the original concealed information, so they
are retrieved through the separate, participant-authorized replay path.

Client resources under `art/generated/assets` are checked in. Edit generator
inputs, run `:art:generateAssets`, and commit the affected outputs. Server data
under `build/generated/data` is generated during resource processing and is not
checked in. See [Custom content](ASSETS.md) when changing the resource formats
used by pack authors.

## Verification

Select checks for the actual change as required by
[AGENTS.md](../AGENTS.md#validation). For example, compile the Fabric sources
with the first command below, or select an individual engine test with the second:

```sh
./gradlew :fabric:compileJava --warning-mode fail
./gradlew :engine:test --tests '*McrSettlementTest' --warning-mode fail
```

The optional Create integration test checks native basin transactions, output
capacity and sequenced assembly using Create's installed APIs. Run it with Create
enabled; the default test run does not compile or execute this suite:

```sh
./gradlew :neoforge:test -PwithCreate=true --tests '*CreateProcessingTest' --warning-mode fail
```

Changes to inventory menus or equipment handling can also be checked in an
isolated Minecraft world. The existing checks in `fabric/src/smoke` use a real
server player to verify menu permissions, item transfers and equipment removal:

```sh
./gradlew :fabric:runSmokeClient --warning-mode fail
```

A run writes its result under `fabric/build/smoke/evidence` and its game log
under `fabric/build/smoke/run/logs`. Inspect the current result and report which
checks completed. For appearance and interaction changes, use the relevant
loader's `runClient` task and inspect the affected screen directly.

`./gradlew buildAll --warning-mode fail` builds all current-profile loaders and
runs the aggregate checks. The [snapshot workflow](../.github/workflows/snapshot.yml)
currently invokes it on pushes and pull requests. That CI behavior does not make
a local full build necessary for each edit.

## Ports

Features are developed on `main` and ported to `compat/1.20.1` and
`compat/26.1.2` only when synchronization is requested. Use a clean worktree for
each port and the same committed `main` revision for both updates. Retain each
port's own toolchains, dependencies and platform adaptations.

Each synchronization merge records its source `main` commit as the second parent.
Use that parent of the port's previous synchronization merge to identify the
changes since its last update. The ordinary merge base is unsuitable because
earlier return merges may make it a port commit instead. Record the new source
relationship with Git's `ours` merge strategy, then apply the diff between the
old and new source commits, excluding `.github/compat-*-ref`. The merge records
ancestry; applying the diff performs the actual content update. Resolve platform
differences and validate the affected behavior before signing the merge commit.

After both ports are verified, merge their tips back into main with `ours` to
preserve main's profile. Update `.github/compat-1.20.1-ref` and
`.github/compat-26.1.2-ref` to those exact tips in the return commit. Main must
descend from both tips. The release workflow builds the pinned revisions, not
the ports' unreviewed latest commits.

## Releases

For an explicit release, choose a version from the actual changes or the requested
target. At the release commit, `gradle.properties` must still contain
`mod_version=x.y.z-SNAPSHOT`, while the Git tag must be `x.y.z`. Move the
Unreleased changelog entries into `## [x.y.z] - YYYY-MM-DD`, update the changelog
links, and leave an empty Unreleased section for subsequent development.

The [release workflow](https://github.com/SkyEye-FAST/mchjong/blob/main/.github/workflows/release.yml) removes the snapshot
suffix for the published artifacts. It builds the tagged `main` revision and
both pinned ports, then publishes to GitHub Releases, Modrinth and CurseForge.
Publishing requires the `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` repository secrets.
After publication, advance `main` to the next patch snapshot for development.
