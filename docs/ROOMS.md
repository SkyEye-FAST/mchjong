# Rooms, permissions and settings

The lobby directly exposes player count, rule preset, rule details, hand visibility,
time allowances, invitations and participants. The primary button fills empty seats
and starts seat preparation. Leave room and the host's Close room control stay
in the top toolbar. The Settings button groups additional controls into World, Room and Personal.
Administrators can edit world values in the World tab; other players can inspect them. Personal
display, input, camera and audio settings remain local to each player. Automatic
play preferences belong to the individual seated player and remain accessible
from the match overlay.

## World policy

Each world save has `config/mchjong-world.json`, shared by all dimensions and
tables in that save. Its initial contents are:

```json
{
  "invitationTeleport": false
}
```

Administrators with command permission level 2 can change these values in
Settings → World. The buttons display server-confirmed values and use the same
permission-checked commands available below:

```text
/mchjong world
/mchjong world invitationTeleport true
/mchjong world reload
```

Changes made by commands are saved atomically. Reload applies file edits. World
policy applies to active tables as well as new rooms; it does not reset room
rules or readiness.

## Hand visibility

The host chooses Hand visibility directly in the lobby before play. The choice is
saved with that table, and changing it clears readiness:

- **Open hands:** everyone sees the hands laid face up.
- **Visible to all players:** everyone, including nearby spectators, sees the
  ordinary upright tile faces from their physical viewing angle.
- **Visible to riichi players:** a player who has declared riichi can see other
  players' upright tile faces. Other viewers see concealed faces.
- **Visible only to self:** each seated player sees their own hand. This is the default.

Hands revealed at settlement remain public. Spectators can stand beside the table
and inspect its world rendering, or interact with an active table to open its
overview. Spectating leaves participant seats available and grants no game actions.

## Room ownership

The first human to sit down becomes the host. Ownership follows the player,
independently of their seat number. The host controls rules and time allowances
before play. Players and bots lists other human participants as ownership-transfer
targets; `/mchjong host <player>` performs the same transfer. The successor must
be participating at that table. Explicitly leaving a waiting room transfers ownership to
a remaining human. Standing up to move to an assigned stool retains room membership.

## Preparing seats

Players join by sitting on a stool. The waiting room has three stages: gathering,
wind drawing on an ordinary table, and occupying the assigned seats. The host
can fill empty places with bots or configure them directly on the top seat cards.

The centered primary button fills empty places, then advances seat preparation.
Once all places have occupants, an ordinary table presents
face-down wind tiles: each human chooses one, then bots draw the remainder. An
automatic table shuffles the entire roster immediately. The original east seat is
the south-side stool; the other winds follow the physical table order. The interface
shows each destination's coordinates so wind assignments cannot be confused with
world compass directions.

Assignment keeps the host, personal preferences and bot difficulty attached to
their respective participants. A player whose stool changed is dismounted and
must sit on the assigned stool. The server verifies actual mounts, including after
reload; a Ready packet cannot substitute for being there. All humans must be in
place and ready, and the table must contain the required equipment, before play
starts. The east assignment is the initial dealer. Starting a new match returns
the roster to gathering and requires a new seat assignment.

An absent participant's place can be replaced by a bot before play. A previously
drawn wind stays with that place, so replacing a participant neither redraws nor
duplicates a wind. Removing a bot makes the place available to a human. Standing
up during repositioning preserves the reservation; Leave room releases it.

## Bot difficulties

Each bot can be set to Easy or Hard during any waiting-room stage.
Changing the roster or difficulty clears human readiness. Bots remain ready while
humans reposition themselves.

The control in each empty or bot-occupied top seat card cycles through Empty, Easy
and Hard. A physically seated human cannot be replaced; their control is
reserved for transferring room ownership. Fill empty seats adds Easy bots.

Easy prioritizes current shanten, live improving tiles and retaining value, with
productive calls and basic defense against clear threats. Hard adds bounded
draw/discard development, including same-shanten improvement, weighted legal
winning value, and more detailed per-opponent attack/defense assessment. It can
trade a little immediate efficiency for better development or retain safe tiles
while advancing a valuable hand. Both compare legal calls, riichi, kans and north
extraction under the table's rules.

Both use only their own hand and public tiles. They do not read opponents'
concealed tiles, even when room visibility allows players to see those hands, and do
not inspect the wall seed. Bot decisions use deterministic heuristic evaluation.

## Invitations

Use Invite player in the lobby or `/mchjong invite <player>`, with an online
player's name or UUID. Invitations are recipient-bound,
expire after 60 seconds, and are checked again when accepted. A player may send
one invitation every five seconds.

With invitation teleportation disabled, accept within six blocks of the table.
When it is enabled, accepting from farther away, including another dimension,
can transport the recipient beside the table. The destination must be loaded,
inside the world border, free of collisions and liquids, and suitable for safe
dismounting. Travel does not load distant chunks. The invitation explains
whether teleportation is enabled; after travelling, sit on a free stool to join.
Travel must have been offered in that invitation and must still be enabled when
accepted. Enabling travel later does not silently change an older local-only
invitation into a teleport. Arrival clears movement velocity and fall distance.

## Settlement and returning to the lobby

Each hand's settlement displays a server-controlled 10-second countdown before
the next hand begins. At the end of a match, the hand settlement advances to final
standings after 10 seconds; those standings remain for another 10 seconds before
everyone returns to the lobby. Reopening the interface or reconnecting shows the
remaining server time. Saved tables preserve that time.

The lobby retains its members, host, bots and room settings. Players can leave
individually, the host can dissolve the room, or the group can prepare fresh seats
for another match. During a running match, ending play still uses unanimous human
approval when more than one human participates.

## Maid players

Install the matching maid mod on the server and clients, then select **Mahjong**
in the maid's task selector. Sit at an equipped table with an empty stool and keep
the maid nearby within her work area. During work hours she approaches the stool
and joins the room. Choose the player count before recruiting maids; the host can
fill remaining places with training bots and adjust each maid's difficulty using
the bot controls.

Maids retain their names and move with their assigned seats. Their saved binding
restores the physical mount after a world reload. Changing tasks, dismissing a
maid from the room, or removing her stool releases the physical seat. During an
active match a training bot continues the vacated place. Room dismissal returns
the maid to Idle; select Mahjong again to recruit her for another room.

See [Compatibility](COMPATIBILITY.md) for supported distributions and versions.
Developer checks are maintained in [Verification](VERIFICATION.md).
