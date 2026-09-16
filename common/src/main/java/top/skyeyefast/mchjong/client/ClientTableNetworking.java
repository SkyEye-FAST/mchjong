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
        table.acceptView(view);
        if (table.clientView() != view) return;
        if (table.clientView() != view) return;
        TableAnimation.of(table).accept(view, Util.getMillis());
        TableScreen active = TableScreen.active(client.screen);
        if (active != null && active.tablePos().equals(payload.pos())) active.receivedView();
        if (payload.open()) {
            TableScreen screen = new TableScreen(payload.pos());
            client.setScreen(screen);
            screen.resetView();
        }
    }
}
