package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.RoomSeating;
import top.skyeyefast.mchjong.engine.TableSession;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.network.RiichiViewPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

public final class ClientRiichiNetworking {
    private ClientRiichiNetworking() {}

    public static void receive(RiichiViewPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)) return;
        var room = payload.room();
        RiichiView view = payload.view().isEmpty() ? null : TableNetworking.JSON.fromJson(payload.view(), RiichiView.class);
        if (room == null || payload.settings() == null
            || (room.lifecycle() == TableSession.Lifecycle.LOBBY) != (view == null)
            || view != null && (view.rules() == null || view.seats().size() != view.rules().players()
                || view.viewerSeat() < -1 || view.viewerSeat() >= view.rules().players())) return;
        var previousRoom = table.clientRoom();
        if (previousRoom != null && previousRoom.tableId().equals(room.tableId())
            && previousRoom.incarnation().equals(room.incarnation()) && room.revision() < previousRoom.revision()) return;
        table.acceptView(view);
        if (view != null && table.clientView() != view) return;
        table.acceptVariant(payload.variant());
        table.acceptRedOptions(payload.redOptions());
        table.acceptRoom(room);
        table.acceptRiichiSettings(payload.settings());
        table.acceptBotService(payload.botService());
        table.acceptWorldPolicy(payload.world());
        boolean assigned = room.seating() == RoomSeating.Stage.POSITIONING
            && (previousRoom == null || !previousRoom.tableId().equals(room.tableId())
                || previousRoom.seating() != RoomSeating.Stage.POSITIONING);
        requestAssignedSeat(client, payload, room, assigned);
        RiichiAudio.accept(table, view);
        if (view == null) RiichiAnimation.of(table).acceptLobby(room.tableId(), room.viewerSeat(), Util.getMillis());
        else RiichiAnimation.of(table).accept(view, Util.getMillis());
        if (payload.leaveDecision()) {
            if (!(client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), room.tableId())))
                client.setScreen(new TableLeaveScreen(payload.pos(), room.tableId(), room.decision()));
            return;
        }
        if (client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), room.tableId()))
            client.setScreen(null);
        RiichiTableScreen active = RiichiTableScreen.active(client.screen);
        if (previousRoom != null && previousRoom.viewerSeat() >= 0 && room.viewerSeat() < 0
            && room.lobby()
            && active != null && active.tablePos().equals(payload.pos())) {
            client.setScreen(null);
            return;
        }
        if (active != null && active.tablePos().equals(payload.pos())) {
            active.receivedView();
            if (payload.controlReply()) active.receivedControlReply();
        }
        if (payload.open()) {
            if (active != null && active.tablePos().equals(payload.pos())) {
                if (client.screen != active) client.setScreen(active);
            } else {
                RiichiTableScreen screen = new RiichiTableScreen(payload.pos());
                client.setScreen(screen);
                screen.resetView();
            }
        } else if (client.screen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
            || client.screen instanceof SichuanScreen screen && screen.tablePos().equals(payload.pos())) {
            client.setScreen(new RiichiTableScreen(payload.pos()));
        }
    }

    private static void requestAssignedSeat(Minecraft client, RiichiViewPayload payload,
                                             top.skyeyefast.mchjong.engine.TableRoomView room, boolean assigned) {
        if (!TableSettings.get().autoSeat || room.viewerSeat() < 0 || client.player == null || client.getConnection() == null) return;
        if (!assigned && !payload.open()) return;
        if (client.player.getVehicle() instanceof SeatEntity seat
            && seat.tablePos().equals(payload.pos()) && seat.seat() == room.viewerSeat()) return;
        client.getConnection().send(PayloadPackets.serverbound(new TableSeatPayload(payload.pos(), room.tableId())));
    }
}
