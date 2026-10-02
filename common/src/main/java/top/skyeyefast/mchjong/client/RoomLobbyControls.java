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

    static MahjongButton button(Component label, int x, int y, int width, Runnable action) {
        return MahjongButton.create(label, ignored -> action.run()).bounds(x, y, width, 20)
            .tooltip(Tooltip.create(label)).build();
    }

    static MahjongButton variantButton(BlockPos pos, TableRoomView room, MahjongVariant choice,
                                       int x, int y, int width, boolean automatic, Runnable sent) {
        var label = Component.translatable("variant.mchjong." + choice.name().toLowerCase(java.util.Locale.ROOT));
        var button = button(label, x, y, width, () -> {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null && room.variant() != choice) {
                connection.send(PayloadPackets.serverbound(new TableVariantPayload(pos, room.tableId(), room.decision(), choice)));
                sent.run();
            }
        }).selected(room.variant() == choice);
        button.active = room.variant() == choice || automatic && room.viewerSeat() == room.host() && room.viewerSeat() >= 0
            && room.seating() == RoomSeating.Stage.GATHERING
            && room.seats().stream().noneMatch(seat -> seat.participant().bot());
        if (!button.active) button.setTooltip(Tooltip.create(label.copy().append("\n").append(Component.translatable("lobby.mchjong.variant_locked"))));
        return button;
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
