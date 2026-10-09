# Training opponent analysis

## Taiwan built-in opponent

Taiwan rooms offer one built-in opponent through the shared add/remove and
fill-seat controls under world `allowBots` policy. It uses one deterministic policy.
One human and three built-in Bots can finish East, South, West and North, including
repeat dealers. External Bots are unavailable for Taiwan.

`TaiwanBot.choose` accepts only its recipient-safe `TaiwanView` and returns an
issued action index. Legal wins take priority. Discards use
`TaiwanHandAnalyzer.discards/analyze`, balancing shanten, public effective copies,
live wait kinds and remaining-copy-weighted scorable tai against attainable shape
potential and openness. Value can outweigh some effective copies. Discard retreats
are limited to one shanten, and are excluded when a ready shape is available
or fewer than 24 drawable tiles remain. Equal evaluations use the lowest physical ID.
Availability deduplicates the Bot's tiles, public rivers, exposed melds and focus.
Covered opponent kongs, opponent hands, future wall identities and seeds never
enter analysis. A simulated discard remains publicly known.

Live waits use the current scorer for ordinary discard and self-draw, current
winds, flowers and ready declaration. Caps and exclusions come from the rules;
preset names do not select behavior. Incomplete hands use copy-aware honor,
triplet, flush and concealment support estimates. Pung fits reserve a separate head;
flush fits use a six-target beam with physical copy budgets and five group slots.
Missing and scarce copies discount routes, and exhausted targets are excluded.
Alternatives compete without adding hypothetical awards; only live waits use
actual combined scorer tai. These estimates influence the bounded speed/value
tradeoff. A legal ready
discard competes with ordinary discards and wins an otherwise equal comparison.

Chows and pongs simulate their best legal mandatory discard. They require a
shanten improvement, at least two additional effective copies, or a clear scored
wait-value gain; merely offering a call supplies no benefit. Kongs first preserve
the shanten of the guaranteed continuation that discards the replacement. They
then compare one replacement/best-discard step weighted by publicly unseen
ordinary copies, including scorer-confirmed replacement wins. A small expected
gain is insufficient, and opening adds a cost. No specific replacement or flower
award is assumed. Unprofitable reactions pass.

The session waits twelve active ticks before decisions, never spends Bot clocks,
and automatically confirms nonfinal hand results. Pending delay, roster,
difficulty and confirmations survive saves. Absence and exit votes pause Bot
actions and result reading through the shared room lifecycle.

## MCR built-in opponent

