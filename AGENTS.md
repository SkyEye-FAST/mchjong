# MCjhong contributor instructions

## Working in this repository

Read the current branch, Git status and relevant source before editing. Continue
existing work and preserve unrelated tracked and untracked changes. Use
`apply_patch` for every direct file edit. Build tools own generated outputs.
Keep patches scoped to the requested work; avoid reset, clean, force pushes and
overwriting local work.

The Windows workspace is `C:\Java\mchjong`. When using the Coding Tools MCP,
confirm the workspace root before choosing a relative workdir (`Java/mchjong`
when the root is `C:\`), pass that workdir explicitly, and use
`pwsh -NoProfile -Command` for PowerShell commands because its default command
shell can be `cmd`. Outside Coding Tools MCP, `pwsh -NoProfile -Command` is not
required.

Deliver complete, runnable increments. Choose the simplest implementation that
meets the requirements, extend existing components and dependencies, and keep
concerns separate. Check library documentation and types before implementing
equivalent functionality. Remove obsolete implementation paths rather than
adding compatibility layers, migrations or speculative configuration.

## Ownership and architecture

`main` contains both loader builds. The root Gradle project builds Fabric;
`neoforge` builds NeoForge. Read [ARCHITECTURE.md](docs/ARCHITECTURE.md) for the
shared contracts.

- `engine`: Minecraft-independent rules, scoring adapters and replay records.
- `common`: shared Minecraft gameplay, items, menus, networking, client rendering
  and translations. The server owns inventory authorization and game decisions;
  client screens display synchronized state and send validated requests.
- `src/main` and `neoforge/src/main`: loader-specific registration and lifecycle
  adapters. Share gameplay and presentation implementations through `common`.
- `art`: deterministic asset and server-data generation, with separate outputs.
  Edit source generators rather than generated textures, models or recipes.
- `src/smoke` and `neoforge/src/smoke`: development-only client/server checks.
- `src/ponderData`: Minecraft-native build-time generation of Ponder structures.

Keep optional integrations in a dedicated client compatibility package. Check
mod availability in loader client entry points before referencing integration
classes. Declare optional metadata and keep third-party implementations out of
release bundles. Verify both installed and absent dependency configurations.
Minecraft and dependency versions belong in `gradle.properties`.

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

Describe current capabilities and supported workflows positively. Omit retired
features and negative feature lists from player documentation, introductions
and changelog prose. Keep version support details in Compatibility sections and
state only what the available builds and validation establish. Preserve useful
operating requirements, permissions and privacy guarantees.

## Validation and delivery

Use JDK 21 for the current profile and the checked-in Gradle wrapper:

```text
gradlew.bat buildAll --warning-mode fail
gradlew.bat :runSmokeClient --console=plain
gradlew.bat :neoforge:runSmokeClient --console=plain
```

`buildAll` covers the engine, presentation, generated resources and NeoForge
dedicated-server tests. Run targeted tests during development, then the complete
build for a finished code batch. Shared visual or interaction changes require
both client smokes and inspection of fresh screenshots in `build/smoke/evidence`
and `neoforge/build/smoke/evidence`. The leading colon selects the root Fabric
task explicitly. Check fresh PASS/FAIL markers and logs; compilation alone is
not visual acceptance. Report any validation that could not be performed.

For Ponder changes, also run both installed-dependency smoke commands in
[PONDER.md](docs/PONDER.md), with `-PwithPonder=true`, and inspect their normal
and small-window tutorial screenshots in each loader's `smoke/ponder-evidence`.

Use signed Git commits for each complete batch and push normally. Check
`git signing-agent status` before committing. In a sandbox with a different
home directory, use the existing user's Git configuration and signing setup;
keep signing enabled and keep credentials and private keys out of the repository.
Inspect the staged diff, use `git commit -S`, verify each resulting signature,
then push and confirm the remote ref. Report commit IDs, tests and outstanding
limitations precisely.
