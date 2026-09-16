# MCjhong

MCjhong is a Minecraft mod that adds a seated, in-world riichi mahjong table for
three or four players. It supports both Fabric and NeoForge.

## Features

- Placeable mahjong tables, stools, and tiles
- Three- and four-player riichi mahjong gameplay
- In-world drawing, discarding, melds, and action previews
- Animated wall assembly, packet dealing, calls, kans, and riichi sticks
- Single-screen settlement panels with winning hands, yaku, indicators, and point changes
- Unanimous table-exit voting and host-controlled open-hand games
- Configurable per-hand reserve and per-decision clocks, with distinct discard animations
- Player invitations, table sound effects, system speech and custom voice packs
- Private replay archives, step-by-step playback and Tenhou JSON export
- Shared gameplay and client presentation across both loaders
- English, Japanese, Simplified Chinese, and Traditional Chinese localization
- Optional patterned tile-back resource pack

## Compatibility

The compatibility target covers mainstream Minecraft releases from 1.20.1
onward. Release artifacts are version-specific because Minecraft and loader APIs
change between release lines; use the artifact built for your Minecraft version.

The current development profile is:

| Platform | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21 or newer |
| Fabric Loader | 0.16.10 or newer, plus Fabric API |
| NeoForge | 21.1.250 or newer |

## Installation

Build the JAR for your loader as described below, then copy it to the
Minecraft `mods` folder:

- Fabric: `build/libs/mchjong-fabric-*.jar`
- NeoForge: `neoforge/build/libs/mchjong-neoforge-*.jar`

Install the matching loader and its required dependencies before launching the
game. Fabric installations also require Fabric API.

## Building from source

Requires JDK 21 or newer.

```bash
git clone https://github.com/SkyEye-FAST/mchjong.git
cd mchjong
```

The default `main` branch contains both Fabric and NeoForge support. Minecraft,
loader, mappings, and Java versions are selected by the build profile in
`gradle.properties`.

### Fabric

```bash
./gradlew build
./gradlew runClient   # optional development client
./gradlew runServer   # optional dedicated server
```

### NeoForge

```bash
./gradlew :neoforge:build
./gradlew :neoforge:runClient   # optional development client
./gradlew :neoforge:runServer   # optional dedicated server
```

To build both loaders and run the shared checks:

```bash
./gradlew buildAll
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Playing at a table

Place stools two blocks from the center on the sides used by your rules. Click
the table to sit on the nearest side, or click a specific stool. Crouch-click the
table to spectate. Clicking the table or your stool again reopens the controls
without creating another seat. Press Esc to close the overlay while staying seated;
dismount with Minecraft's sneak control after closing it.

In the lobby, select **Four-player mahjong** or **Three-player mahjong**, then
choose the rule preset for that player count. The host can enable **Show
opponents' hands** before starting. This setting reveals hands to seated
participants, not spectators, and changing it clears the players' ready votes.

**Exit** ends the table immediately when only one human is registered, even
with bots present. With multiple humans, all must agree in a 30-second vote.
The game and thinking clocks pause during the vote; rejecting or letting it
expire resumes the same decision without granting extra thinking time. A failed
vote has a 30-second cooldown. Disconnecting or dismounting does not count as
agreement or remove a participant from the vote. Successful exit releases every
seat and returns the table to the lobby. Completed hands stay in the replay
archive; an unfinished hand is not scored.

Click tiles directly on the physical table. Settings offer single-click,
double-click, or select-and-confirm discards. Shift-click selects without
discarding. Left/Right selects tiles and Enter confirms; R opens riichi selection
and P passes a response. Riichi highlights only legal discard candidates and
requires confirmation. Right-click or Esc cancels a selection before closing the
overlay. Right-drag looks around; Home or **Center view** restores the table view.

The compact top bar shows points and essential table information; hover over a
player card for detailed status. The concealed-hand rail reserves space for melds
from the first deal. The Camera settings include **Show discards on the table**.
Hiding the rivers always keeps the remaining wall count visible, even when that
information would otherwise be disabled. This is a local presentation preference
and does not change game rules or replay records.

Results show each player's point movement, winning hands and melds, yaku, and
revealed dora/ura indicators without a scroll viewport. Select a winner's tab
when several players ron, or focus the result panel and use Left/Right.
Separate tabs show hand details, animated point changes and server-authoritative
final standings. Large windows show point summaries alongside the hand;
shorter windows keep the dedicated point-change tab available. **View table** hides the receipt without
advancing play. **Continue** readies your seat for the next hand; **Ready for new
match** starts another match only after all seats are ready. Table animations can be disabled
independently of the game rules in the interaction settings.

The lobby host can open **Time control** or run `/mchjong clock 20 5`: a shared
20-second reserve per hand plus a fresh 5-second allowance for each turn or
reaction. The move allowance is used first. Answered reactions stop their own
clock; dealing and settlement do not consume time. The server enforces timeouts
even with the overlay closed or a player disconnected: pass a reaction or discard
the drawn tile (a legal hand tile after a call), never automatically claim a win.
Drawn-tile discards and hand discards have distinct animation paths and cues.

## Invitations and audio

In a waiting table, **Invite player** lists online players. `/mchjong invite <player>`
does the same. The recipient receives clickable Accept/Decline actions. Invitations
expire after 60 seconds, are bound to the recipient and table identity, and are
revalidated when accepted. Approach within six blocks before accepting; invitations
do not teleport players or load distant chunks. Senders may invite once per five seconds.

The Audio settings include positional table effects, countdown warnings, system
speech and custom resource-pack recordings. See [Audio customization](docs/AUDIO.md)
for the event names, volume controls and an example voice resource pack.

## Replays

Use **Replays** in the table overlay or `/mchjong replays [page]` anywhere on the
server. Select a match to view completed hands; `/mchjong replay <match UUID>`
opens a specific match. Only that match's human participants can retrieve it.
Use the timeline, step controls and Play/Pause to inspect draws, discards, calls,
riichi, indicators and settlements. Arrow keys step, Home/End seek, and Space
toggles playback. Scroll the board to inspect every seat.

**Export Tenhou JSON** writes `<game directory>/replays/mchjong/<match UUID>.json`.
The export is Tenhou's `/6` JSON interchange format, not binary/XML `.mjlog`.
Nothing is uploaded to Tenhou. See [Replay storage and format](docs/REPLAYS.md)
for privacy, controls, persistence and format details. Viewing a replay does not
pause an active table or its server clock.

## Optional resource pack

Tile backs are solid teal by default. Enable **MCjhong: Patterned tile backs**
under **Options > Resource Packs** to use the built-in diamond pattern. Disable
the pack to restore the default backs. See [docs/ASSETS.md](docs/ASSETS.md) for
customization and asset details.

## Credits

Tile faces are generated from [FluffyStuff's riichi-mahjong-tiles](https://github.com/FluffyStuff/riichi-mahjong-tiles),
released under CC0. See [NOTICE](NOTICE) and [docs/ASSETS.md](docs/ASSETS.md)
for attribution and the asset generation contract.

## License

MCjhong is licensed under the [Apache License 2.0](LICENSE).

Release history is maintained in [CHANGELOG.md](CHANGELOG.md).
