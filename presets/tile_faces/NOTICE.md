# Native tile-face artwork

The individual tile images and their metadata in `presets/tile_faces` are build-time inputs.
MChjong composes all 45 faces into separate Kansai and Kanto texture atlases
inside the mod. Players select them in the mahjong box; no resource-pack
installation, extraction or download occurs at runtime.

## Kanto

Source: `kanto/tiles/` (45 individual transparent tile engravings).
Built-in Kanto tile-face preset supporting standard 136-tile hands and flower tiles.

## Kansai

Source: `kansai/tiles/` (45 individual transparent tile engravings).
The Kansai artwork uses the open riichi mahjong tile graphics created by lietxia:

- https://github.com/lietxia/mahjong_graphic

Released under the M+ Fonts License (free for commercial and noncommercial use).

## Flower numbering

Kansai uses spring, summer, autumn, winter, plum, orchid, bamboo and
chrysanthemum (1q-8q).
Kanto keeps its four seasons followed by 福禄寿貴 (5q-8q), with matching
localized names. Both designs use their own supplied flower artwork.

The individual tile images, source metadata and this notice are build inputs.
