package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.PlayerPresence;
import top.skyeyefast.mchjong.engine.RoomView;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Edge-aligned, compact information. Detailed counts and status belong in hover text, not over the hand. */
final class TableHud {
    private static final String[] WINDS = {"east", "south", "west", "north"};
    private record Region(int x, int y, int width, int height, Component text) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    private final List<Region> regions = new ArrayList<>();
    private TableView furitenView;
    private boolean furiten;
    void clear() { regions.clear(); }
    int bottom() { return regions.stream().mapToInt(region -> region.y() + region.height()).max().orElse(34); }
    boolean contains(double x, double y) { return regions.stream().anyMatch(region -> region.contains(x, y)); }
    int hintHalfWidth(int center, int bottom, int halfWidth) {
        for (var region : regions) if (region.y() < bottom && region.y() + region.height() > bottom - 57) {
            if (region.x() > center) halfWidth = Math.min(halfWidth, region.x() - center - 4);
            else if (region.x() + region.width() < center)
                halfWidth = Math.min(halfWidth, center - region.x() - region.width() - 4);
        }
        return halfWidth;
    }
    Component tooltip(int x, int y) {
        return regions.stream().filter(region -> region.contains(x, y)).map(Region::text).findFirst().orElse(null);
    }

    void render(Font font, GuiGraphics graphics, TableView view, RoomView room, int width, TileFacePreset preset, TableBoard board) {
        clear();
        TableSettings settings = TableSettings.get();
        boolean lobby = view.phase() == Game.Phase.LOBBY;
        int headerWidth = Math.min(280, Math.max(84, width - 224));
        Component details = Component.translatable(view.rules().translationKey()).append("\n").append(TableScreen.roundName(view))
            .append("\n").append(Component.translatable("ui.mchjong.table_deposits", view.honba(), view.riichiSticks()));
        if (view.openHands()) details = details.copy().append("\n").append(Component.translatable("ui.mchjong.open_hands"));
        Component title = lobby ? Component.translatable("ui.mchjong.title")
            : settings.show(TableSettings.Information.ROUND) ? Component.translatable("ui.mchjong.round_short",
                Component.translatable("wind.mchjong." + WINDS[Math.min(3, view.round() / view.rules().players())]),
                view.round() % view.rules().players() + 1)
            : settings.show(TableSettings.Information.RULES) ? Component.translatable(view.rules().custom()
                ? "rules.mchjong.custom" : view.rules().preset().presetKey()) : Component.empty();
        Component remaining = !lobby && settings.show(TableSettings.Information.REMAINING)
            ? Component.translatable("ui.mchjong.remaining", view.remaining()) : Component.empty();
        if (board != null) {
            title = settings.show(TableSettings.Information.RULES)
                ? Component.translatable(view.rules().custom() ? "rules.mchjong.custom" : view.rules().preset().presetKey()) : Component.empty();
            remaining = settings.show(TableSettings.Information.DEPOSITS)
                ? Component.translatable("ui.mchjong.table_deposits", view.honba(), view.riichiSticks()) : Component.empty();
        }
        var indicators = new ArrayList<Integer>();
        if (!lobby && settings.show(TableSettings.Information.DORA)) for (int i = 0; i < 5; i++) {
            int index = view.wall().size() - 5 - 2 * i;
            if (index >= 0 && view.wall().get(index) >= 0) indicators.add(view.wall().get(index));
        }
        boolean seated = board == null && !lobby;
        boolean compactHeader = seated && headerWidth < 160;
        int indicatorWidth = compactHeader ? 8 : 14;
        int indicatorSpan = indicators.size() * (indicatorWidth + 2);
        boolean deposits = seated && settings.show(TableSettings.Information.DEPOSITS);
        int depositSpan = deposits ? 34 + font.width(Integer.toString(view.honba())) + font.width(Integer.toString(view.riichiSticks())) : 0;
        int depositX = 8 + headerWidth - 4 - (compactHeader ? 0 : indicatorSpan) - depositSpan;
        if (compactHeader && !remaining.getString().isEmpty()) remaining = Component.translatable("ui.mchjong.remaining_short", view.remaining());
        if (!title.getString().isEmpty() || !remaining.getString().isEmpty() || deposits || !indicators.isEmpty()) {
            MahjongUi.panel(graphics, 8, 7, headerWidth, lobby ? 21 : 26);
            text(font, graphics, title, 12, 10, headerWidth - 8 - indicatorSpan, MahjongUi.TEXT);
            text(font, graphics, remaining, 12, 22, seated ? depositX - 12 - (deposits ? 4 : 0) : headerWidth - 8 - indicatorSpan, MahjongUi.MUTED);
            if (deposits) {
                stick(graphics, depositX, 25, false);
                int countX = depositX + 16;
                graphics.drawString(font, Integer.toString(view.honba()), countX, 22, MahjongUi.MUTED, false);
                int riichiX = countX + font.width(Integer.toString(view.honba())) + 2;
                stick(graphics, riichiX, 25, true);
                graphics.drawString(font, Integer.toString(view.riichiSticks()), riichiX + 16, 22, MahjongUi.MUTED, false);
            }
            regions.add(new Region(8, 7, headerWidth, lobby ? 21 : 26, details));
        }
        int cardWidth = (width - 16 - (view.seats().size() - 1) * 4) / view.seats().size();
        int top = lobby ? 32 : 38;
        boolean seatsVisible = lobby || settings.show(TableSettings.Information.NAMES) || settings.show(TableSettings.Information.WINDS)
            || settings.show(TableSettings.Information.POINTS) || settings.show(TableSettings.Information.RANKS)
            || settings.show(TableSettings.Information.STATUS) || settings.show(TableSettings.Information.COUNTS)
            || settings.show(TableSettings.Information.MELDS);
        for (int seat = 0; seatsVisible && seat < view.seats().size(); seat++) {
            var player = view.seats().get(seat);
            PlayerPresence presence = room != null && seat < room.seats().size() ? room.seats().get(seat).presence() : null;
            boolean disconnected = player.occupied() && !player.bot() && presence == PlayerPresence.DISCONNECTED;
            Component wind = Component.translatable("wind.mchjong." + WINDS[Math.floorMod(seat - view.dealer(), view.rules().players())]);
            Component name = settings.show(TableSettings.Information.NAMES) || lobby
                ? player.bot() ? Component.translatable("ui.mchjong.bot_short", seat + 1) : TableScreen.playerName(view, seat) : Component.empty();
            Component shortLine = Component.empty();
            Component hover = TableScreen.playerName(view, seat).copy();
            if (settings.show(TableSettings.Information.WINDS)) {
                shortLine = !lobby
                    ? Component.translatable("wind.mchjong." + WINDS[Math.floorMod(seat - view.dealer(), view.rules().players())] + ".short")
                    : wind.copy();
                hover = hover.copy().append(" · ").append(wind);
            }
            if (settings.show(TableSettings.Information.POINTS)) {
                shortLine = shortLine.copy().append(" " + player.points());
                hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.points", player.points()));
            }
            if (settings.show(TableSettings.Information.RANKS)) {
                long rank = 1 + view.seats().stream().filter(other -> other.points() > player.points()).count();
                hover = hover.copy().append(" · ").append(Component.translatable("ui.mchjong.rank", rank));
            }
            if (lobby) shortLine = wind.copy().append(" · ").append(Component.translatable(player.ready() ? "ui.mchjong.ready" : "ui.mchjong.not_ready"));
            if (player.occupied() && !player.bot() && presence == PlayerPresence.AWAY) {
                shortLine = appendStatus(shortLine, Component.translatable("room.mchjong.away_short"));
                hover = hover.copy().append("\n").append(Component.translatable("room.mchjong.away"));
            } else if (disconnected) {
                shortLine = appendStatus(shortLine, Component.translatable("room.mchjong.disconnected_short"));
                hover = hover.copy().append("\n").append(TableSeatsScreen.presence(presence));
            }
            if (settings.show(TableSettings.Information.STATUS)) {
                if (seat == view.dealer()) hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.dealer"));
                if (seat == view.turn() && !lobby) hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.current_turn"));
                if (player.riichi()) hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.riichi_status"));
                if (player.exposed()) hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.exposed"));
            }
            if (settings.show(TableSettings.Information.COUNTS)) hover = hover.copy().append("\n")
                .append(Component.translatable("ui.mchjong.counts", player.hand().size(), player.river().size(), player.norths().size()));
            if (settings.show(TableSettings.Information.MELDS)) for (var meld : player.melds()) hover = hover.copy().append("\n")
                .append(Component.translatable("action.mchjong." + meld.type().name().toLowerCase(java.util.Locale.ROOT)));
            int x = 8 + seat * (cardWidth + 4);
            boolean meldSummary = seated && settings.show(TableSettings.Information.MELDS) && !player.melds().isEmpty();
            int summaryWidth = meldSummary ? summaryTileWidth(player.melds(), seat, cardWidth - 10) : 0;
            int cardHeight = seatedCardHeight(meldSummary, summaryWidth);
            if (board != null) {
                var card = board.card(seat);
                x = card.x(); top = card.y(); cardWidth = card.width(); cardHeight = card.height();
            }
            graphics.fill(x, top, x + cardWidth, top + cardHeight, seat == view.viewerSeat() ? MahjongUi.SELECTED : MahjongUi.PANEL);
            boolean turn = !lobby && seat == view.turn() && settings.show(TableSettings.Information.TURN);
            boolean riichi = !lobby && player.riichi() && settings.show(TableSettings.Information.STATUS);
            if (riichi) {
                graphics.fill(x + 2, top + cardHeight - 1, x + cardWidth, top + cardHeight, MahjongUi.ACCENT);
                stick(graphics, x + cardWidth - 19, top, true);
            }
            if (turn || lobby && player.ready()) graphics.fill(x, top, x + 2, top + cardHeight, MahjongUi.ACCENT);
            if (board != null) {
                int inset = name.getString().isEmpty() ? 0 : PlayerPortrait.draw(graphics, player, x + 4, top + 3, 14);
                Component label = name.getString().isEmpty() ? shortLine : name;
                text(font, graphics, label, x + 4 + inset, top + 6, cardWidth - 8 - inset,
                    disconnected ? MahjongUi.NEGATIVE : MahjongUi.TEXT);
                if (board.scoresOnCards() && !name.getString().isEmpty()) text(font, graphics, shortLine, x + 4, top + 20, cardWidth - 8,
                    disconnected ? MahjongUi.NEGATIVE : turn ? MahjongUi.ACCENT : MahjongUi.MUTED);
            } else {
                int inset = name.getString().isEmpty() ? 0 : PlayerPortrait.draw(graphics, player, x + 5, top + 2, 10);
                text(font, graphics, name, x + 5 + inset, top + 4, cardWidth - 10 - inset,
                    disconnected ? MahjongUi.NEGATIVE : MahjongUi.TEXT);
                text(font, graphics, shortLine, x + 5, top + 14, cardWidth - 10,
                    disconnected ? MahjongUi.NEGATIVE : turn ? MahjongUi.ACCENT : MahjongUi.MUTED);
                if (meldSummary) {
                    int tileWidth = summaryWidth;
                    if (tileWidth == 0) text(font, graphics, Component.translatable("ui.mchjong.meld_groups", player.melds().size()),
                        x + 5, top + 25, cardWidth - 10, MahjongUi.MUTED);
                    else {
                        int meldX = x + 5;
                        for (var meld : player.melds()) {
                            TileGui.meld(graphics, meld, seat, meldX, top + 27, tileWidth, preset);
                            meldX += TileGui.meldWidth(meld, seat, tileWidth) + 2;
                        }
                    }
                }
            }
            regions.add(new Region(x, top, cardWidth, cardHeight, hover));
            if (seat == view.viewerSeat() && settings.show(TableSettings.Information.STATUS) && furiten(view)) {
                Component label = Component.translatable("ui.mchjong.furiten");
                int badgeWidth = Math.min(cardWidth, font.width(label) + 8);
                int badgeY = top + cardHeight + 2;
                graphics.fill(x, badgeY, x + badgeWidth, badgeY + 12, MahjongUi.DANGER);
                text(font, graphics, label, x + 4, badgeY + 2, badgeWidth - 8, MahjongUi.ON_DANGER);
                regions.add(new Region(x, badgeY, badgeWidth, 12, Component.translatable("ui.mchjong.furiten_hint")));
            }
        }
        if (lobby) return;
        if (!indicators.isEmpty()) {
            int start = headerWidth + 4 - indicatorSpan;
            int x = start, tileWidth = indicatorWidth, tileY = 9;
            for (int tile : indicators) {
                TileGui.tile(graphics, tile, x, tileY, tileWidth, false, false, false, preset);
                x += tileWidth + 2;
            }
            regions.addFirst(new Region(start, tileY, x - start, Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH),
                Component.translatable("ui.mchjong.result_indicators")));
        }
        if (view.focus() != null && (settings.show(TableSettings.Information.FOCUS) || !settings.showRiver)) {
            Component focus = Component.translatable("ui.mchjong.focus");
            int span = Math.min(width / 2 - 8, font.width(focus) + 27);
            int x = width - 8 - span;
            int y = bottom() + 4;
            if (board != null) {
                var focusArea = board.focus();
                x = focusArea.x();
                y = focusArea.y();
                span = focusArea.width();
            }
            MahjongUi.panel(graphics, x, y, span, 17);
            if (span > 28) text(font, graphics, focus, x + 4, y + 5, span - 22, MahjongUi.ACCENT);
            TileGui.tile(graphics, view.focus().tile(), x + span - 15, y + 1, 9, false, false, false, preset);
            regions.add(new Region(x, y, span, 17, TableScreen.playerName(view, view.focus().seat()).copy().append(" · ").append(focus)));
        }
    }

    private static Component appendStatus(Component line, Component status) {
        return line.getString().isEmpty() ? status : line.copy().append(" · ").append(status);
    }

    static int seatedCardHeight(boolean summary, int tileWidth) { return 24 + (summary ? tileWidth == 0 ? 12 : 16 : 0); }

    private boolean furiten(TableView view) {
        if (furitenView == view) return furiten;
        furitenView = view;
        furiten = false;
        if (view.viewerSeat() < 0 || view.viewerSeat() >= view.seats().size()
            || view.phase() != Game.Phase.TURN && view.phase() != Game.Phase.REACTION && view.phase() != Game.Phase.DRAW) return false;
        if (view.ronBlocked()) return furiten = true;
        var self = view.seats().get(view.viewerSeat());
        var concealed = new ArrayList<>(self.hand());
        if (concealed.size() % 3 == 2 && self.drawn() >= 0) concealed.remove(Integer.valueOf(self.drawn()));
        if (concealed.size() + self.melds().size() * 3 != 13 || concealed.stream().anyMatch(tile -> tile < 0)) return false;
        var waits = top.skyeyefast.mchjong.engine.HandAnalyzer.waits(concealed, self.melds());
        return furiten = self.river().stream().anyMatch(discard -> waits.contains(top.skyeyefast.mchjong.engine.Tile.kind(discard.tile())));
    }

    static int summaryTileWidth(List<top.skyeyefast.mchjong.engine.Meld> melds, int owner, int available) {
        for (int size = 7; size >= 5; size--) {
            int width = 0;
            for (var meld : melds) width += TileGui.meldWidth(meld, owner, size) + 2;
            if (width - 2 <= available) return size;
        }
        return 0;
    }

    private static void stick(GuiGraphics graphics, int x, int y, boolean riichi) {
        graphics.fill(x, y, x + 14, y + 4, MahjongUi.TEXT);
        if (riichi) graphics.fill(x + 6, y + 1, x + 8, y + 3, MahjongUi.NEGATIVE);
        else for (int dot = 3; dot <= 9; dot += 3) graphics.fill(x + dot, y + 1, x + dot + 1, y + 3, MahjongUi.INPUT);
    }

    private static void text(Font font, GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        MahjongUi.text(graphics, font, text, x, y, width, color, false);
    }
}
