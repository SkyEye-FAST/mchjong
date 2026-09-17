# MCjhong architecture

The repository keeps Fabric and NeoForge development together on `main` rather
than maintaining loader-specific branches. The root project builds Fabric;
`neoforge` is the NeoForge loader subproject. Both loaders share the same
gameplay, presentation, assets, and tests wherever their target Minecraft API
allows it.

* `engine`: Minecraft-independent Java domain model, with one small, typed Kotlin
  adapter to the MIT-licensed mahjong-utils scoring and hand-analysis library.
* `common`: blocks, seats, server authorization, private snapshots, rendering,
  world-anchored interaction and translations for a Minecraft build profile.
  Both loaders for that profile compile these sources.
* root `src/main/java`: Fabric registration/networking only.
* `neoforge/src/main/java`: NeoForge registration/networking only.
* `art`: deterministic rasterization of pinned riichi vectors and flower engravings,
  client model descriptors, plus a separate server-data generator. SVG rendering
  and source-font outline extraction run at build time.

Survival components, atomic box transformations and component-preserving recipes
live in `common/item` and `common/recipe`. `TableEquipment` stores two internal case
slots, removable cloth and four nine-slot point-stick drawers. Its public projection
contains wood, cloth colors and tile appearance; inventory contents use authorized
native container synchronization. The two tables share one block
entity type and the referee. `engine/ManualHandling` adds explicit shuffle, wall,
packet and draw phases without duplicating scoring or inventing client authority.
See [SURVIVAL.md](SURVIVAL.md) for the lifecycle and exact component contract.

`compat/recipes` creates executable display examples from the loaded recipe
manager. Every output and cycling input is checked through the source recipe's
`matches` and `assemble` methods. Marking reagents and the table-upgrade pattern
are shared with `SupplyCraftingRecipe`; `MahjongSupplies` remains responsible for
component and container transformations. `SupplySubtype` uses an immutable stack
snapshot and Minecraft component equality for recipe-relevant identity.

The independent `compat/jei` and `compat/emi` packages contain the respective
official plugin entrypoints and rendering adapters. Their APIs are compile-only;
the chosen development profile supplies the complete viewer at runtime. Screen
boundaries come from the native container screens themselves. See
[COMPATIBILITY.md](COMPATIBILITY.md) for profiles and validation coverage.

`MahjongUi`, `MahjongButton`, `MahjongSlider` and `MahjongEditBox` share the
project's presentation vocabulary without replacing native input machinery.
`MahjongBoxMenu` has its own registered type on both loaders, with ordinary slot
and carrier-index synchronization. `MahjongBoxScreen` reads that menu to paint
inventory wells and a packing summary; it never writes stored components.
`MahjongTableMenu` exposes two case slots through the same native container protocol;
its lifetime is bound to the specific idle table and nearby player. `MahjongTableScreen`
shows the selected complete set and cloth readiness without changing equipment.
`PointStickMenu` exposes all four drawer rows for hand payments, with withdrawals
authorized per player and practice-bot ownership. `PointStickScreen` shows stored
currency beside the referee's score; only ordinary inventory transactions move
the physical sticks. Signed 32-bit reference scores use paired native data slots.
Lifetime checks bind the menu to its player, current mount and exact table.
Both manual and automatic tables require a cloth and complete set to begin.
Follow [UI_STYLE.md](UI_STYLE.md) for controls, screen structure and visual checks.

The server owns the wall, hands, legal actions and settlement. Requests contain an
action index and decision token, never tiles or a claimed score. Snapshots are built for
each recipient: opponents' concealed tiles and unrevealed wall tiles are replaced
with hidden sentinels **before serialization**. The host can enable open hands in
the lobby; this reveals opponents only to seated participants, never spectators.
Changing that setting invalidates readiness and is forbidden after play starts.
Persistent server NBT is separate
from the client update tag. A table is not a global singleton.

The seated camera remains in the Minecraft world. The interaction overlay does not
draw a replacement 2D table; it projects actual 3D tile locations and connects legal
choices to them with elbow lines. Rendering is read-only and cannot advance play.

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
and packet size. `TableHandling` derives legal physical targets and drop regions
from this recipient-safe view. Pointer gestures grab scattered tiles or the
source wall stack, preview their movement, and submit the existing action index
and decision token on a valid release. A token change or cancellation discards
the gesture. Keyboard focus is anchored to the same physical source. Collected
tiles turn face down toward the center as each participant acknowledges the
settlement. Rendering and drag previews remain read-only.

`TableResults` is a separate, narrated single-screen receipt widget. It uses compact
hands, yaku columns and point tables rather than a scroll viewport. Multiple ron
winners have a mouse/keyboard selector. It displays server-authored deltas and final scores without recalculating settlement or
inventing a private tie-break order. Input stays in `TableScreen`; requests are
suppressed while one is awaiting a response and stale decisions are rejected by
the server. Riichi selection always uses the server's legal discard candidates.

