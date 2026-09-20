# MChjong interface style

This is the shared visual contract for all project-owned screens, HUD cards,
inventory panels and controls. Read it before adding or changing client UI.

## Felt and brass

Use a restrained dark-teal surface, warm-white type and sparse brass accents.
The table and the tile faces remain the visual focus. Interface panels should
look like one family, not a collection of stock menus with different tints.
Use square, pixel-aligned edges, one-pixel borders and flat fills. Do not add
stone-button sprites, beveled grey frames, decorative gradients, heavy shadows,
large rounded cards or textures copied from unrelated Minecraft menus.

`client/MahjongUi.java` is the source of truth for the palette and shared paint:

| Token | ARGB | Purpose |
| --- | --- | --- |
| `BACKDROP` | `D90B1418` | Dim the world behind a modal panel |
| `PANEL` | `F018292E` | Main panel surface |
| `SURFACE` | `FF22383D` | Resting controls and cards |
| `HOVER` | `FF304B50` | Pointer hover |
| `INPUT` | `FF101E23` | Recessed fields and inventory wells |
| `EDGE` | `FF4C686B` | Quiet one-pixel separators |
| `TEXT` | `FFF1EEE3` | Main text |
| `MUTED` | `FFBDCFCA` | Supporting text |
| `DISABLED` | `FF81938F` | Unavailable controls |
| `ACCENT` | `FFE3C082` | Brass; selected state and primary action |
| `SELECTED` | `FF365851` | Selected fill |
| `POSITIVE` | `FFA9D8B8` | Positive state, paired with a label |
| `NEGATIVE` | `FFF0AAA4` | Errors/losses, paired with a label or sign |

Use these tokens rather than introducing almost-identical local colors. Tile
artwork, felt dyes, suit colors and world materials are not interface tokens.
Avoid turning every edge gold: reserve emphasis for the current selection,
keyboard focus, an important decision or the panel's small header accent.

## Components and interaction

Use `MahjongButton`, `MahjongSlider` and `MahjongEditBox`. These specialize the
native widgets' appearance, not their input protocols. Keep keyboard activation,
Tab focus, narration, sounds, pointer handling, clipboard, text selection and
slider dragging supplied by Minecraft. Do not globally reskin Minecraft widgets
or introduce a second UI framework for a project screen.

Selected and disabled are distinct states. A selected tab remains readable and
can retain focus; a disabled action remains unavailable. Show selection with a
filled surface and an accent edge, not color alone. Keyboard focus must remain
visible without hovering. Important actions may use the primary accent; do not
mark all buttons primary. Native tooltips and inventory drag highlights may be
retained where their established interaction is useful.

Use 20 logical pixels for ordinary controls and 26 for tile-preview actions.
Use 4 or 6 pixels between controls, 8 to 12 pixels inside panels, and one-pixel
rules. Keep captions within their own bounds. Long one-line labels are ellipsized
with the complete text available as a tooltip; longer explanations wrap. Do not
shrink every caption to accommodate one long translation. Use the game's font
and translated components, never a hardcoded replacement font or English-only
labels. Button text is centered within its caption area and flat, without a drop
shadow. Text over the world, including clocks, riichi prompts and animation cues,
uses a drop shadow for contrast.

## Layout and information hierarchy

Keep the title, body, supporting information and navigation visually distinct.
Secondary screens use a centered panel over a subdued backdrop. In the table
view, compact HUD cards belong near the edges so the physical hand stays clear.
Actions remain separate from informational labels. Settings use explicit tabs
and an orderly grid; selection does not replace the setting's written value.
Tooltips contain concise labels and state only. Gameplay and crafting explanations
belong in the documentation, not long hover paragraphs covering the table.
Tile tooltips use the same material, face and back-color rows for numbered,
honor and flower tiles. Localized face names are the default; the interaction
settings can switch them to mpsz notation and 1q-8q for flowers.

Design and verify against a minimum 320 x 240 **logical GUI** viewport as well as
the normal 640 x 400 viewport. Reflow or paginate content when needed, rather
than moving controls outside the screen. Actual pixel resolution depends on GUI
scale. Verify English, Japanese, Simplified Chinese and Traditional Chinese.
Retain existing no-scroll settlement navigation and replay keyboard controls.
Immersive play requires at least 480 x 300 logical GUI pixels so side rivers remain
readable. At smaller sizes its button is disabled with a localized explanation,
V cannot enter it, and resizing an immersive screen returns to the seated view.

## Table rules

The settings hub has World, Room and Personal tabs. Administrators edit world policy
in the World tab through permission-checked server commands; other players see
read-only values with a short administrator-only explanation. Room controls
and ownership transfer are paginated separately from local presentation options.

