# Survival equipment and play

The survival loop is deliberately short: build furniture, cut blanks in a
stonecutter, engrave a set in its box, then store the box inside a clothed table.
It uses vanilla crafting and stonecutting with batch preparation for full sets.

## Recipe browsing

With JEI or EMI installed, inspect an item with the viewer's recipe/usage keys.
The ordinary five-color mahjong dye uses the normal crafting category. Face
printing is performed inside a held box. The supplies catalogue places empty
and complete cases together and lists point sticks in ascending denomination.

Component-aware crafting views show the marking reagent and one or eight blank
sticks in separate crafting slots; the result contains the same number of marked
sticks. Back-dye examples include 144 blanks and four 1,000-point sticks in their
separate compartment. Hover the case stacks to inspect their actual contents.
Back dyeing changes the relevant color
while retaining faces, red markings, material and stored sticks. Table upgrading
uses an empty ordinary table and retains its wood and custom name.

The recipe browser's normal crafting and stonecutting controls remain available.
The case, table-storage and point-stick screens reserve their complete panels,
including summaries and controls, for the native container interface.
See [COMPATIBILITY.md](COMPATIBILITY.md) for development profiles and the scope
of the finite case examples.

## Registry entries

| Name | Registry ID | Kind |
| --- | --- | --- |
| Ordinary mahjong table | `mchjong:mahjong_table` | Block and item |
| Automatic mahjong table | `mchjong:automatic_mahjong_table` | Block and item |
| Mahjong stool | `mchjong:mahjong_stool` | Block and item |
| Table cloth | `mchjong:table_cloth` | Item |
| Mahjong tile, blank or engraved | `mchjong:mahjong_tile` | Item |
| Mahjong box | `mchjong:mahjong_box` | Storage item |
| Point stick, blank or marked | `mchjong:point_stick` | Item |
| Dice | `mchjong:dice` | Item |
| Mahjong dye | `mchjong:mahjong_dye` | Item, stacks to 64 |
| Red dora dye | `mchjong:red_dora_dye` | Item, stacks to 64 |
| Undo dye | `mchjong:undo_dye` | Item, stacks to 64 |
| Creative mahjong dye | `mchjong:creative_mahjong_dye` | Creative-only item, stacks to 1 |
| Table occupancy cell | `mchjong:table_space` | Internal block, no item or recipe |

The two tables share the `mchjong:mahjong_table` block entity type. Stools use
`mchjong:mahjong_stool`. `mchjong:seat` remains the non-persistent seating entity.

## Components and variants

| Component | Value | Used by |
| --- | --- | --- |
| `mchjong:wood` | `oak`, `spruce`, `birch`, `jungle`, `acacia`, `dark_oak`, `mangrove`, `cherry`, `bamboo`, `crimson`, `warped` | Table and stool items |
| `mchjong:tile` | `{face, material, red}` | Tile items |
| `mchjong:face_preset` | `kansai`, `kanto` | Built-in tile-face design |
| `mchjong:points` | `-10000`, `0`, `100`, `1000`, `5000`, `10000` | Point sticks |
| `minecraft:base_color` | One of the 16 vanilla dye colors | Tile backs, cloth, stool cushions |
| `minecraft:container` | Native item-stack container | Mahjong boxes |

Tile `face = -1` means unengraved. Faces `0..26` are the three suits; `27..33`
are the winds and dragons, in engine order. Faces `34..41` are spring, summer,
autumn, winter, plum, orchid, bamboo and chrysanthemum in Kansai, written `1q..8q`.
Kanto's `5q..8q` show fortune, prosperity, longevity and nobility (福禄寿貴).
Tooltips use localized names by default; Settings > Handling switches to mpsz/q notation.
Only faces `4`, `13`, `22` can have
`red = true`. Material is one of `wood`, `bone`, `quartz`, `calcite`, `glass`,
`amethyst`. Invalid faces, red combinations and denominations are rejected by
the component codecs. Identical components stack normally. Undyed tile backs use
their material texture; default cloth is cyan and stool cushions are white.

All custom components are immutable codec values with persistence and network
serialization. No item IDs are generated for particular faces, back colors, woods or
denominations. Placed furniture projects its item components into its block
entity, including drops and pick-block results. Private box contents are never
part of a furniture appearance update.

## Crafting recipes

Ordinary table, output one; all seven ingredients use the same wood family:

```text
S S S       S = wooden slab
S . S       F = wooden fence
F . F       . = empty
```

Padded wooden stools, output two, with slabs and fences from the same wood family:

```text
C C       C = white carpet
S S       S = wooden slab
F F       F = wooden fence
```

Automatic table, output one and preserve the ordinary table's wood and name:

