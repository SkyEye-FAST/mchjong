# Changelog

All notable changes to MChjong are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
- Immersive play arranges table action and opponent hands around a centralized round and score panel.
- Engine settlement calculation, hand analysis helpers, replay recorder, and Tenhou export subsystems migrated to Kotlin.

### Fixed

- Stool interactions ignore right-clicks while sneaking, preventing accidental sitting when placing blocks or handling items near tables.
- Wall break opening orientation follows dealer position and dice throw direction.
- Pregame wind drawing lotteries generate uniform seat distributions without correlation bias.
- Mod display name corrected from MCjhong to MChjong across manifests, documentation, and translations.

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

[Unreleased]: https://github.com/SkyEye-FAST/mchjong/compare/0.4.1...HEAD
[0.4.1]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.4.1
[0.4.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.4.0
[0.3.2]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.3.2
[0.3.1]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.3.1
[0.3.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.3.0
[0.2.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.2.0
[0.1.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.1.0