The lobby selects four-player or three-player mahjong before offering matching
rule presets. `TableHud` keeps player summaries along the screen edge and puts
long names and supplementary details in hover text. Action buttons stay along
the lower edge rather than covering the table center. The concealed run stays
centered whenever the right-corner melds leave enough space; otherwise it shifts
left by exactly the missing clearance, rather than centering in the remaining
space. Only an actual drawn tile occupies the separate draw slot; unused draw
and meld slots cannot push a waiting hand left. `MeldLayout` supplies the real
occupied width, including sideways calls and stacked added kans, to both hand
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
seat, table identity and current decision or vote token. One human can end the
table immediately. Otherwise every human must agree, including reserved seats;
bots, spectators, duplicate replies and stale ballots cannot supply approvals.
Voting pauses play and its clocks without replenishing either time allowance.
A rejection or 30-second timeout resumes play, followed by a 30-second ballot
cooldown. Completing the vote releases seats and clears the unfinished hand;
already completed replay records remain available and no fake settlement is made.

Build: `gradlew.bat buildAll`. Loader-specific development runs remain
`gradlew.bat runClient` and `gradlew.bat :neoforge:runClient`.

`gradlew.bat :test` covers deterministic presentation and pointer geometry.
It also checks outward-facing tile winding, stable hand placement, compact rivers
and the mandatory remaining count. Engine tests cover exits, ballots, save reloads
and recipient privacy. Asset tests check all four language key sets, duplicate
keys, format arguments and literal translation references in production sources.
`gradlew.bat :runSmokeClient` runs a real Fabric integrated client/server, exercises
seating and discard packets, and captures settlement and animation screenshots in
`build/smoke/evidence`. Rare multi-winner and animation states use display-only
fixtures; they are not scoring-rule integration tests. The same harness runs with
`gradlew.bat :neoforge:runSmokeClient` and stores evidence under
`neoforge/build/smoke/evidence`. Both harnesses also create a real engine record,
archive it on the integrated server, retrieve it through commands and chunked
networking, render the replay timeline and click the Tenhou export button.
Development-only source sets contain the smoke adapters.
Both smoke clients leave the desktop cursor free, including when closing screens
or entering the world. A smoke-only mouse mixin prevents capture and recentering;
the harness checks the logical grab state and native cursor mode each tick.
NeoForge's dedicated-server integration tests also run in `buildAll`.

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
stack, with immediate vanilla container-component persistence. A narrow shared
stonecutter mixin adds component-aware cache invalidation to the native menu;
crafting rules stay in `recipe/`, and neither loader carries separate rules.

Private equipment saves explicitly encode empty slots. Public block updates
omit those slots entirely and contain appearance only, so receiving an
appearance update cannot clear a server's box, game or private wall. Loading a
private empty save does clear those values. Loot copies only furniture
appearance components; removable equipment is dropped once by the server.

Minecraft-facing artifacts are version-scoped. The project targets mainstream
releases from 1.20.1 onward, but does not declare one binary compatible with an
API range it was not compiled against. Version differences stay at the
Minecraft/loader boundary while the `engine` remains independent of Minecraft.
The selected Minecraft, Java, loader, mapping and dependency versions live in
`gradle.properties`, and resource processing writes the matching compatibility
metadata into each artifact.

Tile faces and tile backs are independent materials. Custom backs use ordinary
player-supplied resource packs.
Resource selection and reloads do not affect server rules,
tile IDs or private snapshots. See `ASSETS.md` for the resource contract.

`TimeControl` is enforced entirely in `Game`: per-hand reserves and fresh decision
allowances are independent for each active responder. Client interpolation and
warning sounds have no authority over deadlines. Lobby changes require the host
and invalidate ready votes. `TableInvitations` binds expiring requests to player
and table UUIDs; acceptance rechecks seating, distance, loaded chunks and phase.

`TableAudioEvents` is a pure snapshot-to-cue transformation, while `TableAudio`
owns client effects and resource-pack recording playback. Registered
resource-pack events separate table effects from recordings. No game logic
depends on an audio completion callback. See `AUDIO.md` for customization.

Replay recording and playback live in the Minecraft-independent engine.
`ReplayStore` handles bounded atomic files, searchable indexes and per-player durable
deletion markers; `ReplayServer` handles
permissions and commands, and `ReplayTransfer` handles bounded reassembly.
The viewer never feeds recorded actions back into a live `Game`. `TenhouReplay`
is the only export encoder; it consumes completed records and does not rerun
scoring. See `REPLAYS.md` for the storage layout and interchange details.
