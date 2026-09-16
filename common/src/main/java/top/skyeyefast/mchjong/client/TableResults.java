package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;

/** Scrollable settlement receipt. Only the server's recipient-safe snapshot is displayed. */
public final class TableResults extends AbstractWidget {
    private static final int TEXT = 0xffe8e6d8;
    private static final int MUTED = 0xffa8c5bc;
    private static final int GOLD = 0xfff2cf86;
    private final Font font;
    private final TableView view;
    private int scroll;
    private int contentHeight;

    public TableResults(Font font, TableView view, int x, int y, int width, int height, int scroll) {
        super(x, y, width, height, Component.translatable("result.mchjong." + view.result()));
        this.font = font;
        this.view = view;
        this.scroll = Math.max(0, scroll);
    }

    public int scrollAmount() { return scroll; }

    public static boolean available(TableView view) {
        return view.phase() == Game.Phase.HAND_END || view.phase() == Game.Phase.MATCH_END;
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        graphics.fill(x, y, x + width, y + height, 0xf21a2a2e);
        graphics.renderOutline(x, y, width, height, isFocused() ? GOLD : 0xff678d82);
        graphics.fill(x, y, x + 3, y + height, GOLD);
        graphics.drawString(font, getMessage(), x + 12, y + 10, GOLD, false);
        Component subtitle = view.phase() == Game.Phase.MATCH_END
            ? Component.translatable("ui.mchjong.match_complete") : TableScreen.roundName(view);
        graphics.drawString(font, font.split(subtitle, width - 24).getFirst(), x + 12, y + 24, MUTED, false);
        int top = y + 42, bottom = y + height - 17;
        // Measure before drawing so resizing and new results never leave a blank viewport.
        contentHeight = contents(null, 0);
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (bottom - top)));
        graphics.enableScissor(x + 5, top, x + width - 5, bottom);
        contents(graphics, top - scroll);
        graphics.disableScissor();
        if (contentHeight > bottom - top) {
            int track = bottom - top;
            int thumb = Math.max(12, track * track / contentHeight);
            int thumbY = top + (track - thumb) * scroll / Math.max(1, contentHeight - track);
            graphics.fill(x + width - 7, top, x + width - 5, bottom, 0xff344c4a);
            graphics.fill(x + width - 7, thumbY, x + width - 5, thumbY + thumb, GOLD);
            graphics.drawString(font, font.split(Component.translatable("ui.mchjong.result_scroll"), width - 24).getFirst(),
                x + 12, y + height - 12, MUTED, false);
        }
    }

    private int contents(GuiGraphics graphics, int origin) {
        int y = origin;
        y = paragraph(graphics, Component.translatable("ui.mchjong.point_changes"), y, GOLD);
        // Seat order avoids inventing a tie-break order absent from the public snapshot.
        for (int seat = 0; seat < view.seats().size(); seat++) {
            TableView.Seat player = view.seats().get(seat);
            int delta = seat < view.deltas().size() ? view.deltas().get(seat) : 0;
            Component name = TableScreen.playerName(view, seat);
            if (seat == view.viewerSeat()) name = name.copy().append(" · ").append(Component.translatable("ui.mchjong.you"));
            Component points = Component.literal((player.points() - delta) + " → " + player.points() + "  ("
                + String.format(Locale.ROOT, "%+d", delta) + ")");
            Component finalScore = seat < view.finalScores().size() ? Component.translatable("ui.mchjong.final_score",
                String.format(Locale.ROOT, "%+.1f", view.finalScores().get(seat)))
                : Component.translatable(player.ready() ? "ui.mchjong.ready" : "ui.mchjong.not_ready");
            int rowEnd = paragraph(null, points, paragraph(null, name, y + 2, TEXT), TEXT);
            if (finalScore != null) rowEnd = paragraph(null, finalScore, rowEnd, GOLD);
            if (graphics != null) graphics.fill(getX() + 9, y - 2, getX() + width - 11, rowEnd + 2,
                seat == view.viewerSeat() ? 0xff2b4242 : 0xff213538);
            y = paragraph(graphics, name, y + 2, TEXT);
            y = paragraph(graphics, points, y, delta > 0 ? 0xff99e0b4 : delta < 0 ? 0xffffaaa0 : MUTED);
            if (finalScore != null) y = paragraph(graphics, finalScore, y, GOLD);
            y += 8;
        }
        y = paragraph(graphics, Component.translatable("ui.mchjong.settlement_note"), y, MUTED) + 8;
        if (view.wins().isEmpty()) {
            y = paragraph(graphics, Component.translatable("ui.mchjong.no_winner"), y, TEXT);
            if (view.result().equals("exhaustive")) {
                for (int seat = 0; seat < view.seats().size(); seat++) {
                    Component status = Component.translatable(view.seats().get(seat).exposed() ? "ui.mchjong.tenpai" : "ui.mchjong.noten");
                    y = paragraph(graphics, TableScreen.playerName(view, seat).copy().append(" · ").append(status), y, MUTED);
                }
            }
        }
        for (TableView.Win win : view.wins()) {
            y = paragraph(graphics, TableScreen.playerName(view, win.seat()), y + 4, GOLD);
            Component source = win.from() < 0 ? Component.translatable("result.mchjong." + view.result())
                : Component.translatable("ui.mchjong.ron_from", TableScreen.playerName(view, win.from()));
            y = paragraph(graphics, source, y, MUTED);
            Component score = win.score().yakuman() > 0 ? Component.translatable("ui.mchjong.yakuman", win.score().yakuman())
                : win.score().fu() > 0 ? Component.translatable("ui.mchjong.han_fu", win.score().han(), win.score().fu())
                : Component.translatable("result.mchjong.nagashi");
            y = paragraph(graphics, score, y, TEXT) + 4;
            TableView.Seat player = view.seats().get(win.seat());
            // Nagashi does not reveal concealed hands. Never fabricate a winning hand.
            if (win.tile() >= 0 && player.exposed()) {
                var tiles = new ArrayList<>(player.hand());
                tiles.remove(Integer.valueOf(win.tile()));
                tiles.add(win.tile());
                int tileWidth = Math.clamp((width - 36) / Math.max(1, tiles.size()) - 2, 7, 19);
                if (graphics != null) for (int i = 0; i < tiles.size(); i++)
                    TileGui.tile(graphics, tiles.get(i), getX() + 12 + i * (tileWidth + 2), y,
                        tileWidth, false, false, i == tiles.size() - 1);
                y += tileWidth * 3 / 2 + 8;
                for (var meld : player.melds()) {
                    if (graphics != null) TileGui.meld(graphics, meld, win.seat(), getX() + 24, y + 5, tileWidth);
                    y += tileWidth * 2 + 8;
                }
            }
            for (String yaku : win.score().yaku())
                y = paragraph(graphics, Component.translatable("yaku.mchjong." + yaku.toLowerCase(Locale.ROOT)), y, TEXT);
            if (win.score().dora() > 0)
                y = paragraph(graphics, Component.translatable("ui.mchjong.dora", win.score().dora()), y, GOLD);
            if (win.tile() >= 0) {
                y = indicators(graphics, y + 8, false);
                if (player.riichi()) y = indicators(graphics, y + 6, true);
            }
            y += 12;
        }
        return y - origin;
    }

    private int indicators(GuiGraphics graphics, int y, boolean ura) {
        var tiles = new ArrayList<Integer>();
        for (int i = 0; i < 5; i++) {
            int index = view.wall().size() - (ura ? 6 : 5) - 2 * i;
            if (index >= 0 && view.wall().get(index) >= 0) tiles.add(view.wall().get(index));
        }
        if (tiles.isEmpty()) return y;
        y = paragraph(graphics, Component.translatable(ura ? "ui.mchjong.ura_indicators" : "ui.mchjong.result_indicators"), y, MUTED);
        if (graphics != null) for (int i = 0; i < tiles.size(); i++)
            TileGui.tile(graphics, tiles.get(i), getX() + 13 + 18 * i, y + 4, 16, false, false, false);
        return y + 32;
    }

    private int paragraph(GuiGraphics graphics, Component text, int y, int color) {
        for (var line : font.split(text, Math.max(20, width - 28))) {
            if (graphics != null) graphics.drawString(font, line, getX() + 13, y, color, false);
            y += 12;
        }
        return y;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x, y)) return false;
        scrollBy((int) Math.round(-vertical * 28));
        return true;
    }

    private void scrollBy(int amount) {
        scroll = Math.clamp(scroll + amount, 0, Math.max(0, contentHeight - (height - 59)));
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scrollBy(-24);
            case GLFW.GLFW_KEY_DOWN -> scrollBy(24);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-(height - 70));
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(height - 70);
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = contentHeight;
            default -> { return super.keyPressed(key, scanCode, modifiers); }
        }
        return true;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        Component summary = getMessage().copy();
        for (int seat = 0; seat < view.seats().size(); seat++) {
            summary = summary.copy().append(". ").append(TableScreen.playerName(view, seat)).append(" ")
                .append(Component.translatable("ui.mchjong.points", view.seats().get(seat).points()));
            if (seat < view.deltas().size()) summary = summary.copy().append(" " + String.format(Locale.ROOT, "%+d", view.deltas().get(seat)));
        }
        for (TableView.Win win : view.wins()) {
            summary = summary.copy().append(". ").append(TableScreen.playerName(view, win.seat()));
            for (String yaku : win.score().yaku()) summary = summary.copy().append(". ")
                .append(Component.translatable("yaku.mchjong." + yaku.toLowerCase(Locale.ROOT)));
        }
        output.add(NarratedElementType.TITLE, summary);
        output.add(NarratedElementType.USAGE, Component.translatable("ui.mchjong.result_scroll"));
    }
}
