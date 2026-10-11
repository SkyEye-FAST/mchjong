# MChjong terminology conventions

This guide defines the player-facing terminology used by MChjong for development, translation, resource authoring, and review. Choose each name for its **locale, variant, concept, and display context**.

It covers `zh_cn`, `zh_tw`, `ja_jp`, and `en_us` for Riichi mahjong, including its three-player form, the Mahjong Competition Rules, Sichuan mahjong, Taiwanese mahjong, and the bundled ancient yaku set. [RULES.md](docs/RULES.md) owns rule mechanics. [AGENTS.md](AGENTS.md) and [UI_STYLE.md](docs/UI_STYLE.md) own presentation conventions.

## 1. Maintenance principles

1. **Translate the intended concept.** Check what the caller displays. `fan` can refer to a scoring pattern, its value, or a doubling unit in Sichuan mahjong. A field named `round` may count individual hands. Resolve each meaning before choosing localized wording.
2. **Distinguish fixed names, explanations, and calls.** Japanese can use `リーチ` on a button and `立直` as the yaku name. Riichi, Tanyao, and Ron are established English labels. Chinese `赤宝牌` and the specific tile name `赤五筒` also serve different purposes.
3. **Keep names within their rule scope.** Pinfu, Seven Pairs, last-tile awards, single waits, passed-win restrictions, and replacement-tile awards can have different conditions across variants. Apply the definition for the selected preset.
4. **Use the project's preferred terms consistently.** Established alternatives remain useful for explanation and search. Change a consistent, accurate project term when the evidence establishes a concrete benefit.
5. **Localize complete sentences.** Preserve conditions, actors, recipients, units, timing, exceptions, and placeholders. Use natural mahjong phrasing in the target language, with clear verbs and player roles.
6. **Review every affected display context.** Language files, manuals, rule titles, settlement labels, inline resource-pack text, and documentation must agree. Keep resource IDs, protocol fields, and enum constants stable when only display wording changes. Changes to an underlying concept belong in the relevant technical guide and implementation review.
7. **Write repository documentation in English.** Use ASCII punctuation in English prose and table structure, including separators between terms. Preserve native punctuation inside quoted localized strings and canonical names. Consult target-language rules directly when changing localized wording.
8. **Use player relationships in rule explanations.** Prefer `自家` and `他家` in Chinese and Japanese, or a precise role such as the winner, discarder, or dealer. Reserve direct address for interface messages and operation instructions. Keep the reference player explicit and consistent.

For wording, follow maintainer requirements and documented project choices, supported by official rules and game usage in the relevant language. Association tables, encyclopedias, and community material provide cross-checks. **Code establishes current behavior. Verify a preset's correctness against its governing rules and edition. When they disagree, record the discrepancy and establish the intended rule before correcting the implementation or its description.**

## 2. Red tiles: generic terms and specific tile names

The following is an explicit project convention for both Chinese locales.

| Concept or context | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| Red fives of characters, circles, and bamboo as a category; rule-option title | **赤宝牌** | **赤寶牌** | 赤ドラ; physical tiles may also be called 赤牌 or 赤五 | **Red Fives** |
| Red-tile bonus in scoring results | 赤宝牌 | 赤寶牌 | 赤ドラ | **Red Dora** |
| A specific red five of characters | **赤五万** | **赤五萬** | 赤五萬 | Red Five of Characters |
| A specific red five of circles | **赤五筒** | **赤五筒** | 赤五筒 | Red Five of Circles |
| A specific red five of bamboo | **赤五索** | **赤五索** | 赤五索 | Red Five of Bamboo |
| Compact quantities under an identified red-tile option | 三赤/四赤 | 三赤/四赤 | Existing options such as 赤3枚/赤4枚 | Existing options such as Three Reds/Four Reds |

Do not use standalone `赤五` as the generic category name in Chinese. For example, write `每张赤宝牌加1番` instead of `每张赤五加1番`. **Keep the accurate specific tile names `赤五万/赤五筒/赤五索`; `tile.mchjong.red = 赤%s` is also correct when it generates a specific tile name.** Use `三赤/四赤` only within options whose red-tile context is already clear, not in unrelated explanatory paragraphs.

English Red Fives/Red Dora and Japanese `赤五/赤ドラ` remain valid in their respective contexts. Distinguish a physical five, its red bonus, and its status as dora indicated by another tile. A red five of circles can also be dora because of the indicator.

### 2.1 Related dora terminology

| Concept | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| The tile that supplies the dora bonus | 宝牌 | 寶牌 | ドラ | Dora |
| The displayed tile that determines which tile is dora | **宝牌指示牌** | **寶牌指示牌** | **ドラ表示牌** | **Dora Indicator** |
| Hidden dora counted for a winning hand after riichi | 里宝牌 | 裏寶牌 | 裏ドラ | Ura Dora |
| Additional dora revealed by a kan | 杠宝牌 | 槓寶牌 | カンドラ; 槓ドラ is also suitable in explanations | Kan Dora |
| The action of extracting North | 拔北 | 拔北 | 北抜き | Extract North; Auto Kita is acceptable for an explained automation option |
| The bonus awarded for an extracted North | 拔北宝牌 | 拔北寶牌 | 抜きドラ | North Dora |
| Declaring ron on another player's North extraction | 对拔北荣和 | 對拔北榮和 | 北抜きへのロン | Ron on an Extracted North |

