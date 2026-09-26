# MChjong architecture

## Module ownership

`main` owns feature development. This Minecraft 1.20.1 port has Fabric and
Forge loader subprojects under an aggregator root. They share gameplay,
presentation, assets and tests at this Minecraft API level.

* `engine`: Minecraft-independent mixed Java/Kotlin domain. Java retains the
  stateful `Game` orchestration, simple records/DTOs and the JVM interop shim for
  mahjong-utils internals. Kotlin owns algorithmic and value-oriented helpers
  where its collection and null-safety model materially reduces boilerplate,
  including tile identity/set composition, wall layout, visible-tile accounting,
  legal-action derivation, hand analysis, scoring, bot evaluation helpers and
  replay-format transformation. Kotlin APIs called from Java keep ordinary JVM
  entry points (`@JvmStatic`, `@JvmField`, `@JvmRecord` or explicit fields where
  required), so Java orchestration does not need Kotlin-specific call shapes. The
  engine Shadow archive embeds and relocates mahjong-utils, Kotlin and kotlinx,
  so neither loader requires a Kotlin language mod at runtime. Engine production
  Java and Kotlin bytecode targets Java 17, allowing the same domain artifact to
  serve the 1.20.1 and 1.21.1 Minecraft profiles. The build and existing test suite
  use the project's Java 21 toolchain.
* `common`: blocks, seats, server authorization, private snapshots, rendering,
  world-anchored interaction and translations for a Minecraft build profile.
  Both loaders for that profile compile these Java sources.
* `fabric/src/main/java`: Fabric registration/networking only.
* `forge/src/main/java`: Forge registration/networking and client extension binding.
* `common/src/smoke`: shared loader smoke harnesses; each loader keeps only its
  lifecycle adapter and metadata in its own `src/smoke`.
* `common/src/ponderData`: shared native-NBT Ponder generator source, compiled
  independently by both loader projects.
* `art`: deterministic packing of native face-preset images, client model
  descriptors, plus a separate server-data generator. Source-image resampling
  runs at build time; each supplied atlas includes its eight flower designs.

`compat/1.20.1` is a version-adaptation branch, synchronized from stable batches
on `main`. It targets Fabric and Forge. Features and engine changes originate on
`main`; the compatibility branch only changes Minecraft APIs, loader adapters,
metadata and version-specific resources. Quilt consumes the matching Fabric JAR.
Artifact names include both loader and Minecraft version to keep releases distinct.

## Networking and authority

`PayloadPackets` is the outgoing wire boundary. Fabric and Forge bind shared
payloads to their respective channels. All receivers dispatch to authorized server handlers
on the game thread. Forge's client-only item accessor binds the shared renderer to
Forge's item extension field without importing loader types into shared items.

`RuleSet` defines named presets; `RuleConfig` is the complete immutable, validated
snapshot used by the engine, public views, saves and native replays. `RuleOption`
defines field bounds, translation keys, categories and preset defaults. Runtime
logic reads individual settings instead of branching on a preset identity.
`TableRulesPayload` carries a bounded proposal and the current table identity and
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
wind permutation is persisted server-side; `RoomView` sends only revealed winds,
available choices, host seat, seated/away/disconnected presence and bot difficulty. `PlayerState`
follows a participant through seat reassignment. Mount presence is transient and
is reconstructed from `SeatEntity` passengers; it is never accepted from a client
or a saved room. Nearby room members retain preparation controls while relocating,
but active-game actions and private hands require the correct physical seat.
Physical dismount in the lobby releases room membership immediately. During an
active match it retains membership with a five-second grace period; expiry or a
lost server connection enables temporary win/pass/tsumogiri automation while another
human remains seated, without changing personal AutoPlay settings or spending the disconnected player's clock.
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
adapter authorizes recruitment through the companion's participating owner,
validates actual stools and mounts, and synchronizes companion presence. Missing
companions release lobby membership after the presence grace period; during play
a training bot continues their place. The maid extension in `compat/maid` uses
the maid mods' task, core-brain and typed task-data APIs. Fabric discovers it via
the Orihime extension entrypoint; Forge uses the maid extension annotation.
Seat vehicles save their passengers with the chunk, including maid companions.
The saved dimension, block position and table UUID restore missing mounts
without loading chunks. No optional maid code is loaded by the base entrypoints.

## Optional integrations

The optional `compat/create` workshop shares its item transformations through
`MahjongSupplies` and its recipe/viewer/storyboard adapters through `common`.
Only registration, exact-NBT ingredients and native inventory transactions live
in the loader-specific `CreatePlatform` classes. Create owns machine timing,
filtering, consumption and output routing. Recipes and mixins are activated only
when Create is installed; no GUI state or duplicate container rules are used.

