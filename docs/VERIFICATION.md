# Verification and evidence

Choose the smallest existing check that exercises the changed boundary. Full
suites belong to broad synchronizations and release acceptance, not every edit.
Dependency versions are in [Compatibility](COMPATIBILITY.md); builds and release
procedures are in [Development](DEVELOPMENT.md). Use JDK 21 on this branch.

## Build and loader checks

```text
gradlew.bat buildAll --warning-mode fail --console=plain
gradlew.bat :fabric:runSmokeClient --console=plain
gradlew.bat :neoforge:runSmokeClient --console=plain
gradlew.bat :forge:runSmokeClient --console=plain
gradlew.bat :fabric:runSmokeServer :neoforge:runSmokeServer :forge:runSmokeServer --console=plain
```

`buildAll` covers engine, presentation, generated assets/data and loader builds.
`--no-parallel --max-workers=2` bounds resource use. Dedicated-server launcher
checks use isolated directories and `--initSettings`; they cover entrypoint and
settings initialization, not simulated worlds. The default EULA is unchanged.

The 1.21.1 Forge client check is a focused registration, item-renderer and title
screen bootstrap under `forge/build/smoke/bootstrap-evidence`. It does not establish
full gameplay acceptance. Fabric and NeoForge run the shared integrated-server
fixture, including worlds, packets, inventory transactions and rendering.

## Optional integrations

Append `-PrecipeBrowser=jei` to Fabric, Forge or NeoForge client checks. Append
`-PrecipeBrowser=emi` or `-PrecipeBrowser=rei` to Fabric or NeoForge. Profiles
write separate viewer evidence directories; Forge JEI uses `jei-bootstrap-evidence`.
`RecipeBrowserDataSmoke` checks finite recipes and every cycling input against
the loaded recipes. Viewer checks query catalogue order, denominations, flower/red
back dyes, name/component-preserving upgrades and native container exclusions,
then capture one recipe page and a server-backed case at 320 × 240.

### Maid integration

```text
gradlew.bat :fabric:runSmokeClient -PwithMaid=true --console=plain
gradlew.bat :neoforge:runSmokeClient -PwithMaid=true --console=plain
```

The existing fixture checks task discovery, default model-name localization,
brain-driven seating, saved entity recovery, assigned mounts, legal computer
play and cleanup after a task change.
Normal and small-window screenshots are under `build/smoke/maid-evidence`.

### Create and Ponder

```text
gradlew.bat :neoforge:test --tests '*CreateProcessingTest' -PwithCreate=true
gradlew.bat :neoforge:runSmokeClient -PwithCreate=true -PwithPonder=true -PsmokePonder=true -PrecipeBrowser=jei
gradlew.bat :fabric:runSmokeClient -PwithPonder=true -PsmokePonder=true
gradlew.bat :neoforge:runSmokeClient -PwithPonder=true -PsmokePonder=true
```

Native machine checks cover output blocking, printing, coloring, red conversion,
marking and atomic packing. Tutorial checks cover storyboard discovery, playback,
four languages, reload and normal/small windows. See [Create](CREATE.md) and
[Ponder](PONDER.md) for the operating and integration contracts.

## Focused interaction checks

Use the following flags with `:fabric:runSmokeClient` and `:neoforge:runSmokeClient`:

| Flag | Existing fixture and evidence |
| --- | --- |
| `-PsmokeRoom=true` | Lobby, four-language layouts, countdowns, final standings, retained members, leave/dissolve packets; `room-evidence` |
| `-PsmokeSettlement=true` | Recorded sequential yaku, han badges, points-before-grade, sextuple-yakuman emphasis, multiple winners, four-language standings with uma, resizing and result navigation; `settlement-evidence` |
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

## Cross-version acceptance

The 2026-09-24 synchronization retains `main` on Minecraft 1.21.1 / Java 21.
Its documentation and formatting changes passed Spotless and local link checks.
The compatibility branches record their own exact revisions and evidence:

| Minecraft/profile | Verified scope |
| --- | --- |
| 1.20.1 Fabric and Forge | Shared engine/resource/presentation build; full base clients; installed Touhou Little Maid/Orihime task, saved-binding, play and cleanup fixtures |
| 26.1.2 Fabric and NeoForge | JDK 25 build; full clients with JEI; pinned maid prerelease fixtures on both loaders |
| 26.1.2 Quilt 0.30.1 | Full existing client fixture on Java 25 using the packaged Fabric JAR, including world/inventory, controls, pause/rejoin, manual play and replay |
| 1.21.1 and 1.20.1 Quilt 0.30.1 | Earlier packaged Fabric JAR title-screen checks, on Java 21 and Java 17 respectively |

Quilt base acceptance does not imply that every Fabric mod combination is
validated on Quilt. Earlier Create/Ponder and other viewer checks remain
versioned baselines in their owning branch's verification guide. No optional
profile is marked rerun merely because shared compilation passed.
