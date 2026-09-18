package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Edge-aligned, compact information. Detailed counts and status belong in hover text, not over the hand. */
final class TableHud {
    private static final String[] WINDS = {"east", "south", "west", "north"};
    private record Region(int x, int y, int width, int height, Component text) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    private final List<Region> regions = new ArrayList<>();
    void clear() { regions.clear(); }
    boolean contains(double x, double y) { return regions.stream().anyMatch(region -> region.contains(x, y)); }
    Component tooltip(int x, int y) {
        return regions.stream().filter(region -> region.contains(x, y)).map(Region::text).findFirst().orElse(null);
    }

    void render(Font font, GuiGraphics graphics, TableView view, int width, TileFacePreset preset) {
        clear();
        TableSettings settings = TableSettings.get();
        boolean lobby = view.phase() == Game.Phase.LOBBY;
        boolean compact = !lobby && TableCamera.overhead();
        int headerWidth = Math.max(100, width - 208);
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
        if (!title.getString().isEmpty() || !remaining.getString().isEmpty()) {
            MahjongUi.panel(graphics, 8, 7, headerWidth, lobby ? 21 : 26);
            text(font, graphics, title, 12, 10, headerWidth - 8, MahjongUi.TEXT);
            text(font, graphics, remaining, 12, 22, headerWidth - 8, MahjongUi.MUTED);
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
            Component wind = Component.translatable("wind.mchjong." + WINDS[Math.floorMod(seat - view.dealer(), view.rules().players())]);
            Component name = settings.show(TableSettings.Information.NAMES) || lobby
                ? player.bot() ? Component.translatable("ui.mchjong.bot_short", seat + 1) : TableScreen.playerName(view, seat) : Component.empty();
            Component shortLine = Component.empty();
            Component hover = TableScreen.playerName(view, seat).copy();
            if (settings.show(TableSettings.Information.WINDS)) { shortLine = wind.copy(); hover = hover.copy().append(" · ").append(wind); }
            if (settings.show(TableSettings.Information.POINTS)) {
                shortLine = shortLine.copy().append(" " + player.points());
                hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.points", player.points()));
            }
            if (settings.show(TableSettings.Information.RANKS)) {
                long rank = 1 + view.seats().stream().filter(other -> other.points() > player.points()).count();
                hover = hover.copy().append(" · ").append(Component.translatable("ui.mchjong.rank", rank));
            }
            if (lobby) shortLine = Component.translatable(player.ready() ? "ui.mchjong.ready" : "ui.mchjong.not_ready");
            if (settings.show(TableSettings.Information.STATUS)) {
                if (seat == view.dealer()) hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.dealer"));
                if (seat == view.turn() && !lobby) hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.current_turn"));
                if (player.riichi()) { shortLine = shortLine.copy().append(" *"); hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.riichi_status")); }
                if (player.exposed()) hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.exposed"));
            }
            if (settings.show(TableSettings.Information.COUNTS)) hover = hover.copy().append("\n")
                .append(Component.translatable("ui.mchjong.counts", player.hand().size(), player.river().size(), player.norths().size()));
            if (settings.show(TableSettings.Information.MELDS)) for (var meld : player.melds()) hover = hover.copy().append("\n")
                .append(Component.translatable("action.mchjong." + meld.type().name().toLowerCase(java.util.Locale.ROOT)));
            int x = 8 + seat * (cardWidth + 4);
            int cardHeight = compact ? 14 : 24;
            graphics.fill(x, top, x + cardWidth, top + cardHeight, seat == view.viewerSeat() ? MahjongUi.SELECTED : MahjongUi.PANEL);
            boolean turn = !lobby && seat == view.turn() && settings.show(TableSettings.Information.TURN);
            if (turn || lobby && player.ready()) graphics.fill(x, top, x + 2, top + cardHeight, MahjongUi.ACCENT);
            if (!compact) text(font, graphics, name, x + 5, top + 3, cardWidth - 10, MahjongUi.TEXT);
            text(font, graphics, shortLine, x + 5, top + (compact ? 3 : 14), cardWidth - 10, turn ? MahjongUi.ACCENT : MahjongUi.MUTED);
            regions.add(new Region(x, top, cardWidth, cardHeight, hover));
        }
        if (lobby) return;
        if (settings.show(TableSettings.Information.DORA)) {
            int x = 8;
            int tileWidth = compact ? 8 : 10, tileY = compact ? 54 : 67;
            for (int i = 0; i < 5; i++) {
                int index = view.wall().size() - 5 - 2 * i;
                if (index < 0 || view.wall().get(index) < 0) continue;
                TileGui.tile(graphics, view.wall().get(index), x, tileY, tileWidth, false, false, false, preset);
                x += tileWidth + 2;
            }
            if (x > 8) regions.add(new Region(8, tileY, x - 8, compact ? 12 : 16, Component.translatable("ui.mchjong.result_indicators")));
        }
        if (view.focus() != null && (settings.show(TableSettings.Information.FOCUS) || !settings.showRiver)) {
            Component focus = Component.translatable("ui.mchjong.focus");
            int span = Math.min(width / 2 - 8, font.width(focus) + 27);
            int x = width - 8 - span;
            MahjongUi.panel(graphics, x, 67, span, 17);
            text(font, graphics, focus, x + 4, 72, span - 22, MahjongUi.ACCENT);
            TileGui.tile(graphics, view.focus().tile(), width - 23, 68, 9, false, false, false, preset);
            regions.add(new Region(x, 67, span, 17, TableScreen.playerName(view, view.focus().seat()).copy().append(" · ").append(focus)));
        }
    }

    private static void text(Font font, GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        MahjongUi.text(graphics, font, text, x, y, width, color, false);
    }
}
