package top.skyeyefast.mchjong.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

public final class TableNetworking {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private TableNetworking() {}

    /** Both loader handlers enqueue this on the server thread. Do not force-load chunks. */
    public static void receive(ServerPlayer player, TableActionPayload payload) {
        if (!player.isAlive() || player.isSpectator() || !player.serverLevel().hasChunkAt(payload.pos())
            || player.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 36)
            return;
        if (player.serverLevel().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.act(player, payload);
    }
}
