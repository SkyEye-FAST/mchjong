# MCjhong

MCjhong is a Minecraft mod that adds a seated, in-world riichi mahjong table for
three or four players. It supports both Fabric and NeoForge.

## Features

- Ordinary tables with manual handling and automatic adjudication, plus upgradeable automatic tables
- Component-based wood furniture, removable 16-color cloth and six physical tile materials
- Stonecut blanks, boxed bulk engraving and dyeing, and physical point sticks
- Three- and four-player riichi mahjong gameplay
- In-world drawing, discarding, melds, and action previews
- Animated wall assembly, packet dealing, calls, kans, and riichi sticks
- Single-screen settlement panels with winning hands, yaku, indicators, and point changes
- Unanimous table-exit voting and host-controlled open-hand games
- Configurable per-hand reserve and per-decision clocks, with distinct discard animations
- Player invitations, table sound effects and custom recorded voice packs
- Two-case table storage, with a tablecloth required for play
- Eight physical flower and season tiles, stored separately from the riichi wall
- Private replay archives, step-by-step playback and Tenhou JSON export
- Shared gameplay and client presentation across both loaders
- English, Japanese, Simplified Chinese, and Traditional Chinese localization
- Resource-pack customization of tile faces and backs
- Optional Ponder tutorials for table placement, equipment and seated play
- Optional component-aware JEI and EMI recipe browsing

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

The optional Ponder integration targets Ponder 1.0.87 or newer for this profile.
Use the Ponder release for the same Minecraft version and loader, together with
its declared dependencies.

JEI and EMI offer component-aware supply recipes. Use the
viewer release for your loader and Minecraft version. See
[compatibility and verification](docs/COMPATIBILITY.md) for the exact development
dependencies, runtime profiles and test coverage.

## Installation

Build the JAR for your loader as described below, then copy it to the
Minecraft `mods` folder:

- Fabric: `build/libs/mchjong-fabric-*.jar`
- NeoForge: `neoforge/build/libs/mchjong-neoforge-*.jar`

Install the matching loader and its required dependencies before launching the
game. Fabric installations also require Fabric API.

### In-game tutorials

Install Ponder to add animated guides to both table items, stools, boxes, cloth,
tiles and point sticks. Hover over an item in an inventory and hold the key shown
by Ponder's tooltip. The **Mahjong** category groups the guides for placement,
equipment and seated play. Tutorials follow the selected game language and use
Ponder's playback, pause and replay controls.

Ponder is an optional client integration. MCjhong's core gameplay works
independently, and each player can choose whether to install the guide.
See [Ponder integration](docs/PONDER.md) for development and validation.

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

Build an ordinary table from matching wooden slabs and fences. Cut material
blocks into blanks, put 136 or 144 matching blanks in a mahjong box, and apply
Kansai or Kanto faces using one mahjong dye in the box's dye slot. The 144-tile set
includes eight flowers. Right-click either table to store up to two boxes inside, and
lay a cloth on its surface. A complete set and a cloth are required to play.
On ordinary tables, sweep the face-down tiles across the felt to shuffle, drag
your highlighted loose tiles toward your wall, and pull starting packets and
draws from the highlighted wall stack toward your hand. Automatic tables handle
these steps for you. Tab focuses the physical source; Enter or Space performs
its available action with keyboard and narration support.
See [Survival equipment and recipes](docs/SURVIVAL.md) for the complete recipes,
component schema, dyes, glass tiles, point sticks and removal controls.

Place stools two blocks from the center on the sides used by your rules. Click
a stool to sit. Right-click the table to manage its two case slots. Crouch-click with
both hands empty collects the cloth from the tabletop. Retrieve boxes through
the storage screen. Cloth and box changes require the lobby.
When no equipment is collected, crouch-clicking opens the spectator view.
Clicking your stool again reopens the controls
without creating another seat. Press Esc to close the overlay while staying seated;
dismount with Minecraft's sneak control after closing it.

