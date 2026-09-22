# MChjong contributor instructions

## Working in this repository

Read the current branch, Git status and relevant source before editing. Continue
existing work and preserve unrelated tracked and untracked changes. Use
`apply_patch` for every direct file edit. Build tools own generated outputs.
Keep patches scoped to the requested work; avoid reset, clean, force pushes and
overwriting local work.

Resolve the repository root from the current checkout and use it as the working
directory for repository commands. Use syntax appropriate to the active shell;
do not assume a particular checkout path, operating system or tool provider.

Deliver complete, runnable increments. Choose the simplest implementation that
meets the requirements, extend existing components and dependencies, and keep
concerns separate. Check library documentation and types before implementing
equivalent functionality. Remove obsolete implementation paths rather than
adding compatibility layers, migrations or speculative configuration.

## Ownership and architecture

`main` is the sole feature development branch. The root Gradle project is an aggregator;
`fabric`, `forge` and `neoforge` are peer 1.21.1 loader subprojects. Read
[ARCHITECTURE.md](docs/ARCHITECTURE.md) for the shared contracts.

- `engine`: Minecraft-independent rules, scoring adapters and replay records.
- `common`: shared Minecraft gameplay, items, menus, networking, client rendering
  and translations. The server owns inventory authorization and game decisions;
  client screens display synchronized state and send validated requests.
- `fabric/src/main`, `forge/src/main` and `neoforge/src/main`: loader-specific registration and lifecycle
  adapters. Share gameplay and presentation implementations through `common`.
- `art`: deterministic asset and server-data generation, with separate outputs.
  Edit source generators rather than generated textures, models or recipes.
- `common/src/smoke`: shared development-only client/server checks, with
  loader-specific adapters under `fabric/src/smoke` and `neoforge/src/smoke`.
- `common/src/ponderData`: shared Minecraft-native build-time generation of Ponder
  structures, compiled independently by both loader projects.

Keep optional integrations in a dedicated client compatibility package. Check
mod availability in loader client entry points before referencing integration
classes. Declare optional metadata and keep third-party implementations out of
release bundles. Verify both installed and absent dependency configurations.
Quilt uses the corresponding Fabric artifact. `compat/1.20.1` is a port-only
branch for Fabric and Forge; synchronize stable batches from `main`, preserving
the engine and shared gameplay rather than developing a second feature line.
Keep version differences limited to actual Minecraft and loader API boundaries.
Minecraft and dependency versions belong in `gradle.properties`. Automated
dependency updates (Renovate) manage routine tool, action and ecosystem version
bumps on weekly schedules without auto-merge. Migrating the target Minecraft
version (`minecraft_version`), Java toolchain (`java_version`), loader runtime
floors and compatibility profiles is strictly manual. Automated bots must never
modify `mod_version` or participate in project release/SemVer decisions.
Dependabot is reserved for security alerts and automated vulnerability patches,
with routine version update pull requests disabled to prevent duplication.

## Interface, resources and documentation

Before changing screens, widgets, HUDs, inventory UI or physical table layout,
read [UI_STYLE.md](docs/UI_STYLE.md). Reuse `MahjongUi`, `MahjongButton`,
`MahjongSlider` and `MahjongEditBox`, preserving native input and accessibility.
Keep the compact table footprint and shared render/picking geometry. Tooltips
contain concise labels and state; tutorials belong in dedicated guides.

Maintain English, Japanese, Simplified Chinese and Traditional Chinese together.
Keep translation keys, placeholders and tutorial content consistent across all
four languages. Follow [ASSETS.md](docs/ASSETS.md) and [AUDIO.md](docs/AUDIO.md)
for resource-pack paths and deterministic generation.

Describe current capabilities and supported workflows positively and definitively.
Omit retired features, negative feature lists, unimplemented caveats, and speculative
future roadmap promises from player documentation, introductions, technical guides and
changelog prose. Do not introduce unnecessary comparisons or references to external
projects, third-party games, historical predecessors, or unadopted algorithms (such as
"参考了xxx" or "未实现xxx"). Do not explain what terms or presets are named in Chinese
or other languages within documentation prose (such as stating "The Chinese preset labels are..."),
while preserving standard feature descriptions of multilingual support across English, Japanese,
Simplified Chinese and Traditional Chinese. Isolate mandatory third-party copyright and asset
attributions strictly to `NOTICE.md` and dedicated license notices rather than embedding
casual references across general documentation. Keep version support details in Compatibility
sections and state only what the available builds and validation establish. Preserve useful
operating requirements, permissions and privacy guarantees.