Room preparation uses separate gathering, concealed wind-selection and assigned-seat
views. The participant screen distinguishes empty places from reserved participants
who are absent, and keeps wind assignments separate from world-direction coordinates.
Per-seat bot controls live directly beneath the top seat cards throughout preparation;
ownership actions remain in the participant screen. The centered brass primary
button advances filling seats, the table-specific assignment, then readiness.
Room timing and invitations live in Settings → Room;
the Ready control reflects server-confirmed presence at the assigned stool.

The rule screen separates preset options, read-only rule details and custom
settings. Preset-supported options retain the preset identity. Red compositions
use three separate choices; unavailable choices remain visible, disabled and
labelled with a shortage tooltip. Availability comes from synchronized server
capabilities rather than access to box inventory. Every composition, including
no-red play, requires a complete matching set with enough ordinary and red fives.
A selected no-red configuration displays a red warning directly at the bottom
of the room home screen. All rule pages paginate at
320 x 240, with complete labels available on hover. The shared smoke captures
the disabled-red tooltip in all four locales at that size.
Minimum yaku han uses explicit one/two/four choices and match length uses
East-only/East–South choices. Bankruptcy stays visible in the preset overview
and is editable in custom match flow; its tooltip states the negative/zero boundary.

Personal interaction settings include default-off tenpai hints. A small diamond
with an exclamation mark appears at the lower right when valid waits are available.
Hover or native keyboard focus opens structural waits and unseen-copy counts,
including exhausted waits at zero. The native narration includes every wait and count.
Hovering over or selecting a legal discard previews the resulting waits; retain
the last preview while the pointer travels to the diamond, and clear it on a new
decision, cancellation or disabled hints. Counts combine red and ordinary fives
and never claim to reveal the actual wall. Anchor the popup above the projected
private hand in seated play and above `TableHand` in immersive play, leaving room
for decisions, clocks and automation. Closed settings persist locally and reset
disables hints.

Seated HUDs place compact honba/riichi-stick counts and 14-pixel dora indicators
between the header and player cards. Respect the independent DEPOSITS and DORA
preferences. Names, winds and points keep their own rows. With MELDS enabled,
an additional card row shows actual resource-pack meld faces at 5–8 pixels; use
a localized group count when all melds cannot fit. Immersive cards retain their
board positions and the board owns the full meld display. In both views, STATUS
shows riichi as a small white stick with a red center and a persistent brass
bottom rule, with the full status in hover details. The sideways river tile
continues to identify the declaration discard.

Immersive play uses an opaque screen-space surface, independent of world lighting,
ceilings and camera orientation. Reflow opponents, rivers and melds into the available
rectangle rather than projecting the physical square furniture. Put the viewer's
interactive hand and melds along the bottom. Place immersive automation in a
horizontal strip below the hand, with action buttons above it. Seated automation
keeps its side column. Three-player layouts place both opponents at the sides.
Anchor all rivers directly against one central score panel, with six tiles per
row growing outward and a shared tile size across seats. The panel carries the
round, remaining tiles, seat winds and scores facing each owner. Keep opponent
hands on the outer rails and melds at each owner's right corner. Place compact
portrait/name cards beside their seats; rules, deposits and dora stay at the top.
Use the same resource-pack tile faces and server-issued actions as seated play.
Reserve at least 8 × 12 logical pixels for river tiles and a 10-pixel width for
opponent meld tiles. Allocate river depth per seat and compress the central score
panel before reducing tile size; concealed outer hand rails can shrink to a 6-pixel
width ahead of melds.
Small immersive windows use a 20-pixel private hand and a single action row.
When dense rivers need the central space, winds and scores move onto seat cards;
the center retains a compact round and remaining-tile summary. Long opponent meld
rails wrap inward at the owner's corner while retaining the minimum face width.
Player names have a small skin portrait immediately before them; practice bots
use a distinct robot portrait. Keep native font sizes and ellipsize long names,
retaining the complete name in the card's hover details.

## Mahjong box

The box has its own registered menu type and `MahjongBoxScreen`. Never use the
generic chest menu, title matching or a replacement of vanilla chest screens.
The left side contains 45 tile slots, nine point-stick slots and 36 player slots.
The right side shows tile/stick counts, set readiness, a dedicated dye slot and
face-preset controls. Keep a native 16-pixel item in an 18-pixel slot pitch. The carrier slot
has an accent border, a small lock mark and an explanatory tooltip.

Set readiness must use `MahjongSupplies.deck`, including composition, material
and back-color checks. A total of 136 tiles alone is not proof of a playable set.
The summary consumes synchronized menu contents. Client styling cannot move,
sort, create or authorize inventory contents. The server retains carrier locks,
invalid-item rejection and native click/shift/drag/swap conservation rules.
Synchronize the carrier index with ordinary menu data, not a parallel payload.

## Physical table layout

