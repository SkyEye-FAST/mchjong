package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.HandVisibility;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;

/** The room's frequent controls share one page, including at 320 by 240. */
final class TableLobby {
    private TableLobby() {}
    static int top(int height) { return height < 300 ? 82 : 94; }
    static int primaryY(int height) { return top(height) + 100; }

    static List<MahjongButton> controls(TableScreen parent, TableView view, int width, int height) {
        var client = Minecraft.getInstance();
        var room = parent.room();
        boolean member = view.viewerSeat() >= 0;
        boolean host = member && room != null && view.viewerSeat() == room.host() && view.exitVote() == null;
        var buttons = new ArrayList<MahjongButton>();
        int span = Math.min(440, width - 20), left = (width - span) / 2, y = top(height);
        int third = (span - 8) / 3, half = (span - 4) / 2;
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
        var presets = Arrays.stream(RuleSet.values()).filter(rule -> rule.players() == view.rules().players()).toList();
        var preset = button(Component.translatable("rules.mchjong.preset", Component.translatable(view.rules().preset().presetKey())),
            left, y + 24, half, () -> {
                int step = MahjongUi.shiftDown() ? -1 : 1;
                for (int offset = 1; offset < presets.size(); offset++) {
                    var target = presets.get(Math.floorMod(presets.indexOf(view.rules().preset()) + step * offset, presets.size()));
                    var config = view.rules().withPreset(target);
                    int action = TableScreen.ruleAction(view, target);
                    if (action >= 0 && parent.canSupplyReds(config.sanma(), config.redFives())) { parent.send(view, action); break; }
                }
            });
        preset.active = host;
        preset.setTooltip(Tooltip.create(Component.translatable("rules.mchjong.preset_help")));
        buttons.add(preset);
        buttons.add(button(Component.translatable("rules.mchjong.title"), left + half + 4, y + 24, half,
            () -> client.setScreen(new TableRulesScreen(parent, view))).selected(view.rules().custom()));
        var visibility = button(Component.translatable("settings.mchjong.hand_visibility", Component.translatable(
            "settings.mchjong.hand_visibility." + view.handVisibility().name().toLowerCase(java.util.Locale.ROOT))), left, y + 48, span,
            () -> parent.configureVisibility(HandVisibility.values()[Math.floorMod(view.handVisibility().ordinal()
                + (MahjongUi.shiftDown() ? -1 : 1), HandVisibility.values().length)]));
        visibility.active = host;
        buttons.add(visibility);
        var invite = button(Component.translatable("ui.mchjong.invite"), left, y + 72, half,
            () -> client.setScreen(new TableInviteScreen(parent)));
        invite.active = member;
        buttons.add(invite);
        buttons.add(button(Component.translatable("room.mchjong.participants"), left + half + 4, y + 72, half,
            () -> client.setScreen(new TableSeatsScreen(parent))));
        int start = TableSeatsScreen.find(view, Action.Type.BEGIN_SEATING, List.of());
        if (start < 0) start = TableSeatsScreen.find(view, Action.Type.FILL_BOTS, List.of());
        int index = start;
        String label = index < 0 ? "room.mchjong.wait_host" : view.actions().get(index).type() == Action.Type.FILL_BOTS
            ? "room.mchjong.start_bots" : parent.automatic() ? "room.mchjong.start_auto" : "room.mchjong.start_manual";
        var primary = button(Component.translatable(label), left, primaryY(height), span, () -> parent.send(view, index)).primary();
        primary.setHeight(26);
        primary.active = index >= 0;
        buttons.add(primary);
        return buttons;
    }

    private static MahjongButton button(Component text, int x, int y, int width, Runnable action) {
        return MahjongButton.create(text, ignored -> action.run()).bounds(x, y, width, 20).tooltip(Tooltip.create(text)).build();
    }
}
