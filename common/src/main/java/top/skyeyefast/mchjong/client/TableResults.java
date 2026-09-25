package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.ScoreAnnouncements;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

/** A single-screen settlement, with a winner selector for multiple ron and no scroll viewport. */
public final class TableResults extends AbstractWidget {
    public enum Page { HAND, POINTS, MATCH }
    private static final int TEXT = MahjongUi.TEXT, MUTED = MahjongUi.MUTED, GOLD = MahjongUi.ACCENT;
    private final Font font;
    private final TableView view;
    private final List<List<ScoreAnnouncements.Row>> receipts;
    private final TileFacePreset preset;
    private final TileMaterial material;
    private final DyeColor dye;
    private final net.minecraft.resources.ResourceLocation backPreset;
    private final Page page;
    private final long started;
    private final int contentScale;
    private int winner;
    private ResultReadout readout;
    private final List<Hit> hits = new ArrayList<>();
    private record Hit(int x, int y, int width, int height, Component text) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }

    public TableResults(Font font, TableView view, TileFacePreset preset, TileMaterial material, DyeColor dye,
                        int x, int y, int width, int height, int winner, Page page, long started, int contentScale) {
        this(font, view, preset, material, dye, TileBackPresets.DEFAULT, x, y, width, height, winner, page, started, contentScale);
    }

    public TableResults(Font font, TableView view, TileFacePreset preset, TileMaterial material, DyeColor dye,
                        net.minecraft.resources.ResourceLocation backPreset,
                        int x, int y, int width, int height, int winner, Page page, long started, int contentScale) {
        super(x, y, width, height, Component.translatable("result.mchjong." + view.result()));
        this.font = font;
        this.view = view;
        this.receipts = view.wins().stream().map(win -> ScoreAnnouncements.rows(view, win)).toList();
        this.preset = preset;
        this.material = material;
        this.dye = dye;
        this.backPreset = backPreset;
        this.page = page;
        this.started = started;
        this.contentScale = contentScale;
        this.winner = Math.clamp(winner, 0, Math.max(0, view.wins().size() - 1));
    }

    public TableResults(Font font, TableView view, TileFacePreset preset, int x, int y, int width, int height,
                        int winner, Page page, long started, int contentScale) {
        this(font, view, preset, TileMaterial.BONE, null, x, y, width, height, winner, page, started, contentScale);
    }

    public TableResults readout(ResultReadout value) { readout = value; return this; }
    public int selectedWinner() { return readout != null && !readout.complete() ? readout.winner() : winner; }
    public TileMaterial material() { return material; }
    public DyeColor dye() { return dye; }
    /** Every result page presents the same once-per-settlement score transition. */
    public int displayedPoints(int seat) {
        int delta = seat < view.deltas().size() ? view.deltas().get(seat) : 0;
        long pointsAt = readout == null ? started : readout.pointsAt();
        double progress = pointsAt < 0 ? 0 : TableSettings.get().animations
            ? Math.clamp((Util.getMillis() - pointsAt - 250) / 900.0, 0, 1) : 1;
        return view.seats().get(seat).points() - delta + (int) Math.round(delta * progress);
    }
    private boolean pointsVisible() { return readout == null || readout.pointsAt() >= 0; }
    public static boolean available(TableView view) {
        return view.phase() == Game.Phase.HAND_END || view.phase() == Game.Phase.MATCH_END;
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        winner = selectedWinner();
        hits.clear();
        int x = getX(), y = getY();
        MahjongUi.panel(graphics, x, y, width, height);
        if (isFocused()) graphics.renderOutline(x, y, width, height, GOLD);
        int contentWidth = width / contentScale, contentHeight = height / contentScale;
        int contentMouseX = Math.floorDiv(mouseX - x, contentScale);
        int contentMouseY = Math.floorDiv(mouseY - y, contentScale);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(contentScale, contentScale, 1);
        renderContent(graphics, contentMouseX, contentMouseY, contentWidth, contentHeight);
        for (Hit hit : hits) if (hit.contains(contentMouseX, contentMouseY)) {
            graphics.renderTooltip(font, font.split(hit.text(), Math.min(360, contentWidth - 24)), contentMouseX, contentMouseY);
            break;
        }
        graphics.pose().popPose();
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, int width, int height) {
        int x = 0, y = 0;
        Component heading = page == Page.HAND ? getMessage() : Component.translatable(
            page == Page.POINTS ? "ui.mchjong.point_changes" : "ui.mchjong.match_complete");
        line(graphics, heading, x + 9, y + 7, width - 18, GOLD);
        int top = y + 23, bottom = y + height - 6;
        if (page != Page.HAND) {
            scoreTable(graphics, x + 8, top, width - 16, bottom - top);
        } else {
            boolean sidebar = width >= 500;
            boolean strip = !sidebar && height >= 220;
            int bodyWidth = width - 20 - (sidebar ? 156 : 0);
            int bodyHeight = bottom - top - (strip ? 40 : 0);
            if (view.wins().size() > 1) {
                int tabWidth = bodyWidth / view.wins().size();
                for (int i = 0; i < view.wins().size(); i++) {
                    int tabX = x + 10 + i * tabWidth;
                    graphics.fill(tabX, top - 2, tabX + tabWidth - 3, top + 12, i == winner ? MahjongUi.SELECTED : MahjongUi.SURFACE);
                    name(graphics, view.wins().get(i).seat(), TableScreen.playerName(view, view.wins().get(i).seat()), tabX + 4, top + 1, tabWidth - 10,
                        i == winner ? GOLD : MUTED);
                }
                top += 18;
                bodyHeight -= 18;
            }
            if (view.wins().isEmpty()) drawHands(graphics, x + 10, top, bodyWidth, bodyHeight);
            else {
                boolean compact = bodyHeight < 150;
                int needed = winningHand(null, bodyWidth, compact);
                float scale = Math.min(1f, bodyHeight / (float) Math.max(1, needed));
                graphics.pose().pushPose();
                graphics.pose().translate(x + 10 + (bodyWidth - bodyWidth * scale) / 2, top, 0);
                graphics.pose().scale(scale, scale, 1);
                winningHand(graphics, bodyWidth, compact);
                graphics.pose().popPose();
                // Keep the tile row clear when the mouse rests in the result panel.
                hits.add(new Hit(x + 10, top, bodyWidth, 10, winnerSummary(view.wins().get(winner))));
            }
            if (sidebar) scores(graphics, x + width - 156, y + 23, 148, bottom - y - 23, false);
            else if (strip) scores(graphics, x + 8, bottom - 36, width - 16, 36, true);
        }
    }

    private int winningHand(GuiGraphics graphics, int span, boolean compact) {
        var win = view.wins().get(winner);
        var player = view.seats().get(win.seat());
        Component source = win.from() < 0 ? Component.translatable("result.mchjong." + view.result())
            : Component.translatable("ui.mchjong.ron_from", TableScreen.playerName(view, win.from()));
        Component score = win.score().yakuman() > 0 ? Component.translatable("ui.mchjong.yakuman", win.score().yakuman())
            : Component.translatable("ui.mchjong.han_fu", win.score().han(), win.score().fu());
        boolean named = view.wins().size() == 1;
        if (named) name(graphics, win.seat(), TableScreen.playerName(view, win.seat()), 0, 0, span, GOLD);
        boolean scored = readout == null || readout.scoredAt(winner) >= 0;
        text(graphics, scored ? score.copy().append("  ").append(source) : source, 0, named ? 12 : 0, span, MUTED);
        int y = named ? 28 : 16;
        if (win.tile() >= 0 && player.exposed()) {
            var hand = new ArrayList<>(player.hand());
            hand.remove(Integer.valueOf(win.tile()));
            hand.add(win.tile());
            int tileWidth = compact ? 12 : 22;
            while (tileWidth > 5 && handWidth(hand.size(), player, win.seat(), tileWidth, 4) > span) tileWidth--;
            int x = 0;
            for (int i = 0; i < hand.size(); i++) {
                if (i == hand.size() - 1) x += 4;
                if (graphics != null) TileGui.tile(graphics, hand.get(i), x, y + tileWidth / 2, tileWidth, false, false, i == hand.size() - 1, false, preset, material, dye, backPreset);
                x += tileWidth + 1;
            }
            for (var meld : player.melds()) {
                x += 5;
                if (graphics != null) TileGui.meld(graphics, meld, win.seat(), x, y + tileWidth / 2, tileWidth, preset, material, dye, backPreset);
                x += TileGui.meldWidth(meld, win.seat(), tileWidth);
            }
            y += tileWidth * 2 + 5;
        }
        var yaku = receipts.get(winner);
        int visible = readout == null ? yaku.size() : readout.visibleRows(winner);
        int columns = yaku.size() > 6 ? 2 : 1;
        int colWidth = span / columns;
        for (int first = 0; first < yaku.size(); first += columns) {
            int rowHeight = 0;
            for (int col = 0; col < columns && first + col < yaku.size(); col++) {
                var row = yaku.get(first + col);
                Component han = Component.translatable("ui.mchjong.han", row.han());
                int badgeWidth = row.han() > 0 ? font.width(han) + 8 : 0;
                var lines = font.split(Component.translatable(row.translationKey()), Math.max(1, colWidth - badgeWidth - 13));
                int color = readout != null && !readout.complete() && first + col == visible - 1 ? GOLD : TEXT;
                if (graphics != null && first + col < visible) {
                    if (badgeWidth > 0) badge(graphics, han,
                        col * colWidth + font.width(lines.getLast()) + 4, y + (lines.size() - 1) * 10, false);
                    for (int line = 0; line < lines.size(); line++)
                        graphics.drawString(font, lines.get(line), col * colWidth, y + 2 + line * 10, color, false);
                }
                rowHeight = Math.max(rowHeight, Math.max(13, lines.size() * 10 + 3));
            }
            y += rowHeight + 2;
        }
        // Reserve the complete receipt from the first frame, so new rows never move the hand.
        String limit = ScoreAnnouncements.limit(win.score(), win.seat() == view.dealer());
        if (scored) {
            int gain = win.seat() < view.deltas().size() ? view.deltas().get(win.seat()) : 0;
            Component points = Component.translatable("ui.mchjong.points", gain);
            Component grade = limit == null ? Component.empty() : Component.translatable(ScoreAnnouncements.SUBTITLES.get(limit));
            int gradeWidth = limit == null ? 0 : 2 * (font.width(grade) + 8);
            boolean beside = compact && limit != null && 2 * font.width(points) + gradeWidth + 8 <= span;
            largeText(graphics, points, 0, y + 2, beside ? span - gradeWidth - 8 : span, GOLD);
            if (graphics != null && limit != null && (readout == null || Util.getMillis() - readout.scoredAt(winner) >= 350))
                largeBadge(graphics, grade, beside ? span - gradeWidth : 0, beside ? y : y + 26);
            y += limit == null || beside ? 28 : 54;
        }
        else y += limit == null ? 28 : 54;
        if (win.tile() >= 0) {
            y += 4;
            int leftHeight = indicators(graphics, 0, y, span / 2 - 4, false, compact);
            int rightHeight = player.riichi() ? indicators(graphics, span / 2, y, span / 2 - 4, true, compact) : 0;
            y += Math.max(leftHeight, rightHeight);
        }
        return y + 2;
    }

    private int handWidth(int tiles, TableView.Seat player, int owner, int tileWidth, int handGap) {
        return tiles * (tileWidth + 1) + handGap
            + player.melds().stream().mapToInt(meld -> TileGui.meldWidth(meld, owner, tileWidth) + 5).sum();
    }

    private int indicators(GuiGraphics graphics, int x, int y, int span, boolean ura, boolean compact) {
        var tiles = new ArrayList<Integer>();
        for (int i = 0; i < 5; i++) {
            int index = view.wall().size() - (ura ? 6 : 5) - 2 * i;
            if (index >= 0 && view.wall().get(index) >= 0) tiles.add(view.wall().get(index));
        }
        if (tiles.isEmpty()) return 0;
        Component label = Component.translatable(compact ? ura ? "ui.mchjong.ura.short" : "ui.mchjong.dora.short"
            : ura ? "ui.mchjong.ura_indicators" : "ui.mchjong.result_indicators");
        int labelWidth = compact ? Math.min(span - tiles.size() * 12 - 3, font.width(label) + 3) : span;
        text(graphics, label, x, y + (compact ? 4 : 0), labelWidth, MUTED);
        for (int i = 0; i < tiles.size(); i++) if (graphics != null)
            TileGui.tile(graphics, tiles.get(i), x + (compact ? labelWidth : 0) + i * (compact ? 12 : 14),
                y + (compact ? 0 : 11), compact ? 10 : 12, false, false, false, false, preset, material, dye, backPreset);
        return compact ? 17 : 31;
    }

    private void drawHands(GuiGraphics graphics, int x, int y, int span, int available) {
        int columns = 2, rows = (view.seats().size() + 1) / 2;
        int cardWidth = span / columns, cardHeight = available / rows;
        for (int seat = 0; seat < view.seats().size(); seat++) {
            var player = view.seats().get(seat);
            int cx = x + seat % columns * cardWidth, cy = y + seat / columns * cardHeight;
            Component status = Component.translatable(view.result().equals("exhaustive")
                ? player.exposed() ? "ui.mchjong.tenpai" : "ui.mchjong.noten" : "ui.mchjong.no_winner");
            name(graphics, seat, TableScreen.playerName(view, seat), cx, cy, cardWidth - 8, TEXT);
            line(graphics, status, cx, cy + 11, cardWidth - 8, player.exposed() ? GOLD : MUTED);
            int concealed = player.exposed() ? player.hand().size() : 0;
            if (concealed > 0 || !player.melds().isEmpty()) {
                int tileWidth = Math.max(4, Math.min(14, (cardHeight - 27) / 2));
                while (tileWidth > 4 && handWidth(concealed, player, seat, tileWidth, 0) > cardWidth - 8) tileWidth--;
                int tx = cx, tileY = cy + 24 + tileWidth / 2;
                if (player.exposed()) for (int tile : player.hand()) {
                    TileGui.tile(graphics, tile, tx, tileY, tileWidth, false, false, false, false, preset, material, dye, backPreset);
                    tx += tileWidth + 1;
                }
                for (var meld : player.melds()) {
                    tx += 5;
                    TileGui.meld(graphics, meld, seat, tx, tileY, tileWidth, preset, material, dye, backPreset);
                    tx += TileGui.meldWidth(meld, seat, tileWidth);
                }
            }
        }
    }

    private void scoreTable(GuiGraphics graphics, int x, int y, int span, int available) {
        boolean standings = page == Page.MATCH;
        var order = IntStream.range(0, view.seats().size()).boxed().toList();
        if (standings && view.finalRanks().size() == view.seats().size())
            order = order.stream().sorted(Comparator.comparingInt(seat -> view.finalRanks().get(seat))).toList();
        int[] ends = standings ? new int[]{span * 40 / 100, span * 62 / 100, span * 80 / 100, span - 4}
            : new int[]{span * 38 / 100, span * 59 / 100, span * 78 / 100, span - 4};
        String[] labels = standings ? new String[]{"ui.mchjong.player", "ui.mchjong.points.short", "ui.mchjong.uma.short", "ui.mchjong.final.short"}
            : new String[]{"ui.mchjong.player", "ui.mchjong.before", "ui.mchjong.change", "ui.mchjong.after"};
        for (int i = 0; i < labels.length; i++) {
            Component label = Component.translatable(labels[i]);
            int start = i == 0 ? 4 : ends[i - 1] + 4;
            text(graphics, label, x + start, y, ends[i] - start, MUTED);
        }
        int rowHeight = Math.min(36, (available - 13) / order.size());
        for (int row = 0; row < order.size(); row++) {
            int seat = order.get(row), cy = y + 13 + row * rowHeight;
            var player = view.seats().get(seat);
            int delta = seat < view.deltas().size() ? view.deltas().get(seat) : 0;
            graphics.fill(x, cy, x + span, cy + rowHeight - 2, seat == view.viewerSeat() ? MahjongUi.SELECTED : MahjongUi.SURFACE);
            Component name = TableScreen.playerName(view, seat);
            if (standings && seat < view.finalRanks().size()) name = Component.literal(view.finalRanks().get(seat) + ". ").append(name);
            int textY = cy + Math.max(2, (rowHeight - 9) / 2);
            name(graphics, seat, name, x + 4, textY, ends[0] - 8, TEXT);
            int points = displayedPoints(seat);
            List<String> values = standings ? List.of(Integer.toString(player.points()),
                seat < view.finalUma().size() ? String.format(Locale.ROOT, "%+.2f", view.finalUma().get(seat)) : "—",
                seat < view.finalScores().size() ? String.format(Locale.ROOT, "%+.1f", view.finalScores().get(seat)) : "—")
                : List.of(Integer.toString(player.points() - delta), pointsVisible() ? String.format(Locale.ROOT, "%+d", delta) : "—", Integer.toString(points));
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                int color = standings ? i > 0 ? value.startsWith("-") ? MahjongUi.NEGATIVE : GOLD : TEXT
                    : i == 1 ? value.startsWith("-") ? MahjongUi.NEGATIVE : GOLD : TEXT;
                graphics.drawString(font, value, x + ends[i + 1] - font.width(value) - 3, textY, color, false);
            }
            hits.add(new Hit(x, cy, span, rowHeight, name.copy().append("  ").append(Component.translatable("ui.mchjong.points", player.points()))));
        }
    }

    private void scores(GuiGraphics graphics, int x, int y, int span, int available, boolean horizontal) {
        var order = IntStream.range(0, view.seats().size()).boxed().toList();
        if (page == Page.MATCH && view.finalRanks().size() == view.seats().size())
            order = order.stream().sorted(Comparator.comparingInt(seat -> view.finalRanks().get(seat))).toList();
        int cardWidth = horizontal ? span / order.size() : span;
        int cardHeight = horizontal ? available : Math.min(58, available / order.size());
        for (int index = 0; index < order.size(); index++) {
            int seat = order.get(index);
            var player = view.seats().get(seat);
            int cx = x + (horizontal ? index * cardWidth : 0), cy = y + (horizontal ? 0 : index * cardHeight);
            int delta = seat < view.deltas().size() ? view.deltas().get(seat) : 0;
            int points = displayedPoints(seat);
            graphics.fill(cx, cy, cx + cardWidth - 3, cy + cardHeight - 3, seat == view.viewerSeat() ? MahjongUi.SELECTED : MahjongUi.SURFACE);
            Component name = TableScreen.playerName(view, seat);
            if (page == Page.MATCH && seat < view.finalRanks().size())
                name = Component.translatable("ui.mchjong.rank", view.finalRanks().get(seat)).append("  ").append(name);
            name(graphics, seat, name, cx + 4, cy + 3, cardWidth - 11, TEXT);
            String amount = horizontal ? Integer.toString(points) : player.points() - delta + " → " + points;
            text(graphics, Component.literal(amount), cx + 4, cy + 14, cardWidth - 11, TEXT);
            Component change = page == Page.MATCH && seat < view.finalScores().size()
                ? Component.translatable("ui.mchjong.final_score", String.format(Locale.ROOT, "%+.1f", view.finalScores().get(seat)))
                : Component.literal(pointsVisible() ? String.format(Locale.ROOT, "%+d", delta) : "—");
            text(graphics, change, cx + 4, cy + 25, cardWidth - 11, delta < 0 ? MahjongUi.NEGATIVE : GOLD);
            hits.add(new Hit(cx, cy, cardWidth - 3, cardHeight - 3, name.copy().append("  ")
                .append(Component.literal((player.points() - delta) + " → " + player.points() + " (" + String.format(Locale.ROOT, "%+d", delta) + ")"))));
        }
    }

    private void name(GuiGraphics graphics, int seat, Component name, int x, int y, int span, int color) {
        int inset = PlayerPortrait.draw(graphics, view.seats().get(seat), x, y - 1, 10);
        line(graphics, name, x + inset, y, span - inset, color);
    }

    private void text(GuiGraphics graphics, Component text, int x, int y, int span, int color) {
        if (graphics != null) graphics.drawString(font, font.plainSubstrByWidth(text.getString(), Math.max(1, span)), x, y, color, false);
    }
    private void badge(GuiGraphics graphics, Component text, int x, int y, boolean grade) {
        graphics.fill(x, y, x + font.width(text) + 8, y + 13, grade ? GOLD : MahjongUi.SELECTED);
        graphics.drawString(font, text, x + 4, y + 2, grade ? MahjongUi.INPUT : TEXT, false);
    }
    private void largeText(GuiGraphics graphics, Component value, int x, int y, int span, int color) {
        if (graphics == null) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(2, 2, 1);
        text(graphics, value, 0, 0, span / 2, color);
        graphics.pose().popPose();
    }
    private void largeBadge(GuiGraphics graphics, Component value, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(2, 2, 1);
        badge(graphics, value, 0, 0, true);
        graphics.pose().popPose();
    }
    private void line(GuiGraphics graphics, Component text, int x, int y, int span, int color) {
        text(graphics, text, x, y, span, color);
        if (font.width(text) > span) hits.add(new Hit(x, y, span, 10, text));
    }
    private Component winnerSummary(TableView.Win win) {
        var summary = TableScreen.playerName(view, win.seat()).copy();
        for (var row : receipts.get(view.wins().indexOf(win))) {
            summary.append("  ").append(Component.translatable(row.translationKey()));
            if (row.han() > 0) summary.append(" ").append(Component.translatable("ui.mchjong.han", row.han()));
        }
        return summary;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        double localX = (x - getX()) / contentScale, localY = (y - getY()) / contentScale;
        int contentWidth = width / contentScale;
        if (button == 0 && page == Page.HAND && view.wins().size() > 1 && localY >= 21 && localY < 36) {
            int span = contentWidth - 20 - (contentWidth >= 500 ? 156 : 0);
            if (localX >= 10 && localX < 10 + span) {
                TableAudio.finishResult();
                winner = Math.min(view.wins().size() - 1, (int) (localX - 10) / (span / view.wins().size()));
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (page == Page.HAND && view.wins().size() > 1 && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            winner = selectedWinner();
            TableAudio.finishResult();
            winner = Math.floorMod(winner + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1), view.wins().size());
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        var summary = getMessage().copy();
        for (int seat = 0; seat < view.seats().size(); seat++)
            summary.append(". ").append(TableScreen.playerName(view, seat)).append(" ")
                .append(Component.translatable("ui.mchjong.points", view.seats().get(seat).points()));
        for (var win : view.wins()) summary.append(". ").append(winnerSummary(win));
        output.add(NarratedElementType.TITLE, summary);
        output.add(NarratedElementType.USAGE, Component.translatable("ui.mchjong.result_help"));
    }
}
