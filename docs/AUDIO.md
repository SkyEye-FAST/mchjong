# Audio and voice presets

Table settings → Audio controls effect volume, countdown warnings, voice playback
and voice volume. Effects are positional and also respect Minecraft's Blocks
volume. Countdown warnings and recorded voices respect Master volume. Setting a
volume to zero mutes that category. The preview button plays one effect and the
selected `ron` recording. Minecraft's accessibility narration has independent
controls.

Choose a voice preset in room Settings → Personal → Personal presets. The
selection and voice volume belong to the local player. **Selected preset** plays
recordings from that preset; **Off** silences recordings while retaining effects.
Missing recordings stay silent. The default voice preset uses the selected
Minecraft resource pack's `mchjong:voice.*` events.

## ZIP voice presets

Put client voice ZIPs in `config/mchjong/presets/voices/`. Put server voice ZIPs
in `config/mchjong/server-presets/voices/`. The server sends its presets to all
joining players. A client-only ZIP affects that player's playback. Each ZIP
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

Action recording filenames are `riichi`, `chi`, `pon`, `kan`, `nuki`, `ron`,
`tsumo`, `draw_end` and `match_end`, followed by `.ogg`.
Settlement recordings use `yaku.<name>.ogg`, where `<name>` is the suffix of the
corresponding `yaku.mchjong.<name>` translation key. For example, use
`voices/yaku.riichi.ogg`, `voices/yaku.menzen_tsumo.ogg` and
`voices/yaku.suuankou_tanki.ogg`. Declaration and settlement recordings are
independent. Dora uses `yaku.dora.ogg`, `yaku.dora_2.ogg` through
`yaku.dora_12.ogg`, and `yaku.dora_many.ogg` above twelve; a missing counted
recording falls back to `yaku.dora.ogg` within the same preset.

Hand grades use `score.mangan`, `score.haneman`, `score.baiman`,
`score.sanbaiman`, `score.kazoe_yakuman`, `score.yakuman` and
`score.yakuman_2` through `score.yakuman_6`, followed by `.ogg`.
`assets/mchjong/sounds.json` is the complete recording-ID and subtitle catalog.
A preset needs at least one
recording. Use Ogg Vorbis, mono or stereo, at 8–96 kHz. **Each recording must be
at most 8 seconds long.** Both the archive reader and the client decoder enforce
the limit. Restart the server after changing server ZIPs; reload client
resources after changing local ZIPs. The sound engine reads these files from
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