`compat/recipes` creates executable display examples from the loaded recipe
manager. Every output and cycling input is checked through the source recipe's
`matches` and `assemble` methods. Marking reagents and the table-upgrade pattern
are shared with `SupplyCraftingRecipe`; `MahjongSupplies` remains responsible for
NBT and container transformations. `SupplySubtype` uses an immutable stack
snapshot and Minecraft NBT equality for recipe-relevant identity.

The independent `compat/jei`, `compat/emi` and `compat/rei` packages contain
viewer entrypoints and rendering adapters. Their APIs are compile-only;
the chosen development profile supplies the complete viewer at runtime. Screen
boundaries come from the native container screens themselves. See
[COMPATIBILITY.md](COMPATIBILITY.md) for profiles and validation coverage.

`MahjongUi`, `MahjongButton`, `MahjongSlider` and `MahjongEditBox` share the
project's presentation vocabulary without replacing native input machinery.
`MahjongBoxMenu` has its own registered type on both loaders, with ordinary slot
and carrier-index synchronization. `MahjongBoxScreen` reads that menu to paint
inventory wells and a packing summary; it never writes stored components.
Face printing uses a bounded cosmetic-ID payload tied to the current menu. The server validates the complete
136/144-tile input, then commits all tile slots and dye
consumption together. Tile, point-stick and dye compartments have distinct
native insertion ranges. `TileFacePreset` is an immutable resource-ID component;
ZIPs in `config/mchjong/presets/faces/` and `config/mchjong/server-presets/faces/`
supply the client selector.
Server ZIPs are loaded at startup and their individual
tile images are sent to joining players; clients assemble render atlases. An
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
with hidden sentinels **before serialization**. `HandVisibility` is saved per room;
the host can change it during preparation, clearing readiness. OPEN reveals and
lays down hands, ALL reveals upright faces to everyone including unseated viewers,
RIICHI reveals upright faces to viewers who have declared riichi, and SELF keeps
concealed faces private. Settlement exposure remains public in every mode.
`WorldSettings` supplies the administrator-controlled invitation teleport policy
per world save, across dimensions. That world policy is transient in `Game`.
`RoomView` publishes room ownership and world capabilities separately from tile
state. The World settings UI uses the server-advertised administrator command
tree to enable controls and submits the existing permission-checked commands;
only synchronized table snapshots update the displayed policy values.
Ownership follows a UUID, not the lowest numbered human seat.
Persistent server NBT is separate
from the client update tag. A table is not a global singleton.

The seated overlay projects the actual 3D table, with `TableSettings` supplying
the matching eye and FOV to world rendering and picking. `SeatedCameraState` owns
seat-local distance, height, yaw/pitch, target translation and interpolated inspect
progress. `SeatedCamera` bridges native free look and the loader tick lifecycle;
`TableKeys` supplies registered, rebindable actions to both loaders. Immersive play uses an
opaque GUI surface: `ImmersiveTable` builds recipient-safe tile solids and projects
the cloth, standing hands, rivers and public melds through `TableProjection`.
`TableBoard` supplies the information and animation anchors, while `TableHand`
supplies the private clickable hand. The viewer's melds lie flat on the immersive
table at their right-hand corner. The compact replay diagram
retains its separate flat layout. Neither world visibility nor camera orientation
controls the immersive camera. Guide anchors and input use GUI coordinates. Ordinary-table handling
uses the existing server-issued action buttons; seated play retains physical
gestures. Rendering is read-only and cannot advance play.

`PlayerPortrait` draws Minecraft's cached player-list skins before names in table,
room and settlement views. Missing player-list entries use the native default
skin, practice bots use a distinct shared-palette robot, and maid companions
use the maid mod's default Reimu icon. Companion model names resolve on the client.
Portrait rendering does not add network requests or store skin data in engine snapshots.

`TenpaiHints` caches structural waits per concealed hand, meld set and discard
kind, separately from snapshot-based availability. `VisibleTiles` deduplicates
physical IDs from the viewer's hand, rivers, melds, extracted norths, indicators
and pending declarations. Training bots share this accounting. Opponents' concealed
hands are ignored even when room hand visibility reveals them. `TableHints` renders
this information only when the local, default-off convenience preference is on;
no private information or new request type is added to the protocol.
Training decisions layer `BotAnalysis` (cached shape and bounded development),
`BotValue` (legal scoring and payout scenarios), `BotYakuPotential` (gradual,
copy-aware incomplete-hand routes), and `BotDefence` (public per-opponent
evidence) beneath `TrainingBot` action selection. `HandBonuses` and call-discard
restrictions are shared with engine execution. Recipient-only furiten and
riichi-han fields support exact self-state simulation. See [BOTS.md](BOTS.md)
for the search boundary and opt-in paired comparison command.

