# Native tile-face artwork

The individual tile images and their metadata in `presets/tile_faces` are build-time inputs.
MChjong composes all 45 faces into separate Kansai, Kanto, Sichuan, Hong Kong,
Taiwan and Fujian texture atlases
inside the mod. Players select them in the mahjong box; no resource-pack
installation, extraction or download occurs at runtime.

## Kanto

Source: `kanto/tiles/` (45 individual transparent tile engravings).
Built-in Kanto tile-face preset supporting standard 136-tile hands and flower tiles.
The existing engravings were redrawn with AI-assisted contour restoration,
flat ink colors and transparent negative space. The flower motifs and
distinctive red-five marks are retained.

## Kansai

Source: `kansai/tiles/` (45 individual transparent tile engravings).
The Kansai artwork uses the open riichi mahjong tile graphics created by lietxia:

- https://github.com/lietxia/mahjong_graphic

Released under the M+ Fonts License (free for commercial and noncommercial use).

## Hong Kong

Source: `hong_kong/tiles/` (45 individual transparent tile engravings).
The suit, wind, dragon and red five engravings are derived from the open mahjong tiles created by samoheen:

- https://github.com/samoheen/mahjong-tiles

Released under the Creative Commons Zero 1.0 Universal (CC0 1.0) Public Domain Dedication.

The Hong Kong flower tiles (1q-8q) are derived from the I.Mahjong-HK font created by Ichiro Naiki:

- https://github.com/SyaoranHinata/I.Mahjong

Released under the M+ Fonts License.

## Sichuan

Source: `sichuan/tiles/` (45 individual transparent tile engravings).
Built-in Sichuan tile-face preset supporting standard hands, regional flower tiles
and framed white dragons. The engravings were redrawn with geometric construction,
contour reconstruction, clean contours, flat ink colors and transparent negative space.

## Taiwan

Source: `taiwan/tiles/` (45 individual transparent tile engravings).
Built-in Taiwan tile-face preset supporting standard hands, regional flower tiles
and framed white dragons. The engravings were redrawn with clean contours, flat
regional ink colors and transparent negative space.

## Fujian

Source: `fujian/tiles/` (45 individual transparent tile engravings).
Built-in Fujian tile-face preset supporting standard hands, regional flower tiles
and framed white dragons. The engravings were redrawn with geometric construction,
contour reconstruction, clean contours, flat ink colors and transparent negative space.

## Flower numbering

Kansai uses spring, summer, autumn, winter, plum, orchid, bamboo and
chrysanthemum (1q-8q).
Kanto keeps its four seasons followed by 福禄寿貴 (5q-8q), with matching
localized names.
Sichuan, Hong Kong, Taiwan and Fujian use the four seasons (春, 夏, 秋, 冬) followed by the four gentlemen
(梅, 蘭, 菊, 竹, 5q-8q), with matching localized names.
All designs use their own supplied flower artwork.

The individual tile images, source metadata and this notice are build inputs.