MCR rooms offer one built-in opponent through the shared room Bot controls and
world `allowBots` policy. One human can play a complete sixteen-hand match with
three Bots. See [Rooms](ROOMS.md#mcr-bot-rooms) for preparation and pause controls.

`McrBot` receives only its own recipient-safe `McrView`. The engine supplies
minimum-fan qualification for issued win actions; qualifying wins take priority.
Discard analysis combines `McrHandAnalyzer`'s structural shanten and effective
tiles with `McrBotRoutes`' gradual evidence for reaching eight non-flower fan.
The library supplies special-form distances; copy-consuming targets cover seven
pairs, thirteen orphans, lesser/greater honors and knitted tiles, knitted straight,
pungs, half/full flush, outside/terminal hands, pure straight, pure shifted/triple
chows, pure shifted pungs, mixed shifted/triple chows, mixed straight, all types
and upper/middle/lower tiles. Fixed melds occupy actual slots and restrict shapes.
Missing copies discount each route continuously; an exhausted indispensable kind
excludes its target. Alternatives compete rather than adding hypothetical fan.
Regular targets are confirmed by the existing scorer for each missing kind that
could finish the hand, so an unrelated retained pair cannot invent a wait bonus.
Six-fan routes need a scorer-confirmed companion or concealed-hand contribution.
Near readiness, ordinary discard/self-draw waits are scored with current winds,
without assuming flowers, last-tile or kong bonuses.

A decision reconstructs a preferred route from its retained structure. Route
feasibility and retention can outweigh wider raw effective tiles or a one-shanten
retreat; larger discard retreats are excluded. Equal evaluations use the lowest
physical tile ID. Availability deduplicates owned tiles, public rivers, exposed
melds and the offered tile. Concealed opponents and wall identities never enter
evaluation. Route fitting uses a bounded beam and a missing-copy cutoff; its
feasibility is heuristic evidence, not a calibrated probability or an exhaustive
search of every winning hand.
`ChineseBotDanger` estimates each opponent's loss exposure from public melds,
discard history, ordinary copy availability and wall count. Flowers increase
exposure but never supply the eight-fan qualification gate. MCR ron charges every
nonwinner eight, so defence prices the discarder-only fan increment. A live
scorer-qualified ready attack discounts danger; weak or late attacks weigh it more.
Opponent flush and pung signals estimate possible value with a qualification
discount; incomplete public melds do not prove a completed scoring pattern.
Rivers provide modest evidence and never establish Riichi furiten or suji safety.

Chows and pungs use the same evaluation after their best mandatory discard and
require strict improvement over passing. Kongs must improve a guaranteed
continuation that discards the replacement, accounting for the consumed copy of
every publicly possible replacement kind. Equal outcomes retain the existing hand. The server
waits twelve ticks before Bot decisions, while the session also owns automatic
draws and flower replacements. Bot actions and completed-hand confirmations share
the normal room pause, exit and persistence lifecycle.

## Sichuan built-in opponent

SBR 2025 and T/TFMJ 01—2024 rooms offer one built-in Bot through the shared room
controls and world `allowBots` policy. One human and three Bots can finish all
eight hands. See [Rooms](ROOMS.md#sichuan-bot-rooms) for preparation and pauses.

`SichuanBot` reads only its seat's recipient-safe `SichuanView` and selects an
issued action index. Decisions are deterministic. Void selection combines suit
size, pairs, triplets, connected fragments and shape efficiency. SBR submits the
suit together with a legal secret physical first discard; heavenly void leaves
that choice unbound. Issued actions preserve the bound discard and force clearing
the void suit before ordinary discards.

`SichuanHandAnalyzer` reuses mahjong-utils regular shapes and accounts for Sichuan
seven pairs, where a four counts as two pairs. `SichuanBotValue` fits gradual,
copy-consuming targets for seven pairs, all pungs, full flush, roots and declared
kongs, including full-flush combinations. The analyzer validates and scores the
completed targets under the current rules. Target distance and scarce missing
copies discount their value; incompatible alternatives compete rather than adding
hypothetical fan. Fixed melds exclude seven pairs and restrict flush suits. Capped
payout supplies the value reward, so additional fan above `fanCap` has no marginal
reward. This bounded target fitting is heuristic evidence, not another shanten or
scoring implementation.

Discards combine route value and distance with shanten and remaining effective
copies, allowing at most one shanten of retreat. Equal evaluations prefer shape
efficiency and then the lowest physical ID. Public availability deduplicates the
Bot's tiles, rivers, melds and focus; a simulated discard remains visible. The
revealed middle tiles of a concealed kong identify all four copies. Opponent hands
and future wall identities stay hidden.
Public danger is tracked separately for each active opponent. Their void suit
cannot win; retired winners contribute no danger. Exposed groups and late walls
increase threat, with loss capped by current `fanCap`. Attack leverage accounts
for the remaining self-draw payers and `selfDrawBonus`; preserving ready shape near
the wall end also protects against the native draw settlement. An opponent's own
discard does not establish absolute safety. The same danger adjustment applies
to ordinary discards and mandatory post-pung discards. Kongs also account for
publicly possible replacement-discard risk and added-kong robbery exposure.

Legal wins are accepted. Pungs must improve the same evaluation after their best
mandatory discard. Kongs compare the concealed remainder before replacement, so
discarding the unknown replacement preserves the evaluated shape. Immediate income
under the current rules can settle a close comparison, but cannot justify worse
shanten; delayed added kongs supply no such income. Flower-pig sanctions suppress
this income preference. The server waits twelve ticks, leaves Bot clocks inactive, confirms
completed hands automatically and saves pending delay, roster and replay flags.

## Local bot service

The server administrator can enable external opponents with
`config/mchjong/bot-service.json`:

```json
{
  "endpoint": "http://127.0.0.1:8791",
  "timeout_ms": 10000
}
```

Restart the Minecraft server after changing the file. At startup it requests
`GET /v1/bots` and offers each discovered Bot as a separate automatic-table
room seat choice beside EASY and HARD. The room offers external Bots only for their advertised
player count and exact rule preset. The adapter currently advertises `TENHOU_4`
and `TENHOU_3` for its respective models. The room save keeps only the stable
Bot ID; the endpoint remains in administrator-owned configuration.

The server sends each bot's opening hand, public hand events, current legal
actions and decision token. Other players' drawn tile identities are hidden.
Each table runtime has a session UUID, while the service keeps separate state
for each seat and hand. Requests include `protocol_version: 1`, the selected
`bot_id` and exact `preset`. The reply contains only an index from that request's
legal actions. The server checks the echoed version, Bot ID, table, session,
hand, seat and decision before applying it. An unavailable service, timeout or
invalid reply appears as a short room error; the selected external seat waits
for administrator intervention. EASY and HARD continue to use
the built-in engine bot. Lobby and settlement automation stays in the game engine.

The service API exposes `GET /v1/health`, `GET /v1/bots` and
`POST /v1/decisions`. Its request/response schema and startup arguments are
documented in the service repository.

The two levels, EASY and HARD, share mahjong-utils 0.7.7 through `RiichiHandAnalyzer`. Its
`ShantenWithGot.discardToAdvance` and `ShantenWithoutGot.advance` provide
structural efficiency; `waits` supplies structural
tenpai and `score` supplies legal yaku, fu and actual payments under `RiichiRules`.
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
the general three-root budget. `RiichiHandAnalyzer.bestDiscardEfficiency` uses the
library's best-shanten mode: advancing draws have a minimum of zero, so every tied
tenpai discard is retained, including regular and special-hand alternatives,
while retreat branches are omitted. This horizon exhaustively covers immediate
tenpai advances and compares bounded same-shanten improvements.

The general search expands at most three roots and 37 draw categories per root.
Two-shanten and more distant hands consider all discard faces and score only the
two strongest continuations. A conservative numerical upper bound skips detailed
route evaluation only when it cannot change those top two. Near readiness, every tenpai continuation is scored;
in particular, dama retains its option to discard the drawn tile when compared
with locked riichi. Both advancing draws and same-shanten improvements participate;
an offensive root can retreat at most one shanten. A completed legal tsumo is taken
immediately. Decisions offering replacement declarations use search at both levels,
including their ordinary alternatives. PASS, or the baseline discard when comparing
replacement declarations, receives a search slot first, followed by replacement
declarations before competing ordinary discards.
Replacement declarations require a searched continuation within the bounded budget.
Continuation leaves use immediate efficiency and legal wait value. Development
compares endpoints at that same depth, without nested good-shape enumeration.
Unexpanded ordinary candidates retain their root value.

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
`RiichiHandAnalyzer` and `BotValue`.

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
