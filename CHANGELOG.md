# Changelog

All notable changes to MCjhong are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

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

[Unreleased]: https://github.com/SkyEye-FAST/mchjong/compare/0.2.0...HEAD
[0.2.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.2.0
[0.1.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.1.0
