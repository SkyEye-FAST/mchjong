package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableViewPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

public final class ClientTableNetworking {
    private ClientTableNetworking() {}

    public static void receive(TableViewPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)) return;
        TableView view = TableNetworking.JSON.fromJson(payload.view(), TableView.class);
        if (view == null || view.rules() == null || view.seats().size() != view.rules().players()
            || view.viewerSeat() < -1 || view.viewerSeat() >= view.rules().players()) return;
        TableView previous = table.clientView();
        table.acceptView(view);
        if (table.clientView() != view) return;
        table.acceptRedOptions(payload.redOptions());
        table.acceptRoom(payload.room());
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
}
