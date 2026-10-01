# Rooms, permissions and settings

The lobby directly exposes player count, rule preset, rule details, hand visibility,
time allowances, invitations and participants. The primary button fills empty seats
and starts seat preparation. Leave room and the host's Close room control stay
in the top toolbar. The Settings button groups additional controls into World, Room and Personal.
Administrators can edit world values in the World tab; other players can inspect them. Personal
display, input, camera and audio settings remain local to each player and are also
available through MChjong's mod-list Config button. Automatic
play preferences belong to the individual seated player and remain accessible
from the match overlay.

The room host can enable **Tenpai hints** in Settings → Room during preparation.
The room shares this choice with every participant. When enabled, the table
shows waits and unseen-copy counts from each player's authorized view.

## MCR Bot rooms

Select MCR with a complete 144-tile set, fill the empty seats with Bots, begin seat
preparation and ready at the assigned stool. The shared room controls also add or
remove individual Bots. One human and three built-in Bots can play all sixteen
hands. World `allowBots` controls Bot availability during preparation.

Bots confirm each completed hand automatically. Human confirmation advances early;
the server also advances after the reading period. Leaving the last occupied human
stool pauses the match, and returning resumes it. A sole human can close the match
directly; rooms with multiple humans use the shared exit vote.

## Sichuan Bot rooms

Select Sichuan with a complete 108-tile suited set. Both SBR 2025 and T/TFMJ 01—2024
support one human and three built-in Bots through all eight hands. Fill the empty
seats, or add/remove individual Bots, then begin seat preparation and ready at your
assigned stool. Sichuan has one built-in Bot choice.

Bots are ready automatically and confirm each completed hand. Human confirmation
advances early; the server also advances after the reading period. Leaving the last
human stool pauses Bots and the match. Returning resumes the saved decisions.
World `allowBots` controls preparation; running matches retain their roster.
Scores, final standings and replay participants include Bots. See
[Bot analysis](BOTS.md#sichuan-built-in-opponent) for their decision boundary.

## World policy

Each world save has `config/mchjong-world.toml`, shared by all dimensions and
tables in that save. Its initial contents are:

```toml
invitationTeleport = false
invitationsEnabled = true
spectatingEnabled = true
spectatorHandVisibility = "hidden"
allowConvenienceHints = true
allowExperienceRewards = false
deductNegativeExperience = true
maxExperienceChange = 5000
replaysEnabled = true
allowBots = true
allowCompanionPlayers = true
allowCustomRules = true
forcedPreset = "none"
```

Administrators with command permission level 2 can change these values in
Settings → World. The buttons display server-confirmed values and use the same
permission-checked commands available below:

```text
/mchjong world
/mchjong world invitationTeleport true
/mchjong world spectatingEnabled false
/mchjong world spectatorHandVisibility follow_players
/mchjong world forcedPreset tenhou_4
/mchjong world reload
```

Changes made by commands are saved atomically. Reload applies file edits. World
policy applies to active tables as well as new rooms. It governs server-wide
information access, Minecraft experience rewards, replay availability, server-side
participants and which room capabilities are allowed. `maxExperienceChange` caps
the absolute experience change from one match; `deductNegativeExperience` only
matters when experience rewards are enabled. `allowCustomRules` restricts rooms to
named presets, while `forcedPreset` can require one preset without copying its
individual mahjong options into world settings.

## Hand visibility

The host chooses Hand visibility directly in the lobby before play. The choice is
saved with that table, and changing it clears readiness:

- **Visible only to self:** each seated player sees their own hand. This is the default.
- **Visible to riichi players:** a player who has declared riichi can see other
  players' concealed tile identities in their authorized view. Other participants do not.
- **Visible to all players:** every participant sees the other participants'
  concealed tile identities in their authorized view.

**Open hands** is separate from information permission. When enabled by the host,
the physical hands are laid face up, so their faces are naturally public in world
rendering.

Hands revealed at settlement remain public. Spectators can stand beside the table
and inspect its world rendering, or interact with an active table to open its
overview when `spectatingEnabled` permits it. Their concealed-hand snapshots are
redacted by `spectatorHandVisibility`: `hidden` reveals none, `follow_players`
never exceeds the room's participant visibility, and `all` reveals every hand.
None of these values grants additional information to participants. Spectating
leaves participant seats available and grants no game actions.

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

Assignment keeps the host, personal preferences and bot choice attached to
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

## Computer players

Each Riichi bot can be set to Easy or Hard during any waiting-room stage. Automatic
tables also offer the external Bots discovered from the administrator's
[Bot Service configuration](BOTS.md#local-bot-service) when their player count
and rule preset match the room. Changing the roster or Bot choice clears human
readiness. Bots remain ready while humans reposition themselves.

The control in each empty or bot-occupied top seat card cycles through Easy,
Hard, compatible external Bots and Empty. External choices retain their stable
Bot ID in the room save. A service error appears on the affected seat card and
its hover details while that Bot waits for a decision. A physically seated human
cannot be replaced; their control is reserved for transferring room ownership.
Fill empty seats adds Easy bots.

Easy prioritizes current shanten, live improving tiles and retaining value, with
productive calls and basic defense against clear threats. Hard adds bounded
draw/discard development, including same-shanten improvement, weighted legal
winning value, and more detailed per-opponent attack/defense assessment. It can
trade a little immediate efficiency for better development or retain safe tiles
while advancing a valuable hand. Both compare legal calls, riichi, kans and north
extraction under the table's rules.

The built-in levels use only their own hand and public tiles. They do not read
opponents' concealed tiles, even when room visibility allows players to see
those hands, and do not inspect the wall seed. Their decisions use deterministic
heuristic evaluation.

## Invitations

Use Invite beside a vacant lobby seat to choose an online player. Invite player
in the lobby opens the same selector. You can also use `/mchjong invite <player>`, with an online
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
remaining server time. A seated player can skip either countdown from the settlement
header. Saved tables preserve the remaining time.

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
