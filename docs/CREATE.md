# Create workshop

Create machines produce, print, dye and pack mahjong supplies without opening
the mahjong box menu. All operations use the existing item-data and box rules.

## Production

| Machine | Materials | Output |
| --- | --- | --- |
| Mechanical saw | One existing tile material, including each wood plank type | 24 blank tiles |
| Mechanical saw | One bone block | 32 blank point sticks |
| Mixer and basin | One each of black, red, green, blue and white dye | Two mahjong dyes |
| Mixer and basin | One red dye | Eight red dora dyes |
| Mixer and basin | One black dye | Eight undo dyes |
| Mixer and basin | 16 identical blank sticks and one marking dye | 16 marked sticks |

Set the saw's output filter to the desired tile material or point stick. Six
materials yield 144 tiles. Stonecutting still yields 16 tiles or 24 blank sticks
per material. Stick dyes keep their existing values: white 100, blue 1,000,
yellow 5,000, red 10,000 and black -10,000.

## Boxes and printing

Send a chest through a deployer holding leather, a deployer holding an iron
nugget, then a mechanical press. One pass completes the box. Hand crafting uses:

```text
L S L       L = leather, S = wooden slab
S C S       C = chest
. I .       I = iron nugget, . = empty
```

A printing plate uses three iron ingots across the bottom row, with paper above
the middle ingot for Kansai faces or bamboo for Kanto faces. Both designs use
`mchjong:mahjong_printing_plate` and the existing `face_preset` value.

Supply a basin under a press with an empty box, **144 identical blank tiles**,
one mahjong dye and a printing plate. Feed the blanks as 64 + 64 + 16. Material,
back color and all other blank data must match. The output contains four ordinary
copies of each numbered/honor face and one of each flower, for 144 tiles total.

The plate is returned intact. Route it back with a filtered return line and feed
it before the blanks and box to reserve the press for printing instead of
packing. Previously packed blanks can also be completed to 144 and printed.
Blocked output stops the transaction without consuming materials.

## Dyeing and packing

In a mixer and basin, **one dye treats two targets**. A target is one whole tile
stack or one box; the pair may contain both kinds. Two boxes and one blue dye
produce two blue-backed sets. Identical loose tiles merge to their normal stack
limit. One undo dye removes back coloring from two targets. Both targets must
change before processing starts.

Box recoloring preserves faces, red-five state, preset, material, names, sticks,
dice and other valid contents. Glass tiles use their colored-glass appearance.
A deployer applies red dora dye to one ordinary 5m, 5p or 5s at a time; undo dye
restores one red five. Each application consumes one reagent and processes one
tile, leaving the rest of the transported stack unchanged.

To pack supplies, feed a box and loose tiles, sticks or dice into a basin under
a press without a printing plate. Matching stacks merge first. All 45 tile
slots, nine stick slots and the dice slot retain their capacity rules. Overflow
leaves the inputs untouched. Adding red 5m × 1, red 5p × 2 and red 5s × 1 to a
standard 144-tile box fills its 45 tile slots and supplies all red-five presets.

JEI and EMI show executable examples. Ponder's Mahjong workshop category
provides production and dyeing tutorials on printing plates, incomplete boxes
and survival dyes, in all four languages.

## Compatibility and development

This branch targets Minecraft 1.20.1 on Forge and Fabric, using Create 6.0.8
and Create Fabric 6.0.8.1 respectively. Install the matching Create distribution
on the server and clients using the workshop. Create is optional and is not
bundled into MChjong. Versions are pinned in `gradle.properties`.

`MahjongSupplies` owns every inventory transformation. Shared `compat/create`
builds exact-NBT recipes. Loader-specific `CreatePlatform` bridges Forge item
capabilities or Fabric transactions; native machines own timing, consumption,
filtering and output routing. Static recipes share a generator with each
loader's mod-presence conditions.

Build with JDK 21; production artifacts target Java 17. The same focused
integrated client/server fixture runs on both loaders:

```text
gradlew.bat :fabric:runSmokeClient -PwithCreate=true -PsmokeCreate=true -PrecipeBrowser=emi
gradlew.bat :forge:runSmokeClient -PwithCreate=true -PsmokeCreate=true -PrecipeBrowser=jei
```

Fresh `PASS.txt`, `create-checks.txt`, browser evidence and Ponder screenshots
are written under each loader's `build/smoke` directory. `withCreate` also
enables the matching Ponder runtime for development.
