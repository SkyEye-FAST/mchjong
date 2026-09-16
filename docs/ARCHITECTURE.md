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
* `art`: deterministic CC0 vector-face rasterization and client model descriptors,
  plus a separate server-data generator. Its SVG renderer is never a game dependency.

Survival components, atomic box transformations and component-preserving recipes
live in `common/item` and `common/recipe`. `TableEquipment` stores actual removable
box, cloth and point-stick stacks; its public projection contains only wood,
colors, material, box presence and placed-stick counts/values. Native container
contents and game state never enter chunk updates. The two tables share one block
entity type and the referee. `engine/ManualHandling` adds explicit shuffle, wall,
packet and draw phases without duplicating scoring or inventing client authority.
See [SURVIVAL.md](SURVIVAL.md) for the lifecycle and exact component contract.

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
centered independently of meld count. The drawn tile has a separate slot, so
drawing never shifts existing tiles. Right-aligned melds occupy an inner depth
band outside the wall; even four kans cannot displace or cover the hand.
Extracted norths sit on the outer rail beside the hand. The equipment box is
displayed only in the lobby, never over playing tiles.
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
Smoke adapters are separate source sets and are never packaged in release JARs.
NeoForge's dedicated-server integration tests also run in `buildAll`.

Survival checks use real server players, menus and levels in the shared smoke
source set rather than introducing null-world behavior into production code.
The ordinary-table client smoke sends real button actions for shuffle, walls,
packet dealing, draws, discards and exit; those snapshots are not display-only
fixtures. Box menu lifetime is bound to the current menu and exact carrier
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
player-supplied resource packs; no optional back pack is bundled or registered.
Resource selection and reloads do not affect server rules,
tile IDs or private snapshots. See `ASSETS.md` for the resource contract.

`TimeControl` is enforced entirely in `Game`: per-hand reserves and fresh decision
allowances are independent for each active responder. Client interpolation and
warning sounds have no authority over deadlines. Lobby changes require the host
and invalidate ready votes. `TableInvitations` binds expiring requests to player
and table UUIDs; acceptance rechecks seating, distance, loaded chunks and phase.

`TableAudioEvents` is a pure snapshot-to-cue transformation, while `TableAudio`
owns client playback and its independent native speech instance. Registered
resource-pack events separate table effects from recordings. No game logic
depends on an audio completion callback. See `AUDIO.md` for customization.

Replay recording and playback live in the Minecraft-independent engine.
`ReplayStore` handles bounded atomic files and indexes, `ReplayServer` handles
permissions and commands, and `ReplayTransfer` handles bounded reassembly.
The viewer never feeds recorded actions back into a live `Game`. `TenhouReplay`
is the only export encoder; it consumes completed records and does not rerun
scoring. See `REPLAYS.md` for the storage layout and interchange details.
