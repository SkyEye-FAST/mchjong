package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import top.skyeyefast.mchjong.engine.McrHints;
import top.skyeyefast.mchjong.engine.McrView;
import top.skyeyefast.mchjong.engine.SichuanHints;
import top.skyeyefast.mchjong.engine.SichuanView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Presentation shared by the two rule-specific analyzers; it has no gameplay authority. */
final class TableHints extends TableHintsButton {
    private final McrHints mcr = new McrHints();
    private final SichuanHints sichuan = new SichuanHints();
    private int previewDiscard = Tile.ABSENT, lastSelected = Tile.ABSENT;
    private List<Entry> entries = List.of();
    private List<FormattedCharSequence> lines = List.of();
    private int left, top, span, columns, cell, popupHeight;
    private int page, pages = 1, pageSize;
    private String summaryText = "";
    private record Entry(int kind, int count, Component value, Component detail) {}

    void clearPreview() {
        previewDiscard = lastSelected = Tile.ABSENT;
        visible = active = false;
        setFocused(false);
        page = 0;
    }

    @Override public void onPress() { page = (page + 1) % pages; }

    private int discard(int hovered, int selected) {
        if (hovered >= 0) previewDiscard = hovered;
        else if (selected != lastSelected) previewDiscard = selected;
        lastSelected = selected;
        return previewDiscard;
    }

    void update(McrView view, int hovered, int selected, Font font, int center, int bottom, int halfWidth, int topBound, int scale) {
        var preview = mcr.preview(view, discard(hovered, selected));
        if (preview == null) { visible = active = false; setFocused(false); return; }
        var summary = heading(preview.discard(), preview.shanten());
        if (preview.shanten() == 0) summary.append("\n").append(Component.translatable("hints.mchjong.mcr.fan_basis"));
        if (preview.winForbidden()) summary.append("\n").append(Component.translatable("mcr.mchjong.win_forbidden"));
        var rows = new ArrayList<Entry>();
        for (var tile : preview.tiles()) {
            var value = tile.discardFan() < 0 ? Component.empty()
                : Component.literal(tile.discardFan() + (tile.discardFan() >= 8 ? "✓" : "×") + " / "
                    + tile.drawFan() + (tile.drawFan() >= 8 ? "✓" : "×"));
            var detail = tile.discardFan() < 0 ? Component.empty() : Component.translatable("hints.mchjong.mcr.fan",
                tile.discardFan(), qualification(tile.discardFan()), tile.drawFan(), qualification(tile.drawFan()));
            if (tile.currentFan() >= 0) {
                value = Component.literal(tile.currentFan() + (tile.currentFan() >= 8 ? "✓" : "×"));
                detail = Component.translatable("hints.mchjong.mcr.current_fan", tile.currentFan(), qualification(tile.currentFan()));
            }
            rows.add(new Entry(tile.kind(), tile.remaining(), value, detail));
        }
        present(summary, rows, font, center, bottom, halfWidth, topBound, scale);
    }

    void update(SichuanView view, int hovered, int selected, Font font, int center, int bottom, int halfWidth, int topBound, int scale) {
        var preview = sichuan.preview(view, discard(hovered, selected));
        if (preview == null) { visible = active = false; setFocused(false); return; }
        var rows = new ArrayList<Entry>();
        net.minecraft.network.chat.MutableComponent summary;
        if (!preview.voidTiles().isEmpty()) {
            summary = Component.translatable("hints.mchjong.sichuan.void_tiles",
                Component.translatable("sichuan.mchjong.suit." + preview.voidSuit()), preview.voidTiles().size());
            if (preview.discard()) summary = Component.translatable("hints.mchjong.after_discard", summary);
            preview.voidTiles().stream().map(Tile::kind).distinct().sorted().forEach(kind -> rows.add(new Entry(kind,
                (int) preview.voidTiles().stream().filter(tile -> Tile.kind(tile) == kind).count(), Component.empty(), Component.empty())));
        } else {
            summary = heading(preview.discard(), preview.shanten()).append("\n").append(preview.readyValue() > 0
                ? Component.translatable("hints.mchjong.sichuan.ready", preview.readyValue())
                : Component.translatable("hints.mchjong.sichuan.not_ready"));
            for (var tile : preview.tiles()) {
                var value = tile.fan() < 0 ? Component.empty() : Component.translatable("hints.mchjong.sichuan.value", tile.fan(), tile.value());
                rows.add(new Entry(tile.kind(), tile.remaining(), value, value));
            }
        }
        if (preview.passedFan() >= 0) summary.append("\n").append(Component.translatable("hints.mchjong.sichuan.passed_fan", preview.passedFan()));
        present(summary, rows, font, center, bottom, halfWidth, topBound, scale);
    }

