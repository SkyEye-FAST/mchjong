# Taiwanese sixteen-tile engine and integration boundary

This is the requested integration design, not a claim of playable Minecraft
support. Implemented now: independent library, structure/analysis, two documented
scoring profiles, the analysis adapter and a deterministic engine-only single hand. The
existing playable variants remain Riichi, MCR and Sichuan.

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

## Selected single-hand contract

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
flowers to the next round. The linear index wraps when a 136-tile wall has only seventeen
stacks. That overflow convention is a project decision pending a stronger source.

`Tile.standard144Set()` supplies 136 ordinary identities plus eight flowers.
`Flowers.NONE` selects 136 undecorated ordinary tiles. All physical IDs must occur
exactly once. `TaiwanWall` rotates at the cut, draws from the front and replaces
from the tail. Reserve remains inaccessible for both ordinary and replacement
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

## Acceptance boundary

The engine can run a full single hand from a physical permutation or seed, expose
legal decisions, and return a settlement. Player snapshots contain all information
and must not be sent to clients. No `TaiwanSession`, recipient `TaiwanView`, save,
replay, bot, network, UI or loader integration exists. `MahjongVariant.TAIWAN` is
not registered. Library tests establish scoring semantics; engine tests establish
this explicit composition, not unknown regional rules or Minecraft runtime play.

See [Verification](VERIFICATION.md#taiwan-hand-library) for focused commands.
