# Training opponent analysis

The two levels, EASY and HARD, share mahjong-utils 0.7.7 through `HandAnalyzer`. Its
`ShantenWithGot.discardToAdvance`, `ShantenWithoutGot.advance` and
`goodShapeAdvance` provide structural efficiency; `waits` supplies structural
tenpai and `score` supplies legal yaku, fu and actual payments under `RuleConfig`.
Incomplete hands use labelled yaku potential, never the complete-hand scorer.
The existing Java interop boundary selects the pinned library's JVM-visible
analysis switches: it keeps input validation, every discard and the existing
decompositions, while omitting unused stock totals, kan suggestions and nested
tenpai-improvement maps. Completed score interpretations are constructed once
and compared by actual payment. Dependency upgrades must recheck this internal
Kotlin API, even though its JVM entry points are callable from Java.

`BotAnalysis` receives only a recipient view. Opponents' hand contents, physical
copy numbers, wall order and seed do not enter evaluation. Physical identities
serve visible-tile deduplication and selecting a canonical physical copy of an
equivalent legal action only. The view additionally supplies only the recipient's
temporary/permanent ron block and established/current declaration's riichi han.
Availability starts with the selected 136/108-tile composition and subtracts
visible ordinary/red faces separately. Moving a discard to the river does not
restore availability. A simulated draw consumes one available face.

Unseen counts include concealed opponents and the dead wall. Future draws are
weighted across exchangeable unseen tiles using heuristic risk and action
utilities.

EASY uses current shanten, live improving tiles, basic retention and capped
winning value. It can call for a viable yaku and fold a distant hand against an
obvious threat. HARD adds good-shape advances, viable yaku potential, weighted
winning value and opponent evidence beyond riichi. Its bounded draw/discard search
includes same-shanten improvements, with walls, combined-threat push/fold and safe
reserves in the defense assessment.

HARD expands at most three candidate actions, at most 37 draw categories each,
and two valued continuations after each draw. All legal discard faces take part
in continuation ranking. Both advancing draws and same-shanten improvements
participate; an offensive root can retreat at most one shanten. A completed
legal tsumo is taken immediately. The horizon is one draw/discard, with at most
111 draw nodes and 222 continuation leaves, each comparing dama/riichi where
eligible. Both levels use the same full draw search for replacement declarations.
Good-shape analysis is lazy at the root. Continuation leaves use immediate
efficiency and legal wait value, without implicitly enumerating yet another draw
inside good-shape analysis. Development is the difference between two evaluations
at that same leaf depth, added to the root value. Unexpanded candidates retain
their root value. Locked riichi uses its one forced discard directly.

EASY preserves a viable advancing route instead of retreating merely for a
larger raw ukeire count. The speed term divides live advances by total unseen
stock, so one exchangeable draw can contribute at most one shanten of progress,
including in three-player play. A dead or yakuless route can still be reconsidered; HARD
must actually expand an offensive retreat before accepting it. Full defence and
replacement declarations have their own safety/shape comparisons. A lower-risk
discard can retreat defensively without satisfying the offensive search gate.

A decision owns its shape and scoring caches. Shape keys contain the concealed
multiset and fixed melds. Scoring keys additionally contain bonus/red value,
winning face, ron/tsumo, one/two-han riichi and replacement-draw status. Structural
wait caches do not contain scoring or furiten. Rules, winds and visible indicators
stay fixed within a decision; unseen draws and furiten are applied at each leaf.

## Actions and defence

`TrainingBot` compares only engine-issued actions. Chi/pon outcomes include a
legal discard using `LegalActions.forbiddenAfterCall`, shared with generation
and execution. Yaku viability and actual legal waits gate opening the hand;
being tenpai or facing a threat does not itself prohibit calling. Kan and north
extraction compare replacement development, retained shape/value and declaration
risk. New-indicator effects average unseen categories for self and opponents;
no unrevealed indicator is assigned as a definite bonus.

`BotValue` scores completed waits separately for ron/tsumo and dama/riichi.
`HandBonuses` is shared with settlement, including repeated indicators and a
tile's simultaneous red/indicator bonuses. Bonus han do not supply a yaku or
meet the minimum. No ura or ippatsu is assumed. Any discarded structural wait
blocks the entire ron set; tsumo remains separate. Temporary furiten clears
after a real draw's discard, while calls follow `callsClearFuriten`. Already
declared permanent furiten remains blocked. Double riichi retains its two han;
ordinary continuation discards end eligibility for a first-turn declaration.

