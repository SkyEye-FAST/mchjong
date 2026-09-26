# Playing at a table

[Documentation](README.md) · [Project overview](../README.md)

MChjong features three- and four-player riichi mahjong. See
[Rules and presets](RULES.md) for available presets and custom settings.

## Equipment and seating

### In-game handbook

Install the matching optional Patchouli build and use the Mahjong Handbook item
to open it. The handbook includes equipment recipes, seating, controls, riichi
basics, settlement, replays and customization in all four supported languages.
Each player receives one on first joining a world with Patchouli installed on the
server. Craft additional copies with a book, green dye and any mahjong tile, or
take one from the MChjong creative tab.

On supported loaders, a client without Patchouli shows a chat recommendation
once per game session after entering a world, with a clickable download link.
Set `recommendPatchouli = false` in
`config/mchjong-client.toml` to disable the automatic reminder.
See [Compatibility](COMPATIBILITY.md) for matching builds.

### Preparing the table

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
See [Survival equipment and recipes](SURVIVAL.md) for the complete recipes,
component schema, dyes, glass tiles, point sticks and removal controls.

Place stools two blocks from the center on the sides used by your rules. Click
a stool to sit. Right-click the table to manage its two case slots. Crouch-click with
both hands empty collects the cloth from the tabletop. Retrieve boxes through
the storage screen. Cloth and box changes require the lobby.
When no equipment is collected, crouch-clicking opens the spectator view.
Clicking your stool again reopens the controls
without creating another seat. Press Esc to close the overlay while staying seated;
dismount with Minecraft's sneak control after closing it.

## Point-stick payments

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

## Lobby and leaving a match

In the lobby, select **Four-player mahjong** or **Three-player mahjong**, then
choose the rule preset for that player count. **Settings** separates world,
room and personal controls. Hosts choose **Hand visibility** for their room:
open hands, visible to all players, visible to riichi players, or visible only
to self. Nearby spectators can see upright hand faces in the all-players mode
while standing beside the table. Hosts can transfer room ownership to another participant. See
[Rooms and permissions](ROOMS.md) for configuration and commands.

**Exit** ends the table immediately when only one human is registered, even
with bots present. With multiple humans, all must agree in a 30-second vote.
The game and thinking clocks pause during the vote; rejecting or letting it
expire resumes the same decision without granting extra thinking time. A failed
vote has a 30-second cooldown. Disconnecting or dismounting does not count as
agreement or remove a participant from the vote. Successful exit releases every
seat and returns the table to the lobby. Completed hands stay in the replay
archive; an unfinished hand is not scored.

## Controls and views

Click tiles directly on the physical table. Settings offer single-click,
double-click, or select-and-confirm discards. Shift-click selects without
discarding. Enter confirms the selected tile; R opens riichi selection
and P passes a response. Riichi highlights only legal discard candidates and
requires confirmation. Right-click or Esc cancels a selection before closing the
overlay. Right-drag looks freely around the seated table; the wheel moves closer
or farther away. Shift-right-drag pans across the table, and holding an arrow key
finely adjusts yaw or pitch. Hold C to inspect with a smooth move toward the table,
a narrower field of view and gentler controls. Home restores the complete seated
view. Distance and height sliders preserve the current look direction.
Rebind C, V, Home, R, P and E in Minecraft's Controls → Key Binds → Mahjong table.
Use the top-bar view button or V to switch between the seated and immersive
views. Immersive play uses one fixed 1280 × 800 virtual mahjong layout: your large
clickable hand and raised rack form the foreground, melds lie flat at each owner's
right-hand table corner, and four rivers surround the compact central table device.
One perspective camera projects the cloth, solid tiles and upright opponent hands.
Long melds wrap into the owner's inner corner. Compact player plaques remain at
the table edges, and the rivers preserve six discards per row with sideways riichi
tiles aligned to the row's top edge. Draws animate into the hand and discards into the river. Tsumogiri is dimmed in the
river while tedashi remains at normal brightness and follows a more pronounced discard
arc. The central device uses point-stick icons for honba and riichi deposits.
Minecraft GUI scale and window dimensions do not reflow this layout. The complete
1280 × 800 canvas is uniformly scaled to the largest size that fits the window, with
solid black letterbox or pillarbox bars around the unused area. Small windows keep the
same proportions and rendering instead of switching to another layout.
It remains usable under ceilings and other world obstructions, with the same
discard modes and keyboard controls. Closing the overlay reveals the world again.
Ordinary-table handling actions are available as buttons in immersive view;
E opens your point-stick drawer.

The compact top bar shows points and essential table information; hover over a
player card for detailed status. Player names have skin portraits; practice bots
use a distinct robot portrait. The concealed run stays centered, with a separate
drawn-tile slot. Melds extend left from your right corner at hand depth; the hand
shifts left only when the actual tiles need room. The Camera settings include **Show discards on the table**.
Hiding the rivers always keeps the remaining wall count visible, even when that
information would otherwise be disabled. This is a local presentation preference
and does not change game rules or replay records.

## Automatic-table quick controls

During an automatic-table match, the lower-left quick controls offer auto sort,
auto win, no calls and auto discard, plus auto kita in three-player games.
The side arrow switches between localized initials and full labels; each option
remains clickable and keyboard-accessible in either presentation. Filled or
outlined state marks and hover labels identify the current setting. Each change
waits for the server's acknowledgement; sorting starts enabled and the other
options start disabled. Auto kita uses legal north extractions before automatic
discards, while a possible win always takes priority.

## Results and progression

Results reveal yaku one at a time with the selected voice preset, followed by
points and the hand grade. They also show winning hands and melds, and revealed
dora/ura indicators without a scroll viewport. Select a winner's tab
when several players ron, or focus the result panel and use Left/Right.
Separate tabs show hand details, animated point changes and server-authoritative
final standings. Large windows show point summaries alongside the hand;
shorter windows keep the dedicated point-change tab available. **View table** hides the receipt without
advancing play. The first **Skip wait** finishes the local readout; another
**Skip wait** advances to the next stage. Otherwise, after seated players finish
their readouts, the server leaves ten seconds to inspect the receipt before
continuing. Final standings have a separate ten-second stage before returning to
the lobby. Table animations can be disabled
independently of the game rules in the interaction settings.

## Time controls

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
revalidated when accepted. By default, approach within six blocks before accepting.
Administrators can enable safe invitation teleportation beside loaded tables;
after travelling, sit down to join. Senders may invite once per five seconds.

The Audio settings include positional table effects, countdown warnings and
custom ZIP recordings. See [Audio and voice presets](AUDIO.md) for the
recording names, volume controls and preset layout.

## Replays

Use **Replays** in the table overlay or `/mchjong replays [page]` anywhere on the
server. Select a match to view completed hands; `/mchjong replay <match UUID>`
opens a specific match. Only that match's human participants can retrieve it.
Use the timeline, step controls and Play/Pause to inspect draws, discards, calls,
riichi, indicators and settlements. Arrow keys step, Home/End seek, and Space
toggles playback. Scroll the board to inspect every seat.

**Export Tenhou JSON** writes `<game directory>/replays/mchjong/<match UUID>.json`.
The export uses Tenhou's `/6` JSON interchange format and is saved locally.
See [Replay storage and format](REPLAYS.md)
for privacy, controls, persistence and format details. Viewing a replay does not
pause an active table or its server clock.
