package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
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
        if (payload.open()) {
            if (view.viewerSeat() >= 0 && client.player != null) {
                client.player.setYRot(top.skyeyefast.mchjong.world.TableGeometry.yaw(view.viewerSeat()));
                client.player.setXRot(30);
                client.player.yRotO = client.player.getYRot();
                client.player.xRotO = client.player.getXRot();
            }
            client.setScreen(new TableScreen(payload.pos()));
        }
    }
}
