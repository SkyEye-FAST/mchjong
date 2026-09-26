# Changelog

All notable changes to MChjong are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Invite controls beside vacant seats in the lobby, opening the online player selector.
- Pale oak tables, stools and tile material with matching crafting and stonecutting recipes for Minecraft 26.1.2.
- Zip preset archives for custom tile faces, tile backs, riichi sticks and voice packages, synchronized from the server to connected clients.
- Mahjong box tile-back selector screen allowing players to inspect and choose from available tile-back presets.
- Personal preset configuration screen for selecting custom riichi stick models and localized voice announcements.
- Built-in banner tile-back preset and vanilla item riichi stick presets.
- Sequential voice playback and score announcements during settlement, with synchronized yaku highlights.
- Dedicated voice and sound event distinguishing double riichi declarations.
- Configurable match rule awarding player experience points according to final uma standings.
- Create workshop recipe animations across recipe viewers, including REI recipe support on NeoForge.
- Gradual, copy-aware yaku route evaluation for training opponents, with candidate utility diagnostics, compact position inspection, scored-yaku comparison statistics.

### Changed

- Use a single Kan label for open, concealed and added kan actions and hints in all four languages.
- Bound routine verification to affected behavior, consolidate client smoke scenarios, and make screenshot capture explicitly selectable; keep maintenance guides focused on current contracts.
- Synchronize corner meld placement and hand clearance to both compatibility profiles, plus Ponder overlay placement to 1.20.1, and advance the release branch pins.
- Synchronize post-0.7.2 changes across the Minecraft 1.20.1, 1.21.1 and 26.1.2 loader profiles, with signed branch ancestry and reviewed release pins.
- Face and back presets use paginated box screens, with four sample tiles for each face design and one Mahjong dye consumed per successful change.
- Explicit bot timings include decision-thread CPU time alongside wall-clock latency.
- Client and server mod settings are stored in TOML format (`mchjong.toml` and `mchjong-client.toml`).
- Create workshop Kansai face printing uses a single face plate.
- Training route matching uses packed copy-capacity caches, reusable group masks, sparse target checks, direct pair/companion evaluation and conservative head bounds. Cached continuation ordering retains deterministic ties, and exact one-shanten advances use every tied best-shanten discard from the library.
- Hard opponents evaluate every effective draw and tenpai continuation from one-shanten candidates; calls compare route value and closed-hand opportunity with their actual progress. Decision-local caches, reusable route tables and scratch buffers, and conservative distant-branch score bounds reduce repeated evaluation work.

### Fixed

- Resolve build dependencies, plugins and Minecraft downloads through official repositories.
- Preserve client-side maid seating when vehicle following is disabled, including restored companions and assigned seat changes.
- Align the box face and back selectors, show both only with Mahjong dye, and apply their selections together from the main screen for one dye.
- Training opponents reserve search for passing when comparing calls, evaluate replacement alternatives at matching depth, and retain the closed-hand riichi option at both difficulty levels.
- Use supported loader APIs for native model rendering, resource identifiers and client registration across Minecraft profiles.
- Keep Ponder item controls above the table and tutorial text in the standard reading area so the furniture remains visible.
- Anchor immersive melds at every player's right-hand cloth corner, with complete tile bounds inside the cloth and matching animation positions. Increase crowded hand-to-meld clearance in both immersive and seated views.
- Keep the authorized Mahjong box menu open while choosing face and back presets, and retain its native close lifecycle.

