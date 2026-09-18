package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.TenpaiHints;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** A compact, non-interactive tile rail above the decision buttons. */
final class TableHints {
    private final TenpaiHints hints = new TenpaiHints();

    void render(GuiGraphics graphics, Font font, TableView view, int discard, int width, int leftBound, int bottom,
                boolean immersive, TileFacePreset preset) {
        var waits = hints.waits(view, discard);
        if (waits.isEmpty()) return;
        int step = Math.min(20, (width - (immersive ? 8 : leftBound) - 22) / waits.size()), tileWidth = step - 4;
        int tileHeight = Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
        int span = Math.max(112, waits.size() * step + 12), height = tileHeight + 32;
        int left = width - span - 10, top = immersive ? 34 : 66;
        // Dense action choices take priority over the optional rail in a short viewport.
        if (tileWidth < 2 || top + height > bottom) return;
        int total = waits.stream().mapToInt(TenpaiHints.Wait::remaining).sum();
        boolean preview = view.seats().get(view.viewerSeat()).hand().size() % 3 == 2;
        MahjongUi.panel(graphics, left, top, span, height);
        MahjongUi.text(graphics, font, Component.translatable(preview ? "hints.mchjong.preview" : "hints.mchjong.waits", total),
            left + 6, top + 5, span - 12, MahjongUi.ACCENT, false);
        for (int index = 0; index < waits.size(); index++) {
            var wait = waits.get(index);
            int x = left + 6 + index * step;
            TileGui.tile(graphics, wait.kind() * 4, x, top + 17, tileWidth, false, false, false, preset);
            graphics.drawCenteredString(font, Integer.toString(wait.remaining()), x + tileWidth / 2,
                top + 20 + tileHeight, wait.remaining() == 0 ? MahjongUi.NEGATIVE : MahjongUi.TEXT);
        }
    }
}
