# Taiwanese sixteen-tile engine and integration boundary

Taiwan is the fourth built-in Minecraft variant alongside Riichi, MCR and Sichuan.
The independent library owns structure/analysis and two documented scoring
profiles; the engine owns deterministic hands, strict persistence, recipient
projections and a human/built-in-Bot match session. The common world/network/client layer
provides automatic-table preparation, native actions and a complete four-wind match.

## Ownership

`taiwan-mahjong/` is a standalone Kotlin/JVM 17 Gradle build with its own Maven
coordinate, tests and sources artifact. It is pinned as a Git submodule from
[its own repository](https://github.com/SkyEye-FAST/taiwan-mahjong).
Root `settings.gradle` includes it as a composite build; there is no dependency
on the MChjong root or engine. Its runtime
is Kotlin/JDK only. The engine declares its coordinate and relocates the embedded
library under `top.skyeyefast.mchjong.internal.taiwan`.

`TaiwanHandAnalyzer` is the sole production import boundary. It validates physical
IDs and meld provenance, translates kinds and named flowers, deduplicates public
physical aliases, and returns engine/JDK values. The library owns algorithms and
scoring. `TaiwanRules`, `TaiwanPreset` and `TaiwanWinContext` are engine-owned
contracts; preset identity never controls score calculation or turn dispatch.

The library's [contract](https://github.com/SkyEye-FAST/taiwan-mahjong/blob/main/README.md) and
[source/interpretation ledger](https://github.com/SkyEye-FAST/taiwan-mahjong/blob/main/RULES.md) own the algorithm and
scoring details. Source-qualified `POCKET_COMMON` replaces the initially suggested
Taipei label because the selected source does not substantiate that geography.
`SOUTHERN_COMMON` is explicitly a composed common preset. Taichung has no preset
until independent, adequate evidence supports its choices.

## Selected runtime contract

Checked 2026-10-04. This is a **project composition**, not a reproduction of any
one publisher's game or an official regional standard. P and S below are the
scoring ledger's sources. F and W supply independently selected match semantics.

| Source | Selected facts |
| --- | --- |
| F: [戲谷 basic play](https://www.funtown.com.tw/mj/intro/intro5.html) | Opening, flower rounds, calls/kongs, reserve, dealer succession and base-plus-tai payment, detailed below. |
| W: [網銀國際／麻將之星 rules](https://mahjongstar.waningames.com/Games/Game_Rule) | Nearest winner after supplier; pass-win also blocks self-draw, ending on a non-winning discard or completed added kong, not concealed kong. Fixed sixteen reserve is an alternative. No four-kong/four-wind abort. |
| P: [口袋 rules](https://pocket.funclub.com.tw/rule) | Hand awards, compulsory flower victories, lone-flower payer, dealer one tai and two tai per repeat. |
| S: [EASTKING differences](https://www.eastking.com.tw/blog/posts/mahjong-south-north-differences) | Southern composition omits flowers and dealer tai and caps the hand at four tai before continuation. |
| D: [NTUNHS repository, teaching appendix p. 70](https://irlib.ntunhs.edu.tw/retrieve/573/101NTCN0687006-001.pdf) | Indexed appendix describes three dice, four packet rounds and dealer's extra tile. Teaching material, not an official competition standard; direct PDF fetch timed out during this audit. |
| M: [戲谷 terminology](https://www.mjonline.com.tw/content/mj/rules_04.html) | Four successive winds form one full match; dealer wins and exhaustion retain the dealer. Concealed kongs remain covered. |

F's kong eligibility is selected instead of W's predecessor/open-kong-win
restrictions. W's additional chi/pong pass and discard restrictions are not part
of this composition. F does not fully specify these restrictions; permitting
them here is an explicit house choice, not a factual claim about F's software.
There is one nearest-winner policy, not an unimplemented multi-ron option.

## Opening and wall representation

`TaiwanOpening` accepts the already selected dealer and three dice. D supports
the three-dice teaching convention; F specifies only the sum. Initial dealer
selection is the caller's task. D's indexed passage is weaker evidence than a
directly inspected tournament rule, and remains identified as such.
The cut opens that dealer's wall, **not** a second dice-selected wall. The stock
contains four right-to-left walls, upper/lower tile per stack; seats advance
counterclockwise. Skip the dice sum of stacks; deal four-tile packets four times,
then dealer takes one. Initial replacements cycle dealer-first, deferring new
flowers to the next round.

`TaiwanWallLayout` freezes the current preset's physical contract for both stocks.
Side numbers are the engine's seat numbers `0..3`; each side has eighteen stacks
with flowers, seventeen without. Columns count from that side's right edge;
`slot = side * (stockSize / 4) + 2 * column + layer`, with upper `0`, lower `1`.
The cut is `(dealer * (stockSize / 4) + 2 * diceSum) mod stockSize`.
Ordinary traversal starts at the cut and increments slots modulo the stock size:
upper before lower, then the next column, then the next numbered side. Tail
replacement is its exact reverse, starting immediately before the cut: lower
before upper, then the preceding column/side. Reserve permission applies to both.
These numbered-side traversal and tail-layer choices are explicit project rules;
consumers must use the layout rather than infer a different compass traversal.

In 136 mode a sum of seventeen skips the whole dealer wall; eighteen also skips
the next side's first stack. For dealer three those cuts are slot zero and slot
two respectively. The 2026-10-04 source audit found no stronger primary Taiwan
specification for this no-flower overflow or tail-layer order. They are now final
rules of these composed presets, with no provisional interpretation or alternate
fallback. F supports starting after the counted stacks and replacing from the
tail; it does not establish the entire physical indexing contract above.

`Tile.standard144Set()` supplies 136 ordinary identities plus eight flowers.
`Flowers.NONE` selects 136 undecorated ordinary tiles. All physical IDs must occur
exactly once. `TaiwanWall` retains identities in fixed slots until taken, then
marks those slots absent. Front/tail cursors count takes in the layout's two
traversals; identities never rotate in storage. Reserve remains inaccessible for both ordinary and replacement
draws: default sixteen plus one per completed kong. A kong is offered only if its increased reserve still leaves a replacement;
a final flower without a legal replacement ends the hand in exhaustion. These
end-of-wall details are explicit deterministic interpretations of F's reserve,
not MCR dead-wall semantics. `Reserve.FIXED_SIXTEEN` expresses W's real alternative.

## Decisions, passing and chronology

`TaiwanGame` owns four hands, physical rivers, melds, flowers and a wall. It emits
immutable `TaiwanDecision` lists with a token, seat and indexed `TaiwanAction`s.
All three opponents answer a reaction before resolution. Win precedes pong/kong,
which precede chi; ties use distance from supplier, independent of arrival order.
This priority order is the project's explicit call arbitration contract. Claimed
discards move out of the physical river. Added kongs remain pongs until the robbery
window closes; robbery takes only the pending fourth tile. Concealed kongs cannot
be robbed. Chi/pong must be followed by a discard, not an immediate kong.
Chi uses the preceding discard; pong/open kong use any discard. Added kong needs
the drawn fourth tile. Every kong takes a tail replacement and may self-draw.

Pass-win applies whenever an issued win is declined, including choosing a call.
For W's otherwise underspecified “non-winning discard”, the engine uses the
structural wait kinds immediately before that turn's draw or call. Discarding a
kind outside that set clears passing; drawing alone does not. This interpretation
is tested and remains a documented source ambiguity. Added kong clears passing
only after robbery resolves. Compulsory flower victories bypass ordinary passing.

Ready is a discard declaration on a structurally ready hand, locking subsequent
discards to the drawn tile; no further calls/kongs. This selects F's lock-hand
description conservatively (W allows added kongs after ready). Heavenly/earthly
ready use the first uninterrupted discard. Opening wins require no previous call:
dealer's dealt win is heavenly, another player's first draw earthly, ron on a
supplier's first discard before dealer's second discard human. These chronology choices narrow P's prose;
they are not inferred from another variant's opening flags.

Initial flower victories wait until all replacement rounds finish, then use dealer
order. A nondealer can have sixteen ordinary tiles and needs no invented winning
tile. During play, the opponent's eighth flower triggers seven-versus-one before
replacement; one's own seventh/eighth requires completed replacement. Ordinary
replacement wins can add their separate score. This opening timing is a project
composition of F's round-based replacement and P's mandatory flower awards, not
an independently confirmed Pocket opening procedure.

## Payment, repeat dealer and cap

`TaiwanRules.Payment` holds base, per-tai unit, dealer tai and repeat tai. Default
units are 1/1; these are configurable accounting units, not a currency claim.
For every liable opponent the transfer is:

`base + perTai * (payable hand tai + dealerTai + continuation * repeatTai)`

Dealer/repeat additions apply only when winner or that payer is dealer. Default
increments are 1 and 2; Southern omits the 1. The entire dealer/repeat addition
sits outside the hand cap. The two-per-repeat increment is the selected 拉莊
convention, not a second charge on top of 連莊. No continuation cap is imposed.
Dealer win or exhaustion returns the same next dealer and continuation + 1;
another winner returns the next seat and zero. No next hand is started implicitly.

Eight flowers charge all three opponents. Seven-versus-one charges the lone
flower holder; an accompanying regular replacement win charges all three for its
ordinary component. A payer owes one base even when liable for both components.
For custom capped flower profiles, flower tai consumes the shared cap first, then
ordinary tai. These combined-base/cap allocation choices are project decisions:
P identifies payers but does not specify the ambiguous combined case. The immutable
settlement retains both raw scores, per-payer hand/dealer tai and balanced deltas.
Arithmetic and configuration bounds prevent payment overflow.

## Match lifecycle

The first session plays **one full match: East, South, West, North, four dealer
positions per wind**. M explicitly defines four winds per match; F defines a
circle by all four players taking the dealer role. A dealer position is completed
only when a nondealer wins. Dealer wins and exhaustive draws increment
continuation and retain that position, including the last North dealer. Every
four completed dealer positions advances the round wind; the sixteenth ends the
match. There is no time-limit, bankruptcy, automatic winner-stop or repeat cap.
The unlimited final repeat is the project's composition of those succession
clauses, rather than a claim that every publisher uses the same stop rule.
Match length is fixed here, so no speculative match-length options are added to
`TaiwanRules`. Seat zero starts as dealer; initial seating belongs to the host.

`TaiwanSession` extends `TableSession` and owns rules, the complete undecorated
136/144 stock, independent `TimeControl` allowances, one current game and completed
hand proofs. Each new hand uses the saved future seed, the settlement's next
dealer/continuation and the derived round wind. Scores start at zero and are the
exact sum of completed `TaiwanSettlement.deltas` plus the current settlement,
with checked arithmetic. Advancing or restoring never pays a hand twice.
Completed proofs are private terminal game states for validating the dealer,
score and rule chain. Native sealed hands retain the corresponding event timeline.

Human participants use the existing identity/mount authority. Built-in Bots use
the shared room controls, become ready automatically and select issued actions
only from their own recipient-safe views. Taiwan offers one built-in Bot policy;
see [Bot analysis](BOTS.md#taiwan-built-in-opponent). No human
seated, or an active exit vote, pauses decisions, allowances and result reading.
Pending responders spend their independent move allowance then reserve; timeout
passes a reaction or discards the legal drawn tile, otherwise the first discard.
Each hand replenishes reserves. Bots wait twelve active ticks and spend no clock.
They confirm nonfinal results automatically. A result remains until all humans acknowledge
or 200 active ticks pass. Partial acknowledgements and the remaining reading time
survive saves. The final result is terminal and does not accept next-hand requests.

## Persistence and privacy

`TaiwanGameState` format **1** is the only accepted private game format. It saves
the complete rules/opening, round/continuation, phase/turn, fixed wall slots and
front/tail/kong counts, physical player zones, ready/passing state, discard/call/
draw chronology, drawn tile and origin, pre-turn wait/reset provenance, pending
discard or added kong, submitted responses and terminal settlement provenance.
Legal decisions are rebuilt, never serialized. Restore checks stock conservation,
structural hand sizes, meld source seats/called tiles, wall occupancy/cursors,
replacement counts, ready restrictions and pending transactions. Submitted choices
must still match rebuilt actions; duplicate or complete pending response windows
are rejected. Terminal scores and payments are recomputed and compared with the
saved result. Decision/revision authority refreshes on restore; session hand
boundaries also advance decision tokens.

`TaiwanSession.State` format **2** saves the common room, rules,
stock, future seed, completed proofs, current game, clock reserves, decision age
and confirmations, current replay/recorder and unacknowledged archive queue.
Restore validates the entire dealer/wind/continuation chain
and scores before returning a session, creates a fresh incarnation, and requires
live presence to be observed again. `TaiwanCodec` separates private game/session
saves from recipient view documents, rejects malformed data without replacement
games, bounds game/view documents to 65,536 characters and session saves to 8 MiB,
and limits nesting to sixteen. All fields, including nullable fields, are required;
duplicates, unknown keys, invalid enum/map keys and incorrectly typed or out-of-range
integers/booleans are rejected.

`TaiwanView` is a separate immutable model. A recipient sees their complete hand,
draw identity, legal actions, passing restriction and own submitted-response flag.
Other concealed hands have only a count. Spectators (`-1`) receive no private
actions, response flag, passing restriction or clock. Flowers, physical rivers,
ready declarations and exposed melds are public. M's covered concealed-kong
semantics use four hidden identities for every other recipient, including after
completion. Declaring an added kong publicly offers that fourth tile during
robbery; this declared tile is a public focus, while response choices remain
private. Every wall slot carries only hidden/absent occupancy, even after the hand.
Completion publishes awards and payments, with no private decomposition or
automatic reveal of any opponent's hand. The selected rules do not require all
hands to be exposed. Session views expose only the recipient's clock, so clock
activation cannot reveal another player's submitted reaction.

## Native replay

`TaiwanReplay`, `TaiwanReplayHand`, `TaiwanReplayRecorder` and
`TaiwanReplayPlayback` are independent Taiwan contracts. The recorder stores
the complete initial physical wall, real three-die opening, raw dealt player zones,
rules, hand number, round wind/dealer/continuation and starting long scores.
Only accepted engine decisions append their seat, complete issued options and
selected index. Passive engine physical checkpoints supply state differences for
draws, discards, ready declarations, flowers, calls, all kongs and settlement.
Transient response collections belong to live saves and accepted decisions, rather
than physical checkpoints. Recording never executes its own rules.

Initial flower rounds and victories use decision cursor zero. Continuous flowers,
seven-versus-one, eight flowers and kong replacements retain their actual physical
slot/front/tail/kong cursors. The added-kong offer remains separate from commit;
robbery keeps the pong and consumes no replacement. Automatic events do not invent
player actions. Playback reconstructs the opening through `TaiwanGame`, reissues
and compares every option, submits the recorded index and compares every derived
event. It checks conservation after accepted actions and compares the complete
terminal state, settlement, succession and ending scores. Mismatches are rejected.

The shared match validates all sixteen dealer positions and derives final ranks
from exact long cumulative scores. Participant identities and built-in Bot flags
remain those of the real room; only human participants can fetch the archive under
the shared world replay policy. Mid-hand and partial-reaction restores reexecute
the recorder and compare it with the saved position, without paying or recording
again. Complete matches enter the shared archive queue once; early closure queues
only completed hands, and acknowledged entries are never queued again.

The shared browser shows Taiwan, hand count, completion and final standings.
The viewer uses Taiwan's 136/144 fixed slots, native public zones and receipts,
with event/decision navigation and participant viewpoints. Ordinary timelines and
settlement keep opponents' concealed hands and covered kongs hidden for the
selected viewpoint. The explicit physical-wall panel provides the original sealed
wall for review. Storage and controls are described in [Replays](REPLAYS.md).

## Minecraft integration and acceptance

`MahjongVariant.TAIWAN`, `TableSession.selectVariant` and `TableSessionCodec`
register Taiwan through the same room, identity and NBT envelope as the other
variants. `TaiwanCodec` owns the nested Taiwan data. Every session requires its
variant identity; there is no alternate world restore path.

`TableHost` prepares Taiwan only on an automatic table with cloth and matching
stock. `TaiwanDeck` selects one physical case through the existing supplies parser:
Pocket requires four copies of every ordinary kind plus eight distinct flowers
(144 tiles total); Southern uses the 136 ordinary tiles. Material, back and face
preset come from those physical tiles. Taiwan does not use `McrDeck`.

The registered view payload carries public room/rules/world/appearance state and
an optional recipient-safe session view. Action, rules, clock and next-hand
payloads carry table UUID, incarnation and the relevant current decision. Shared
reach, authenticated participant and mount checks authorize them. Lobby edits
require the host and obey world custom-rule policy. Restore renews incarnation,
reobserves mounts and invalidates pre-restore requests. Exit votes, absence pauses
and hand acknowledgements use the common lifecycle.

The Taiwan lobby and rule editor expose Pocket/Southern presets, reserve policy
and payment values; the shared editor configures move/reserve clocks. The table
screen offers server-issued chow, pong, kong, ready, win and pass choices plus
physical discard picking in seated and immersive views. `TaiwanTableScene` uses
`TaiwanWallLayout` fixed slots for seventeen/eighteen stacks per side, sixteen-tile
hands, flowers, physical rivers and up to five melds. Chinese presentation
primitives share placement and artwork without controlling Taiwan rules or wall
indexing. Results show public tai awards, transfers, cumulative scores and
next-hand confirmation; the sixteenth completed dealer position ends the match.
Opponents' hands and covered kongs stay hidden even at settlement.

Focused Fabric smoke uses one real client and three mounted human test identities.
It covers four-variant switching, both stock boundaries, initial flower replacement,
issued calls, recipient privacy, seated/immersive discard packets, exit pause,
settlement/next-hand packets, NBT restoration, final North completion and stale
incarnation/decision rejection. It also prepares and restores a Bot roster through
native room handlers and finishes a one-human/three-Bot Southern match. Test-only
human legal-action drivers are not production Bots. Both presets' complete
four-wind Bot matches have engine coverage. The smoke retrieves the completed
Taiwan match through the shared browser, opens playback, steps events, changes
viewpoints, inspects an opening flower and settlement, and returns to the list.
External Bots and convenience hints remain outside Taiwan support.
Maven Central publication and compatibility-port synchronization
are separate work.

See [Verification](VERIFICATION.md#taiwan-hand-library) for focused commands.