- Tile faces keep an opaque white plate with a rear surface visible through glass, including hidden hands and face-down walls. Glass shells render after opaque plates. Only visible identities show printed artwork, and standing faces retain their owner-facing orientation in both views. Immersive melds stay in one row at their original depth while standing hands move aside.
- Voice event regression coverage checks both the declaring player and another seated player; box smoke checks use the preset button's translation key, and visibility smoke accepts the room's initial mode.
- The built-in Mojang back pattern matches the vanilla banner emblem.
- Settlement receipts keep han badges next to yaku names and enlarge points and hand grades.
- Personal voice presets play for the selecting player's declarations and wins instead of other players' actions.
- Stool vehicles persist through logout and chunk saves, preserving seated players and maid companions.
- Tile faces fall back to default Kansai artwork when a requested custom preset archive is unavailable.
- Concealed kans preserve eligible iipeikou routes, with focused regression coverage alongside copy-aware caches and complete tenpai-discard enumeration.
- HUD deposit counters and score displays retain standard riichi stick icons regardless of personal stick presets.
- Red five composition automatically adjusts to the available tile stock when initializing table rules.
- Rule navigation page order and hand visibility option ordering match the settings screen layout.
- Immersive focus indicator size is enlarged for clearer tile targeting.
- Immersive table cloth boundaries and meld placement align accurately with physical tile positions.
- Built-in banner tile-back artwork renders with clean single-color marks.
- Training opponents advance the narrated settlement stage, and retain valuable dama continuations when comparing riichi.

## [0.7.2] - 2026-09-25

### Added

- Creative inventory lists mahjong stools in all sixteen dye colors, with localized names.

### Fixed

- Companion portraits and stool seating display and behave correctly.
- Replacement tiles draw from their physical dead-wall slots.
- Immersive melds follow table order, and flat tile layers align with physical tiles.
- Settlement controls separate the skip action from hand hints, with final standings appearing at the settlement midpoint.
- Automatic play settings reset at the start of each hand.

## [0.7.1] - 2026-09-24

### Added

- REI displays MChjong supply recipes with ingredient choices and shaped or shapeless layouts.
- Modrinth and CurseForge publication for all seven supported loader artifacts.

### Compatibility

- REI supply recipes are available on Minecraft 1.21.1 Fabric and NeoForge, 1.20.1 Fabric and Forge, and 26.1.2 Fabric and NeoForge.
- Fabric and Quilt releases require Fabric API.
- Loader-specific validation coverage is recorded in [Compatibility and verification](docs/COMPATIBILITY.md).

## [0.7.0] - 2026-09-24

### Added

- Mahjong tile blanks can be cut from all eleven wood plank types, each retaining its own wood color.
- Mahjong boxes accept Undo Dye in the dye compartment to clear tile-back dyeing through the Remove dye action.
- Optional Create workshop with efficient sawing and mixing, reusable face-printing plates, two-target back dyeing, single-tile red-five application and automatic packing.
- Component-aware JEI/EMI workshop examples and localized Ponder production and dyeing tutorials.
- Optional Touhou Little Maid and Orihime integration with dedicated mahjong tasks and table seating.
- Rule option descriptions in rule screens and tooltips across all four supported languages.

### Changed

