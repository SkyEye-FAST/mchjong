package top.skyeyefast.mchjong.client;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.ReplayHand;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.ReplayPlayback;
import top.skyeyefast.mchjong.engine.WallLayout;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Initial physical wall with consumption state projected from a replay frame. */
final class ReplayWallPanel extends AbstractWidget {
    private final Font font;
    private final ReplayMatch match;
    private final ReplayPresentation playback;
    private final TileFacePreset preset;
    private ReplayPresentation.Frame frame;

    ReplayWallPanel(Font font, ReplayMatch match, ReplayPresentation playback, TileFacePreset preset,
                    int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("replay.mchjong.wall"));
        this.font = font;
        this.match = match;
        this.playback = playback;
        this.preset = preset;
    }

    void show(ReplayPresentation.Frame frame) { this.frame = frame; }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (frame == null) return;
        MahjongUi.panel(graphics, getX(), getY(), width, height);
        MahjongUi.text(graphics, font, getMessage(), getX() + 9, getY() + 7, width - 18, MahjongUi.ACCENT, false);
        MahjongUi.text(graphics, font, Component.translatable("replay.mchjong.wall_help"), getX() + 9, getY() + 19,
            width - 18, MahjongUi.MUTED, false);

        var wall = playback.wall();
        int players = match.participants().size();
        int stacksPerSide = (wall.size() + players * 2 - 1) / (players * 2);
        int columns = width >= 250 ? 2 : 1;
        int rows = (players + columns - 1) / columns;
        int bodyTop = getY() + 34;
        int cardWidth = Math.max(1, (width - 18 - (columns - 1) * 8) / columns);
        int cardHeight = Math.max(1, (height - 40 - (rows - 1) * 5) / rows);
        Set<Integer> used = usedTiles();
        int replacementCount = frame.replacements();
        int deadStart = playback.deadStart();
        int liveEnd = Math.max(0, deadStart - replacementCount);
        int nextLive = nextLive(used, liveEnd);
        int revealed = Math.min(5, frame.dora().size());

        for (int side = 0; side < players; side++) {
            int column = side % columns, row = side / columns;
            int x = getX() + 9 + column * (cardWidth + 8);
            int y = bodyTop + row * (cardHeight + 5);
            graphics.fill(x, y, x + cardWidth, y + cardHeight, MahjongUi.SURFACE);
            MahjongUi.text(graphics, font, Component.literal(match.participants().get(side).name()), x + 4, y + 4,
                cardWidth - 8, MahjongUi.TEXT, false);
            var slots = playback.wallSlots().get(side);
            stacksPerSide = slots.size() / 2;
            int tileWidth = Math.clamp((cardWidth - 8) / stacksPerSide - 1, 4, 12);
            int tileHeight = Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
            int rowSpan = stacksPerSide * (tileWidth + 1) - 1;
            int startX = x + (cardWidth - rowSpan) / 2;
            int startY = y + Math.max(15, (cardHeight - tileHeight * 2 - 2) / 2 + 4);
            for (int stack = 0; stack < stacksPerSide; stack++) for (int layer = 0; layer < 2; layer++) {
                int slot = slots.get(layer * stacksPerSide + stack);
                if (slot < 0) continue;
                int tx = startX + stack * (tileWidth + 1), ty = startY + layer * (tileHeight + 2);
                boolean dead = slot >= deadStart;
                if (dead) graphics.fill(tx - 1, ty - 1, tx + tileWidth + 1, ty + tileHeight + 1, MahjongUi.INPUT);
                TileGui.tileArtwork(graphics, wall.get(slot), tx, ty, tileWidth, false, false, false, false, 0,
                    preset, top.skyeyefast.mchjong.item.TileMaterial.BONE, null, TileBackPresets.DEFAULT, tile ->
                        match.variant() == top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI ? TileMesh.face(tile)
                            : top.skyeyefast.mchjong.engine.Tile.isFlower(tile) ? 37 + tile - 136 : top.skyeyefast.mchjong.engine.Tile.kind(tile));
                if (used.contains(wall.get(slot))) graphics.fill(tx, ty, tx + tileWidth, ty + tileHeight, 0x990b1418);
                int dora = playback.doraSlots().indexOf(slot), ura = playback.uraSlots().indexOf(slot);
                if (dora >= 0 && dora < revealed) graphics.outline(tx, ty, tileWidth, tileHeight, MahjongUi.ACCENT);
                else if (ura >= 0 && ura < revealed) graphics.outline(tx, ty, tileWidth, tileHeight, MahjongUi.MUTED);
                else if (slot == nextLive) graphics.outline(tx, ty, tileWidth, tileHeight, MahjongUi.POSITIVE);
            }
        }
    }

    private Set<Integer> usedTiles() {
        var used = new HashSet<Integer>();
        for (var seat : frame.board().seats()) {
            for (int tile : seat.hand()) if (tile >= 0) used.add(tile);
            for (var meld : seat.melds()) for (int tile : meld.tiles()) if (tile >= 0) used.add(tile);
            for (var discard : seat.river()) if (discard.tile() >= 0) used.add(discard.tile());
            for (int tile : seat.norths()) if (tile >= 0) used.add(tile);
        }
        return used;
    }

    private int nextLive(Set<Integer> used, int liveEnd) {
        if (match.variant() == top.skyeyefast.mchjong.engine.MahjongVariant.TAIWAN && frame.board().remaining() == 0) return -1;
        for (int slot : playback.wallDrawOrder()) if (slot < liveEnd && !used.contains(playback.wall().get(slot))) return slot;
        return -1;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE, Component.translatable("replay.mchjong.wall_help"));
    }
}
