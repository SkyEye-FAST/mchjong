package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.RoomSeating;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.network.TableViewPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

public final class ClientTableNetworking {
    private ClientTableNetworking() {}

    public static void receive(TableViewPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)) return;
        TableView view = TableNetworking.JSON.fromJson(payload.view(), TableView.class);
        if (view == null || view.rules() == null || view.seats().size() != view.rules().players()
            || view.viewerSeat() < -1 || view.viewerSeat() >= view.rules().players()) return;
        TableView previous = table.clientView();
        var previousRoom = table.clientRoom();
        table.acceptView(view);
        if (table.clientView() != view) return;
        table.acceptRedOptions(payload.redOptions());
        table.acceptRoom(payload.room());
        boolean assigned = payload.room().seating() == RoomSeating.Stage.POSITIONING
            && (previous == null || !previous.tableId().equals(view.tableId()) || previousRoom == null
                || previousRoom.seating() != RoomSeating.Stage.POSITIONING);
        requestAssignedSeat(client, payload, view, assigned);
        TableAudio.accept(table, view);
        TableAnimation.of(table).accept(view, Util.getMillis());
        TableScreen active = TableScreen.active(client.screen);
        if (previous != null && previous.viewerSeat() >= 0 && view.viewerSeat() < 0
            && view.phase() == top.skyeyefast.mchjong.engine.Game.Phase.LOBBY
            && active != null && active.tablePos().equals(payload.pos())) {
            client.setScreen(null);
            return;
        }
        if (active != null && active.tablePos().equals(payload.pos())) {
            active.receivedView();
            if (payload.controlReply()) active.receivedControlReply();
        }
        if (payload.open()) {
            TableScreen screen = new TableScreen(payload.pos());
            client.setScreen(screen);
            screen.resetView();
        }
    }

    private static void requestAssignedSeat(Minecraft client, TableViewPayload payload, TableView view, boolean assigned) {
        if (!TableSettings.get().autoSeat || view.viewerSeat() < 0 || client.player == null || client.getConnection() == null) return;
        if (!assigned && !payload.open()) return;
        if (client.player.getVehicle() instanceof SeatEntity seat
            && seat.tablePos().equals(payload.pos()) && seat.seat() == view.viewerSeat()) return;
        client.getConnection().send(PayloadPackets.serverbound(new TableSeatPayload(payload.pos(), view.tableId())));
    }
}
