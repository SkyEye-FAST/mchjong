# Project instructions

Before changing client screens, widgets, HUDs, inventory UI or physical table
layout, read [docs/UI_STYLE.md](docs/UI_STYLE.md) and follow its shared visual and
interaction contract. Extend the existing `MahjongUi`, `MahjongButton`,
`MahjongSlider` and `MahjongEditBox` components instead of adding parallel themes
or stock Minecraft button painting.

Keep shared code in `common`, loader registration in the appropriate Fabric or
NeoForge entry point, and rules in `engine`. Inventory authorization belongs to
the server menu; appearance and read-only summaries belong to the client screen.

Preserve unrelated local edits. Use signed Git commits. Before committing a
complete shared UI/menu batch, run `gradlew.bat buildAll`; run both client smoke
tasks and inspect the screenshots for visual or interaction changes.
