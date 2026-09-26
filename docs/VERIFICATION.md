# Verification

Choose the smallest existing check that exercises the changed boundary. Full
suites belong to broad synchronizations and release acceptance, not every edit.
Dependency versions are in [Compatibility](COMPATIBILITY.md); builds and release
procedures are in [Development](DEVELOPMENT.md). Use JDK 25 on this branch.

## Build and loader checks

```text
gradlew.bat buildAll --warning-mode fail --console=plain
gradlew.bat :fabric:runSmokeClient --console=plain
gradlew.bat :neoforge:runSmokeClient --console=plain
gradlew.bat :fabric:runSmokeServer :neoforge:runSmokeServer --console=plain
```

`buildAll` covers engine, presentation, generated assets/data and loader builds.
`--no-parallel --max-workers=2` bounds resource use. Dedicated-server launcher
checks use isolated directories and `--initSettings`; they cover entrypoint and
settings initialization, not simulated worlds. The default EULA is unchanged.

Fabric and NeoForge run the shared integrated-server fixture, including worlds,
packets, inventory transactions and rendering.

## Optional integrations

Append `-PrecipeBrowser=jei` or `-PrecipeBrowser=rei` to Fabric or NeoForge client
checks, writing to a viewer-specific smoke evidence directory. Add
`-PsmokeBrowser=true` for the focused viewer run
under `build/smoke/browser-evidence`.
REI's NeoForge artifact still carries legacy `@OnlyIn` annotations. NeoForge's
development-only warning screen is hidden for that smoke profile so the actual
viewer can run; the warnings remain logged and production behavior is unchanged.
`RecipeBrowserDataSmoke` checks finite recipes and every cycling input against
the loaded recipes. Viewer checks query catalogue order, denominations, flower/red
back dyes, name/component-preserving upgrades and native container exclusions,
including a recipe page and a server-backed case at 320 × 240.

### Maid integration

```text
gradlew.bat :fabric:runSmokeClient -PwithMaid=true --console=plain
gradlew.bat :neoforge:runSmokeClient -PwithMaid=true --console=plain
```

The existing fixture checks task discovery, default model-name localization,
brain-driven seating, saved entity recovery, assigned mounts, legal computer
play and cleanup after a task change.
Selected screenshots and results use `build/smoke/maid-evidence`.

### Quilt

```text
gradlew.bat :fabric:runQuiltSmokeClient --console=plain
```

This runs the existing full client fixture through Quilt's actual `KnotClient`,
using the packaged Fabric JAR and a development-only fixture JAR. It checks the
real Quilt loader identity and does not replace dependency versions to bypass
loader validation. Evidence is stored in `fabric/build/smoke/quilt-evidence`;
`quilt-runtime.txt` records the loader, Java and Minecraft versions and the
exact Fabric JAR SHA-256. No separate Quilt release artifact is created.

## Focused interaction checks

Use the following flags with `:fabric:runSmokeClient` and `:neoforge:runSmokeClient`:

| Flag | Existing fixture and evidence |
| --- | --- |
| `-PsmokeRoom=true` | Lobby, four-language layouts, countdowns, final standings, retained members, leave/dissolve packets; `room-evidence` |
| `-PsmokeSettlement=true` | Recorded sequential yaku, han badges, points-before-grade, sextuple-yakuman emphasis, multiple winners, four-language standings with uma, resizing and result navigation; `settlement-evidence` |
| `-PsmokeInterface=true` | Box transactions, carrier synchronization, keyboard/disabled states, immersive controls; `interface-evidence` |
| `-PsmokeSeating=true` | Mounts, private deals, camera clearance, immersive controls, closed-screen camera and third-person stool; `seating-evidence` |
| `-PsmokeVisibility=true` | Four room policies, private packets and unmounted spectator permissions; `visibility-evidence` |
| `-PsmokeManual=true` | Physical shuffle, wall building, dice, packet dealing, draws, save/reload and exit; `manual-evidence` |
| `-PsmokeItems=true` | Native held/dropped supplies and exact inventory changes; `items-evidence` |
| `-PsmokePalette=true` | Material, dye and furniture presentation; `palette-evidence` |

## Test ownership and acceptance

Engine tests own scoring, reaction priority, exits, ballots, saved state and
recipient privacy. Lifecycle/manual checks exercise both player counts; enumerate
presets only where rules differ. `CompactTableLayoutTest` owns hand clearance and
picking; `TableLayoutTest` owns melds, rivers and walls. Replay storage/authorization
belongs to `ReplayStoreTest`, and bounded chunk reassembly to `ReplayTransferTest`.
Asset tests cover deterministic output, texture/model contracts, translation key
parity, duplicate keys, placeholders and literal source references.

`PhysicalSuppliesTest` exercises native recipes and component codecs on NeoForge's
test server. Shared box, point-stick and equipment fixtures use real players,
menus and worlds: clicks, shift transfers, splits, dragging, hotbar/offhand swaps,
carrier invalidation, 136/144-tile printing, reagent consumption and conservation.
They check placement/headroom, occupancy-cell removal, explosions, private/public
save separation and recoverable sanma stock. Do not introduce null-world behavior
into production to support these checks.

Full client runs also exercise ordinary-table physical handling, live rule and
control packets, replay archival, command fetch, chunk transfer, timeline seeking
and the Tenhou export button. Rare multi-winner and animation screenshots use
display-only fixtures and are not independent scoring proofs. Smoke-only mouse
hooks keep the desktop cursor free and verify its native mode.

## Scope and screenshots

For a small fix, run its owning test or focused smoke once. Add another check only
when a failure or a changed boundary justifies it. A shared implementation normally
needs one representative loader; compile each affected API adapter. Use additional
locales, viewports or player counts only for different behavior or layout risks.
Full builds and complete client suites are reserved for broad changes and releases.

Client smoke runs produce assertions and logs by default. Request exact, comma-separated
filenames with `-PsmokeScreenshots`, for example:

```text
gradlew.bat :fabric:runSmokeClient -PsmokeSeating=true -PsmokeScreenshots=41-layout-four-kans-320x240.png --console=plain
```

Choose normally one or two affected states from the owning fixture. Screenshot
filenames are defined at its capture calls. The selected profile replaces its old
evidence directory; captures stay in ignored build output. Inspect requested images
and the fresh `PASS.txt`, `FAIL.txt` and logs. Compilation verifies API use, while
visual acceptance requires viewing the affected state.

Report commands, results and coverage limits in the delivery message or PR.
Guides retain reusable instructions and current contracts; individual run logs,
screenshot inventories, timings and experiment histories remain outside tracked docs.

## Bot checks

Use `:engine:test --tests "*TrainingBotTest"` for decision regressions.
Performance experiments are opt-in; commands and interpretation are in
[Training opponents](BOTS.md#reproduction).
