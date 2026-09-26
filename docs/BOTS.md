# Training opponent analysis

The two levels, EASY and HARD, share mahjong-utils 0.7.7 through `HandAnalyzer`. Its
`ShantenWithGot.discardToAdvance` and `ShantenWithoutGot.advance` provide
structural efficiency; `waits` supplies structural
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

EASY uses current shanten, live improving tiles, route retention and capped
winning value. It can call for a viable yaku and fold a distant hand against an
obvious threat. HARD adds exact tenpai development, weighted winning value
and opponent evidence beyond riichi. Its bounded draw/discard search
includes same-shanten improvements, with walls, combined-threat push/fold and safe
reserves in the defense assessment.

For every eligible one-shanten candidate, HARD enumerates every live effective
draw face and every legal discard reaching tenpai. Each continuation uses actual
ron/tsumo waits, whole-hand furiten and payments, comparing dama and a legal riichi
declaration. These roots have their own decision-local cache and do not consume
the general three-root budget. `HandAnalyzer.bestDiscardEfficiency` uses the
library's best-shanten mode at these draws: the minimum is zero, so every tied
tenpai discard is retained, including regular and special-hand alternatives,
while retreat branches are omitted. Non-advancing draws retain the immediate estimate;
this horizon is exhaustive for immediate tenpai advances, not for all future play.

The general search expands at most three roots and 37 draw categories per root.
Two-shanten and more distant hands consider all discard faces and score only the
two strongest continuations. A conservative numerical upper bound skips detailed
route evaluation only when it cannot change those top two. Near readiness, every tenpai continuation is scored;
in particular, dama retains its option to discard the drawn tile when compared
with locked riichi. Both advancing draws and same-shanten improvements participate;
an offensive root can retreat at most one shanten. A completed legal tsumo is taken
immediately. Both levels use the general search for replacement declarations.
Continuation leaves use immediate efficiency and legal wait value. Development
compares endpoints at that same depth, without nested good-shape enumeration.
Unexpanded candidates retain their root value.

## Incomplete-hand routes

`BotYakuPotential` evaluates pinfu, tanyao, yakuhai, iipeikou, sanshoku, ittsu,
chanta/junchan, toitoi, sanankou, shousangen, honitsu/chinitsu, chiitoitsu and
kokushi. Compulsory sequence and dragon templates consume distinct tile copies;
fixed melds restrict the remaining slots and shapes. Pair/triplet role allocation
also consumes distinct kinds, so a quad cannot count as two chiitoitsu pairs.
Pinfu and outside-hand fits inspect retained heads, complete groups and fragments,
with pinfu distinguishing two-sided support from edge/closed fragments.

The diagnostic `missing` value is a structural deficit, not another exact shanten
number. Its exponential decay supplies gradual `progress` as supporting tiles are
kept or discarded. This is heuristic evidence, not a calibrated completion
probability or a legal-yaku assertion. A target requiring unavailable copies is
excluded. Scores, custom yaku minima and completed waits remain owned by
`HandAnalyzer` and `BotValue`.

Alternatives compete: the selected plan has one primary route and at most one
discounted compatible companion. Sequence templates do not accumulate with
triplet or seven-pairs templates. Suit restrictions, outside/simple conflicts and
mandatory dragon yakuhai are accounted for before combining evidence. Route
retention is capped below a shanten step; it does not replace the offensive
retreat gate or public-information defence.

An undeclared closed hand receives only a distance-discounted riichi option when
the deposit and remaining-wall conditions permit it. Opening loses that option
and any closed-only routes through the resulting-hand evaluation. Calls compare
this with the unchanged PASS state, including shanten, live advances and value;
their separate safety adjustment depends on public threat pressure.

The fractional yaku estimate already includes route-progress decay. It feeds
conditional payout scenarios, while shanten and live advances separately express
speed. Concealed kans preserve closed-only iipeikou eligibility but exclude pinfu
and special hands.

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
Route caches pack exact concealed counts and available copy capacity into
integer keys alongside fixed melds. Pinfu and outside fits have decision-local
caches containing only their relevant kinds, with the group family, remaining
slots and sequence requirement. Availability remains part of every fit key;
an exhausted target cannot reuse a live target's result. Group masks and
value-pair candidates are precomputed, with original forward/reverse tie orders
preserved. Sparse target checks, direct pair counts and companion selection
avoid temporary collection pipelines in the search leaves. Continuation states
cache discard identity sums and canonical tie-order strings.
A copy-count bound skips head assignments only when they cannot improve the
best fit, without changing the search roots or the retained greedy tie orders.
Fractional payout scenarios interpolate cached integer-han endpoints.

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
warm-up and decision timing experiment. It prints wall-clock mean/percentiles
and decision-thread `cpu_ms` using JDK thread CPU accounting. CPU time helps
distinguish computation from scheduling delays; it excludes work on other JVM
threads and is not a replacement for the server-visible wall-clock latency.
`-PbotArgs='4 TENHOU_4 HARD EASY'`
runs paired seeds 74291 onward, rotating the challenger through every seat
against a homogeneous opponent field. Use `MAHJONG_SOUL_3` for three players.
`-PbotArgs='suite 4 2'` runs timings and HARD versus EASY with four seeds in
four-player play and two seeds in three-player play (22 matches total).
An optional final seed argument, e.g. `-PbotArgs='suite 8 8 106601'`, selects an
independent seed range. `-PbotProfile` enables JDK Flight Recorder and saves the
slowest recipient snapshots plus early unannounced retreats in `engine/build`.
Use `-PbotArgs='position build/bot-slow-HARD.json'` to time one saved position,
or `inspect` in place of `position` to print candidate analysis. Inspection runs
the same candidate selection/search as play and includes calls with their planned
discards, exclusion reasons, route deficits/progress, selected plans, legal waits,
speed/retention/value/legality terms, defence/action adjustments, development and
final utility. `-PbotArgs='hand 123m123p12457889s'` inspects a compact fixture;
append `legal` to generate riichi actions as well. `opening 74318` inspects a
seeded opening. These inputs are confined to the development harness.
`-PbotArgs='tables 4 12000 MAHJONG_SOUL_3 HARD'` interleaves four actual `Game.tick()`
loops on one thread, including their usual decision pacing. This measures the
engine's aggregate tick cost, excluding Minecraft's rendering, networking and
other server work; it is not a live-server TPS test.
This reuses `GameLifecycleTest` startup and actual engine actions/settlements;
the experiment is outside `check` and `buildAll`.

The comparison also prints scored-yaku occurrence counts per role. A winning hand
can contribute several different yaku; these counts are not disjoint percentages.
Win/deal-in rates use player-hands, multiple ron counts a deal-in once per
discarder/hand, win value excludes honba/deposits and follows the actual
three/four-player payment schedule. Points and rank are final match outcomes.
Decisions include forced actions; timings are wall-clock JVM measurements.
Seat rotations sharing a seed are correlated, and small samples cannot prove
a strength ordering or require every auxiliary metric to improve monotonically.

## Persistence

Room saves encode difficulty by enum name and require current EASY/HARD values.
Validation rejects unknown values; the table loader retains the original
unreadable save. Client and server use matching builds. Lobby requests select a
server-issued action index with its decision token.
