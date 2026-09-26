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

The existing fixture checks task discovery, default model-name localization,
brain-driven seating, saved entity recovery, assigned mounts, legal computer
play and cleanup after a task change.
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

The shared engine benchmark records below were collected on `main` with JDK 21.
Minecraft 26.1.2 loader checks use this branch's JDK 25 profile.

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

## Bot route valuation experiment

This diagnostic batch kept the one-shanten enumeration and general search limits while
reusing local fits, exact copy-capacity cache keys, precomputed group masks and
the library's full best-shanten discard set after effective one-shanten draws.
It additionally multiplied incomplete payout scenarios by route support and
retained eligible iipeikou routes after concealed kans. Defence thresholds and
recipient-information accounting were unchanged. The support multiplier was
not selected for delivery; accepted changes and verification follow below.

The diagnostic `TrainingBotTest` suite contained 13 tests. An isolated run
passed all 13 in 7.823 seconds, with no skipped tests. Existing fixtures were
extended to check complete tied tenpai-discard retention, exhausted-copy cache
invalidation, irrelevant value-honor invariance, concealed-kan iipeikou and the
discount on speculative dora-heavy attack values. The earlier route, call,
riichi/dama, defence and hidden-information regressions remain active.

This workstation's initial attempts encountered missing generated class files
and a test-results EOF. The successful run used a separate engine output
directory and Gradle project cache; these are validation-only paths, not a new
product profile. Its log is `build/bot-followup-verified-check.log`, and its fresh
XML report is under `build/bot-followup-verified-engine/test-results/test`.
The routine owning tasks remain:

```text
gradlew.bat :engine:test --tests top.skyeyefast.mchjong.engine.TrainingBotTest :spotlessKotlinCheck :spotlessMiscCheck --console=plain
gradlew.bat :engine:botCompare "-PbotArgs=suite 2 2 137601" --console=plain
```

The diagnostic comparison and both Spotless checks completed successfully in
`build/bot-followup-final-comparison.log` (2m 9s). It used seeds 137601 and 137602
for each player count, with HARD rotated through every seat against EASY: 14
matches, 103 four-player hands and 56 three-player hands. The same seed range
was recorded before the support/eligibility changes in
`build/bot-followup-before.log` (98 and 52 hands). That baseline already contained
the first allocation-only group-mask and tie-order cache optimizations; it is
not an untouched revision-to-revision microbenchmark. Both roles run the policy
of their corresponding batch, not a frozen-old-opponent tournament.

| Rules / role | Before / after player-hands | Win rate before / after | Deal-in rate before / after | Mean win points before / after |
| --- | ---: | ---: | ---: | ---: |
| TENHOU_4 / HARD | 98 / 103 | 30.61% / 33.98% | 15.31% / 13.59% | 3893.3 / 3800.0 |
| TENHOU_4 / EASY | 294 / 309 | 22.45% / 22.01% | 13.61% / 12.62% | 4807.6 / 4894.1 |
| MAHJONG_SOUL_3 / HARD | 52 / 56 | 34.62% / 28.57% | 5.77% / 5.36% | 5511.1 / 5956.3 |
| MAHJONG_SOUL_3 / EASY | 104 / 112 | 23.08% / 26.79% | 16.35% / 13.39% | 5791.7 / 6590.0 |

HARD's four-player mean rank changes from 2.750 to 2.375 and mean net points
from -912.5 to +2237.5. Its three-player mean rank stays at 1.833, while mean
net points change from +6016.7 to -300.0. The three-player deal-in count is three
in both batches; its percentage falls only because more player-hands occurred.
Four-player mean winning value still falls, and three-player winning frequency
falls despite the larger mean winning value. These are mixed observations from
two independent seeds per rule set, with correlated seat rotations, not evidence
of a general strength improvement.

