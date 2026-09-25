# Supply data and recipe contracts

This technical reference defines item identity, persistence and recipe examples.
For crafting and table setup, use [Survival equipment](SURVIVAL.md).

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
`mchjong:mahjong_stool`. `mchjong:seat` is the non-persistent seating entity.
The optional Create adapter additionally registers `mchjong:mahjong_printing_plate`
and `mchjong:incomplete_mahjong_box` for the [workshop](CREATE.md).

## Components and variants

| Component | Value | Used by |
| --- | --- | --- |
| `mchjong:wood` | `oak`, `spruce`, `birch`, `jungle`, `acacia`, `dark_oak`, `mangrove`, `cherry`, `bamboo`, `crimson`, `warped` | Table and stool items |
| `mchjong:tile` | `{face, material, red}` | Tile items |
| `mchjong:face_preset` | Built-in `kansai` or `kanto`, or a configured ZIP preset | Tile-face design |
| `mchjong:back_preset` | Default or a configured ZIP preset | Tile-back pattern |
| `mchjong:points` | `-10000`, `0`, `100`, `1000`, `5000`, `10000` | Point sticks |
| `minecraft:base_color` | One of the 16 vanilla dye colors | Tile backs, cloth, stool cushions |
| `minecraft:container` | Native item-stack container | Mahjong boxes |

Tile `face = -1` means unengraved. Faces `0..26` are the three suits; `27..33`
are the winds and dragons, in engine order. Faces `34..41` are spring, summer,
autumn, winter, plum, orchid, bamboo and chrysanthemum in Kansai, written `1q..8q`.
Kanto's `5q..8q` show fortune, prosperity, longevity and nobility (福禄寿貴).
Tooltips use localized names by default; Settings > Handling switches to mpsz/q notation.
Only faces `4`, `13`, `22` can have `red = true`. Material is one of the eleven
wood variants in `TileMaterial`, or `bone`, `quartz`, `calcite`, `glass` or
`amethyst`. Invalid faces, red combinations and denominations are rejected by
the component codecs. Identical components stack normally. Undyed tile backs use
their material texture and selected pattern; default cloth is cyan and stool cushions are white.

All custom components are immutable codec values with persistence and network
serialization. No item IDs are generated for particular faces, back colors, woods
or denominations. Placed furniture projects its item components into its block
entity, including drops and pick-block results. Private box contents are never
part of a furniture appearance update.

## Implementation ownership

`item/` owns immutable values and atomic inventory transformations;
`recipe/` owns component-sensitive crafting and stonecutting recipes.
`TableEquipment` owns removable stacks and their public appearance projection;
`ManualHandling` owns physical handling phases inside the engine. Scoring and
replay code remain independent of Minecraft items.

`FurnitureMesh`, `TileMesh` and `MahjongItemRenderer` share geometry between
world and inventory rendering. Loader entry points register components,
serializers, blocks, items and renderers without duplicating these rules.
NeoForge defers intrusive object creation until registry events.

`GenerateAssets` generates textures and client models. `GenerateData` serializes
server data through `SurvivalRecipes` and `FurnitureData`, with separate
synchronized Gradle outputs and reproducibility checks.

## Recipe catalogue and identity

`MahjongCatalog` is shared by the loaders. Empty and complete 144-tile cases are
consecutive. Blank tiles cover sixteen materials; point sticks are ordered by
denomination, including the inactive negative stick. Furniture covers all eleven
wood types. Flowers and red fives share the existing tile item.

Recipe identity distinguishes wood, material, back color, face preset, face, red
markings, denomination and complete container components. An outer custom name
is cosmetic for lookup; crafting still applies the source recipe's name-preservation
rules. Examples are built from the recipes in the loaded datapack. Every cycling
ingredient is accepted only when the source recipe produces the displayed output
with identical components and count.

Finite displays cover eight-stick marking batches, all table woods and sixteen
back colors on representative tiles, stools, cloth, boxes, red fives and flowers.
Case examples cover each material with blue backs: a completed set, or 144 blanks
with four 1,000-point sticks in the separate compartment. Five-color mahjong dye
uses the viewer's normal crafting category. Dye colors cycle only when crafting
yields the exact displayed output.

Arbitrarily rearranged, mixed-material or specially named contents retain exact
identities and follow the same server recipes; these unbounded layouts are not
all pre-enumerated as viewer examples. See [Verification](VERIFICATION.md) for
native transaction, conservation and loaded-recipe checks.
