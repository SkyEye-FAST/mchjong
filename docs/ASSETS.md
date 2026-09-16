# Artwork and resource contract

Both loaders use the same generated textures and models. Run
`gradlew.bat :art:generateAssets`; the resources are published to
`build/generated/assets`. Edit the generator, not the generated PNG or JSON files.

Server data is generated separately by `GenerateData` through
`gradlew.bat :art:generateData` into `build/generated/data`. Both loaders include
both directories. Recipes, tags and loot do not belong in `GenerateAssets`.
Each output is synchronized independently, removing retired resources on rebuild.

## Tile faces

The printed faces are rasterized from FluffyStuff's CC0
[riichi-mahjong-tiles](https://github.com/FluffyStuff/riichi-mahjong-tiles), pinned to
revision `26e127ba2117f45cdce5ea0225748cc0cfad3169`. Gradle resolves and caches the
source archive as a build dependency. Its SHA-256 must be
`79f892bfde6e9450b359cabe939db69a4217ff539518018967a30947c295e276`.
The generator rejects changed archive bytes before rendering.

JSVG is a build-time dependency only. Neither JSVG nor the source archive is
bundled in the playable mod. Rendering uses the source vector outlines rather
than a platform font, with antialiased edges at 256 by 384 pixels. No network
access is needed at game time. The source license is bundled under
`META-INF/licenses/riichi-mahjong-tiles-LICENSE.txt`. Source provenance and the
pinned revision are documented above.

`assets/mchjong/textures/tiles.png` is a 2048 by 2048 atlas, eight cells per row.
Cells 0-26 are 1-9 characters, circles and bamboo; 27-33 are East, South, West,
North, white, green and red dragon. Cells 34-36 are the three red fives in suit
order. The white dragon is intentionally blank. Individual face PNGs are not
shipped: the renderer consumes the two atlases, not duplicate standalone images.
The retired callout-panel PNG is also omitted; the UI draws its controls directly.
The asset test compares every atlas cell directly against the pinned source
artwork, including distinct faces, red fives, opacity and the declared order.

The bottom-right 32 by 32 pixels, beginning at (2016, 2016), are a solid white
material swatch. Keep this swatch white when replacing the atlas: the renderer
uses it for the tile body and table display colors, independently of the printed
faces. Texture metadata enables linear filtering and clamping; the shared tile
render types also retain linear filtering at draw time (vanilla entity render
types override the metadata). Face UVs stop at half-pixel insets to avoid
sampling neighboring cells. The client smoke checks the live OpenGL minification
and magnification filters after rendering, including after resource-pack reloads.

## Tile backs and customization

`assets/mchjong/textures/tile/back.png` is a separate 256 by 384 texture. Every
pixel of the default back is opaque white, a neutral tint mask for the item's
vanilla dye component. Defaults render blue. There is no logo, border or noise.
The material-colored body, opaque white face plate and shallow back shell supply
the tile's physical shape; the back shell samples the top-left pixel and applies
the same dye tint. Every material, including wood, amethyst and glass, has a white
printed surface. Glass transparency is restricted to the body, not the face plate.

No patterned resource pack is bundled. To supply a design, create a normal
resource pack for your target Minecraft version containing that same texture
path and the matching `pack_format`. Keep it opaque, preserve the 2:3 aspect ratio
and use an unmarked, uniform rim so that the back shell matches. Selection,
persistence and reloading use Minecraft's resource-pack system. Custom backs do
not replace the face atlas or affect private game data.

Concealed tiles and physical rear faces use the independent back texture. There
is no back cell in the face atlas, no `tile/37.png`, and no unused `tile/edge.png`.
Front and back geometry is batched by material rather than switching render
buffers for each tile.

## Table and other resources

Furniture uses fifteen original 64 by 64 pixel textures under
`assets/mchjong/textures/furniture`: eleven `wood_<family>.png` finishes and
`felt.png`, `steel.png`, `brass.png`, `edge.png`. `FurnitureArtwork` generates
flowing wood grain, neutral woven fabric, brushed metal and dark edge material
deterministically. It does not read, copy or composite Minecraft textures. Wood
components select the finish; neutral fabric is tinted by the existing dye
component. Resource packs can replace these paths directly.
The block atlas explicitly stitches the wooden particle sprite through
`assets/minecraft/atlases/blocks.json`; it does not duplicate the runtime textures.
Tile inventory icons use front lighting so their white faces remain readable.

`FurnitureShape` supplies small textured boxes, tapered legs and clipped-corner
bevels with outward normals and consistent pixel density. `FurnitureMesh` shares
the resulting geometry between blocks and items. Ordinary tables have framed
playing surfaces, beveled rails, tapered legs and stretchers; automatic tables
have a metal pedestal, a low plinth, a brass band and ventilation slots. Stools
have joined frames and padded, piped cushions. The dark wooden case has a fitted
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
uniform default backs, absence of bundled packs, language keys, original furniture
materials, source integrity and byte-for-byte reproducible generation. Client-side
unit tests cover bevel winding, tapered legs, all wood/dye combinations, mesh bounds,
white face plates, picking and exact wall/river contact, including riichi discards.

`gradlew.bat runSmokeClient` exercises placement, seating, a private deal and a
discard in an isolated Fabric world, then reloads the resources. It checks that
the retired pack is absent and that filtering and resource bytes survive reload.
Screenshots and the final result are written beneath `build/smoke/evidence`.
`00-furniture-details.png` and `00-material-gallery.png` are development-only
contact sheets rendered by the real item renderer. They cover the furniture,
eleven wood finishes and six white-face tile materials. The held/dropped-item
screenshots and real table views additionally exercise the world render paths.
`:neoforge:runSmokeClient` runs the same assertions and contact sheets under
`neoforge/build/smoke/evidence`. Resource reload checks include all furniture textures.
