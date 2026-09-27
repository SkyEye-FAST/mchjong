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
equivalent legal action only. The view additionally supplies the recipient's
temporary/permanent ron block, established/current declaration's riichi han, and
public per-opponent masks of passed discards after accepted riichi.
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

For every eligible one-shanten candidate, HARD considers every live draw face.
Advancing draws enumerate every discard reaching tenpai. Each ready continuation uses actual
ron/tsumo waits, whole-hand furiten and payments, comparing dama and a legal riichi
declaration. Non-advancing draws also compare the two widest continuations with
keeping the existing hand, allowing connected shapes to improve before shanten
decreases. The retained hand includes the simulated discard in its furiten state.
These roots have their own decision-local cache and do not consume
the general three-root budget. `HandAnalyzer.bestDiscardEfficiency` uses the
library's best-shanten mode: advancing draws have a minimum of zero, so every tied
tenpai discard is retained, including regular and special-hand alternatives,
while retreat branches are omitted. This horizon exhaustively covers immediate
tenpai advances and compares bounded same-shanten improvements.

The general search expands at most three roots and 37 draw categories per root.
Roots are selected from speed, value and defence non-dominated candidates;
the unchanged PASS or discard and a replacement declaration receive reserved
consideration when relevant. Static utility orders equal-priority work, while
unexpanded routes do not compete against a next-turn
forecast. At distant leaves, two non-dominated continuations retain distinct
speed, value and safety; near readiness, every tenpai continuation is scored;
in particular, dama retains its option to discard the drawn tile when compared
with locked riichi. Both advancing draws and same-shanten improvements participate;
an offensive root can retreat at most one shanten. A completed legal tsumo is taken
immediately. Decisions offering replacement declarations use search at both levels,
including their ordinary alternatives. PASS, or the baseline discard when comparing
replacement declarations, receives a search slot first, followed by replacement
declarations before competing ordinary discards.
Replacement declarations require a searched continuation within the bounded budget.
Continuation leaves use immediate efficiency and legal wait value. Current action
costs and the terminal state use one common utility scale; an expanded candidate
adds the next discard's risk once at that later step. No-next-turn decisions use
the current state as their common endpoint.

## Incomplete-hand routes

`BotYakuPotential` evaluates pinfu, tanyao, yakuhai, iipeikou, sanshoku, ittsu,
chanta/junchan, toitoi, sanankou, shousangen, honitsu/chinitsu, chiitoitsu and
kokushi. Compulsory sequence and dragon templates consume distinct tile copies;
fixed melds restrict the remaining slots and shapes. Pair/triplet role allocation
also consumes distinct kinds, so a quad cannot count as two chiitoitsu pairs.
Pinfu and outside-hand fits inspect retained heads, complete groups and fragments,
with pinfu distinguishing two-sided support from edge/closed fragments.

The `missing` value is a structural deficit, not another exact shanten
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

An undeclared closed hand receives a distance-discounted riichi option in route
retention when the deposit and remaining-wall conditions permit it. Conditional
payout includes one han for the completed declaration. Both levels retain the
option. Opening loses that option and any closed-only routes through the
resulting-hand evaluation. Calls compare
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
meet the minimum. For legal riichi wins with ura enabled, conditional payout
averages concealed-indicator scenarios over the remaining unseen tiles, without
replacement and after removing the winning tile. Each scenario retains the
scored yaku and fu and applies the configured point-table limits. Ura never
changes legal-yaku eligibility; ippatsu is not assumed. Any discarded structural wait
blocks the entire ron set; tsumo remains separate. Temporary furiten clears
after a real draw's discard, while calls follow `callsClearFuriten`. Already
declared permanent furiten remains blocked. Double riichi retains its two han;
ordinary continuation discards end eligibility for a first-turn declaration.
Riichi's forfeited deposit is priced using the same payout-utility scale as the
candidate hand, weighted by the approximate chance of not winning. Public threat
pressure and the remaining draw horizon separately penalize locked defence.

Incomplete own hands and conditional opposing wins use the existing point tables
for 30/40-fu scenarios, interpolating fractional estimated han. Own scenarios
average ron and the actual player-count tsumo receipts; threat scenarios use ron.
Visible opposing bonuses are counted, with concealed bonus content estimated from
public unseen-face density and concealed hand size. Opponent threat pressure is
assessed separately from hand estimates. Completed own waits use exact legal
scoring with the conditional ura scenarios described above.

`BotDefence` builds a separate threat and risk vector for every opponent using
public riichi, meld/yakuhai content, exposed bonuses, dealer status and elapsed
turns. Genbutsu is opponent-specific. Discards that pass the ron response window
after an accepted riichi are also safe against that player. Public event order
establishes this evidence; confirmed kan/north declarations clear that player's
accumulated mask. Suji only reduces the sequence component;
walls and visible honor counts retain residual pair/special-hand risk. Several
weak signals combine to increase discard risk, balanced against remaining draw
opportunities before triggering full defensive folding.

Four fixed opposing melds leave only a single-tile pair wait, so their risk vector
uses visible pair availability without sequence or special-hand components. Four
fixed triplet/kan groups also establish two han of toitoi in conditional payout.

On the final discard with no live wall remaining, a no-more-dangerous alternative
can preserve formal tenpai or nagashi mangan instead of future development value.
Formal tenpai shares settlement's structural-wait predicate, including dead and
yakuless waits. Nagashi eligibility also shares settlement's rule checks; another
player's qualifying nagashi replaces noten payments. This comparison prioritizes
the current hand's draw settlement while retaining the ordinary danger estimate.

Push/fold uses live waits, realizable/estimated value, remaining draw opportunities,
opposing value weighted by its public threat pressure, multiple threats and
late-match score gaps. Riichi supplies full pressure; uncertain open hands supply
their estimated pressure. Full folding orders
discards by safety before efficiency, allowing completed groups to be broken.
Each candidate's resulting hand determines its attack/defence mode; a viable,
fast, valuable call is compared before folding the unchanged hand.
HARD can also keep safe reserves while continuing a valuable hand. An already
declared hand still compares legal replacement actions with its forced discard.
The search models a conditional next own turn and stops when the public remaining
draw count cannot reach that turn, evaluating candidate choices over this
one-draw/discard horizon.
