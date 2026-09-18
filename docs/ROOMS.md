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
before play. Room settings list other human participants as ownership-transfer
targets; `/mchjong host <player>` performs the same transfer. The successor must
be participating at that table. Leaving a waiting room transfers ownership to
a remaining human.

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