```text
I R I       I = iron ingot, R = redstone dust
R T R       T = ordinary mahjong table
C H C       C = copper ingot, H = hopper
```

Three same-color carpets in a horizontal row make one matching cloth.
A mahjong box uses three wooden slabs across the top row, leather/chest/leather
across the middle row and one iron nugget in the bottom center. The slabs may
use different wood types; the box keeps its single appearance.
A cloth, stool or individual tile plus one dye changes its color, shapeless.
A box plus one dye recolors every tile back in the box, including spare blanks;
the materials, faces, red flags and point sticks are unchanged.

One each of black, red, green, blue and white dye make one mahjong dye, shapeless. Ordinary
mahjong dye stacks to 64; creative mahjong dye stacks to one and is supplied
through the creative catalogue, not survival crafting or loot.

Open a box and put exactly **136 or 144 matching blanks** into its tile slots.
Insert mahjong dye into the dedicated dye slot and select **Apply faces**.
The 136-tile set has four ordinary copies of each face and no red fives;
144 additionally includes one of each of the eight flowers. These sets occupy
34 and 42 stacks respectively.
One ordinary dye is consumed per successful
operation; creative dye remains unchanged, even when used by a survival player.
Materials, back colors and point sticks are preserved. Invalid counts, mixed
blanks, invalid selections and already-matching no-red sets do not consume anything.

Choose **Kansai** or **Kanto** in the box. Both designs are built into the mod;
the selection applies to inventory items, the world table, immersive hands,
action previews and settlement. Reprinting an existing complete set applies
the face preset and restores ordinary fives, including when the
selected face preset is already in use. Other tile components are preserved. Tile-back
recoloring remains the separate vanilla-grid dye recipe.

One vanilla red dye crafts **four red dora dyes**. Combine one red dora dye with
one ordinary 5m, 5p or 5s to make the corresponding red five. Material, back color,
face preset and other tile components stay unchanged. Keep spare red fives in the
box and choose no reds, three reds or four reds in the rule screen. Three reds
use one of each suit; four reds use one red 5m, two red 5p and one red 5s. Every
required face still has four playable copies, counting ordinary and red tiles
separately. The box may contain more tiles than play requires. For example,
136 ordinary tiles plus four spare reds cover all three compositions. Sanma
requires only 108 tiles and ignores 2m through 8m. A single box must supply the
entire matching subset; two boxes do not combine. Mahjong Soul and Tenhou retain
their preset names for these options. M.League specifies three reds, while
JPML A and WRC specify no reds. Unavailable red choices show a shortage tooltip.

One vanilla black dye crafts **four undo dyes**. Combine one undo dye with one
red five to restore its ordinary five, preserving its material, back color,
face preset, custom name and other components. This recipe consumes one tile
and one undo dye per craft, independently of the box's face-printing operation.

Eight unmarked point sticks, each in its own crafting slot, plus one
matching dye produce eight marked sticks:

| Dye | Denomination |
| --- | ---: |
| White dye | 100 |
| Blue dye | 1,000 |
| Yellow dye | 5,000 |
| Red dye | 10,000 |
| Black dye | −10,000 |

Already marked sticks cannot be remarked. Component-preserving crafting operations
use shared custom crafting serializers, not ingredients that accidentally
ignore component values. They still use the vanilla crafting grid. The recipes
are documented here; component-dependent recipes are special
recipes. Face printing instead uses the box's server-authorized menu button.

## Stonecutting

One plank of any of the eleven vanilla woods, bone block, quartz block, calcite,
glass block or amethyst block yields **16 unengraved tiles** of its corresponding
material. Each wooden blank retains its wood species. One bone block can
alternatively yield **24 unmarked point sticks**.

The eight flowers are physical tiles in all sixteen materials and sixteen back colors.
They can be carried, stored in a box, dyed and dropped like the other tiles.
Nine source blocks make the 144 blanks used to print a complete flower set.
Riichi uses the validated 136-tile set (108 in sanma), while flowers remain
stored safely inside the case during those games.

## Boxes, installation and removal

Right-click a held box to open its dedicated supply-case screen. It has 45 tile
slots, nine point-stick slots, a dice slot beside the sticks and one dye slot; shift-clicks route each supply to
its own compartment. The carried box slot is locked for ordinary clicks,
shift-transfer, number keys, offhand swaps, dragging, double-click collection,
throwing and creative cloning. Neither the carrier nor another box can be
inserted. Changes persist immediately in `minecraft:container`. Closing the
menu, replacing it, losing the carrier slot or dying permanently invalidates
that menu; returning the carrier does not revive an old handle. Oversized or nested
command-created containers cannot be silently truncated by opening or crafting.

