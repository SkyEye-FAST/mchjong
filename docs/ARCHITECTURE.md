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
* `art`: deterministic CC0 vector-face rasterization, original back/table textures
  and low-complexity block models. Its SVG renderer is never a game dependency.

The server owns the wall, hands, legal actions and settlement. Requests contain an
action index and decision token, never tiles or a claimed score. Snapshots are built for
each recipient: opponents' concealed tiles and unrevealed wall tiles are replaced
with hidden sentinels **before serialization**. Persistent server NBT is separate
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

`TableResults` is a separate scrollable, narrated receipt widget. It displays
server-authored point deltas and final scores without recalculating settlement or
inventing a private tie-break order. Input stays in `TableScreen`; requests are
suppressed while one is awaiting a response and stale decisions are rejected by
the server. Riichi selection always uses the server's legal discard candidates.

Build: `gradlew.bat buildAll`. Loader-specific development runs remain
`gradlew.bat runClient` and `gradlew.bat :neoforge:runClient`.

`gradlew.bat :test` covers deterministic presentation and pointer geometry.
`gradlew.bat runSmokeClient` runs a real Fabric integrated client/server, exercises
seating and discard packets, and captures settlement and animation screenshots in
`build/smoke/evidence`. Rare multi-winner and animation states use display-only
fixtures; they are not scoring-rule integration tests. The same harness runs with
`gradlew.bat :neoforge:runSmokeClient` and stores evidence under
`neoforge/build/smoke/evidence`. Both harnesses also create a real engine record,
archive it on the integrated server, retrieve it through commands and chunked
networking, render the replay timeline and click the Tenhou export button.
Smoke adapters are separate source sets and are never packaged in release JARs.
NeoForge's dedicated-server integration tests also run in `buildAll`.

Minecraft-facing artifacts are version-scoped. The project targets mainstream
releases from 1.20.1 onward, but does not declare one binary compatible with an
API range it was not compiled against. Version differences stay at the
Minecraft/loader boundary while the `engine` remains independent of Minecraft.
The selected Minecraft, Java, loader, mapping and dependency versions live in
`gradle.properties`, and resource processing writes the matching compatibility
metadata into each artifact.

Tile faces and tile backs are independent materials. Optional patterned backs
are a native, disabled-by-default resource pack registered by each loader's
client entry point. Resource selection and reloads do not affect server rules,
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