| Rules / role | Decisions before / after | Mean ms before / after | p95 ms before / after | Max ms before / after |
| --- | ---: | ---: | ---: | ---: |
| TENHOU_4 / HARD | 1534 / 1598 | 52.193 / 42.200 | 213.108 / 146.290 | 801.094 / 668.050 |
| TENHOU_4 / EASY | 4424 / 4629 | 0.791 / 0.711 | 2.587 / 2.621 | 43.410 / 24.150 |
| MAHJONG_SOUL_3 / HARD | 826 / 898 | 35.604 / 33.194 | 127.538 / 129.383 | 450.110 / 400.856 |
| MAHJONG_SOUL_3 / EASY | 1617 / 1776 | 1.690 / 1.712 | 7.506 / 6.358 | 77.960 / 142.747 |

Whole-match timings cover different trajectories and cannot isolate pure
algorithmic speed. The fixed opening's HARD mean/p95/max changes from
30.314/39.754/40.976 ms to 34.399/49.062/89.457 ms; final decision-thread CPU
mean is 30.156 ms. Underlying discard analysis changes from 0.230 to 0.612 ms.
An intermediate cache-only check measured HARD mean 24.616 ms, but it is not
substituted for the final measurement. The final fixed-opening result does not
establish the distant-hand latency goal, and the earlier pre-route 17.637 ms
reference remains unmet. Synchronous decisions still have substantial tails;
these runs do not establish a server-tick bound.

Diagnostic HARD yaku occurrences are:

| Rules | Scored-yaku occurrences |
| --- | --- |
| TENHOU_4 | Chun 2, Haku 2, Hatsu 3, Ipe 2, Ippatsu 3, Pinhu 15, Richi 9, SelfWind 2, Tanyao 6, Tsumo 10 |
| MAHJONG_SOUL_3 | Chitoi 1, Haku 6, Hatsu 2, Ippatsu 1, Pinhu 1, Richi 2, RoundWind 5, Sananko 1, SelfWind 2, Tanyao 1, Toitoi 1, Tsumo 2 |

These counts overlap within wins. The four-player HARD sample contains no
sanshoku wins, compared with three before; the deterministic sanshoku discard
regression still passes. The three-player sample includes toitoi/sanankou, but
rare-yaku counts in 35/16 HARD wins do not establish route quality. No loader
build or client smoke is counted as fresh acceptance for this engine-only follow-up.

A separate two-seed diagnostic (131701-131702) is recorded in
`build/bot-refine-verified-final.log`. Against the unchanged `b25d17c` baseline,
the extra multiplier changed four-player HARD win/deal-in rates from
32.26%/12.90% to 29.70%/19.80%; three-player rates changed from 34.09%/11.36%
to 36.17%/10.64%. It did not provide consistent evidence of improvement across
the two seed ranges. Route han already include progress decay, so multiplying
the whole payout applies an additional discount to that evidence and alters
closed/open comparisons. The multiplier, its diagnostic field and dedicated
assertions were removed rather than retained as an alternate policy. The final
implementation keeps the original conditional payout semantics.

## Accepted bot route optimization

The accepted 2026-09-25 follow-up keeps the graded route evaluator and original
conditional payout semantics. It retains exact copy-capacity fit caches,
precomputed group masks, conservative head bounds, sparse target checks,
allocation-free pair/companion selection and cached continuation tie ordering.
One-shanten advances use the library's complete best-shanten discard set;
general search limits and all eligible tenpai continuations are unchanged.
Concealed kans now retain eligible iipeikou routes. No dependency, defence
threshold or recipient-information boundary changed.

All 13 focused bot tests passed in 5.848 seconds, with no failures, errors or
skips. Existing tests were extended for exhausted-copy cache separation,
irrelevant value-honor invariance, concealed-kan iipeikou and equivalence of
best-only versus full zero-shanten discard maps. The existing route, call,
riichi/dama, hidden-information, defence and north-extraction checks remain.
Kotlin/Misc Spotless checks passed. Local incremental-cache and test-output
failures were resolved for verification with an isolated engine output and
Gradle project cache; no permanent build profile or workaround was added.

The accepted test/comparison run completed in 1m 44s, recorded in
`build/bot-refine-accepted.log`. Its XML report is
`engine/build/bot-refine-verify/test-results/test/TEST-top.skyeyefast.mchjong.engine.TrainingBotTest.xml`.
The reusable owning commands are:

