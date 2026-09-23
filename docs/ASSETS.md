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
Resource packs can override or add definitions at
`assets/<namespace>/tile_face_presets/<name>.json`. The file path defines the
persistent preset ID `<namespace>:<name>`, for example:

```json
{"atlas":"example:textures/ink/tiles.png","glyphs":"example:textures/ink/tile_glyphs.png"}
```

Supply both PNGs using this atlas layout. The box selector discovers definitions
on every resource reload, ordered by namespaced ID. Override
`assets/mchjong/tile_face_presets/kansai.json` or `kanto.json` to redirect a built-in
design, or replace its PNGs directly. Pack priority follows Minecraft's normal
resource selection. Add `preset.<namespace>.<name>` to the pack's language files
(replace `/` in nested names with `.`), including `en_us`, `ja_jp`, `zh_cn` and
`zh_tw`. The built-in flower labels remain associated with the stored preset.

Printing stores only the preset ID on tiles and synchronizes it with table
appearance. A client resource pack is sufficient to print a new design; the
server validates the physical set, carrier, menu and dye transaction. Other
players need the same resources to display the design. Removing the pack keeps
stored IDs intact and removes its selector entries; unavailable designs display
Minecraft's missing texture until their resources are installed.
The asset test compares every atlas cell directly against its generated design,
including all 45 distinct faces, red fives, flowers, opacity and the declared order.

The bottom-right 32 by 32 pixels, beginning at (2016, 4064), are a solid white
material swatch. Keep this swatch white when replacing the atlas: the renderer
uses it for opaque face plates and table display colors, independently of the printed
faces. Texture metadata enables linear filtering and clamping. GUI faces retain
their independent linearly filtered atlas. World and item engravings use
`TileFaceTexture`: it composites the resource-pack engraving over the white face
plate before generating mipmaps with Minecraft's mip generator, preserving thin
antialiased strokes without alpha-cutout loss. The shared face RenderType uses
trilinear minification and linear magnification. The five mip levels of the native
atlas retain exact cell and white-swatch boundaries; lower-resolution packs use
fewer levels. Face UVs stop at half-pixel insets. The client smoke checks uploaded
mip dimensions and live world/GUI filters, including after resource-pack reloads.
The print occupies 240 × 320 pixels within each 256 × 384 cell, retaining source
proportions and the physical tile envelope.

## Mahjong dye items

`MahjongDyeArtwork` draws original 16 by 16 four-ink pouch textures at
`assets/mchjong/textures/item/mahjong_dye.png`, `creative_mahjong_dye.png`, `red_dora_dye.png` and `undo_dye.png`.
Gathered folds, a lit cloth edge, a tied cord and a sewn label keep the small silhouettes readable.
The reusable variant has a purple pouch and brass seal; red dora dye uses a red pouch and ink. All use vanilla
generated-item models and can be replaced independently by resource packs.

## Tile backs and customization

Undyed tiles use their own material texture for the rear face and back shell, so
wood, bone, quartz, calcite, glass and amethyst have no separate default back
color. `assets/mchjong/textures/tile/back.png` is a separate 256 by 384 transparent
pattern layer applied to every rear face, including undyed and glass tiles and
concealed face covers. Its default pixels are fully transparent. The tile's
`BASE_COLOR` component selects one of Minecraft's sixteen dye colors without
changing the material-colored body or opaque white printed surface.

The pattern preserves its own colors and alpha independently of dye. Dyeing a glass tile tints its
translucent glass material instead, matching the stained-glass model while its
rear face continues to use `textures/tile_material/glass.png`. Undyed glass keeps
the neutral glass tint. Resource packs can replace the material textures and the
back pattern independently. Dyed opaque shells use the solid white
`textures/tile/plain.png` tinted by the dye, beneath the pattern.

The body uses original neutral relief textures under `textures/tile_material/`:
`wood.png`, `bone.png`, `quartz.png`, `calcite.png`, `glass.png`, and `amethyst.png`.
`TileMaterialArtwork` generates original 16 by 16, limited-palette grain, pores,
chalk veins, stepped glass reflections and crystal facets
without borrowing game textures; the material component supplies their tint.
World, item and immersive body surfaces use nearest-neighbor sampling to retain
the pixel clusters independently of the smoothly filtered printed faces.
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

For a focused grip check, run `gradlew.bat :fabric:runSmokeClient -PsmokeItems=true` or
`gradlew.bat :neoforge:runSmokeClient -PsmokeItems=true`. This reuses the item
presentation captures and writes nine screenshots to
`fabric/build/smoke/items-evidence/screenshots` or
`neoforge/build/smoke/items-evidence/screenshots`, separately from full gameplay evidence.
The shared item capture renders both supplies with each main-hand preference in
the native first-person view at 1280 by 800, with an empty-hand reference for each
side, then repeats the right-hand comparisons at 640 by 480. Inspect the resulting
screenshots for native-like hand placement, grip
contact, readable printed faces and clearance above the hotbar.

