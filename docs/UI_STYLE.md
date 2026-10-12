# Interface guidance

Build project screens with `MahjongUi`, `MahjongButton`, `MahjongSlider` and
`MahjongEditBox` so they share the same appearance and native input behavior.
Use their existing palette and drawing helpers rather than introducing a
separate visual style for each screen.

## Appearance and controls

Use dark-teal surfaces, warm-white text, square pixel-aligned edges and flat
fills. Reserve brass accents for selection, focus and important actions, keeping
ordinary borders quiet so the tiles remain the visual focus. Selected, focused
and disabled controls must be distinguishable without relying on color alone.

The shared widgets retain Minecraft's keyboard navigation, narration, text
editing, drag handling and sounds. Preserve these behaviors when changing a
control's appearance, including when a screen rebuilds after a state update.

Write labels using [TERMS.md](../TERMS.md), including its full-name requirements
and language-specific abbreviations. Size controls to fit their labels or wrap
the text instead of removing essential nouns. When visual truncation is
unavoidable, keep the complete label in the tooltip and narration. A tooltip
may explain a control's effect or why it is unavailable; it should not contain
an unrelated mahjong tutorial.

## Screen organization

Keep room membership, match rules and personal preferences in separate groups.
The lobby selects the mahjong variant; the rule editor sets the player count
and preset for that variant. Present the player count as one mutually exclusive
choice, not as separate settings for three-player and four-player play. Show
category navigation when the editor first opens and keep it visible while
visiting its preset, award and help pages.

These child pages return to the same rule draft, including unfinished numeric
input. Category changes and resizing must preserve that input as well. Show an
applied change only after server acknowledgement. If the server rejects a draft,
retain it for correction; Reload explicitly replaces it with the confirmed room
settings. Explain permission restrictions where the affected controls appear.

Ordinary screens fit a 320 x 240 logical viewport by wrapping or paginating
content. The immersive table instead preserves a single 1280 x 800 layout,
scaled uniformly and centered with black bars around unused space. Transform
pointer coordinates back into that same canvas so resizing does not separate
click targets from the visible controls and tiles.

## Mahjong table and result screens

Keep the mahjong table within its compact 3 x 3 block footprint. Fit hands and
melds using the space actually occupied by tiles rather than enlarging the
furniture or reserving empty meld slots. Preserve each variant's physical
arrangement and use the same geometry for rendering, animation and picking,
so a moving tile's click target follows its visible position. When a tile's
identity is hidden from the viewer, its face must remain covered through glass
bodies and from every camera angle.

Keep action buttons, clocks and hints clear of the player's hand and public
tiles. In result screens, reserve the final layout before revealing scoring
rows so the content does not jump. Riichi mahjong results present the points
before the hand grade. Winner selection, point changes and final standings
must remain accessible at small window sizes, including during the readout.
