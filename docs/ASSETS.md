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
edges. Both built-in designs are ordinary mod resources selected in the mahjong
box and require no runtime pack installation or network access.

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
Custom face presets are ZIP archives in `config/mchjong/presets/faces/` on a client
or `config/mchjong/server-presets/faces/` on a server. Inside a ZIP,
`<namespace>/<name>/preset.toml` defines the persistent
preset ID `<namespace>:<name>`. The manifest can supply a display name:

```toml
name = "Ink"
```

Place 45 transparent PNGs under
`<namespace>/<name>/tiles/`. Their filenames are
`1m`–`9m`, `1p`–`9p`, `1s`–`9s`, `1z`–`7z`, `0m`, `0p`, `0s`, and `1q`–`8q`,
each followed by `.png`. They are fitted to the same face area as the supplied
individual source images. The white dragon image is included; its printed face
remains blank. The client assembles the images into render textures at load time.
A ZIP can contain several `<namespace>/<name>/` directories.
Each ZIP belongs to its category directory and contains only face presets.
Reload client resources after changing local ZIPs.

Printing stores only the preset ID on tiles and synchronizes it with table
appearance. To offer face presets to every player, place ZIP archives in the server's
`config/mchjong/server-presets/faces/` directory and restart the server. The mod sends the
configured images to connecting players and adds them to their mahjong box
selectors. ZIPs in a client's `config/mchjong/presets/faces/` directory add presets to
that client's selector; other players without those images see default Kansai faces.
The server validates the physical set,
carrier, menu and dye transaction. Removing an archive keeps stored IDs intact and
removes its selector entries; unavailable designs display Kansai faces until
their resources are installed.
The asset test compares every atlas cell directly against its generated design,
including all 45 distinct faces, red fives, flowers, opacity and the declared order.

The bottom-right 32 by 32 pixels, beginning at (2016, 4064), are a solid white
material swatch. Keep this swatch white when replacing the atlas: the renderer
uses it for opaque face plates and table display colors, independently of the printed
faces. Built-in GUI faces retain their independent linearly filtered atlas. Built-in
world and item engravings use `TileFaceTexture`: it composites the engraving over
the white face plate before generating mipmaps with Minecraft's mip generator.
Configured presets are assembled from individual images with the same white
backing and linear filtering. The native atlas's five mip levels retain exact
cell and white-swatch boundaries. Face UVs stop at half-pixel insets. The client
smoke checks built-in mip dimensions and live world/GUI filters after resource reloads.
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
all eleven woods, bone, quartz, calcite, glass and amethyst have no separate default back
color. `assets/mchjong/textures/tile/back.png` is the default 256 by 384 transparent
pattern layer applied to every rear face, including undyed and glass tiles and
concealed face covers. Its pixels are fully transparent. The tile's
`BASE_COLOR` component selects one of Minecraft's sixteen dye colors without
changing the material-colored body or opaque white printed surface.

The pattern preserves its own colors and alpha independently of dye. Dyeing a glass tile tints its
translucent glass material instead, matching the stained-glass model while its
rear face continues to use `textures/tile_material/glass.png`. Undyed glass keeps
the neutral glass tint. Resource packs can replace the material textures.
Dyed opaque shells use the solid white
`textures/tile/plain.png` tinted by the dye, beneath the pattern.

The body uses original neutral relief textures under `textures/tile_material/`:
`wood.png`, `bone.png`, `quartz.png`, `calcite.png`, `glass.png`, and `amethyst.png`.
`TileMaterialArtwork` generates original 16 by 16, limited-palette grain, pores,
chalk veins, stepped glass reflections and crystal facets
without borrowing game textures; the material component supplies their tint.
Oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, bamboo, crimson
and warped tiles share the wood relief with individual material colors. Cutting
each wood's planks yields its matching blank tiles. Blank fronts use the same
material texture and color as that material's undyed back.
World, item and immersive body surfaces use nearest-neighbor sampling to retain
the pixel clusters independently of the smoothly filtered printed faces.
The body and the beveled printed and dyed shells meet edge-to-edge without
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

