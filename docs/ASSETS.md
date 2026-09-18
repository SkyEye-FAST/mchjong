# Artwork and resource contract

Both loaders use the same generated textures and models. Run
`gradlew.bat :art:generateAssets`; the resources are published to
`build/generated/assets`. Edit the generator, not the generated PNG or JSON files.

Server data is generated separately by `GenerateData` through
`gradlew.bat :art:generateData` into `build/generated/data`. Both loaders include
both directories. Recipes, tags and loot do not belong in `GenerateAssets`.
Each output is synchronized independently with the current generator results.

## Tile faces

The 45 faces use the individual PNG source tiles under
`presets/tile_faces/kanto/tiles/` and `presets/tile_faces/kansai/tiles/`, assigned to Kanto and
Kansai respectively.
`TileArtwork` maps their transparent engravings to runtime
cells, including all three red fives, preserving proportions and antialiased
edges. Both designs are ordinary mod resources selected in the mahjong box;
there is no runtime pack installation or network access.

Source metadata and [the artwork notice](../presets/tile_faces/NOTICE.md) ship
under `META-INF/licenses/`. The supplied Kansai metadata identifies
lietxia (M+ Fonts License). Neither preset is relicensed under the repository's code license.

`assets/mchjong/textures/tiles.png` (Kansai) and `kanto/tiles.png` are 2048 by 4096
atlases, eight cells per row, each with a matching `tile_glyphs.png` transparent
engraving atlas in the same directory.
Cells 0-26 are 1-9 characters, circles and bamboo; 27-33 are East, South, West,
North, white, green and red dragon. Cells 34-36 are the three red fives in suit
order. Cells 37-44 are numbered 1q-8q. Kansai uses spring, summer, autumn,
winter, plum, orchid, bamboo and chrysanthemum; Kanto uses the four seasons
followed by fortune, prosperity, longevity and nobility (福禄寿貴).
All faces preserve the supplied artwork and proportions.
Physical component faces are 34-41, separate from engine wall tile IDs.
The white dragon is intentionally blank. Name tooltips follow the stored preset.
World, held-item and GUI renderers all use the selected design's atlas pair.
The asset test compares every atlas cell directly against its generated design,
including all 45 distinct faces, red fives, flowers, opacity and the declared order.

The bottom-right 32 by 32 pixels, beginning at (2016, 4064), are a solid white
material swatch. Keep this swatch white when replacing the atlas: the renderer
uses it for opaque face plates and table display colors, independently of the printed
faces. Texture metadata enables linear filtering and clamping; the shared tile
render types also retain linear filtering at draw time (vanilla entity render
types override the metadata). Face UVs stop at half-pixel insets to avoid
sampling neighboring cells. The client smoke checks the live OpenGL minification
and magnification filters after rendering, including after resource-pack reloads.

## Mahjong dye items

`MahjongDyeArtwork` draws original 16 by 16 four-ink pouch textures at
`assets/mchjong/textures/item/mahjong_dye.png`, `creative_mahjong_dye.png`, `red_dora_dye.png` and `undo_dye.png`.
The reusable variant has a purple pouch and brass seal; red dora dye uses a red pouch and ink. All use vanilla
generated-item models and can be replaced independently by resource packs.

## Tile backs and customization

`assets/mchjong/textures/tile/back.png` is a separate 256 by 384 texture. Every
pixel of the default back is opaque white, a neutral tint mask for the item's
vanilla dye component. Defaults render solid blue.
The material-colored body, opaque white face plate and shallow back shell supply
the tile's physical shape; the back shell samples the top-left pixel and applies
the same dye tint. Every material, including wood, amethyst and glass, has a white
printed surface. Glass transparency is restricted to the body, not the face plate.

The body uses original neutral relief textures under `textures/tile_material/`:
`wood.png`, `bone.png`, `quartz.png`, `calcite.png`, `glass.png`, and `amethyst.png`.
`TileMaterialArtwork` generates their grain, pores, veins, reflections and facets
without borrowing game textures; the material component supplies their tint.
The body and the beveled white and colored shells meet edge-to-edge without
internal caps or overlapping side polygons. Their combined surface is closed.
First-person supplies sit beside the native outstretched hand rather than shifting
the grip toward the screen center. Tiles are turned to expose their thickness. Their tops
lean outward like a held sword while the printed faces turn inward toward the
player. Point sticks follow the same free-end and printed-surface relationship,
retaining their slender length, width and thickness. Both meshes leave clearance above the hotbar and
inside a 4:3 viewport. Minecraft mirrors these transforms for the left hand.
`HeldSupplyArm` attaches the player's native arm and sleeve to the lower tile edge
or point-stick end. The grip follows the model's first-person transform and the
same vanilla equip and swing pose as the item, while the arm retains player scale.
Both main-hand preferences and offhand supplies use the corresponding skin arm.
Invisible players retain the vanilla hidden-arm presentation.

For a focused grip check, run `gradlew.bat :runSmokeClient -PsmokeItems=true` or
`gradlew.bat :neoforge:runSmokeClient -PsmokeItems=true`. This reuses the item
presentation captures and writes nine screenshots to each loader's
`build/smoke/items-evidence/screenshots` directory, separately from full gameplay evidence.
The shared item capture renders both supplies with each main-hand preference in
the native first-person view at 1280 by 800, with an empty-hand reference for each
side, then repeats the right-hand comparisons at 640 by 480. Inspect the resulting
screenshots for native-like hand placement, grip
contact, readable printed faces and clearance above the hotbar.

