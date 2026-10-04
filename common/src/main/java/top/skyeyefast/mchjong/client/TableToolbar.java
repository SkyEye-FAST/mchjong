package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;

/** Shared match navigation; rule-specific actions remain beside the private hand. */
final class TableToolbar {
    private TableToolbar() {}

    static List<MahjongButton> build(Screen parent, BlockPos pos, TableRoomView room, int width,
                                     boolean immersive, Runnable switchView) {
        var client = Minecraft.getInstance();
        int scale = immersive ? 2 : 1, right = width - 8 * scale, y = 8 * scale;
        var buttons = new ArrayList<MahjongButton>();
        if (room.exitVote() != null) return buttons;
        String[] keys = {"ui.mchjong.exit", "settings.mchjong.scopes", immersive ? "ui.mchjong.view_seated" : "ui.mchjong.view_immersive", "replay.mchjong.title"};
        int[] sizes = {42, 42, 62, 46};
        Runnable[] actions = {() -> TableExitControls.send(pos, room, TableSessionControlPayload.Operation.REQUEST_EXIT, room.decision(), false),
            () -> client.setScreen(new TableOptionsScreen(parent, pos)), switchView, () -> ClientReplays.list(0, "", false)};
        for (int index = 0; index < keys.length; index++) {
            if (index == 0 && room.viewerSeat() < 0 || index == 3 && room.variant() == top.skyeyefast.mchjong.engine.MahjongVariant.TAIWAN) continue;
            int current = index;
            var fullCaption = Component.translatable(keys[index]);
            boolean compact = !immersive && width < 560;
            var caption = Component.translatable(keys[index] + (compact && index != 0 ? ".short" : ""));
            int span = compact ? sizes[index] : (client.font.width(caption) + 14) * scale;
            var button = MahjongButton.create(caption, ignored -> actions[current].run())
                .bounds(right - span, y, span, 20 * scale).tooltip(Tooltip.create(fullCaption)).build().textScale(scale);
            if (index == 2) {
                button.setTooltip(Tooltip.create(Component.translatable("ui.mchjong.switch_view",
                    TableKeys.VIEW.getTranslatedKeyMessage())));
            }
            buttons.add(button);
            right -= span + 4 * scale;
        }
        return buttons;
    }
}
