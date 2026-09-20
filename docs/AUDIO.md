# Audio and custom voices

Both loaders expose the same events. Table settings → Audio controls effect volume,
countdown warnings, voice source, and resource-pack voice volume. Effects are
positional and also respect Minecraft's Blocks volume. Countdown warnings and
recorded voices respect Master volume. Setting an individual volume to zero mutes it.

**Resource pack** is the default and plays only recordings supplied by the selected
resource pack, with independent volume. Missing recordings stay silent. **Off**
disables recorded calls without disabling other effects. The preview button tests
the selected source. Playback follows new table events after joining or reconnecting.
Minecraft's accessibility narration retains its independent controls.

## Resource-pack contract

Create a normal Minecraft resource pack for the selected game version. For the
current build profile its `pack.mcmeta` is:

```json
{"pack":{"pack_format":34,"description":"My MChjong voices"}}
```

Add a recording at `assets/mchjong/sounds/my_voice/ron.ogg`, and place this in
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

Select the pack in Minecraft and select **Resource pack** in the audio settings.
Only use recordings you own or have permission to redistribute. Selection and
reload use Minecraft's normal client-side sound-resource handling.

Voice suffixes: `riichi`, `chi`, `pon`, `kan`, `nuki`, `ron`, `tsumo`, `draw_end`,
`match_end`. Prefix each with `voice.` in `sounds.json`.

Effect suffixes: `wall`, `deal`, `draw`, `tsumogiri`, `tedashi`, `riichi`, `chi`,
`pon`, `kan`, `nuki`, `ron`, `tsumo`, `draw_end`, `match_end`, `countdown`, `turn`.
Prefix each with `table.` to replace the corresponding effect. The distributed
effects reference Minecraft's own sound events rather than redistributing audio
from another mahjong game. Mono Ogg Vorbis is recommended for positional effects;
use Minecraft's standard `sounds.json` format for variations, volume and pitch.
