# Training opponent analysis

The three levels share mahjong-utils 0.7.7 through `HandAnalyzer`. Its
`ShantenWithGot.discardToAdvance`, `ShantenWithoutGot.advance` and
`goodShapeAdvance` provide structural efficiency; `waits` supplies structural
tenpai and `score` supplies legal yaku, fu and actual payments under `RuleConfig`.
Incomplete hands use labelled yaku potential, never the complete-hand scorer.

`BotAnalysis` receives only a recipient view. Opponents' hand contents, physical
copy numbers, wall order and seed do not enter evaluation. Physical identities
serve visible-tile deduplication and returning the selected legal action only.
Availability starts with the selected 136/108-tile composition and subtracts
visible ordinary/red faces separately. Moving a discard to the river does not
restore availability. A simulated draw consumes one available face.

Unseen counts include concealed opponents and the dead wall. Weighting future
draws by these counts assumes exchangeable unseen tiles; it is not knowledge of
live-wall contents. Risk and action utilities are uncalibrated heuristics.

EASY uses current shanten, live improving tiles and basic retention. NORMAL adds
good-shape advances, viable yaku potential and actual weighted ron/tsumo value.
HARD evaluates at most three candidate discards, all at most 37 draw categories,
and the best two leaf continuations after each draw. Both advancing draws and
same-shanten improvements participate. Every continuation's structural shape
comes from the existing library; all legal discard faces participate in leaf
ranking. The horizon is one draw/discard, with at most 111 draw nodes and 222
fully valued continuation leaves. A decision owns its shape and scoring caches;
their keys include shape, declared melds, red/bonus value and riichi status as
applicable. Rules, winds and visible indicators stay fixed within that decision.

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