    private static Component qualification(int fan) {
        return Component.translatable(fan >= 8 ? "hints.mchjong.mcr.qualified" : "hints.mchjong.mcr.insufficient");
    }
    private static net.minecraft.network.chat.MutableComponent heading(boolean discard, int shanten) {
        var heading = shanten == 0 ? Component.translatable("hints.mchjong.tenpai") : Component.translatable("hints.mchjong.shanten", shanten);
        return discard ? Component.translatable("hints.mchjong.after_discard", heading) : heading;
    }

    private void present(Component summary, List<Entry> rows, Font font, int center, int bottom, int halfWidth, int topBound, int scale) {
        this.scale = scale;
        if (!entries.equals(rows) || !summaryText.equals(summary.getString())) page = 0;
        entries = rows;
        summaryText = summary.getString();
        int available = 2 * halfWidth / scale;
        int minimumCell = Math.max(48, rows.stream().mapToInt(row -> font.width(row.value()) + 8).max().orElse(48));
        columns = Math.max(1, Math.min(Math.max(1, rows.size()), (available - 12) / minimumCell));
        span = Math.min(available, Math.max(160, columns * minimumCell + 12));
        if (span < 60) { visible = active = false; setFocused(false); return; }
        lines = font.split(summary, span - 12);
        cell = (span - 12) / columns;
        int maxHeight = Math.min(180, (bottom - topBound) / scale);
        int rowCount = Math.min(Math.max(1, (rows.size() + columns - 1) / columns),
            (maxHeight - 24 - lines.size() * 10) / 28);
        if (rowCount < 1) { visible = active = false; setFocused(false); return; }
        pageSize = columns * rowCount;
        pages = Math.max(1, (rows.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pages - 1);
        popupHeight = 12 + lines.size() * 10 + (rows.isEmpty() ? 0 : rowCount * 28) + (pages > 1 ? 12 : 0);
        visible = active = bottom - topBound >= popupHeight * scale;
        if (!visible) setFocused(false);
        left = center - span * scale / 2;
        top = bottom - popupHeight * scale;
        setWidth(20 * scale); setHeight(16 * scale);
        setX(center + halfWidth - getWidth()); setY(bottom + 4 * scale);
        var message = Component.translatable("hints.mchjong.button").append("\n").append(summary);
        for (var row : rows) message.append("\n").append(Component.translatable("tile.mchjong." + Tile.notation(row.kind())))
            .append(" × " + row.count()).append("\n").append(row.detail());
        if (pages > 1) message.append("\n").append(Component.translatable("hints.mchjong.page", page + 1, pages));
        setMessage(message);
    }

    void renderPopup(GuiGraphics graphics, Font font, TileFacePreset preset) {
        if (!visible || !isHoveredOrFocused()) return;
        graphics.pose().pushPose(); graphics.pose().translate(left, top, 0); graphics.pose().scale(scale, scale, 1);
        MahjongUi.panel(graphics, 0, 0, span, popupHeight);
        for (int index = 0; index < lines.size(); index++) graphics.drawString(font, lines.get(index), 6, 6 + index * 10, MahjongUi.ACCENT, false);
        for (int index = 0; index < pageSize && page * pageSize + index < entries.size(); index++) {
            var entry = entries.get(page * pageSize + index);
            int x = 6 + index % columns * cell, y = 10 + lines.size() * 10 + index / columns * 28;
            TileGui.tile(graphics, entry.kind() * 4, x, y, 9, false, false, false, preset);
            graphics.drawString(font, "× " + entry.count(), x + 13, y + 3, entry.count() == 0 ? MahjongUi.NEGATIVE : MahjongUi.TEXT, false);
            MahjongUi.text(graphics, font, entry.value(), x, y + 16, cell - 4, MahjongUi.TEXT, false);
        }
        if (pages > 1) MahjongUi.text(graphics, font, Component.translatable("hints.mchjong.page", page + 1, pages),
            6, popupHeight - 12, span - 12, MahjongUi.MUTED, false);
        graphics.pose().popPose();
    }
}
