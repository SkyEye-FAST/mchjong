package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.GameType;
import top.skyeyefast.mchjong.engine.PlayerHandVisibility;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;

/** The room's frequent controls share one page, including at 320 by 240. */
final class TableLobby {
    private TableLobby() {}
    static int top(int height) { return height < 300 ? 82 : 94; }
    static int primaryY(int height) { return top(height) + 124; }

    static List<MahjongButton> controls(TableScreen parent, TableView view, int width, int height) {
        var client = Minecraft.getInstance();
        var room = parent.room();
        boolean member = view.viewerSeat() >= 0;
        boolean host = member && room != null && view.viewerSeat() == room.host() && view.exitVote() == null;
        var buttons = new ArrayList<MahjongButton>();
        int span = Math.min(440, width - 20), left = (width - span) / 2, y = top(height);
        int third = (span - 8) / 3, half = (span - 4) / 2;
        for (var type : GameType.values()) {
            var choice = button(Component.translatable("mcr.mchjong.game_type." + type.name().toLowerCase(java.util.Locale.ROOT)),
                left + type.ordinal() * (half + 4), y, half, () -> parent.chooseGameType(view, type));
            choice.selected(parent.gameType() == type);
            choice.active = host && parent.automatic() && parent.gameType() != type
                && (type != GameType.MCR || view.rules().players() == 4
                    && view.seats().stream().noneMatch(TableView.Seat::bot));
            buttons.add(choice);
        }
        y += 24;
        if (parent.gameType() == GameType.MCR) {
            var stock = button(Component.translatable("mcr.mchjong.stock_hint"), left, y, span, () -> {});
            stock.active = false;
            buttons.add(stock);
            buttons.add(button(Component.translatable("room.mchjong.participants"), left, y + 24, span,
                () -> client.setScreen(new TableSeatsScreen(parent))));
            int index = TableSeatsScreen.find(view, Action.Type.BEGIN_SEATING, List.of());
            var primary = button(Component.translatable(index >= 0 ? "room.mchjong.start_auto"
                : view.seats().stream().allMatch(TableView.Seat::occupied) ? "ui.mchjong.equipment_needed" : "room.mchjong.wait_host"),
                left, primaryY(height), span, () -> parent.send(view, index)).primary();
            primary.setHeight(26);
            primary.active = index >= 0;
            buttons.add(primary);
            return buttons;
        }
        for (int count : new int[]{4, 3}) {
            var preset = view.rules().preset().tenhou() ? count == 4 ? RuleSet.TENHOU_4 : RuleSet.TENHOU_3
                : count == 4 ? RuleSet.MAHJONG_SOUL_4 : RuleSet.MAHJONG_SOUL_3;
            int index = TableScreen.ruleAction(view, preset);
            var config = view.rules().withPreset(preset);
            boolean supplied = parent.canSupplyReds(config.sanma(), config.redFives());
            var button = button(Component.translatable("ui.mchjong.players." + count),
                left + (4 - count) * (third + 4), y, third, () -> parent.send(view, index));
            button.active = host && index >= 0 && count != view.rules().players() && supplied;
            button.selected(count == view.rules().players());
            if (!supplied) button.setTooltip(Tooltip.create(Component.translatable("rules.mchjong.insufficient_reds")));
            buttons.add(button);
        }
        var clock = button(Component.translatable("ui.mchjong.clock_settings"), left + 2 * (third + 4), y,
            span - 2 * (third + 4), () -> client.setScreen(new TableClockScreen(parent, view.timeControl())));
        clock.active = host;
        buttons.add(clock);
        var preset = button(Component.translatable("rules.mchjong.preset", Component.translatable(view.rules().preset().presetKey())).append(" ▼"),
            left, y + 24, half, () -> client.setScreen(new TableRulesScreen(parent, view, true)));
        preset.setTooltip(Tooltip.create(Component.translatable("rules.mchjong.preset_help")));
        buttons.add(preset);
        buttons.add(button(Component.translatable("rules.mchjong.title"), left + half + 4, y + 24, half,
            () -> client.setScreen(new TableRulesScreen(parent, view))).selected(view.rules().custom()));
        var visibility = button(Component.translatable("settings.mchjong.hand_visibility", Component.translatable(
            "settings.mchjong.hand_visibility." + view.playerHandVisibility().name().toLowerCase(java.util.Locale.ROOT))), left, y + 48, span,
            () -> parent.configureVisibility(PlayerHandVisibility.values()[Math.floorMod(view.playerHandVisibility().ordinal()
                + (Screen.hasShiftDown() ? -1 : 1), PlayerHandVisibility.values().length)]));
        visibility.active = host;
        buttons.add(visibility);
        var invite = button(Component.translatable("ui.mchjong.invite"), left, y + 72, half,
            () -> client.setScreen(new TableInviteScreen(parent)));
        var world = parent.worldPolicy();
        invite.active = member && (world == null || world.invitationsEnabled());
        buttons.add(invite);
        buttons.add(button(Component.translatable("room.mchjong.participants"), left + half + 4, y + 72, half,
            () -> client.setScreen(new TableSeatsScreen(parent))));
        int start = TableSeatsScreen.find(view, Action.Type.BEGIN_SEATING, List.of());
        if (start < 0) start = TableSeatsScreen.find(view, Action.Type.FILL_BOTS, List.of());
        int index = start;
        String label;
        if (index >= 0) label = view.actions().get(index).type() == Action.Type.FILL_BOTS
            ? "room.mchjong.start_bots" : parent.automatic() ? "room.mchjong.start_auto" : "room.mchjong.start_manual";
        else if (host && view.seats().stream().allMatch(TableView.Seat::occupied))
            label = parent.automatic() ? "ui.mchjong.equipment_needed" : "ui.mchjong.manual_equipment_needed";
        else label = "room.mchjong.wait_host";
        var primary = button(Component.translatable(label), left, primaryY(height), span, () -> parent.send(view, index)).primary();
        primary.setHeight(26);
        primary.active = index >= 0;
        buttons.add(primary);
        var service = parent.botService();
        if (service != null && service.discoveryError() != null && !"discovering".equals(service.discoveryError())) {
            var status = button(Component.translatable("bot.mchjong.service_status",
                Component.translatable("bot.mchjong.service." + service.discoveryError())),
                left, primaryY(height) + 30, span, () -> {});
            status.active = false;
            buttons.add(status);
        }
        return buttons;
    }

    private static MahjongButton button(Component text, int x, int y, int width, Runnable action) {
        return MahjongButton.create(text, ignored -> action.run()).bounds(x, y, width, 20).tooltip(Tooltip.create(text)).build();
    }
}