The physical table reserves a 3 x 3 block footprint around a 2.875-block frame
and a 2.625-block playing surface. `TableGeometry` owns these and the seat
dimensions; the furniture mesh, footprint colliders, placement checks, stool
lookup and dismount positions must use those same dimensions.
Keep this compact footprint; do not enlarge the furniture to avoid hand layout.

Melds start at the player's right-hand table corner and extend left along the
same depth as the hand. Never move melds forward into a rail between the hand
and the wall. The concealed hand stays centered on the table while its right
edge, including any drawn tile and draw gap, clears the actual meld bounds by
`TableScene.HAND_MELD_GAP`. When they do not fit, shift the hand left only by the
missing clearance. Do not use a fixed left offset or center the hand in the
entire remaining space. Do not
reserve absent melds or drawn tiles. Drawing can move a constrained hand only as
far as the additional tile requires; an unconstrained hand does not move.
Account for open/closed/added kans and sideways calls without moving
earlier melds away from their corner. Extracted norths form two short rows on
the left, with clearance from the adjacent player's right-corner melds.

Use `TableScene` and `MeldLayout` for rendering and picking together. Derive
occupied widths from the real `TileMesh` dimensions, including sideways called
tiles and front-aligned added kans. All tiles in a meld share the same bottom
edge toward their owner. An added-kan tile lies flat immediately in front of
the sideways called tile, toward the center, at the same height.
Concealed hand tiles, wall tiles, river tiles and adjacent melds touch edge to edge.
Keep the deliberate drawn-tile and hand-to-meld gaps separate from those physical
contact rules. Keep all four seat orientations and exposed
hands within the playing surface. A stored box must not cover an active hand.
Extend wooden rails and cloth at their existing texture
density rather than stretching the whole furniture mesh. Camera limits and
defaults must keep the compact table and its right corner usable from the seated view.

The default first-person seated camera is 2 blocks from the center and
2.20 blocks above the table's base, aiming at the felt 0.20 blocks toward the
viewer so the hand does not obscure the rivers and central display. It retains
the same position with the controls open or closed, and permits free looking.
Native first-person picking uses that same eye position.
The seated world FOV expands as needed for the near playing-surface corners and
window aspect ratio, while preserving wider player FOV settings. It stays stable
while freely looking around; table projection and picking use the rendered FOV.
Holding the Inspect binding (C by default) smoothly moves the eye 0.28 blocks toward
the table and narrows the FOV by a 1.45 tangent ratio. Rendering and picking share
the sampled pose. Inspect reduces mouse and arrow sensitivity to 40% at full progress.
Right-drag adjusts yaw and pitch after a four-logical-pixel deadzone; the wheel changes
distance in 0.12-block steps. Shift-right-drag pans the target along the seat-local
table axes, bounded to 0.55 blocks on each axis. Holding arrows adjusts yaw/pitch
at 20 degrees per second. Home restores distance, height, direction, target and
inspect progress. Minecraft controls third-person views. Camera sliders preserve
the current look direction; saved personal adjustments remain adjustable, and Restore defaults applies
the current elevated seating view. The top-bar view button or V switches to the
immersive GUI. `TableHand` displays only the recipient's own hand along the bottom,
retaining the drawn-tile gap and normal selection, discard and riichi controls.
Its left edge stays on a fourteen-tile rail, with exposed melds fitted to its right.
Action buttons stay above it. Switching views preserves world camera orientation;
closing the overlay reveals the seated world. Third-person remains under Minecraft's
control. Seat cards retain names, wind and scores in both presentations.
Inspect, view, reset, riichi, pass and drawer controls are registered Minecraft
key mappings on both loaders. Help text displays the current bindings. Selection
uses pointer clicks and the existing confirmation controls.
The active immersive clock replaces the footer help line instead of covering melds.
Tile highlights follow the beveled
front and back rims and the side edges in the animated world pose, with depth testing.

For a focused seating check, use `:runSmokeClient -PsmokeSeating=true` and
`:neoforge:runSmokeClient -PsmokeSeating=true`. These reuse the furniture, seating
and private-deal captures, the zero-to-four-meld matrix at both viewport sizes,
and immersive rivers with the hand and expanded automatic controls. They then
inspect the camera with controls closed and the third-person stool pose.
Evidence goes to each loader's `build/smoke/seating-evidence`.

