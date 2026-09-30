# MChjong project instructions

## Ownership and architecture

`main` is the sole feature development branch: Minecraft 1.21.1 / Java 21.
The root Gradle project aggregates peer `fabric`, `forge` and `neoforge` loaders.
Shared contracts are in [ARCHITECTURE.md](docs/ARCHITECTURE.md).

- `engine`: Minecraft-independent rules, scoring adapters and replay records.
- `common`: shared Minecraft gameplay, items, menus, networking, client rendering
  and translations. The server owns inventory authorization and game decisions;
  client screens display synchronized state and send validated requests.
- `fabric`, `forge`, `neoforge`: loader registration and lifecycle adapters;
  gameplay and presentation belong in `common`.
- `art`: deterministic asset and server-data generation, with separate outputs.
  Edit source generators rather than generated textures, models or recipes.
- `common/src/smoke`: shared client/server checks; loader adapters live in
  `fabric/src/smoke` and `neoforge/src/smoke`. `forge/src/smoke` owns bootstrap checks.
- `common/src/ponderData`: shared Minecraft-native build-time generation of Ponder
  structures, compiled independently by both loader projects.

Optional integrations belong in the client compatibility package. Loader entry
points must check mod availability before referencing integration classes.
Declare optional metadata, exclude third-party implementations from release JARs,
and verify installed/absent configurations. Quilt uses the matching Fabric JAR.

`compat/1.20.1` (Fabric/Forge, Java 17) and `compat/26.1.2` (Fabric/NeoForge,
Java 25) are port-only branches. Synchronize only on explicit request, including
code, tests, resources, formatting and docs while retaining each port's APIs and
dependencies. Use signed merges from `main` into both ports and back into `main`,
preserving each profile's tree; `main` must descend from both synchronized tips.
Advance `.github/compat-1.20.1-ref` and `.github/compat-26.1.2-ref` to verified tips.
Keep all README compatibility tables aligned and distinguish adapters from runtime
validation. See [DEVELOPMENT.md](docs/DEVELOPMENT.md) for artifact coordination.

Versions live in `gradle.properties`. Renovate handles routine dependency updates;
Dependabot handles security only. Minecraft targets, Java toolchains, loader floors,
compatibility profiles and `mod_version` are manual decisions, not bot updates.

## Interface, resources and documentation

Documentation ownership under `docs/`: player operations in `PLAYING.md`,
`SURVIVAL.md`, `ROOMS.md`; registry/persistence/recipes in `SUPPLIES.md`; module
contracts in `ARCHITECTURE.md`; UI in `UI_STYLE.md`; dependencies in
`COMPATIBILITY.md`; build/ports/releases in `DEVELOPMENT.md`; checks in
`VERIFICATION.md`. Link to the owning guide instead of duplicating it.
Meaningful changes get one `CHANGELOG.md` entry under `## [Unreleased]`
(`Added`, `Changed`, `Fixed`); documentation cleanup and routine checks do not.

For UI/table changes, read [UI_STYLE.md](docs/UI_STYLE.md). Reuse `MahjongUi`,
`MahjongButton`, `MahjongSlider`, `MahjongEditBox` and shared render/picking geometry.
Keep the compact table footprint; tutorials belong in guides, not tooltips.

Update `en_us.json`, `ja_jp.json`, `zh_cn.json` and `zh_tw.json` together: identical
keys and format specifiers, two-space indentation, LF and alphabetical key order.
Use hierarchical dot-separated snake_case keys, `.description` and `.short`
suffixes. Rule titles use standard Riichi terminology; mechanics go in descriptions.
Resource paths/generation follow [ASSETS.md](docs/ASSETS.md) and [AUDIO.md](docs/AUDIO.md).

Guides describe current capabilities, permissions and privacy, not retired features,
speculative roadmaps or unrelated comparisons. Keep third-party attribution in
`NOTICE.md`/license notices and version support in Compatibility sections.

## Validation

Use JDK 21 and the checked-in Gradle wrapper (`--warning-mode fail`). Test ownership
and focused commands are in [VERIFICATION.md](docs/VERIFICATION.md); Ponder checks
are in [PONDER.md](docs/PONDER.md). `buildAll` includes engine, presentation,
generated resources and NeoForge dedicated-server tests; reserve it for broad changes.
Client smokes cover loader/world/packet/UI boundaries. Screenshots are opt-in;
inspect fresh PASS/FAIL markers and requested captures. Logs and screenshots stay
in ignored build output, not tracked acceptance archives.

## Versioning and releases

Keep `mod_version` on `-SNAPSHOT`; ordinary commits do not bump it. After a release,
use the next patch Snapshot as a development placeholder. For an explicit release,
reassess SemVer from actual changes or follow the user's requested version; update
the target Snapshot first if necessary. Tags are bare SemVer matching `mod_version`
without `-SNAPSHOT`; the workflow supplies the formal version to published JARs.
Move Unreleased entries into `## [x.y.z] - YYYY-MM-DD`, retain an empty Unreleased
section and update changelog links. Then advance to the next patch Snapshot.

Commits use Conventional Commits (lowercase imperative subject, no trailing period)
and the existing GPG signing setup. Commit complete batches by concern with
`git commit -S`, verify signatures and push normally. Check signing readiness with
`git signing-agent status`.