```text
gradlew.bat :engine:test --tests top.skyeyefast.mchjong.engine.TrainingBotTest :spotlessKotlinCheck :spotlessMiscCheck --console=plain
gradlew.bat :engine:botCompare "-PbotArgs=suite 2 2 131701" --console=plain
```

The unchanged `b25d17c` baseline is `build/bot-refine-before.log`. Both runs use
seeds 131701 and 131702 for each player count, rotating HARD through every seat
against EASY: eight four-player and six three-player matches, 93 and 44 hands
respectively. Per-rotation hand totals, role-level action/opportunity counts,
outcome metrics and all scored-yaku counts match the baseline exactly. This is
an aggregate deterministic comparison, not an assertion that individual action
traces were compared or that playing strength improved.

| Rules / role | Player-hands | Win rate | Deal-in rate | Mean win points | Mean rank |
| --- | ---: | ---: | ---: | ---: | ---: |
| TENHOU_4 / HARD | 93 | 32.26% | 12.90% | 4596.7 | 2.000 |
| TENHOU_4 / EASY | 279 | 21.15% | 17.56% | 4459.3 | 2.667 |
| MAHJONG_SOUL_3 / HARD | 44 | 34.09% | 11.36% | 7266.7 | 1.833 |
| MAHJONG_SOUL_3 / EASY | 88 | 31.82% | 23.86% | 6657.1 | 2.083 |

| Measurement / role | Mean ms before / after | p95 ms before / after | Max ms before / after |
| --- | ---: | ---: | ---: |
| Fixed opening / HARD | 38.855 / 24.376 | 63.069 / 36.130 | 86.260 / 44.092 |
| Fixed opening / EASY | 1.623 / 1.947 | 4.182 / 3.281 | 8.394 / 4.230 |
| TENHOU_4 / HARD | 48.446 / 33.406 | 171.064 / 111.216 | 488.683 / 404.741 |
| TENHOU_4 / EASY | 0.781 / 0.578 | 2.355 / 1.661 | 50.019 / 32.630 |
| MAHJONG_SOUL_3 / HARD | 51.201 / 37.134 | 192.330 / 153.089 | 572.332 / 436.463 |
| MAHJONG_SOUL_3 / EASY | 2.113 / 1.707 | 9.687 / 7.383 | 69.065 / 58.055 |

The fixed opening uses 20 warm-up and 100 measured decisions. Underlying
discard analysis changes from 0.301 to 0.321 ms. Final decision-thread CPU means
are 23.750 ms for HARD and 1.875 ms for EASY. The fixed-opening HARD wall-clock
mean falls 37.3%; full-match HARD means fall 31.0% and 27.5%. EASY's fixed-opening
mean rises despite lower full-match means, illustrating remaining timing noise.
CPU and wall-clock measurements have different scopes; neither eliminates JVM
warm-up, system load or scheduling effects.

The earlier pre-route 17.637 ms fixed-opening reference was measured in another
batch and is still lower than this run. No claim is made that the original
latency goal is fully achieved. Decisions remain synchronous, with observed
tails above 400 ms; bounded search nodes are not a server-tick time guarantee.
Only two independent seeds per rule set are used and seat rotations are
correlated. No policy change followed this accepted run; no loader build or
client smoke is counted as fresh verification.

## Cross-version acceptance

### Post-0.7.2 synchronization (2026-09-26)

This profile passed `buildAll --warning-mode fail`, both smoke compilation tasks,
the Fabric presentation tests, and final Fabric/NeoForge JAR and Spotless checks
on JDK 25. Both loaders passed focused `smokeInterface`, `smokeSettlement` and
`smokeVisibility` runs. Fresh logs contain their PASS markers; preset screens,
settlement receipts and immersive hand captures were inspected. Evidence is in
each loader's `build/smoke/{interface,settlement,visibility}-evidence` directory.
The interface run covers synchronized custom preset archives and the box-menu
lifecycle. This batch's runtime acceptance covers Fabric and NeoForge; Quilt and
optional integrations retain the separately dated records below.

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
