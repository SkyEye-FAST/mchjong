package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.PlayerPresence;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

/** Shared player cards, presence, score, public hand summaries and focus presentation. */
final class TableHud {
    static void surface(GuiGraphics g, int x, int y, int width, int height, boolean immersive, boolean own, boolean turn) {
        g.fill(x, y, x + width, y + height, immersive ? own ? 0xc832554f : 0xb80c2024 : own ? MahjongUi.SELECTED : MahjongUi.PANEL);
        if (immersive) g.renderOutline(x, y, width, height, turn ? MahjongUi.ACCENT : own ? 0xff73938b : 0xff355257);
        if (turn) g.fill(x, y, x + 2, y + height, MahjongUi.ACCENT);
    }
    static void text(Font font, GuiGraphics g, Component text, int x, int y, int width, int color, float scale) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        MahjongUi.text(g, font, text, 0, 0, (int) (width / scale), color, false);
        g.pose().popPose();
    }
    static void render(Font font, GuiGraphics g, TableBoardState state, TableRoomView room, List<Component> statuses,
                       int width, boolean immersive, TileFacePreset preset, TileMaterial material,
                       net.minecraft.world.item.DyeColor back, net.minecraft.resources.ResourceLocation backPreset,
                       int mouseX, int mouseY, java.util.function.IntUnaryOperator artwork) {
        var settings = TableSettings.get();
        if (!immersive && (settings.show(TableSettings.Information.ROUND) || settings.show(TableSettings.Information.REMAINING))) {
            int header = Math.min(280, Math.max(84, width - 224));
            MahjongUi.panel(g, 8, 7, header, 26);
            if (settings.show(TableSettings.Information.ROUND)) MahjongUi.text(g, font, state.roundLabel(), 12, 10, header - 8, MahjongUi.TEXT, false);
            if (settings.show(TableSettings.Information.REMAINING)) MahjongUi.text(g, font,
                Component.translatable("ui.mchjong.remaining.short", state.remaining()), 12, 22, header - 8, MahjongUi.MUTED, false);
        }
        boolean visible = settings.show(TableSettings.Information.NAMES) || settings.show(TableSettings.Information.POINTS)
            || settings.show(TableSettings.Information.WINDS)
            || settings.show(TableSettings.Information.STATUS) || settings.show(TableSettings.Information.MELDS)
            || settings.show(TableSettings.Information.COUNTS) || settings.show(TableSettings.Information.RANKS);
        for (int seat = 0; visible && seat < state.players(); seat++) {
            var p = state.seats().get(seat); var member = room.seats().get(seat);
            int w = (width - 16 - (state.players() - 1) * 4) / state.players(), x = 8 + seat * (w + 4), y = 38;
            int meldWidth = settings.show(TableSettings.Information.MELDS) ? RiichiHud.summaryTileWidth(p.melds(), seat, w - 10) : 0;
            boolean summary = !p.melds().isEmpty() && settings.show(TableSettings.Information.MELDS);
            int h = RiichiHud.seatedCardHeight(summary, meldWidth);
            if (immersive) { var rect = TableCanvas.card(TableBoard.side(seat, state.viewerSeat(), state.players()));
                x = rect.left(); y = rect.top(); w = rect.width(); h = rect.height(); }
            boolean turn = seat == state.turn() && settings.show(TableSettings.Information.TURN);
            boolean disconnected = member.presence() == PlayerPresence.DISCONNECTED;
            surface(g, x, y, w, h, immersive, seat == state.viewerSeat(), turn);
            int inset = settings.show(TableSettings.Information.NAMES) ? PlayerPortrait.draw(g, member.participant(),
                x + (immersive ? 8 : 5), y + (immersive ? 8 : 2), immersive ? 32 : 10) : 0;
            Component name = settings.show(TableSettings.Information.NAMES)
                ? member.participant().bot() && !member.participant().entityBot()
                    ? Component.translatable("ui.mchjong.bot.short", seat + 1)
                    : Component.literal(member.participant().name()) : Component.empty();
            boolean badge = settings.show(p.variant() == top.skyeyefast.mchjong.engine.MahjongVariant.SICHUAN
                ? TableSettings.Information.STATUS : TableSettings.Information.WINDS);
            Component score = badge && state.indicator() != null ? state.indicator().seats().get(seat) : Component.empty();
            if (settings.show(TableSettings.Information.POINTS)) score = score.copy()
                .append(score.getString().isEmpty() ? "" : " ").append(Long.toString(p.points()));
            int color = disconnected ? MahjongUi.NEGATIVE : MahjongUi.TEXT;
            text(font, g, name, x + (immersive ? 8 : 5) + inset, y + (immersive ? 6 : 4), w - inset - 18, color, immersive ? 1.75f : 1);
            text(font, g, score, x + (immersive ? 8 + inset : 5), y + (immersive ? 27 : 14), w - (immersive ? inset + 18 : 10),
                disconnected ? MahjongUi.NEGATIVE : turn ? MahjongUi.ACCENT : MahjongUi.MUTED, immersive ? 2 : 1);
            if (!immersive && summary) {
                if (meldWidth == 0) MahjongUi.text(g, font, Component.translatable("ui.mchjong.meld_groups", p.melds().size()), x + 5, y + 25, w - 10, MahjongUi.MUTED, false);
                else { int mx = x + 5; for (var meld : p.melds()) { TileGui.meldArtwork(g, p.layout(meld, seat), mx, y + 27, meldWidth, 0, preset, material, back, backPreset, artwork); mx += TileGui.meldWidth(p.layout(meld, seat), meldWidth) + 2; } }
            }
            if (member.presence() == PlayerPresence.AWAY) g.renderOutline(x + w - 12, y + 20, 6, 6, MahjongUi.MUTED);
            if (disconnected) g.fill(x + w - 12, y + 20, x + w - 6, y + 26, MahjongUi.NEGATIVE);
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                Component hover = Component.literal(member.participant().name()).append("\n").append(Component.translatable("ui.mchjong.points", p.points()))
                    .append("\n").append(statuses.get(seat));
                if (settings.show(TableSettings.Information.RANKS)) hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.rank", 1 + state.seats().stream().filter(other -> other.points() > p.points()).count()));
                if (settings.show(TableSettings.Information.COUNTS)) hover = hover.copy().append("\n").append(Component.translatable("ui.mchjong.counts", p.hand().size(), p.river().size(), p.norths().size()));
                hover = hover.copy().append("\n").append(TableParticipantsScreen.presence(member.presence()));
                g.renderTooltip(font, hover, mouseX, mouseY);
            }
        }
        if (state.focus() != null && (settings.show(TableSettings.Information.FOCUS) || !settings.showRiver)) {
            int scale = immersive ? 2 : 1, x = immersive ? 220 : width - 100, y = immersive ? 550 : 84, span = 90 * scale;
            MahjongUi.panel(g, x, y, span, 17 * scale);
            text(font, g, Component.translatable("ui.mchjong.focus"), x + 4 * scale, y + 5 * scale, span - 22 * scale, MahjongUi.ACCENT, scale);
            TileGui.tileArtwork(g, state.focus().tile(), x + span - 15 * scale, y + scale, 9 * scale, false, false, false, false, 0, preset, material, back, backPreset, artwork);
        }
    }
}
