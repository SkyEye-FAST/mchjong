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

## Bot route evaluation

The 2026-09-25 route evaluator was checked on JDK 21 with:

```text
gradlew.bat :engine:test --tests top.skyeyefast.mchjong.engine.TrainingBotTest :spotlessKotlinCheck :spotlessMiscCheck :engine:botCompare "-PbotArgs=suite 1 1 127601" --console=plain
```

All 13 focused bot tests passed in 5.649 seconds. Three consolidated additions
cover route-preserving discards, gradual copy-aware evidence and exact one-shanten
search beyond the general root budget. The positions cover sanshoku, ittsu,
pinfu/iipeikou, triplet versus sequence structure, flushes, chiitoitsu and an
offensive-retreat counterexample. Assertions also check conservative beam bounds,
quad/pair distinction, fixed-meld restrictions and reconstruction of diagnostic
utility. Existing legal-call, riichi/dama, furiten, red-stock, public-information,
defence and sanma north-extraction assertions remain active. Settlement progress
is checked alongside immediate winning-action priority.

Before and after use seed 127601 for each player count, rotating HARD through
every seat against EASY. The baseline includes the shared settlement-progress
repair and the scored-yaku counters, with the preceding evaluation policy.
Each run contains seven matches: 60 hands before and 62 after. Both difficulty
levels use the corresponding revision; this is a paired-seed behaviour check,
not a frozen-old-opponent tournament. Only one independent seed per player count
is represented, and seat rotations are correlated.

| Rules | Revision / role | Player-hands | Win rate | Deal-in rate | Mean win points |
| --- | --- | ---: | ---: | ---: | ---: |
| TENHOU_4 | Before / HARD | 36 | 33.33% | 8.33% | 7066.7 |
| TENHOU_4 | After / HARD | 39 | 35.90% | 5.13% | 5100.0 |
| TENHOU_4 | Before / EASY | 108 | 21.30% | 16.67% | 4313.0 |
| TENHOU_4 | After / EASY | 117 | 20.51% | 19.66% | 5275.0 |
| MAHJONG_SOUL_3 | Before / HARD | 24 | 25.00% | 4.17% | 7283.3 |
| MAHJONG_SOUL_3 | After / HARD | 23 | 26.09% | 13.04% | 6766.7 |
| MAHJONG_SOUL_3 | Before / EASY | 48 | 29.17% | 25.00% | 9814.3 |
| MAHJONG_SOUL_3 | After / EASY | 46 | 32.61% | 15.22% | 7393.3 |

HARD's four-player mean rank changes from 2.000 to 1.750, with mean net points
from +15850 to +12225. Its three-player mean rank changes from 2.000 to 2.333,
with mean net points from +133.3 to -6300. Four-player win/deal-in rates improve
in this sample, but HARD's mean winning value falls in both player counts and
three-player deal-ins increase from one to three. These mixed outcomes do not
establish an overall strength or average-value improvement.

| Rules | Revision / role | Decisions | Mean ms | p95 ms | Max ms |
| --- | --- | ---: | ---: | ---: | ---: |
| TENHOU_4 | Before / HARD | 485 | 28.709 | 104.834 | 315.148 |
| TENHOU_4 | After / HARD | 505 | 29.938 | 108.139 | 234.592 |
| TENHOU_4 | Before / EASY | 1483 | 0.703 | 2.647 | 30.411 |
| TENHOU_4 | After / EASY | 1462 | 0.697 | 2.604 | 10.511 |
| MAHJONG_SOUL_3 | Before / HARD | 352 | 21.923 | 68.783 | 227.180 |
| MAHJONG_SOUL_3 | After / HARD | 343 | 28.070 | 80.586 | 350.687 |
| MAHJONG_SOUL_3 | Before / EASY | 715 | 1.236 | 5.818 | 39.831 |
| MAHJONG_SOUL_3 | After / EASY | 674 | 1.674 | 7.857 | 50.001 |

For the unchanged fixed opening, warmed HARD mean/p95/max changes from
17.637/23.308/28.083 ms to 28.769/35.351/38.053 ms. EASY changes from
0.791/1.341/4.285 ms to 0.984/2.214/2.491 ms; underlying discard efficiency
changes from 0.254 to 0.240 ms. The distant-hand latency goal is therefore not
fully met: the fixed-opening HARD mean increases by about 63%, despite bounded
nodes, route caching, reusable buffers and conservative rank bounds. Complete-match
timings also cover different trajectories and cannot isolate one search tier.
Decisions remain synchronous; these measurements are not a server-tick guarantee.

Scored-yaku occurrences for HARD are retained below. They overlap within a win;
the sample contains only 12/14 four-player and 6/6 three-player HARD wins.

| Rules / revision | Scored-yaku occurrences |
| --- | --- |
| 4p / Before | Chanta 2, Chun 2, Hatsu 2, Pinhu 2, Richi 1, RoundWind 2, Sanshoku 1, SelfWind 1, Tanyao 3, Tsumo 3, WRichi 2 |
| 4p / After | Chanta 1, Chitoi 1, Chun 3, Hatsu 1, Ippatsu 1, Pinhu 3, Richi 1, RoundWind 1, Sanshoku 1, Tanyao 2, Tsumo 2, WRichi 2 |
| 3p / Before | Chanta 1, Chitoi 2, Haku 2, Hatsu 2, Honitsu 1, Rinshan 1, RoundWind 1 |
| 3p / After | Chitoi 2, Haku 1, Hatsu 2, Ipe 1, Ippatsu 1, Richi 1 |

The route regression positions, not rare-yaku occurrence counts in these few
matches, establish the specific discard/search behaviour. Raw local evidence is
`build/bot-routes-baseline.log` and `build/bot-routes-final.log`; the latter
completed all focused checks and comparisons successfully in 42 seconds.
The intermediate diagnostic comparison has identical final outcomes after the
subsequent allocation-only optimizations. Loader and client smoke checks were
not rerun for this engine-only change.

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
