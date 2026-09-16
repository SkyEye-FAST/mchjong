# Survival equipment and play

The survival loop is deliberately short: build furniture, cut blanks in a
stonecutter, engrave a set in its box, then install the box at a table. There is
no new workstation, battery, diamond component or per-tile crafting grind.

## Registry entries

| Name | Registry ID | Kind |
| --- | --- | --- |
| Ordinary mahjong table | `mchjong:mahjong_table` | Block and item |
| Automatic mahjong table | `mchjong:automatic_mahjong_table` | Block and item |
| Mahjong stool | `mchjong:mahjong_stool` | Block and item |
| Table cloth | `mchjong:table_cloth` | Item |
| Mahjong tile, blank or engraved | `mchjong:mahjong_tile` | Item |
| Mahjong box | `mchjong:mahjong_box` | Item, not a workstation |
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
are the winds and dragons, in engine order. Only faces `4`, `13`, `22` can have
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
ignore component values. They still use the vanilla crafting grid. Their rules
are described in the item tooltips; component-dependent recipes are special
recipes rather than a separate custom crafting interface.

## Stonecutting

One plank of any vanilla wood, bone block, quartz block, calcite, glass block or
amethyst block yields **16 unengraved tiles** of its corresponding material.
All wooden blanks use the single `wood` material; furniture still retains its
specific wood species. One bone block can alternatively yield **16 unmarked
point sticks**.

Put a blank tile in a stonecutter to select any of the 34 ordinary faces or
three red fives. One blank makes one tile, preserving its material and back
color. This is for replacements or individual pieces, not the normal full-set
path. Engraved tiles cannot be recut into another face. The 37 shared engraving
recipes use the native stonecutting interface and recipe type.

## Boxes, installation and removal

Right-click a held box to open a vanilla six-row container. Its 54 slots accept
tiles and point sticks only. The carried box slot is locked while open;
shift-transfer and offhand swaps cannot move the carrier or nest another box.
Changes persist directly in `minecraft:container`. Oversized or nested
command-created containers cannot be silently truncated by opening or crafting.

Use a full box on either table to install it. The table validates the physical
136-tile multiset, three red fives, uniform material and uniform back color.
Spare unengraved tiles and point sticks are allowed in the box. A mixed-back,
mixed-material, incomplete or duplicate-faced set cannot start a game. The box
is transferred into table storage, not copied in survival; replacing it returns
the previous box. There is no free virtual set when a survival table is empty.

Use a cloth on either table to install or replace it. Cloth is optional: a bare
wooden surface remains playable. To remove equipment, end the match, empty both
hands and sneak-use: the top removes cloth, a side removes the box. If physical
point sticks are present on the nearest side, the top first returns that stack.
Breaking a table returns its furniture, box, cloth and placed sticks; equipment
storage is cleared before spawning drops so occupancy-cell removal cannot
duplicate the contents. An unfinished hand is not fabricated into a result.

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
resource packs may still replace `assets/mchjong/textures/tile/back.png`.
No patterned-back pack is bundled. See [ASSETS.md](ASSETS.md).

Use a marked stick on a table to place one on the nearest side; matching sticks
stack there. Sneak-use the top with both hands empty to retrieve that side's
stack. They are stored and dropped as real item stacks, rendered with their
denomination. These interactions never call the scoring engine or modify
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
separate Gradle outputs and reproducibility checks. Retired generated data is
removed, not retained as compatibility recipes.

Run `gradlew.bat buildAll`. `ManualHandlingTest` checks all presets, stale
actions, conservation, reloads and a complete manually handled bot hand.
`PhysicalSuppliesTest` loads real recipes and component codecs on a dedicated
NeoForge test server. The shared smoke harness uses an equipped automatic table
with glass tiles: `gradlew.bat runSmokeClient` and
`gradlew.bat :neoforge:runSmokeClient`.