Incomplete own hands and conditional opposing wins use the existing point tables
for 30/40-fu scenarios, interpolating fractional estimated han. Own scenarios
average ron and the actual player-count tsumo receipts; threat scenarios use ron.
Visible opposing bonuses are counted, with concealed bonus content estimated from
public unseen-face density and concealed hand size. Opponent threat pressure is
assessed separately from hand estimates. Completed own waits use exact legal scoring
rather than heuristic estimates.

`BotDefence` builds a separate threat and risk vector for every opponent using
public riichi, meld/yakuhai content, exposed bonuses, dealer status and elapsed
turns. Genbutsu is opponent-specific. Suji only reduces the sequence component;
walls and visible honor counts retain residual pair/special-hand risk. Several
weak signals combine to increase discard risk, balanced against remaining draw
opportunities before triggering full defensive folding.

Push/fold uses live waits, realizable/estimated value, remaining draw opportunities,
opposing value, multiple threats and late-match score gaps. Full folding orders
discards by safety before efficiency, allowing completed groups to be broken.
Each candidate's resulting hand determines its attack/defence mode; a viable,
fast, valuable call is compared before folding the unchanged hand.
HARD can also keep safe reserves while continuing a valuable hand. An already
declared hand still compares legal replacement actions with its forced discard.
The search models a conditional next own turn and stops when the public remaining
draw count cannot reach that turn, evaluating candidate choices over this
one-draw/discard horizon.

## Reproduction

`./gradlew :engine:botCompare -PbotArgs=measure --console=plain` runs an explicit
warm-up and decision timing experiment. `-PbotArgs='4 TENHOU_4 HARD EASY'`
runs paired seeds 74291 onward, rotating the challenger through every seat
against a homogeneous opponent field. Use `MAHJONG_SOUL_3` for three players.
`-PbotArgs='suite 4 2'` runs timings and HARD versus EASY with four seeds in
four-player play and two seeds in three-player play (22 matches total).
An optional final seed argument, e.g. `-PbotArgs='suite 8 8 106601'`, selects an
independent seed range. `-PbotProfile` enables JDK Flight Recorder and saves the
slowest recipient snapshots plus early unannounced retreats in `engine/build`.
Use `-PbotArgs='position build/bot-slow-HARD.json'` to time one saved position,
or `inspect` in place of `position` to print candidate analysis.
`-PbotArgs='tables 4 12000 MAHJONG_SOUL_3 HARD'` interleaves four actual `Game.tick()`
loops on one thread, including their usual decision pacing. This measures the
engine's aggregate tick cost, excluding Minecraft's rendering, networking and
other server work; it is not a live-server TPS test.
This reuses `GameLifecycleTest` startup and actual engine actions/settlements;
the experiment is outside `check` and `buildAll`.

Win/deal-in rates use player-hands, multiple ron counts a deal-in once per
discarder/hand, win value excludes honba/deposits and follows the actual
three/four-player payment schedule. Points and rank are final match outcomes.
Decisions include forced actions; timings are wall-clock JVM measurements.
Seat rotations sharing a seed are correlated, and small samples cannot prove
a strength ordering or require every auxiliary metric to improve monotonically.

Initial fixed-opening baseline on this workstation: discard analysis 0.370 ms;
EASY/NORMAL/HARD mean decisions 0.443/0.346/0.314 ms (100 measured decisions,
after warm-up). The first bounded-search implementation measured HARD mean
18.348 ms, p95 23.991 ms and max 30.437 ms on the same opening. These numbers
describe one shape and are not worst-case guarantees.

## Historical three-level validation, 20 September 2026

The following baseline and performance-follow-up records describe the three-level
implementation through commit `9004fbf`. Their NORMAL comparisons and old `suite`
counts are historical; reproduce them from that revision. Current two-level
validation is recorded after these measurements.

Before the performance/valuation optimization, `suite 4 2` completed 44 matches / 461 hands with seeds 74291–74294
for four players and 74291–74292 for three players. Each match has one challenger
and three/two opponents of the indicated field level; every seed rotates the
challenger through all seats. Rows report each role within that comparison,
not comparable absolute ratings across different opponent fields.