Ordinary tables have a wooden point-stick drawer on each side. Open one from the
world, click its front while seated, or press E in the seated overlay. The native container shows all four
drawers: take sticks from your own row and place them in the recipient's row to
pay. Each drawer holds nine stacks, including mixed denominations. Shift-click
stores inventory sticks in the drawer you opened. During a match, withdrawal is
limited to your own drawer; the practice host also handles training players'
drawers. Closing the container returns to the seated view.

The container displays physical totals beside the referee's game scores as a
payment reference. Players transfer the actual items themselves. After settling
a hand, close the result panel and sweep your tiles toward the center to collect
them for the next hand. Drawer contents persist with the table and are returned
with its equipment when the table is removed.

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
overlay. Right-drag looks around in the seated view; Home centers it again.
Use the top-bar view button or V to switch between the elevated seated view and
the overhead view. The overhead view keeps your hand in a clickable strip along
the bottom, with the same discard modes and keyboard controls. Closing the
overlay returns to the seated view.

The compact top bar shows points and essential table information; hover over a
player card for detailed status. The concealed run stays centered, with a separate
drawn-tile slot. Melds extend left from your right corner at hand depth; the hand
shifts left only when the actual tiles need room. The Camera settings include **Show discards on the table**.
Hiding the rivers always keeps the remaining wall count visible, even when that
information would otherwise be disabled. This is a local presentation preference
and does not change game rules or replay records.

During an automatic-table match, **Auto play** at the lower left expands four
quick controls: auto sort, auto win, no calls and auto discard. **Collapse** hides
them again without changing their values. Each change waits for the server's
acknowledgement; sorting starts enabled and the other options start disabled.

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

The Audio settings include positional table effects, countdown warnings and
custom resource-pack recordings. See [Audio customization](docs/AUDIO.md)
for the event names, volume controls and an example voice resource pack.

## Replays

Use **Replays** in the table overlay or `/mchjong replays [page]` anywhere on the
server. Select a match to view completed hands; `/mchjong replay <match UUID>`
opens a specific match. Only that match's human participants can retrieve it.
Use the timeline, step controls and Play/Pause to inspect draws, discards, calls,
riichi, indicators and settlements. Arrow keys step, Home/End seek, and Space
toggles playback. Scroll the board to inspect every seat.

**Export Tenhou JSON** writes `<game directory>/replays/mchjong/<match UUID>.json`.
The export uses Tenhou's `/6` JSON interchange format and is saved locally.
See [Replay storage and format](docs/REPLAYS.md)
for privacy, controls, persistence and format details. Viewing a replay does not
pause an active table or its server clock.

## Interface style

The interface uses shared flat, dark-teal controls with warm-white text and brass
accents. The mahjong box has a dedicated supply-case screen with a synchronized
inventory, a locked carrier slot and a read-only packing summary. Contributors
should follow [Interface style](docs/UI_STYLE.md) for all presentation changes.

## Tile customization

Tile backs are solid by default. Custom resource packs can replace the independent
back texture independently of tile faces and rules.
See [docs/ASSETS.md](docs/ASSETS.md) for customization and asset details.

## Credits

The built-in presets use the supplied Mizuno Maruichi and FluffyStuff/lietxia
atlases. Their original attribution and permission metadata are preserved in
[the artwork notice](presets/tile_faces/NOTICE.md), including the Mizuno source's
unverified redistribution permission. Kansai includes the seasons and botanical
flowers; Kanto includes the seasons and 福禄寿貴, with names matching each preset.
See [docs/ASSETS.md](docs/ASSETS.md) for source provenance, bundled upstream
licenses, adaptation details, and the asset generation contract.

## License

MCjhong's code is licensed under the [Apache License 2.0](LICENSE).
Third-party artwork retains its separately documented rights and notices.

Release history is maintained in [CHANGELOG.md](CHANGELOG.md).
