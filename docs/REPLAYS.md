# Replays

## Viewing and exporting

Open **Replays** from the table overlay, or use these server commands:

```text
/mchjong replays
/mchjong replays 2
/mchjong replays 1 true "player name"
/mchjong replay <match UUID>
/mchjong replay <match UUID> delete
```

The index contains twelve matches per page. Search player names or a replay UUID,
and choose newest-first or oldest-first ordering. Search applies before pagination;
timestamps come from the match rather than filesystem modification time. In the
command form, `true` means oldest first and `false` means newest first. Select an
entry with the mouse or Up/Down, then choose **View replay** or press Enter. A double
click also opens it. **Delete** (or the Delete key while the list is focused) opens
a confirmation screen; Escape or Cancel does not change any records. Deletion
retains the active filter and sort order and returns to its first page.

The viewer's upper arrows change hands. Its lower controls
seek to the initial deal, step backward, play/pause at two steps per second,
step forward, or seek to settlement. The timeline allows direct seeking.
Left/Right step, Home/End seek and Space toggles playback. Wheel scrolling or
focusing the board and using Up/Down or Page Up/Page Down reveals every seat.

Each step shows all participants' recorded hands, draws, discards, calls,
extracted norths, riichi payments and revealed dora. The settlement frame adds
the original point changes, winning tile, yaku, relevant ura indicators and,
when the match has finished, final standings. Gold river borders mark hand
discards; sideways tiles mark riichi discards; dimmed tiles have been called.

The viewer provides read-only playback of matches recorded by this server.
Live games and their clocks continue independently during replay viewing.

Export is explicit: **Export Tenhou JSON** writes UTF-8 JSON to
`<game directory>/replays/mchjong/<match UUID>.json`, using an atomic replacement.
The chat shows the full path or the screen reports a failure. Re-exporting the
same match replaces that match's local export. No file is opened automatically
and nothing is uploaded to an external service.

## Privacy and persistence

The live table continues to send only recipient-redacted `TableView` snapshots.
An independent server recorder stores initial hands and actual committed actions.
Only when a hand settles is an immutable `ReplayHand` appended to its match.
The archive never contains an active hand, RNG seed or future wall.

Server records live at `<world>/data/mchjong/replays/<match UUID>.json`.
Small `by-player/<player UUID>/` indexes support browsing without exposing other
players' match lists. Fetches check the canonical match's human-participant IDs
in addition to its index; possession of a UUID or forged index is insufficient.
Spectators and training bots do not gain retrieval permissions. Administrators
with direct filesystem access can naturally read the server's files.

Deleting a replay removes only the requesting participant's reference. It never
revokes another participant's access and never deletes a previously exported local
file. Deletion checks the canonical record's participant IDs, not merely the index.
A small `.deleted` marker persists beside the player's index so a later finished
hand, a queued save or a server restart cannot recreate the deleted entry. Once
every human participant has deleted their reference, the canonical archive is
removed as well. These markers remain part of the world backup.

Each finished hand updates the archive atomically. Pending writes also remain
in the table's normal saved game data until all index updates succeed. An I/O
failure is logged and retried after sixty seconds; it does not acknowledge or
silently discard the queued record. Successful records survive table removal,
rematches, disconnects and server restarts. Normal world backups should include
the archive directory.

Retrieval is rate-limited per player. Transfers are separate from live snapshots,
split into bounded chunks, checked for identity/order, and reassembled only at
completion. An incomplete transfer expires after thirty seconds; disconnects
clear it. A canonical archive is limited to 8 MiB and transfer memory is bounded.
The client validates the recorded timeline before presenting it.

## Tenhou JSON format

Exports use the `/6` viewer's JSON interchange structure (`ver: "2.3"`). They are
**not** Tenhou's compressed XML `.mjlog` format. Format encoding follows the
[tensoul converter's primary implementation](https://github.com/Equim-chan/tensoul/blob/main/convert.js).
The implementation is independent and preserves MCjhong's actual rules and
payments rather than pretending a Mahjong Soul or M.League match used Tenhou rules.

The starting dealer becomes encoded seat zero. Sanma has an empty fourth seat,
zero-filled fourth score entries, no red manzu, and round numbering that skips
East 4, South 4, and so on. Tiles use 11–47 with red fives 51–53. Drawn-tile
discards use `60`; a riichi declaration prefixes its discard with `r`. Chi, pon,
open/closed/added kan and north extraction use `c`, `p`, `m`, `a`, `k` and `f44`.
Called-tile position preserves the caller/discarder relation.

Riichi declaration and payment are recorded separately: a declaration discard
can be won before the deposit is committed. Kan/north declarations robbed before
completion are retained without consuming tiles in native playback. Dora reveal
events retain their actual timing. Multi-ron exports preserve each winner's
payment vector; honba, riichi deposits and responsibility payments come from
settlement, not a second scoring pass. Dora, ura, red dora and extracted-north
bonuses are distinct entries. Abortive draws and nagashi have their own labels.
Final score pairs are emitted only for a completed match, with the original
return-point, placement and tie policy already applied by the engine.

Automated format tests cover tile codes, red fives, meld orientation, sanma padding,
seat rotation, declarations and draw labels. Full matches for every supported
ruleset are reloaded and replayed against the recorded hands, rivers, calls,
norths and point balances. The client smoke tests exercise the archive, fetch,
viewer, resizing and export button on both loaders. Import in every external
Tenhou-compatible viewer has not been verified; viewer-specific behavior may differ.
