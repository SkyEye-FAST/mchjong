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
  `forge/src/smoke` owns the focused Forge bootstrap checks.
- `common/src/ponderData`: shared Minecraft-native build-time generation of Ponder
  structures, compiled independently by both loader projects.

Keep optional integrations in a dedicated client compatibility package. Check
mod availability in loader client entry points before referencing integration
classes. Declare optional metadata and keep third-party implementations out of
release bundles. Verify both installed and absent dependency configurations.
Quilt uses the corresponding Fabric artifact. Do not synchronize changes from
`main` to any other branch unless the user explicitly requests it.
`compat/1.20.1` is a port-only branch for Fabric and Forge; when synchronization
is requested, preserve the engine and shared gameplay rather than developing a
second feature line.
`compat/26.1.2` is the corresponding port-only branch for Fabric/Quilt and
NeoForge, using Java 25. Keep `main` on Minecraft 1.21.1 / Java 21 and the
1.20.1 runtime on Java 17. When requested, synchronize applicable code, tests,
resources, formatting configuration and documentation together; retain each port's loader
APIs, dependency versions and single-version snapshot workflow. Omit optional
adapters when the dependency has no build for that Minecraft/loader profile.
Record completed synchronization with signed merge commits: merge the relevant
`main` commit into each compatibility branch after adapting its changes, then
merge both compatibility branch tips back into `main`. Preserve each branch's
Minecraft and loader-specific tree when recording already adapted changes.
Finish with `main` as a descendant of both compatibility branch tips, and
advance the release pins to those synchronized tips. Verify the ancestry and
remote refs before calling the branch coordination complete.
Keep the three-version loader and integration tables in every README aligned,
and distinguish shipped adapters from completed runtime validation.
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

Keep player operations in `PLAYING.md`, `SURVIVAL.md` and `ROOMS.md`; registry,
persistence and recipe identity in `SUPPLIES.md`; module ownership in
`ARCHITECTURE.md`; visual contracts in `UI_STYLE.md`; dependency support in
`COMPATIBILITY.md`; build, branch synchronization and release procedures in
`DEVELOPMENT.md`; and reusable verification commands and test ownership in
`VERIFICATION.md`. Link to the owning guide rather than duplicating its paragraphs.
Add one concise `CHANGELOG.md` entry per meaningful completed change under
`## [Unreleased]`, using `Added`, `Changed` or `Fixed`. Documentation cleanup and
routine verification do not each need an entry. Keep guides focused on current
contracts, operating instructions and necessary maintenance constraints. Put test
results and coverage limits in the delivery message or PR, not permanent dated
acceptance logs, screenshot inventories, benchmark tables or experiment archives.

Before changing screens, widgets, HUDs, inventory UI or physical table layout,
read [UI_STYLE.md](docs/UI_STYLE.md). Reuse `MahjongUi`, `MahjongButton`,
`MahjongSlider` and `MahjongEditBox`, preserving native input and accessibility.
Keep the compact table footprint and shared render/picking geometry. Tooltips
contain concise labels and state; tutorials belong in dedicated guides.

Maintain English, Japanese, Simplified Chinese and Traditional Chinese together.
Language JSON files (`en_us.json`, `ja_jp.json`, `zh_cn.json`, `zh_tw.json`) must
maintain strict key parity, formatted with two-space indentation, LF line endings,
and ascending alphabetical key order. Placeholders and format argument specifiers
(`%s`, `%1$s`) must match across all four translations. Follow hierarchical
snake_case namespaces with dot separation (e.g. `rules.mchjong.option.<name>`,
`.description`, `yaku.mchjong.<name>`, and `.short` rather than `_short` suffixes).
Rule option titles use standard Riichi Mahjong terminology, with detailed mechanics
placed in `.description` tooltips. Keep terms and item names aligned with registered
translations. Follow [ASSETS.md](docs/ASSETS.md) and [AUDIO.md](docs/AUDIO.md) for
resource-pack paths and deterministic generation.

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

Before running checks, identify the changed boundary and select the smallest
owning command. A small fix must not trigger unrelated suites, full client runs
or a screenshot matrix. Do not add a permanent test for a one-time diagnostic,
cosmetic adjustment or an assertion that only mirrors implementation. Preserve
tests for distinct failure modes, permissions, persistence and data integrity.
Remove diagnostic fixtures when their investigation ends. Each extra parameter
dimension must exercise different behavior; do not take Cartesian products of
loaders, languages, sizes, presets, materials and player counts by default.

Use JDK 21 for the current profile and the checked-in Gradle wrapper. The examples
below use `./gradlew`; use `gradlew.bat` on Windows when required by the shell:

```text
./gradlew :fabric:compileJava --warning-mode fail
./gradlew :fabric:test --tests '*PresetArchivesTest'
```

`buildAll` covers the engine, presentation, generated resources and NeoForge
dedicated-server tests. Choose validation by the changed behavior: run the owning
unit tests, compilation tasks and focused client smoke modes. Do not default to
the complete build or full client smoke suites for every change or repeat them
after each small adjustment. Reserve full suites for releases, broad cross-module
changes, or failures whose scope cannot be isolated. Shared visual or interaction
changes need a focused visual check only when rendering or interaction can change.
Use one representative loader and capture only the affected state (normally one
or two images). Add another loader, locale or viewport only for a concrete
loader API, translation or responsive-layout risk. Screenshot capture is opt-in;
assertion-only smoke runs must not produce a gallery. Inspect requested captures
and fresh PASS/FAIL markers; compilation alone is not visual acceptance.
Report exactly which checks ran and any outstanding failures or coverage limits.
For routine fixes, run the smallest owning test or smoke command once after the
change is ready; expand validation only when that check reveals a concrete risk.
Keep generated screenshots and logs in ignored build output, never in a tracked
archive. Replace evidence for the selected profile rather than accumulating runs.

For Ponder changes, select the affected installed-dependency check in
[PONDER.md](docs/PONDER.md). Test another loader or small window only when the
change crosses that boundary. API replacements require compilation of affected
source sets; do not silence deprecation diagnostics instead of replacing calls.

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
`0.4.0-SNAPSHOT`. Move accumulated entries under `## [Unreleased]` in
`CHANGELOG.md` into the formal version section `## [x.y.z] - YYYY-MM-DD`, retain
an empty `## [Unreleased]` section above it, and update the release links at the
end of the changelog. Release tags are pure SemVer without a prefix, for example
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
