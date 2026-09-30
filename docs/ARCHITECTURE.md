# MChjong architecture

## Module ownership

The repository keeps Fabric, Forge and NeoForge feature development together on
`main`. The root project is an aggregator; `fabric`, `forge` and `neoforge` are
peer loader subprojects. The loaders share the same
gameplay, presentation, assets, and tests wherever their target Minecraft API
allows it.

* `engine`: Minecraft-independent mixed Java/Kotlin domain. Java retains the
  shared `TableSession` room lifecycle, rule-specific `RiichiSession` and
  `McrSession` ownership, active `RiichiGame` and `McrGame` orchestration,
  simple records/DTOs and the JVM interop shim for
  mahjong-utils internals. Kotlin owns algorithmic and value-oriented helpers
  where its collection and null-safety model materially reduces boilerplate,
  including tile identity/set composition, wall layout, visible-tile accounting,
  legal-action derivation, hand analysis, scoring, bot evaluation helpers and
  replay-format transformation. Kotlin APIs called from Java keep ordinary JVM
  entry points (`@JvmStatic`, `@JvmField`, `@JvmRecord` or explicit fields where
  required), so Java orchestration does not need Kotlin-specific call shapes. The
  engine Shadow archive embeds and relocates mahjong-utils, mcr-mahjong, Kotlin
  and kotlinx. Each loader embeds that self-contained artifact without requiring
  an external Kotlin language mod or MCR library. Engine production
  Java and Kotlin bytecode targets Java 17, allowing the same domain artifact to
  serve the 1.20.1 and 1.21.1 Minecraft profiles. The build and existing test suite
  use the project's Java 21 toolchain.
* `common`: blocks, seats, server authorization, private snapshots, rendering,
  world-anchored interaction and translations for a Minecraft build profile.
  Both loaders for that profile compile these Java sources.
  `BotServiceClient` discovers external Bot IDs and exact preset support from
  the administrator-configured service, sends engine-owned `BotPosition`
  snapshots asynchronously, and applies only validated action indices on the
  server thread. Service failures are synchronized as room status.
* `fabric/src/main/java`: Fabric registration/networking only.
* `forge/src/main/java`: Forge registration/networking and client extension binding.
* `neoforge/src/main/java`: NeoForge registration/networking only.
* `common/src/smoke`: shared loader smoke harnesses; each loader keeps only its
  lifecycle adapter and metadata in its own `src/smoke`.
* `common/src/ponderData`: shared native-NBT Ponder generator source, compiled
  independently by both loader projects.
* `art`: deterministic extraction and packing of native face-preset images, client model
  descriptors, plus a separate server-data generator. Source-image resampling
  runs at build time; each supplied atlas includes its eight flower designs.

`compat/1.20.1` is a version-adaptation branch, synchronized from stable batches
on `main`. It targets Fabric and Forge. Features and engine changes originate on
`main`; the compatibility branch only changes Minecraft APIs, loader adapters,
metadata and version-specific resources. Quilt consumes the matching Fabric JAR.
Artifact names include both loader and Minecraft version to keep releases distinct.

## Hand analysis boundaries

`Meld.Type` names physical structures: sequence, triplet, open quad, concealed
quad and added quad. The last preserves the original triplet's supplier and
fourth-tile provenance. Riichi declarations map explicitly at the `RiichiAction`
boundary; library notation belongs to `MahjongUtilsInterop`.

`McrAction` is the MCR declaration contract. `WIN` obtains its draw/claim method
from the current game position. `MELDED_KONG` takes three owned tiles in a discard
reaction, or one owned tile when supplementing a public triplet on a draw turn.
The latter keeps the original triplet and fourth tile pending until robbery
responses finish. MCR save and session records accept only their current format.

`RiichiHandAnalyzer` owns the Riichi scoring and shape adapter used by `RiichiGame`,
legal actions, hints, replays and bots. `McrHandAnalyzer` is the sole production
boundary to mcr-mahjong. Its public methods accept engine tile IDs, `Meld` values
and JDK collections, and return ordinary kind sets or `McrHandScore` records.
Explicit named mappings translate all 34 kinds, meld shapes, called chow positions,
suppliers and winds. Physical IDs are validated and deduplicated before conversion.

The engine declares `top.skyeyefast:mcr-mahjong:0.1.0` from Maven Central and
relocates its `top.skyeyefast.mcr` package to `top.skyeyefast.mchjong.internal.mcr`.
The library's MIT license and upstream attribution remain in the bundled archive.
`RiichiGame`, `RiichiRules`, `RiichiView` and `RiichiAction` own the Riichi match contract.
`McrGame` independently runs four-player MCR matches through engine-owned actions.

MCR analysis takes the concealed hand before drawing or winning:
`concealed.size() + 3 * melds.size() == 13`, with the winning tile supplied
separately. Owner/supplier seats use `0..3`; seat/round winds in `McrWinContext`
use the `Tile.EAST`, `SOUTH`, `WEST` and `NORTH` kind constants. Structural waits
exclude fifth copies already owned in the concealed hand and fixed melds.

`McrWinContext` separates the last wall tile from the last physical copy and
distinguishes kong replacement from robbing a kong. Flower replacement alone
is not a kong event. Its flower count maps directly to the library context.
`McrHandScore` retains the library's total fan, non-flower fan, minimum
qualification and named fan entries, including awarded subtotals and the
mixed-kong marker. The library owns shanten, winning shapes and fan calculation;
these records carry scoring facts independently of Riichi `HandScore` and table
payments.

