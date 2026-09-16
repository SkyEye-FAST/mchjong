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
order. The white dragon is intentionally blank. The individual images are
`assets/mchjong/textures/tile/0.png` through `36.png`.

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
pixel of the default back is the same opaque teal, `#2b6b75`. There is no logo,
border or noise. The raised ivory body and shallow back shell supply the tile's
physical shape; the back shell samples the texture's top-left pixel for its color.

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

Cloth, wood, brass and cushion textures are original
MCjhong artwork. Cloth and wood use a deterministic hash for their pixel grain.
`models/block/mahjong_table.json` contains fourteen cuboids for the complete 3 by 3
table, centered on its center block; the felt top is at y = 15/16. Its block ID is
`mchjong:mahjong_table`. Block occupancy belongs to the world implementation, not
the art generator.

`models/block/mahjong_stool.json` uses eight cuboids for a single-block stool with
its cushion top at y = 10/16. Its ID is `mchjong:mahjong_stool`. Tile meshes retain
three shallow cuboids instead of creating a separate entity for each tile.

The four source language files live in `common/src/main/resources`. Their engine
action and ruleset keys match the corresponding names in lowercase. Upstream
yaku names are lowercased only when constructing translation keys.

Yet Another Bingo is a visual reference, not an asset dependency. No artwork or
code from that project or riichi_advanced is bundled.

## Verification

`gradlew.bat :art:check` verifies atlas coordinates and distinct faces, source
order, red fives, the blank white dragon, texture resolution and filtering,
uniform default backs, absence of bundled packs, language keys, model
bounds, source integrity and byte-for-byte reproducible generation.

`gradlew.bat runSmokeClient` exercises placement, seating, a private deal and a
discard in an isolated Fabric world, then reloads the resources. It checks that
the retired pack is absent and that filtering and resource bytes survive reload.
Screenshots and the final result are written beneath
`build/smoke/evidence`.