Both tables need a clear 3 by 3 footprint, clear space immediately above it and
solid support beneath the center. Place stools two blocks from the center on
the four cardinal sides, outside that footprint. The playing surface is 2.625
blocks across; melds extend left from the owner's right corner beside the hand.
The hand remains centered while it fits, shifting left only enough to clear the
actual melds and any drawn tile, without reserving unused slots.

Right-click either table, including any footprint cell, to open its two-case
storage screen. Each slot holds one box inside the table, keeping the playing surface clear.
Empty and incomplete boxes can be stored and retrieved. The first complete box
compatible with the selected rules, reading left to right, supplies the game;
the second box is not combined with it. Changing rules rechecks the box selection.
Use normal clicks, shift-clicks or number-key swaps to move cases. The server
checks distance, table identity and match state on every transaction. Closing
the screen, leaving range, breaking the table or starting a match permanently
invalidates an old menu. Both cases are locked during a match.

The table selects exactly 136 tiles for four-player play or 108 for sanma,
with the chosen red-five composition and matching material, back color and face
preset. Every required ordinary and red face must be sufficiently stocked in
one box. Additional copies, unused faces, unengraved tiles, flowers and point
sticks remain in storage. Surplus tiles with a different appearance do not
invalidate an otherwise complete matching subset. The box
is transferred into table storage, not copied, including when using creative
inventory transfers. There is no free virtual set when a table is empty.

Use a cloth on either table to install or replace it. Both table types require
a cloth before play; a bare wooden table cannot ready players or start practice.
To remove the cloth, end the match, empty both hands and sneak-use the top.
Retrieve point sticks through the side drawers' container slots.
Equipment collection takes priority over spectator interaction; a sneak-click
opens the spectator view only when it does not collect any equipment.
Breaking a table returns its furniture, both stored boxes, cloth and stored point sticks; equipment
storage is cleared before spawning drops so occupancy-cell removal cannot
duplicate the contents. An unfinished hand is not fabricated into a result.
Explosion drop decay still follows vanilla rules for the furniture block; the
world tests disable that decay when asserting an exact furniture-item count.
Stored equipment is returned once by the removal callback.

## Ordinary and automatic table flows

Prepare at least two dice in the installed cases and the fixed starting kit
for each participating seat: 25000 points uses ten 100s, four 1000s, two 5000s
and one 10000. A 30000-point start adds one 5000; 35000 adds one 10000.
Custom starts retain the small payment denominations and adjust the remainder.
Missing denominations are topped up from the installed cases in one transaction;
insufficient combined stock prevents starting without moving any supplies.
When bankruptcy is disabled, each seat also needs one −10000 bust stick.
Bone meal, white dye and black dye craft two dice. The complete creative case
includes two dice.

An ordinary table offers these server-authorized actions:

1. Ready the players; the dealer sweeps the face-down tiles across the felt to shuffle.
2. Each participant drags their highlighted loose tiles toward their own wall.
3. Once every wall is built, the dealer picks up the two central dice and rolls
   them to determine the opening. Hover the central dice to see both faces and
   their total. Every new hand requires a new dealer roll.
4. Participants pull starting packets from the highlighted wall stack toward
   their hands in turn: three rounds of four, then one.
5. The dealer pulls the fourteenth tile into their hand. Subsequent normal and
   replacement draws use the same drag interaction at the appropriate wall stack.
6. Discard, call and win with the existing rules controls. Hand over physical
   point sticks through the side drawers, close the result panel, and sweep
   your tiles to the center to collect them for the next hand.

The gesture completes when released over its destination. Escape cancels a
held gesture; Tab focuses the physical source and Enter or Space activates it.
The server validates the current action and decision token. Human preparation and drawing
wait for player input; training bots perform their own steps. Settlement advances
after 10 seconds, allowing collection gestures during that interval. Normal decision
clocks govern discards and claim responses, starting with 30 seconds per decision
and a 120-second reserve per hand. The lobby host can edit both allowances.
After riichi, normal draws and drawn-tile discards advance after a short server-paced
pause. Legal wins, concealed kans and north extractions retain explicit choices.
State and unfinished handling steps
survive saves. The referee checks rules, furiten, calls, yaku, han/fu and settlement.

The automatic table uses the same installed physical set and referee but
performs shuffle, wall construction, packet dealing and draws automatically.
Its in-match controls store each player's sorting, win, call, drawn-tile discard
and three-player north-extraction preferences with their seat. Compact and
expanded buttons both toggle their settings directly. Sorting starts enabled. Automatic wins use
the referee's legal tsumo and ron actions, including declaration-robbery responses.
Wins take priority over north extraction, skipped calls or automatic discards.
Auto kita uses the referee's legal declarations, including riichi restrictions
and opponents' robbery responses, before considering an automatic discard. Preferences remain
with the player across hands and saves and reset when their seat is released.
For three-player rules, the engine uses the appropriate 108 tiles. The omitted
28 tiles are optional stock and remain in the box when present. No stored tiles
are consumed by playing. Completed hands use the existing replay archive.

