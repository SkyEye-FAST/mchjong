# Verification

Choose the smallest existing check that exercises the changed boundary. Full
suites belong to broad synchronizations and release acceptance, not every edit.
Dependency versions are in [Compatibility](COMPATIBILITY.md); builds and release
procedures are in [Development](DEVELOPMENT.md). Use JDK 21 on this branch.

## Build and loader checks

```text
gradlew.bat buildAll --warning-mode fail --console=plain
gradlew.bat :fabric:runSmokeClient --console=plain
gradlew.bat :neoforge:runSmokeClient --console=plain
gradlew.bat :forge:runSmokeClient --console=plain
gradlew.bat :fabric:runSmokeServer :neoforge:runSmokeServer :forge:runSmokeServer --console=plain
```

`buildAll` covers engine, presentation, generated assets/data and loader builds.
`--no-parallel --max-workers=2` bounds resource use. Dedicated-server launcher
checks use isolated directories and `--initSettings`; they cover entrypoint and
settings initialization, not simulated worlds. The default EULA is unchanged.

The 1.21.1 Forge client check is a focused registration, item-renderer and title
screen bootstrap under `forge/build/smoke/bootstrap-evidence`. It does not establish
full gameplay acceptance. Fabric and NeoForge run the shared integrated-server
fixture, including worlds, packets, inventory transactions and rendering.

## Optional integrations

Append `-PrecipeBrowser=jei` to Fabric, Forge or NeoForge client checks. Append
`-PrecipeBrowser=emi` or `-PrecipeBrowser=rei` to Fabric or NeoForge. Profiles
write separate viewer evidence directories; Forge JEI uses `jei-bootstrap-evidence`.
`RecipeBrowserDataSmoke` checks finite recipes and every cycling input against
the loaded recipes. Viewer checks query catalogue order, denominations, flower/red
back dyes, name/component-preserving upgrades and native container exclusions,
including a recipe page and a server-backed case at 320 × 240.

### Patchouli handbook

```text
gradlew.bat :fabric:runSmokeClient -PwithPatchouli=true -PsmokePatchouli=true --console=plain
gradlew.bat :fabric:runSmokeClient -PsmokePatchouli=true --console=plain
```

The installed profile checks the one-time starter book, crafting and opening the book item, chapter and entry
loading, recipes, resource reload and Latin/CJK page rendering. The absent profile
checks recipe exclusion, the automatic chat recommendation, download URL and once-per-session delivery
without opening a screen. Evidence uses `patchouli-installed-evidence`
and `patchouli-absent-evidence`. Select `manual-zh_cn.png` or
`manual-recommendation.png` with `-PsmokeScreenshots` for visual review.

### Maid integration

```text
gradlew.bat :fabric:runSmokeClient -PwithMaid=true --console=plain
gradlew.bat :neoforge:runSmokeClient -PwithMaid=true --console=plain
```

The existing fixture checks task discovery, default model-name localization,
brain-driven seating with vehicle following disabled, client mount synchronization,
saved entity recovery, assigned mounts, legal computer
play and cleanup after a task change.
Selected screenshots and results use `build/smoke/maid-evidence`.

### Create and Ponder

```text
gradlew.bat :neoforge:test --tests '*CreateProcessingTest' -PwithCreate=true
gradlew.bat :neoforge:runSmokeClient -PwithCreate=true -PwithPonder=true -PsmokePonder=true -PrecipeBrowser=jei
gradlew.bat :fabric:runSmokeClient -PwithPonder=true -PsmokePonder=true
gradlew.bat :neoforge:runSmokeClient -PwithPonder=true -PsmokePonder=true
```

Native machine checks cover output blocking, printing, coloring, red conversion,
marking and atomic packing. Tutorial checks cover storyboard discovery, playback,
representative Latin/CJK text, reload and one small-window boundary. See [Create](CREATE.md) and
[Ponder](PONDER.md) for the operating and integration contracts.

## Focused interaction checks

Use the following flags with `:fabric:runSmokeClient` and `:neoforge:runSmokeClient`:

| Flag | Existing fixture and evidence |
| --- | --- |
| `-PsmokeRoom=true` | Lobby, four-language layouts, countdowns, final standings, retained members, leave/dissolve packets; `room-evidence` |
| `-PsmokeSettlement=true` | Recorded sequential yaku, han badges, points-before-grade, sextuple-yakuman emphasis, multiple winners, four-language standings with uma, resizing and result navigation; `settlement-evidence` |
| `-PsmokeInterface=true` | Box transactions, carrier synchronization, personal mod settings and preset navigation, keyboard/disabled states, immersive controls; `interface-evidence` |
| `-PsmokeSeating=true` | Mounts, private deals, camera clearance, inspect/reset and rebound input, dragging, focus loss, immersive selection, closed-screen camera and third-person stool; `seating-evidence` |
| `-PsmokeVisibility=true` | Three participant visibility modes, private packets and independently redacted unmounted spectators; `visibility-evidence` |
| `-PsmokeManual=true` | Physical shuffle, wall building, dice, packet dealing, draws, save/reload and exit; `manual-evidence` |
| `-PsmokeItems=true` | Native held/dropped supplies and exact inventory changes; `items-evidence` |
| `-PsmokePalette=true` | Material, dye, furniture and player head-slot presentation; `palette-evidence` |

Use `:fabric:runSmokeClient -PsmokeInterface=true -PwithModMenu=true` to
exercise the installed Mod Menu config factory. The base Fabric interface run
checks the client without Mod Menu. Forge's client bootstrap and NeoForge's
interface smoke verify their native config-screen factories.

## Test ownership and acceptance

`McrWallTest` owns the 144-tile domain, independent flower identities, repeated
tail replacements, exhaustion and conservation across player-owned zones.
`McrWallLayoutTest` owns the 72-stack topology, both dice rolls, physical packet
and first/third jump sources, front/tail traversal and restored physical cursors.
`McrLayoutTest` owns the rotated hash-shaped wall clearance, compact six-column
rivers, left-corner source-marked flat melds, flower/hand separation and scene source indices.
Run the shared presentation checks with
`gradlew.bat :fabric:test --tests "*McrLayoutTest" --tests "*TableLayoutTest" --tests "*CompactTableLayoutTest" --warning-mode fail --console=plain`.

The focused shared-client MCR display fixture draws the unopened 144-tile wall
and a complete physical stock distributed across walls, hands, rivers, melds
and flowers, plus its wall-free immersive projection. It exercises the shared renderer without starting a room or a
second game simulation. On Fabric, run:

```text
gradlew.bat :fabric:runSmokeClient -PsmokeMcrLayout=true "-PsmokeScreenshots=mcr-play.png,mcr-immersive.png" --warning-mode fail --console=plain
```

Inspect the two images in `fabric/build/smoke/mcr-layout-evidence/screenshots`
and the fresh `PASS.txt`/`FAIL.txt` marker. Select `mcr-wall.png` when changing the
built wall. Omit `smokeScreenshots` for assertion-only
runs. Compile the other affected loaders without duplicating these shared images.

The two `PhysicalSuppliesTest.mcr*` cases own native case admission, flower/item
identities, appearance matching, separate-case selection and the supplied-stock
session boundary. Run them with
`gradlew.bat :neoforge:test --tests "*PhysicalSuppliesTest.mcr*" --warning-mode fail --console=plain`.

The focused automatic-table MCR smoke uses one real client and three server-side
human participants. It selects MCR through the registered payload, configures the
shared clock editor, seats four players, checks the standard 144-tile stock,
synchronized action clock and shared exit vote, then drives a
physical hand selection, view switching, immersive click/confirmation, flower
replacement and responses through the independent MCR view and action path, opens
settlement and confirms the next hand. It also keeps a match paused after the
last dismount and resumes it when everyone returns. It then fetches the sealed
MCR replay and checks initial display, event stepping and viewpoint switching.
On Fabric, run:

```text
gradlew.bat :fabric:runSmokeClient -PsmokeMcrAuto=true "-PsmokeScreenshots=mcr-auto-replay.png,mcr-auto-replay-settlement.png" --warning-mode fail --console=plain
```