## Physical tile domains

`Tile.kind` covers the 34 ordinary kinds. `FlowerTile` assigns one physical ID to
each of Spring, Summer, Autumn, Winter, Plum, Orchid, Bamboo and Chrysanthemum;
`Tile.mcrSet` combines those eight identities with 136 ordinary tiles. Flowers
have their own `McrPlayerState.flowers` area and are included by
`McrPlayerState.physicalTiles` accounting, separately from concealed tiles.
`RiichiPlayerState` tracks extracted norths and Riichi-only declarations.
Riichi rivers use `RiichiDiscard` for declaration and draw provenance.
MCR rivers use `McrDiscard` without Riichi declaration state.
A hand reset clears the corresponding rule-specific zones.

`McrWall` owns 144 fixed physical slots, with front draws and tail replacements.
A drawn flower moves to the player's flower area. `McrGame` issues a
`REPLACE_FLOWER` decision to its owner; each accepted declaration takes exactly one
tail tile, issuing another decision if it is a flower. Exhaustion returns
`Tile.ABSENT` and retains flowers already collected. The wall's conservation check covers the remaining physical wall and
all player-owned zones. The Riichi `Wall` independently owns its 136/108-tile
composition, 14-tile dead wall, replacement slots and dora/ura indicators.

The Minecraft item boundary uses `McrDeck` and `TableEquipment.mcrStock` to
select a complete, uniform 144-tile subset from one case and supply the physical
identities to `McrSession.configureEquipment`. Selection is read-only and keeps flower
item faces separate from ordinary analysis kinds. See [Supply data](SUPPLIES.md#mcr-stock-boundary).

`McrWallLayout` owns four walls of eighteen stacks, with upper/lower slots in
each stack. Columns run from the owner's right to left; clockwise traversal
visits walls in the opposite order to player turns. `McrOpening` records both
two-die rolls, the second roller and the first drawable stack after the counted
break. The first roll counts the dealer as one to select the second roller;
the sum of both rolls counts stacks from that roller's right end, continuing
clockwise onto the next wall when the count exceeds eighteen. Shuffling and
both rolls use the server-owned seed.

`McrWall.takeRaw` takes a named physical slot for initial packets and the dealer's
jump. `drawRaw` takes the next occupied front slot and `replaceRaw` takes the next tail slot. Independent front and back
cursors skip taken slots; both traversals take a stack's upper tile before its
lower tile. Tile identities remain in their physical slots until taken.
`McrSettlement` keeps normal wins and exhaustive draws in its `Result` contract;
below-minimum declarations produce separate `Penalty` events. Self-draw charges
each opponent eight plus total fan points; discard wins charge the discarder that
amount and the other opponents eight each. A wrong-win penalty transfers ten
points to each opponent, independently of hand results.

## MCR match orchestration

`McrGame` owns one fixed four-player, sixteen-hand match. Seats are indices in
turn order, with seat zero as the opening dealer. Each completed hand advances
the dealer once. Four hands advance the prevalent wind, and the sixteenth hand
ends the match. Points start at zero and retain both win payments and penalties.

Construction follows `McrWallLayout.initialDeal`: three four-tile packets to each
player, then the dealer takes the first and third remaining stacks' upper tiles.
South, West and North take the next available front slots. In traversal indices,
the final five raw takes are 48, 52, 49, 50 and 51; the next front take is 53.
Only after these 53 physical tiles have been allocated does the game expose
flowers. `INITIAL_FLOWERS` issues replacement decisions in East, South, West,
North order, completing each seat's chain before advancing. Ordinary and kong
draws that encounter a flower enter `REPLACE_FLOWER`; the automatic table takes
each replacement from the tail. Pending hand deficits and the phase/turn survive
save and restore without consuming another tile. The game tracks the actual draw origin separately from the
end of the wall; a flower replacement is distinct from a kong replacement,
including a flower drawn after declaring a kong.

`McrLegalActions` derives the current `McrAction` choices. `McrGame.act` accepts a
seat, the current decision token and one issued action index. A reaction window
collects each eligible seat's response once before arbitration: qualifying wins
take precedence over pung/kong, then chow. Simultaneous qualifying wins select
the nearest seat after the supplier, independently of response arrival order.
An added kong retains its original pung and the fourth tile in the owner's hand
until the robbing window closes. A winning claim transfers the physical tile to
the winner's hand. Ordinary claimed discards remain historical aliases; robbing
a kong does not create a discard. The winning result also references the same
tile without creating another owned copy.

`McrGame` is the source of `McrWinContext`: seat and prevalent winds, draw/claim
method, last-wall status, kong/robbing origin and collected flower count all come
from its current state. Last-copy detection deduplicates actual public river and
exposed-meld identities, excluding the winning tile and concealed information.

A structural score produces a win action even below the eight-point minimum.
The declaration then either settles the hand or appends a separate wrong-win
`Penalty` and sets the MCR-only `winForbidden` state for that seat. The hand
continues and ordinary tile actions remain available. Each new hand clears the
stop-win flags while preserving points and the match's penalty events.
Exhaustive draws have zero payment. `result()` describes only the completed hand;
`penalties()` describes the independent penalty events.

The host starts with `new McrGame(seed)`, inspects `actions(seat)` and submits
`act(seat, decision, index)`. `nextHand()` advances only from `HAND_END`;
`MATCH_END` is terminal. Hand, meld, river and flower accessors are immutable
private-engine inspection values. Recipient authorization and redaction belong
to the Minecraft-facing boundary, not these accessors. `validate()` checks hand
sizes, drawn-tile aliases, all 144 physical identities and zero-sum points.

### MCR persistence

`McrGame.save()` produces the immutable, private `McrGameState` record.
It contains the current-format identifier, future-wall seed, revision and decision,
hand position, physical wall slots, both rolls and break, front/back cursors, physical player zones, stop-win flags,
draw provenance, pending added kong, submitted responses, penalties and hand result.
`McrCodec.save(game)` encodes that record; `McrCodec.restore(json)` decodes it
and constructs a game without dealing or applying any payment again. The codec
uses the engine's embedded Gson and explicit win/draw tags for settlement results.
All record fields are required, and incompatible formats or invalid data are rejected.
The MCR game format is 5 and the session format is 7. Wall validation checks upper-before-lower occupancy and that
each cursor points to the next occupied slot in its own traversal.
The JSON boundary limits input to 65,536 characters and sixteen nesting levels,
rejects duplicate fields and checks numeric/boolean types before binding records.

Restoration checks physical conservation, hand and meld structure, wall bounds,
claim ownership, penalty/stop-win consistency and completed winning results.
Legal actions are regenerated from the restored position. Previously submitted
responses must still be valid and remain submitted; a completed reaction window
cannot be restored as pending. Restore advances revision and decision so the saved
decision token cannot be reused. A partial response advances only revision,
leaving the shared decision valid for the other responders.

The save record is separate from recipient data. Restore failures are reported
to the storage caller; the codec never substitutes a new match.

### MCR recipient views

`McrGame.view(seat)` builds an immutable `McrView` before serialization.
The engine host resolves the authorized participant seat; `-1` requests the
unprivileged spectator view. Ordinary concealed hands and drawn identities are
visible only to their owner. Concealed kongs use four hidden sentinels for other
recipients during play and become public at `HAND_END` or `MATCH_END`. A normal
winning result exposes that winner's hand, while other hands and every remaining
wall tile stay hidden. Wall slots retain only
hidden/absent occupancy in fixed physical order; public opening metadata exposes
the dice, second roller and break, not the seed or tile identities.
Flowers, rivers, exposed melds, points, stop-win flags,
declared claim tiles and penalty events are public.

Only the recipient receives their legal actions, engine-calculated minimum-fan
win qualification and submitted-response status.
Other players' action choices and responses are absent from the data model.
The public focus contains the offered discard or added-kong tile, not another
player's response. Below-minimum structural wins remain issued actions; a
subsequent wrong-win event updates points and the stop-win flag independently
of the hand result.

`McrCodec.encodeView(view)` and `decodeView(json)` use the view contract rather
than the private save contract. Both codecs bound document size and nesting,
require complete typed record fields and reject duplicate fields. View
construction enforces concealed-data redaction, so private state is not a valid
view document. Snapshot revision reflects partial-response changes without
invalidating the other players' shared decision token.

### Shared rooms and rule sessions

`MahjongVariant` selects one of the two built-in runtimes. `TableSession` owns
the table UUID, host, participants, seats, readiness, observed presence, variant,
request incarnation, exit controls and room lifecycle. `RoomSeating` owns the
concealed wind lottery. `TableRoomView` projects this state and recipient-specific
`RoomAction` choices for both rules. `TableSession.actRoom` resolves only issued
room-action indices against the table, incarnation and current room decision.
A variant change creates a new concrete session with the target variant's capacity,
retaining eligible human seats and fresh preparation state. `RiichiSession`
owns Riichi room rules, equipment, visibility, bot choices, rewards, replay queue
and match lifecycle. It creates one `RiichiGame` when play begins and clears it
on return to the lobby. `RiichiGame` has only active match phases; its private
actions, wall, scores, replay recording and settlement belong to that match.
`TableRoomView` contains only common room state. `RiichiRoomSettings` projects
Riichi rules and lobby configuration; `RiichiView` projects only an active match,
including private actions, convenience hints and settlement timing. Lobby
updates carry no `RiichiView`.

`McrSession` owns a complete 144-tile stock, `McrGame` and completed-hand
acknowledgements. The shared room starts the match once all four seats are ready;
human participants occupy their assigned stools and built-in bots are ready automatically.
Only a human participant mounted at the assigned
seat receives that seat's private view. A missing, displaced or ambiguously
occupied mount grants spectator access. MCR decision clocks continue for absent
participants while any human remains seated. Timeouts pass responses or discard
the drawn tile, falling back to the first legal discard. Win declarations remain
explicit. Forced draws and flower replacements advance after twelve server ticks.
Shared exit votes pause actions, clocks and completed-hand acknowledgements. The
last player to leave their stool chooses whether to keep the match paused or close
it; a retained match resumes when a player returns. Both the vote and leave decision
survive private table saves. Bots confirm completed hands automatically; completed
hands advance after all human confirmations or a 200-tick reading period.
Session saves retain decision age, remaining move
allowances, hand reserves and confirmations alongside the pending game decision.

`McrBot` consumes only its own `McrView` and selects an issued action index.
`McrHandAnalyzer.analyze` adapts the library's shanten and effective-tile analysis,
with physical-ID deduplication of owned and public tiles. Discards compare shanten,
remaining effective copies, effective kinds and then physical ID. Calls require
strict improvement after the best mandatory discard; kong evaluation conservatively
checks publicly possible replacements. The session schedules bots after twelve ticks,
without charging their clocks, and retains the same delay across partial responses
and saves. Shared `allowBots` policy controls preparation; active matches retain
their roster. See [Bot analysis](BOTS.md#mcr-built-in-opponent).

`TableHost` adapts one `TableSession` to Minecraft equipment and external bots.
`MahjongTableBlockEntity` owns only that host. It observes stools and authenticated
connections, validates world and equipment boundaries, sends recipient snapshots
and stores one private `session` field. `TableSessionCodec` owns the bounded
envelope and variant dispatch. Each rule codec serializes an explicit session
state containing the common room record and its own match record when play has
started. Runtime game objects are reconstructed by the owning session; participant
identity lives only in the room record. Presence is reconstructed from live
mounts after loading; a fresh incarnation and decision invalidate requests from
before restoration. The block entity stores the private session JSON as UTF-8
NBT bytes so completed replay queues fit beyond NBT's single-string limit.

`TableRoomActionPayload` carries a common room-action index. `RiichiActionPayload`
and `McrActionPayload` carry only their respective issued match-action indices.
`McrNextHandPayload` carries MCR completed-hand confirmation separately; the
server resolves the acting seat from the authenticated sender.
`RiichiViewPayload` and `McrViewPayload` carry the public
`TableRoomView` alongside their rule-specific recipient-safe projections.
Both view payloads send the last-player leave decision directly to its
unmounted recipient.
`RiichiVisibilityPayload` and `RiichiHandOrderPayload` carry Riichi-only
preparation and private-hand changes. `ClientRiichiNetworking` and
`ClientMcrNetworking` decode and apply their respective views.
`RiichiLobby` and `McrLobbyScreen` share room-control lookup and sending while
retaining their own rule settings. Match and settlement screens remain separate.

### MCR physical presentation

The engine's `McrWallLayout` contains stack/slot topology, traversal and initial
take plans, with no world coordinates or mesh dimensions. The presentation-owned
`McrTableScene` consumes `McrView` and projects one seat-local geometry through
the shared `TableGeometry.orient` rotation. It preserves physical wall slot
indices, original hand indices and discard history indices for picking and
animation consumers. Its complete built-wall projection contains only hidden
identities, and its live projection consumes only already-redacted data.

`McrRiverLayout` packs unclaimed discards into six columns. `McrMeldLayout`
positions flat public melds with a source-marking sideways tile, including all
four tiles of a supplemented triplet in one row. `McrFlowerLayout` reserves a
separate public flower area. `McrSceneRenderer` consumes the scene and `McrDeck`,
sharing tile meshes, materials and artwork lookup while retaining MCR geometry.
The dimensional and visibility contracts are in [Interface style](UI_STYLE.md#mcr-physical-layout).

`McrTableScreen` opens over the seated world and composes `TableViewController`
for camera input and view switching, and `TableCanvas` for the opaque 1280 × 800
canvas, letterboxing and pointer conversion. The world renderer consumes
`McrTableScene.build`; `McrImmersiveTable` consumes its public immersive rail and
river poses through `ImmersiveTable` primitives and `TableProjection`, with a
private `TableHand` in the foreground. `TilePicking` intersects the MCR piece's
actual scale and orientation.
Hand selection resolves only issued discard indices; other declarations use
native action buttons with localized names and tile previews. View changes retain
selection and leave the world camera pose intact. Settlement carries the selected
view into the next hand.

## Networking and authority

`PayloadPackets` is the outgoing wire boundary. Fabric and NeoForge use native
custom payload packets; Forge wraps the same shared payloads with its registered
channel encoder. All receivers dispatch to the existing authorized server handlers
on the game thread. Forge's client-only item accessor binds the shared renderer to
Forge's item extension field without importing loader types into shared items.

`RiichiPreset` defines named presets; `RiichiRules` is the complete immutable,
validated snapshot used by the engine, public views, saves and native replays. `RiichiRuleOption`
defines field bounds, translation keys, categories and preset defaults. Runtime
logic reads individual settings instead of branching on a preset identity.
`RiichiRulesPayload` carries a bounded proposal and the current table identity and
decision. Only the room host can apply it before play; clients retain a draft
until server acknowledgement. Rule changes clear all readiness and recheck boxes.
Preset metadata distinguishes supported table options from custom changes; the
editor shows options, read-only details and custom settings separately. The view
payload adds six public red-stock capability bits (three compositions for each
player count), not box contents. The server independently authorizes every
proposed red composition against stock.

Survival components, atomic box transformations and component-preserving recipes
live in `common/item` and `common/recipe`. `TableEquipment` stores two internal case
slots, removable cloth and four point-stick drawers, each with nine scoring slots
and a tenth non-scoring bust-stick reserve. Ordinary matches
reserve the initial drawer contents in private saves, constrain payments to table
currency, and restore those positions on match completion or exit. Its public projection
contains wood, cloth colors and tile appearance; inventory contents use authorized
native container synchronization. The two tables share one block
entity type and the referee. `engine/ManualHandling` adds explicit shuffle, wall,
packet and draw phases, with dealer-only dice pickup and roll after wall building,
without duplicating scoring or inventing client authority.
See [SURVIVAL.md](SURVIVAL.md) for the lifecycle and exact component contract.

`RoomSeating` owns the gathering, wind-drawing and positioning stages. Its concealed
wind permutation is persisted server-side; `TableRoomView` sends only revealed winds,
available choices, host seat, seated/away/disconnected presence and Bot choice.
Rule-specific player state stays in the active match, while `TableSession` keeps
the room roster through wind assignment. A lobby member retains their assigned
room seat while moving between stools; active private views require a matching mount.
Mount presence is transient and
is reconstructed from `SeatEntity` passengers; it is never accepted from a client
or a saved room. Nearby room members retain preparation controls while relocating,
but active-game actions and private hands require the correct physical seat.
Physical dismount in the lobby releases room membership immediately. During an
active match it retains membership with a five-second grace period; expiry or a
lost server connection enables temporary win/pass/tsumogiri automation while another
human remains seated, without changing personal `RiichiAutoPlay` settings or spending the disconnected player's clock.
Returning to the assigned stool restores control. Explicit lobby leave releases
membership; hosts may replace disconnected guests with bots after the grace period.
An idle lobby closes and releases all seats once every human is disconnected,
including after the dismount grace period expires.
An active match pauses its settlement, clocks and automatic actions as soon as no
human is seated. The last player to dismount normally chooses to keep the paused
match or end it; connection loss and invalidated seats keep it paused by default.
The match resumes when a human returns to an assigned stool.
The default-on personal automatic seating option requests relocation after seat
assignment or reopening a reserved table during preparation. Its payload contains only table position
and identity; the server derives the destination from membership and validates
the player, distance, stool and mount before moving them.

Physical companion players use an `entityBot` identity in the engine and public
seat view. Their UUID and display name survive wind assignment and difficulty
changes, while decisions use the same private-view training AI. The shared table
adapter authorizes recruitment through the companion's nearby living owner,
validates actual stools and mounts, and synchronizes companion presence. Missing
companions release lobby membership after the presence grace period; during play
a training bot continues their place. The maid extension in `compat/maid` uses
the maid mods' task, core-brain and typed task-data APIs. Fabric discovers it via
the Orihime extension entrypoint; NeoForge uses the maid extension annotation.
Seat vehicles save their passengers with the chunk, including maid companions.
The saved dimension, block position and table UUID restore missing mounts
without loading chunks. Optional maid classes load only when the maid mod is present.
Loader client adapters accept synchronized Mahjong mounts independently of the
maid's vehicle-follow preference, preserving the server-authorized seated pose.

## Optional integrations

`compat/patchouli` provides the installation recommendation in chat. Supported
loader client entry points check mod presence before offering the recommendation. Shared
Patchouli resources define `mchjong:guide`, using the ordinary language files
for chapter, entry and page text. The handbook reads client resources and has no
gameplay authority. The presence-guarded server adapter gives each player a starter
book once, recording recipient UUIDs in overworld saved data.

`compat/recipes` creates executable display examples from the loaded recipe
manager. Every output and cycling input is checked through the source recipe's
`matches` and `assemble` methods. Marking reagents and the table-upgrade pattern
are shared with `SupplyCraftingRecipe`; `MahjongSupplies` remains responsible for
component and container transformations. `SupplySubtype` uses an immutable stack
snapshot and Minecraft component equality for recipe-relevant identity.

The independent `compat/jei`, `compat/emi` and `compat/rei` packages contain the
respective official plugin entrypoints and rendering adapters. Their APIs are compile-only;
the chosen development profile supplies the complete viewer at runtime. Screen
boundaries come from the native container screens themselves. See
[COMPATIBILITY.md](COMPATIBILITY.md) for profiles and validation coverage.

`MahjongUi`, `MahjongButton`, `MahjongSlider` and `MahjongEditBox` share the
project's presentation vocabulary without replacing native input machinery.
The loader mod-list configuration entry opens the shared client-local personal
settings and preset screens; Fabric uses optional Mod Menu while Forge and
NeoForge register their native config-screen factories.
`MahjongBoxMenu` has its own registered type on both loaders, with ordinary slot
and carrier-index synchronization. `MahjongBoxScreen` reads that menu to paint
inventory wells and a packing summary; it never writes stored components.
Face printing uses a bounded cosmetic-ID payload tied to the current menu. The server validates the complete
136/144-tile input, then commits all tile slots and dye
consumption together. Tile, point-stick and dye compartments have distinct
native insertion ranges. `TileFacePreset` is an immutable resource-ID component;
ZIPs in `config/mchjong/presets/faces/` and `config/mchjong/server-presets/faces/`
supply the client selector.
`PresetDirectory` detects stable ZIP changes on client and server ticks.
Server ZIPs are loaded at startup and refreshed during play; their individual
tile images are sent to joining and already connected players; clients assemble render atlases. An
unavailable ID displays Kansai artwork. The
deck's face and back presets are synchronized as public appearance, independently of private
container contents.
`MahjongTableMenu` exposes two case slots through the same native container protocol;
its lifetime is bound to the specific idle table and nearby player. `MahjongTableScreen`
shows the selected complete set and cloth readiness without changing equipment.
`PointStickMenu` exposes all four drawer rows for hand payments, with withdrawals
authorized per player and practice-bot ownership. `PointStickScreen` shows stored
currency beside the referee's score; only ordinary inventory transactions move
the physical sticks. Signed 32-bit reference scores use paired native data slots.
Lifetime checks bind the menu to its player, current mount and exact table.
Both manual and automatic tables require a cloth and a box covering the selected
set to begin. Ordinary tables additionally require two stored dice and each seat's
fixed denomination kit. A dry-run stock plan validates all drawers and boxes;
match start commits complete top-ups before snapshotting the resulting drawers.
Rules without bankruptcy also require one reserved −10000 stick per player.
Surplus tiles remain untouched. Subset selection groups stock by
appearance, counts ordinary and red faces separately and never combines boxes.
The engine receives exactly 136 or 108 physical identities for the selected mode.
Follow [UI_STYLE.md](UI_STYLE.md) for controls, screen structure and visual checks.

The server owns the wall, hands, legal actions and settlement. Requests contain an
action index and decision token, never tiles or a claimed score. Snapshots are built for
each recipient: opponents' concealed tiles and unrevealed wall tiles are replaced
with hidden sentinels **before serialization**. `PlayerHandVisibility` is saved per
room; the host can change it during preparation, clearing readiness. ALL authorizes
all concealed hand identities in participant snapshots, RIICHI does so only for
participants who have declared riichi, and SELF keeps opponents concealed. `openHands` is a separate room option:
it physically lays down the hands and therefore makes their faces public in world
rendering. Settlement exposure remains public in every mode.

Non-participants use a separate server-side path. `SpectatorHandVisibility` is a
world policy: HIDDEN redacts concealed hands, FOLLOW_PLAYERS never exceeds the
room's participant visibility, and ALL authorizes every hand without changing what
participants may see. `spectatingEnabled` independently controls whether a
non-participant may open an active table's spectator screen.

`WorldSettings` owns administrator policy per world save, across dimensions. It
also controls invitations and invitation teleportation, Minecraft experience
rewards, replay availability, bots and companion participants, convenience hints,
and the thin custom-rule/forced-preset boundary. `RiichiSession` receives only the pure
runtime `WorldPolicy`; Minecraft UI and storage behavior stay outside the engine.
`RiichiViewPayload` synchronizes `WorldSettings.Policy` separately from tile and
room state. The World settings UI uses the server-advertised administrator command
tree to enable controls and submits the existing permission-checked commands;
only synchronized table snapshots update the displayed policy values.
Ownership follows a UUID, not the lowest numbered human seat.
Persistent server NBT is separate
from the client update tag. A table is not a global singleton.

The seated overlays project the actual 3D table, with `TableSettings` supplying
the matching eye and FOV to world rendering and picking. `SeatedCameraState` owns
seat-local distance, height, yaw/pitch, target translation and interpolated inspect
progress. `SeatedCamera` bridges native free look and the loader tick lifecycle.
`RiichiTableScreen` and `McrTableScreen` compose `TableViewController`, which owns
view mode, inspect/reset, held arrows, right-drag look/pan and wheel distance/height.
Each screen controls when switching is allowed and retains its own actions, HUD,
hand selection and settlement. `TableKeys` supplies registered, rebindable actions;
`SeatedTableProjection` derives screen anchors and picking rays from the rendered
camera. `TableCanvas` owns the fixed 1280 × 800 immersive transform and black bars,
so rendering and pointer input use the same coordinates.

`ImmersiveTable` owns tile solids, material/artwork lookup, contact shadows and
depth painting through `TableProjection`. `RiichiImmersiveTable` owns Riichi rails,
rivers, extracted norths, center device and draw/discard motion;
`McrImmersiveTable` owns the MCR scene adapter. Both compose the same primitives.
`RiichiBoard` supplies Riichi information and animation anchors, including the
separate flat replay layout. `TableHand`, `TileMesh` and `TilePicking` retain their
shared hand, mesh and intersection responsibilities. Riichi scene, animation,
HUD, action and result components use the `Riichi*` prefix; shared camera,
settings and session-exit components keep neutral names. Rendering consumes
recipient-safe views and remains read-only.

`PlayerPortrait` draws Minecraft's cached player-list skins before names in table,
room and settlement views. Missing player-list entries use the native default
skin, practice bots use a distinct shared-palette robot, and maid companions
use the maid mod's default Reimu icon. Companion model names resolve on the client.
Portrait rendering does not add network requests or store skin data in engine snapshots.

`TenpaiHints` caches structural waits per concealed hand, meld set and discard
kind, separately from snapshot-based availability. `VisibleTiles` deduplicates
physical IDs from the viewer's hand, rivers, melds, extracted norths, indicators
and pending declarations. Training bots share this accounting. Opponents' concealed
hands are ignored even when room hand visibility reveals them. `RiichiHints` renders
this information when the room host enables convenience hints during preparation;
the room view synchronizes that choice to every participant.
Training decisions layer `BotAnalysis` (cached shape and bounded development),
`BotValue` (legal scoring and payout scenarios), `BotYakuPotential` (gradual,
copy-aware incomplete-hand routes), and `BotDefence` (public per-opponent
evidence) beneath `TrainingBot` action selection. `HandBonuses` and call-discard
restrictions are shared with engine execution. Recipient-only furiten and
riichi-han fields support exact self-state simulation. See [BOTS.md](BOTS.md)
for the search boundary.

`RiichiAnimation` tracks recipient-safe snapshots by table identity, hand number,
and viewing permission. Visible physical tile identities follow hand/river/meld
transitions; hidden slots never acquire guessed identities. Wall assembly and
packet dealing reconstruct hidden source poses without sending private wall data.
Interrupted transitions start from their sampled pose, and repeated snapshots do
not restart motion. Changing viewer permissions clears the presentation history.
Training opponents receive an opening grace period; the engine never waits for
client animation callbacks. `TilePicking` clips a camera ray against the same
animated oriented tile box used by the renderer.

`RiichiView.Handling` publishes wall-build bits, the next physical source slot
and packet size, plus the two public dice faces and dealer-held status. `RiichiDice`
shares those faces across physical cubes, native pickup focus and compact hover
equations. Dice randomness and wall opening remain server-owned.
`RiichiHandling` derives legal physical targets and drop regions
from this recipient-safe view. Pointer gestures grab scattered tiles or the
source wall stack, preview their movement, and submit the existing action index
and decision token on a valid release. A token change or cancellation discards
the gesture. Keyboard focus is anchored to the same physical source. Collected
tiles turn face down toward the center as each participant acknowledges the
settlement. Rendering and drag previews remain read-only.

`WallLayout` maps logical draw indices to clockwise physical stacks, shared by
manual wall ownership, rendering, picking and animation. Players advance in the
opposite, counterclockwise seat order. Each hand uses two server-rolled dice to
count a wall from the current dealer, then skips that many stacks from its owner's
right end. The opening's left side starts the live wall; its right side holds the
dead wall. Three-player tables use the same counting across their three walls.
Live draws take the upper tile before the
lower; the dead wall is indexed from its opposite end. A replacement leaves its
wall slot empty while the last live tile becomes dead in place. Revealing dora or ura
does not change their physical layers. This follows the deal in the
[EMA Riichi rules](https://mahjong-europe.org/portal/images/docs/Riichi-rules-2025-EN.pdf).

`RiichiResults` is a separate, narrated single-screen receipt widget. It uses compact
hands, yaku columns and point tables rather than a scroll viewport. Multiple ron
winners have a mouse/keyboard selector. It displays server-authored deltas and final scores without recalculating settlement or
inventing a private tie-break order. Input stays in `RiichiTableScreen`; requests are
suppressed while one is awaiting a response and stale decisions are rejected by
the server. Riichi selection always uses the server's legal discard candidates.
Final standings also show the server's separate uma shares. The table queues
optional uma-based Minecraft experience changes for human players in its private
save and pays connected players once from the server thread.

`ScoreAnnouncements` supplies the same ordered rows to the receipt and narration,
using the scoring library's per-yaku han and public winning tiles for the four
bonus counts. Seat snapshots retain the server's double-riichi declaration for
action recordings. `ResultReadout` retains one timeline per observed hand across widget rebuilds.
It reveals each scored yaku and counted-dora row with its recording, then the
winner's points, then the applicable hand grade. Multiple winners run in order;
manual navigation completes the local readout without replaying it. The audio
engine bounds recordings and does not derive or modify any score.

Draw settlements retain their 200-tick timer. Winning receipts have a finite
server fallback derived from their recording count and the eight-second clip
limit. Seated human clients acknowledge completion using the server-issued
`SETTLEMENT_DONE` action; once all seated humans finish, `RiichiGame` shortens the
remaining hand stage to a 200-tick reading tail. This acknowledgement cannot
change points or advance the stage immediately. Bots need no acknowledgement;
the fallback still expires if a client never acknowledges. Each settlement stage
advances early when every human player confirms its skip action. Match settlement
then adds a 200-tick final-standings stage before restoring the roster.
`RiichiView` synchronizes the remaining duration and skip confirmations; the saved decision
age preserves the countdown across reloads. `RiichiTableScreen` switches to final standings at the
stage boundary, including when opened partway through settlement.

`RiichiLobby` groups player count, matching rule presets, rule details, visibility,
clocks, invitations and participants on one page. The top toolbar offers individual
leave and host-only dissolution. `RiichiHud` keeps player summaries along the screen edge and puts
long names and supplementary details in hover text. Action buttons stay along
the lower edge rather than covering the table center. The concealed run stays
centered whenever the right-corner melds leave enough space; otherwise it shifts
left by exactly the missing clearance, rather than centering in the remaining
space. Only an actual drawn tile occupies the separate draw slot; unused draw
and meld slots cannot push a waiting hand left. `MeldLayout` supplies the real
occupied width, including sideways calls and front-aligned added kans, to both hand
clearance and meld rendering.
The table reserves a 3 x 3 footprint with dimensions shared by placement,
colliders, furniture and seating. Melds are anchored at the owner's right-hand
corner, beside the hand at the same depth. Extracted norths form one continuous run
to the left of the hand, clear of the adjacent player's corner. The table's
two-slot inventory stores up to two cases inside the furniture.
Rivers pack six visible tiles per row, close gaps
left by calls and account for the width of sideways riichi discards. Hiding rivers
is a local rendering preference; it also forces the remaining-wall count and
current claimed-tile preview to remain visible, without changing game records.

`TableSessionControlPayload` carries shared exit requests, votes and last-player
leave decisions directly to `TableSession`. `RiichiControlPayload` carries
automatic play, convenience hints and open hands only for Riichi. These controls
remain separate from match-action indices. Every loader checks the loaded table,
table identity and current decision or vote token. Exit requests and votes require
the sender's physical seat; a leave decision requires the pending actor. In the
lobby only the host
can dissolve the room. During play, one human can end the table immediately.
Otherwise every human must agree, including reserved seats;
bots, spectators, duplicate replies and stale ballots cannot supply approvals.
Voting pauses play and its clocks without replenishing either time allowance.
A rejection or 30-second timeout resumes play, followed by a 30-second ballot
cooldown. Completing the vote releases seats and clears the unfinished hand;
already completed replay records remain available and no fake settlement is made.

## Supply transformations and verification boundaries

Test ownership, focused commands and screenshot acceptance are maintained in
[Verification](VERIFICATION.md). Registry IDs, persistence and finite recipe
identity are maintained in [Supply data contracts](SUPPLIES.md).

The optional client integration in `compat/ponder` registers three tutorials with
Ponder after a loader presence check. The scenes share gameplay furniture models,
component types and seating geometry inside Ponder's display worlds. Native NBT
generation supplies the same structure to both loader artifacts. See
[Ponder integration](PONDER.md) for the installed and base-client checks.

Survival checks use real server players, menus and levels in the shared smoke
source set rather than introducing null-world behavior into production code.
The ordinary-table client smoke projects real tile positions and sends pointer
drags for shuffle, walls, packet dealing and draws, followed by normal discard
and exit controls. `PointStickMenuSmoke` exercises native transfers, splitting,
drag distribution, swaps, close, distance and removal with exact item accounting.
Box menu lifetime is bound to the current menu and exact carrier
stack, with immediate vanilla container-component persistence. Dedicated tile,
point-stick and dye slots share vanilla click validation. Face printing previews
the entire 136/144-tile transaction, then commits it with exactly one ordinary
dye consumed; creative dye is retained. Presets are immutable item components.
Crafting rules stay in `recipe/`, and neither loader carries separate rules.
Face and back preset selections are applied together in one server-owned inventory
transaction, consuming one ordinary Mahjong dye when either changes; creative
Mahjong dye remains available.

Optional Create machine adapters live in NeoForge's `compat/create` package.
`MahjongSupplies` owns component-preserving red-five conversion, stick marking,
batch back coloring, default-Kansai full-set printing and atomic box packing. Crafting and
Create both delegate to those pure transformations. Native Create machinery
executes immutable snapshot recipes with exact-component ingredients; the
integration only adapts inventory admission, recipe selection and output
capacity at its machine boundaries. Create data is generated separately and
packaged only in that loader profile. See [Create workshop](CREATE.md).

Private equipment saves explicitly encode empty slots. Public block updates
omit those slots entirely and contain appearance only, so receiving an
appearance update cannot clear a server's box, game or private wall. Loading a
private empty save does clear those values. Loot copies only furniture
appearance components; removable equipment is dropped once by the server.

Minecraft-facing artifacts are version-scoped. The project provides dedicated
builds for targeted Minecraft releases, ensuring API compatibility at compile
time. Version differences stay at the Minecraft/loader boundary while the
`engine` remains independent of Minecraft.
The selected Minecraft, Java, loader, mapping and dependency versions live in
`gradle.properties`, and resource processing writes the matching compatibility
metadata into each artifact.

Tile faces and tile backs are independent materials. Back presets use ZIPs in
`config/mchjong/presets/backs/` and `config/mchjong/server-presets/backs/`.
Resource selection and reloads do not affect server rules,
tile IDs or private snapshots. See `ASSETS.md` for the resource contract.
Riichi stick presets use the corresponding `sticks/` directories. Their model
dimensions are bounded before transfer, while each player stores a personal
selection in the client TOML settings. The server broadcasts only selections
from its own stick presets; a client-only selection stays on that player's client.

`TimeControl` is enforced by `RiichiGame` and `McrSession`: per-hand reserves and fresh decision
allowances are independent for each active responder. Both use `TimeControl.Clock`
to spend move time before reserve. Client interpolation and
warning sounds have no authority over deadlines. Lobby changes require the host
and invalidate ready votes. `TableInvitations` binds expiring requests to player
and table UUIDs; acceptance rechecks seating, distance, loaded chunks and phase.

`RiichiAudioEvents` is a pure snapshot-to-cue transformation, while `RiichiAudio`
owns client effects and recorded voice playback. Voice ZIPs use the client and
server `voices/` directories; the client decodes them through Minecraft's sound
engine and enforces an eight-second limit. Server-authorized voice selections
are broadcast by player name; action cues carry their originating seat so each
listener resolves the speaker's voice. No game logic depends on an audio
completion callback. See `AUDIO.md` for the recording contract.

## Replay storage and export

Replay recording and playback live in the Minecraft-independent engine.
`ReplayMatch` holds shared identity, participants, variant, timestamps and
completion state; `RiichiReplay` and `McrReplay` own independent completed-hand
types. MCR records a complete physical opening and accepted server decisions,
then reconstructs read-only event frames with the MCR game rules. A completed
hand enters the existing archive queue once; the session save retains both
unfinished recording state and unacknowledged completed archives.
`ReplayStore` handles bounded atomic files, searchable indexes and per-player durable
deletion markers; `ReplayServer` handles
permissions and commands, and `ReplayTransfer` handles bounded reassembly.
The viewer never feeds recorded actions back into a live match. `TenhouReplay`
consumes completed Riichi records and does not rerun
scoring. See `REPLAYS.md` for the storage layout and interchange details.
