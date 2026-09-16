# Audio and custom voices

Both loaders expose the same events. Table settings → Audio controls effect volume,
countdown warnings, voice source, and resource-pack voice volume. Effects are
positional and also respect Minecraft's Blocks volume. Countdown warnings and
recorded voices respect Master volume. Setting an individual volume to zero mutes it.

**System speech** is the default: the Minecraft-bundled text-to-speech library
reads translated calls using its platform speech backend. Windows uses SAPI;
Linux uses the installed Flite backend, whose default voice is English. Voice,
language availability and volume controls therefore depend on the platform.
MCjhong owns a separate narrator and does not enable, disable, interrupt or change
Minecraft's accessibility narration. Availability and pronunciation depend on the
installed backend. Setting Minecraft's Master volume to zero also suppresses new
system calls; use the operating system for other system-speech volume changes.
This is not a bundled set of actor recordings.

**Resource pack** plays recordings supplied by the selected resource pack, with
independent volume. There is no automatic substitution with system speech when a
recording is absent. **Off** disables calls. The preview button tests the selected
source. Snapshots received on joining/reconnecting do not replay historical calls.

## Resource-pack contract

Create a normal Minecraft resource pack for the selected game version. For the
current build profile its `pack.mcmeta` is:

```json
{"pack":{"pack_format":34,"description":"My MCjhong voices"}}
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
Only use recordings you own or have permission to redistribute. Resource reload
uses Minecraft's normal sound-resource handling; no executable plugins, network
speech service, download step or server-side asset upload is needed.

Voice suffixes: `riichi`, `chi`, `pon`, `kan`, `nuki`, `ron`, `tsumo`, `draw_end`,
`match_end`. Prefix each with `voice.` in `sounds.json`.

Effect suffixes: `wall`, `deal`, `draw`, `tsumogiri`, `tedashi`, `riichi`, `chi`,
`pon`, `kan`, `nuki`, `ron`, `tsumo`, `draw_end`, `match_end`, `countdown`, `turn`.
Prefix each with `table.` to replace the corresponding effect. The distributed
effects reference Minecraft's own sound events rather than redistributing audio
from another mahjong game. Mono Ogg Vorbis is recommended for positional effects;
use Minecraft's standard `sounds.json` format for variations, volume and pitch.
