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
action index and revision, never tiles or a claimed score. Snapshots are built for
each recipient: opponents' concealed tiles and unrevealed wall tiles are replaced
with hidden sentinels **before serialization**. Persistent server NBT is separate
from the client update tag. A table is not a global singleton.

The seated camera remains in the Minecraft world. The interaction overlay does not
draw a replacement 2D table; it projects actual 3D tile locations and connects legal
choices to them with elbow lines. Rendering is read-only and cannot advance play.

Build: `gradlew.bat buildAll`. Loader-specific development runs remain
`gradlew.bat runClient` and `gradlew.bat :neoforge:runClient`.

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
