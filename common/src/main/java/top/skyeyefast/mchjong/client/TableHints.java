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
    private int scale = 1;
    private Component heading = Component.empty();

    TableHints() {
        super(0, 0, 20, 16, Component.translatable("hints.mchjong.button"), ignored -> {});
        visible = active = false;
        setTooltip(null);
    }

    void clearPreview() { previewDiscard = lastSelected = Tile.ABSENT; waits = List.of(); visible = active = false; }

    void update(TableView view, int hovered, int selected, int screenWidth, int buttonTop,
                int leftBound, int rightBound, int bottom, int topBound, int scale) {
        this.scale = scale;
        if (hovered >= 0) previewDiscard = hovered;
        else if (selected != lastSelected) previewDiscard = selected;
        lastSelected = selected;
        waits = hints.waits(view, previewDiscard);
        popup = layout(rightBound, leftBound, topBound, bottom, waits.size(), scale);
        visible = active = !waits.isEmpty() && popup != null;
        if (!visible) setFocused(false);
        setWidth(20 * scale);
        setHeight(16 * scale);
        setX(screenWidth - 30 * scale);
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

    record Layout(int x, int y, int width, int height, int step, int tileWidth, int columns) {}

    static Layout layout(int rightBound, int leftBound, int topBound, int bottom, int count, int scale) {
        if (count == 0) return null;
        int available = (rightBound - leftBound) / scale;
        int columns = Math.min(count, (available - 12) / 12);
        if (columns < 1) return null;
        int rows = (count + columns - 1) / columns;
        int step = Math.min(20, (available - 12) / columns);
        int tileWidth = Math.min(step - 4, (int) ((((bottom - topBound) / scale - 20) / rows - 12) * TileMesh.WIDTH / TileMesh.HEIGHT));
        if (tileWidth < 5) return null;
        int height = rows * (Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH) + 12) + 20;
        int span = Math.max(Math.min(112, available), columns * step + 12);
        return new Layout(leftBound + (rightBound - leftBound - span * scale) / 2, bottom - height * scale,
            span * scale, height * scale, step * scale, tileWidth * scale, columns);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(getX(), getY(), 0);
        graphics.pose().scale(scale, scale, 1);
        int centerX = 10, centerY = 8;
        int edge = isHoveredOrFocused() ? MahjongUi.ACCENT : MahjongUi.EDGE;
        for (int dy = -7; dy <= 7; dy++) {
            int half = 7 - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, edge);
            if (half > 1) graphics.fill(centerX - half + 1, centerY + dy, centerX + half, centerY + dy + 1, MahjongUi.PANEL);
        }
        graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 1, MahjongUi.ACCENT);
        graphics.fill(centerX, centerY + 3, centerX + 1, centerY + 4, MahjongUi.ACCENT);
        graphics.pose().popPose();
    }

    void renderPopup(GuiGraphics graphics, Font font, TileFacePreset preset) {
        if (!visible || !isHoveredOrFocused()) return;
        var box = popup;
        graphics.pose().pushPose();
        graphics.pose().translate(box.x(), box.y(), 0);
        graphics.pose().scale(scale, scale, 1);
        MahjongUi.panel(graphics, 0, 0, box.width() / scale, box.height() / scale);
        MahjongUi.text(graphics, font, heading, 6, 5, box.width() / scale - 12, MahjongUi.ACCENT, false);
        int tileWidth = box.tileWidth() / scale;
        int tileHeight = Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
        for (int index = 0; index < waits.size(); index++) {
            var wait = waits.get(index);
            int x = 6 + index % box.columns() * box.step() / scale;
            int y = 17 + index / box.columns() * (tileHeight + 12);
            TileGui.tile(graphics, wait.kind() * 4, x, y, tileWidth, false, false, false, preset);
            graphics.drawCenteredString(font, Integer.toString(wait.remaining()), x + tileWidth / 2,
                y + 3 + tileHeight, wait.remaining() == 0 ? MahjongUi.NEGATIVE : MahjongUi.TEXT);
        }
        graphics.pose().popPose();
    }
}