Riichi deposits occupy four lanes in the central area, above the automatic display
or on the ordinary table's felt; carried deposits remain visible between hands.
The automatic table's active-match overlay includes collapsible controls at the
lower left: sort hand, claim wins, skip calls, discard drawn tiles, and (in three-player
matches) extract norths. Both compact and expanded rows are individual toggle buttons;
seated rows retain 20-pixel hit areas with inset 16-pixel surfaces and no vertical
gap. A quiet side handle changes their presentation, retaining a 20-pixel hit area
and a visible keyboard focus outline. Immersive controls retain the horizontal strip.
Compact rows use localized
single-character labels, while expanded rows show full names on up to two lines.
Filled/hollow indicators distinguish states alongside the shared selected surface.
Tooltips and narration always contain the full option name and its on/off state.
Keep a clear gutter between these controls and the action buttons, including at
320 x 240 seated and 480 x 300 immersive. These preferences belong to the server's
seated player and are acknowledged before another toggle is enabled. Keep keyboard
focus across snapshot updates and collapse/expand. Countdown, riichi and animation
text stay in the action-side gutter. Sorting starts enabled; the other options start
disabled. A legal win takes priority over automatic north extraction, discards and
skipped calls. Without automatic wins, the win decision remains explicit. Automatic
north extraction uses only legal server actions, independently of the skip-calls
preference, and retains the normal robbery and replacement-draw flow.
Riichi draws and ordinary discards advance after
12 server ticks on both tables, retaining legal concealed-kan and north-extraction
choices. Ordinary tables start with a 30-second move allowance and 120-second
hand reserve; lobby hosts can edit both values.
Ordinary tables present their central deposits directly on the felt. Automatic tables use a compact
seven-segment display, large localized wind characters, round pips and seat lamps.
English winds use E/S/W/N; Chinese and Japanese use their localized characters.
Scores use a .048-block digit height, with width fitting only for unusually long
values. Each score display faces its owner, with glyph tops pointing toward the
table center. The wind characters use the resource-pack font, keeping explanatory
labels off the felt and the riichi-deposit lanes clear.

## Acceptance and future changes

Run `gradlew.bat buildAll` and both `:runSmokeClient` and
`:neoforge:runSmokeClient` after shared menu/widget changes. Check the resulting
screenshots rather than treating compilation as visual verification. Cover
small-window layout, keyboard focus, disabled/selected states, box carrier
synchronization and real inventory transactions. Keep geometric regression
tests for conditional hand centering, minimal left shifts, drawn-tile clearance,
meld/wall clearance and tile contact.
Inspect the zero-to-four-meld screenshots and the four-open-kan fixture at both
640 x 400 and 320 x 240 logical resolutions. The complete tile bounds must stay
visible beside the hand, not just the center of the rightmost meld. Remove only
the smoke's own dropped-item fixtures before those screenshots. Placement must
reject obstructed outer corners and headroom without consuming an item; breaking
an outer corner must remove all occupancy cells and return equipment once.
Placement must also succeed with obstructions just outside the 3 x 3 footprint
without overwriting them or reserving extra cells. Compare the two-open-kan
waiting/drawn screenshots: the waiting hand stays centered, and only the actual
draw causes a minimal shift. Geometric tests cover every call type and source,
all four seats, standing/exposed tiles and waiting/drawn/post-call hands.
Update this document and the shared tokens together when intentionally changing
the style; do not establish a competing set of local widgets or palette values.

The shared smoke captures compact and expanded match controls in all four locales
at 640 x 400 and 320 x 240. The live four-player match toggles each option once in
each presentation, using both pointer and keyboard activation. A live three-player
match verifies the additional north button and its server acknowledgements at
320 x 240 seated. Immersive captures use 480 x 300 or larger. Lobby controls are checked separately.
Native first-person screenshots cover a right-hand tile and point stick. Each
table has one carried-deposit fixture.
The wait-preview smoke additionally captures pointer hover and keyboard focus
at 640 x 400 and 320 x 240 seated and 480 x 300 immersive in all four languages,
with thirteen waits, five indicators, carried sticks, riichi and four-kan summaries.
The immersive preview begins with pointer-only discard hover to check that moving
onto the diamond preserves the preview without selecting or submitting a tile.

On Windows, `tools/Review-Smoke.ps1` creates labelled contact sheets from the
original screenshots. Supply the evidence folder, filename pattern, run start
time and output name, for example:

```powershell
./tools/Review-Smoke.ps1 -Evidence build/smoke/evidence `
    -Pattern '54-table-options-*.png' -Since (Get-Date '2026-09-18T11:30:00') -Name controls
```

Use the actual start time of the run being reviewed. The tool checks source
timestamps and keeps the original screenshots alongside the contact sheets.

Native supply containers reserve a 24-pixel logical bottom strip for optional
recipe-browser controls. The 55-slot box uses a 304 x 216 panel and the four-row
point-stick drawer a 286 x 216 panel. Their slot pitch remains 18 pixels; compact
header and inventory gaps keep every native slot visible at 320 x 240. Screen
bounds provide one source for viewer exclusion areas and native click tests.
The JEI custom recipe categories reuse `MahjongUi` panel, slot and text tokens.