Use the item-presentation profile in [Verification](VERIFICATION.md#focused-interaction-checks)
for focused grip checks on Fabric and Forge.
The shared item capture renders both supplies with each main-hand preference in
the native first-person view at 1280 by 800, with an empty-hand reference for each
side, then repeats the right-hand comparisons at 640 by 480. Inspect the resulting
screenshots for native-like hand placement, grip
contact, readable printed faces and clearance above the hotbar.

Back presets are ZIP archives in `config/mchjong/presets/backs/` on a client or
`config/mchjong/server-presets/backs/` on a server. Each
`<namespace>/<name>/preset.toml` names one preset, and
`<namespace>/<name>/back.png` supplies its 256 by 384 transparent pattern.
Several back presets can share one ZIP, which contains only back presets.
The mahjong box's back selector applies
the chosen preset to its stored tiles independently of dye. Server presets are
sent to joining players; an unavailable ID uses the transparent default pattern.
The built-in back choices are Creeper and Mojang banner marks in a single ink
color. Their artwork is generated with the mod and is available to every player
without a preset ZIP.
On face-down wall tiles, the image's top points toward the table center.
The pattern covers the flat cap while the bevel retains its material or dye.
To customize the body, replace `textures/tile_material/<material texture>.png`.
All eleven woods use `wood.png`.

Riichi stick presets are ZIP archives in `config/mchjong/presets/sticks/` on a
client or `config/mchjong/server-presets/sticks/` on a server. Each preset has
`<namespace>/<name>/preset.toml` and a 384 by 32 transparent
`<namespace>/<name>/stick.png`. A ZIP can contain several stick presets and no
other preset category. The manifest defines a display name and cuboid dimensions
in sixteenths of a block:

```toml
name = "Lacquer"
length = 12
width = 1
height = 0.5
```

Length must be 8–16, width 0.25–2 and height 0.125–1. Length must be at least
five times both width and height, keeping the model a long bar. Select a stick
in Settings > Personal > Personal presets. The choice is stored in the client's
TOML settings. Server presets chosen by a player are shown to everyone; a
client-only choice appears as the default stick to other players. Unavailable
artwork also uses the default stick.
The built-in Bamboo, Lightning Rod and End Rod choices use Minecraft's block
models and textures, laid horizontally and scaled to the deposit lanes. They are
shown to every player when selected.

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
`assets/mchjong/textures/gui/stick_icons.png` contains fixed riichi and honba
HUD icons copied from the default blue and ivory strips at build time. Changing
point-stick textures or selecting a stick preset does not alter those icons.

Furniture uses fifteen original 16 by 16 pixel textures under
`assets/mchjong/textures/furniture`: eleven `wood_<family>.png` finishes and
`felt.png`, `steel.png`, `brass.png`, `edge.png`. `FurnitureArtwork` generates
broken wood grain with small knots, subdued neutral woven felt, stepped metal highlights
with sparse tooling marks and clustered wear on dark edge material
deterministically with small, discrete palettes and nearest-neighbor sampling.
The patterns are original.
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
Tile inventory icons show a three-dimensional angled view with front lighting.

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
printed face plates, material-colored blank fronts, picking and exact wall/river contact, including riichi discards.

`gradlew.bat :fabric:runSmokeClient` exercises placement, seating, a private deal and a
discard in an isolated Fabric world, then reloads the resources. It checks that
filtering and resource bytes survive reload.
Screenshots and the final result are written beneath `fabric/build/smoke/evidence`.
`00-material-palette.png` shows the sixteen tile materials, four dyes, furniture item
models and six dice faces together through the real client resource and item pipelines.
For a focused inventory check on both loaders, run `:fabric:runSmokeClient -PsmokePalette=true`
and `:forge:runSmokeClient -PsmokePalette=true`. Their screenshots and pass markers
are under each loader's `build/smoke/palette-evidence`.
The held-item screenshots cover both main-hand preferences at 1280 by 800. Real table views
additionally exercise the world render paths.
`:forge:runSmokeClient` runs the same assertions and screenshots under
`forge/build/smoke/evidence`. Resource reload checks include all furniture and
tile-body textures, including their live pixel sampling state.

Both `:fabric:runSmokeClient -PsmokeInterface=true` and its Forge equivalent
also load local and server face preset ZIPs, print a local preset through the real
packet, override a built-in definition with a test resource pack, verify replacement
riichi geometry, capture default/custom table patterns, then remove the local ZIP
and check the reloaded preset list. Captures are under
`build/smoke/interface-evidence/resource-default` and `resource-custom`.
