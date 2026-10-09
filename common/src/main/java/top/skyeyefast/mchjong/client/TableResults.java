package top.skyeyefast.mchjong.client;

import top.skyeyefast.mchjong.text.CountedText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

/** A single-screen settlement, with a winner selector for multiple ron and no scroll viewport. */
public final class TableResults extends AbstractWidget {
    public enum Page { HAND, POINTS, MATCH, PAYMENTS }
    private static final int TEXT = MahjongUi.TEXT, MUTED = MahjongUi.MUTED, GOLD = MahjongUi.ACCENT;
    private final Font font;
    private TableResultState view;
    private final TableResultState source;
    private final List<List<TableResultState.Row>> receipts;
    private final TileFacePreset preset;
    private final TileMaterial material;
    private final DyeColor dye;
    private final net.minecraft.resources.ResourceLocation backPreset;
    private final Page page;
    private final long started;
    private final int contentScale;
    private int winner;
    private ResultReadout readout;
    private int paymentPage;
    private java.util.function.IntUnaryOperator artwork = TileMesh::face;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> awardHits = new ArrayList<>();
    private int helpRow;
    private float receiptScale = 1;
    private float receiptX;
    private int receiptY;
    private record Hit(int x, int y, int width, int height, Component text) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }

    public TableResults(Font font, RiichiView view, TileFacePreset preset, TileMaterial material, DyeColor dye,
                        int x, int y, int width, int height, int winner, Page page, long started, int contentScale) {
        this(font, view, preset, material, dye, TileBackPresets.DEFAULT, x, y, width, height, winner, page, started, contentScale);
    }

    public TableResults(Font font, RiichiView view, TileFacePreset preset, TileMaterial material, DyeColor dye,
                        net.minecraft.resources.ResourceLocation backPreset,
                        int x, int y, int width, int height, int winner, Page page, long started, int contentScale) {
        this(font, TableResultState.riichi(view), preset, material, dye, backPreset, x, y, width, height, winner, page, started, contentScale);
    }

    TableResults(Font font, TableResultState view, TileFacePreset preset, TileMaterial material, DyeColor dye,
                 net.minecraft.resources.ResourceLocation backPreset, int x, int y, int width, int height,
                 int winner, Page page, long started, int contentScale) {
        super(x, y, width, height, view.heading());
        this.font = font; this.source = view; this.view = view.withViewer(view.viewerSeat()); this.receipts = view.wins().stream().map(TableResultState.Win::rows).toList();
        this.preset = preset; this.material = material; this.dye = dye; this.backPreset = backPreset;
        this.page = page; this.started = started; this.contentScale = contentScale;
        this.winner = Math.clamp(winner, 0, Math.max(0, view.wins().size() - 1));
    }

    public TableResults(Font font, RiichiView view, TileFacePreset preset, int x, int y, int width, int height,
                        int winner, Page page, long started, int contentScale) {
        this(font, view, preset, TileMaterial.BONE, null, x, y, width, height, winner, page, started, contentScale);
    }

    public TableResults readout(ResultReadout value) { readout = value; return this; }
    TableResults artwork(java.util.function.IntUnaryOperator value) { artwork = value; return this; }
    void setViewer(int viewer) { view = source.withViewer(viewer); }
    public int selectedWinner() { return readout != null && !readout.complete() ? readout.winner() : winner; }
    public TileMaterial material() { return material; }
    public DyeColor dye() { return dye; }
    /** Every result page presents the same once-per-settlement score transition. */
    public long displayedPoints(int seat) {
        long delta = seat < view.deltas().size() ? view.deltas().get(seat).longValue() : 0;
        long pointsAt = readout == null ? started : readout.pointsAt();
        double progress = pointsAt < 0 ? 0 : TableSettings.get().animations
            ? Math.clamp((Util.getMillis() - pointsAt - 250) / 900.0, 0, 1) : 1;
        return view.seats().get(seat).points() - delta + Math.round(delta * progress);
    }
    private boolean pointsVisible() { return readout == null || readout.pointsAt() >= 0; }
    public static boolean available(RiichiView view) {
        return view.phase() == RiichiView.Phase.HAND_END || view.phase() == RiichiView.Phase.MATCH_END;
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        winner = selectedWinner();
        hits.clear(); awardHits.clear();
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
        var tooltips = new ArrayList<>(awardHits); tooltips.addAll(hits);
        for (Hit hit : tooltips) if (hit.contains(contentMouseX, contentMouseY)) {
            graphics.renderTooltip(font, font.split(awardHits.contains(hit) ? RuleHelp.hover(hit.text()) : hit.text(), Math.min(360, contentWidth - 24)), contentMouseX, contentMouseY);
            break;
        }
        graphics.pose().popPose();
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, int width, int height) {
        int x = 0, y = 0;
        Component heading = page == Page.HAND ? getMessage() : Component.translatable(
            page == Page.POINTS ? "ui.mchjong.point_changes" : page == Page.PAYMENTS ? "ui.mchjong.result_page.3" : "ui.mchjong.match_complete");
        line(graphics, heading, x + 9, y + 7, width - 18, GOLD);
        int top = y + 23, bottom = y + height - 6;
        if (page == Page.PAYMENTS) {
            int rows = Math.max(1, (bottom - top - 16) / 13);
            paymentPage = Math.min(paymentPage, Math.max(0, (view.payments().size() - 1) / rows));
            for (int i = paymentPage * rows; i < Math.min(view.payments().size(), (paymentPage + 1) * rows); i++)
                line(graphics, view.payments().get(i), 10, top + (i % rows) * 13, width - 20, TEXT);
            line(graphics, Component.literal((paymentPage + 1) + " / " + Math.max(1, (view.payments().size() + rows - 1) / rows)), 10, bottom - 12, width - 20, MUTED);
        } else if (page != Page.HAND) {
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
                    name(graphics, view.wins().get(i).seat(), view.name(view.wins().get(i).seat()), tabX + 4, top + 1, tabWidth - 10,
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
                receiptScale = scale; receiptX = x + 10 + (bodyWidth - bodyWidth * scale) / 2; receiptY = top;
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
        Component source = win.source();
        Component score = win.score();
        boolean named = view.wins().size() == 1;
        if (named) name(graphics, win.seat(), view.name(win.seat()), 0, 0, span, GOLD);
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
                if (graphics != null) TileGui.tileArtwork(graphics, hand.get(i), x, y + tileWidth / 2, tileWidth, false, false, i == hand.size() - 1, false, 0, preset, material, dye, backPreset, artwork);
                x += tileWidth + 1;
            }
            for (var meld : player.melds()) {
                x += 5;
                if (graphics != null) TileGui.meldArtwork(graphics, player.layout(meld, win.seat()), x, y + tileWidth / 2, tileWidth, 0, preset, material, dye, backPreset, artwork);
                x += TileGui.meldWidth(player.layout(meld, win.seat()), tileWidth);
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
                Component han = row.badge();
                int badgeWidth = !han.getString().isEmpty() ? font.width(han) + 8 : 0;
                var lines = font.split(row.label(), Math.max(1, colWidth - badgeWidth - 13));
                int color = readout != null && !readout.complete() && first + col == visible - 1 ? GOLD : TEXT;
                if (graphics != null && first + col < visible) {
                    if (RuleHelp.award(row.label()) != null) {
                        int rowIndex = awardHits.size();
                        awardHits.add(new Hit(Math.round(receiptX + col * colWidth * receiptScale), Math.round(receiptY + y * receiptScale),
                            Math.max(1, Math.round((colWidth - 4) * receiptScale)), Math.max(1, Math.round(Math.max(13, lines.size() * 10 + 3) * receiptScale)), row.label()));
                        if (isFocused() && helpRow == rowIndex) graphics.renderOutline(col * colWidth - 1, y, colWidth - 4, Math.max(13, lines.size() * 10 + 3), GOLD);
                        graphics.drawString(font, "?", col * colWidth + colWidth - 10, y + 2, MUTED, false);
                    }
                    if (badgeWidth > 0) badge(graphics, han,
                        col * colWidth + font.width(lines.getLast()) + 4, y + (lines.size() - 1) * 10, false);
                    for (int line = 0; line < lines.size(); line++)
                        graphics.drawString(font, lines.get(line), col * colWidth, y + 2 + line * 10, color, false);
                }
                rowHeight = Math.max(rowHeight, Math.max(13, lines.size() * 10 + 3));
            }
            y += rowHeight + 2;
        }
        y += 8;
        // Reserve the complete receipt from the first frame, so new rows never move the hand.
        boolean hasGrade = !win.grade().getString().isEmpty();
        long gain = win.seat() < view.deltas().size() ? view.deltas().get(win.seat()).longValue() : 0;
        Component points = CountedText.of("ui.mchjong.points", 0, gain);
        Component grade = win.grade();
        int gradeWidth = !hasGrade ? 0 : 2 * (font.width(grade) + 8);
        boolean beside = compact && hasGrade && 2 * font.width(points) + gradeWidth + 8 <= span;
        int scoreHeight = !hasGrade || beside ? 28 : 54;
        if (scored) {
            largeText(graphics, points, 0, y + 2, beside ? span - gradeWidth - 8 : span, GOLD);
            if (graphics != null && hasGrade && (readout == null || readout.limitVisible(winner)))
                largeBadge(graphics, grade, beside ? span - gradeWidth : 0, beside ? y : y + 26);
        }
        y += scoreHeight;
        if (win.tile() >= 0) {
            y += 4;
            int leftHeight = indicators(graphics, 0, y, span / 2 - 4, false, compact);
            int rightHeight = indicators(graphics, span / 2, y, span / 2 - 4, true, compact);
            y += Math.max(leftHeight, rightHeight);
        }
        return y + 2;
    }

    private int handWidth(int tiles, TableResultState.Seat player, int owner, int tileWidth, int handGap) {
        return tiles * (tileWidth + 1) + handGap
            + player.melds().stream().mapToInt(meld -> TileGui.meldWidth(player.layout(meld, owner), tileWidth) + 5).sum();
    }

    private int indicators(GuiGraphics graphics, int x, int y, int span, boolean ura, boolean compact) {
        var tiles = ura ? view.wins().get(winner).ura() : view.wins().get(winner).indicators();
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
            Component status = player.status();
            name(graphics, seat, view.name(seat), cx, cy, cardWidth - 8, TEXT);
            line(graphics, status, cx, cy + 11, cardWidth - 8, player.exposed() ? GOLD : MUTED);
            int concealed = player.exposed() ? player.hand().size() : 0;
            if (concealed > 0 || !player.melds().isEmpty()) {
                int tileWidth = Math.max(4, Math.min(14, (cardHeight - 27) / 2));
                while (tileWidth > 4 && handWidth(concealed, player, seat, tileWidth, 0) > cardWidth - 8) tileWidth--;
                int tx = cx, tileY = cy + 24 + tileWidth / 2;
                if (player.exposed()) for (int tile : player.hand()) {
                    TileGui.tileArtwork(graphics, tile, tx, tileY, tileWidth, false, false, false, false, 0, preset, material, dye, backPreset, artwork);
                    tx += tileWidth + 1;
                }
                for (var meld : player.melds()) {
                    tx += 5;
                    TileGui.meldArtwork(graphics, player.layout(meld, seat), tx, tileY, tileWidth, 0, preset, material, dye, backPreset, artwork);
                    tx += TileGui.meldWidth(player.layout(meld, seat), tileWidth);
                }
            }
        }
    }

    private void scoreTable(GuiGraphics graphics, int x, int y, int span, int available) {
        boolean standings = page == Page.MATCH;
        var order = IntStream.range(0, view.seats().size()).boxed().toList();
        if (standings && view.finalRanks().size() == view.seats().size())
            order = order.stream().sorted(Comparator.comparingInt(seat -> view.finalRanks().get(seat))).toList();
        boolean uma = view.seats().getFirst().variant() == top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI;
        int[] ends = standings && !uma ? new int[]{span * 65 / 100, span - 4} : standings ? new int[]{span * 40 / 100, span * 62 / 100, span * 80 / 100, span - 4}
            : new int[]{span * 38 / 100, span * 59 / 100, span * 78 / 100, span - 4};
        String[] labels = standings && !uma ? new String[]{"ui.mchjong.player", "ui.mchjong.points.short"} : standings ? new String[]{"ui.mchjong.player", "ui.mchjong.points.short", "ui.mchjong.uma.short", "ui.mchjong.final.short"}
            : new String[]{"ui.mchjong.player", "ui.mchjong.before", "ui.mchjong.change", "ui.mchjong.after"};
        graphics.fill(x, y, x + span, y + 17, MahjongUi.INPUT);
        graphics.fill(x, y + 16, x + span, y + 17, MahjongUi.EDGE);
        for (int i = 0; i < labels.length; i++) {
            Component label = Component.translatable(labels[i]);
            int start = i == 0 ? 18 : ends[i - 1] + 4;
            if (i == 0) line(graphics, label, x + start, y + 4, ends[i] - start - 4, MUTED);
            else tableNumber(graphics, label, x + start, y + 4, ends[i] - start - 4, MUTED);
        }
        int rowHeight = Math.min(26, (available - 18) / Math.max(1, order.size()));
        for (int row = 0; row < order.size(); row++) {
            int seat = order.get(row), cy = y + 18 + row * rowHeight;
            var player = view.seats().get(seat);
            long delta = seat < view.deltas().size() ? view.deltas().get(seat).longValue() : 0;
            graphics.fill(x, cy, x + span, cy + rowHeight, seat == view.viewerSeat() ? MahjongUi.SELECTED : MahjongUi.SURFACE);
            graphics.fill(x, cy + rowHeight - 1, x + span, cy + rowHeight, MahjongUi.EDGE);
            if (seat == view.viewerSeat()) graphics.fill(x, cy, x + 2, cy + rowHeight - 1, GOLD);
            Component name = view.name(seat);
            if (standings && seat < view.finalRanks().size()) name = Component.literal(view.finalRanks().get(seat) + ". ").append(name);
            int textY = cy + Math.max(2, (rowHeight - 9) / 2);
            name(graphics, seat, name, x + 4, textY, ends[0] - 8, TEXT);
            long points = displayedPoints(seat);
            List<String> values = standings && !uma ? List.of(Long.toString(player.points())) : standings ? List.of(Long.toString(player.points()),
                seat < view.finalUma().size() ? String.format(Locale.ROOT, "%+.2f", view.finalUma().get(seat)) : "—",
                seat < view.finalScores().size() ? String.format(Locale.ROOT, "%+.1f", view.finalScores().get(seat)) : "—")
                : List.of(Long.toString(player.points() - delta), pointsVisible() ? String.format(Locale.ROOT, "%+d", delta) : "—", Long.toString(points));
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                int color = standings ? i > 0 ? value.startsWith("-") ? MahjongUi.NEGATIVE : GOLD : TEXT
                    : i == 1 ? value.startsWith("-") ? MahjongUi.NEGATIVE : GOLD : TEXT;
                tableNumber(graphics, Component.literal(value), x + ends[i] + 4, textY, ends[i + 1] - ends[i] - 8, color);
            }
            hits.add(new Hit(x, cy, span, rowHeight, name.copy().append("  ").append(CountedText.of("ui.mchjong.points", 0, player.points()))));
        }
    }

    private void tableNumber(GuiGraphics graphics, Component value, int x, int y, int span, int color) {
        int measured = font.width(value);
        MahjongUi.text(graphics, font, value, x + Math.max(0, span - measured), y, span, color, false);
        if (measured > span) hits.add(new Hit(x, y, span, 10, value));
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
            long delta = seat < view.deltas().size() ? view.deltas().get(seat).longValue() : 0;
            long points = displayedPoints(seat);
            graphics.fill(cx, cy, cx + cardWidth - 3, cy + cardHeight - 3, seat == view.viewerSeat() ? MahjongUi.SELECTED : MahjongUi.SURFACE);
            Component name = view.name(seat);
            if (page == Page.MATCH && seat < view.finalRanks().size())
                name = Component.translatable("ui.mchjong.rank", view.finalRanks().get(seat)).append("  ").append(name);
            name(graphics, seat, name, cx + 4, cy + 3, cardWidth - 11, TEXT);
            String amount = horizontal ? Long.toString(points) : player.points() - delta + " → " + points;
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
        var p = view.seats().get(seat);
        int inset = p.occupied() ? PlayerPortrait.draw(graphics, p.entityBot(), p.bot(), p.name().getString(), x, y - 1, 10) : 0;
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
        if (graphics != null) MahjongUi.text(graphics, font, text, x, y, span, color, false);
        if (font.width(text) > span) hits.add(new Hit(x, y, span, 10, text));
    }
    private Component winnerSummary(TableResultState.Win win) {
        var summary = view.name(win.seat()).copy();
        for (var row : receipts.get(view.wins().indexOf(win))) {
            summary.append("  ").append(row.label());
            if (!row.badge().getString().isEmpty()) summary.append(" ").append(row.badge());
        }
        return summary;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        double localX = (x - getX()) / contentScale, localY = (y - getY()) / contentScale;
        if (button == 0) for (int i = 0; i < awardHits.size(); i++) if (awardHits.get(i).contains(localX, localY)) {
            helpRow = i; RuleHelp.openAward(Minecraft.getInstance().screen, awardHits.get(i).text()); return true;
        }
        int contentWidth = width / contentScale;
        if (button == 0 && page == Page.HAND && view.wins().size() > 1 && localY >= 21 && localY < 36) {
            int span = contentWidth - 20 - (contentWidth >= 500 ? 156 : 0);
            if (localX >= 10 && localX < 10 + span) {
                if (readout != null) readout.finish(Util.getMillis());
                RiichiAudio.finishResult();
                winner = Math.min(view.wins().size() - 1, (int) (localX - 10) / (span / view.wins().size()));
                helpRow = 0;
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (page != Page.PAYMENTS) return false;
        paymentPage = Math.max(0, paymentPage + (vertical < 0 ? 1 : -1));
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (page == Page.HAND && !awardHits.isEmpty()) {
            helpRow = Math.clamp(helpRow, 0, awardHits.size() - 1);
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                helpRow = Math.floorMod(helpRow + (key == GLFW.GLFW_KEY_UP ? -1 : 1), awardHits.size()); return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
                RuleHelp.openAward(Minecraft.getInstance().screen, awardHits.get(helpRow).text()); return true;
            }
        }
        if (page == Page.PAYMENTS && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            paymentPage = Math.max(0, paymentPage + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1)); return true;
        }
        if (page == Page.HAND && view.wins().size() > 1 && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            winner = selectedWinner();
            if (readout != null) readout.finish(Util.getMillis());
            RiichiAudio.finishResult();
            winner = Math.floorMod(winner + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1), view.wins().size());
            helpRow = 0;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        var summary = getMessage().copy();
        for (int seat = 0; seat < view.seats().size(); seat++)
            summary.append(". ").append(view.name(seat)).append(" ")
                .append(CountedText.of("ui.mchjong.points", 0, view.seats().get(seat).points()));
        for (var win : view.wins()) summary.append(". ").append(winnerSummary(win));
        output.add(NarratedElementType.TITLE, summary);
        output.add(NarratedElementType.USAGE, Component.translatable("ui.mchjong.result_help"));
        if (!awardHits.isEmpty()) output.add(NarratedElementType.USAGE, Component.translatable("ui.mchjong.result_explanation"));
        if (!awardHits.isEmpty()) output.add(NarratedElementType.HINT, RuleHelp.hover(awardHits.get(Math.clamp(helpRow, 0, awardHits.size() - 1)).text()));
    }
}