To supply a design, create a normal resource pack for your target Minecraft
version containing that same texture
path and the matching `pack_format`. Keep it opaque, preserve the 2:3 aspect ratio
and use an unmarked, uniform rim so that the back shell matches. Selection,
persistence and reloading use Minecraft's resource-pack system. Custom backs do
not replace the face atlas or affect private game data.

Concealed tiles and physical rear faces use the independent back texture.
Front and back geometry is batched by material rather than switching render
buffers for each tile.

## Table and other resources

`assets/mchjong/textures/point_sticks.png` is a 384 by 160 atlas of five 32-pixel
strips: blank ivory, ivory with eight gray dots (100), blue with one white dot
(1,000), yellow with five white dots (5,000), and red with nine white dots
(10,000). Round markings, a subtle recessed panel and molded edge highlights
follow the colored physical-stick style. `PointStickArtwork` generates the atlas
deterministically. `FurnitureMesh.stick` uses it for both printed faces and the
unmarked sides in inventory, held-item and table renders. Half-pixel UV insets
and linear filtering keep the strips separate. Resource packs can replace the
atlas, including body colors, without changing the existing denominations.

Furniture uses fifteen original 16 by 16 pixel textures under
`assets/mchjong/textures/furniture`: eleven `wood_<family>.png` finishes and
`felt.png`, `steel.png`, `brass.png`, `edge.png`. `FurnitureArtwork` generates
clustered wood grain, neutral felt, broad metal highlights and dark edge material
deterministically with small, discrete palettes and nearest-neighbor sampling.
[Create's framed machinery](https://github.com/Creators-of-Create/Create) and
[Farmer's Delight's crafted wooden utensils](https://github.com/vectorwing/FarmersDelight) inform
the restrained material separation and pixel scale. The patterns are original.
Wood components select the finish; neutral fabric is tinted by the existing dye
component. Resource packs can replace these paths directly.
The block atlas explicitly stitches the wooden particle sprite through
`assets/minecraft/atlases/blocks.json`; it does not duplicate the runtime textures.
Tile inventory icons use front lighting so their white faces remain readable.

`FurnitureShape` supplies small textured boxes, tapered legs, clipped-corner
bevels and a continuous mitered table rim, with outward normals and consistent
pixel density of sixteen texels per world block. Coplanar polygons share local-space
texture coordinates, keeping the grain continuous across subdivided caps.
Large material clusters, square fasteners, cushion tufts and ventilation slots
remain readable at the seated camera distance. `FurnitureMesh` shares
the resulting geometry between blocks and items. Ordinary tables have framed
playing surfaces, beveled rails, tapered legs and stretchers; automatic tables
have a metal pedestal, a low plinth, a brass band and ventilation slots. Stools
have tapered wooden legs, stretchers, padded fabric and four tufts, with a
10/16-block seat height. Their mesh, collision and seat anchor share
`TableGeometry.STOOL_HEIGHT`; seated and overhead cameras remain independently
anchored to the table and keep their current framing.
The dark wooden case has a fitted
lid, corner hardware, paired clasps, hinges, a handle and a small tile inlay.
Folded cloth uses the same woven material as the table. None of this adds block
IDs. `TableGeometry` defines the 2.875-block frame and 2.625-block playing surface;
rails and cloth retain their material density. The 3 by 3 occupancy
and colliders clipped to the frame remain in the world implementation. Bare panels
and installed cloth finish at the same playing height.

`assets/mchjong/textures/tile_glyphs.png` has the same cells and white material
swatch as the GUI face atlas, but transparent backgrounds. All material-aware
world and item faces use this glyph atlas. Glass additionally uses a sorted
alpha-blended pass after opaque backs and table surfaces. A custom face pack
should replace both atlases to cover GUI thumbnails and world/item rendering.
Private tile faces are withheld by the server and never resolved by the hidden
tile mesh. There is no per-tile world entity. `TileMesh.WIDTH`, `HEIGHT` and `DEPTH`
define the physical envelope used by picking and table layout. Wall columns and
layers touch edge-to-edge; river steps equal the tile width and length, with the
extra width of a sideways riichi tile accounted for. Called discards still leave
the visible river without changing the original discard identities.

The four source language files live in `common/src/main/resources`. Their engine
action and ruleset keys match the corresponding names in lowercase. Upstream
yaku names are lowercased only when constructing translation keys.

Yet Another Bingo is a visual reference, not an asset dependency. No artwork or
code from that project or riichi_advanced is bundled.

## Verification

`gradlew.bat :art:check` verifies atlas coordinates and distinct faces, source
order, red fives, the blank white dragon, texture resolution and filtering,
uniform default backs, resource-pack customization paths, language keys, original furniture
materials, source integrity and byte-for-byte reproducible generation. Client-side
unit tests cover bevel winding, tapered legs, all wood/dye combinations, mesh bounds,
white face plates, picking and exact wall/river contact, including riichi discards.

`gradlew.bat runSmokeClient` exercises placement, seating, a private deal and a
discard in an isolated Fabric world, then reloads the resources. It checks that
filtering and resource bytes survive reload.
Screenshots and the final result are written beneath `build/smoke/evidence`.
The held-item screenshots cover both main-hand preferences at 1280 by 800. Real table views
additionally exercise the world render paths.
`:neoforge:runSmokeClient` runs the same assertions and screenshots under
`neoforge/build/smoke/evidence`. Resource reload checks include all furniture textures.