- Tile inventory models show their thickness in 3D, and blank fronts use the same material finish as undyed backs.
- Mahjong dye requires white dye alongside black, red, green and blue. Mahjong boxes use a shaped chest, wooden-slab, leather and iron-nugget recipe.
- Simplified Chinese translations standardize mahjong terminology for rule presets, extensions, bot actions and table furniture ([#5]).

### Fixed

- Settlement screens preserve table tile-back dye and material choices on concealed kongs.
- Symmetric rasterization removes asymmetric pip boundary protrusion on dice textures.
- Tile back textures on face-down walls align upright toward the table center rather than upside down ([#3]).
- Winning ron declarations resolve immediately ahead of lower-priority chi, pon or kan prompts ([#4]).
- Inactive or abandoned matches pause turn timers and confirm final departure before vacating seats.
- Dice roll controls appear after all four walls are built on manual tables.
- Unavailable rule presets remain visible with explanatory requirements.

### Compatibility

- Minecraft 1.21.1: Fabric, Forge and NeoForge; Minecraft 1.20.1: Fabric and Forge; Minecraft 26.1.2: Fabric and NeoForge. Quilt uses the corresponding Fabric JAR.
- JEI is available on all seven builds. EMI covers Fabric and NeoForge on 1.21.1, and Fabric and Forge on 1.20.1. REI provides catalogue and recipe viewers on supported profiles.
- Ponder tutorials target Fabric and NeoForge on 1.21.1, and Fabric and Forge on 1.20.1.
- Create workshop integration targets NeoForge on 1.21.1, and Fabric and Forge on 1.20.1.
- Touhou Little Maid and Orihime integrations target Fabric and NeoForge on 1.21.1, Fabric and Forge on 1.20.1, and tested prerelease builds on 26.1.2.
- Minecraft 1.21.1 requires Java 21; Minecraft 1.20.1 requires Java 17; Minecraft 26.1.2 requires Java 25.
- Loader-specific validation coverage is recorded in [Compatibility and verification](docs/COMPATIBILITY.md).

## [0.6.0] - 2026-09-22

### Added

- Shift-wheel height adjustment for the seated camera.
- Resource packs can add and override tile-face presets, with namespaced identities retained on printed tiles and synchronized tables.
- Transparent pattern layers for tile backs and table cloths, shared by world, item and immersive rendering.
- Resource-pack JSON models and textures for riichi deposits.
- Minecraft 1.21.1 Forge support, including the shared JEI recipe integration.
- Minecraft 1.20.1 Fabric and Forge builds with native item NBT, networking, rendering, and JEI, EMI, REI and Ponder integrations.

### Changed

- Slightly lower default seated camera height for a closer table-level view.
- Riichi stick icons and physical deposits share a blue, white-dot design matching the 1,000-point stick.

### Fixed

- Players retain their room membership while automatic seating moves them to their assigned wind.
- Forge development launches discover the mod and its resource pack together, and additional riichi-stick models load correctly.

### Compatibility

- Minecraft 1.21.1: Fabric, Forge and NeoForge artifacts; Minecraft 1.20.1: Fabric and Forge artifacts. Quilt uses the corresponding Fabric JAR.
- JEI is available on all five builds. EMI, REI and Ponder integrations target Fabric and NeoForge on 1.21.1, and Fabric and Forge on 1.20.1.
- Minecraft 1.21.1 requires Java 21; Minecraft 1.20.1 artifacts target Java 17.
- Loader-specific validation coverage is recorded in [Compatibility and verification](docs/COMPATIBILITY.md).

## [0.5.3] - 2026-09-22

### Added

- Room hand visibility choices: open hands, visible to all players, visible to riichi players, and visible only to self.
- Table-side spectators can see upright hand faces when the room permits all players to view them.

### Changed

- Lobby controls provide direct access to player count, rules, hand visibility, clocks, invitations and participants.
- Settlement screens advance after a 10-second countdown; completed matches show final standings and return the group to the lobby, where players can leave or the host can dissolve the room.

### Fixed

- Concealed tile fronts form a complete beveled shell without gaps around the face covering.

## [0.5.2] - 2026-09-22

### Added

- Dedicated creative mode tab featuring a Hatsu tile icon, cataloging mahjong supplies and all sixteen colored table cloths.
- Localized names for all wood table variants and sixteen table cloth colors across English, Japanese, Simplified Chinese, and Traditional Chinese.

### Changed

- Default table cloth crafting recipe and standard table finish updated to cyan wool and carpet.
- Low-resolution tile materials, dice faces, and dyed table cloths enriched for sharper contrast and visual detail.
- Loader builds separated into dedicated Fabric and NeoForge subprojects under an aggregator root Gradle project.
- Verified download mirrors configured for Fabric Loom and NeoForm runtime manifests.

### Fixed

- Immersive turn clocks and action buttons repositioned to prevent overlapping player hands and maintain clear discard visibility.
- Immersive game controls scale consistently across GUI scales, and mahjong box interaction simplified for item insertion.
- Blank tile stonecutting recipes updated to reliably accept bone blocks and smooth stone.

## [0.5.1] - 2026-09-22

### Added

- Roughly Enough Items (REI) integration displaying table variants, blank tile materials, and point-stick crafting recipes.
- Point-stick drawer recipient cycling via mouse wheel or direct row selection, with Shift-click shortcut delivery directly into the selected recipient's row.

### Changed

- Stonecutting bone blocks yields 24 unmarked point sticks, and point-stick marking requires eight blank sticks and one dye per craft.
- Live immersive play keeps river discards at normal brightness and distinguishes tedashi from tsumogiri through motion arcs and trajectory timing, reserving dimmed discards for replay diagrams.

### Fixed

- Dismounting or standing from a stool in an unstarted game lobby immediately vacates the seat and transfers room hosting when applicable.
- Manual match preparation requires point sticks to be fully stocked in the mahjong box before starting.
- Red fives are preserved across tile face preset engravings and back recoloring.
- Immersive settlement and exhaustive draw score panels scale to the fixed 1280 × 800 canvas with aligned click targets and meld displays.
- Immersive hand tiles align to a uniform baseline with raised selection offsets.

## [0.5.0] - 2026-09-21

### Added

- Mahjong boxes accept any vanilla dye in their dye slot to recolor the stored tile backs to all sixteen Minecraft dye colors; undyed tiles use their material texture, while dyed glass uses translucent stained glass instead of an opaque back.

### Changed

- Immersive play now uses a fixed 1280 × 800 virtual canvas with a unified perspective table, solid tiles, upright opponent hands, foreground hand rack, perimeter player plaques and a compact point-stick display. Larger captions and a separate action rail keep the hand and rivers readable; sideways riichi discards align to the top of their row. GUI scale and window size apply one uniform letterboxed scale.

### Fixed

- Immersive wait previews enlarge the hint target, tile faces and text for the fixed canvas.
- Immersive toolbar buttons fit their full translated captions at the enlarged text size.
- Immersive keyboard help and footer clocks use the enlarged caption size.
- Added-kan tiles render from back to front so the rear tile's shadow keeps the called tile's face clear.

## [0.4.1] - 2026-09-20

### Added

- Compact stocked mahjong boxes in creative inventory and recipe viewers (JEI, REI, EMI) providing pre-sorted 144-tile sets with zero, three, or four red-five presets.

### Changed

- Engine legal actions and tile domain utilities migrated to Kotlin.

### Fixed

- Unstarted game lobbies are released when all seated players leave or log out before the match begins.

## [0.4.0] - 2026-09-20

### Added

- Physical 25,000-point point stick reserves in table side drawers, with interactive dealer dice rolling, dice-driven wall breaks, and manual drawer payments.
- Presence lifecycle separating room membership from physical stool seating, with automatic stool remounting when returning to matches and non-blocking table exit flow.
- Real-time private furiten indicators displayed on the local player card during seated play.
- Interactive wait previews displaying winning tiles, remaining unseen counts, and han valuations centered above the private hand.
- Hold-C seated camera zoom and right-drag table translation controls with configurable keybindings.
- Bounded tile development search and push/fold defensive evaluation for training bots aligned with shared engine rules.

### Changed

- Training bot opponents consolidated into Easy and Hard difficulty presets.
- Seated HUD uses compact player cards and integrates enlarged dora indicators into the central score panel header.
- Immersive play arranges table action and opponent hands around a shallow-perspective felt surface with public wall stacks, beveled tiles and a centralized round/score panel; tsumogiri is dimmed separately from tedashi.
- Seated inspect zoom is stronger, and manual-table drag source/drop hit areas are more forgiving while remaining bounded to the owning side.
- Immersive seat depth now scales the opposite and side players, river rows gain subtle perspective, the private hand has a shallow foreground arc, and player labels/automation controls occupy less of the table.
- Immersive draws and discards animate through screen-space table coordinates; tsumogiri uses a shorter path, tedashi a higher arc, and riichi rotates as the tile lands.
- Engine settlement calculation, hand analysis helpers, replay recorder, and Tenhou export subsystems migrated to Kotlin.

### Fixed

- Stool interactions ignore right-clicks while sneaking, preventing accidental sitting when placing blocks or handling items near tables ([#1]).
- Wall break opening orientation follows dealer position and dice throw direction.
- Pregame wind drawing lotteries generate uniform seat distributions without correlation bias.
- Mod display name corrected from MCjhong to MChjong across manifests, documentation, and translations ([#2]).

## [0.3.2] - 2026-09-18

### Fixed

- Riichi discards align with river top edges across table-board and 3D views.

## [0.3.1] - 2026-09-18

### Added

- Immersive screen-space mahjong layout with a clickable private hand, public melds and rivers, usable under world obstructions with the existing selection and keyboard controls.
- Player skin portraits before names in table, room and result views, with distinct practice-bot portraits.
- Hold-C seated zoom, forward/back camera movement with right-drag, and administrator world-policy editing in Settings.

### Changed

- Room seat cards contain bot controls, with a centered primary preparation button and table-specific seating guidance.
- Immersive opponents and discards face inward around the player's bottom hand.
- Immersive view requires 480 × 300 logical GUI pixels to keep side rivers readable, with a localized size hint and automatic return to the seated view when resized smaller.

### Fixed

- Player invitations resolve online UUIDs and names correctly.

## [0.3.0] - 2026-09-18

### Added

- Default-off personal tenpai hints show structural waits and unseen-copy counts, with legal-discard previews and shared public-tile accounting for training bots.
- One/two/four-yaku-han minimum and East-only/East–South options for Mahjong Soul and Tenhou, with length-aware extensions, replay labels and an explicit custom bankruptcy control.
- Automatic north extraction in three-player matches, with server-validated legal declarations, win priority and normal robbery/replacement handling.
- World, room and personal settings, administrator-controlled hand visibility and optional safe invitation teleportation, plus transferable room ownership.
- Pregame wind drawing for ordinary tables and randomized seating for automatic tables, with server-verified mounts before readiness and a fresh assignment for each match.
- Per-seat bot selection and difficulty controls throughout room preparation, with wind reservations and host ownership preserved during repositioning.
- Bot tile-efficiency, visible-tile availability, open-yaku calling and public-information push/fold analysis, with bounded easy, normal and hard policies.
- Editable rules with named presets, independent scoring and progression options, physical red-set restrictions, starting/return/target points, and fixed or floating-player placement bonuses. Drafts use explicit apply/cancel and are validated by the server; complete rules are retained in saves and native replays.
- Undo dye restores ordinary fives from red fives while preserving their other components.
- Tile-name and mpsz tooltip preferences, defaulting to localized names; flowers use 1q-8q, with four seasons followed by plum/orchid/bamboo/chrysanthemum in Kansai or fortune/prosperity/longevity/nobility in Kanto.
- Craftable mahjong dye, reusable creative mahjong dye, separate box compartments and atomic 136/144-tile face printing with built-in Kansai and Kanto designs.

### Changed

- Match automation controls remain directly interactive when compact or expanded, with localized short labels, full-name tooltips, visible state indicators and a separate collapse arrow.
- Overhead view fits the complete table between the HUD and private hand, adapting to viewport size with reduced perspective distortion.
- Localized room controls use natural opponent names, action captions are centered, and clocks and world-overlay prompts use text shadows for readability.
- Tile-face printing is performed inside the box with mahjong dye; stonecutting prepares raw blanks and recipe viewers describe the surviving crafting operations.
- Red dora dye and undo dye each yield four per craft; 136/144-tile box face printing produces and restores ordinary no-red sets.
- Boxes may hold spare tiles and supply the exact matching 136/108-tile subset required by the selected rules. Rule screens distinguish preset options, rule details and custom settings; unavailable red compositions show a shortage tooltip.
- Padded wooden stools have their tapered legs and carpet/slab/fence recipe restored while retaining the current seated and overhead cameras.

### Fixed

- Red dora options validate a complete matching set, including four ordinary fives per used suit for no-red play. The room home screen highlights a selected no-red configuration in red.
- Settlement score changes play once per result and retain their progress across page navigation and screen rebuilds.
- Ready bots retain their readiness while human players move to their assigned stools.
- Waiting-room status distinguishes empty places from absent participants and displays the current preparation step.
- Clockwise wall draws, counterclockwise player turns and stable upper/lower dead-wall layers across manual handling, rendering and animation.

## [0.2.0] - 2026-09-17

### Added

- Optional Ponder integration on Fabric and NeoForge, with three animated guides and four-language localization.
- Contributor guidance in `AGENTS.md`, including architecture, validation and signed delivery workflows.
- Survival crafting for ordinary and automatic tables, eleven wood families, removable sixteen-color cloth, boxes, tiles and point sticks.
- Server-authoritative manual shuffle, wall construction, starting-tile pickup and draws on ordinary tables; automatic tables keep automatic handling.
- Physical 136-tile box validation, six tile materials, genuine translucent glass, batch engraving and whole-box dyeing.
- High-resolution 3D rendered mod icon artwork for loader mod lists.
- Component-preserving furniture drops, stonecut engraving, box storage and side-drawer point-stick storage with manual payments.

### Changed

- The seated first-person view uses standing eye height at the stool position, adapts FOV to window aspect ratio to frame the playing surface, and stays consistent when opening or closing the controls.
- Mahjong stools are low floor cushions with thin wooden bases, crafted in a 2 x 2 grid from carpets and matching wooden slabs.
- Ordinary tables use physical drag gestures to shuffle scattered tiles, build walls, take packets, draw and collect a completed hand.
- Tables retain their compact 3 x 3 footprint, with matching furniture, collisions, seats and camera range.
- Melds extend left from the player's right-hand table corner beside the hand. Hands stay centered where space permits and shift left only by the clearance required by actual melds and the drawn tile, without reserving unused slots. Extracted norths use two short rows on the left, clear of the adjacent corner.
- Furniture uses original pixel wood, fabric and metal textures with beveled frames, tapered legs, padded stools and detailed case hardware.
- All six tile materials have opaque white faces; material colors and glass transparency remain on the body.
- Wall columns and layers and normal river rows now touch edge-to-edge, including width-aware sideways riichi discards.
- Texture/model generation and recipe/tag/loot generation have independent outputs and tests.

## [0.1.0] - 2026-09-16

### Added

- Three- and four-player riichi mahjong gameplay on in-world tables for Fabric and NeoForge.
- Shared scoring and rules engine with configurable table rules and open-hand games.
- Animated wall setup, dealing, discards, calls, kans, riichi sticks, and settlement stages.
- Compact in-game table controls, settlement panels, configurable decision clocks, and unanimous table-exit voting.
- Player invitations, table sound effects and custom resource-pack recordings.
- Private replay archives with timeline playback and Tenhou JSON export.
- English, Japanese, Simplified Chinese, and Traditional Chinese localization.
- Deterministic high-resolution tile artwork generation and resource-pack tile-back customization.
- GitHub Release automation for tagged versions, publishing both Fabric and NeoForge artifacts.

[Unreleased]: https://github.com/SkyEye-FAST/mchjong/compare/0.7.2...HEAD
[0.7.2]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.7.2
[0.7.1]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.7.1
[0.7.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.7.0
[0.6.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.6.0
[0.5.3]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.5.3
[0.5.2]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.5.2
[0.5.1]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.5.1
[0.5.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.5.0
[0.4.1]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.4.1
[0.4.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.4.0
[0.3.2]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.3.2
[0.3.1]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.3.1
[0.3.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.3.0
[0.2.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.2.0
[0.1.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.1.0
[#1]: https://github.com/SkyEye-FAST/mchjong/issues/1
[#2]: https://github.com/SkyEye-FAST/mchjong/issues/2
[#3]: https://github.com/SkyEye-FAST/mchjong/issues/3
[#4]: https://github.com/SkyEye-FAST/mchjong/issues/4
[#5]: https://github.com/SkyEye-FAST/mchjong/issues/5
