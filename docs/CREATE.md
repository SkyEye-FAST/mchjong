# Create workshop

Create machines can produce, print, dye and pack mahjong supplies without opening
the mahjong box menu. The workshop uses the same item components and container
rules as hand crafting and the box menu.

## Production

| Machine | Materials | Output |
| --- | --- | --- |
| Mechanical saw | One existing tile material, including each wood plank type | 24 blank tiles |
| Mechanical saw | One bone block | 32 blank point sticks |
| Mixer and basin | One each of black, red, green, blue and white dye | Two mahjong dyes |
| Mixer and basin | One red dye | Eight red dora dyes |
| Mixer and basin | One black dye | Eight undo dyes |
| Mixer and basin | 16 identical blank sticks and one marking dye | 16 marked sticks |

Set the saw's output filter to the desired blank tile or point stick. Six tile
materials yield a 144-tile set. The ordinary stonecutter still yields 16 blanks
per material; ordinary crafting yields 24 blank sticks per bone block.

For marking sticks, white means 100 points, blue 1,000, yellow 5,000, red 10,000
and black -10,000. Each mixer batch uses one dye rather than one dye per eight
sticks at a crafting table.

## Boxes and printing plates

Assemble a box by sending a chest through a deployer holding leather, a deployer
holding an iron nugget, then a mechanical press. One pass completes the box.
The crafting-table route instead uses a chest, three wooden slabs, two leather
and an iron nugget.

```text
L S L       L = leather, S = wooden slab
S C S       C = chest
. I .       I = iron nugget, . = empty
```

A printing plate uses three iron ingots across the bottom row and paper above
the middle ingot. The reusable `mchjong:mahjong_printing_plate` can retain its
custom name. The press prints the default Kansai faces; the mahjong box switches
finished sets to any available face preset.

## Printing a complete set

Supply a basin under a press with an empty box, **144 identical blank tiles**, one
mahjong dye and a printing plate. A normal inventory holds the blanks as
64 + 64 + 16; the basin accepts these separate stacks without increasing item
stack limits. Every blank must have identical material, back coloring and other
components. The result has Kansai faces, with four ordinary copies of each numbered/honor face
and one of each flower: 144 tiles total.

The plate is returned as a separate output. Route it back to the printing basin
with a filtered return line. Feed the plate before the blanks and box so the
press reserves that basin for printing rather than packing. Previously packed
blanks can also be completed to 144 and printed with the same operation.

Keep a complete output route available for both the box and plate. A blocked
output pauses processing without consuming materials. Printing retains the blank
tiles' material, back color and custom components, and the box's own name.

## Coloring backs

In a mixer and basin, **one vanilla dye treats two targets**. A target is one
whole tile stack or one valid mahjong box; a pair may contain both kinds. For
example, two complete boxes and one blue dye produce two blue-backed sets.
Supply two separate occupied stack slots: equal tiles naturally merge until
their normal maximum stack size is reached.

Every tile inside a box is recolored together. Tile faces, red-five state, face
preset, material and names are retained, as are the box's point sticks, dice and
other valid contents. Glass tiles use their colored-glass appearance.

One undo dye removes back coloring from two targets through the same mixer
operation. Both targets must change; an already matching target waits for a
different batch without consuming the reagent. The mixer changes backs only.

## Red fives and packing

A deployer holding red dora dye changes one ordinary 5m, 5p or 5s on a belt or
depot into its red counterpart. A deployer holding undo dye restores one red five.
Each application consumes one tile from the transported stack and one reagent;
the remaining tiles retain their original components.

To pack supplies, put a box and loose tiles, point sticks or dice into a basin
under a press, without a printing plate. Matching components merge before empty
slots are used. The transaction respects all 45 tile slots, nine point-stick
slots and the dice slot; it never places supplies in the dye compartment.
Insufficient space leaves the inputs untouched. The resulting box can go into
the mahjong table's internal equipment storage.

Packing four spare reds into a standard 144-tile box produces a universal stock:
one red 5m, two red 5p and one red 5s. The 148 tiles occupy exactly 45 tile slots
and cover the no-red, three-red and four-red options. Their material, back and
face preset must agree with the printed set for those rule options to use them.

On NeoForge, JEI, EMI and REI show component-aware workshop examples alongside
the ordinary recipes, with animated Create machines and their basins. Ponder's
**Mahjong workshop** category provides production and dyeing
guides on the printing plate, incomplete box and survival dyes, in all four
supported languages.

## Compatibility and implementation

This branch supports Minecraft 1.20.1 with Forge and Create 6.0.8 or Fabric
and Create Fabric 6.0.8.1.
Install Create on the server and clients using the workshop. Its dependency is
optional; the base mod continues to load independently. The loader's generated
Create recipes are guarded by a mod-presence condition. Dependency versions
remain in `gradle.properties`.

`MahjongSupplies` owns printing, back coloring, red-five conversion, stick marking
and atomic packing. Shared `compat/create` supplies immutable, exact-NBT recipes
to native Create machines; Create controls processing time, ingredient
consumption, filtering and output handling. Narrow machine-boundary adaptations
allow repeated supply stacks and reserve sufficient output space for large
batches. Loader-specific `CreatePlatform` bridges Forge item capabilities or
Fabric transactions. The animated JEI/EMI/REI examples call these same adapters.

Build with JDK 21; production artifacts target Java 17. For development, add
`-PwithCreate=true` to either loader's run. The focused checks share the same
native transaction fixture for basin and saw inventories, output blocking,
printing, coloring, marking and packing:

```text
gradlew.bat :fabric:runSmokeClient -PwithCreate=true -PsmokeCreate=true -PrecipeBrowser=emi
gradlew.bat :forge:runSmokeClient -PwithCreate=true -PsmokeCreate=true -PrecipeBrowser=jei
```
Fresh `PASS.txt`, `create-checks.txt`, browser evidence and Ponder screenshots
are written under each loader's `build/smoke` directory. `withCreate` also
enables the matching Ponder runtime for development.
