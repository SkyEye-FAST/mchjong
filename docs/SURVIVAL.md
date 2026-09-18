# Survival equipment and play

The survival loop is deliberately short: build furniture, cut blanks in a
stonecutter, engrave a set in its box, then store the box inside a clothed table.
It uses vanilla crafting and stonecutting with batch preparation for full sets.

## Recipe browsing

With JEI or EMI installed, inspect an item with the viewer's recipe/usage keys.
Inspect a blue-backed bone blank's uses to find every engraved face, including
flowers and red fives. Engraving examples pair matching material and back color on the input
and output. The supplies catalogue places the empty and complete cases together
and lists point sticks in ascending denomination.

Component-aware crafting views show the marking reagent and one or eight blank
sticks in separate crafting slots; the result contains the same number of marked
sticks. Full-set engraving shows 136 matching blanks in a case plus an ink sac.
Additional examples include 144 blanks and four 1,000-point sticks: engraving
keeps the eight spare blanks and the sticks beside the completed set. Hover the
case stacks to inspect their actual contents. Dyeing changes the relevant color
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
| Table occupancy cell | `mchjong:table_space` | Internal block, no item or recipe |

The two tables share the `mchjong:mahjong_table` block entity type. Stools use
`mchjong:mahjong_stool`. `mchjong:seat` remains the non-persistent seating entity.

## Components and variants

| Component | Value | Used by |
| --- | --- | --- |
| `mchjong:wood` | `oak`, `spruce`, `birch`, `jungle`, `acacia`, `dark_oak`, `mangrove`, `cherry`, `bamboo`, `crimson`, `warped` | Table and stool items |
| `mchjong:tile` | `{face, material, red}` | Tile items |
| `mchjong:points` | `0`, `100`, `1000`, `5000`, `10000` | Point sticks |
| `minecraft:base_color` | One of the 16 vanilla dye colors | Tile backs, cloth, stool cushions |
| `minecraft:container` | Native item-stack container | Mahjong boxes |

Tile `face = -1` means unengraved. Faces `0..26` are the three suits; `27..33`
are the winds and dragons, in engine order. Faces `34..41` are plum, orchid,
chrysanthemum, bamboo, spring, summer, autumn and winter. Only faces `4`, `13`, `22` can have
`red = true`. Material is one of `wood`, `bone`, `quartz`, `calcite`, `glass`,
`amethyst`. Invalid faces, red combinations and denominations are rejected by
the component codecs. Identical components stack normally. Default backs are
blue; default cloth is green and stool cushions are white.

All custom components are immutable codec values with persistence and network
serialization. No item IDs are generated for particular faces, dyes, woods or
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

Floor-cushion stools, output two in a 2 x 2 crafting grid, with two matching
wooden slabs and two white carpets:

```text
C C       C = white carpet
S S       S = wooden slab
```

Automatic table, output one and preserve the ordinary table's wood and name:

```text
I R I       I = iron ingot, R = redstone dust
R T R       T = ordinary mahjong table
C H C       C = copper ingot, H = hopper
```

Three same-color carpets in a horizontal row make one matching cloth.
A vanilla chest and one string make a mahjong box, shapeless.
A cloth, stool or individual tile plus one dye changes its color, shapeless.
A box plus one dye recolors every tile back in the box, including spare blanks;
the materials, faces, red flags and point sticks are unchanged.

Put at least 136 blanks of matching material and back color in a box, then craft
that box with one ink sac. Exactly 136 blanks are engraved; spare blanks and
point sticks remain in the result. The set contains four of each of the 34
faces, replacing one five in each suit with a red five, not adding three extra
tiles. Thus nine source blocks produce 144 blanks: one set and eight spares.
The output occupies 37 tile stacks, plus the retained spare and stick stacks.
The entire recipe fails unchanged if the box lacks enough room or the blanks
do not match. It never partly consumes a set.

One to eight unmarked point sticks, each in its own crafting slot, plus one
marking material produce the same number of marked sticks:

| Marking material | Denomination |
| --- | ---: |
| Black dye | 100 |
| Redstone dust | 1,000 |
| Lapis lazuli | 5,000 |
| Gold nugget | 10,000 |

Already marked sticks cannot be remarked. All component-preserving operations
use shared custom crafting serializers, not ingredients that accidentally
ignore component values. They still use the vanilla crafting grid. The recipes
are documented here; component-dependent recipes are special
recipes rather than a separate custom crafting interface.

## Stonecutting

One plank of any vanilla wood, bone block, quartz block, calcite, glass block or
amethyst block yields **16 unengraved tiles** of its corresponding material.
All wooden blanks use the single `wood` material; furniture still retains its
specific wood species. One bone block can alternatively yield **16 unmarked
point sticks**.

Put a blank tile in a stonecutter to select any of the 34 ordinary faces,
three red fives or eight flower designs. One blank makes one tile, preserving its material and back
color. This is for replacements or individual pieces, not the normal full-set
path. Engraved tiles cannot be recut into another face. The 45 shared engraving
recipes use the native stonecutting interface and recipe type.
The shared stonecutter hook invalidates vanilla's item-ID-only cache when a
tile's components change. Swapping material, color or an already engraved tile
into the input clears the old selection and output before another result can
be taken. The recipe also independently rejects nonblank inputs on assembly.

