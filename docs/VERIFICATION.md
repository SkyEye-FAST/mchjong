# Verification

Choose the smallest existing check that exercises the changed boundary. Full
suites belong to broad synchronizations and release acceptance, not every edit.
Dependency versions are in [Compatibility](COMPATIBILITY.md); builds and release
procedures are in [Development](DEVELOPMENT.md). Use JDK 21 to build this branch;
the production artifacts and Minecraft runtime target Java 17.

## Build and loader checks

```text
gradlew.bat buildAll --warning-mode fail --console=plain
gradlew.bat :fabric:runSmokeClient --console=plain
gradlew.bat :forge:runSmokeClient --console=plain
gradlew.bat :fabric:runSmokeServer :forge:runSmokeServer --console=plain
```

`buildAll` covers engine, presentation, generated assets/data and loader builds.
`--no-parallel --max-workers=2` bounds resource use. Dedicated-server launcher
checks use isolated directories and `--initSettings`; they cover entrypoint and
settings initialization, not simulated worlds. The default EULA is unchanged.
Snapshot CI limits Gradle to two workers and uploads test reports, XML results,
problem reports and the build log even when the build fails.

Fabric and Forge run the shared integrated-server fixture, including worlds,
packets, inventory transactions and rendering. Forge additionally supports
`-PsmokeBootstrap=true` for a focused registration, renderer and native-NBT title
screen check under `forge/build/smoke/bootstrap-evidence`.

## Optional integrations