| Rules / challenger vs field | Role | Player-hands | Win rate | Deal-in rate | Mean win points | Mean rank | Mean net points |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| TENHOU_4 / NORMAL vs EASY | NORMAL | 191 | 21.47% | 12.04% | 5775.6 | 2.000 | +7150.0 |
| TENHOU_4 / NORMAL vs EASY | EASY | 573 | 22.34% | 17.80% | 4251.6 | 2.667 | -2383.3 |
| TENHOU_4 / HARD vs NORMAL | HARD | 172 | 22.09% | 8.14% | 5081.6 | 2.063 | +4537.5 |
| TENHOU_4 / HARD vs NORMAL | NORMAL | 516 | 17.83% | 11.63% | 5131.5 | 2.646 | -1512.5 |
| MAHJONG_SOUL_3 / NORMAL vs EASY | NORMAL | 46 | 23.91% | 17.39% | 8545.5 | 2.333 | -5250.0 |
| MAHJONG_SOUL_3 / NORMAL vs EASY | EASY | 92 | 38.04% | 16.30% | 7828.6 | 1.833 | +2625.0 |
| MAHJONG_SOUL_3 / HARD vs NORMAL | HARD | 52 | 34.62% | 19.23% | 9177.8 | 1.667 | +6066.7 |
| MAHJONG_SOUL_3 / HARD vs NORMAL | NORMAL | 104 | 26.92% | 15.38% | 7064.3 | 2.167 | -3033.3 |

The four-player outcomes favor the more detailed evaluator in both comparisons.
The three-player NORMAL result is worse than EASY; HARD's better three-player
rank/points also came with more deal-ins. There are only four/two independent
seeds per comparison, no confidence intervals, and no independent holdout
tournament. These observations do not establish a stable strength ordering.
Custom scoring/furiten/bonus boundaries are covered by deterministic tests,
but custom rules have not received match-scale strength comparisons.

Final fixed-opening timings (100 measured decisions after warm-up): underlying
discard analysis mean 0.355 ms; EASY mean/p95/max 0.764/1.564/1.908 ms,
NORMAL 0.382/0.486/2.626 ms, HARD 16.839/22.483/27.335 ms.
The complete matches include more expensive hand shapes and replacement actions:

| Comparison / role | Decisions | Mean ms | p95 ms | Max ms |
| --- | ---: | ---: | ---: | ---: |
| 4p NORMAL vs EASY / NORMAL | 3063 | 1.632 | 7.766 | 75.697 |
| 4p NORMAL vs EASY / EASY | 9355 | 1.830 | 9.526 | 170.930 |
| 4p HARD vs NORMAL / HARD | 2960 | 107.679 | 578.393 | 2454.530 |
| 4p HARD vs NORMAL / NORMAL | 8812 | 1.650 | 6.840 | 94.700 |
| 3p NORMAL vs EASY / NORMAL | 652 | 4.674 | 19.771 | 402.224 |
| 3p NORMAL vs EASY / EASY | 1304 | 9.061 | 21.798 | 1840.060 |
| 3p HARD vs NORMAL / HARD | 810 | 155.792 | 813.466 | 3248.546 |
| 3p HARD vs NORMAL / NORMAL | 1571 | 6.271 | 19.534 | 1826.463 |

The node cap bounds work, not elapsed time inside mahjong-utils. Long tails remain,
including 3.25 seconds for HARD and 1.84 seconds for EASY in this sample. Bot
decisions currently execute synchronously; multiple concurrent tables and server
tick latency have not been load-tested. JVM warm-up, garbage collection and the
host also affect these timings. No wall-clock cutoff changes the selected action.