To supply a back design, create a normal resource pack for your target
Minecraft version containing the `textures/tile/back.png` path and matching
`pack_format` (34 for Minecraft 1.21.1). Preserve the 2:3 aspect ratio; transparent
pixels reveal the tile material or dye beneath, and partial alpha is supported.
On face-down wall tiles, the image's top points toward the table center.
The pattern covers the flat cap while the bevel retains its material or dye.
To customize the body, replace `textures/tile_material/<material>.png`. Selection,
persistence and reloading use Minecraft's resource-pack system. Back textures do
not replace the face atlas or affect private game data.

Concealed tiles and physical rear faces combine the material or dyed shell with
the independent back pattern. Glass keeps its translucent material below that pattern.
Front and back geometry is batched by material rather than switching render
buffers for each tile.

## Table and other resources

Dice use six deterministic 32-pixel textures at `textures/item/dice_1.png`
through `dice_6.png`, shared by a native cube item model and the central-roll
tooltip. Warm ivory edge bands and shaded recesses give the pips depth; the single
pip is larger. Opposite faces sum to seven; ones and fours use red pips.

`assets/mchjong/textures/point_sticks.png` is a 384 by 192 atlas of six 32-pixel
strips: blank ivory, ivory with eight gray dots (100), blue with one white dot
(1,000), yellow with five white dots (5,000), red with nine white dots
(10,000), and black with the same nine white dots (−10,000). Round markings,
a subtle recessed panel and molded edge highlights
follow the colored physical-stick style. `PointStickArtwork` generates the atlas
deterministically. `FurnitureMesh.stick` uses it for both printed faces and the
unmarked sides in inventory, held-item and table renders. Half-pixel UV insets
and linear filtering keep the strips separate. Resource packs can replace the
atlas, including body colors, without changing the existing denominations.

Riichi deposits use the native JSON model `assets/mchjong/models/item/riichi_stick.json`
and `assets/mchjong/textures/item/riichi_stick.png` (384 × 32). The default texture
is exactly the 1,000-point strip: blue with one white dot. The model uses ordinary
Minecraft `elements`, face UVs, parents and texture references, so packs can
replace its geometry and texture. Default bounds are `[2.4,0,7.52]` to
`[13.6,0.4,8.48]` in model pixels; the table applies the lane scale and orientation.
Interface riichi icons use the same texture.

Furniture uses fifteen original 16 by 16 pixel textures under
`assets/mchjong/textures/furniture`: eleven `wood_<family>.png` finishes and
`felt.png`, `steel.png`, `brass.png`, `edge.png`. `FurnitureArtwork` generates
broken wood grain with small knots, subdued neutral woven felt, stepped metal highlights
with sparse tooling marks and clustered wear on dark edge material
deterministically with small, discrete palettes and nearest-neighbor sampling.
[Create's framed machinery](https://github.com/Creators-of-Create/Create) and
[Farmer's Delight's crafted wooden utensils](https://github.com/vectorwing/FarmersDelight) inform
the restrained material separation and pixel scale. The patterns are original.
Wood components select the finish; neutral fabric is tinted by the existing dye
component. Resource packs can replace these paths directly.
`assets/mchjong/textures/furniture/cloth_pattern.png` is a separate, fully
transparent 256 × 256 default image. Replace it with an RGBA design to add a
single pattern across the table's whole cloth, its folded item and the immersive
table. Pattern colors are independent of the dyed fabric beneath; transparent
pixels preserve the fabric. This layer is separate from the repeating `felt.png`
material shared with stool upholstery.
The block atlas explicitly stitches the wooden particle sprite through
`assets/minecraft/atlases/blocks.json`; it does not duplicate the runtime textures.
The table and stool block models contain opaque cuboids inside their visible meshes.
These cuboids cast terrain shadows with shader packs while the block entity renderers
draw the furniture materials and moving tiles.
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
`TableGeometry.STOOL_HEIGHT`; the seated camera remains anchored to the table,
while immersive play uses the independent GUI layout.
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

## Verification

`gradlew.bat :art:check` verifies atlas coordinates and distinct faces, source
order, red fives, the blank white dragon, texture resolution and filtering,
transparent default patterns, material-backed defaults, resource-pack customization paths, language keys, original furniture
materials, source integrity and byte-for-byte reproducible generation. Client-side
unit tests cover bevel winding, tapered legs, all wood/dye combinations, mesh bounds,
white face plates, picking and exact wall/river contact, including riichi discards.

`gradlew.bat :fabric:runSmokeClient` exercises placement, seating, a private deal and a
discard in an isolated Fabric world, then reloads the resources. It checks that
filtering and resource bytes survive reload.
Screenshots and the final result are written beneath `fabric/build/smoke/evidence`.
`00-material-palette.png` shows all six tile materials, four dyes, furniture item
models and six dice faces together through the real client resource and item pipelines.
The held-item screenshots cover both main-hand preferences at 1280 by 800. Real table views
additionally exercise the world render paths.
`:neoforge:runSmokeClient` runs the same assertions and screenshots under
`neoforge/build/smoke/evidence`. Resource reload checks include all furniture and
tile-body textures, including their live pixel sampling state.

Both `:fabric:runSmokeClient -PsmokeInterface=true` and its NeoForge equivalent
also install a generated test resource pack, discover and print a new preset
through the real packet, override a built-in definition, verify replacement
riichi geometry, capture default/custom table patterns, then remove the pack
and check the reloaded preset list. Captures are under
`build/smoke/interface-evidence/resource-default` and `resource-custom`.