## Glass, backs and physical point sticks

Glass bodies use an alpha-blended render pass and a transparent glyph
atlas. Colored backs remain opaque.
Hidden hands reach unauthorized clients only as hidden sentinels, and the
renderer never resolves a face texture for such a sentinel. Moving a camera
behind a glass tile therefore cannot reveal an opponent's printed face.

The default back is a neutral texture tinted by the 16 dye colors. Custom
resource packs can replace `assets/mchjong/textures/tile/back.png`.
See [ASSETS.md](ASSETS.md).

Ordinary tables have four wooden drawers below their side rails. Open a drawer
by interacting with its front, including from the seated overlay. The native
inventory shortcut E also opens your own drawer while seated. The
container exposes nine scoring slots and a separate final reserve slot per player.
The black −10000 bust stick shares the red 10000 stick's markings and is crafted
from eight blank sticks and black dye. It has no value in the reserve slot; moving it
to a scoring slot subtracts 10000 from the physical balance.
Marked denominations share a drawer;
matching stacks use vanilla splitting, drag distribution and shift-click rules.

Take sticks from your own row and place the chosen amount in the recipient's
row to pay by hand. During a match, other human players' drawers accept deposits;
their owners control withdrawals. The host handles bot drawers in practice.
Before play, the highlighted drawer receives shift-clicked inventory stacks.
During play, payments stay within the table: backpack insertion, removal and
hotbar exchanges are locked. Closing returns any unpaid cursor sticks to the
drawers and returns the player to the table overlay. Ending or abandoning the
match restores the opening drawer contents and returns the two dice to their
case. These reserved supplies are not consumed or duplicated by play.

The screen pairs each physical total with the referee's signed game score.
Scores provide the settlement reference; players handle the actual currency
through inventory transactions. Drawers remain available to seated participants
during play. Full item components persist in private table saves and are returned
once on removal. Public appearance updates describe furniture and tile colors.

## Code ownership and checks

`item/` owns immutable components and atomic inventory transformations;
`recipe/` owns component-sensitive vanilla-grid/stonecutter recipes.
`TableEquipment` owns removable stacks and their public appearance projection;
`ManualHandling` owns physical handling phases inside the engine. Existing
scoring and replay code remain independent of Minecraft items.

`FurnitureMesh`, `TileMesh` and `MahjongItemRenderer` share geometry between
world and inventory rendering. Loader entry points register components,
serializers, blocks, items and renderers without duplicating these rules.
NeoForge defers intrusive object creation until registry events.

`GenerateAssets` generates textures and client models only. `GenerateData`
serializes server data through `SurvivalRecipes` and `FurnitureData`, with
separate synchronized Gradle outputs and reproducibility checks.

Run `gradlew.bat buildAll`. `ManualHandlingTest` checks all presets, stale
actions, conservation, reloads and a complete manually handled bot hand.
`PhysicalSuppliesTest` loads real recipes and component codecs on a dedicated
NeoForge test server. `BoxMenuSmoke` checks native container operations and exact
inventory conservation on a real server player, including 136/144-tile printing,
ordinary/creative dye consumption, invalid buttons and closed menus. The equipment
smokes exercise survival placement, occupancy-cell interaction, replacement,
unloading, private/public save separation, sanma's recoverable unused tiles,
root/occupancy destruction and native explosions in a real world.

The shared client harness operates both an equipped automatic table and an
ordinary glass-tile table. `ManualTableSmoke` drags actual scattered tiles and
wall stacks to shuffle, build, take all four packets and draw, then uses normal
discard and exit controls through client/server packets. It verifies that human
handling waits for input, private hands
remain hidden, an in-progress manual save round-trips, and the full box can be
recovered after exit. Screenshots include the manual phases, two-case inventory,
colored furniture, cloth and physical point sticks.
`ItemPresentationSmoke` also selects the actual hotbar items and sends native
drop actions for both tables, cloth, a glass tile, a point stick and a stool.
It checks inventory counts and the synchronized dropped-item components while
capturing their first-person and dropped appearances, without spawning visual
copies of those items.

Run `gradlew.bat :fabric:runSmokeClient --console=plain` for Fabric, then
`gradlew.bat :neoforge:runSmokeClient --console=plain` for NeoForge. Each run
clears old PASS/FAIL markers before launch;
only a fresh successful completion writes PASS. Inspect that run's logs and
screenshots in `fabric/build/smoke/evidence` or `neoforge/build/smoke/evidence`.
