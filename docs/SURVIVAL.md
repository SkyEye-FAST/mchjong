# Survival equipment and play

The survival loop is deliberately short: build furniture, cut blanks in a
stonecutter, engrave a set in its box, then store the box inside a clothed table.
It uses vanilla crafting and stonecutting with batch preparation for full sets.

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

Stools, output two, with matching wood and two white carpets for their cushions:

```text
C C       C = white carpet
S S
F F
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
bulk-engraving 144 blanks. Creative inventory also exposes the eight designs.
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
To remove the cloth, end the match, empty both hands and sneak-use the top. If physical
point sticks are present on the nearest side, the top first returns that stack.
Equipment collection takes priority over spectator interaction; a sneak-click
opens the spectator view only when it does not collect any equipment.
Breaking a table returns its furniture, both stored boxes, cloth and placed sticks; equipment
storage is cleared before spawning drops so occupancy-cell removal cannot
duplicate the contents. An unfinished hand is not fabricated into a result.
Explosion drop decay still follows vanilla rules for the furniture block; the
world tests disable that decay when asserting an exact furniture-item count.
Stored equipment is returned once by the removal callback.

## Ordinary and automatic table flows

An ordinary table offers these server-authorized actions:

1. Ready the players; the dealer chooses **Shuffle tiles**.
2. Each participant chooses **Build my wall** once.
3. Participants take starting tiles in turn: three rounds of four, then one.
4. The dealer explicitly draws the fourteenth tile; subsequent normal and
   replacement draws require **Draw a tile** from the player whose turn it is.
5. Discard, call, win, and continue using the existing table controls. The
   engine checks rules, furiten, legal calls, yaku, han/fu, payments and results.

These are physical handling steps in the in-world controls, not a free-form
wall editor. Humans do not shuffle or draw automatically on a timeout; training
bots perform their own handling actions. Normal decision clocks still govern
discards and claim responses. State and unfinished handling steps survive saves.

The automatic table uses the same installed physical set and referee but
performs shuffle, wall construction, packet dealing and draws automatically.
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

Use a marked stick on a table to place one on the nearest side; matching sticks
stack there. Sneak-use the top with both hands empty to retrieve that side's
stack. They are stored and dropped as real item stacks, rendered with their
denomination. Physical point sticks can be placed and retrieved during a match;
the active-game equipment lock applies to the box and cloth, not these trays.
These interactions never call the scoring engine or modify
points, honba or riichi deposits. The engine's riichi animation is a rule-state
indicator, not permission to mint or withdraw physical currency.

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
ordinary glass-tile table. `ManualTableSmoke` clicks the actual shuffle, wall,
four packet, draw, discard and exit controls through normal client/server
packets. It verifies waiting does not handle tiles for the human, private hands
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
