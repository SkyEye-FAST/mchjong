# MCjhong architecture

The current checkout builds both loaders. The root project is Fabric; `neoforge`
uses the existing NeoForge 1.21.1 toolchain. No release branch history is rewritten.

* `engine`: Minecraft-independent Java domain model, with one small, typed Kotlin
  adapter to the MIT-licensed mahjong-utils scoring and hand-analysis library.
* `common`: blocks, seats, server authorization, private snapshots, rendering,
  world-anchored interaction and translations. Both loaders compile these sources.
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

Tile faces and tile backs are independent materials. Optional patterned backs
are a native, disabled-by-default resource pack registered by each loader's
client entry point. Resource selection and reloads do not affect server rules,
tile IDs or private snapshots. See `ASSETS.md` for the resource contract.
