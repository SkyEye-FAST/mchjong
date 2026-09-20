package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.TenpaiHints;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Native focus target for an on-demand wait preview above the private hand. */
final class TableHints extends MahjongButton {
    private final TenpaiHints hints = new TenpaiHints();
    private List<TenpaiHints.Wait> waits = List.of();
    private List<TenpaiHints.Wait> narrated = List.of();
    private int previewDiscard = Tile.ABSENT;
    private int lastSelected = Tile.ABSENT;
    private Layout popup;
    private Component heading = Component.empty();

    TableHints() {
        super(0, 0, 20, 16, Component.translatable("hints.mchjong.button"), ignored -> {});
        visible = active = false;
        setTooltip(null);
    }

    void clearPreview() { previewDiscard = lastSelected = Tile.ABSENT; waits = List.of(); visible = active = false; }

    void update(TableView view, int hovered, int selected, int screenWidth, int buttonTop,
                int leftBound, int rightBound, int bottom, int topBound) {
        if (hovered >= 0) previewDiscard = hovered;
        else if (selected != lastSelected) previewDiscard = selected;
        lastSelected = selected;
        waits = hints.waits(view, previewDiscard);
        popup = layout(rightBound, leftBound, topBound, bottom, waits.size());
        visible = active = !waits.isEmpty() && popup != null;
        if (!visible) setFocused(false);
        setX(screenWidth - 30);
        setY(buttonTop);
        if (visible && narrated != waits) {
            narrated = waits;
            int total = waits.stream().mapToInt(TenpaiHints.Wait::remaining).sum();
            boolean preview = view.seats().get(view.viewerSeat()).hand().size() % 3 == 2;
            heading = Component.translatable(preview ? "hints.mchjong.preview" : "hints.mchjong.waits", total);
            Component message = Component.translatable("hints.mchjong.button").append("\n").append(heading);
            for (var wait : waits) message = message.copy().append("\n")
                .append(Component.translatable("tile.mchjong." + Tile.notation(wait.kind())))
                .append(" × " + wait.remaining());
            setMessage(message);
            setTooltip(null);
        }
    }

    record Layout(int x, int y, int width, int height, int step, int tileWidth) {}

    static Layout layout(int rightBound, int leftBound, int topBound, int bottom, int count) {
        if (count == 0) return null;
        int step = Math.min(20, (rightBound - leftBound - 12) / count);
        int tileWidth = Math.min(step - 4, (int) ((bottom - topBound - 32) * TileMesh.WIDTH / TileMesh.HEIGHT));
        if (tileWidth < 5) return null;
        int height = Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH) + 32;
        int span = Math.max(112, count * step + 12);
        if (span > rightBound - leftBound) return null;
        return new Layout(leftBound + (rightBound - leftBound - span) / 2, bottom - height, span, height, step, tileWidth);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = getX() + 10, centerY = getY() + 8;
        int edge = isHoveredOrFocused() ? MahjongUi.ACCENT : MahjongUi.EDGE;
        for (int dy = -7; dy <= 7; dy++) {
            int half = 7 - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, edge);
            if (half > 1) graphics.fill(centerX - half + 1, centerY + dy, centerX + half, centerY + dy + 1, MahjongUi.PANEL);
        }
        graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 1, MahjongUi.ACCENT);
        graphics.fill(centerX, centerY + 3, centerX + 1, centerY + 4, MahjongUi.ACCENT);
    }

    void renderPopup(GuiGraphics graphics, Font font, TileFacePreset preset) {
        if (!visible || !isHoveredOrFocused()) return;
        var box = popup;
        MahjongUi.panel(graphics, box.x(), box.y(), box.width(), box.height());
        MahjongUi.text(graphics, font, heading, box.x() + 6, box.y() + 5, box.width() - 12, MahjongUi.ACCENT, false);
        int tileHeight = Math.round(box.tileWidth() * TileMesh.HEIGHT / TileMesh.WIDTH);
        for (int index = 0; index < waits.size(); index++) {
            var wait = waits.get(index);
            int x = box.x() + 6 + index * box.step();
            TileGui.tile(graphics, wait.kind() * 4, x, box.y() + 17, box.tileWidth(), false, false, false, preset);
            graphics.drawCenteredString(font, Integer.toString(wait.remaining()), x + box.tileWidth() / 2,
                box.y() + 20 + tileHeight, wait.remaining() == 0 ? MahjongUi.NEGATIVE : MahjongUi.TEXT);
        }
    }
}