Dora are not yaku and do not replace a required qualifying yaku or minimum yaku han. The `yaku` namespace in keys such as `yaku.mchjong.dora` does not change that distinction. Extracting North and declaring a kan are distinct actions. Describe Chankan, Rinshan Kaihou and Kanburi according to the preset-specific conditions in [RULES.md](docs/RULES.md#three-player-play). Descriptions must state whether non-Kokushi wins on an extraction are allowed and retain the normal winning conditions.

For Traditional Chinese, the project uses the scoring label `裏寶牌`. `裡寶牌` is an established external alternative. Apply this spelling convention to the dora term while retaining ordinary uses of `裡`, such as `抽屜裡`.

## 3. Terminology boundaries between variants

### 3.1 Variant names, scoring categories, and units

| Variant or concept | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| Riichi mahjong | 立直麻将 | 立直麻將 | リーチ麻雀 | Riichi |
| Mahjong Competition Rules | 国标麻将 | 國標麻將 | 国際公式麻雀 | MCR; expand to Mahjong Competition Rules on first use when needed |
| Sichuan mahjong | 四川麻将 | 四川麻將 | 四川麻雀 | Sichuan |
| Taiwanese mahjong | 台湾麻将 | 台灣麻將 | 台湾麻雀 | Taiwanese |
| Riichi mahjong scoring category | 役/役种 | 役/役種 | 役 | Yaku |
| Riichi mahjong units | 番, 符, 点 | 番, 符, 點 | **飜, 符, 点** | **han, fu, points** |
| Mahjong Competition Rules scoring category | 番种 | 番種 | 役 | Scoring Pattern; Fan is acceptable in rule explanations |
| Mahjong Competition Rules pattern value or total | 番值; 番 in explanations, 分 for point values | 番值; 番 in explanations, 分 for point values | **点** | **points** |
| Sichuan mahjong doubling unit and settlement score | 番/分 | 番/分 | **飜/点** | **fan/points** |
| Taiwanese mahjong scoring category and unit | 台种/台 | 台種/台 | 役/台 | Tai Pattern/**tai** |
| Taiwanese mahjong base and points per tai | 底/每台点数 | 底/每台點數 | 底/1台あたりの点数 | Base/Points per Tai |

In the Mahjong Competition Rules, `番种` identifies a scoring pattern and `番值` its value. Each player's actual score change is a separate quantity. Chinese manual wording such as `8番起和` and a scoring total such as `合计8分` are appropriate in their respective contexts. Resolve the value represented by `total_fan` before choosing its display unit. Use Japanese `点` and English points for numerical values. Fan remains available as an English scoring-category name.

Riichi mahjong uses han, Sichuan mahjong uses fan, and Taiwanese mahjong uses tai. Their scoring relationships follow the relevant rules, including doubling in Sichuan mahjong and base-plus-tai calculations in Taiwanese mahjong. Use Japanese `飜` consistently for the applicable scoring units. Retain unrelated words such as `手番`, `番号`, and `無番和`.

### 3.2 Actions, states, and hand components

| Concept and scope | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| Riichi mahjong call buttons | 吃/碰/杠 | 吃/碰/槓 | チー/ポン/カン | Chi/Pon/Kan |
| Mahjong Competition Rules calls | 吃/碰/杠 | 吃/碰/槓 | チー/ポン/槓 | Chow/Pung/Kong |
| Sichuan mahjong calls | 碰/杠 | 碰/槓 | ポン/槓 | Pung/Kong; this variant has no chow action |
| Taiwanese mahjong call buttons | 吃/碰/杠 | 吃/碰/槓 | チー/ポン/槓 | Retain Chi/Pong/Kong; chow and pung are acceptable alternatives in explanations |
| A concealed kan or kong | 暗杠 | 暗槓 | 暗槓 | Concealed Kan for Riichi mahjong. Concealed Kong for other variants |
| A four-tile meld made from another player's discard | 大明杠; 明杠 when the context is clear | 大明槓; 明槓 when the context is clear | 大明槓 | Open Kan; retain Melded/Discard/Open Kong in the relevant existing variant context |
| Adding a fourth tile to an existing called triplet | 加杠 | 加槓 | 加槓 | Added Kan/Added Kong |
| Riichi mahjong discard win and self-draw win | 荣和/自摸 | 榮和/自摸 | ロン/ツモ | Ron/Tsumo |
| Mahjong Competition Rules win and discard win | 和牌/点和 | 和牌/點和 | 和了; ロン is acceptable in explanations | Win/Discard Win |
| Sichuan mahjong win | 和牌; 胡牌 is acceptable in explanations | 和牌; 胡牌 is acceptable in explanations | 和了 | Win |
| Taiwanese mahjong win and self-draw win | 胡牌/自摸 | 胡牌/自摸 | 和了/自摸; ロン/ツモ are acceptable in explanations | Win/Self-Draw; use Discard Win when contrasting a win on another player's discard |
| Drawing a tile | 摸牌 | 摸牌 | 牌をツモる/摸牌, the existing Taiwanese mahjong button | Draw Tile; Draw is sufficient in an unambiguous action area |
| A hand ending in a draw | 流局 | 流局 | 流局 | Draw; distinguish Exhaustive Draw from Abortive Draw |
| Sequence, triplet, and pair | 顺子/刻子/对子 | 順子/刻子/對子 | 順子/刻子/対子 | Sequence/Triplet/Pair; Chow/Pung/Pair in the Mahjong Competition Rules |
| The pair in an ordinary winning shape | 雀头 in Riichi mahjong. 将牌 in the Mahjong Competition Rules and other relevant contexts | 雀頭/將牌 | 雀頭; 将 is acceptable in the Mahjong Competition Rules terminology | Pair |
| A closed hand | 门前清/门清 | 門前清/門清 | 門前 | Closed Hand; distinguish this state from the named Concealed Hand scoring pattern |
| A ready hand | 听牌 | 聽牌 | 聴牌; テンパイ is acceptable in the UI | Tenpai in Riichi mahjong. Ready Hand in other variants |
| Distance from a ready hand | 向听 | 向聽 | 向聴 | Shanten |
| A player's ready status in a room | 已准备 | 已準備 | 準備完了 | Ready |
| Taiwanese mahjong ready declaration | 报听; 听牌 is acceptable on a clearly identified action button | 報聽; 聽牌 is acceptable on a clearly identified action button | 聴牌宣言 | Declare Ready |

Use the established katakana action labels and kanji scoring names in the tables above. Retain established English labels such as Riichi, Tanyao, Toitoi, Honitsu, Ron, Tsumo, Kan, and Honba in Riichi mahjong.

A hand remains closed after a concealed kan even though some tile faces are visible. Resolve whether `concealed` describes hidden tiles, closed-hand status, or a named scoring pattern. The `听牌/聽牌` pattern in Taiwanese mahjong requires its corresponding declaration. Keep that declaration distinct from general readiness and from a riichi declaration in Riichi mahjong.

### 3.3 Hands, rounds, and matches

| Level or duration | Chinese usage | Japanese usage | English usage | Boundary to preserve |
| --- | --- | --- | --- | --- |
| From the deal to one win or draw in Riichi mahjong, the Mahjong Competition Rules, or Taiwanese mahjong | 一局 in Riichi mahjong and Taiwanese mahjong; 一盘/一盤 in the Mahjong Competition Rules | 局 | **Hand** | Do not translate an individual-hand counter as Round merely because its field is named `round` |
| One rotation of dealer positions under the same prevailing wind | Use 东场/東場 in Riichi mahjong and 东圈/東圈 in the Mahjong Competition Rules and Taiwanese mahjong | 東場 and the corresponding other winds | **Round** | Distinguish this from one hand. Three-player Riichi mahjong has three dealer positions per wind |
| The complete room match | 对局/對局; the formal Mahjong Competition Rules use 一局 for four rounds | 対局; 半荘 when that match length is intended | **Match** | Game can be natural in explanations; it does not make Round appropriate everywhere |
| East round only | 东风战/東風戰 | 東風戦 | East-Only | This is more than one hand |
| East and South rounds | 半庄战/半莊戰 | 半荘戦 | East-South; Hanchan is acceptable in explanations | State extension and dealer-repeat conditions separately |
| Riichi mahjong continuation counter | 本场/本場 | 本場 | Honba | Distinguish it from riichi sticks; it does not count only consecutive dealer wins |
| Repeating the same dealer position in Taiwanese mahjong | 连庄/連莊 | 連荘 | Dealer Repeat/Continuation | Do not import Riichi mahjong honba amounts or progression rules |

The 16 hands in the Mahjong Competition Rules and the 16 completed dealer positions in Taiwanese mahjong are different counts. Taiwanese mahjong also allows dealer repeats. A hand of Sichuan mahjong may continue after the first player wins and ends according to its selected preset. The Chinese Sichuan Mahjong Competition Rules (SBR) and T/TFMJ 01-2024 Sichuan Mahjong Competition Rules use `副` for one deal through its conclusion and `盘/局` for larger match segments. Keep those levels distinct when comparing variants. English hand can also mean a player's tiles, depending on the sentence. The Mahjong Competition Rules separately define `盘`, `圈`, and `局`. The project's combined Taiwanese mahjong presets are documented in [TAIWAN_DESIGN.md](docs/TAIWAN_DESIGN.md).

### 3.4 Common collisions and near-synonyms

Where a row lists four localized names, their order is `zh_cn`, `zh_tw`, `ja_jp`, and `en_us`.

| Scope | Fixed names or distinction | Meaning to preserve |
| --- | --- | --- |
| Riichi mahjong win on the final draw | 海底摸月/海底摸月/海底摸月/Haitei Raoyue | Keep 摸 in the Chinese name for this Riichi mahjong pattern |
| Riichi mahjong win on the final discard | 河底捞鱼/河底撈魚/河底撈魚/Houtei Raoyui | Distinguish it from winning on the final draw |
| Mahjong Competition Rules win on the final draw | 妙手回春/妙手回春/妙手回春/Last Tile Draw | Use 妙手回春 for this pattern, distinct from 海底摸月 in Riichi mahjong |
| Mahjong Competition Rules win on the final discard | 海底捞月/海底撈月/海底撈月/Last Tile Claim | Here 海底捞月 does not mean a win on the final draw |
| Mahjong Competition Rules win on the fourth copy | 和绝张/和絕張/和絶張/Last Tile | Distinguish this from Last Tile Draw and Last Tile Claim |
| Riichi mahjong 七对子 and Mahjong Competition Rules 七对 | Chiitoitsu/Seven Pairs | Riichi mahjong requires seven distinct pairs. The Mahjong Competition Rules allow four identical tiles to count as two pairs, with other scoring governed separately |
| Riichi mahjong 国士无双 and Mahjong Competition Rules 十三幺 | Kokushi Musou/Thirteen Orphans | A similar shape does not make their pattern names or scoring categories interchangeable |
| Riichi mahjong 平和 | Pinfu | Closed status, a pair worth no fu, and a two-sided wait belong to the Riichi mahjong definition |
| Mahjong Competition Rules 平和 | All Chows | Riichi mahjong's closed-hand and two-sided-wait requirements do not apply; follow the pair conditions in the Mahjong Competition Rules |
| Taiwanese mahjong 平胡 | Pinfu (Taiwanese); retain Pinfu in an interface dedicated to Taiwanese mahjong | Keep the project name and apply the selected Taiwanese mahjong preset's definition, not the Pinfu conditions in Riichi mahjong |
| Riichi mahjong 对对和, Mahjong Competition Rules 碰碰和, and Taiwanese mahjong 碰碰胡 | Toitoi/All Pungs/All Triplets | Preserve all three fixed display names |
| Mahjong Competition Rules 边张, 坎张, and 单钓将 | Edge Wait/Closed Wait/Single Wait | Closed Wait is a middle-tile wait within a sequence, not a closed hand; Single Wait completes the pair |
| Taiwanese mahjong 独听 | 獨聽/独聴（待ちが1種類）/Single Wait (Taiwanese) | Only one tile type completes the hand; this can be an edge, closed, or pair wait and does not mean only one physical copy remains |
| Mahjong Competition Rules 无番和 | Chicken Hand | This is a specific Mahjong Competition Rules scoring pattern, not the qualifying-yaku requirement in Riichi mahjong |
| Mahjong Competition Rules 推不倒 | Reversible Tiles | This names a pattern made from a defined set of tile types, not a tile-flipping action or a prohibition on toppling tiles |
| Riichi mahjong 振听 | 振聽/振聴/Furiten | It restricts ron while a tsumo win may remain possible; explain discard, temporary, and post-riichi furiten separately |
| Taiwanese mahjong and Sichuan mahjong 过水 | 過水; Passed-Win Restriction is an acceptable English description | Triggers, restricted wins, and clearing events depend on the variant; Taiwanese mahjong restrictions can also affect self-draws, so do not reuse the furiten definition from Riichi mahjong |
| Ron on an extracted North and the Chankan yaku | 北抜きへのロン/搶槓 | Permission to win on an extraction does not award Chankan; normal winning conditions still apply |
| Rinshan Kaihou and ancient Kanburi | Rinshan Kaihou/Kanburi | The former is a self-draw from one's own kan replacement; the latter is ron on another player's discard after a kan. Do not use the ambiguous After a Kan for the ancient yaku |

`docs/RULES.md` documents exact values, excluded combinations, flower-win rules, and passed-win exceptions.

## 4. Natural phrasing in each locale

### 4.1 Chinese settlement descriptions

Write Chinese scoring explanations from the relevant variant's rules and natural usage. Identify the players and their point changes instead of mechanically translating payment, payer, and payment amount as `付款`, `付款方`, and `付款金额`. Do not use `赔付/賠付` as a universal replacement. `支付` is appropriate when the sentence makes the responsibility and object clear, as in `包牌时本场棒由放铳者支付`. Choose the verb for the whole sentence.

Use the following sentence structures while preserving the applicable scoring conditions, values, and exceptions.

| Scoring context | Chinese wording | Structure to use when rewriting |
| --- | --- | --- |
| Riichi mahjong ron | `榮和時收取點數` | Describe the points received from the winner's perspective, distinguishing dealer and nondealer shares for self-draws |
| Taiwanese mahjong discard wins and self-draws | `只有算放槍者一人輸` / `其他三家皆算輸` | State directly which players lose or gain points |
| Taiwanese mahjong dealer tai | `胡牌者或放槍者為莊家` | Identify the player whose dealer status adds tai. Describe the dealer's particular share separately for a self-draw |
| Mahjong Competition Rules cumulative scores | `每盘得失的比赛分之和为局分` | Explain scoring levels through gains, losses, and cumulative scores |
| Sichuan mahjong kong points | `开杠者收取分数` | Use `开杠者从尚未和牌的各家收取……` and preserve exactly which players are involved |
| Sichuan mahjong kong payment transfer | `分值平分给和牌方` | Describe the transfer through 转给 or 平分给 |

Depending on the context, use phrases such as `从……收取……点`, `……获得……分`, `由……分摊`, `……给……分`, and `退还开杠所得的分数`, or list each player's score changes directly.

For settlement buttons and headings, consider `结算`, `得失`, and `计分` first. Explanations should name the relevant variant's discarder, winner, dealer, or players who have not yet won. Examples include `放铳者/点炮者/放枪者` and `和牌者/胡牌者`. Keep ordinary win scores, honba additions, penalties, kong refunds, and ready-hand checks distinct. The Mahjong Competition Rules use `付分`. Choose a player-role noun according to the sentence rather than mechanically deriving `付分者` from payer.

A sentence such as `和牌者或付款方为庄家时` needs an explicit player relationship. A ron in Riichi mahjong, a discard win in Taiwanese mahjong, and a self-draw involving the dealer's share have different scoring scopes.

Write Simplified and Traditional Chinese separately and retain each variant's terms. Taiwanese mahjong commonly uses `胡牌, 放槍, 台, 底, 連莊, 過水`, while Traditional Chinese Riichi mahjong uses `榮和, 放銃, 番, 符`. For alternatives such as `门花/正花` and `三暗刻/三暗坎`, follow the selected preset and established project convention.

### 4.2 Information every localized sentence must preserve

Before rewriting, identify **the actor, action, affected player or tile group, conditions, effective or clearing time, and scoring consequence**. Natural phrasing can reorder clauses, split sentences, or omit an already clear subject, but it must retain every condition that determines the rule.

- In Chinese, favor actions and player roles over stacks of terms such as `执行`, `进行`, `应用`, `有效性`, and `负责方`. Resolve claim as the applicable chi, pon, or winning action rather than always writing `索取`. For visible tiles, choose `明牌`, `亮出的牌`, or `副露` as appropriate instead of mechanically translating exposed as `暴露`.
- In Japanese, use natural player terminology and split long English-style sentences where useful. Identify actors through `自家`, `他家`, `放銃者`, and `親/子`. `支払い` and `支払う` are normal scoring expressions; **Chinese wording choices do not impose a restriction on these Japanese verbs**.
- English may use payment, pay, payer, and established Riichi loanwords. Do not invent English equivalents to imitate Chinese `付分` word for word. Resolve draw as a tile-drawing action or a drawn hand, and hand as a player's tiles or one unit of play.
- Follow each locale's numeric and punctuation conventions in `AGENTS.md`. Preserve format-specifier order and types when changing sentence structure.

### 4.3 Written forms and alternatives to retain

| Scope | Project choice | Guidance |
| --- | --- | --- |
| Chinese 一杯口/二杯口 | Retain 杯 in both Chinese locales | External yaku lists may use 盃, but 杯 is not exclusively a Simplified Chinese form and does not require correction here |
| Corresponding Japanese yaku | 一盃口/二盃口 | Do not replace 盃 with 杯 to match Chinese |
| Riichi mahjong Nine Gates | Chinese 九莲宝灯/九蓮寶燈; Japanese 九蓮宝燈 | Preserve the established form in each locale |
| Japanese terminology for the Mahjong Competition Rules Nine Gates | 九連宝灯 | Keep it distinct from Riichi mahjong 九蓮宝燈, following the selected Japanese list |
| Japanese terminology for the Mahjong Competition Rules dragon patterns | 組合竜/花竜/清竜/三色双竜会/一色双竜会 | Keep headings and prose consistent within the Mahjong Competition Rules; do not substitute Chinese 龍 |
| Riichi mahjong 搶槓 and Mahjong Competition Rules 槍槓和 | Retain both within their respective domains | Do not replace 槍 with 搶 globally |
| Traditional Chinese Mahjong Competition Rules closed wait | 坎張 | 嵌張 may remain in Riichi mahjong and other appropriate contexts |
| Traditional Chinese Mahjong Competition Rules All Simples | 斷幺 | Align prose with the registered name. External 么 is an alternative written form, not a character forbidden everywhere |
| Taiwanese mahjong seat flower | Chinese 门花/門花; Japanese 正花; English Seat Flower | Align Chinese with Pocket Mahjong. Retain Japanese 正花. |

### 4.4 Player perspective in rules and interface text

Rules describe relationships between seats. Establish the reference player, then prefer `自家` and `他家` in Chinese and Japanese rule explanations. Use the precise seat or role when it determines eligibility, priority, or the points transferred. Keep that reference player consistent throughout the paragraph.

| Relationship or context | Chinese wording | Japanese wording | English wording |
| --- | --- | --- | --- |
| The player whose hand or action is being explained | `自家` | `自家` | The player, the affected player, or the named role |
| The other seats relative to that player | `他家` | `他家` | The other players or opponents |
| A particular relative seat | `上家`, `下家`, `对家`/`對家` | `上家`, `下家`, `対面` | The previous player, next player, or opposite player, with the reference player and order clear |
| A role that determines the rule | `庄家`/`莊家`, `和牌者`/`胡牌者`, and the variant's discarder term | `親`, `子`, `和了者`, `放銃者`, `鳴いたプレイヤー` | The dealer, nondealer, winner, discarder, or caller |
| A direct interface message or operation instruction | `你` when an address is useful; omit an obvious subject in short instructions | An imperative or `あなた` when direct address is useful | You, your, or a direct imperative |

For example, a rule explanation can say `他家的鸣牌不会解除自家的同巡振听。` or `他家の鳴きでは、自家の同巡振聴は解除されません。` A direct interface prompt can say `轮到你出牌了。` or `あなたの番です。` These are examples of register; quoted controls must still match their actual localized labels.

Choose the register by what the sentence communicates. A tooltip explaining a table-wide rule still uses rule terminology. A manual paragraph asking the reader to select a tile is a direct operation instruction. The Simplified Chinese second-person convention in `AGENTS.md` specifies `你` when addressing the user; it does not require second person in every rule explanation.

Avoid mechanically replacing every subject with `自家`. In settlement formulas, name the winner, discarder, dealer, or liable player so the recipient and each player's share remain explicit. In English, express the same relationships with natural role nouns rather than inventing literal equivalents for `自家` and `他家`.

### 4.5 Japanese terminology and phrasing

Use this guidance when maintaining `ja_jp.json`. The rules vocabulary below describes Riichi mahjong and its three-player presets. Shared equipment and interface terms also apply to the other variants. Their scoring names remain in their own catalogues. `docs/RULES.md` owns rule behavior. `AGENTS.md` and `docs/UI_STYLE.md` own formatting and interface conventions.

#### 4.5.1 Choose the register for the resource

Japanese mahjong uses kanji, kana, and mixed spellings together. Select the wording for its function and preserve established names within that function.

| Resource or context | Maintain this distinction |
| --- | --- |
| Action buttons and calls | Keep `リーチ`, `チー`, `ポン`, `カン`, `ロン`, and `ツモ`. The kan button can cover several declarations. |
| Scoring names | Keep catalogue names such as `立直`, `門前清自摸和`, and `断么九`. A button labelled `ツモ` does not establish the yaku `門前清自摸和`. |
| Explanatory prose | `立直を宣言する`, `リーチをかける`, `ツモ和了する`, and `ロンする` are natural in their respective contexts. Match a quoted control to its actual label. |
| Recognizable variants | `カンドラ` and `槓ドラ`, `喰いタン` and `食いタン`, and `フリテン` and `振聴` are established Japanese forms. Make any consistency change within the relevant resource family. |
| Full and compact labels | Keep full forms in explanations and preserve intentional short controls such as `切`, `理`, and `抜`. |

`赤五` is valid Japanese for red fives. Use `赤ドラ` when describing their scoring role and `赤五萬`, `赤五筒`, or `赤五索` for an identified tile. Preserve the distinction from the Chinese generic term `赤宝牌`. Chinese concrete tile names such as `赤五万` are valid within their own locale.

#### 4.5.2 Name tiles, groups, and table areas precisely

| Concept | Japanese wording | Maintenance rule |
| --- | --- | --- |
| Numbered suits | `数牌`, `萬子`, `筒子`, `索子` | Use `色` for a suit in combinations such as `同じ色の順子`. |
| Honors | `字牌`, `風牌`, `三元牌` | `風牌` and `三元牌` are subdivisions of `字牌`. |
| Terminals and honors | `一九牌`, `老頭牌`, `么九牌` | The first two refer to numbered terminals. `么九牌` also includes honors. |
| Tiles held by a player | `手牌` | Use this for the actual hand or its display. Ordinary expressions such as `手を進める` remain natural. |
| An arrangement or completed shape | `牌姿`, `牌形`, `和了形` | Use `和了形` for a completed structural shape and `牌姿` or `牌形` for an arrangement being examined. |
| Initial tiles | `配牌` | Distinguish the initial deal from subsequent draws. |
| A new draw | `ツモ`, `ツモ牌` | The action is `ツモる`; the newly drawn tile is `ツモ牌`. |
| Winning tile | `和了牌` | Identify the tile that completes the win, whether drawn or claimed. |
| Wall | `牌山`, `山` | Use `牌山` for a labelled object and `山` in clear prose. `壁牌` is a formal rulebook variant. |
| Reserved wall and replacement tile | `王牌`, `嶺上牌` | Name the relevant portion or tile. Use `補充牌` when describing replacement in general. |
| Discard action, tile, and area | `打牌`, `捨て牌`, `河` | A player performs `打牌`; the discarded tile is a `捨て牌`; `河` names the discard area. |
| Newly drawn tile discarded unchanged | `ツモ切り` | Preserve the origin of the discarded tile. Sorting the hand is a different action. |
| A tile discarded from the existing hand | `手出し` | Keep this distinct from `ツモ切り` in replay and display explanations. |
| Sorting, shuffling, and building | `理牌`, `洗牌`, `牌山を積む` | Use the corresponding verbs for player operations. `築牌` is the formal name for building the wall. |
| Structural groups | `面子`, `順子`, `刻子`, `槓子` | A `面子` need not be exposed. Name its structure independently of how it was obtained. |
| A pair and its role | `対子`, `雀頭` | `対子` names a pair. `雀頭` names the pair's role in an ordinary completed hand. |

Translate the English word "hand" according to its referent. Use `手牌` for tiles, `局` for a phase of play, `和了形` for a shape, and `1回の和了` for a scoring event. Retain ordinary Japanese uses of `手` when they express a player's developing hand or its value.

#### 4.5.3 Describe calls and closed hands independently

| Concept | Japanese wording | Maintenance rule |
| --- | --- | --- |
| Calling another player's discard | `鳴く`, `鳴き` | Use these in operations and short explanations. Name the caller when timing or eligibility depends on that player. |
| Exposed melds | `副露`, `副露牌`, `副露面子` | Use these for exposed melds. Inspect the contents of a code collection named `melds` before labelling all of it `副露`. |
| Declared groups including concealed kans | `副露・暗槓` | Use this explicit combined label when the count or display includes concealed kans. |
| A revealed hand | `手牌公開` | Revealing a winning or ready hand is independent of whether it contains exposed melds. |
| Closed hand | `門前`, `門前清`, `メンゼン` | `門前` is the established prose form here. `門前限定` describes a scoring condition. |
| Concealed kan | `暗槓` | Displaying a concealed kan does not break `門前`. |
| Claiming a discard for a kan | `大明槓` | Use this precise name when the procedure or rule differs from added kan. |
| Adding a tile to an exposed triplet | `加槓` | Name the pre-existing pon and the added tile when needed. |
| Both exposed kan types | `明槓` | This can encompass `大明槓` and `加槓`. Check whether a rule actually applies to both. |
| Reduction after calling | `食い下がり` | Describe the affected yaku's reduced han value. |
| Discard restriction after calling | `食い替え` | This concerns which tile may be discarded after a call. |

In three-player Riichi mahjong, `北抜き` names the extraction action and `抜きドラ` names its scoring benefit. The extracted tile remains a `北`. An extraction can affect interruption rules while preserving `門前`; state each effect separately. Describe a win on another player's extracted North as `北抜きへのロン`. Any added yaku or replacement-draw treatment follows the selected preset.

#### 4.5.4 Keep shape, readiness, and permission separate

| Concept | Japanese wording | Maintenance rule |
| --- | --- | --- |
| A hand one tile from completion | `聴牌`, `テンパイ` | The repository uses `聴牌` in its state and rules text. |
| A hand that is not ready | `ノーテン`, `不聴` | Both are Japanese. Prefer the established `ノーテン` in explanations and when choosing an accessible new label. |
| Distance to readiness | `向聴数`, `シャンテン数` | Preserve the analyzer's meaning. This is different from a count of waiting tiles. |
| Tiles awaited | `待ち`, `待ち牌` | Distinguish structural waits from the ability to declare a legal win now. |
| Tiles that improve the hand | `有効牌`, `受け入れ` | Use the term matching the analyzer's output and describe its counting basis. |
| Wait shapes | `両面`, `辺張`, `嵌張`, `双碰`, `単騎` | These describe how the hand completes. `シャンポン待ち` is an established reading of `双碰待ち`. |
| A hand with no qualifying yaku | `役なし` | Keep this separate from `ノーテン` and `振聴`. |
| A restriction on ron | `振聴`, `フリテン` | Preserve the distinction between ron permission and tsumo permission. |
| Sources of furiten | `捨て牌振聴`, `同巡振聴`, `リーチ後の振聴` | Name the applicable restriction and the player whose action can end it. |
| Declining an opportunity to win | `見逃す`, `ロンを見送る` | `パス` is an interface action whose consequence depends on the available opportunities. |

A discard remains relevant to `捨て牌振聴` after another player calls it. Describe its history rather than only whether it is still visible in the river. Temporary-furiten instructions must identify the exact event, such as the player's own draw, call, or subsequent discard, as specified by the preset. Preserve those timing distinctions when simplifying a sentence.

#### 4.5.5 Keep players and units of play distinct

| Concept | Japanese wording | Maintenance rule |
| --- | --- | --- |
| A complete game | `対局` | Use this for participation, match completion, and the overall result. |
| One phase ending in a win or draw | `局` | Use `この局`, `次局`, and labels such as `東1局`. |
| A cycle of dealer positions | `場`, `東場`, `南場` | Describe progression through `各場`. The number of dealer positions depends on player count. |
| Game length | `東風戦`, `東南戦`, `半荘戦` | Use the selected length. Dealer repeats can increase the number of hands played. |
| Extension | `南入`, `西入`, `延長戦` | Name the destination wind or maximum stage when explaining an extension. |
| One player's opportunity to act | `手番`, `ツモ番` | Use `ツモ番` when a draw specifically matters. |
| Turn cycle or ordinal turn | `巡`, `巡目` | State the relevant start and end events for timing rules. |
| Repeat counter | `本場` | Keep it separate from both the hand number and the riichi-stick count. |
| Initial and current dealer | `起家`, `親`, `東家` | `起家` identifies the initial dealer. `親` identifies the current dealer. |
| Other player roles | `子`, `自家`, `他家`, `上家`, `下家`, `対面` | `子` excludes the dealer. `他家` means the other players relative to the stated player. |

`上家` precedes a player in normal draw order, while `下家` follows them. In priority explanations, make the reference player explicit, as in `放銃者からツモ順で最も近い`. For lobby participation, use `ホスト`, `席順`, and `準備完了`. Drawing a wind tile is `風牌を引く`.

Write rule explanations from a defined player's perspective. Prefer `自家` for that player and `他家` for the others, with `上家`, `下家`, and `対面` relative to that perspective. Use `親`, `子`, `和了者`, or `放銃者` when the rule depends on that specific role. Reserve `あなた` for interface text and instructions that address the user directly. Preserve direct prompts such as `あなたの番` and ordinary subjectless instructions such as `牌を選んでください。`.

#### 4.5.6 Use Japanese scoring and settlement language

| Concept | Japanese wording | Maintenance rule |
| --- | --- | --- |
| Current and starting points | `持ち点`, `配給原点` | Explain the latter as `対局開始時の各プレイヤーの持ち点`. |
| Final conversion basis | `返し点` | Keep this separate from the score required to end an extended game. |
| Scoring components | `役`, `飜`, `符`, `基本点` | The repository's Riichi han unit is `飜`. `翻` is also established Japanese, including in other games' documentation. |
| Win value | `和了点`, `打点` | `和了点` suits a result or formula; `打点` suits discussion of a hand's value. |
| Settling points | `精算`, `点数の移動`, `点数の増減`, `収支` | Distinguish the settlement operation, transfers, and net change. |
| Paying and receiving | `支払う`, `払う`, `受け取る` | These are ordinary Japanese mahjong verbs. State the payer and recipient where the formula needs them. |
| Riichi deposits | `供託`, `立直棒`, `リーチ棒` | Identify the deposited balance separately from points transferred directly to a winner. |
| Final result | `順位`, `最終成績`, `ウマ`, `オカ` | Preserve the distinction between standings, converted final scores, and their adjustment components. |

Use `各` or `それぞれ` for an amount paid by each player and `合計` for a total. Use `本場分` or `本場代` for the honba component. Japanese `支払い` remains appropriate when a Chinese translation requires different wording.

#### 4.5.7 Write complete Japanese instructions

Use a clear actor, condition, and action. Keep the original values, placeholders, eligibility, and event order when improving a sentence.

| Writing purpose | Pattern to follow |
| --- | --- |
| State an enabled scoring condition | `副露していても、タンヤオが成立します。` |
| Explain an option's value | `対局開始時の各プレイヤーの持ち点です。` |
| Identify an action's object | `王牌の新しいドラ表示牌をめくります。` |
| Describe an ordinary ron payment | `放銃者が和了者に点数を支払います。` |
| Describe a display | `この局の点数の増減を表示します。` |
| Ask for a preparation action | `風牌を引いて席順を決めてください。` |
| Describe waiting | `他のプレイヤーの応答を待っています。` |

Preserve native Japanese rule text and localized examples in Japanese. English explanations in repository documentation describe the maintenance decision; future Japanese changes should still consult Japanese usage directly.

### 4.6 Japanese interface text, analysis, and counts

Use the existing Japanese action labels as the source for quoted controls in manuals. A status, an action, and a scoring award may share an English word while requiring different Japanese text.

| Display context | Project wording | Maintenance rule |
| --- | --- | --- |
| Preparing to start | `準備する` for the action; `準備完了` for the state | Keep both separate from `聴牌`, which describes a hand. Quote the label used by the actual control. |
| Declining the current reaction | `パス` | Describe the available action being declined when its consequences matter. This is separate from ending a hand or leaving a table. |
| Disabling call prompts | `鳴きなし` | Name the setting separately from `パス`. State its actual effect on calls and winning choices in the description. |
| Automatic actions | `自動和了`, `自動ツモ切り`, `自動理牌`, `自動北抜き` | Preserve the action named by each option. Drawing a tile, discarding it, declaring a win, and sorting the hand are distinct operations. |
| Participants | `プレイヤー`, `対局者`, `観戦者`; `CPU` in compact seat labels | Use `対局者` for people playing the match and `観戦者` for spectators. Retain `プレイヤー` as the project's general Minecraft spelling. |
| Timers | `持ち時間`, `長考時間` | Identify whether the allowance applies to one action, one hand, or the match. A new timer label must preserve when that allowance resets. |
| Tile analysis | `向聴数`, `待ち牌`, `残り枚数` | Distinguish a complete shape from a legal win. `和了可` requires the current rule conditions. When counts come from visible information, explain that scope rather than claiming knowledge of tiles left in the wall. |

Choose counters from the displayed quantity: `枚` for physical tiles, `種類` for tile types or distinct enabled awards, `組` for meld groups, `本` for point sticks, `人` for players, `点` for scores, and `位` for ranks. Preserve a field's meaning when shortening a label. A one-type wait can still have several physical copies available.

Write interface instructions with a clear object and verb, such as `牌を選ぶ`, `牌を切る`, `結果を確認する`, or `設定を変更する`. Use `してください` in complete instructions and short action labels on controls. Technical operations can naturally use `コマンドを実行する` or `牌譜を出力する`; those verbs do not need to be forced into ordinary tile-play explanations.

Follow the Japanese punctuation and spacing rules in `AGENTS.md`. Keep native punctuation inside localized values, including `「」`, `：`, `（）`, and `／`. English prose and the structure of this document use English punctuation. Preserve format specifiers exactly, and place particles and counters according to what each argument represents.

### 4.7 Japanese terminology for other rulesets

Use the Japanese name registered for the selected ruleset in result rows, rule headings, and references to scoring exclusions. Explain the pattern with ordinary Japanese sentences. A familiar Riichi mahjong name can help explain a pattern, but it does not replace the name registered for another ruleset.

In rule explanations, use `自家`, `他家`, or a precise role such as `親`, `和了者`, or `放銃者` when a subject is needed. Reserve direct address for interface instructions to the user.

Use `么` in Japanese terms such as `断么九` and `么九牌`. Chinese spellings follow their own locale conventions.

Use `国際公式麻雀` for Mahjong Competition Rules, `四川麻雀` for Sichuan mahjong, and `台湾麻雀` for Taiwanese mahjong. Preserve the exact names of presets and resource identifiers. The rules, scoring values, and preset conditions are owned by `docs/RULES.md`. Taiwanese mahjong composition and event timing are owned by `docs/TAIWAN_DESIGN.md`.

#### 4.7.1 Units and award names

| Ruleset and context | Preferred Japanese | Usage |
| --- | --- | --- |
| Mahjong Competition Rules award or qualification value | `点`, `点数`, `8点縛り` | The value is an additive point total. Use `役` for a scoring pattern and `役名` for its name. |
| Sichuan mahjong award or multiplier count | `飜`, `飜数` | Keep the multiplier count separate from `基礎点` and the points transferred between players. |
| Taiwanese mahjong award value | `台`, `台数` | Use `役` for an award and `台数` for its numerical value. Use `手役` when referring specifically to the ordinary hand component. |
| Taiwanese mahjong payment settings | `底`, `1台あたり`, `親`, `連荘` | `底` is the base included in each liable opponent's payment. `1台あたり` is the point amount multiplied by the applicable tai total. |

Keep the fixed name `無番和` in Mahjong Competition Rules. Its `番` does not change the scoring unit to `番`, and the Japanese Sichuan mahjong spelling choice `飜` does not apply inside this name. Resource identifiers containing `fan` or `tai` do not determine the displayed unit.

#### 4.7.2 Mahjong Competition Rules

The following names follow the Japanese list published by the Japan Mahjong Sports Association and the project's registered names. Use the complete name when naming an award, including in sentences about awards that cannot be combined.

| Preferred Japanese | Context and nearby terms |
| --- | --- |
| `一般高` | This is the award name. `一盃口` can explain the repeated sequence, while the applicable Mahjong Competition Rules conditions remain in force. |
| `碰碰和` | Retain this award name. `対々和` is a useful Japanese explanation and is also the project's name in Sichuan mahjong and Taiwanese mahjong. |
| `門前清`, `不求人` | These are separate awards. `門前` describes a concealed hand more generally. Use the registered award that matches the win method. |
| `平和` | Retain the name with its Mahjong Competition Rules definition. Its wait and call conditions differ from Riichi mahjong. |
| `坎張` | Use this spelling for the award. `嵌張待ち` remains appropriate when explaining the wait shape. |
| `単釣将` | Use the complete award name. `単騎待ち` explains the pair wait. Keep the full name in combination and exclusion lists. |
| `圏風刻`, `門風刻` | These name the round-wind and seat-wind awards. `場風` and `自風` are useful terms in explanations. Preserve `圏` in the registered award. |
| `大于五`, `小于五` | Preserve `于` in these award names, including references under `全大` and `全小`. |
| `清竜`, `花竜`, `組合竜`, `三色双竜会`, `一色双竜会` | Preserve `竜` throughout these names and their prose references. |
| `断么`, `全帯么`, `混么九`, `清么九`, `十三么` | Preserve the complete Mahjong Competition Rules names. `断么九`, `混老頭`, `清老頭`, and `国士無双` belong to other naming conventions or explanatory comparisons. |
| `七対`, `連七対` | Keep these registered names. The Sichuan mahjong award is registered as `七対子`. |
| `三槓`, `四槓` | Keep these award names. `三槓子` and `四槓子` can describe other rulesets' awards. |
| `五門斉`, `四帰一` | Preserve these Japanese forms rather than converting their characters from another locale. |
| `九連宝灯` | Preserve `連` and `灯`. Riichi mahjong uses the separate project spelling `九蓮宝燈`. |
| `槍槓和` | Preserve this Mahjong Competition Rules award name. The project uses `搶槓和` in Sichuan mahjong and `搶槓` in Taiwanese mahjong. |
| `双明槓`, `双暗槓`, `明槓と暗槓` | Keep the award name or descriptive combination that the score row represents. Obtain point values from the project's selected rules. |

Distinguish an award name from a description of its shape. For example, `「坎張」は嵌張待ちで成立する役です。` uses both spellings intentionally. A list of awards being counted should use `辺張・坎張・単釣将`.

#### 4.7.3 Sichuan mahjong

Use ordinary Japanese verbs around the imported rules terms. The terms marked as project labels below are deliberate localization choices. They do not establish a common naming standard across all Japanese descriptions of Sichuan mahjong.

| Context | Preferred Japanese | Usage |
| --- | --- | --- |
| The implemented win-and-retire mode | `血戦到底` | Keep this project label. A winner leaves the current hand while the remaining players continue. `血流成河` names a different mode. |
| Selecting a suit to remove | `定缺` | Keep this project label. Explain the action as `定缺で選んだ色の牌を先に捨てます。` |
| Four copies and declared kongs | `根`, `槓`, `帯根` | Preserve the registered label selected by the preset. The treatment of a declared kong differs between the presets. |
| Point transfers for a kong | `槓による点数の支払い`, `槓で受け取った点数` | These expressions describe transferred points. They are distinct from an award's `飜数`. |
| Winning on a kong replacement | `槓上花` | Keep the Sichuan mahjong award name. Other rulesets use `槓上開花` or `嶺上開花`. |
| A win attributed to a discard after a kong | `槓上砲` | Use `砲`, including in option names and descriptions. Preserve the preset's treatment of an added kong after a replacement. |
| Robbing a kong | `搶槓和` | Keep the registered award name. The same event must be classified according to the selected preset. |
| End-of-hand ready check and settlement | `查大叫` | Retain this imported project label. Explain the procedure as `流局時の聴牌確認` and its settlement as `その精算`. Preserve the label until a deliberate naming change covers its actual callers. |
| Returning kong points | `槓分返還`, `点数を返す` | `退税` is a source-game alias. State which already received points are returned and to whom. |
| Transferring kong points after a shooting event | `呼叫転移` | Keep this project label and explain it as transferring the applicable kong points to the winner or winners. It is distinct from returning points to the original payers. |
| Supplying a transfer shortfall | `呼叫転移の不足分補填` | Preserve the distinction between the transferred amount and the additional amount supplied by the liable player. |
| A void-suit violation | `花猪`, `能動花猪`, `受動花猪` | These are project labels for the selected rules. Keep the distinction between a competition-point deduction and transfers to other players. |
| Passing a win | `過水` | Explain the actual restriction and its reset conditions. The Sichuan mahjong comparison uses the applicable capped `飜数`. |

Japanese game terms such as `退税` and `雨銭` can help explain kong refunds and income. The project's interface describes those amounts directly as points under the selected preset.

Use `七対子` in Sichuan mahjong award references and prose. Preserve `七対` in Mahjong Competition Rules. Keep the imported label `查大叫` distinct from payment descriptions such as `聴牌料` and `ノーテン罰符`. A spelling change to `査大叫` requires an explicit project decision. The rules determine the recipients and amounts.

For a concealed-kong amount paid by each eligible opponent, an explanation from the declarer's perspective can say `自家が暗槓した場合、まだ和了していない他家が、それぞれこの点数を支払います。` Preserve the selected preset's recipients and amounts.

#### 4.7.4 Taiwanese mahjong

Use Japanese mahjong grammar with Taiwanese mahjong's own scoring and event conditions. Keep `台湾麻雀`, `平和`, and `台数` in their defined contexts. The more specific labels below follow the project's composed Taiwanese mahjong rules.

| Context | Preferred Japanese | Usage |
| --- | --- | --- |
| Winning generally or by a particular method | `和了`, `ロン`, `ツモ和了` | Use the method when it affects eligibility or scoring. The registered self-draw award remains `自摸`. |
| Concealed-hand awards | `門前`, `門前自摸` | Keep the registered names. A concealed-hand state, its award, and a self-draw event are distinct concepts. |
| Declaring a ready hand | `聴牌宣言` | The button describes a declaration. `聴牌` can name the ready state or its registered award. |
| The dealer and continuation | `親`, `連荘` | Use the established project terms. `荘家` occurs in other Japanese game text, but does not require renaming `親`. |
| Seat-matching flowers | `正花` | Retain this project choice in Japanese. Chinese uses the separate project terms `门花` and `門花`. Japanese `正花` is not presented as a universal Taiwanese mahjong standard. |
| Four seasons or four plants | `花牌セット` | Retain this project description. It differs from `正花` and from a flower victory. |
| A flower victory | `花牌和了`, `花牌による和了` | Keep the general event distinct from `八仙過海` and `七搶一`, which name its awards. |
| An ordinary win on a replacement | `補充牌によるツモ和了` | Its eligibility and scoring are separate from the automatic flower event. A flower victory alone does not establish an ordinary self-draw award. |
| Replacing a flower | `補花`, `花牌の補充` | These describe taking a replacement, not the victory event. |
| A replacement-tile award | `嶺上開花` | Keep the registered name. Its flower-replacement scope depends on the selected Taiwanese mahjong preset. |
| A single winning tile kind | `独聴` | This means one winning kind for the whole hand. It is broader than `単騎待ち` and does not mean one physical copy remains. |
| Passing a win | `過水` | Keep the Taiwanese mahjong term and its own clearing conditions. A draw alone does not clear the implemented restriction. |
| The inaccessible end of the wall | `山に残す牌` in explanations | The existing `王牌` setting labels refer to the Taiwanese mahjong reserve. Preserve its own draw and replacement restrictions. |

Describe the base once per liable payer. Describe dealer and continuation tai by asking whether the winner or that particular payer is the dealer. This keeps the text aligned with per-payer scoring when a flower victory and an ordinary win occur together.

Japanese `支払い`, `支払う`, `受け取る`, `返す`, and `加算する` are appropriate verbs for point settlement. State the actor and amount when a compact noun phrase would hide who owes or receives the points. Useful sentence patterns include:

- `和了者か支払う側が親の場合に、親と連荘による台数を加算します。`
- `両方が成立した場合は、花牌による和了と通常のツモ和了を別々に計算します。`
- `加槓は搶槓の対象になりますが、暗槓は対象になりません。`

Use these patterns with the ruleset's actual eligibility conditions. A Japanese word does not need replacement merely because a similar expression was unnatural in a Chinese translation.

#### 4.7.5 Identical names with different scope

| Japanese name | Mahjong Competition Rules | Sichuan mahjong | Taiwanese mahjong |
| --- | --- | --- | --- |
| `海底撈月` | A win on the final discard. The final self-draw award is `妙手回春`. | The project's award covers the final draw or its following discard. | A win on the final drawable tile. The final discard award is `河底撈魚`. |
| `平和` | Apply the Mahjong Competition Rules award definition. | This is not a registered project award. | Apply the Taiwanese mahjong award and preset conditions. |
| `過水` | This is not the name of a project restriction here. | Compare eligible wins by the capped multiplier and apply the Sichuan mahjong reset conditions. | Apply the Taiwanese mahjong passing and clearing conditions. |

Check the resource namespace before editing one of these names. Matching Japanese characters do not imply matching award conditions or event timing.

## 5. Tiles and variant-specific concepts

Physical tile names follow the existing `tile.mchjong.*` vocabulary: Simplified Chinese `万/筒/索`, Traditional Chinese `萬/筒/索`, Japanese `萬/筒/索`, and English Characters/Circles/Bamboo. `白`, `发/發`, and `中` are White Dragon, Green Dragon, and Red Dragon respectively. The three dragon types are Dragons; the broader category including winds is Honors. Do not call all honor tiles Dragons.

Alternatives such as `条/饼`, `紅中/發財/白板`, man/pin/sou, and dots/bams are acceptable in explanations for the relevant variant. They do not justify isolated changes to shared item names. `docs/RULES.md` defines `m/p/s` notation and the honor order from `1z` through `7z`. Display-name changes do not change the encoding.

| Scope | Preferred Chinese | Preferred Japanese | Preferred English | Semantic boundary |
| --- | --- | --- | --- | --- |
| Riichi mahjong dead wall | 王牌 | 王牌 | Dead Wall | Do not equate it with the Taiwanese mahjong reserved tail |
| Riichi mahjong kan replacement tile | 岭上牌/嶺上牌 | 嶺上牌 | Replacement Tile | Distinguish the physical replacement source from the conditions for Rinshan Kaihou |
| Riichi mahjong abortive draw | 途中流局 | 途中流局 | Abortive Draw | Distinguish it from wall exhaustion |
| Riichi mahjong deposit | 立直棒/供托; 供託 in Traditional Chinese | リーチ棒/供託 | Riichi Stick/Riichi Deposit | Keep it separate from Honba; Point Stick is the generic item name |
| Sichuan mahjong voided-suit selection | 定缺 | 定缺 | Choose Voided Suit; Voided Suit as a noun | Select the suit that must be cleared, not a suit to pause |
| Sichuan mahjong roots and kongs | 根/杠; 根/槓 | 根/槓 | Root/Kong | Whether they combine in scoring depends on the selected competition preset. Similar names do not merge the concepts. |
| Sichuan mahjong ready-hand check | 查叫/查大叫 | 查大叫 | Ready-Hand Check; Not-Ready Payment may identify the settlement item | Distinguish readiness and eligibility checks from the resulting score changes |
| Sichuan mahjong flower pig | 花猪/花豬 | 花猪 | Flower Pig | This concerns eligibility, including the voided suit, not possession of flower tiles |
| Sichuan mahjong kong payment transfer | 呼叫转移/呼叫轉移 | 呼叫転移 | Kong Payment Transfer | Transfer kong points; this is neither a telephone transfer nor a chi/pon action |
| Taiwanese mahjong flower replacement | 补花/補花 | 花牌の補充 | Flower Replacement | Explain it separately from a kong replacement and a flower win |
| Taiwanese mahjong flower win | 花胡 | 花牌による和了 | Flower Win | Eight Flowers and Seven Robbing One have preset-specific triggers and do not require the ordinary 17-tile winning shape |
| Taiwanese mahjong reserved tail | 保留牌/牌尾 | 残す牌 | Reserved Tiles | The reserved count depends on the preset; do not assume the 14-tile Riichi mahjong dead wall |

`docs/RULES.md` defines the supported features of the two Sichuan mahjong presets. Treat external examples such as `换三张` and `血流成河` according to that scope when describing MChjong's capabilities.

## 6. Complete scoring-name index

This index defines the **preferred names and their scope** for all four locales. English labels use Title Case, and descriptions follow the sentence-case convention in `AGENTS.md`. `docs/RULES.md` owns scoring values and excluded combinations.

Each entry identifies its resource location. Dora remain bonuses, and the combined Mahjong Competition Rules display item remains separate from the official scoring patterns.

### 6.1 Riichi mahjong yaku and special-settlement labels

Prepend `yaku.mchjong.` to each key suffix. English retains the established loanwords used in Riichi mahjong and descriptive names for dragon and wind labels. The selected preset determines whether Nagashi Mangan, Renhou, and special double yakuman are enabled.

| Key suffix | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| `chankan` | 抢杠 | 搶槓 | 搶槓 | Chankan |
| `chanta` | 混全带幺九 | 混全帶幺九 | 混全帯么九 | Chanta |
| `chihou` | 地和 | 地和 | 地和 | Chiihou |
| `chiitoitsu` | 七对子 | 七對子 | 七対子 | Chiitoitsu |
| `chinitsu` | 清一色 | 清一色 | 清一色 | Chinitsu |
| `chinroutou` | 清老头 | 清老頭 | 清老頭 | Chinroutou |
| `chun` | 中 | 中 | 中 | Red Dragon |
| `chuuren_nine_waiting` | 纯正九莲宝灯 | 純正九蓮寶燈 | 純正九蓮宝燈 | Pure Chuuren Poutou |
| `chuuren_poutou` | 九莲宝灯 | 九蓮寶燈 | 九蓮宝燈 | Chuuren Poutou |
| `daisangen` | 大三元 | 大三元 | 大三元 | Daisangen |
| `daisuushii` | 大四喜 | 大四喜 | 大四喜 | Daisuushii |
| `double_riichi` | 两立直 | 兩立直 | ダブル立直 | Double Riichi |
| `haitei_raoyue` | 海底摸月 | 海底摸月 | 海底摸月 | Haitei Raoyue |
| `haku` | 白 | 白 | 白 | White Dragon |
| `hatsu` | 发 | 發 | 發 | Green Dragon |
| `honitsu` | 混一色 | 混一色 | 混一色 | Honitsu |
| `honroutou` | 混老头 | 混老頭 | 混老頭 | Honroutou |
| `houtei_raoyui` | 河底捞鱼 | 河底撈魚 | 河底撈魚 | Houtei Raoyui |
| `iipeikou` | 一杯口 | 一杯口 | 一盃口 | Iipeikou |
| `ippatsu` | 一发 | 一發 | 一発 | Ippatsu |
| `ittsu` | 一气通贯 | 一氣通貫 | 一気通貫 | Ittsu |
| `junchan` | 纯全带幺九 | 純全帶幺九 | 純全帯么九 | Junchan |
| `kokushi_musou` | 国士无双 | 國士無雙 | 国士無双 | Kokushi Musou |
| `kokushi_thirteen_waiting` | 国士无双十三面 | 國士無雙十三面 | 国士無双十三面待ち | Kokushi Musou Thirteen-Sided Wait |
| `menzen_tsumo` | 门前清自摸和 | 門前清自摸和 | 門前清自摸和 | Menzen Tsumo |
| `nagashi_mangan` | 流局满贯 | 流局滿貫 | 流し満貫 | Nagashi Mangan |
| `pinhu` | 平和 | 平和 | 平和 | Pinfu |
| `renhou` | 人和 | 人和 | 人和 | Blessing of Man |
| `riichi` | 立直 | 立直 | 立直 | Riichi |
| `rinshan_kaihou` | 岭上开花 | 嶺上開花 | 嶺上開花 | Rinshan Kaihou |
| `round_wind` | 场风牌 | 場風牌 | 場風牌 | Round Wind |
| `ryanpeikou` | 二杯口 | 二杯口 | 二盃口 | Ryanpeikou |
| `ryuuiisou` | 绿一色 | 綠一色 | 緑一色 | Ryuuiisou |
| `sanankou` | 三暗刻 | 三暗刻 | 三暗刻 | Sanankou |
| `sankantsu` | 三杠子 | 三槓子 | 三槓子 | Sankantsu |
| `sanshoku_doujun` | 三色同顺 | 三色同順 | 三色同順 | Sanshoku Doujun |
| `sanshoku_doukou` | 三色同刻 | 三色同刻 | 三色同刻 | Sanshoku Doukou |
| `seat_wind` | 自风牌 | 自風牌 | 自風牌 | Seat Wind |
| `shousangen` | 小三元 | 小三元 | 小三元 | Shousangen |
| `shousuushii` | 小四喜 | 小四喜 | 小四喜 | Shousuushii |
| `suuankou` | 四暗刻 | 四暗刻 | 四暗刻 | Suuankou |
| `suuankou_tanki` | 四暗刻单骑 | 四暗刻單騎 | 四暗刻単騎 | Suuankou Tanki |
| `suukantsu` | 四杠子 | 四槓子 | 四槓子 | Suukantsu |
| `tanyao` | 断幺九 | 斷幺九 | 断么九 | Tanyao |
| `tenhou` | 天和 | 天和 | 天和 | Tenhou |
| `toitoi` | 对对和 | 對對和 | 対々和 | Toitoi |
| `tsuuiisou` | 字一色 | 字一色 | 字一色 | Tsuuiisou |

### 6.2 Dora bonus labels

These entries are bonuses, not qualifying yaku. Their keys still use the `yaku.mchjong.` prefix.

| Key suffix | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| `dora` | 宝牌 | 寶牌 | ドラ | Dora |
| `nuki_dora` | 拔北宝牌 | 拔北寶牌 | 抜きドラ | North Dora |
| `red_dora` | 赤宝牌 | 赤寶牌 | 赤ドラ | Red Dora |
| `ura_dora` | 里宝牌 | 裏寶牌 | 裏ドラ | Ura Dora |

### 6.3 Riichi mahjong limit labels

Prepend `score.mchjong.` to each key suffix. Counted Yakuman and natural yakuman remain distinct concepts even when their point values coincide.

| Key suffix | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| `baiman` | 倍满 | 倍滿 | 倍満 | Baiman |
| `haneman` | 跳满 | 跳滿 | 跳満 | Haneman |
| `kazoe_yakuman` | 累计役满 | 累計役滿 | 数え役満 | Counted Yakuman |
| `mangan` | 满贯 | 滿貫 | 満貫 | Mangan |
| `sanbaiman` | 三倍满 | 三倍滿 | 三倍満 | Sanbaiman |
| `yakuman` | 役满 | 役滿 | 役満 | Yakuman |
| `yakuman_2` | 两倍役满 | 兩倍役滿 | ダブル役満 | Double Yakuman |
| `yakuman_3` | 三倍役满 | 三倍役滿 | トリプル役満 | Triple Yakuman |
| `yakuman_4` | 四倍役满 | 四倍役滿 | 四倍役満 | Quadruple Yakuman |
| `yakuman_5` | 五倍役满 | 五倍役滿 | 五倍役満 | Quintuple Yakuman |
| `yakuman_6` | 六倍役满 | 六倍役滿 | 六倍役満 | Sextuple Yakuman |

### 6.4 The 81 official patterns in the Mahjong Competition Rules

Prepend `mcr.mchjong.fan.` to each key suffix. Use the names specific to the Mahjong Competition Rules. Traditional Chinese `closed_wait` uses 坎張. Verify values such as Two Concealed Kongs against the rule edition used by the preset.

| Key suffix | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| `all_chows` | 平和 | 平和 | 平和 | All Chows |
| `all_even_pungs` | 全双刻 | 全雙刻 | 全双刻 | All Even Pungs |
| `all_five` | 全带五 | 全帶五 | 全帯五 | All Fives |
| `all_green` | 绿一色 | 綠一色 | 緑一色 | All Green |
| `all_honors` | 字一色 | 字一色 | 字一色 | All Honors |
| `all_pungs` | 碰碰和 | 碰碰和 | 碰碰和 | All Pungs |
| `all_simples` | 断幺 | 斷幺 | 断么 | All Simples |
| `all_terminals` | 清幺九 | 清幺九 | 清么九 | All Terminals |
| `all_terminals_and_honors` | 混幺九 | 混幺九 | 混么九 | All Terminals and Honors |
| `all_types` | 五门齐 | 五門齊 | 五門斉 | All Types |
| `big_four_winds` | 大四喜 | 大四喜 | 大四喜 | Big Four Winds |
| `big_three_dragons` | 大三元 | 大三元 | 大三元 | Big Three Dragons |
| `big_three_winds` | 三风刻 | 三風刻 | 三風刻 | Big Three Winds |
| `chicken_hand` | 无番和 | 無番和 | 無番和 | Chicken Hand |
| `closed_wait` | 坎张 | 坎張 | 坎張 | Closed Wait |
| `concealed_hand` | 门前清 | 門前清 | 門前清 | Concealed Hand |
| `concealed_kong` | 暗杠 | 暗槓 | 暗槓 | Concealed Kong |
| `double_pung` | 双同刻 | 雙同刻 | 双同刻 | Double Pung |
| `dragon_pung` | 箭刻 | 箭刻 | 箭刻 | Dragon Pung |
| `edge_wait` | 边张 | 邊張 | 辺張 | Edge Wait |
| `flower_tiles` | 花牌 | 花牌 | 花牌 | Flower Tiles |
| `four_concealed_pungs` | 四暗刻 | 四暗刻 | 四暗刻 | Four Concealed Pungs |
| `four_kongs` | 四杠 | 四槓 | 四槓 | Four Kongs |
| `four_pure_shifted_chows` | 一色四步高 | 一色四步高 | 一色四歩高 | Four Pure Shifted Chows |
| `four_pure_shifted_pungs` | 一色四节高 | 一色四節高 | 一色四節高 | Four Pure Shifted Pungs |
| `full_flush` | 清一色 | 清一色 | 清一色 | Full Flush |
| `fully_concealed_hand` | 不求人 | 不求人 | 不求人 | Fully Concealed Hand |
| `greater_honors_and_knitted_tiles` | 七星不靠 | 七星不靠 | 七星不靠 | Greater Honors and Knitted Tiles |
| `half_flush` | 混一色 | 混一色 | 混一色 | Half Flush |
| `knitted_straight` | 组合龙 | 組合龍 | 組合竜 | Knitted Straight |
| `last_tile` | 和绝张 | 和絕張 | 和絶張 | Last Tile |
| `last_tile_claim` | 海底捞月 | 海底撈月 | 海底撈月 | Last Tile Claim |
| `last_tile_draw` | 妙手回春 | 妙手回春 | 妙手回春 | Last Tile Draw |
| `lesser_honors_and_knitted_tiles` | 全不靠 | 全不靠 | 全不靠 | Lesser Honors and Knitted Tiles |
| `little_four_winds` | 小四喜 | 小四喜 | 小四喜 | Little Four Winds |
| `little_three_dragons` | 小三元 | 小三元 | 小三元 | Little Three Dragons |
| `lower_four` | 小于五 | 小於五 | 小于五 | Lower Four |
| `lower_tiles` | 全小 | 全小 | 全小 | Lower Tiles |
| `melded_hand` | 全求人 | 全求人 | 全求人 | Melded Hand |
| `melded_kong` | 明杠 | 明槓 | 明槓 | Melded Kong |
| `middle_tiles` | 全中 | 全中 | 全中 | Middle Tiles |
| `mixed_double_chow` | 喜相逢 | 喜相逢 | 喜相逢 | Mixed Double Chow |
| `mixed_shifted_chows` | 三色三步高 | 三色三步高 | 三色三歩高 | Mixed Shifted Chows |
| `mixed_shifted_pungs` | 三色三节高 | 三色三節高 | 三色三節高 | Mixed Shifted Pungs |
| `mixed_straight` | 花龙 | 花龍 | 花竜 | Mixed Straight |
| `mixed_triple_chow` | 三色三同顺 | 三色三同順 | 三色三同順 | Mixed Triple Chow |
| `nine_gates` | 九莲宝灯 | 九蓮寶燈 | 九連宝灯 | Nine Gates |
| `no_honors` | 无字 | 無字 | 無字 | No Honors |
| `one_voided_suit` | 缺一门 | 缺一門 | 缺一門 | One Voided Suit |
| `out_with_replacement_tile` | 杠上开花 | 槓上開花 | 槓上開花 | Out with Replacement Tile |
| `outside_hand` | 全带幺 | 全帶幺 | 全帯么 | Outside Hand |
| `prevalent_wind` | 圈风刻 | 圈風刻 | 圏風刻 | Prevalent Wind |
| `pung_of_terminals_or_honors` | 幺九刻 | 幺九刻 | 么九刻 | Pung of Terminals or Honors |
| `pure_double_chow` | 一般高 | 一般高 | 一般高 | Pure Double Chow |
| `pure_shifted_chows` | 一色三步高 | 一色三步高 | 一色三歩高 | Pure Shifted Chows |
| `pure_shifted_pungs` | 一色三节高 | 一色三節高 | 一色三節高 | Pure Shifted Pungs |
| `pure_straight` | 清龙 | 清龍 | 清竜 | Pure Straight |
| `pure_terminal_chows` | 一色双龙会 | 一色雙龍會 | 一色双竜会 | Pure Terminal Chows |
| `pure_triple_chow` | 一色三同顺 | 一色三同順 | 一色三同順 | Pure Triple Chow |
| `quadruple_chow` | 一色四同顺 | 一色四同順 | 一色四同順 | Quadruple Chow |
| `reversible_tiles` | 推不倒 | 推不倒 | 推不倒 | Reversible Tiles |
| `robbing_the_kong` | 抢杠和 | 搶槓和 | 槍槓和 | Robbing the Kong |
| `seat_wind` | 门风刻 | 門風刻 | 門風刻 | Seat Wind |
| `self_drawn` | 自摸 | 自摸 | 自摸 | Self-Drawn |
| `seven_pairs` | 七对 | 七對 | 七対 | Seven Pairs |
| `seven_shifted_pairs` | 连七对 | 連七對 | 連七対 | Seven Shifted Pairs |
| `short_straight` | 连六 | 連六 | 連六 | Short Straight |
| `single_wait` | 单钓将 | 單釣將 | 単釣将 | Single Wait |
| `thirteen_orphans` | 十三幺 | 十三幺 | 十三么 | Thirteen Orphans |
| `three_concealed_pungs` | 三暗刻 | 三暗刻 | 三暗刻 | Three Concealed Pungs |
| `three_kongs` | 三杠 | 三槓 | 三槓 | Three Kongs |
| `three_suited_terminal_chows` | 三色双龙会 | 三色雙龍會 | 三色双竜会 | Three-Suited Terminal Chows |
| `tile_hog` | 四归一 | 四歸一 | 四帰一 | Tile Hog |
| `triple_pung` | 三同刻 | 三同刻 | 三同刻 | Triple Pung |
| `two_concealed_kongs` | 双暗杠 | 雙暗槓 | 双暗槓 | Two Concealed Kongs |
| `two_concealed_pungs` | 双暗刻 | 雙暗刻 | 双暗刻 | Two Concealed Pungs |
| `two_dragons_pungs` | 双箭刻 | 雙箭刻 | 双箭刻 | Two Dragon Pungs |
| `two_melded_kongs` | 双明杠 | 雙明槓 | 双明槓 | Two Melded Kongs |
| `two_terminal_chows` | 老少副 | 老少副 | 老少副 | Two Terminal Chows |
| `upper_four` | 大于五 | 大於五 | 大于五 | Upper Four |
| `upper_tiles` | 全大 | 全大 | 全大 | Upper Tiles |

The following **combined display item** summarizes two kongs. Its total and restrictions on duplicate counting follow the applicable scoring rules.

| Key suffix | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| `mixed_kong_pair` | 一明杠一暗杠 | 一明槓一暗槓 | 明槓と暗槓 | Melded and Concealed Kong |

### 6.5 Sichuan mahjong scoring-pattern labels

Prepend `sichuan.mchjong.fan.` to each key suffix. Use 平和/碰碰和 for the project's competition presets. Treat alternatives such as 基本胡/碰碰胡 as ruleset-specific. `root_with_kong` represents roots with kongs included according to the preset and is distinct from the separate Kong entry.

| Key suffix | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| `all_pungs` | 碰碰和 | 碰碰和 | 対々和 | All Pungs |
| `basic` | 平和 | 平和 | 基本形 | Basic Hand |
| `full_flush` | 清一色 | 清一色 | 清一色 | Full Flush |
| `golden_single_wait` | 金钩钓 | 金鉤釣 | 金鉤釣 | Golden Single Wait |
| `kong` | 杠 | 槓 | 槓 | Kong |
| `robbing_kong` | 抢杠和 | 搶槓和 | 搶槓和 | Robbing a Kong |
| `root` | 根 | 根 | 根 | Root |
| `root_with_kong` | 带根 | 帶根 | 帯根 | Root (including Kongs) |
| `seven_pairs` | 七对 | 七對 | 七対子 | Seven Pairs |
| `shoot_after_kong` | 杠上炮 | 槓上炮 | 槓上砲 | Win on a Discard after a Kong |
| `under_the_sea` | 海底捞月 | 海底撈月 | 海底撈月 | Last-Tile Win |
| `win_after_kong` | 杠上花 | 槓上花 | 槓上花 | Win after Kong |

### 6.6 Taiwanese mahjong scoring-pattern labels

Prepend `taiwan.mchjong.pattern.` to each key suffix. Use Chinese 门花/門花 and Japanese 正花. See section 3.4 for the meanings of Pinfu and Single Wait in Taiwanese mahjong. Add Taiwanese when comparing variants and use the shorter labels in an interface dedicated to Taiwanese mahjong.

| Key suffix | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| `all_from_others` | 全求人 | 全求人 | 全求人 | All from Others |
| `all_honors` | 字一色 | 字一色 | 字一色 | All Honors |
| `all_triplets` | 碰碰胡 | 碰碰胡 | 対々和 | All Triplets |
| `big_four_winds` | 大四喜 | 大四喜 | 大四喜 | Big Four Winds |
| `big_three_dragons` | 大三元 | 大三元 | 大三元 | Big Three Dragons |
| `concealed` | 门清 | 門清 | 門前 | Concealed Hand |
| `concealed_self_draw` | 门清自摸 | 門清自摸 | 門前自摸 | Concealed Self-Draw |
| `declared_ready` | 听牌 | 聽牌 | 聴牌 | Declared Ready |
| `earthly_ready` | 地听 | 地聽 | 地聴 | Earthly Ready |
| `earthly_win` | 地胡 | 地胡 | 地和 | Earthly Hand |
| `eight_flowers` | 八仙过海 | 八仙過海 | 八仙過海 | Eight Flowers |
| `five_concealed_triplets` | 五暗刻 | 五暗刻 | 五暗刻 | Five Concealed Triplets |
| `flower_set` | 花杠 | 花槓 | 花牌セット | Flower Set |
| `four_concealed_triplets` | 四暗刻 | 四暗刻 | 四暗刻 | Four Concealed Triplets |
| `full_flush` | 清一色 | 清一色 | 清一色 | Full Flush |
| `green_dragon` | 发财 | 發財 | 發 | Green Dragon |
| `half_flush` | 混一色 | 混一色 | 混一色 | Half Flush |
| `half_from_others` | 半求人 | 半求人 | 半求人 | Half from Others |
| `heavenly_ready` | 天听 | 天聽 | 天聴 | Heavenly Ready |
| `heavenly_win` | 天胡 | 天胡 | 天和 | Heavenly Hand |
| `human_win` | 人胡 | 人胡 | 人和 | Human Hand |
| `last_discard` | 河底捞鱼 | 河底撈魚 | 河底撈魚 | Last Discard |
| `last_draw` | 海底捞月 | 海底撈月 | 海底撈月 | Last Draw |
| `pinfu` | 平胡 | 平胡 | 平和 | Pinfu |
| `red_dragon` | 红中 | 紅中 | 中 | Red Dragon |
| `replacement_win` | 杠上开花 | 槓上開花 | 嶺上開花 | Replacement Win |
| `robbing_kong` | 抢杠 | 搶槓 | 搶槓 | Robbing a Kong |
| `round_wind` | 圈风 | 圈風 | 場風 | Round Wind |
| `seat_flower` | 门花 | 門花 | 正花 | Seat Flower |
| `seat_wind` | 门风 | 門風 | 自風 | Seat Wind |
| `self_draw` | 自摸 | 自摸 | 自摸 | Self-Draw |
| `seven_robs_one` | 七抢一 | 七搶一 | 七搶一 | Seven Robbing One |
| `single_wait` | 独听 | 獨聽 | 独聴（待ちが1種類） | Single Wait |
| `small_four_winds` | 小四喜 | 小四喜 | 小四喜 | Small Four Winds |
| `small_three_dragons` | 小三元 | 小三元 | 小三元 | Small Three Dragons |
| `three_concealed_triplets` | 三暗刻 | 三暗刻 | 三暗刻 | Three Concealed Triplets |
| `white_dragon` | 白板 | 白板 | 白 | White Dragon |

### 6.7 Inline ancient yaku names

These project localizations are in `awards` within `engine/src/main/resources/yaku_sets/mahjong_soul_ancient.json`. Prepend `mahjongsoul:` to each ID suffix. Use Kanburi for the award on another player's discard after a kan. The JSON and `docs/RULES.md` own the conditions, values, and exclusions.

| ID suffix | `zh_cn` | `zh_tw` | `ja_jp` | `en_us` |
| --- | --- | --- | --- | --- |
| `tsubame_gaeshi` | 燕返 | 燕返 | 燕返し | Swallow Reversal |
| `kanburi` | 杠振 | 槓振 | 槓振り | Kanburi |
| `four_open_melds` | 十二落抬 | 十二落抬 | 十二落抬 | Four Open Melds |
| `uumen_sai` | 五门齐 | 五門齊 | 五門斉 | Five Gates |
| `sanrenkou` | 三连刻 | 三連刻 | 三連刻 | Three Consecutive Triplets |
| `isshoku_sanjun` | 一色三同顺 | 一色三同順 | 一色三順 | Three Identical Sequences |
| `ippin_moyue` | 一筒摸月 | 一筒摸月 | 一筒摸月 | Moon from the Well |
| `chuupin_raoyui` | 九筒捞鱼 | 九筒撈魚 | 九筒撈魚 | Fish from the River |
| `renhou` | 人和 | 人和 | 人和 | Blessing of Man |
| `daisuurin` | 大数邻 | 大數鄰 | 大数隣 | Big Numbers |
| `daisharin` | 大车轮 | 大車輪 | 大車輪 | Big Wheels |
| `daichikurin` | 大竹林 | 大竹林 | 大竹林 | Bamboo Forest |
| `ishino_uenimo_sannen` | 石上三年 | 石上三年 | 石の上にも三年 | Three Years on a Stone |
| `dai_chisei` | 大七星 | 大七星 | 大七星 | Big Seven Stars |

## 7. Change locations and review procedure

| Content | Locations to inspect |
| --- | --- |
| UI text, buttons, hints, and settlement names in all four locales | `common/src/main/resources/assets/mchjong/lang/{en_us,ja_jp,zh_cn,zh_tw}.json` |
| Inline yaku-set names and descriptions | `engine/src/main/resources/yaku_sets/*.json` |
| Terminology in reference documentation | `docs/RULES.md` and other documents that actually discuss the concept |
| Dynamically assembled sentences and placeholder bindings | The relevant client display code and public engine results; establish the meaning before changing the text |
| Images, audio, and external resources | Source assets and generation inputs that actually contain text or speech; searching JSON alone does not cover text embedded in images |

When changing a concept, search its key, preferred term, and known alternatives, then inspect each context. Scope replacements to the specific locale and concept so that specific red-tile names, variant-specific written forms, and distinct scoring units remain intact.

A review should answer the following questions:

1. Is the wording correct for the locale, variant, concept, and display context? Have tile names, bonuses, actions, or states been conflated?
2. Does the rewrite preserve who acts, who is affected, when it takes effect, the quantities involved, and every exception? Does it read naturally as a mahjong explanation, using a consistent player perspective and the register appropriate to rules or direct instructions?
3. Does the change cover all affected titles, prose, hints, settlement labels, inline localizations, and documentation? Is a shared key used by multiple variants?
4. Do the four locales retain identical key sets and compatible format specifiers? Are the order, count, and types of `%s` and other placeholders, intact? Do generated specific red-tile names remain correct?
5. If the alternative name is also reasonable, is the benefit of changing it sufficient? When a preferred term changes, have its definition, affected locales, and rule scope been updated in this guide?

Use focused checks to verify the intended value of each affected key and preserve placeholders. Read the explanatory prose for natural phrasing and complete conditions. [VERIFICATION.md](docs/VERIFICATION.md) owns test scope and commands. Add behavioral verification when correcting the corresponding rule behavior.
