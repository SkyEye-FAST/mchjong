# Taiwanese sixteen-tile support: first-stage boundary and integration design

This is the requested integration design, not a claim of playable Minecraft
support. Implemented now: independent library, structure/analysis, two documented
scoring profiles, custom scoring rules and the engine analysis adapter. The
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
scoring contracts; preset identity never controls score calculation.

The library's [contract](https://github.com/SkyEye-FAST/taiwan-mahjong/blob/main/README.md) and
[source/interpretation ledger](https://github.com/SkyEye-FAST/taiwan-mahjong/blob/main/RULES.md) own the algorithm and
scoring details. Source-qualified `POCKET_COMMON` replaces the initially suggested
Taipei label because the selected source does not substantiate that geography.
`SOUTHERN_COMMON` is explicitly a composed common preset. Taichung has no preset
until independent, adequate evidence supports its choices.

## Match integration contract

The following types enter production together with a functioning engine match,
rather than registering an unusable lobby option or adding placeholder sessions:

| Type | Responsibility |
| --- | --- |
| `MahjongVariant.TAIWAN` | Variant dispatch, only when its session can start and run. |
| `TaiwanSession` | `TableSession` subclass; room authority, roster, clocks and game lifecycle. |
| `TaiwanGame` | Independent four-seat state machine, issued decisions, dealer progression. No MCR/Sichuan inheritance. |
| `TaiwanAction` | Taiwan-specific actions and decision tokens; requests select issued indices. |
| `TaiwanView` | Recipient-safe immutable hand, wall occupancy, public flowers/melds and own actions. |
| `TaiwanSettlement` | Ordered transfers, ordinary/flower components and dealer/continuation contributions. |
| `TaiwanRules` | Extend the current scoring contract with validated match-policy values. |
| `TaiwanPreset` | Complete named match-rule snapshots, with identity derived from values. |
| `TaiwanHandAnalyzer` | Sole production library adapter, already implemented. |

No shared Riichi/MCR/Sichuan hand-size switches or inherited match orchestration
are required. A Taiwan implementation shares physical tile identities, immutable
meld descriptions and room authorization only where their semantics agree.

## Independent wall and turn flow

`Tile.standard144Set()` is the shared 136 ordinary + eight flower stock; the old
MCR-named factory and validator were removed. A no-flower game takes the ordinary
136-tile set without red fives. `McrDeck`, MCR stock admission, `McrWall` and
`McrOpening` still describe MCR behavior and are not renamed into misleading
generic game components.

Taiwan needs its own wall/opening/deal implementation: four 18-stack walls with
flowers or 17-stack walls without, dealer 17 structural tiles and others 16,
then rule-specific replacement sequencing. Dice cut, packet order, reserve wall,
replacement exhaustion and opening-win timing require a selected *match* source
before implementation; the scoring source alone does not establish these facts.

The intended independent phases are initial dealing/replacement, turn, reaction,
flower victory, hand end and match end. Added kongs stay pending through robbery
arbitration. Ordinary and flower replacement origins remain distinct, even when
a scoring profile awards both. A flower victory can occur without a standard
winning hand; seven-versus-one can have a different payer from an accompanying
replacement-hand win. Settlement must retain separate components.

## Match rule dimensions

Use enum/value policies for demonstrated differences, not a boolean per rule:

| Dimension | Contract and evidence requirement |
| --- | --- |
| Stock and flower position | Scoring flower policy plus explicit dealer-relative/opening-relative position; source P uses opening-relative flowers. |
| Multiple winners | `NEAREST_CLAIM` or `ALL_CLAIMS`; W explicitly supplies nearest-claim behavior. No default inferred for P. |
| Passing a win | A policy specifies which claims are blocked and the precise reset event. W blocks self-draw too and resets on a non-winning discard/added kong; never reuse Riichi furiten or Sichuan passed-fan state. |
| Dealer continuation | Separate eligibility (win/draw/other) and integer dealer/continuation increments. P's dealer 1 and continuation 2 per repeat differ from S's omitted dealer tai. Flow/source must settle draws and multi-winner cases. |
| Base and tai payment | Integer base and per-tai unit, explicit payer selection and cap scope. Hand tai excludes dealer/continuation. Arithmetic must be checked and the transfer ledger must balance. |
| Cap | Library caps hand subtotal only; settlement decides any separately sourced continuation limit. |

Wall reserve, draw retention, flowers in opening order, payment responsibility and
passing semantics remain match-design evidence gaps. They are not silently
inherited from a neighboring variant or treated as verified regional facts.

## Acceptance boundaries

The first stage can independently answer shape, distance, effective/waiting kinds,
discard alternatives and tai with source-bearing immutable awards. It does not
register `TAIWAN` in room/network/replay enums yet, create stub game types, modify
translations, or claim loader runtime validation. Complete engine play precedes
Minecraft networking, persistence and screens in the next integration layer.

See [Verification](VERIFICATION.md#taiwan-hand-library) for focused commands.