`TableAnimation` tracks recipient-safe snapshots by table identity, hand number,
and viewing permission. Visible physical tile identities follow hand/river/meld
transitions; hidden slots never acquire guessed identities. Wall assembly and
packet dealing reconstruct hidden source poses without sending private wall data.
Interrupted transitions start from their sampled pose, and repeated snapshots do
not restart motion. Changing viewer permissions clears the presentation history.
Training opponents receive an opening grace period; the engine never waits for
client animation callbacks. `TilePicking` clips a camera ray against the same
animated oriented tile box used by the renderer.

`TableView.Handling` publishes wall-build bits, the next physical source slot
and packet size, plus the two public dice faces and dealer-held status. `TableDice`
shares those faces across physical cubes, native pickup focus and compact hover
equations. Dice randomness and wall opening remain server-owned.
`TableHandling` derives legal physical targets and drop regions
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

`TableResults` is a separate, narrated single-screen receipt widget. It uses compact
hands, yaku columns and point tables rather than a scroll viewport. Multiple ron
winners have a mouse/keyboard selector. It displays server-authored deltas and final scores without recalculating settlement or
inventing a private tie-break order. Input stays in `TableScreen`; requests are
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
`SETTLEMENT_DONE` action; once all seated humans finish, `Game` shortens the
remaining hand stage to a 200-tick reading tail. This acknowledgement cannot
change points or advance the stage immediately. Bots need no acknowledgement;
the fallback still expires if a client never acknowledges. A seated player's
explicit skip request can advance the stage. Match settlement then adds a
separately skippable 200-tick final-standings stage before restoring the roster.
`RoomView.settlementTicks` synchronizes the remaining duration; the saved decision
age preserves it across reloads. `TableScreen` switches to final standings at the
stage boundary, including when opened partway through settlement.

`TableLobby` groups player count, matching rule presets, rule details, visibility,
clocks, invitations and participants on one page. The top toolbar offers individual
leave and host-only dissolution. `TableHud` keeps player summaries along the screen edge and puts
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
corner, beside the hand at the same depth. Extracted norths form two short rows
to the left of the hand, clear of the adjacent player's corner. The table's
two-slot inventory stores up to two cases inside the furniture.
Rivers pack six visible tiles per row, close gaps
left by calls and account for the width of sideways riichi discards. Hiding rivers
is a local rendering preference; it also forces the remaining-wall count and
current claimed-tile preview to remain visible, without changing game records.

`TableControlPayload` carries administrative controls separately from tile-action
indices. Both loaders use the same server authorization: loaded table, physical
seat, table identity and current decision or vote token. In the lobby only the host
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

Optional Create machine adapters live in Fabric's and Forge's `compat/create`
packages, sharing inventory-independent operations through `common`.
`MahjongSupplies` owns component-preserving red-five conversion, stick marking,
batch back coloring, default-Kansai full-set printing and atomic box packing. Crafting and
Create both delegate to those pure transformations. Native Create machinery
executes immutable snapshot recipes with exact-NBT ingredients; the
integration only adapts inventory admission, recipe selection and output
capacity at its machine boundaries. Fabric uses transactional storage, while
Forge uses item capabilities. Create data is generated separately for each
loader profile. See [Create workshop](CREATE.md).

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

`TimeControl` is enforced entirely in `Game`: per-hand reserves and fresh decision
allowances are independent for each active responder. Client interpolation and
warning sounds have no authority over deadlines. Lobby changes require the host
and invalidate ready votes. `TableInvitations` binds expiring requests to player
and table UUIDs; acceptance rechecks seating, distance, loaded chunks and phase.

`TableAudioEvents` is a pure snapshot-to-cue transformation, while `TableAudio`
owns client effects and recorded voice playback. Voice ZIPs use the client and
server `voices/` directories; the client decodes them through Minecraft's sound
engine and enforces an eight-second limit. No game logic depends on an audio
completion callback. See `AUDIO.md` for the recording contract.

## Replay storage and export

Replay recording and playback live in the Minecraft-independent engine.
`ReplayStore` handles bounded atomic files, searchable indexes and per-player durable
deletion markers; `ReplayServer` handles
permissions and commands, and `ReplayTransfer` handles bounded reassembly.
The viewer never feeds recorded actions back into a live `Game`. `TenhouReplay`
is the only export encoder; it consumes completed records and does not rerun
scoring. See `REPLAYS.md` for the storage layout and interchange details.
