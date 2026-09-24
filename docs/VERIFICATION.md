# Verification and evidence

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
then capture one recipe page and a server-backed case at 320 × 240.

### Maid integration

```text
gradlew.bat :fabric:runSmokeClient -PwithMaid=true --console=plain
gradlew.bat :neoforge:runSmokeClient -PwithMaid=true --console=plain
```

The existing fixture checks task discovery, brain-driven seating, saved entity
recovery, assigned mounts, legal computer play and cleanup after a task change.
Normal and small-window screenshots are under `build/smoke/maid-evidence`.

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
| `-PsmokeInterface=true` | Box transactions, carrier synchronization, keyboard/disabled states, immersive controls; `interface-evidence` |
| `-PsmokeSeating=true` | Mounts, private deals, zero-to-four melds, immersive controls, closed-screen camera and third-person stool; `seating-evidence` |
| `-PsmokeVisibility=true` | Four room policies, seated and unmounted spectator views in four languages and two sizes; `visibility-evidence` |
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

Inspect normal 640 × 400 and minimum 320 × 240 logical layouts: complete tile
bounds, zero-to-four melds, four-open-kan clearance, centered waiting hands and
minimal drawn-hand shifts. Placement must reject obstructed corners/headroom
without consuming items, while ignoring obstacles outside the reserved footprint.
Match controls use pointer and keyboard activation, all four locales, both player
counts and real server acknowledgements. Immersive captures retain the same
1280 × 800 canvas under changes of window size, aspect ratio and GUI scale.
Wait-preview captures cover pointer/keyboard focus, thirteen waits, indicators,
carried deposits, riichi/four-kan summaries and pointer-only hover transitions.

## Evidence review

Each client task clears its old PASS/FAIL markers. Check the fresh `PASS.txt`,
logs, `browser-checks.txt` and `survival-checks.txt` for the selected profile and
revision. Compilation does not establish installed/absent-mod, dedicated-server
or visual acceptance. Multiple simultaneous viewers require their own run.

On Windows, `tools/Review-Smoke.ps1` creates labelled contact sheets while
retaining originals. Supply the actual run start time, for example:

```powershell
./tools/Review-Smoke.ps1 -Evidence fabric/build/smoke/evidence `
    -Pattern '54-table-options-*.png' -Since $runStart -Name controls
```

Record completed checks and coverage limits with the reviewed revision. A prior
port's screenshots or a running CI job are not acceptance of a later change.

## Recorded acceptance

The synchronization from `main` at `a5d6852`, committed as `bf262d6`, passed
`buildAll --warning-mode fail` and the full Fabric/NeoForge client fixtures with
JEI on 2026-09-24. Fresh `jei-evidence` markers cover physical manual handling,
private inventory, rule selection, last-player pause/rejoin, replay and recipe
pages. The 26.1.2 recipe generator uses native string-tag ingredients for the
mahjong box's wooden slabs.

The follow-up committed as `1f07860` passed the full packaged-JAR Quilt 0.30.1
client fixture on Java 25 (PASS at 15:36 local time), and both installed-maid
fixtures (Fabric PASS at 15:39, NeoForge PASS at 15:40). Both maid runs exercised
real task discovery, brain-driven seating, saved entity/binding restoration,
seat assignment, legal play and cleanup. Small-window screenshots from both
loaders and Quilt's leave-decision screenshot were inspected. The final base
`buildAll --warning-mode fail` also passed with the optional mods absent.

The Quilt run's packaged Fabric JAR SHA-256 was:

```text
5361c1aac3a7d6cc3a07473f8f904dacc53d3a8a84fdb852ed523494919e16a6
```

Quilt base acceptance is distinct from the installed-maid Fabric and NeoForge
runs. This batch does not claim installed-maid acceptance on Quilt or validation
of every optional viewer combination. Historical palette and manual-only runs
are recorded by the baseline port at `b5d4847`; they are not additional fresh runs.
