# Terminology and localization

Use this guide when writing or reviewing MChjong's localized text. Interface
translations are in `common/src/main/resources/assets/mchjong/lang/`; custom
award names and descriptions also appear in `engine/src/main/resources/yaku_sets/`.
Choose wording for the language, mahjong variant and purpose of the text.

## Writing

Read the caller and the relevant target-language rules before changing a term.
Code establishes what is implemented, not whether the rule or translation is
correct. Preserve conditions, actors, objects, recipients, units and timing.
Report a discrepancy instead of silently changing mechanics during a wording edit.

Write complete target-language sentences rather than translating an English
sentence structure. Do not omit head nouns to shorten text in any language, including
in prose, headings, lists, table cells, code comments, buttons and tooltips. An
apparently clear context or limited space does not justify the omission. Retain action objects,
quantity units and logical connections. Give headings concrete subjects; avoid stock
"从……到……" headings. Remove unnecessary content rather than compressing complete
sentences into fragments.

Read a revised explanation with the surrounding paragraphs, not just as an
isolated translation value. Keep the context needed to understand its subject,
conditions and connection to the next sentence. If a detail no longer serves the
section after editing, delete it instead of leaving an unexplained assertion.

Established names and abbreviations permitted under [Names and units](#names-and-units)
are not head-noun omissions. Their permission applies only to the specified locale.
Chinese uses `国标麻将`/`國標麻將`, `台湾麻将`/`台灣麻將` and `四川麻将`/`四川麻將`,
not `国标`/`國標`, `台湾`/`台灣` or `四川` when naming the games. Japanese uses
`台湾麻雀` and `四川麻雀`, not the place names alone. English uses Taiwanese
mahjong, Sichuan mahjong and Riichi mahjong when naming those games.
Apply the same rule to other named objects: identify the face preset, back preset,
replay file or Minecraft server when that is the subject or object of the sentence.
Wrap or resize a control instead of dropping the head noun from its label, unless
the control has a compact label that meets [Compact labels](#compact-labels). Preserve
code identifiers and literal quotations, including existing UI labels, exactly;
their spelling does not establish how to write new explanations or labels.

Rules use `自家`, `他家`, or a precise role such as the winner, discarder or dealer.
Keep the reference player consistent; `上家`, `下家` and `对家/對家` are relative
to that player. Direct operation instructions may address `你`. A tooltip explaining
a rule still uses rule terminology. English uses natural role nouns rather than
literal translations of `自家` and `他家`.

Do not make examples compulsory for scoring entries. Many definitions need only
text. Add a diagram or worked example only when it explains something the text
does not convey adequately. A generic winning shape does not explain timing,
eligibility or the source of the winning tile.

## Locale conventions

| Locale | Formatting and register |
| --- | --- |
| `zh_cn` | No spaces between CJK text and ASCII letters, numbers or format arguments. Use `：`, `（）`, `……` and `“”`, not corner or straight quotes. Follow vanilla Simplified Chinese for Minecraft objects and direct UI instructions. |
| `zh_tw` | Separate CJK text from ASCII letters, numbers and format arguments with spaces; fullwidth punctuation directly precedes an argument without a space. Use `「」`, `『』` and `（）`. Do not insert spaces between translated Chinese words. |
| `ja_jp` | No spaces between kana/kanji and ASCII letters, numbers or format arguments. Use `「」`, `：`, `（）` and `／`. Use Japanese mahjong phrasing, not Chinese expressions converted character by character. |
| `en_us` | Title Case for titles, labels and names; sentence case for descriptions. Use complete imperatives for operations and established loanwords in Riichi mahjong. English repository prose uses ASCII punctuation; quoted localized strings retain native punctuation. |

Do not use middle dots as generic interface separators. Use punctuation or separate
lines appropriate to the language. Preserve argument types and their bindings when
reordering a sentence. English `.one` and `.other` forms follow the displayed count.
Quote variable player names where needed to make Traditional Chinese boundaries clear.

## Compact labels

Toolbars, member rows and collapsed panels can be too narrow for a full label. A
control there may use a compact label stored under the same key with the suffix
`.short`. The layout chooses it; it never replaces the full label in prose, tooltips
or descriptions. A compact label is acceptable only when all of these hold:

1. The full label of the same control stays reachable on the same screen, as a
   tooltip, in the expanded state of the same panel, or as the heading of the
   surrounding group. Check this whenever a `.short` key is added.
2. It drops words that the surrounding screen already supplies, or it is a short form
   already established in that language and variant. Examples are `Leave` for Leave
   Room on a room screen, `余` for remaining tiles, a single Chinese character or
   kanji naming an action in the Chinese and Japanese locales, and the wind letters
   E, S, W and N. Do not coin a new abbreviation, and do not substitute a different
   word for the full label.
3. It keeps the meaning of the full label and differs from every other compact label
   on the same screen.
4. Single Latin letters are limited to the wind letters and to the Riichi mahjong
   automatic-play toggles, whose letters are established for that variant. New
   English labels use words.

Do not use a compact label as the name of a variant, preset or scoring pattern, and
do not use it inside a sentence.

## Leaving, ending and closing

Each of these effects has one name per language, and a control is named for its
effect. Do not give two controls the same label when they do different things; in
particular, Chinese `退出` and Japanese `退出` alone must not label both leaving the
room and ending the match.

| Effect | `en_us` | `zh_cn` | `zh_tw` | `ja_jp` |
| --- | --- | --- | --- | --- |
| Leave the seat and keep the match paused | Stand Up | 起身离座 | 起身離座 | 席を立つ |
| Give up the seat in the lobby | Leave Room | 退出房间 | 退出房間 | 部屋を退出 |
| End a running match, after approval or by a sole player | End Match | 结束对局 | 結束對局 | 対局を終了 |
| Remove every member and close the room | Close Room | 解散房间 | 解散房間 | 部屋を解散 |
| Return from finished results to the lobby | Return to Room | 返回房间 | 返回房間 | 部屋に戻る |

Ending a match keeps completed hands and does not score the unfinished hand. Closing
a room ends any active match first. Say which of these a confirmation will do, in
its title, its explanation and its confirming button.

## Chinese scoring language

Describe the actual players and point changes. Do not mechanically translate
payment, payer or payment amount as `付款`, `付款方` or `付款金额`, or use
`赔付/賠付` as a universal substitute. `支付` and the MCR term `付分` are usable
in an appropriate complete sentence; this is not a blanket prohibition on a verb.

Use the relevant roles and actions: `放铳者/放銃者` in Riichi mahjong, `点炮者` where
appropriate in Sichuan mahjong, and `放枪者/放槍者` in Taiwanese mahjong. Explain
who receives points, which other players contribute, and whether an amount applies
to each player or to the total. Keep win scores, honba, deposits, kong income,
refunds and penalties distinct. In Taiwanese mahjong, describe dealer additions
by naming the relevant players rather than using "收款或付款的一方".

Chinese descriptions of Taiwanese mahjong use `胡牌`, `放槍`, `台`, `底`, `連莊`
and `過水` in their locale-appropriate forms. Chinese descriptions of Riichi
mahjong use `荣和/榮和`, `放铳/放銃`, `番` and `符`. Edit Simplified Chinese and
Traditional Chinese separately rather than relying on character conversion.

## Names and units

### Game names

Use these names when identifying a mahjong variant. The abbreviation rules below
apply separately to each language; an accepted English abbreviation does not
authorize a shortened Chinese label.

| Context | Chinese | Japanese | English |
| --- | --- | --- | --- |
| Riichi mahjong | 立直麻将/立直麻將 | リーチ麻雀 | Riichi mahjong |
| Mahjong Competition Rules | 国标麻将/國標麻將 | 国際公式麻雀 | Mahjong Competition Rules; MCR |
| Sichuan mahjong | 四川麻将/四川麻將 | 四川麻雀 | Sichuan mahjong |
| Taiwanese mahjong | 台湾麻将/台灣麻將 | 台湾麻雀 | Taiwanese mahjong |

SBR is the established English abbreviation of Sichuan Bloody Rules, the ruleset
called `血战到底` in Chinese. It names a ruleset, not the game: use it in preset names
such as MIL SBR 2025 and expand it on first mention in prose. A variant title,
variant list entry or replay label names the game, so it is Sichuan Mahjong and
never `Sichuan SBR` or `四川SBR`.

English may use MCR in prose, headings, tables and interface labels. Expand it to
Mahjong Competition Rules on first mention when introducing the term; do not
mechanically expand every occurrence. Chinese names the game `国标麻将`/`國標麻將`
in all these contexts. Neither `国标`/`國標` nor MCR replaces the Chinese name.

Japanese keeps `国際公式麻雀` as this project's variant label. The established
terms `国際公式ルール` and `中国麻将`, used by the
[Japan Mahjong Sports Association](https://jmsa.jp/about/), are also suitable
in explanations that identify this ruleset. They do not name every form of
Chinese mahjong. Introduce `国際公式麻雀（MCR）` before using MCR in the same
explanation; standalone variant labels retain `国際公式麻雀`.

Established Japanese short forms are different from arbitrary truncations.
`三麻`, `四麻`, `三人打ち` and `四人打ち` can describe player-count modes in an
identified mahjong context, as in [SEGA's rule headings](https://sega-mj.com/players/gameguide/manual/rule/rule_b/).
They do not justify calling `台湾麻雀` merely `台湾`, or `四川麻雀` merely `四川`.
Keep `リーチ麻雀` when naming that game, while `リーチ` remains its action label
and `立直` its scoring name. Similarly, do not truncate `国際公式麻雀` to
`国際公式` or `中国`.

### Scoring units

The scoring category, its numerical value and the resulting score change are
different quantities. Identify which one a field represents before translating it.

| Context | Chinese | Japanese | English |
| --- | --- | --- | --- |
| Scoring in Riichi mahjong | 役/役种, 番, 符, 点 | 役, 飜, 符, 点 | yaku, han, fu, points |
| MCR scoring | 番种, 番值; 分 for points | 役, 点 | scoring patterns, points |
| Scoring in Sichuan mahjong | 番 and 分 | 飜 and 点 | fan and points |
| Scoring in Taiwanese mahjong | 台种/台種, 台, 底 | 役, 台, 底 | tai patterns, tai, base |

In Chinese descriptions of MCR, `8番起和` states the minimum qualification, while
`合计8分` can describe a scoring total. A code identifier containing `fan` does
not determine the displayed unit. Use Japanese `飜` for the applicable scoring
units, while preserving the unrelated character `番` in `手番`, `番号` and `無番和`.

### Tiles, hands and rounds

The English words hand, ready and round have several meanings in this project.
Distinguish the player's tiles and game state from room preparation and match
progression when choosing a translation.

| Context | Chinese | Japanese | English |
| --- | --- | --- | --- |
| Ordinary tile suits | 万/萬, 筒, 索 | 萬, 筒, 索 | Characters, Circles, Bamboo |
| Tile groups | 顺子/順子, 刻子, 对子/對子 | 順子, 刻子, 対子 | sequence, triplet, pair; chow/pung in MCR |
| Closed hand | 门前清/門前清 | 門前 | closed hand |
| Ready hand | 听牌/聽牌 | 聴牌 | tenpai in Riichi mahjong; ready hand in other variants |
| Ready declaration in Taiwanese mahjong | 报听/報聽 | 聴牌宣言 | Declare Ready |
| Room readiness | 已准备/已準備 | 準備完了 | Ready |

Honors include both wind tiles and dragon tiles. Use Dragons only for the three
dragon types, not for the whole honor-tile category.

A hand of play is `局` in Japanese. Chinese descriptions use `一局` in Riichi
mahjong and Taiwanese mahjong, and `一盘/一盤` in MCR. One rotation of dealer
positions is a round: Chinese descriptions use `场/場` in Riichi mahjong and
`圈` in MCR and Taiwanese mahjong. A complete MCR match is formally `一局`.
Competition rules for Sichuan mahjong use `副` for one hand. Resolve the counter
before translating a field named `hand`, `round` or `game`. Honba in Riichi mahjong
and dealer repeats in Taiwanese mahjong are not interchangeable. Neither refers
to a riichi deposit.

## Red tiles and dora

| Meaning | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| Generic red-tile option | 赤宝牌 | 赤寶牌 | 赤ドラ | Red Fives |
| Red-tile scoring bonus | 赤宝牌 | 赤寶牌 | 赤ドラ | Red Dora |
| A particular red five | 赤五万/赤五筒/赤五索 | 赤五萬/赤五筒/赤五索 | 赤五萬/赤五筒/赤五索 | Red Five of Characters, Red Five of Circles, Red Five of Bamboo |
| Dora indicator | 宝牌指示牌 | 寶牌指示牌 | ドラ表示牌 | Dora Indicator |
| Ura dora | 里宝牌 | 裏寶牌 | 裏ドラ | Ura Dora |
| Kan dora | 杠宝牌 | 槓寶牌 | カンドラ/槓ドラ | Kan Dora |
| North extraction | 拔北 | 拔北 | 北抜き | Extract North |
| Extracted-North bonus | 拔北宝牌 | 拔北寶牌 | 抜きドラ | North Dora |

Do not use standalone `赤五` as the generic category in Chinese. Keep the valid
specific tile names and `tile.mchjong.red = 赤%s`. `三赤/四赤` requires an already
identified red-tile option. Japanese `赤五` remains valid. Keep `裏寶牌` as the
project's term without replacing ordinary Traditional Chinese `裡` elsewhere.

Dora is a bonus, not a qualifying yaku. North extraction is not a kan. Permission
to win on an extraction, Chankan eligibility and replacement-win eligibility are
separate preset rules. Kanburi is an award for winning on another player's discard
after a kan; it is not Rinshan Kaihou. Do not derive these conditions from their similar names.

## Japanese wording

Use `リーチ`, `チー`, `ポン`, `カン`, `ロン` and `ツモ` for action labels, while
scoring names retain forms such as `立直` and `門前清自摸和`. Quoted controls must
match their actual labels. Use `自家`, `他家`, `親`, `子`, `和了者` and `放銃者`
for rules; subjectless instructions are often suitable for direct operations.

Use `手牌` for a player's tiles, `牌姿/牌形` for a tile arrangement, `配牌` for the
initial deal and `和了牌` for the winning tile. In discard explanations, `打牌`
names the action, `捨て牌` the discarded tile and `河` the discard area. Distinguish
`手出し`, a discard from the existing hand, from `ツモ切り`, discarding the new draw.

A `面子` need not be exposed. A collection containing both called melds and
concealed kans can be labelled `副露・暗槓`; calling the whole collection `副露`
would obscure that distinction. Similarly, use `大明槓` for a kong made from a
discard, `加槓` for adding to a called triplet, and `暗槓` for a concealed kong.
`食い下がり` describes the reduction in a yaku's value after calling, whereas
`食い替え` concerns which tile may be discarded after a call.

When translating hand-analysis displays, distinguish `向聴数` (distance to
readiness), `待ち牌` (winning tile types), `有効牌/受け入れ` (improving tiles)
and `残り枚数` (remaining copies). A structurally ready hand can still lack a
qualifying yaku or be blocked from ron, so do not use `役なし`, `ノーテン` and
`振聴` interchangeably. Match counters to the displayed quantity: `枚` for
physical tiles, `種類` for tile types, `組` for meld groups, `本` for point sticks,
`人` for players and `位` for ranks.

Japanese settlement text can naturally use `支払う`, `受け取る` and `返す`;
the Chinese guidance above is not a prohibition on these Japanese verbs.
Specify the players involved, using `各/それぞれ` for each player's amount and
`合計` for the total. A result heading such as `点数の増減` describes changes
in players' scores, not the value of the winning hand alone.

## Variant-specific names

Keep each variant's registered names when discussing combinations or exclusions.
Identical-looking names do not establish identical rules. In particular:

| Distinction | Project convention |
| --- | --- |
| English call names | Riichi mahjong keeps the Japanese loanwords Chi, Pon and Kan. MCR, Sichuan mahjong and Taiwanese mahjong use Chow, Pung and Kong, the terms of the official English MCR rules and of English guides to Taiwanese mahjong. Do not write Chi or Pong for the Chinese variants. |
| Chinese sequence-pair names | 一杯口/二杯口; Japanese uses 一盃口/二盃口 |
| Nine Gates | Riichi mahjong uses 九莲宝灯/九蓮寶燈 in Chinese and 九蓮宝燈 in Japanese. MCR uses 九連宝灯 in Japanese. |
| Seven Pairs | Riichi mahjong uses 七对子/七對子 in Chinese and 七対子 in Japanese. MCR uses 七对/七對 in Chinese and 七対 in Japanese. Sichuan mahjong uses 七対子 in Japanese. |
| All Pungs | Chinese names are 对对和/對對和 in Riichi mahjong, 碰碰和 in MCR and 碰碰胡 in Taiwanese mahjong. Japanese uses 碰碰和 in MCR and 対々和 in Sichuan mahjong and Taiwanese mahjong. |
| Last-tile names | Riichi mahjong uses 海底摸月 and 河底捞鱼/河底撈魚. MCR uses 妙手回春, 海底捞月/海底撈月 and 和绝张/和絕張 for distinct winning circumstances. |
| Pinfu and single waits | 平和 in Riichi mahjong, 平和 in MCR and 平胡 in Taiwanese mahjong follow their own conditions. 独听/獨聽 in Taiwanese mahjong means one winning tile kind, not necessarily a pair wait or one remaining copy. |
| MCR Japanese names | Preserve 一般高, 圏風刻, 門風刻, 坎張, 単釣将, 大于五, 小于五, 五門斉 and 四帰一 |
| MCR Japanese dragon names | 清竜, 花竜, 組合竜, 三色双竜会 and 一色双竜会 use 竜 |
| MCR Japanese terminal names | Preserve 断么, 全帯么, 混么九, 清么九 and 十三么 rather than substituting names from Riichi mahjong. |
| Robbing a kong | MCR uses 搶槓和 in Traditional Chinese and 槍槓和 in Japanese. Japanese uses 搶槓和 in Sichuan mahjong and 搶槓 in Taiwanese mahjong. |
| Japanese labels in Sichuan mahjong | Preserve 血戦到底, 定缺, 根/槓/帯根, 槓上花, 槓上砲, 查大叫, 呼叫転移 and 花猪 within the applicable preset. |
| Flower terminology in Taiwanese mahjong | Chinese uses 门花/門花 for seat flowers. Japanese uses 正花 for seat flowers and 花牌セット for flower sets. Flower wins are distinct from replacement-tile wins. |
| Passed-win restrictions | 過水 has different triggers and clearing events in Sichuan mahjong and Taiwanese mahjong. Neither restriction is equivalent to 振聴 in Riichi mahjong. |

Keep `坎張` and `斷幺` in Traditional Chinese descriptions of the corresponding
MCR patterns. Do not normalize spellings across variants. In Japanese descriptions
of Taiwanese mahjong, `独聴`, `聴牌宣言`, `門前自摸` and `花牌和了` retain their
separate meanings. The Japanese name `海底撈月` also refers to different winning
circumstances in MCR, Sichuan mahjong and Taiwanese mahjong.
