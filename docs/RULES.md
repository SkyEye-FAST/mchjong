# Riichi rule presets

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
| Abortive draws / bankruptcy / extension / agari-yame | Yes | Yes | No | No | No |
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

The box, not a virtual lobby toggle, determines the red-five composition. A valid
set always contains four physical copies of each of the 34 faces:

| Composition | Red 5m | Red 5p | Red 5s |
| --- | ---: | ---: | ---: |
| No reds | 0 | 0 | 0 |
| Three reds | 1 | 1 | 1 |
| Four reds | 1 | 2 | 1 |

Mahjong Soul and Tenhou accept all three compositions as table variants.
M.League accepts three reds only; A and WRC accept no reds only. In sanma the
physical box remains a full set, while 2m through 8m stay outside the live game;
the three/four-red box therefore supplies two/three playable red fives.

Printing blank tiles produces the no-red set. One vanilla red dye crafts three
red dora dyes; one red dora dye plus an ordinary five creates its red version.
The first compatible complete box supplies the table. Changing the rule preset
rechecks both stored boxes before Ready or Practice is offered.

Physical identity and red marking are represented separately. Walls, hands,
melds, scoring and replays retain the actual red markings. Replay metadata also
stores the set composition, so Tenhou exports report the correct `aka51`,
`aka52` and `aka53` counts even when a red tile was never drawn.
