package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.IntStream;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.ReplayHand;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.engine.YakuCatalog;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Replay-only settlement view backed by the immutable recorded result. */
final class ReplayResultPanel extends AbstractWidget {
    private final Font font;
    private final ReplayMatch match;
    private final ReplayHand hand;
    private final TileFacePreset preset;
    private int viewer;
    private int winner;

    ReplayResultPanel(Font font, ReplayMatch match, ReplayHand hand, TileFacePreset preset,
                      int x, int y, int width, int height, int viewer) {
        super(x, y, width, height, Component.translatable("result.mchjong." + hand.result()));
        this.font = font;
        this.match = match;
        this.hand = hand;
        this.preset = preset;
        this.viewer = viewer;
    }

    void setViewer(int viewer) { this.viewer = viewer; }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.panel(graphics, getX(), getY(), width, height);
        line(graphics, getMessage(), getX() + 9, getY() + 7, width - 18, MahjongUi.ACCENT);
        int top = getY() + 24;
        int sidebar = width >= 520 ? 170 : 0;
        int bodyWidth = width - 20 - sidebar;
        int bodyHeight = height - 32 - (sidebar == 0 ? 42 : 0);
        if (hand.wins().isEmpty()) drawNoWinner(graphics, getX() + 10, top, bodyWidth, bodyHeight);
        else drawWin(graphics, getX() + 10, top, bodyWidth, bodyHeight);
        if (sidebar > 0) drawScores(graphics, getX() + width - sidebar, top, sidebar - 8, height - 32);
        else drawScoreStrip(graphics, getX() + 8, getY() + height - 39, width - 16, 34);
    }

    private void drawWin(GuiGraphicsExtractor graphics, int x, int y, int span, int available) {
        if (hand.wins().size() > 1) {
            int tabWidth = Math.max(1, span / hand.wins().size());
            for (int i = 0; i < hand.wins().size(); i++) {
                int tx = x + i * tabWidth;
                graphics.fill(tx, y, tx + tabWidth - 3, y + 15, i == winner ? MahjongUi.SELECTED : MahjongUi.SURFACE);
                line(graphics, Component.literal(match.participants().get(hand.wins().get(i).seat()).name()),
                    tx + 4, y + 3, tabWidth - 8, i == winner ? MahjongUi.ACCENT : MahjongUi.MUTED);
            }
            y += 20;
            available -= 20;
        }
        var win = hand.wins().get(winner);
        var player = hand.finalSeats().get(win.seat());
        Component source = win.from() < 0 ? Component.translatable("result.mchjong." + hand.result())
            : Component.translatable("ui.mchjong.ron_from", Component.literal(match.participants().get(win.from()).name()));
        Component score = win.score().yakuman() > 0 ? Component.translatable("ui.mchjong.yakuman", win.score().yakuman())
            : Component.translatable("ui.mchjong.han_fu", win.score().han(), win.score().fu());
        line(graphics, Component.literal(match.participants().get(win.seat()).name()), x, y, span, MahjongUi.ACCENT);
        line(graphics, score.copy().append("  ").append(source), x, y + 12, span, MahjongUi.MUTED);
        y += 29;

        var concealed = new ArrayList<>(player.hand());
        if (win.tile() >= 0) {
            concealed.remove(Integer.valueOf(win.tile()));
            concealed.add(win.tile());
        }
        int tileWidth = Math.min(22, Math.max(8, (span - 36) / Math.max(14, concealed.size() + player.melds().size() * 3)));
        int tx = x;
        for (int i = 0; i < concealed.size(); i++) {
            if (i == concealed.size() - 1 && win.tile() >= 0) tx += 4;
            TileGui.tile(graphics, concealed.get(i), tx, y, tileWidth, false, false, i == concealed.size() - 1 && win.tile() >= 0, preset);
            tx += tileWidth + 1;
        }
        for (var meld : player.melds()) {
            tx += 5;
            TileGui.meld(graphics, meld, win.seat(), tx, y, tileWidth, preset);
            tx += TileGui.meldWidth(meld, win.seat(), tileWidth);
        }
        y += Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH) + 8;

        int columns = span >= 300 ? 3 : 2;
        int colWidth = span / columns;
        var yaku = new ArrayList<Component>();
        for (var value : win.yaku()) yaku.add(Component.translatable(YakuCatalog.translationKey(value.name()))
            .append(value.yakuman() ? "" : " " + value.han()));
        if (win.dora() + win.ura() + win.redDora() + win.nukiDora() > 0)
            yaku.add(Component.translatable("ui.mchjong.dora", win.dora() + win.ura() + win.redDora() + win.nukiDora()));
        for (int first = 0; first < yaku.size(); first += columns) {
            for (int col = 0; col < columns && first + col < yaku.size(); col++)
                line(graphics, yaku.get(first + col), x + col * colWidth, y, colWidth - 6, MahjongUi.TEXT);
            y += 12;
        }
        if (!hand.dora().isEmpty()) {
            y += 3;
            y = indicators(graphics, Component.translatable("ui.mchjong.result_indicators"), hand.dora(), x, y, span);
        }
        if (!hand.ura().isEmpty()) indicators(graphics, Component.translatable("ui.mchjong.ura_indicators"), hand.ura(), x, y, span);
    }

    private void drawNoWinner(GuiGraphicsExtractor graphics, int x, int y, int span, int available) {
        int columns = 2, rows = (hand.finalSeats().size() + 1) / 2;
        int cardWidth = span / columns, cardHeight = Math.max(44, available / rows);
        for (int seat = 0; seat < hand.finalSeats().size(); seat++) {
            var player = hand.finalSeats().get(seat);
            int cx = x + seat % columns * cardWidth, cy = y + seat / columns * cardHeight;
            Component status = Component.translatable(hand.result().equals("exhaustive")
                ? player.exposed() ? "ui.mchjong.tenpai" : "ui.mchjong.noten" : "ui.mchjong.no_winner");
            line(graphics, Component.literal(match.participants().get(seat).name()), cx, cy, cardWidth - 8, MahjongUi.TEXT);
            line(graphics, status, cx, cy + 11, cardWidth - 8, player.exposed() ? MahjongUi.ACCENT : MahjongUi.MUTED);
            int concealed = player.exposed() ? player.hand().size() : 0;
            if (concealed > 0 || !player.melds().isEmpty()) {
                int tw = Math.max(5, Math.min(14, (cardHeight - 27) / 2));
                while (tw > 5 && handWidth(player, seat, concealed, tw) > cardWidth - 8) tw--;
                int tx = cx, tileY = cy + 24 + tw / 2;
                if (player.exposed()) for (int tile : player.hand()) {
                    TileGui.tile(graphics, tile, tx, tileY, tw, false, false, false, preset);
                    tx += tw + 1;
                }
                for (var meld : player.melds()) {
                    tx += 5;
                    TileGui.meld(graphics, meld, seat, tx, tileY, tw, preset);
                    tx += TileGui.meldWidth(meld, seat, tw);
                }
            }
        }
    }

    private static int handWidth(TableView.Seat player, int owner, int concealed, int tileWidth) {
        return concealed * (tileWidth + 1)
            + player.melds().stream().mapToInt(meld -> TileGui.meldWidth(meld, owner, tileWidth) + 5).sum();
    }

    private int indicators(GuiGraphicsExtractor graphics, Component label, java.util.List<Integer> tiles, int x, int y, int span) {
        int labelWidth = Math.min(92, Math.max(40, font.width(label) + 5));
        line(graphics, label, x, y + 4, labelWidth, MahjongUi.MUTED);
        int tw = 10;
        for (int i = 0; i < tiles.size(); i++) TileGui.tile(graphics, tiles.get(i), x + labelWidth + i * (tw + 2), y, tw, false, false, false, preset);
        return y + Math.round(tw * TileMesh.HEIGHT / TileMesh.WIDTH) + 4;
    }

    private void drawScores(GuiGraphicsExtractor graphics, int x, int y, int span, int available) {
        var order = IntStream.range(0, hand.finalSeats().size()).boxed().toList();
        if (hand.finalRanks().size() == hand.finalSeats().size())
            order = order.stream().sorted(Comparator.comparingInt(seat -> hand.finalRanks().get(seat))).toList();
        int cardHeight = Math.min(54, Math.max(35, available / order.size()));
        for (int row = 0; row < order.size(); row++) {
            int seat = order.get(row), cy = y + row * cardHeight;
            var player = hand.finalSeats().get(seat);
            graphics.fill(x, cy, x + span, cy + cardHeight - 3, seat == viewer ? MahjongUi.SELECTED : MahjongUi.SURFACE);
            Component name = Component.literal(match.participants().get(seat).name());
            if (hand.finalRanks().size() == hand.finalSeats().size()) name = Component.literal(hand.finalRanks().get(seat) + ". ").append(name);
            line(graphics, name, x + 5, cy + 4, span - 10, MahjongUi.TEXT);
            line(graphics, Component.literal(String.format(Locale.ROOT, "%+d", hand.deltas().get(seat))), x + 5, cy + 17,
                span - 10, hand.deltas().get(seat) < 0 ? MahjongUi.NEGATIVE : MahjongUi.ACCENT);
            Component points = Component.translatable("ui.mchjong.points", player.points());
            if (hand.finalScores().size() == hand.finalSeats().size())
                points = points.copy().append("  ").append(Component.translatable("ui.mchjong.final_score",
                    String.format(Locale.ROOT, "%+.1f", hand.finalScores().get(seat))));
            line(graphics, points, x + 5, cy + 29, span - 10, MahjongUi.MUTED);
        }
    }

    private void drawScoreStrip(GuiGraphicsExtractor graphics, int x, int y, int span, int height) {
        int cardWidth = span / hand.finalSeats().size();
        for (int seat = 0; seat < hand.finalSeats().size(); seat++) {
            int cx = x + seat * cardWidth;
            graphics.fill(cx, y, cx + cardWidth - 3, y + height, seat == viewer ? MahjongUi.SELECTED : MahjongUi.SURFACE);
            line(graphics, Component.literal(match.participants().get(seat).name()), cx + 4, y + 3, cardWidth - 8, MahjongUi.TEXT);
            String points = hand.finalSeats().get(seat).points() + "  " + String.format(Locale.ROOT, "%+d", hand.deltas().get(seat));
            line(graphics, Component.literal(points), cx + 4, y + 16, cardWidth - 8,
                hand.deltas().get(seat) < 0 ? MahjongUi.NEGATIVE : MahjongUi.ACCENT);
        }
    }

    private void line(GuiGraphicsExtractor graphics, Component text, int x, int y, int span, int color) {
        graphics.text(font, font.plainSubstrByWidth(text.getString(), Math.max(1, span)), x, y, color, false);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (button == 0 && hand.wins().size() > 1 && mouseY >= getY() + 24 && mouseY < getY() + 39) {
            int span = width - 20 - (width >= 520 ? 170 : 0);
            if (mouseX >= getX() + 10 && mouseX < getX() + 10 + span) {
                winner = Math.min(hand.wins().size() - 1, (int) (mouseX - getX() - 10) / Math.max(1, span / hand.wins().size()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (hand.wins().size() > 1 && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            winner = Math.floorMod(winner + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1), hand.wins().size());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