## Validation and delivery

Keep regression tests short and deterministic, extending the existing suite that
owns the behavior. Consolidate duplicate fixtures and assertions while preserving
their distinct boundary cases. Exercise shared lifecycle paths once per player
count; enumerate rule presets only where their behavior differs. Do not multiply
geometry checks by texture, dye or scoring variants that leave geometry unchanged.
Reserve client smokes for real loader, world, packet and UI boundaries rather than
repeating domain assertions. Remove redundant tests instead of disabling them or
adding alternate suites, fallback paths or new test frameworks.

Use JDK 21 for the current profile and the checked-in Gradle wrapper. The examples
below use `./gradlew`; use `gradlew.bat` on Windows when required by the shell:

```text
./gradlew buildAll --warning-mode fail
./gradlew :fabric:runSmokeClient --console=plain
./gradlew :neoforge:runSmokeClient --console=plain
```

`buildAll` covers the engine, presentation, generated resources and NeoForge
dedicated-server tests. Choose validation by the changed behavior: run the owning
unit tests, compilation tasks and focused client smoke modes. Do not default to
the complete build or full client smoke suites for every change or repeat them
after each small adjustment. Reserve full suites for releases, broad cross-module
changes, or failures whose scope cannot be isolated. Shared visual or interaction
changes need fresh screenshots of the affected flows on both loaders; prefer
focused captures over unrelated gameplay checks. Inspect those screenshots and
fresh PASS/FAIL markers and logs; compilation alone is not visual acceptance.
Report exactly which checks ran and any outstanding failures or coverage limits.

For Ponder changes, also run both installed-dependency smoke commands in
[PONDER.md](docs/PONDER.md), with `-PwithPonder=true`, and inspect their normal
and small-window tutorial screenshots in each loader's `smoke/ponder-evidence`.

## Versioning and releases

Keep `mod_version` in `gradle.properties` on a `-SNAPSHOT` version during normal
development. The default development target is the next patch after the latest
formal Release: for example, after `0.3.2`, use `0.3.3-SNAPSHOT`. The Snapshot's
base SemVer must be greater than the latest formal Release. Do not recalculate or
advance the version for ordinary commits; the current Snapshot identifies the
version being developed.

The default next-patch target is only a development placeholder, not a promise
that the next Release will be a patch. When explicitly asked to prepare or publish
a Release, review the actual changes since the previous formal Release and choose
the SemVer level again: compatible fixes and small changes are normally patch,
clear new functionality may require minor, and incompatible changes require
major. Never publish `x.y.(z+1)` merely because the checkout currently says
`x.y.(z+1)-SNAPSHOT`. An explicitly requested version or bump level overrides the
default assessment.

If the release assessment changes the target, first update `mod_version` to that
target with `-SNAPSHOT` before releasing, such as `0.3.3-SNAPSHOT` to
`0.4.0-SNAPSHOT`. Release tags are pure SemVer without a prefix, for example
`0.4.0`. The Release workflow requires the tag to equal `mod_version` with the
`-SNAPSHOT` suffix removed and supplies `-Pmod_version=$GITHUB_REF_NAME` so the
published JARs contain the formal version rather than the Snapshot version.

After a Release is complete, advance normal development to the released version's
next patch Snapshot, for example `0.4.0` to `0.4.1-SNAPSHOT`. That new Snapshot is
again only the default development target; reassess the SemVer level at the next
release request.

Use signed Git commits for each complete batch and push normally. Every commit
must follow the Conventional Commits specification (`<type>(<scope>): <description>`
or `<type>: <description>`), using standard types (`feat`, `fix`, `docs`, `style`,
`refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert` or `art`), with a
concise, lowercase, imperative description and no trailing punctuation. Confirm that
the configured Git signing mechanism is ready before committing (`git signing-agent status`).
Use the contributor's existing signing setup; keep signing enabled and keep credentials
and private keys out of the repository.
Inspect the staged diff, use `git commit -S`, verify each resulting signature,
then push and confirm the remote ref. Report commit IDs, tests and outstanding
limitations precisely.
