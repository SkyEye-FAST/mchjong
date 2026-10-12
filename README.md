# MChjong

[![Snapshot CI](https://github.com/SkyEye-FAST/mchjong/actions/workflows/snapshot.yml/badge.svg?branch=main)](https://github.com/SkyEye-FAST/mchjong/actions/workflows/snapshot.yml)
[![Release](https://github.com/SkyEye-FAST/mchjong/actions/workflows/release.yml/badge.svg)](https://github.com/SkyEye-FAST/mchjong/actions/workflows/release.yml)

MChjong adds mahjong tables to Minecraft for matches with other players or computer
opponents. Play at an ordinary mahjong table to handle the tiles yourself, or use
an automatic mahjong table to shuffle, deal and draw for you.

## Installation

Choose a supported Minecraft version and loader from the table below. Download
the corresponding JAR from [Releases](https://github.com/SkyEye-FAST/mchjong/releases)
and place it in the `mods` folder on the server and each client. Fabric
installations also require Fabric API.

| Minecraft | Branch | Java runtime | Loaders |
| --- | --- | --- | --- |
| 1.21.1 | `main` | 21 | Fabric, Forge, NeoForge |
| 1.20.1 | `compat/1.20.1` | 17 | Fabric, Forge |
| 26.1.2 | `compat/26.1.2` | 25 | Fabric, NeoForge |

Quilt uses the matching Fabric artifact. For Minecraft 1.21.1, install Fabric
Loader 0.16.10 or later, Forge 52.1.16 or later, or NeoForge 21.1.250 or later.
Unreleased builds are available as artifacts from
[Snapshot CI](https://github.com/SkyEye-FAST/mchjong/actions/workflows/snapshot.yml).
See [CHANGELOG.md](CHANGELOG.md) for changes in published and upcoming versions.

## Playing

### Preparing a mahjong table

The variant determines which mahjong table and tile set you need:

| Variant | Players | Tile set | Mahjong table |
| --- | --- | --- | --- |
| Riichi mahjong | 3 or 4 | 108 tiles for three players; 136 tiles for four players | Ordinary or automatic |
| Mahjong Competition Rules (MCR) | 4 | 144 tiles, including eight flowers | Automatic |
| Sichuan mahjong | 4 | 108 suited tiles | Automatic |
| Taiwanese mahjong | 4 | Pocket preset: 144 tiles with flowers; Southern preset: 136 tiles without flowers | Automatic |

The three-player Riichi mahjong set excludes the character tiles numbered two
through eight. Every variant requires a complete set with matching material,
back color and design in a single mahjong box. The table selects the tiles it
needs and leaves spare tiles in storage; two incomplete boxes cannot supply one set.

To make a new set, use a stonecutter to produce blank tiles. Open a held mahjong
box, put exactly 136 or 144 matching blank tiles into its tile slots, and insert
Mahjong Dye into the dye slot. Choose a face preset and select Apply. Printing
produces ordinary fives; use Red Dora Dye on individual fives when your chosen
Riichi mahjong preset requires red fives. Undo Dye reverses that conversion,
while reprinting a complete set restores all its ordinary fives.

Place the mahjong table in a clear 3 x 3 block area with solid support beneath
its center. Place stools two blocks from the center on the participating sides,
install a cloth, and put the box inside the table. An ordinary mahjong table
also needs two dice and the starting point sticks listed by the preparation
screen. The room footer identifies any missing equipment before play can begin.

### Starting a match

Sit on a stool to join the room. The first human player becomes the host and can
choose the variant and rule preset before preparing seats. Riichi mahjong offers
Mahjong Soul, Tenhou, M.League, JPML A and WRC presets; only Mahjong Soul and Tenhou
provide three-player presets. Sichuan mahjong offers MIL SBR 2025 and
T/TFMJ 01-2024 presets, while Taiwanese mahjong offers Pocket and Southern presets.

Edit match settings in the rule editor and select Apply Rules to save them.
Wait for confirmation before continuing; Cancel discards edits that have not
been submitted. Invite other players or fill empty seats with computer opponents.
Once the roster is complete, start seat assignment. Each human player must occupy
the assigned stool and select Ready.

### Playing and leaving the table

On ordinary mahjong tables, use the highlighted tiles and dice to shuffle the
tiles, build the walls, deal the starting hands and draw tiles. Automatic mahjong
tables perform these steps themselves. During play, click a tile in your hand to
discard it and use the action buttons for calls and win declarations. At an
ordinary mahjong table, transfer physical point sticks through the side drawers
after each hand, using the result screen's point changes as the payment reference.

The view button switches between seated and immersive play. In seated view,
drag with the right mouse button to adjust the camera and scroll the mouse
wheel to change its distance. Personal Settings lets you choose single- or
double-click discards and adjust your display and audio preferences.

Press Esc to cancel a selection or close the interface, then use the sneak
control to stand up. Standing up does not end an active match: the table pauses
when no human player is seated and resumes when a participant returns. To end
the match instead, use its exit control. A sole human participant can end play
directly; a match with several human participants requires unanimous approval.
An early exit preserves completed hands without scoring the unfinished hand.

## Rooms and server settings

Hosts manage their room through Room settings. In Riichi mahjong, they can also
enable convenience hints or change hand visibility. The hints estimate unseen
tile counts using the player's hand and public tiles; they do not reveal the
wall. Built-in computer opponents use the same limited information, even when
the room allows players to see other hands.

To invite an online player, use `/mchjong invite <player>`. The invitation lasts
60 seconds and normally requires the recipient to accept within six blocks of
the table. An administrator can enable invitation teleportation to let players
accept from farther away.

Administrators manage these server-wide permissions in World settings, including
invitation teleportation, spectator access, computer players, replays and custom
rules. The values are stored in `<world>/config/mchjong-world.toml`.
After editing that file, run `/mchjong world reload` to apply the changes.

### External computer opponents

To use an external Riichi mahjong opponent, first run a compatible bot service
such as the [Mortal adapter](https://github.com/SkyEye-FAST/mchjong-mortal-adapter).
Point Minecraft at that service by creating `config/mchjong/bot-service.json`
in the Minecraft server directory:

```json
{"endpoint": "http://127.0.0.1:8791", "timeout_ms": 10000}
```

Replace the endpoint with the service's address and restart the Minecraft server.
Discovered opponents appear in automatic-table rooms whose player count and
rule preset they support. Keep the default scoring-award selection for these
opponents. If the service fails, the room displays an error and the affected
opponent waits rather than being replaced by a built-in opponent.

## Replays

Open Replays at the table or run `/mchjong replays` to browse matches in which
you participated. Select a match, then use the hand selector, timeline and
viewpoint control to review its completed hands. Spectators cannot retrieve
another player's replay, and opening a replay does not pause a live match.

The export button saves a file under `<game directory>/replays/mchjong/`.
Riichi mahjong matches with default scoring awards use Tenhou JSON. Other
variants, and Riichi mahjong matches with modified award selections, use native
replay JSON. Native records include the original wall and concealed hands, so
sharing an export can reveal information that was hidden during play.

Deleting an entry removes it from your replay list without removing other
participants' access or previously exported files. Server administrators should
include `<world>/data/mchjong/replays/` in world backups to preserve the archives.

## Optional integrations

The following integrations are optional. Install the additional mod for the
Minecraft version and loader shown below:

| Integration | 1.21.1 | 1.20.1 | 26.1.2 |
| --- | --- | --- | --- |
| JEI | Fabric, Forge, NeoForge | Fabric, Forge | Fabric, NeoForge |
| EMI | Fabric, NeoForge | Fabric, Forge | Not included |
| REI | Fabric, NeoForge | Fabric, Forge | Fabric, NeoForge |
| Ponder | Fabric, NeoForge | Fabric, Forge | Not included |
| Patchouli handbook | Not included | Fabric, Forge | Fabric, NeoForge |
| Mod Menu | Fabric | Fabric | Fabric |
| Create | NeoForge | Fabric, Forge | Not included |
| Touhou Little Maid | Fabric, NeoForge | Fabric, Forge | Fabric, NeoForge |

JEI, EMI and REI display recipes for mahjong supplies. Create adds machine-based
production, printing, dyeing and packing; Fabric installations use Create Fabric.
Ponder provides animated equipment and operation tutorials: hover over a
supported item and hold the key shown in its tooltip.

Touhou Little Maid adds maid participants; Fabric installations use Touhou
Little Maid: Orihime. To recruit a maid, select her Mahjong task and sit at an
equipped table with an empty stool inside her work area during working hours.

Mod Menu adds a personal-settings entry to the Fabric mod list. Forge and
NeoForge provide that entry through their native Mods screens.

## Documentation

To add tile designs, recordings or custom scoring awards, see
[Custom content](docs/ASSETS.md). For source builds and contributions, start with
[Development](docs/DEVELOPMENT.md) and [AGENTS.md](AGENTS.md). Interface changes
follow [UI guidance](docs/UI_STYLE.md), and localization follows [TERMS.md](TERMS.md).

## License and credits

MChjong's code is licensed under [Apache License 2.0](LICENSE). Third-party
artwork retains the rights listed in the [artwork notice](presets/tile_faces/NOTICE.md),
and [NOTICE.md](NOTICE.md) attributes the bundled ancient-yaku example.