Inspect both selected captures in `fabric/build/smoke/mcr-auto-evidence/`
and its fresh `PASS.txt` or `FAIL.txt` marker. Omit `smokeScreenshots` for an
assertion-only run.
`SichuanGameTest` owns suited-stock conservation, rule-selected wall layouts and
all dealer/dice cuts, secret physical first-discard
binding and heavenly void, recipient redaction, MIL/T/TFMJ fan naming and
added-kong distinctions, multi-win blood battle, T/TFMJ linked call transfers and
rounding, MIL last-kong refunds, flower-pig
deductions, maximum ready payments, eight-hand lifecycle, session confirmations,
private views and explicit save/restore. The clause mapping is in
[Sichuan match orchestration](ARCHITECTURE.md#sichuan-match-orchestration). Run
`gradlew.bat :engine:test --tests "*SichuanGameTest" --warning-mode fail --console=plain`.
`SichuanReplayTest` owns sealed physical openings, recorder restoration,
serialization and rule-executed event consistency, secret selection reconstruction,
MIL shooting refunds, T/TFMJ call transfers, multi-win and sequential-win
retirement, kong robbery and call transfers, flower-pig/ready checks, cumulative
scores and dealer succession, queue acknowledgement and altered-record rejection.
Run `gradlew.bat :engine:test --tests "*SichuanReplayTest" --warning-mode fail --console=plain`.
`SichuanBotTest` owns deterministic void/secret-discard choices, quad-pair efficiency,
public-copy deduplication, seven-pairs/all-pungs/full-flush retention, capped value,
progress-preserving kong income, conservative calls, recipient privacy, room/policy
controls, paused/restored scheduling, multi-win Bot replies, inactive Bot clocks,
automatic confirmations and both presets' complete one-human/three-Bot eight-hand
matches with restored settlements, final standings and replay flags. Run
`gradlew.bat :engine:test --tests "*SichuanBotTest" --warning-mode fail --console=plain`.
`SichuanRoomSettingsTest` owns lobby-only settings, exact preset/custom matching
for every field, rule bounds, host/stale authority, readiness reset, world-policy
restrictions and lobby/match save restoration. Run
`gradlew.bat :engine:test --tests "*SichuanRoomSettingsTest" --warning-mode fail --console=plain`.
`ServerIntegrationTest.actualMinecraftCodecsRoundTripOnlyDeclaredPayloads` also
checks Sichuan proposals and settings at both field bounds and rejects malformed
numeric proposals through the native codec.
`PhysicalSuppliesTest.sichuanStock*` owns native single-case stock admission.
`SichuanPresentationTest` owns both 108-slot physical layouts, recipient-safe scene
projection, multi-win aliases and faithful result-ledger formatting. Run
`gradlew.bat :fabric:test --tests "*SichuanPresentationTest" --warning-mode fail --console=plain`.
The focused real-client Sichuan smoke checks three-way lobby selection,
server-confirmed rule drafts and preset restoration, old-snapshot rejection,
non-host/stale/incarnation payload rejection, the
108-tile subset, private suit/first-discard declarations, NBT restoration, seated and immersive
physical-tile selection and discard, exit
voting, result screens, duplicate confirmation packets, next-hand privacy,
eight-hand completion, shared-browser retrieval, Sichuan replay event/hand
navigation, final standings and return to the lobby. It then uses native room
packets to add/remove/fill three Bots, restores their NBT roster and completes an
eight-hand T/TFMJ match with one real human, automatic confirmations, final client
results and a validated Bot replay:

```text
gradlew.bat :fabric:runSmokeClient -PsmokeSichuan=true --warning-mode fail --console=plain
```

Inspect the fresh marker in `fabric/build/smoke/sichuan-evidence`. This focused
profile runs assertions without captures. Add
`-PsmokeScreenshots=sichuan-rules.png` for the rule editor, or
`-PsmokeScreenshots=sichuan-table.png,sichuan-results.png` for table and hand-result
changes, or `-PsmokeScreenshots=sichuan-replay.png,sichuan-replay-settlement.png`
for the affected replay states.

`WallInvariantTest` owns the Riichi live/dead wall and indicator invariants.
`McrSettlementTest` owns MCR win payments, separate penalties and zero-payment draws.
`McrSessionTest` owns UUID/seat binding, live-mount privacy, paused actions,
incarnation-scoped requests, supplied-stock shuffling, independent response clocks,
safe absent-player timeouts, resumed forced actions and timed hand acknowledgements.
`McrBotTest` owns shared one-human/three-Bot preparation, world policy, recipient-only
decisions, minimum-fan wins, discard availability, conservative calls, delayed
simultaneous responses, restored pauses/votes and a complete sixteen-hand Bot match.
`McrBotRoutesTest` owns eight-fan route choices against faster low-fan waits,
special-form retention, calls that destroy required structures and publicly
exhausted/scarce indispensable tiles.
`McrPersistenceTest` owns private save round trips, resumed responses and added
kongs, payment idempotence, stale-token rejection and invalid-save rejection.
The sixteen-hand `McrGameTest` lifecycle restores each completed hand before advancing.
`McrViewTest` owns participant/spectator redaction, concealed kongs, private
response progress, public flowers/penalties and the save/view codec separation.
`McrGameTest` owns fixed-wall dealing, ordered supplementary draws, action
arbitration, wrong-win continuation/stop-win, kong transaction boundaries,
server-derived winning facts and the complete sixteen-hand lifecycle.
`McrHandAnalyzerTest` owns the explicit tile/meld/context conversions, basic
library calls, flower exclusion and fan-result projection. `ScoringBridgeTest`
owns the existing Riichi scoring adapter. Run these engine boundaries with:

```text
gradlew.bat :engine:test --tests "*Mcr*Test" --tests "*WallInvariantTest" --tests "*ScoringBridgeTest" --tests "*GameLifecycleTest" :engine:shadowJar --configure-on-demand --warning-mode fail --console=plain
```

Engine tests own scoring, reaction priority, exits, ballots, saved state and
recipient privacy. Lifecycle/manual checks exercise both player counts; enumerate
presets only where rules differ. `CompactTableLayoutTest` owns hand clearance and
picking; `TableLayoutTest` owns melds, rivers and walls. Replay storage/authorization
belongs to `ReplayStoreTest`, and bounded chunk reassembly to `ReplayTransferTest`.
The storage suite also checks Sichuan browser headers, final standings, participant
permissions and altered-event rejection. Run
`gradlew.bat :fabric:test --tests "*ReplayStoreTest" --warning-mode fail --console=plain`.
Asset tests cover deterministic output, texture/model contracts, translation key
parity, duplicate keys, placeholders and literal source references.

`PhysicalSuppliesTest` exercises native recipes and component codecs on NeoForge's
test server. Shared box, point-stick and equipment fixtures use real players,
menus and worlds: clicks, shift transfers, splits, dragging, hotbar/offhand swaps,
carrier invalidation, 136/144-tile printing, reagent consumption and conservation.
They check placement/headroom, occupancy-cell removal, explosions, private/public
save separation and recoverable sanma stock. Do not introduce null-world behavior
into production to support these checks.

Full client runs also exercise ordinary-table physical handling, live rule and
control packets, replay archival, command fetch, chunk transfer, timeline seeking
and the Tenhou export button. Rare multi-winner and animation screenshots use
display-only fixtures and are not independent scoring proofs. Smoke-only mouse
hooks keep the desktop cursor free and verify its native mode.

## Scope and screenshots

For a small fix, run its owning test or focused smoke once. Add another check only
when a failure or a changed boundary justifies it. A shared implementation normally
needs one representative loader; compile each affected API adapter. Use additional
locales, viewports or player counts only for different behavior or layout risks.
Full builds and complete client suites are reserved for broad changes and releases.

Client smoke runs produce assertions and logs by default. Request exact, comma-separated
filenames with `-PsmokeScreenshots`, for example:

```text
gradlew.bat :fabric:runSmokeClient -PsmokeSeating=true -PsmokeScreenshots=41-layout-four-kans-320x240.png --console=plain
```

Choose normally one or two affected states from the owning fixture. Screenshot
filenames are defined at its capture calls. The selected profile replaces its old
evidence directory; captures stay in ignored build output. Inspect requested images
and the fresh `PASS.txt`, `FAIL.txt` and logs. Compilation verifies API use, while
visual acceptance requires viewing the affected state.

Report commands, results and coverage limits in the delivery message or PR.
Guides retain reusable instructions and current contracts; individual run logs,
screenshot inventories, timings and experiment histories remain outside tracked docs.

## Bot checks

Use `:engine:test --tests "*TrainingBotTest"` for decision regressions.
