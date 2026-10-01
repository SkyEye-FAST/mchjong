# Audio and voice presets

Personal settings → Audio controls effect volume, countdown warnings, voice playback
and voice volume. Physical tile, dice and mechanism effects are positional at the table and also
respect Minecraft's Blocks volume. UI accents, settlement reveals, countdown
warnings and recorded voices respect Master volume. Setting a
volume to zero mutes that category. The preview button plays one effect and the
selected `ron` recording. Minecraft's accessibility narration has independent
controls.

Choose a voice preset in MChjong's mod-list Config → Personal presets. The
selection belongs to the player, while playback volume belongs to each listener.
**Selected preset** plays declarations and winning-hand recordings with the
speaker's selected server voice. Players hear their own local voice selection on
their client. Nearby spectators also hear players' declarations. **Off** silences
recordings while retaining effects.
Missing recordings stay silent. The default voice preset uses the selected
Minecraft resource pack's `mchjong:voice.*` events.

## Original effects

`MahjongSounds` registers the `mchjong:table.<name>` events below. `sounds.json`
references project-owned mono Ogg Vorbis files at `sounds/table/<name>.ogg`.
Riichi, MCR and Sichuan share the physical sound vocabulary. Repeated/cosmetic
snapshots stay silent; revealing a concealed kong is not another kong sound.
Native Minecraft widgets retain their normal click feedback.

| Event suffix | Meaning | Source |
| --- | --- | --- |
| `tile_draw` | A tile leaves the wall for a hand | Table / Blocks |
| `tile_discard` | A discard lands on the felt | Table / Blocks |
| `tile_call` | A called group, flower/north or dealing packet lands | Table / Blocks |
| `tile_kong` | A new or supplemented kong lands | Table / Blocks |
| `riichi_stick` | The declaration stick lands | Table / Blocks |
| `dice` | Dice roll and settle | Table / Blocks |
| `table_mechanical` | Automatic-table opening/build motion | Table / Blocks |
| `score_reveal` | Winner points or MCR/Sichuan receipt appears | UI / Master |
| `grade_reveal` | Applicable Riichi grade appears with its voice | UI / Master |
| `ui_accent` | Local turn attention | UI / Master |
| `countdown` | Last-five-seconds warning | UI / Master |
| `ron`, `tsumo` | Physical win declaration | Table / Blocks |
| `draw_end` | Draw announcement | Table / Blocks |
| `match_end` | Final standings | UI / Master |

`art/audio/synthesize.py` is the source for the original effects. It synthesizes
seeded impact noise and damped resonances, without external samples. Regenerate
with Python 3 and FFmpeg's `libvorbis`; normal Gradle builds consume the checked-in
Ogg files and need neither tool. Files are 44.1 kHz mono, 65–650 ms, with bounded
peaks (at most 0.38 before encoding). Resin-like tile impacts are hard and brief,
mechanics emphasize low frequencies, and reveals use restrained bright tones.
Resource packs may replace these event files; voice presets remain independent.

## Settlement playback

Winning settlements reveal one yaku at a time, playing its recording and waiting
for playback to finish before continuing. The receipt and narration share a fixed
yaku order. Ordinary yaku show their individual han after open-hand reductions;
natural yakuman show only their applicable yakuman rows. Bonus rows follow in
this order: dora, red dora, extracted-north dora, ura dora. Each row uses its own
counted recording, with thirteen or more sharing the same recording. Each
winner follows explicit `YAKU → POINTS → LIMIT → NEXT_WINNER` presentation events.
After the last yaku, at least 300 ms of silence precedes points and `score_reveal`.
Points hold alone for 750 ms before the applicable grade recording and
`grade_reveal`. Ordinary hands omit LIMIT entirely and finish after the points
hold; grade names are not played as a ladder. Multiple winners complete these
stages independently in order, ending with `COMPLETE`. Missing
recordings and muted voices retain a short visual cadence rather than blocking
the receipt. Sound volume and animation settings remain independent.

Resizing and cosmetic table updates retain progress. Selecting another winner,
changing result pages, hiding or closing the receipt completes the local readout
and stops pending recordings. The first Skip wait during a readout reveals the
full receipt; pressing it again sends the server's stage-skip action. Once all
seated players finish, the server leaves a ten-second reading period. A bounded
fallback prevents unavailable clients from holding the table indefinitely.
The match-end recording belongs to final standings, after the hand readout.

The mod does not bundle character recordings or invoke device text-to-speech.
The default events remain silent until a resource pack supplies recordings.

## ZIP voice presets

Put client voice ZIPs in `config/mchjong/presets/voices/`. Put server voice ZIPs
in `config/mchjong/server-presets/voices/`. The server sends its presets to all
joining players and refreshes online players when the archives change. Selecting
a server voice shares that choice so other players hear the speaker's recordings.
A client-only ZIP affects that player's playback. The selector separates Server
and Local presets, marks built-in entries in Server, and shows names and IDs in tooltips. Each ZIP
contains only voice presets and may contain several of them:

```text
my_pack/announcer/preset.toml
my_pack/announcer/voices/riichi.ogg
my_pack/announcer/voices/ron.ogg
```

The folder pair defines the ID `my_pack:announcer`. The manifest supplies a
display name:

```toml
name = "Announcer"
```

Action recording filenames are `riichi`, `double_riichi`, `chi`, `pon`, `kan`, `nuki`, `ron`,
`tsumo`, `draw_end` and `match_end`, followed by `.ogg`.
Settlement recordings use `yaku.<name>.ogg`, where `<name>` is the suffix of the
corresponding `yaku.mchjong.<name>` translation key. For example, use
`voices/yaku.riichi.ogg`, `voices/yaku.menzen_tsumo.ogg` and
`voices/yaku.suuankou_tanki.ogg`. Declaration and settlement recordings are
independent, including `double_riichi.ogg` and `yaku.double_riichi.ogg`.
Seat-wind recordings use `yaku.seat_wind_east.ogg` through
`yaku.seat_wind_north.ogg`; round winds use `yaku.round_wind_east.ogg` through
`yaku.round_wind_north.ogg`.

All four bonus rows share the counted recordings: `yaku.dora.ogg` is one tile,
`yaku.dora_2.ogg` through `yaku.dora_12.ogg` select that many tiles, and
`yaku.dora_many.ogg` selects thirteen or more. Each row chooses its recording
independently from its own count.

Hand grades use `score.mangan`, `score.haneman`, `score.baiman`,
`score.sanbaiman`, `score.kazoe_yakuman`, `score.yakuman` and
`score.yakuman_2` through `score.yakuman_6`, followed by `.ogg`.
`assets/mchjong/sounds.json` is the complete recording-ID and subtitle catalog.
A preset needs at least one
recording. Use Ogg Vorbis, mono or stereo, at 8–96 kHz. **Each recording must be
at most 8 seconds long.** Both the archive reader and the client decoder enforce
the limit. Local and server ZIP changes load automatically after two stable
one-second directory checks. The sound engine reads these files from
the ZIP data in memory.

## Default resource-pack voices

The default preset can use recordings supplied by a normal Minecraft resource
pack. Add `assets/mchjong/sounds/my_voice/ron.ogg` and this definition in
`assets/mchjong/sounds.json`:

```json
{
  "voice.ron": {
    "replace": true,
    "subtitle": "action.mchjong.ron",
    "sounds": [{"name": "mchjong:my_voice/ron", "stream": false}]
  }
}
```

Select the pack in Minecraft and choose the default voice preset. Use recordings
you own or have permission to redistribute.
