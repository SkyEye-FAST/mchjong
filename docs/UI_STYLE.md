# MChjong interface style

Use [TERMS.md](../TERMS.md) for terminology, localized phrasing and player perspective.

This is the shared visual contract for all project-owned screens, HUD cards,
inventory panels and controls. Read it before adding or changing client UI.

## Felt and brass

Use a restrained dark-teal surface, warm-white type and sparse brass accents.
The table and the tile faces remain the visual focus. Interface panels should
look like one family. Use square, pixel-aligned edges, one-pixel borders and flat
fills. Keep decoration restrained and use the shared MChjong palette and widgets.

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
| `DANGER` | `FFA33232` | Persistent local furiten badge |
| `ON_DANGER` | `FFFFFFFF` | White text on the furiten badge |

Use these shared tokens for interface colors. Tile artwork, felt dyes, suit colors
and world materials retain their own palettes.
Primary buttons use dark teal with light text, a brass outline and a bottom
accent strip. Brass also marks selected navigation, focus, locked slots and the
shared backdrop's top rule. Keep ordinary borders quiet and button fills dark teal.
Always display the full Immersive View label, including in
compact toolbars. Reserve emphasis for the current selection,
keyboard focus or an important decision. Selected navigation uses a short bottom
rule, while ordinary controls and titles retain plain edges.

## MCR physical layout

