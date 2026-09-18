# Riichi rules and presets

## Preset options and custom rules

The lobby's Mahjong Soul, Tenhou, M.League, League A and WRC choices are presets.
Open **Table rules** for three sections: **Preset options**, **Rule details** and
**Custom rules**. Mahjong Soul and Tenhou allow open tanyao, no/three/four red
fives, a one/two/four-yaku-han minimum and East-only or East–South matches within
the named preset. The defaults are one han and East–South. Changing those options retains the preset label;
changing other settings marks the rules as custom. Switching presets retains
supported table options and loads the remaining defaults. Rule details show all
current draft values, including enabled and disabled features, without editing.
Six paginated categories cover points/ranks, placement, scoring, match flow,
riichi/calls and responsibility payments. Apply commits the
draft; Cancel leaves the table unchanged. Reload discards the draft and reads the
current server settings. Only the seated host may apply rules in the lobby, and
all players must ready again after a change. Stale decisions are rejected.

Point fields use raw points in increments of 100. A placement bonus of 15000 means
+15.0 in the final standings. Starting points, return points and extension target
are independent; oka is derived from the return-minus-start difference and player
count. Fixed placement bonuses and every floating-player table entry are editable.
The floating count is the number of players at or above the return threshold.
Custom scoring, draw, furiten, kan, liability and ending options no longer inherit
hidden behavior from the preset name. Unchanged presets retain the defaults below.

Red-five settings select the exact composition taken from a physical box. A
choice with insufficient red fives is disabled and shows a shortage tooltip.
The server rechecks stock when applying a proposal. These settings do not
manufacture, recolor or remove stored tiles. Saves and
native replays contain the entire validated rule snapshot, not just a preset ID.

The minimum applies to every winning interpretation before selecting its payment.
Dora, ura dora, red fives and extracted-north bonuses do not satisfy it; natural
yakuman always qualify. Tenhou also excludes ippatsu from this minimum, as its
official manual specifies for bonus awards. The custom scoring section exposes
that distinction independently. A wait that cannot meet a higher minimum still
counts for furiten and formal tenpai; riichi does not guarantee an eligible ron.

East-only matches schedule one wind and East–South matches schedule two, using
three hands per wind in sanma and four in four-player play. The existing dealer
repeat and agari-yame rules apply at the selected last hand. When enabled,
extension adds at most one further wind (South or West); dealer repeats retain
priority over another player's reaching the target. Starting points, return
points and placement bonuses do not change with match length.

**End on bankruptcy** is editable in **Custom rules → Match flow**, and appears
in the preset overview. Mahjong Soul and Tenhou enable it; M.League, League A and
WRC disable it. The check is strictly below zero, after hand settlement: zero
points remain playable and negative scores remain recorded. Changing this switch
away from its preset default marks the rules as custom.

## Preset reference

Checked on 18 September 2026. Presets describe hanchan scoring and automated
play. Physical tournament administration (referees, fouls and tournament clocks)
is separate from the mod's table controls.

## Sources and versions