Append `-PrecipeBrowser=jei`, `emi` or `rei` to either loader's client check.
Add `-PsmokeBrowser=true` for focused viewer checks under
`build/smoke/browser-<viewer>-evidence`; REI also loads Cloth Config and Architectury.
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
gradlew.bat :forge:runSmokeClient -PwithMaid=true --console=plain
```

The existing fixture checks task discovery, default model-name localization,
brain-driven seating with vehicle following disabled, client mount synchronization,
saved entity recovery, assigned mounts, legal computer
play and cleanup after a task change.
Selected screenshots and results use `build/smoke/maid-evidence`.
`maid-furniture-head.png` and `maid-gecko-furniture-head.png` render the actual
maid through the Bedrock and Gecko head layers. Each sheet compares ordinary and
automatic tables with stools in the equipment and display slots, including dyed
components and simultaneous skull equipment; neither uses a player-model substitute.

### Create and Ponder

```text
gradlew.bat :fabric:runSmokeClient -PwithCreate=true -PsmokeCreate=true -PrecipeBrowser=emi
gradlew.bat :forge:runSmokeClient -PwithCreate=true -PsmokeCreate=true -PrecipeBrowser=jei
gradlew.bat :fabric:runSmokeClient -PwithPonder=true -PsmokePonder=true
gradlew.bat :forge:runSmokeClient -PwithPonder=true -PsmokePonder=true
```

Native machine checks cover output blocking, printing, coloring, red conversion,
marking and atomic packing. Tutorial checks cover storyboard discovery, playback,
representative Latin/CJK text, reload and one small-window boundary. See [Create](CREATE.md) and
[Ponder](PONDER.md) for the operating and integration contracts.

## Focused interaction checks

Use the following flags with `:fabric:runSmokeClient` and `:forge:runSmokeClient`:

| Flag | Existing fixture and evidence |
| --- | --- |
| `-PsmokeRoom=true` | Lobby, four-language layouts, countdowns, final standings, retained members, leave/dissolve packets; `room-evidence` |
| `-PsmokeSettlement=true` | Recorded sequential yaku, han badges, points-before-grade, sextuple-yakuman emphasis, multiple winners, four-language standings with uma, resizing and result navigation; `settlement-evidence` |
| `-PsmokeInterface=true` | Box transactions, carrier synchronization, personal mod settings and preset navigation, explicit input choices, category-only reset, keyboard/disabled states, immersive controls; `interface-evidence` |
| `-PsmokeSeating=true` | Mounts, private deals, camera clearance, inspect/reset and rebound input, dragging, focus loss, immersive selection, closed-screen camera and third-person stool; `seating-evidence` |
| `-PsmokeVisibility=true` | Three participant visibility modes, private packets and independently redacted unmounted spectators; `visibility-evidence` |
| `-PsmokeManual=true` | Physical shuffle, wall building, dice, packet dealing, draws, save/reload and exit; `manual-evidence` |
| `-PsmokeItems=true` | Native held/dropped supplies and exact inventory changes; `items-evidence` |
| `-PsmokePalette=true` | Material, dye, furniture, native player head-slot, crouched table edge and item-context presentation; `palette-evidence` |

Use `:fabric:runSmokeClient -PsmokeInterface=true -PwithModMenu=true` to
exercise the installed Mod Menu config factory. The base Fabric interface run
checks the client without Mod Menu. Forge's client bootstrap verifies its native
config-screen factory.

## Test ownership and acceptance

`McrWallTest` owns the 144-tile domain, independent flower identities, repeated
tail replacements, exhaustion and conservation across player-owned zones.
`McrWallLayoutTest` owns the 72-stack topology, both dice rolls, physical packet
and first/third jump sources, front/tail traversal and restored physical cursors.
`McrLayoutTest` and `SichuanPresentationTest` own fixed-size Chinese geometry:
12-degree windmill walls, opposite inner-face spacing along the wall normal,
physical slots, layer contact, felt bounds, left-side meld chronology, minimal
hand displacement and six-column rivers. `ChineseGameplayFixtures` uses only
engine-issued actions and calls engine conservation validation after every
accepted action. Multiple shuffled openings are checked through initial flower
replacement/voiding, post-deal, midgame, late game and termination. Claims are
preferred and wins passed to exercise public areas through wall exhaustion.
No full-wall-plus-maximum-river/hand/meld capacity fixture constrains geometry.
Run shared checks with:

```text
gradlew.bat :fabric:test --tests "*McrLayoutTest" --tests "*SichuanPresentationTest" --tests "*TableLayoutTest" --tests "*CompactTableLayoutTest" --warning-mode fail --console=plain
```

The layout smoke renders the unopened wall and actual engine post-deal, midgame
and late-game snapshots for MCR, Sichuan SBR and Sichuan TFMJ. All captures include
the automatic table's central instrument housing. Run:

```text
gradlew.bat :fabric:runSmokeClient -PsmokeMcrLayout=true "-PsmokeScreenshots=mcr-wall.png,mcr-dealt.png,mcr-midgame.png,mcr-late.png,sichuan-wall-east-west.png,sichuan-dealt-east-west.png,sichuan-midgame-east-west.png,sichuan-late-east-west.png,sichuan-wall-north-south.png,sichuan-dealt-north-south.png,sichuan-midgame-north-south.png,sichuan-late-north-south.png" --warning-mode fail --console=plain
```

Inspect fresh `PASS.txt`/`FAIL.txt` and selected images under
`fabric/build/smoke/mcr-layout-evidence/screenshots`. `mcr-immersive.png` captures
the same midgame with walls omitted. Check large Chinese tile proportions,
matching wall yaw, larger Sichuan end gaps, the first river outside the housing,
later rows in emptied wall space and chronological public groups on the left.

The `smokeMcrAuto` profile supplies `mcr-wall-seated.png`, `mcr-dealt-seated.png`,
`mcr-midgame-seated.png` and `mcr-late-seated.png`. `smokeSichuan` supplies the
same four phases as `sichuan-{wall,dealt,midgame,late}-{east-west,north-south}-seated.png`.
These use the real seated world camera and active center panel. Empty-hand full
walls establish neither wall/hand clearance nor later river capacity; inspect
the legal played positions as well. Screenshots are opt-in. Compile the other
affected loaders without duplicating shared images.

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

`ConvenienceHintsTest` owns the shared room hint setting, world-policy restriction,
save/variant retention, MCR structural versus eight-non-flower-fan waits, Sichuan
void tiles and structural/capped ready values, and public-copy accounting. The
MCR privacy fixture and `SichuanGameTest.hintsIgnoreOpponentsConcealedHandsAndFutureWallIdentities`
change private hands and wall identities without changing hint results.
`SichuanGameTest.passedWinsBlockSameFanUntilDrawingAndSurviveRestore` also checks
recipient-only restriction projection. Run with `:engine:test --tests "*ConvenienceHintsTest"
--tests "*SichuanGameTest" --tests "*TenpaiHintsTest" --warning-mode fail --console=plain`.
The MCR automatic-table and Sichuan client smokes enable hints through the shared
room control, select a discard and focus the native hint widget. Opt-in captures
are `mcr-auto-hints.png` and `sichuan-hints.png` in their respective evidence directories.
Both smokes also check scored display fixtures at 320 × 240 seated and on the
fixed immersive canvas; optional captures are `mcr-scored-hints-seated.png`,
`mcr-scored-hints-immersive.png`, `sichuan-scored-hints-seated.png` and
`sichuan-scored-hints-immersive.png`.
`ServerIntegrationTest.actualMinecraftCodecsRoundTripOnlyDeclaredPayloads` also
checks Sichuan proposals and settings at both field bounds and rejects malformed
numeric proposals through the native codec.
`PhysicalSuppliesTest.sichuanStock*` owns native single-case stock admission.
`SichuanPresentationTest` owns both 108-slot physical layouts, recipient-safe scene
projection, multi-win aliases and faithful result-ledger formatting. Run
`gradlew.bat :fabric:test --tests "*SichuanPresentationTest" --warning-mode fail --console=plain`.
The focused real-client Sichuan smoke checks three-way lobby selection at
320 × 240, the shared settings/clock return chain and synchronized World scope
for each variant, server-confirmed rule drafts and preset restoration, old-snapshot rejection,
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
Its automation cases cover below-minimum win rejection, qualified self-draw/ron,
win priority, private preferences, unchanged clocks/partial responses and restored
preferences with per-hand reset. `SichuanGameTest` checks unhandled self-draw/ron,
unchanged passed-win state, simultaneous replies, private/restored preferences and
legal physical draw discards under void-suit priority and SBR first-discard binding.
`RiichiAutoPlayTest` and `ReactionRulesTest` retain shared win/pass/discard priority,
private/restored preferences and Riichi-only sort/north behavior.
The MCR and Sichuan client profiles also run `MatchAutomationControlsSmoke` for
native pointer/keyboard packets, acknowledgement focus, 320 x 240 seated controls
and the fixed immersive canvas, including clock/control separation and opening
and returning from the replay browser by pointer in both views. Opt-in captures are `mcr-automation-seated.png`,
`mcr-automation-immersive.png`, `sichuan-automation-seated.png` and
`sichuan-automation-immersive.png`.
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
`ReplayPresentationTest` checks static MCR/Sichuan hand selectors and every frame's
remaining wall count from real played openings through exhaustion, including both
Sichuan presets. Run `:fabric:test --tests "*ReplayPresentationTest"`.
`ChinesePickingTest` checks the projected centers of every concealed hand tile
against the same oriented tile boxes, in real post-deal, midgame and late-game
positions from all four seats at 320 × 240. Run `:fabric:test --tests "*ChinesePickingTest"`.
The settlement smoke also checks complete operation hints at the minimum viewport
and Sichuan short badge widths in all four languages; full suit names remain in
player-card hover details and Sichuan screen narration.

`RecipeBrowserDataSmoke` exercises loaded recipes and native NBT identity.
Shared box, point-stick and equipment fixtures use real players,
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

## Furniture and settlement presentation

The palette fixture captures `00-table-head.png`, `00-stool-head.png`, `00-armor-stand-head.png`,
`00-table-edge-sneaking.png`, `00-table-edge-sneaking-shift.png`,
`00-table-first-person.png`, `00-table-third-person.png` and
`00-furniture-contexts.png`. The two edge captures move the crouched camera by
one degree to expose depth flicker. The context sheet uses native GUI, FIXED,
GROUND and both hand rendering paths with the shared furniture mesh.

`FurnitureShapeTest` checks face-local UV scale, opaque cloth composition,
single-plane cloth geometry and furniture context transforms.
`RiichiAudioEventsTest` checks explicit result events, the 750 ms points hold,
ordinary hands without LIMIT, long recordings and independent multiple winners.
`AudioEffectsSmoke` checks native registration, resource resolution, mono decoding,
duration and peak headroom for every original sound.
`AssetContractTest` checks all project effect resources and neutral furniture
model transforms. Settlement captures `06-readout-points.png` and
`06-readout-grade.png` assert points precede the grade by at least 750 ms.
`UiControlsSmoke` checks visible settings rectangles for bounds and overlap at
320 × 240, 640 × 400 and 1280 × 800, retaining native keyboard behavior.

The shared presentation is checked on all three Minecraft profiles with
`buildAll --warning-mode fail` and the Fabric `smokeMcrAuto`, `smokeSichuan`,
`smokeSettlement`, `smokeManual` and `smokeVisibility` client profiles. MCR and
Sichuan checks exercise the shared immersive canvas, physical hand picking,
settlements and replays while retaining their own rules and hidden information.
The Sichuan picking check switches back to the physical table and verifies the
same tile remains selected.

The adjacent bot-advisor project owns decision and overlay-placement tests.
Its development smoke installer can add the matching loader JAR to a focused
run and removes it afterward. Run its `mcr-auto`, `sichuan` and `seating` profiles
to check the three native rule adapters against actual client screens; these
installed checks complement the advisor's decision tests.
