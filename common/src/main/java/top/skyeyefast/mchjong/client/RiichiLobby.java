package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.PlayerHandVisibility;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.RiichiRoomSettings;
import top.skyeyefast.mchjong.engine.TableRoomView;

/** The room's frequent controls share one page, including at 320 by 240. */
final class RiichiLobby {
    private RiichiLobby() {}
    static int top(int height) { return height < 300 ? 82 : 94; }
    static int primaryY(int height) { return top(height) + 124; }

    static List<MahjongButton> controls(RiichiTableScreen parent, TableRoomView room, RiichiRoomSettings settings, int width, int height) {
        var client = Minecraft.getInstance();
        boolean member = room.viewerSeat() >= 0;
        boolean host = member && room.viewerSeat() == room.host() && room.exitVote() == null;
        var buttons = new ArrayList<MahjongButton>();
        int span = Math.min(440, width - 20), left = (width - span) / 2, y = top(height);
        int third = (span - 8) / 3, half = (span - 4) / 2;
        buttons.addAll(RoomLobbyControls.variantButtons(parent.tablePos(), room, left, y, span, parent.automatic()));
        y += 24;
        for (int count : new int[]{4, 3}) {
            var preset = settings.rules().preset().tenhou() ? count == 4 ? RiichiPreset.TENHOU_4 : RiichiPreset.TENHOU_3
                : count == 4 ? RiichiPreset.MAHJONG_SOUL_4 : RiichiPreset.MAHJONG_SOUL_3;
            var config = settings.rules().withPreset(preset);
            boolean supplied = parent.canSupplyReds(config.sanma(), config.redFives());
            var button = button(Component.translatable("ui.mchjong.players." + count),
                left + (4 - count) * (third + 4), y, third, () -> parent.configureRules(room, config));
            button.active = host && count != settings.rules().players() && supplied
                && (count == 4 || room.seats().size() < 4 || room.seats().get(3).participant().id() == null);
            button.selected(count == settings.rules().players());
            if (!supplied) button.setTooltip(Tooltip.create(Component.translatable("rules.mchjong.insufficient_reds")));
            buttons.add(button);
        }
        var clock = button(Component.translatable("ui.mchjong.clock_settings"), left + 2 * (third + 4), y,
            span - 2 * (third + 4), () -> client.setScreen(new TableClockScreen(parent, settings.timeControl())));
        clock.active = host;
        buttons.add(clock);
        var preset = button(Component.translatable("rules.mchjong.preset", Component.translatable(settings.rules().preset().presetKey())).append(" ▼"),
            left, y + 24, half, () -> client.setScreen(new RiichiRulesScreen(parent, room, settings, true)));
        preset.setTooltip(Tooltip.create(Component.translatable("rules.mchjong.preset_help")));
        buttons.add(preset);
        buttons.add(button(Component.translatable("rules.mchjong.title"), left + half + 4, y + 24, half,
            () -> client.setScreen(new RiichiRulesScreen(parent, room, settings))).selected(settings.rules().custom()));
        var visibility = button(Component.translatable("settings.mchjong.hand_visibility", Component.translatable(
            "settings.mchjong.hand_visibility." + settings.playerHandVisibility().name().toLowerCase(java.util.Locale.ROOT))), left, y + 48, span,
            () -> parent.configureVisibility(PlayerHandVisibility.values()[Math.floorMod(settings.playerHandVisibility().ordinal()
                + (Screen.hasShiftDown() ? -1 : 1), PlayerHandVisibility.values().length)]));
        visibility.active = host;
        buttons.add(visibility);
        var invite = button(Component.translatable("ui.mchjong.invite"), left, y + 72, half,
            () -> client.setScreen(new RiichiInviteScreen(parent)));
        var world = parent.worldPolicy();
        invite.active = member && (world == null || world.invitationsEnabled());
        buttons.add(invite);
        buttons.add(button(Component.translatable("room.mchjong.participants"), left + half + 4, y + 72, half,
            () -> client.setScreen(new RiichiSeatsScreen(parent))));
        int start = RoomLobbyControls.find(room, RoomAction.Type.BEGIN_SEATING, List.of());
        if (start < 0) start = RoomLobbyControls.find(room, RoomAction.Type.FILL_BOTS, List.of());
        int index = start;
        String label;
        if (index >= 0) label = room.actions().get(index).type() == RoomAction.Type.FILL_BOTS
            ? "room.mchjong.start_bots" : parent.automatic() ? "room.mchjong.start_auto" : "room.mchjong.start_manual";
        else if (host && room.seats().stream().allMatch(seat -> seat.participant().id() != null))
            label = parent.automatic() ? "ui.mchjong.equipment_needed" : "ui.mchjong.manual_equipment_needed";
        else label = "room.mchjong.wait_host";
        var primary = button(Component.translatable(label), left, primaryY(height), span, () -> parent.sendRoom(room, index)).primary();
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
        return RoomLobbyControls.button(text, x, y, width, action);
    }
}