- [Mahjong Soul official rules](https://mahjongsoul.com/news/46).
- [Tenhou official manual](https://tenhou.net/man/), including its game-rule
  table. The separate Rating formula is not the hanchan uma.
- [M.League official competition rules](https://m-league.jp/about/).
- [Japan Professional Mahjong League competition rules](https://www.ma-jan.or.jp/activity/game_rule.html):
  the official/A column of the embedded `20260401_競技ルール0.7` sheet, including
  its separate floating-player uma table.
- [WRC official rules](https://www.worldriichi.org/wrc-rules): international
  **WRC Rules 2025**, version 20250526, plus the **29 June 2025 clarification**.
  This is the international rulebook, rather than the JPML domestic WRC variant.

The Chinese preset labels are **联盟A规则** and **WRC规则**. Both current A and
WRC permit thirteen-or-more ordinary han to reach a fourfold mangan. M.League
caps ordinary hands at sanbaiman. A counted limit is distinct from a natural
yakuman and does not trigger natural-yakuman responsibility payments.

## Points and placement

Uma values below are in thousands of points, in first-to-last order, before oka.

| Preset | Starting points | Return points | Uma | Oka |
| --- | ---: | ---: | --- | ---: |
| Mahjong Soul, four players | 25,000 | 25,000 | +15 / +5 / -5 / -15 | 0 |
| Mahjong Soul, three players | 35,000 | 35,000 | +15 / 0 / -15 | 0 |
| Tenhou, four players | 25,000 | 30,000 | +20 / +10 / -10 / -20 | +20 |
| Tenhou, three players | 35,000 | 40,000 | +20 / 0 / -20 | +15 |
| M.League | 25,000 | 30,000 | +30 / +10 / -10 / -30 | +20 |
| League A | 30,000 | 30,000 | Floating-player table below | 0 |
| WRC | 30,000 | 30,000 | +15 / +5 / -5 / -15 | 0 |

League A counts players with at least 30,000 points as floating:

| Floating players | First | Second | Third | Fourth |
| ---: | ---: | ---: | ---: | ---: |
| 0 or 4 | 0 | 0 | 0 | 0 |
| 1 | +12 | -1 | -3 | -8 |
| 2 | +8 | +4 | -4 | -8 |
| 3 | +8 | +3 | +1 | -12 |

League A and WRC split tied placement bonuses and leave final unclaimed riichi
deposits on the table. M.League shares tied ranks and distributes fractional
remainders by initial seat order. Online presets use initial seat order to
resolve equal scores. Mahjong Soul's 30,000/40,000-point extension target is
independent of its return points.

## Scoring switches

| Feature | Mahjong Soul | Tenhou | M.League | League A | WRC |
| --- | --- | --- | --- | --- | --- |
| Ippatsu | Yes | Yes | Yes | No | Yes |
| Ura dora | Yes | Yes | Yes | No | Yes |
| Kan dora and kan ura | Yes | Yes | Yes | No | Yes |
| Counted fourfold limit | Yes | Yes | No | Yes | Yes |
| Kiriage: 4 han 30 fu and 3 han 60 fu | No | No | Yes | No | Yes |
| Special double yakuman | Yes | No | No | No | No |
| Compound natural yakuman | Yes | Yes | Yes | Yes | Yes |
| Double-wind pair | 4 fu | 4 fu | 2 fu | 2 fu | 2 fu |
| Renhou | No | No | No | No | Alternative mangan |
| Bankruptcy (strictly negative points) | Yes | Yes | No | No | No |
| Abortive draws / extension / agari-yame | Yes | Yes | No | No | No |
| Multiple ron | Yes | Yes; triple ron aborts | Head bump | Head bump | Head bump |

WRC renhou is compared with the ordinary hand and the higher payment wins; it
does not add five han to other yaku or dora. Riichi kans preserve waits; the
three competition presets also preserve the hand's interpretations. League A
additionally prohibits losing a yaku. WRC permits riichi with no live-wall tiles
remaining, as specified in its clarification; M.League and A require at least one.

The scoring boundary continues to use mahjong-utils for hand interpretations,
yaku, fu and point tables. Its 0.7.7 point-table inputs are adjusted for 3-han
60-fu kiriage and for natural yakuman when the ordinary counted limit is disabled.
Candidate interpretations are compared by actual payment rather than han alone.

## Physical red fives

The selected rules specify the playing set; the first compatible box must cover
every required ordinary and red tile. Surplus tiles stay in the box. The selected
subset has matching material, back color and face design; different boxes never
pool their contents. Four-player play uses four copies of each of the 34 faces:

| Composition | Red 5m | Red 5p | Red 5s |
| --- | ---: | ---: | ---: |
| No reds | 0 | 0 | 0 |
| Three reds | 1 | 1 | 1 |
| Four reds | 1 | 2 | 1 |

Mahjong Soul and Tenhou accept all three compositions as table variants.
M.League uses three reds; A and WRC use no reds. Changing those fixed settings
creates custom rules. Sanma requires only the 108 played tiles: 2m through 8m,
including the red 5m, need not be present. The three/four-red options therefore
select two/three playable red fives in sanma.

Printing 136 or 144 blank tiles or reprinting a complete set produces no red
fives. New tables start with the no-red Mahjong Soul variant. Spare red fives may
be added alongside all four ordinary fives, allowing one box to cover multiple
choices. An ordinary five cannot substitute for a required red five, or vice versa.
One vanilla red dye crafts four red dora dyes; one red dora dye plus an ordinary
five creates its red version. One black dye crafts four undo dyes; one undo dye
plus a red five restores its ordinary face while retaining its other components.
The first compatible complete box supplies the table. Changing the rule preset
rechecks both stored boxes before Ready or Practice is offered.

Physical identity and red marking are represented separately. Walls, hands,
melds, scoring and replays retain the actual red markings. Replay metadata also
stores the set composition, so Tenhou exports report the correct `aka51`,
`aka52` and `aka53` counts even when a red tile was never drawn.