`TileDimensions` owns two physical envelopes. Riichi and Taiwanese mahjong share the
SLIM 33 reference, H33 × W24 × D17.5 mm. With the 2.625-block felt calibrated to
850 mm, their height/width/depth are .101912/.074118/.054044 blocks. MCR and
Sichuan mahjong retain .160/.104/.0726 blocks. These are independent axis dimensions,
not a uniform scale. [SLIM 33 specifications](https://rongho.tw/product/0433-folding/)
and [its Taiwanese mahjong set](https://rongho.tw/product/mahjong-tw-rongho/) establish the
shared reference; tile families are also listed by
[Matsuoka](https://www.mahjong.co.jp/automahjongmachinetop/tile/).
World meshes, layout spacing, occupied bounds, picking, immersive solids and
replay table faces read this same envelope. The canonical mesh receives its
axis transform exactly once, after yaw and pitch.
Seat-local x runs left to right as seen by the seated player; z increases toward
that player. Rotate the complete local poses by `TableGeometry.orient`.

Riichi retains its native wall arrangement. MCR, Sichuan mahjong and Taiwanese mahjong retain four
straight, double-layer walls tilted 12 degrees with the same handedness.
`TiltedWallLayout` calculates the wall half-length from stack count and tile
width. The tangent offset and normal position jointly leave .012-block corner
clearance, keep the ends within the felt margin, and leave the center housing
and first river row clear. The normal clearance also keeps the housing visible
from the default seated camera. Shorter Sichuan mahjong walls and smaller Taiwanese mahjong tiles
therefore bring their wall ends inward instead of inheriting an eighteen-stack
large-tile rail. Opposite-wall distances are measured along the wall normal.

MCR retains eighteen stacks per side and 144 tiles. Sichuan mahjong retains 108 tiles and
thirteen/fourteen stacks according to `eastWestLongWall`. Stack pitch equals tile
width. Physical slot indices, opening and front/tail traversal remain engine-owned.
Check oriented bounds rather than overlapping world-axis AABBs. The complete wall
stays inside the felt and keeps the automatic instrument panel visible.

Rivers pack physically present discards six per row, preserving history indices
while omitting called-away entries. Derive the first row center from
`TableIndicator.HALF_WIDTH + tileHeight / 2 + .005`; offset the row by half a tile
width to separate neighboring owners' corners. Later rows advance toward the
player by at least one actual tile height. When a remaining wall segment occupies
a later row, slide the row into available space or use the next available row.
`ChineseTableLayout` checks the actual oriented walls, public tiles, hands and
center housing. Already emptied wall positions are usable river space. An unusually long river
can start another six-column bank when its outward lane is full. Rendering
and picking consume the resulting piece poses together.

Chinese melds begin in the owner's left corner, with chronological groups
extending right. A neighboring wall end can temporarily occupy part of that
public area; place an actual group in front of that end or advance it right to
the next clear space. Preserve group order and physical size. Keep the concealed
hand centered unless an actual meld at the hand's depth requires the minimum
rightward displacement. Reserve no absent groups. The called tile's sideways
position still identifies its supplier: previous player left, opposite middle,
next player right. A chow uses the previous player. MCR kongs use one flat
four-tile row. Sichuan mahjong retains its native added-kong arrangement. Concealed kongs
retain native visibility during play and reveal after the hand ends.

MCR and Taiwanese mahjong flowers join the same left-side public area as melds. Present
meld groups are packed intact in order, followed by individual flowers along
the same baseline. Continue inward on another row when the current row cannot
fit a complete group. No flowers or absent melds reserve space. The concealed
hand stays centered and moves right only to clear actual public pieces on its
row. Taiwanese mahjong uses the small envelope for sixteen concealed tiles plus a draw and
up to five meld groups. Actual remaining wall occupancy constrains public and
river placement; source indices and hidden identities remain unchanged.

Geometry acceptance uses conserved engine positions: complete walls before any
hands or rivers exist, actual post-deal slots, and multiple openings advanced
through midgame, late game and termination. Each accepted action must preserve
the original physical stock. Complete walls combined with maximum hands, melds
and four river rows are not gameplay space constraints.

The MCR seat overlay uses the same world camera and tile-box picking as Riichi.
Its immersive view uses the same fixed 1280 × 800 canvas, perspective solids and
foreground hand, player plaques, center display and motion as Riichi and Sichuan mahjong.
MCR retains its native flat-kong display in the shared left public area.
Compact edge cards show player names, winds and scores. The current turn is marked
with the shared accent. Automatic flower replacement names the player currently replacing.
Clicking a legal hand tile discards it, while claims, kongs, wins and pass use localized
native buttons with tile previews. MCR uses the shared room time editor and turn
clock, reserving a clock lane below the actions and above the private hand.

## Components and interaction

Use `MahjongButton`, `MahjongSlider` and `MahjongEditBox`. These specialize the
native widgets' appearance while retaining their input protocols. Keep keyboard activation,
Tab focus, narration, sounds, pointer handling, clipboard, text selection and
slider dragging supplied by Minecraft. Apply the shared styling through these
project widgets.

Selected and disabled are distinct states. A selected tab remains readable and
can retain focus. A disabled action remains unavailable. Show selection with a
filled surface and an accent edge. Keep keyboard focus visible at all times.
Reserve the primary accent for important actions. Native tooltips and inventory drag highlights may be
retained where their established interaction is useful.

Use 20 logical pixels for ordinary controls and 26 for tile-preview actions.
Use 4 or 6 pixels between controls, 8 to 12 pixels inside panels, and one-pixel
rules. Keep captions within their own bounds. Long one-line labels are ellipsized
with the complete text available as a tooltip. Longer explanations wrap while
captions retain a readable size. Use the game's font and translated components.
Button text is centered within its caption area and uses flat rendering.
Text over the world, including clocks, riichi prompts and animation cues,
uses a drop shadow for contrast.

## Layout and information hierarchy

Lobby and settings controls share straight row baselines and four-pixel gutters.
Primary actions use dark teal with a brass outline and bottom accent on the same
footer baseline as their secondary actions. Match HUD cards and action rows share
aligned edges and baselines.
On the immersive canvas, spacing follows the content scale. Keep native rectangular
widget bounds aligned with their surfaces; preserve Tab order, narration and focus outlines.

The receipt's point strip and grade badge align with the yaku body's edges.
Reserve the final geometry before any reveal.

Keep necessary titles, body and navigation visually distinct. Lobby and settings
screens use compact centered floating panels, leaving the world visible around
them without a full-screen dim layer. Their height follows the actual option and
navigation rows, with a 30-pixel title area and pagination at small sizes. Reserve
a category column whenever the page has categories. Rule editors keep their category
rail visible from first entry through preset and award changes. Preset choices open
a separate paginated child page. Use headings and status lines
for information that helps players navigate or act. In the table view, compact HUD cards belong near
the edges so the physical hand stays clear.
Actions remain separate from informational labels. Settings use explicit category
navigation and grouped option rows. Keep each setting's written value visible
alongside its selection state. Tooltips contain concise labels and state.
Gameplay and crafting explanations belong in the documentation.
Tile tooltips use the same material, face and back-color rows for numbered,
honor and flower tiles. Localized face names are the default. The interaction
settings can switch them to mpsz notation and 1q-8q for flowers.

Design and verify against a minimum 320 x 240 **logical GUI** viewport as well as
the normal 640 x 400 viewport. Reflow or paginate content to keep controls within
the screen. Actual pixel resolution depends on GUI
scale. Support English, Japanese, Simplified Chinese and Traditional Chinese.
Select visual checks according to the changed layout or translation.
Use the full locale/viewport matrix for broad interface acceptance.
Keep settlement tabs and replay controls visible and directly clickable.
Settlement reserves the complete receipt layout before revealing individual yaku
rows. Highlight the current row with the shared accent; points precede the hand
grade. Put each ordinary yaku's han in a solid square-cornered badge directly
after the yaku name's last line; natural yakuman have no han badge. Enlarge
points and hand grades together. Leave eight logical pixels after the fan/yaku
list before the enlarged score, and draw the score without a background box. Show the hand grade in
an accent badge beneath the points, or beside them in compact receipts.
Small receipts reserve their body for the hand and yaku. The Point changes page
provides the complete score table. Reserve both badge and text
space before the readout begins. Preserve the hand layout and readout progress
on a resize or snapshot refresh.
Award rows provide short hover text, a quiet `?` cue and a clickable explanation.
Winner tabs switch hands by mouse. Rule editors keep their draft while a separate `?` opens help.
The shared compact help panel paginates text, reuses handbook diagrams and offers
Back plus an optional Handbook button. Closing the book returns through the same
parent chain, including when synchronized table state changes.
All four variants share the table, player-card, action-grid, camera, settings
and settlement design. All four share replay presentation. Rule adapters supply native winds,
flowers, void suits, yaku/fan and payment data. Receipts retain native win methods;
MCR, Sichuan mahjong and Taiwanese mahjong standings show cumulative points, without Riichi uma fields.
Dense payment ledgers paginate with the wheel. Their original
entry order and related-entry links remain visible. Taiwanese mahjong receipts show public
tai awards and transfers while keeping opponent hands and covered kongs hidden.
Taiwanese mahjong uses its own fixed 136/144-slot wall scene and accommodates sixteen-tile
hands and five melds at full tile size; Chinese artwork and placement primitives
are shared. Its lobby exposes human and built-in Bot seats, presets,
payment/reserve rules, clocks and shared Replay navigation.
All variants share one lobby layout. A left sidebar selects the rule variant;
its width and row height are independent of the number of variants. Overflow
scrolls by whole rows using the wheel or a visible, draggable scrollbar. Hidden
rows cannot receive pointer input, and a newly selected variant is brought into
view. Variant selection never appears among that variant's settings. The right
body has Room Members and Match Settings tabs, with invitation and replay
navigation on a separate row below. Keep the roster visible on arrival, with
per-seat Bot and ownership controls. Match Settings and Settings → Room share
one Match Rules entry and the same hand display, timing and convenience controls.
Player count and presets belong to the rule editor's draft. The footer
spans the full panel so primary actions retain their space at small sizes.
The header names the current preparation stage and the footer supplies one brief
next-step instruction. Leave and host dissolution stay distinct in the toolbar.
Emphasize Fill empty seats while players are missing, then seat preparation when
available. Drawing winds and readiness reuse the same footer.
Settlement uses a top action with the server's countdown in parentheses beside
the view selector (and the ordinary table's point-stick drawer), and
automatically opens final standings at the match's second stage. The roster
returns to the lobby after that stage, with leave and dissolution available there.
This responsive rule applies to ordinary screens and the seated overlay, not the
immersive table. The immersive table is the fixed virtual canvas described below;
small viewports scale that canvas uniformly rather than reflowing it.

## Table rules

The table settings hub has World, Room and Personal tabs. Administrators edit world policy
in the World tab through permission-checked server commands. Other players see
read-only values with a short administrator-only explanation. Room controls
are paginated separately from local presentation options; ownership transfer stays
in the lobby roster. During play, Settings → Room provides Room Members with
the same member labels and the active-match ownership action.
Rule editors and personal preset pages share compact panels, option rows and
aligned footers. Settings with multiple categories retain a populated left rail
beneath one concise page title. The selected navigation item
already identifies the section; show inline notices only for pending submissions,
permission restrictions or errors. Labels align left, values align right, and boolean
options use square indicators. Keep borders for keyboard focus and footer actions.
Personal presentation enum values open explicit choice lists and return to their
category. Restore category defaults affects only the current presentation category;
personal stick/voice presets are managed on their own page. Changes apply immediately
and are saved when leaving the personal settings page.
Personal presets use a full-width Stick/Voice tab row followed by a Server/Local
source row. Mark both active choices with selected fill and a bottom rule. The
list begins immediately below the source row and paginates at narrow sizes.
Settlement score tables use compact rows, quiet horizontal separators and a leading
accent for the local player. Numeric headings and values share right-aligned column
edges. The player heading aligns with names after their portraits.

Room preparation uses separate gathering, concealed wind-selection and assigned-seat
views. The participant screen distinguishes empty places from reserved participants
who are absent, and keeps wind assignments separate from world-direction coordinates.
Roster rows show names, wind assignments, presence and readiness. Bot controls
and ownership transfer occupy the right edge of the affected row. Complete names,
seat coordinates and Bot service errors remain available on hover. The footer
advances gathering to the table-specific assignment and then readiness.
Participants distinguish temporary absence, disconnection,
and an empty/released place. Disconnected human names use the negative text color;
HUD cards include their presence in the summary and hover details. Bot replacement
becomes available only after the absence grace period or a server disconnection.
When the last human leaves an active match, a centered decision panel offers to
keep the paused match or end it. Closing the panel keeps the paused match.
The personal automatic seating option starts enabled and requests the assigned
stool when allocation finishes. Turning it off keeps the coordinate-guided manual
seating flow; ordinary presence updates never force a player back onto a stool.
Room timing lives in Settings → Room, while invitations remain in the lobby's room
operations. The Ready control reflects server-confirmed presence at the assigned stool.
Equipment failures use a short footer caption, concise hover list and a paginated
help page accessible by native pointer or keyboard activation. The server chooses
the applicable requirements. A recipient-only brass particle cue at the actual
assigned stool lasts three seconds. It does not replace walking or stool interaction.
The room Hand Display page contains two independent fields. Hand Visibility selects
Visible Only to Self, Visible to Riichi Players or Visible to All Players through
an explicit choice page. Open Hands lays tiles face up and makes them public while
retaining the visibility setting. Only the host can change these fields before play. Tile fronts face their
owner. Hidden identities suppress only the printed artwork. The opaque white
plate keeps its thickness and rear cap, including when viewed through a glass
body or in a face-down wall. Nearby spectators use the world view
without occupying participant seats.

The Riichi rule editor always shows six categories: Basic Settings, Points and
Ranking, Win Scoring, Match Progress, Riichi and Calls, and Liability Payments.
Basic Settings contains one mutually exclusive player-count field, the rule preset,
open tanyao, red fives, minimum yaku han, match length and Restore Preset Defaults.
Points and Ranking includes the applicable fixed or floating placement values.
Win Scoring owns the yaku selector, including the full, partial and disabled Mahjong
Soul ancient selection. Match Progress includes bankruptcy and its negative/zero
boundary explanation. Each remaining field follows its engine-owned group and visibility.

Choose Rule Preset is a separate child page. Selecting the current preset preserves
the complete draft and unfinished input. A different Riichi preset uses `withPreset()`:
it retains the target preset's supported basic options and restores fixed details
and awards to that preset's defaults. Player-count changes use the same family when
available, or ask for a target-count preset. Canceling that choice retains the original
count and draft. Custom status derives from the complete rules and appears alongside
the preset name. Category navigation remains available in read-only states.

Apply Rules submits the complete draft and waits for an explicit server reply.
Cancel discards unsent edits. While awaiting a reply, Back leaves the editor without
claiming to withdraw the request. Rejections retain the draft. Stale or rejected
feedback offers Reload, which replaces the draft with confirmed room rules.
Category changes, pagination, resizing, help and award navigation preserve unfinished
numeric text. Invalid relevant fields disable Apply across all pages.

Preset-supported basic options remain editable when world policy disables custom
rules; fixed details and awards respect their separate permission. Disabled controls
distinguish host permission, preparation stage, forced presets and physical stock.
Red compositions use three explicit choices and synchronized server capability bits.
Every composition, including no-red play, requires a complete matching set.
All pages paginate at 320 × 240, with full labels in hover text and independent help
buttons. Minimum yaku han uses one/two/four choices and match length uses
East-only/East–South choices.

Sichuan mahjong uses Basic Settings, Kongs and Payments, and Draws and Settlement.
Taiwanese mahjong uses Basic Settings and Scoring and Payments, with award selection
on the scoring page, its existing wall reserve choices and four payment parameters.
Their preset selectors, draft navigation and acknowledgement footers follow the same
page structure while retaining their own world-policy permissions. MCR offers a fixed
rule summary and the existing explanations.

Riichi room settings include default-off convenience hints. A small diamond
with an exclamation mark appears near the private hand when analysis is available.
Hover or native keyboard focus opens structural waits and unseen-copy counts,
including exhausted waits at zero. The native narration includes every wait and count.
Show hints only for the current thirteen-tile ready shape, counting each meld as three tiles.
Clear them when the hand no longer has that shape or the room disables hints. Counts combine red and ordinary fives
and never claim to reveal the actual wall. Center the popup on the actual private
hand, directly above its projected top edge in seated play and `TableHand` in
immersive play. Keep a small gap to the tiles instead of anchoring to the action
rail. Draw the exclamation icon at the diamond's geometric center, independent
of language and font glyph bearings. Wrap waits into compact rows when side
controls or player cards restrict the space, preserving the hand anchor and
native text size.
The room host can change this setting during preparation.

Seated HUDs integrate honba/riichi-stick counts and dora into the existing 26-pixel
round/remaining header. Their stick icons use fixed HUD artwork, independent of
the selected point-stick preset. Indicators use 14-pixel faces when space permits. Compact
headers use 8-pixel faces beside the round and short remaining text beside the
stick counts. Immersive headers use 14-pixel indicators and reserve their width
on both text lines. Seated Riichi player cards share the y=38 baseline and have no
separate indicator row. Immersive left and right player cards share their top edge.
Respect the independent DEPOSITS and DORA preferences. Show a compact
red-background, white-text furiten badge below
the local player's card in either view. STATUS controls this recipient-only badge;
it uses the synchronized ron restriction and the player's own waits/discard history,
independently of optional wait previews. Its tooltip explains that tsumo is possible.
Names, winds and points keep their own rows in 24-pixel cards. With MELDS enabled, only cards with melds
grow to 40 pixels for actual resource-pack faces at 5–7 pixels, or 36 pixels for
a localized group count when all melds cannot fit. Immersive cards retain their
board positions and the board owns the full meld display. In both views, STATUS
shows riichi using the fixed HUD icon (blue with a white dot) and a persistent accent
bottom rule, with the full status in hover details. The sideways river tile
continues to identify the declaration discard.

Immersive play uses an opaque screen-space surface, independent of world lighting,
ceilings and camera orientation. A single perspective camera projects the cloth,
solid tile bodies and upright concealed opponent hands.
Tile faces and dyed resource-pack backs share that geometry and use fixed lighting.
The viewer's
large interactive hand and raised tile rack form the foreground along the bottom. Place
the Riichi viewer's melds flat on the table at the right-hand corner, with the earliest
meld nearest that corner and later melds extending left. Apply the same owner-relative
right-end rule to every Riichi seat. Chinese variants use the left-side public
layout above. Extracted norths lie in one run at the left end
of the same outer rail as the melds.
Place immersive automation in a compact centered horizontal strip below the hand, with
action buttons above the right end of the hand, clear of the central rivers and table melds. Seated automation keeps its
side column. Three-player layouts place both opponents at the sides and do not reserve
an empty opposite-seat region.

Lay the four rivers on the same table plane around the compact central device. Retain
six tiles per row and project each tile's corners, thickness and contact shadow through
the table camera. Align sideways riichi tiles to the same owner-relative top edge as
the other tiles in their row, matching seated play. The viewer's rows grow
toward the foreground, the opposite rows recede toward the far rail, and the side rivers
remain broad enough to read rather than becoming screen-edge strips. Keep opponent hands
and native melds at their owner-relative public areas. Riichi uses the right-hand end.
Place compact player plaques at the matching canvas edges: self at the bottom-left,
opponents at the left, right and top center. Keep every plaque clear of hands, melds
and rivers. Reserve the space beside the local plaque for footer help and keep its
furiten badge below it. Each plaque contains only portrait, name, wind and score;
turn, riichi and presence use small marks. The central
device carries the round, remaining tiles, turn direction and graphical honba/riichi
point sticks with counts. It must not spell out honba or deposits.
MCR, Sichuan mahjong and Taiwanese mahjong use a compact instrument panel with a
short round caption above a larger remaining-tile count. The MCR caption uses the
actual prevailing wind. Player plaques carry seat winds, authorized void suits and
the public won state. ROUND, REMAINING and TURN control the instrument independently
of the plaques' WINDS and STATUS preferences. Native panels have no deposit counters.
Use the same resource-pack tile faces and server-issued actions as seated play.
In replay diagrams, dim tsumogiri and mark tedashi. Live play keeps both at normal
brightness and distinguishes their movement by animation. The sideways riichi discard
remains an independent marker.
Perspective depth follows each tile's position: the far rail is smallest, the side
rails converge toward it, and the viewer's foreground stays full size. Animation
anchors use the same projected centers as the tile faces. Player labels are compact
translucent plaques in open table margins. Use larger portrait and score type on the
virtual canvas; toolbar, automation, score and status captions use twice the native
font size.
The bottom-left control hint uses that same enlarged size. In both views, show
the move allowance as large warm-white digits with a smaller `+reserve` in the accent color,
right-aligned above the private hand. Reserve a separate clock lane below the
action buttons. Center riichi prompts and animation cues horizontally in the upper
part of the screen, using muted text and an area clear of player cards, controls,
the clock, hint popups, the central device and the private hand. Keep the current
discard card at the right edge, clear of these elements.
Seated placement follows the animated tile-box projection, including melds and
selection lift. Immersive placement uses the raised hand's top edge. Clock labels
remain localized in hover details and narration, and the last five total seconds
use the warning color. When the move allowance expires, promote the remaining
reserve to the large digits. In seated free look, use the space below the hand
when its projection leaves insufficient room above and sufficient room below.
Size immersive toolbar buttons from their translated captions at that scale, including padding.
Riichi and animation captions, exit-vote panels and controls, settlement navigation
and player-information tooltips use the same 2x content scale. Reserve matching
line spacing in the centered caption area for multiple animation cues.
Wait previews use twice the seated size for their focus target, tile faces, counts
and heading. Reserve the enlarged popup's height when avoiding player plaques and actions.
Immersive play has exactly one 1280 × 800 virtual layout. Minecraft GUI scale, window
size and window aspect ratio must never select another immersive layout, change tile
sizes, move controls, hide controls or fall back to the seated renderer. Fit that
canvas into the current screen with one uniform `min(width / 1280, height / 800)` scale
and center it; fill all unused space with solid black letterbox/pillarbox bars. A
window smaller than the virtual canvas scales the complete image down instead of
reflowing it. Pointer coordinates are transformed back into the same virtual canvas
before hit testing, so visual and interactive geometry remain identical at every GUI
scale.
With animations enabled, a draw travels from the viewer-side wall toward the drawn-tile
slot and keeps that target slot visually empty until arrival. Discards travel from the
hand/seat edge to the river; tsumogiri uses a shorter, flatter path while tedashi uses a
longer arc. Riichi rotation occurs near landing. These animations are presentation-only
and never alter the authoritative action or tile identity.
Reserve at least 18 logical pixels of face width for immersive river tiles and 20 pixels
for opponent meld tiles. Allocate river depth per seat before reducing these sizes.
Riichi opponent melds stay in one row at the owner's corner and the hand shifts left
to clear them, retaining the minimum face width. Player names have a portrait immediately before them; practice bots use
a distinct robot portrait and maid companions use the default Reimu icon. Immersive
plaque text may scale above native size for the 1280 × 800 canvas. Ellipsize long
names and retain the complete name in hover details.

## Mahjong box

The box has its own registered menu type and `MahjongBoxScreen`. Never use the
generic chest menu, title matching or a replacement of vanilla chest screens.
The left side contains 45 tile slots, nine point-stick slots, one adjacent dice
slot and 36 player slots.
The right side shows tile/stick counts, set readiness and a dedicated dye slot.
An empty dye slot shows a short insertion hint. Vanilla dye reveals only the
back-color action; Mahjong dye reveals face and back buttons that open paginated
secondary screens. Both selectors have separate headings and retain draft choices.
Apply on the main box screen commits both selections together, consuming one
ordinary Mahjong dye only when the stored appearance changes;
creative Mahjong dye remains in the slot. Show four sample tiles beside each
face preset. Keep action visibility synchronized with the actual slot contents
and clear focus when its control disappears.
Preset pages retain the same authorized box menu and return to its inventory
without reopening it; leaving the box ends the native menu lifecycle.
Vanilla dyes in that slot recolor every stored tile back to one of the
sixteen dye colors through the explicit Dye backs action, while Undo dye removes tile-back dyeing through the Remove dye action; Mahjong dye remains the reagent for
face printing. Keep a native 16-pixel item in an 18-pixel slot pitch. The carrier slot
has an accent border, a small lock mark and an explanatory tooltip.

Set readiness must use `MahjongSupplies.deck`, including composition, material
and back-color checks. A total of 136 tiles alone is not proof of a playable set.
The summary consumes synchronized menu contents. Client styling cannot move,
sort, create or authorize inventory contents. The server retains carrier locks,
invalid-item rejection and native click/shift/drag/swap conservation rules.
Synchronize the carrier index with ordinary menu data, not a parallel payload.

The Personal scope in room settings and the mod-list configuration entry open
the same local settings and preset screens. The preset screen lets a player choose a
riichi stick and voice. The stick category shows the strip texture beside each name;
the voice category lists recording sets. Both selections are saved locally. Keep the
lists paginated at small window sizes. All four preset selectors have Server
and Local source navigation, mark built-in entries only with a gray parenthetical
in their tooltips, automatically refresh loaded entries, and show
the full name and persistent ID in each choice's tooltip.

Native supply containers reserve a 24-pixel logical bottom strip for optional
recipe-browser controls. The 56-slot box uses a 304 x 216 panel and the four-row
point-stick drawer a 304 x 216 panel. Each drawer has nine scoring slots and a
separated final slot for inactive black bust sticks, with an explanatory tooltip.
The point-stick drawer highlights the receiving player. The mouse wheel cycles
that recipient and Shift-click stores point sticks in the highlighted row.
Their slot pitch remains 18 pixels. Compact header and inventory gaps keep every
native slot visible at 320 x 240. Screen bounds provide one source for viewer
exclusion areas and native click tests. JEI recipe categories reuse `MahjongUi`
panel, slot and text tokens.

## Physical table layout

Ordinary tables show two dice in the central felt area after every wall is built.
The dealer can then focus/click the dice to pick them up and roll via
the native action control. Both faces use the item textures; hovering the region
shows a compact face + face = total panel with native narrated numeric text.
Players and spectators can switch between seated and immersive play throughout
preparation and play. Both the toolbar and the view key use the same unrestricted
presentation toggle. Automatic tables use the shared deal timeline and keep tile
actions locked until arrival. Dice and their hover target belong exclusively to
seated play.
The completed match returns dice and point-stick positions to storage.

The physical table reserves a 3 x 3 block footprint around a 2.875-block frame
and a 2.625-block playing surface. `TableGeometry` owns these and the seat
dimensions. The furniture mesh, footprint colliders, placement checks, stool
lookup and dismount positions must use those same dimensions.
Keep this compact footprint. Do not enlarge the furniture to avoid hand layout.

Riichi melds start at the player's right-hand table corner and extend left along the
same depth as the hand. Never move melds forward into a rail between the hand
and the wall. The concealed hand stays centered on the table while its right
edge, including any drawn tile and draw gap, clears the actual meld bounds by
`RiichiTableScene.HAND_MELD_GAP`. When they do not fit, shift the hand left only by the
missing clearance. Do not use a fixed left offset or center the hand in the
entire remaining space. Do not
reserve absent melds or drawn tiles. Drawing can move a constrained hand only as
far as the additional tile requires. An unconstrained hand does not move.
Account for open/closed/added kans and sideways calls without moving
earlier melds away from their corner. Extracted norths form one continuous run on
the left at the hand's depth, with clearance from the adjacent player's right-corner melds.

Use `RiichiTableScene` and `MeldLayout` for rendering and picking together. Derive
occupied widths from the rule-selected `TileDimensions`, including sideways called
tiles and front-aligned added kans. All tiles in a meld share the same bottom
edge toward their owner. An added-kan tile lies flat immediately in front of
the sideways called tile, toward the center, at the same height.
Concealed hand tiles, wall tiles, river tiles and adjacent melds touch edge to
edge. MCR, Sichuan mahjong and Taiwanese mahjong wall stacks touch along their tilted wall tangent.
Keep the deliberate drawn-tile and hand-to-meld gaps separate from those physical
contact rules. Keep all four seat orientations and exposed
hands within the playing surface. A stored box must not cover an active hand.
Extend wooden rails and cloth at their existing texture
density rather than stretching the whole furniture mesh. Camera limits and
defaults must keep the compact table and its public corners usable from the seated view.

The default first-person seated camera is 2 blocks from the center and
2.10 blocks above the table's base, aiming at the felt 0.20 blocks toward the
viewer so the hand does not obscure the rivers and central display. It retains
the same position with the controls open or closed, and permits free looking.
Native first-person picking uses that same eye position.
The seated world FOV expands as needed for the near playing-surface corners and
window aspect ratio, while preserving wider player FOV settings. It stays stable
while freely looking around; table projection and picking use the rendered FOV.
Right-drag adjusts yaw and pitch after a four-logical-pixel deadzone. The wheel changes
distance in 0.12-block steps. Shift-wheel raises or lowers the eye in 0.05-block
steps, preserving the look direction and bounded by the personal height slider's
1.35–2.50-block range. Shift-right-drag pans the target along the seat-local
table axes, bounded to 0.55 blocks on each axis. Personal Settings provides a reset
for distance, height, direction and target. Minecraft controls third-person views. Camera sliders preserve
the current look direction; saved personal adjustments remain adjustable, and Restore defaults applies
the current elevated seating view. All four variants use the same top-right toolbar order: Replays, view, Settings,
and Exit. Narrow seated overlays use short captions with the full names on hover;
normal and immersive toolbars size buttons from their translated captions.
The top-bar view button switches to the
immersive GUI. `TableHand` displays only the recipient's own hand along the bottom,
retaining the drawn-tile gap and direct discard and riichi controls.
Tile width uses a stable fourteen-tile capacity (seventeen for Taiwanese mahjong). Short Riichi
hands keep their left anchor; Chinese foreground hands stay centered and move
right only when actual projected public tiles overlap their screen band.
The Chinese foreground hand reserves its automation strip throughout play, so
changes in available controls never move the hand vertically.
Action buttons stay above it. Switching views preserves world camera orientation;
closing the overlay reveals the seated world. Third-person remains under Minecraft's
control. Seat cards retain names, wind and scores in both presentations.
Use the selected single-click or double-click mode to discard a legal hand tile.
Other declarations use their native action controls.
Native text editing, widget focus and Escape retain Minecraft's behavior.
An active turn clock suppresses the footer help line, keeping attention on the
decision controls and the separate clock lane above the hand.
Tile highlights follow the beveled
front and back rims and the side edges in the animated world pose, with depth testing.

Riichi deposits occupy four lanes in the central area, above the automatic display
or on the ordinary table's felt. Carried deposits remain visible between hands.
The automatic table's active-match overlay shares collapsible controls for all
three rules at the lower left: claim wins, skip calls and discard drawn tiles.
Riichi additionally offers sort hand and, in three-player matches, extract norths.
Both compact and expanded rows are individual toggle buttons;
seated rows retain 20-pixel hit areas with inset 18-pixel surfaces and no vertical
gap. A quiet side handle changes their presentation, retaining a 20-pixel hit area
and a visible keyboard focus outline. Immersive controls retain the horizontal strip.
Compact rows use localized
single-character labels, while expanded rows show full names on up to two lines.
Filled/hollow indicators distinguish states alongside the shared selected surface.
Tooltips and narration always contain the full option name and its on/off state.
Keep a clear gutter between these controls and the action buttons. The seated layout
is checked at 320 × 240; immersive controls always use the fixed 1280 × 800 virtual
canvas and are only uniformly scaled by the outer letterbox transform. These preferences belong to the server's
seated player, reset at the start of each hand, and are acknowledged before another toggle is enabled. Keep keyboard
focus across snapshot updates and collapse/expand. Countdown, riichi and animation
text use separate areas. Countdown stays beside the actions; riichi and animation
captions stay centered and clear of other interface elements. Sorting starts enabled. The other options start
disabled. A qualifying win takes priority over automatic north extraction, discards and
skipped calls. Without automatic wins, the win decision remains explicit. MCR uses
eight non-flower fan. Sichuan mahjong uses its issued win and discard choices. Automatic
north extraction uses only legal server actions, independently of the skip-calls
preference, and retains the normal robbery and replacement-draw flow.
Turning sorting off keeps the hand's current order. Dragging a tile onto another
tile changes its position. Dragging it upward at least 48 logical pixels in seated
view or 96 pixels on the immersive canvas discards only when a discard is legal.
Shorter drags retain the tile. Clicking without dragging uses the personal
single-click or double-click discard setting.
Riichi draws and ordinary discards advance after
12 server ticks on both tables, retaining legal concealed-kan and north-extraction
choices. Ordinary tables start with a 30-second move allowance and 120-second
hand reserve; lobby hosts can edit both values.
Automatic table indicators retain their housing, inactive panels and lamps before
a game starts. Active views supply native seat scores and turn lights for all rules.
Ordinary tables present their central deposits directly on the felt. Automatic tables use a compact
seven-segment display, large localized wind characters, round pips and seat lamps.
English winds use E/S/W/N; Chinese and Japanese use their localized characters.
Scores use a .048-block digit height, with width fitting only for unusually long
values. Each score display faces its owner, with glyph tops pointing toward the
table center. The wind characters use the resource-pack font, keeping explanatory
labels off the felt and the riichi-deposit lanes clear. Immersive center labels
use the same projected table-plane axes as the housing, including seat rotation,
shear and foreshortening; glyph tops point toward the center.

English quantity phrases use explicit `.one` and `.other` translations selected
by `CountedText` from the relevant numeric argument. Preserve nested components
and language reloads. Traditional Chinese joins translated Chinese words without
spaces, retains spaces around numeric values, and quotes variable player names
so both Chinese and Latin names have unambiguous boundaries.

## Visual acceptance

Update this document and the shared tokens together when intentionally changing
the style. Do not establish competing local widgets or palettes. Focused commands,
viewport checks and screenshot review are maintained in [Verification](VERIFICATION.md).
Compilation alone is not visual acceptance.
