# Training opponent analysis

The three levels share mahjong-utils 0.7.7 through `HandAnalyzer`. Its
`ShantenWithGot.discardToAdvance`, `ShantenWithoutGot.advance` and
`goodShapeAdvance` provide structural efficiency; `waits` supplies structural
tenpai and `score` supplies legal yaku, fu and actual payments under `RuleConfig`.
Incomplete hands use labelled yaku potential, never the complete-hand scorer.

`BotAnalysis` receives only a recipient view. Opponents' hand contents, physical
copy numbers, wall order and seed do not enter evaluation. Physical identities
serve visible-tile deduplication and selecting a canonical physical copy of an
equivalent legal action only. The view additionally supplies only the recipient's
temporary/permanent ron block and established/current declaration's riichi han.
Availability starts with the selected 136/108-tile composition and subtracts
visible ordinary/red faces separately. Moving a discard to the river does not
restore availability. A simulated draw consumes one available face.

Unseen counts include concealed opponents and the dead wall. Weighting future
draws by these counts assumes exchangeable unseen tiles; it is not knowledge of
live-wall contents. Risk and action utilities are uncalibrated heuristics.

EASY uses current shanten, live improving tiles, basic retention and capped
winning value. It can call for a viable yaku and fold a distant hand against an
obvious threat. NORMAL adds good-shape advances, viable yaku potential, weighted
winning value and opponent evidence beyond riichi. HARD adds draw/discard
development, walls, combined-threat push/fold assessment and safe-tile reserves.

HARD expands at most three candidate actions, at most 37 draw categories each,
and two valued continuations after each draw. All legal discard faces take part
in continuation ranking. Both advancing draws and same-shanten improvements
participate; an offensive root can retreat at most one shanten. A completed
legal tsumo is taken immediately. The horizon is one draw/discard, with at most
111 draw nodes and 222 continuation leaves, each comparing dama/riichi where
eligible. Other levels use that same search for replacement declarations.
Unexpanded candidates retain the same evaluator's leaf value.

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

`BotDefence` builds a separate threat and risk vector for every opponent using
public riichi, meld/yakuhai content, exposed bonuses, dealer status and elapsed
turns. Genbutsu is opponent-specific. Suji only reduces the sequence component;
walls and visible honor counts retain residual pair/special-hand risk. These
scores are not calibrated deal-in probabilities or monetary expectations.

Push/fold uses live waits, realizable/estimated value, remaining draw opportunities,
opposing value, multiple threats and late-match score gaps. Full folding orders
discards by safety before efficiency, allowing completed groups to be broken.
HARD can also keep safe reserves while continuing a valuable hand. An already
declared hand still compares legal replacement actions with its forced discard.
The search models a conditional next own turn and stops when the public remaining
draw count cannot reach that turn. It does not model every intervening opponent
call, win or draw; its finite horizon can miss longer plans.

## References

Reviewed on 20 September 2026. These are algorithm references, not runtime
dependencies. Implementation is original Java and does not copy the projects'
empirical constants or assume their rule environments.

- [mahjong-utils documentation](https://github.com/ssttkkl/mahjong-utils): existing
  MIT-licensed dependency; its notice ships in the engine resources.
- [tenhou-python-bot hand_builder.py](https://github.com/MahjongRepository/tenhou-python-bot/blob/dev/project/game/ai/hand_builder.py):
  weighted second-level ukeire after the next best discard;
  [defence](https://github.com/MahjongRepository/tenhou-python-bot/tree/dev/project/game/ai/defence)
  and [riichi.py](https://github.com/MahjongRepository/tenhou-python-bot/blob/dev/project/game/ai/riichi.py)
  separate opponent threats and compare dama value with declaration value.
  [MIT license](https://github.com/MahjongRepository/tenhou-python-bot/blob/dev/LICENSE.txt).
- [mahjong-helper](https://github.com/EndlessCheng/mahjong-helper): weighted
  development, same-shanten improvement, wait value and per-opponent danger.
  [MIT license, Copyright (c) 2018 Σndless](https://github.com/EndlessCheng/mahjong-helper/blob/master/LICENSE).
- [Kurita and Hoki, 2019](https://arxiv.org/abs/1904.07491): bounded abstract
  search and separate outcomes inspire the finite horizon, not a reproduction
  of its models. Copyright IEEE; no paper text, figures or model assets are bundled.

## Reproduction

`./gradlew :engine:botCompare -PbotArgs=measure --console=plain` runs an explicit
warm-up and decision timing experiment. `-PbotArgs='4 TENHOU_4 HARD NORMAL'`
runs paired seeds 74291 onward, rotating the challenger through every seat
against a homogeneous opponent field. Use `MAHJONG_SOUL_3` for three players.
`-PbotArgs='suite 4 2'` runs timings, both adjacent-level comparisons with four
seeds in four-player play and two seeds in three-player play (44 matches total).
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

## Recorded validation, 20 September 2026

The final `suite 4 2` run completed 44 matches / 461 hands with seeds 74291–74294
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

`buildAll --warning-mode fail` and both smoke source compilations passed on JDK 21.
All 103 engine tests passed, including nine consolidated bot tests covering live
availability/development, red and repeated bonuses, legal score/minimum han,
whole-wait furiten, hidden-state invariance, weak-hand folding, strong-hand pushing,
multiple threats, riichi/dama, legal calls and three-player north extraction.
The opt-in comparison completed successfully in 9m 58s and remains outside the
routine build. Local raw logs are `build/bot-batch2-build.log` and
`build/bot-comparison-final.log`; this document retains their measured results.
Fabric `:runSmokeClient` (4m 34s) and NeoForge `:neoforge:runSmokeClient` (4m 33s)
both completed with fresh `MCJHONG_CLIENT_SMOKE_PASS` markers. The newly generated
`02-dealt-table.png` screenshots in each loader's `build/smoke/evidence/screenshots`
were inspected for the own-hand, opponent-back and public-table rendering after
the recipient-view change. Their logs are `build/bot-fabric-smoke.log` and
`build/bot-neoforge-smoke.log`.
