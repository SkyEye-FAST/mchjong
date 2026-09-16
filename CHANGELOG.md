# Changelog

All notable changes to MCjhong are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Survival crafting for ordinary and automatic tables, eleven wood families, removable sixteen-color cloth, boxes, tiles and point sticks.
- Server-authoritative manual shuffle, wall construction, starting-tile pickup and draws on ordinary tables; automatic tables keep automatic handling.
- Physical 136-tile box validation, six tile materials, genuine translucent glass, batch engraving and whole-box dyeing.
- Component-preserving furniture drops, stonecut engraving, box storage and placed point-stick interactions independent of scoring.

### Changed

- Tables reserve a 5 x 5 footprint with a larger playing surface and matching furniture, collisions, seats and camera range.
- Melds extend left from the player's right-hand table corner beside the centered hand, rather than occupying a forward rail; extracted norths use the left side.
- Furniture uses original pixel wood, fabric and metal textures with beveled frames, tapered legs, padded stools and detailed case hardware.
- All six tile materials have opaque white faces; material colors and glass transparency remain on the body.
- Wall columns and layers and normal river rows now touch edge-to-edge, including width-aware sideways riichi discards.
- Texture/model generation and recipe/tag/loot generation have independent outputs and tests.

### Removed

- The bundled patterned-back resource pack and both loader registrations. Custom resource-pack backs remain supported.

## [0.1.0] - 2026-09-16

### Added

- Three- and four-player riichi mahjong gameplay on in-world tables for Fabric and NeoForge.
- Shared scoring and rules engine with configurable table rules and open-hand games.
- Animated wall setup, dealing, discards, calls, kans, riichi sticks, and settlement stages.
- Compact in-game table controls, settlement panels, configurable decision clocks, and unanimous table-exit voting.
- Player invitations, table sound effects, system speech, and custom voice packs.
- Private replay archives with timeline playback and Tenhou JSON export.
- English, Japanese, Simplified Chinese, and Traditional Chinese localization.
- Deterministic high-resolution tile artwork generation and an optional patterned tile-back resource pack.
- GitHub Release automation for tagged versions, publishing both Fabric and NeoForge artifacts.

[Unreleased]: https://github.com/SkyEye-FAST/mchjong/compare/0.1.0...HEAD
[0.1.0]: https://github.com/SkyEye-FAST/mchjong/releases/tag/0.1.0
