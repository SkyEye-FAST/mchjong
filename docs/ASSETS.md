# Custom content

Use a Minecraft resource pack to replace existing artwork or sound. Use preset
ZIPs to add selectable designs and recordings. Custom scoring awards use server
data packs and are independent of appearance presets.

## Resource packs

To replace a built-in tile face, place a PNG in your resource pack at
`assets/mchjong/textures/tile_faces/<preset>/tiles/<key>.png`. For example,
`assets/mchjong/textures/tile_faces/kansai/tiles/1m.png` replaces the one of
characters in the Kansai face preset. The built-in preset IDs are `kansai`,
`kanto`, `sichuan`, `hong_kong`, `taiwan` and `fujian`. Source images are available
under `presets/tile_faces/`; consult the [artwork notice](../presets/tile_faces/NOTICE.md)
before redistributing any of them.

Other replacement paths under `assets/mchjong/` include:

| Resource | Path |
| --- | --- |
| Default tile-back pattern | `textures/tile/back.png` |
| Tile materials | `textures/tile_material/` |
| Cloth pattern | `textures/furniture/cloth_pattern.png` |
| Mahjong box model texture | `textures/furniture/mahjong_box.png` |
| Physical point sticks | `textures/point_stick/<points>.png` |
| Physical scoring cards | `textures/scoring_card/<points>.png` |
| Default riichi-stick model and texture | `models/item/riichi_stick.json`, `textures/item/riichi_stick.png` |
| Effect recordings and voice events | `sounds/table/`, `sounds.json` |

For replacement images and models, browse the
[generated resources](../art/generated/assets/assets/mchjong). Sound definitions
and other source resources are under
[common resources](../common/src/main/resources/assets/mchjong). Your resource pack
uses the same `assets/mchjong/` paths, regardless of where the files are maintained
in this repository.

### Patchouli handbook

Override handbook categories, entries and templates under
`assets/mchjong/patchouli_books/guide/en_us/`. Page text uses translation keys
from the four shared language files. The book definition is at
`data/mchjong/patchouli_books/guide/book.json`; its `use_resource_pack` setting
enables these resource-pack pages. The generated cover is
`assets/mchjong/textures/item/mahjong_manual.png`.

Use the checked-in entries as authoring examples. Each award in the award
entries has one text page whose `anchor` is the award's identifier; preserve
those anchors so rule explanations continue to reach the matching award. A
`mchjong:example` tile diagram occupies a spread: two consecutive pages, starting
on an even page index, with matching `text`, `tiles` and `face` fields. Tile
diagrams use the playing atlas and the selected regional face preset.

A `mchjong:table` page holds one translation key in its `text` field. Each line
of the translated text is a table row, `|` separates cells, `$(br)` breaks a
line inside a cell, the first row is the header, a first line without `|` is a
caption, and a blank line starts another table on the same page. Keep the tables
within one page, and place a table page directly after the text pages of the
section it belongs to, so rule explanations include it.

## Preset ZIPs

Client presets go in `config/mchjong/presets/<category>/`; shared server presets
go in `config/mchjong/server-presets/<category>/`. Categories are `faces`, `backs`,
`sticks` and `voices`. The server distributes its presets to joining players and
updates online players when the archives change. Local presets are not uploaded
to other players. Server definitions take precedence over matching local IDs.

Each ZIP contains presets from a single category, without unrelated files.
Inside the archive, each `<namespace>/<name>/` directory defines one persistent
preset ID. For example, a tile-back preset with the ID `my_pack:ink` contains
these two files:

```text
my_pack/ink/preset.toml
my_pack/ink/back.png
```

Put a transparent 256 x 384 pixel pattern in `back.png` and set the display name
in `preset.toml`:

```toml
name = "Ink"
```

Save the archive in `config/mchjong/presets/backs/` for local use, or in the
server's `config/mchjong/server-presets/backs/` directory to share it. Once the
archive loads, open a mahjong box containing a complete set, put Mahjong Dye in
the dye slot, choose Ink in the back-preset selector, and apply the selection.
Use the same directory structure for the other categories, with the files
listed below.

