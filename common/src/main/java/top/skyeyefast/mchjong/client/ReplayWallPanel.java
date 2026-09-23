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
    private final ReplayHand hand;
    private final TileFacePreset preset;
    private ReplayPlayback.Frame frame;

    ReplayWallPanel(Font font, ReplayMatch match, ReplayHand hand, TileFacePreset preset,
                    int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("replay.mchjong.wall"));
        this.font = font;
        this.match = match;
        this.hand = hand;
        this.preset = preset;
    }

    void show(ReplayPlayback.Frame frame) { this.frame = frame; }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (frame == null) return;
        MahjongUi.panel(graphics, getX(), getY(), width, height);
        MahjongUi.text(graphics, font, getMessage(), getX() + 9, getY() + 7, width - 18, MahjongUi.ACCENT, false);
        MahjongUi.text(graphics, font, Component.translatable("replay.mchjong.wall_help"), getX() + 9, getY() + 19,
            width - 18, MahjongUi.MUTED, false);

        var wall = hand.wall();
        int players = match.rules().players();
        int stacksPerSide = wall.tiles().size() / (players * 2);
        int columns = width >= 250 ? 2 : 1;
        int rows = (players + columns - 1) / columns;
        int bodyTop = getY() + 34;
        int cardWidth = Math.max(1, (width - 18 - (columns - 1) * 8) / columns);
        int cardHeight = Math.max(1, (height - 40 - (rows - 1) * 5) / rows);
        var slots = slots();
        Set<Integer> used = usedTiles();
        int replacementCount = replacementCount();
        int deadStart = wall.tiles().size() - 14;
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
            int tileWidth = Math.clamp((cardWidth - 8) / stacksPerSide - 1, 4, 12);
            int tileHeight = Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
            int rowSpan = stacksPerSide * (tileWidth + 1) - 1;
            int startX = x + (cardWidth - rowSpan) / 2;
            int startY = y + Math.max(15, (cardHeight - tileHeight * 2 - 2) / 2 + 4);
            for (int stack = 0; stack < stacksPerSide; stack++) for (int layer = 0; layer < 2; layer++) {
                int slot = slots[side * stacksPerSide + stack][layer];
                if (slot < 0) continue;
                int tx = startX + stack * (tileWidth + 1), ty = startY + layer * (tileHeight + 2);
                boolean dead = slot >= deadStart;
                if (dead) graphics.fill(tx - 1, ty - 1, tx + tileWidth + 1, ty + tileHeight + 1, MahjongUi.INPUT);
                TileGui.tile(graphics, wall.tiles().get(slot), tx, ty, tileWidth, false, false, false, preset);
                if (used.contains(wall.tiles().get(slot))) graphics.fill(tx, ty, tx + tileWidth, ty + tileHeight, 0x990b1418);
                int dora = wall.dora().indexOf(slot), ura = wall.ura().indexOf(slot);
                if (dora >= 0 && dora < revealed) graphics.outline(tx, ty, tileWidth, tileHeight, MahjongUi.ACCENT);
                else if (ura >= 0 && ura < revealed) graphics.outline(tx, ty, tileWidth, tileHeight, MahjongUi.MUTED);
                else if (slot == nextLive) graphics.outline(tx, ty, tileWidth, tileHeight, MahjongUi.POSITIVE);
            }
        }
    }

    private int[][] slots() {
        int size = hand.wall().tiles().size();
        int[][] slots = new int[size / 2][2];
        for (int stack = 0; stack < slots.length; stack++) java.util.Arrays.fill(slots[stack], -1);
        for (int slot = 0; slot < size; slot++) {
            int stack = WallLayout.stack(slot, hand.wall().breakOffset(), size);
            slots[stack][slot & 1] = slot;
        }
        return slots;
    }

    private Set<Integer> usedTiles() {
        var used = new HashSet<Integer>();
        for (var seat : frame.seats()) {
            for (int tile : seat.hand()) if (tile >= 0) used.add(tile);
            for (var meld : seat.melds()) for (int tile : meld.tiles()) if (tile >= 0) used.add(tile);
            for (var discard : seat.river()) if (discard.tile() >= 0) used.add(discard.tile());
            for (int tile : seat.norths()) if (tile >= 0) used.add(tile);
        }
        return used;
    }

    private int replacementCount() {
        int count = 0;
        int end = Math.min(frame.rawCursor(), hand.events().size());
        for (int i = 0; i < end; i++) {
            var event = hand.events().get(i);
            if (!event.committed()) continue;
            if (event.kind() == ReplayHand.Kind.NUKI || event.kind() == ReplayHand.Kind.MELD && event.meld() != null && event.meld().kan()) count++;
        }
        return count;
    }

    private int nextLive(Set<Integer> used, int liveEnd) {
        for (int slot = 0; slot < liveEnd; slot++) if (!used.contains(hand.wall().tiles().get(slot))) return slot;
        return -1;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE, Component.translatable("replay.mchjong.wall_help"));
    }
}
