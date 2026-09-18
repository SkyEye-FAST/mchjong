# Native tile-face artwork

The two source PNG atlases and their supplied metadata are build-time inputs.
MCjhong composes all 45 faces into separate Kansai and Kanto texture atlases
inside the mod. Players select them in the mahjong box; no resource-pack
installation, extraction or download occurs at runtime.

## Kanto

Source: `kanto_mizuno/atlas/mizuno.png` (1500 x 1000).
The supplied metadata names Mizuno Maruichi (水野丸一) as the copyright holder,
credits 棠坣TangStudio as contributor, and marks the artwork `unauthorized`.
No permission or public-domain status is inferred from its inclusion here.
The repository's code license does not relicense this artwork. Redistribution
permission remains unverified. The original metadata is preserved alongside
the source and in the built mod.

## Kansai

Source: `kansai_fluffystuff/atlas/default.png` (1500 x 1000).
The supplied metadata identifies the artwork as public domain and credits
FluffyStuff and lietxia, referencing:

- https://github.com/FluffyStuff/riichi-mahjong-tiles
- https://github.com/lietxia/mahjong_graphic

## Flower numbering

Kansai uses spring, summer, autumn, winter, plum, orchid, bamboo and
chrysanthemum (1q-8q); its last two source cells are reordered accordingly.
Kanto keeps its four seasons followed by 福禄寿貴 (5q-8q), with matching
localized names. Both designs use their own supplied flower artwork.

Only the two PNG atlases, source metadata and this notice are build inputs.
Supplied ZIPs, WebP copies, individual crops and resource-pack exports are
reference copies, not runtime resources or alternative implementation paths.