| Category | Files within each preset directory |
| --- | --- |
| Tile faces (`faces`) | `tiles/<key>.png`: 45 transparent PNG images, each at most 2048 x 2048 pixels |
| Tile backs (`backs`) | `back.png`: a transparent pattern measuring 256 x 384 pixels |
| Riichi sticks (`sticks`) | `stick.png`: a transparent image measuring 384 x 32 pixels, plus model dimensions in the manifest |
| Voice recordings (`voices`) | `voices/<recording>.ogg`: one or more supported recordings |

### Tile faces

Tile-face keys are `1m`-`9m`, `1p`-`9p`, `1s`-`9s`, `1z`-`7z`, `0m`, `0p`, `0s`
and `1q`-`8q`. The suits are characters, circles and bamboo. The honor-tile order
is East Wind, South Wind, West Wind, North Wind, White Dragon, Green Dragon and
Red Dragon. Zero denotes a red five tile. Flower-tile images use the eight q keys.
Supply every required image, then select the tile-face preset in the mahjong box
and apply it with Mahjong Dye. Face and back archives reload automatically after
changes, so an updated design becomes available without recreating the preset ID.

### Riichi sticks

Riichi-stick preset manifests also require `length`, `width` and `height`,
measured in sixteenths of a block. Their ranges are 8-16, 0.25-2 and 0.125-1
respectively; length must be at least five times both width and height.

```toml
name = "Lacquer"
length = 12
width = 1
height = 0.5
```

Place the ZIP in the `sticks` category directory and choose the resulting
riichi-stick preset in Personal Presets. The dimensions control its model, while
`stick.png` supplies its appearance.

### Voice recordings

Action-recording names include `riichi`, `double_riichi`, `chi`, `pon`, `kan`, `nuki`,
`ron`, `tsumo`, `draw_end` and `match_end`. Settlement recordings use names such
as `yaku.riichi` and `score.mangan`. Counted bonus recordings use `yaku.dora`,
`yaku.dora_2` through `yaku.dora_12`, and `yaku.dora_many` for thirteen or more dora tiles.
Append `.ogg` to each recording name: for example, use `voices/ron.ogg` for the
declaration and `voices/yaku.riichi.ogg` for the scored yaku. The complete list is
in [sounds.json](../common/src/main/resources/assets/mchjong/sounds.json); remove
the `voice.` prefix from a sound-event key to obtain its recording name.

Use Ogg Vorbis, mono or stereo, at 8-96 kHz, with a maximum duration of eight
seconds per recording. A voice preset needs at least one recording; missing
recordings remain silent. Put the ZIP in the `voices` category directory and
select it in Personal Presets. Distribute only recordings you have permission
to share.

## Custom scoring awards

Riichi mahjong and Taiwanese mahjong support custom scoring awards. Put each set
at `data/<namespace>/mchjong/yaku_sets/<name>.json` in a server data pack, then
run `/reload`. A room host with custom-rule permission can select its awards in
the rule editor and apply the draft. This feature is not available for MCR or
Sichuan mahjong.

Use the [bundled ancient-yaku definition](../engine/src/main/resources/yaku_sets/mahjong_soul_ancient.json)
as an authoring example. Each definition identifies the variant, the award's
conditions and value, and its name and description in all four supported locales:
`en_us`, `ja_jp`, `zh_cn` and `zh_tw`. Riichi mahjong awards grant han or
natural-yakuman units. For Taiwanese mahjong, use `"variant": "taiwan"` and a
reward such as `{"type": "tai", "value": 3}`. Follow [TERMS.md](../TERMS.md)
when writing the localized names and descriptions.

The [authoring codec](../engine/src/main/java/top/skyeyefast/mchjong/engine/YakuSetCodec.java)
and [condition types](../engine/src/main/java/top/skyeyefast/mchjong/engine/YakuCondition.java)
define the accepted fields and limits. Conditions examine an existing winning
interpretation; they cannot add a new winning shape or game action. Reloading
changes the selectable catalog, but tables already using an award retain the
definition they selected, including in saves and native replays. Because Tenhou
JSON cannot represent modified award selections, those Riichi mahjong matches
use native replay export.
