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
        if (!canReach(player, payload.pos())) return;
        if (player.serverLevel().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.act(player, payload);
    }

    public static void receive(ServerPlayer player, TableControlPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.serverLevel().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.control(player, payload);
    }

    private static boolean canReach(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        return player.isAlive() && !player.isSpectator()
            && player.serverLevel().getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 36;
    }
}