The following historical measurements were collected on the 1.21.1 mainline,
not on this compatibility profile. Current port acceptance is recorded in
[Verification](VERIFICATION.md#recorded-acceptance).

`buildAll --warning-mode fail` and both smoke source compilations passed on JDK 21.
All 103 engine tests passed, including nine consolidated bot tests covering live
availability/development, red and repeated bonuses, legal score/minimum han,
whole-wait furiten, hidden-state invariance, weak-hand folding, strong-hand pushing,
multiple threats, riichi/dama, legal calls and three-player north extraction.
The opt-in comparison completed successfully in 9m 58s and remains outside the
routine build. Local raw logs are `build/bot-batch2-build.log` and
`build/bot-comparison-final.log`; this document retains their measured results.
Fabric `:fabric:runSmokeClient` (4m 34s) and NeoForge `:neoforge:runSmokeClient` (4m 33s)
both completed with fresh `MCHJONG_CLIENT_SMOKE_PASS` markers. The newly generated
`02-dealt-table.png` screenshots in each loader's `build/smoke/evidence/screenshots`
were inspected for the own-hand, opponent-back and public-table rendering after
the recipient-view change. Their logs are `build/bot-fabric-smoke.log` and
`build/bot-neoforge-smoke.log`.

## Performance and valuation follow-up

JDK Flight Recorder on one three-player HARD/NORMAL seed (three seat rotations)
found 1,589 of 4,213 execution samples under the library's `fillImprovement` and
1,945 under `getGoodShapeAdvance`; these overlapping counts are not additive.
The bot did not consume the former's recursive improvement maps. The latter was
being calculated inside continuation leaves, silently adding another draw depth.
The follow-up disables the unused analysis, requests good-shape results only at
eligible roots, and constructs each completed score interpretation once. It
retains the library's shape decomposition and legal scoring algorithms.

Seeds 84301–84308 were used diagnostically while correcting offensive retreats,
premature folding and the linear payout estimate. On that reused three-player
NORMAL/EASY diagnostic, NORMAL's mean net points moved from -8716.7 to -3825.0
and deal-in rate from 18.88% to 14.15%; EASY's policy also changed through the
shared efficiency fixes. These are development observations, not independent
evidence of a strength gain. The frozen implementation is evaluated separately
with seeds 95601–95608 below.

| New-seed comparison | Role | Player-hands | Win rate | Deal-in rate | Mean win points | Mean rank | Mean net points |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 4p NORMAL vs EASY | NORMAL | 374 | 20.86% | 15.51% | 4273.1 | 2.625 | -2868.8 |
| 4p NORMAL vs EASY | EASY | 1122 | 24.24% | 15.69% | 4291.5 | 2.458 | +956.3 |
| 4p HARD vs NORMAL | HARD | 365 | 24.93% | 12.88% | 4551.6 | 2.313 | +1821.9 |
| 4p HARD vs NORMAL | NORMAL | 1095 | 22.01% | 15.43% | 4812.9 | 2.563 | -607.3 |
| 3p NORMAL vs EASY | NORMAL | 211 | 34.60% | 14.69% | 6789.0 | 1.875 | +5900.0 |
| 3p NORMAL vs EASY | EASY | 422 | 26.54% | 17.77% | 5876.8 | 2.063 | -2950.0 |
| 3p HARD vs NORMAL | HARD | 184 | 28.80% | 17.39% | 6128.3 | 1.917 | +775.0 |
| 3p HARD vs NORMAL | NORMAL | 368 | 30.16% | 17.93% | 6157.7 | 2.042 | -387.5 |

This new-seed run completed 112 matches / 1,134 hands in 8m 41s. Four-player
NORMAL remains behind EASY; three-player NORMAL and both HARD comparisons have
better mean rank/net points. HARD's three-player win rate and mean winning value
are lower than NORMAL's. There are only eight independent seeds per comparison,
with correlated seat rotations and no confidence intervals. These mixed outcomes
do not prove monotonic strength. No policy change was made after examining this
new-seed run.

| New-seed comparison / role | Decisions | Mean ms | p95 ms | Max ms |
| --- | ---: | ---: | ---: | ---: |
| 4p NORMAL vs EASY / NORMAL | 5903 | 6.943 | 34.204 | 201.347 |
| 4p NORMAL vs EASY / EASY | 17877 | 0.458 | 1.923 | 36.578 |
| 4p HARD vs NORMAL / HARD | 5918 | 28.221 | 90.231 | 265.149 |
| 4p HARD vs NORMAL / NORMAL | 17537 | 7.028 | 34.797 | 406.723 |
| 3p NORMAL vs EASY / NORMAL | 2927 | 10.585 | 47.359 | 398.744 |
| 3p NORMAL vs EASY / EASY | 5861 | 1.351 | 6.002 | 114.757 |
| 3p HARD vs NORMAL / HARD | 2563 | 25.967 | 94.203 | 355.890 |
| 3p HARD vs NORMAL / NORMAL | 5182 | 9.218 | 42.907 | 355.142 |

NORMAL now spends more time on legal-wait development than the baseline. The
fixed opening is not representative of that one-shanten work. HARD's much shorter
tails come from removing duplicate/nested library work, not a wall-clock cutoff
or fewer legal draw categories. Timing comparisons include changed trajectories
and are not an identical-position microbenchmark.

The original seed ranges were rerun with the same frozen policy: 44 matches /
431 hands in 3m 25s (baseline: 44 / 461, 9m 58s). Outcomes changed as decisions
changed, so both outcome and timing tables are retained rather than treating the
new sample as identical hands.

| Original-seed rerun | Role | Player-hands | Win rate | Deal-in rate | Mean win points | Mean rank | Mean net points |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 4p NORMAL vs EASY | NORMAL | 168 | 19.64% | 13.10% | 5142.4 | 2.813 | -356.3 |
| 4p NORMAL vs EASY | EASY | 504 | 23.41% | 18.85% | 4692.4 | 2.396 | +118.8 |
| 4p HARD vs NORMAL | HARD | 165 | 20.00% | 12.73% | 5760.6 | 2.188 | +1956.3 |
| 4p HARD vs NORMAL | NORMAL | 495 | 21.82% | 15.96% | 4743.5 | 2.604 | -652.1 |
| 3p NORMAL vs EASY | NORMAL | 52 | 28.85% | 28.85% | 8086.7 | 2.333 | -11950.0 |
| 3p NORMAL vs EASY | EASY | 104 | 33.65% | 12.50% | 7834.3 | 1.833 | +5975.0 |
| 3p HARD vs NORMAL | HARD | 46 | 30.43% | 8.70% | 6621.4 | 1.667 | +2300.0 |
| 3p HARD vs NORMAL | NORMAL | 92 | 26.09% | 17.39% | 8283.3 | 2.167 | -1150.0 |

NORMAL still loses in this small three-player sample, and its four-player result
has regressed relative to the baseline. Thus the changes establish specific
efficiency/defence behavior and reduce HARD latency, but do not establish a
general strength improvement for NORMAL. Longer independent paired runs and
further action-value validation remain necessary.

| Original-seed rerun / role | Decisions | Mean ms | p95 ms | Max ms |
| --- | ---: | ---: | ---: | ---: |
| 4p NORMAL vs EASY / NORMAL | 2512 | 6.975 | 39.245 | 121.228 |
| 4p NORMAL vs EASY / EASY | 7682 | 0.456 | 1.747 | 86.903 |
| 4p HARD vs NORMAL / HARD | 2730 | 24.725 | 85.346 | 248.451 |
| 4p HARD vs NORMAL / NORMAL | 8170 | 6.948 | 36.622 | 182.028 |
| 3p NORMAL vs EASY / NORMAL | 775 | 11.181 | 45.239 | 269.644 |
| 3p NORMAL vs EASY / EASY | 1554 | 1.967 | 7.394 | 244.451 |
| 3p HARD vs NORMAL / HARD | 704 | 29.090 | 94.118 | 206.976 |
| 3p HARD vs NORMAL / NORMAL | 1427 | 9.556 | 45.218 | 142.499 |

Final fixed-opening mean/p95/max milliseconds: EASY 1.063/1.749/1.991,
NORMAL 0.591/0.784/3.427, HARD 18.183/24.714/34.733; underlying discard analysis
mean 0.344 ms. The opening cost is similar to the baseline; the meaningful
reduction is in complete-match expensive shapes.

The four-table HARD three-player `Game.tick()` workload completed all four
matches (42 hands) after 8,192 aggregate ticks and 1,681 decision-version
transitions. Aggregate tick mean/p95/max was 5.910/38.831/474.224 ms; 319 ticks
(3.89%) exceeded 50 ms. This includes initial JVM warm-up and the engine's pacing
but no Minecraft/network/world overhead. Decisions remain synchronous and can
still stall a server tick. The improved sample tails are not a real-time bound;
live-server multi-table TPS and long-run latency remain unverified.

The comparison runs together cover 156 matches / 1,565 hands, plus the four-table
workload. Raw local logs: `build/bot-final-holdout.log`,
`build/bot-final-comparison.log`, `build/bot-final-tables.log`, and
`build/bot-final-engine-test.log`. The 104 engine tests include ten consolidated
bot cases: new boundaries cover uncertain combined threats, valuable calls before
folding, lower-risk alternative waits, and a three-player offensive retreat
observed in diagnostics. The HARD development witness keeps minimum shanten while
trading 20 immediate live tiles for 19 and better weighted next-turn development;
action-list reversal preserves the choice.

JDK 21 `buildAll --warning-mode fail` passed in 25s, covering engine, shared
presentation, generated resources and NeoForge dedicated-server tests; log:
`build/bot-optimization-build.log`. This follow-up changes no UI, recipient-view
contract or loader integration, so client smokes were not rerun. The preceding
batch's two loader smoke results are recorded above, not claimed as fresh runs.
The internal analysis switches remain verified with the domain library. Custom
rules retain deterministic boundary test coverage.

## Two-level consolidation

The current product offers EASY and HARD, with EASY as the default for filling
empty seats. The previous middle level added scoring/search cost without stable
independent benefit in the measured outcomes. Its advancing-only search branch,
enum value and UI/translation entries have been removed. EASY and HARD retain
their decision algorithms; this consolidation simplifies the product rather than
claiming a new strength gain. The room control cycles Empty → Easy → Hard → Empty.

The deterministic development fixture now compares EASY directly with HARD: on
seed 74318 both retain minimum shanten; EASY takes 20 immediate live tiles and
HARD takes 19 with higher weighted continuation value. Other retained boundaries
cover legal wins/calls, shared rule/scoring correctness, public-information
invariance, weak-hand folding, strong-hand pushing, and north extraction. The
expensive-riichi fixture confirms HARD can break cheap tenpai to discard genbutsu.

Direct paired comparison uses fresh seeds 106601–106608, with HARD rotating
through each seat against EASY. It completed 32 four-player matches / 346 hands
and 24 three-player matches / 194 hands. No evaluator change was made in response
to these results.

| Rules | Role | Player-hands | Win rate | Deal-in rate | Mean win points | Mean rank | Mean net points |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| TENHOU_4 | HARD | 346 | 23.41% | 15.03% | 4400.0 | 2.594 | -2981.3 |
| TENHOU_4 | EASY | 1038 | 23.51% | 13.87% | 4738.1 | 2.469 | +993.8 |
| MAHJONG_SOUL_3 | HARD | 194 | 33.51% | 12.89% | 6816.9 | 1.667 | +3562.5 |
| MAHJONG_SOUL_3 | EASY | 388 | 30.93% | 18.81% | 7446.7 | 2.167 | -1781.3 |

| Rules / role | Decisions | Mean ms | p95 ms | Max ms |
| --- | ---: | ---: | ---: | ---: |
| 4p HARD | 5476 | 25.720 | 84.452 | 328.113 |
| 4p EASY | 16513 | 0.422 | 1.821 | 105.538 |
| 3p HARD | 2628 | 26.686 | 100.769 | 357.044 |
| 3p EASY | 5396 | 1.612 | 7.015 | 99.569 |

These 56 matches / 540 hands establish direct behavior/performance observations,
not stable strength ordering. HARD leads the three-player sample but trails the
four-player sample; only eight independent seeds per rule set were used, and seat
rotations are correlated. Longer independent comparisons and four-player action
evaluation remain outstanding. Synchronous decision latency remains subject to
the limitations measured above. Logs: `build/bot-two-tier-4p.log` (2m 37s) and
`build/bot-two-tier-3p.log` (1m 25s).

### Validation

Validation on JDK 21: all 104 engine tests and `buildAll --warning-mode fail`
passed (30s initially, 8s final verification). Both client smokes produced fresh
`MCHJONG_CLIENT_SMOKE_PASS` markers: Fabric 4m 35s, NeoForge 4m 37s. The existing
room-preparation smoke now clicks the actual Easy → Hard → Empty → Easy controls
and waits for server snapshots before proceeding. Fresh `01-room-bot-*.png`
captures from both loaders and the NeoForge small-window three-player
`53-sanma-controls-bot-easy.png` were inspected. Logs are
`build/bot-two-tier-build-final.log`, `build/bot-two-tier-fabric-smoke.log`, and
`build/bot-two-tier-neoforge-smoke.log`.

### Compatibility

Room saves encode difficulty by enum name and must contain current EASY/HARD
values. As required by the repository's compatibility policy, NORMAL is not
migrated or aliased. Validation rejects unknown difficulty values; the existing
table loader retains the original unreadable save. Client and server must use
matching builds. Lobby requests still select a server-issued action index with
its decision token; clients do not submit a raw difficulty ordinal for execution.