The eight flowers are physical tiles in all six materials and sixteen back colors.
They can be carried, stored in a box, dyed and dropped like the other tiles. Cut
eight extra blanks to complete a 144-tile case, or carve the eight spares left by
bulk-engraving 144 blanks. Their faces use the same tile component and engraving
recipes as the other individual tiles.
Riichi uses the validated 136-tile set (108 in sanma), while flowers remain
stored safely inside the case during those games.

## Boxes, installation and removal

Right-click a held box to open its dedicated supply-case screen. Its 54 slots accept
tiles and point sticks only. The carried box slot is locked for ordinary clicks,
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
Empty and incomplete boxes can be stored and retrieved. The first complete box,
reading left to right, supplies the game; the second box is not combined with it.
Use normal clicks, shift-clicks or number-key swaps to move cases. The server
checks distance, table identity and match state on every transaction. Closing
the screen, leaving range, breaking the table or starting a match permanently
invalidates an old menu. Both cases are locked during a match.

The table validates the physical
136-tile multiset, three red fives, uniform material and uniform back color.
Spare unengraved tiles, flowers and point sticks are allowed in the box. A mixed-back,
mixed-material, incomplete or duplicate-faced set cannot start a game. The box
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

An ordinary table offers these server-authorized actions:

1. Ready the players; the dealer sweeps the face-down tiles across the felt to shuffle.
2. Each participant drags their highlighted loose tiles toward their own wall.
3. Participants pull starting packets from the highlighted wall stack toward
   their hands in turn: three rounds of four, then one.
4. The dealer pulls the fourteenth tile into their hand. Subsequent normal and
   replacement draws use the same drag interaction at the appropriate wall stack.
5. Discard, call and win with the existing rules controls. Hand over physical
   point sticks through the side drawers, close the result panel, and sweep
   your tiles to the center to collect them for the next hand.

The gesture completes when released over its destination. Escape cancels a
held gesture; Tab focuses the physical source and Enter or Space activates it.
The server validates the current action and decision token. Human handling
waits for player input; training bots perform their own steps. Normal decision
clocks govern discards and claim responses, starting with 30 seconds per decision
and a 120-second reserve per hand. The lobby host can edit both allowances.
After riichi, normal draws and drawn-tile discards advance after a short server-paced
pause. Legal wins, concealed kans and north extractions retain explicit choices.
State and unfinished handling steps
survive saves. The referee checks rules, furiten, calls, yaku, han/fu and settlement.

The automatic table uses the same installed physical set and referee but
performs shuffle, wall construction, packet dealing and draws automatically.
Its collapsible in-match controls store each player's sorting, win, call and
drawn-tile discard preferences with their seat. Sorting starts enabled. Automatic wins use
the referee's legal tsumo and ron actions, including declaration-robbery responses.
Wins take priority over skipped calls or automatic discards. Preferences remain
with the player across hands and saves and reset when their seat is released.
For three-player rules, the engine uses the appropriate 108 tiles; the unused
28 remain part of the installed, recoverable 136-tile set. Tiles are not
consumed by playing. Completed hands use the existing replay archive.

## Glass, backs and physical point sticks

Glass bodies use a genuinely alpha-blended render pass and a transparent glyph
atlas; they are not merely painted pale blue. Colored backs remain opaque.
Hidden hands reach unauthorized clients only as hidden sentinels, and the
renderer never resolves a face texture for such a sentinel. Moving a camera
behind a glass tile therefore cannot reveal an opponent's printed face.

The default back is a neutral texture tinted by the 16 dye colors. Custom
resource packs can replace `assets/mchjong/textures/tile/back.png`.
See [ASSETS.md](ASSETS.md).

Ordinary tables have four wooden drawers below their side rails. Open a drawer
by interacting with its front, including from the seated overlay. The native
inventory shortcut E also opens your own drawer while seated. The
container exposes nine slots per player. Marked denominations share a drawer;
matching stacks use vanilla splitting, drag distribution and shift-click rules.

Take sticks from your own row and place the chosen amount in the recipient's
row to pay by hand. During a match, other human players' drawers accept deposits;
their owners control withdrawals. The host handles bot drawers in practice.
The opened drawer is highlighted and receives shift-clicked inventory stacks.
Closing the container while seated returns to the table overlay.

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
inventory conservation on a real server player. `StonecutterSmoke` checks cached
recipe invalidation and both ordinary and shift-click engraving. The equipment
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

Run `gradlew.bat :runSmokeClient --console=plain` for Fabric, then
`gradlew.bat :neoforge:runSmokeClient --console=plain` for NeoForge. The leading
colon selects only the root Fabric task. The unqualified `runSmokeClient` task
selector includes both loaders, with an explicit ordering constraint so they
never launch together. Each run clears old PASS/FAIL markers before launch;
only a fresh successful completion writes PASS. Inspect that run's logs and
screenshots in `build/smoke/evidence` or `neoforge/build/smoke/evidence`.
