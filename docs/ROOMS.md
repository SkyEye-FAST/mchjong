# Rooms, permissions and settings

The table's Settings button groups controls into World, Room and Personal.
World values are displayed read-only to players, including room hosts. Personal
display, input, camera and audio settings remain local to each player. Automatic
play preferences belong to the individual seated player and remain accessible
from the match overlay.

## World policy

Each world save has `config/mchjong-world.json`, shared by all dimensions and
tables in that save. Its initial contents are:

```json
{
  "openHands": false,
  "invitationTeleport": false
}
```

Administrators with command permission level 2 can query, change or reload it:

```text
/mchjong world
/mchjong world openHands true
/mchjong world invitationTeleport true
/mchjong world reload
```

Changes made by commands are saved atomically. Reload applies file edits. World
policy applies to active tables as well as new rooms; it does not reset room
rules or readiness. Open hands reveals opponents' concealed hands only to
seated participants. Spectators still receive hidden tile identities.

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
can fill empty places with bots or manage each place through Players and bots.

Once all places have occupants, choose Assign seats. An ordinary table presents
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

Each bot can be set to Easy, Normal or Hard during any waiting-room stage.
Changing the roster or difficulty clears human readiness. Bots remain ready while
humans reposition themselves.

The control beside each empty or bot-occupied place cycles through Empty, Easy,
Normal and Hard. A physically seated human cannot be replaced; their control is
reserved for transferring room ownership. Fill empty seats adds Normal bots.

Easy prioritizes shanten reduction and retaining valuable tiles, with basic
defense. Normal additionally counts live improving tiles, makes productive calls
with a guaranteed open-hand yaku, and folds toward safe discards under pressure.
Hard also considers good-shape improvement, furiten, multiple threats, dealer
risk, suji, visible tile exhaustion and closed sequence routes. Open calls still
need a yaku and measurable progress.

All three use only their own hand and public tiles. They do not read opponents'
concealed tiles, even when world policy allows players to see those hands, and do
not inspect the wall seed. These are deterministic heuristics, not a trained model
or a claim of a benchmarked win rate.

## Invitations

Use Invite player or `/mchjong invite <player>`. Invitations are recipient-bound,
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

## Verification

`gradlew.bat buildAll --warning-mode fail` covers the seating state machine,
ownership, readiness, saved-room privacy, bot choices and both loader builds.
The existing room tests exercise departures during wind drawing, replacements
after assignment and fresh seating on a rematch.

`gradlew.bat :runSmokeClient --console=plain` and
`gradlew.bat :neoforge:runSmokeClient --console=plain` drive the real preparation
controls and physical remounts. The invitation checks cover administrator-only
world commands, policy reload, revoked travel, safe arrival, single-use acceptance
and joining by sitting. Each loader records its settings and participant screens
at 320 x 240 logical pixels, alongside ordinary-table wind drawing and automatic
seat assignment, under its `build/smoke/evidence` directory.
