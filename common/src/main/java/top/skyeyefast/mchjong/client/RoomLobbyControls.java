package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.RoomSeating;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableRoomActionPayload;
import top.skyeyefast.mchjong.network.TableVariantPayload;

/** Shared room controls and variant layout. */
final class RoomLobbyControls {
    private RoomLobbyControls() {}

    static MahjongButton hintsButton(BlockPos pos, TableRoomView room, int x, int y, int width, Runnable sent) {
        var label = Component.translatable("settings.mchjong.convenience_hints");
        var value = Component.translatable(room.convenienceHints() ? "options.on" : "options.off");
        var button = button(Component.translatable("settings.mchjong.toggle", label, value), x, y, width, () -> {
            TableExitControls.send(pos, room, top.skyeyefast.mchjong.network.TableSessionControlPayload.Operation.CONVENIENCE_HINTS,
                room.decision(), !room.convenienceHints());
            sent.run();
        }).option(label, value).checked(room.convenienceHints());
        button.active = room.lobby() && room.viewerSeat() >= 0 && room.viewerSeat() == room.host()
            && room.allowConvenienceHints() && room.exitVote() == null;
        button.setTooltip(Tooltip.create(Component.translatable("settings.mchjong.convenience_hints_help")));
        return button;
    }

    static MahjongButton button(Component label, int x, int y, int width, Runnable action) {
        return MahjongButton.create(label, ignored -> action.run()).bounds(x, y, width, 20)
            .tooltip(Tooltip.create(label)).build();
    }

    static MahjongButton variantButton(BlockPos pos, TableRoomView room, MahjongVariant choice,
                                       int x, int y, int width, boolean automatic) {
        var label = Component.translatable("variant.mchjong." + choice.name().toLowerCase(java.util.Locale.ROOT));
        var button = button(label, x, y, width, () -> {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) connection.send(PayloadPackets.serverbound(new TableVariantPayload(pos,
                room.tableId(), room.decision(), choice)));
        }).selected(room.variant() == choice);
        button.active = automatic && room.viewerSeat() == room.host() && room.viewerSeat() >= 0
            && room.seating() == RoomSeating.Stage.GATHERING && room.variant() != choice
            && room.seats().stream().noneMatch(seat -> seat.participant().bot());
        return button;
    }

    static List<MahjongButton> variantButtons(BlockPos pos, TableRoomView room, int x, int y, int width, boolean automatic) {
        var variants = MahjongVariant.values();
        var buttons = new java.util.ArrayList<MahjongButton>();
        int available = width - (variants.length - 1) * 4;
        for (int index = 0; index < variants.length; index++) {
            int start = index * available / variants.length;
            int end = (index + 1) * available / variants.length;
            buttons.add(variantButton(pos, room, variants[index], x + start + index * 4, y, end - start, automatic));
        }
        return buttons;
    }

    static int find(TableRoomView room, RoomAction.Type type, List<Integer> arguments) {
        if (room == null) return -1;
        return room.actions().indexOf(new RoomAction(type, arguments));
    }

    static void send(BlockPos pos, TableRoomView room, int index) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && room != null && index >= 0 && index < room.actions().size())
            connection.send(PayloadPackets.serverbound(new TableRoomActionPayload(pos, room.tableId(),
                room.incarnation(), room.decision(), index)));
    }
}
