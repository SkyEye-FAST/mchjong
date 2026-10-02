package top.skyeyefast.mchjong.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

public final class TableNetworking {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private TableNetworking() {}

    /** Both loader handlers enqueue this on the server thread. Do not force-load chunks. */
    public static void receive(ServerPlayer player, RiichiActionPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.act(player, payload);
    }

    public static void receive(ServerPlayer player, McrActionPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.mcrAction(player, payload);
    }

    public static void receive(ServerPlayer player, SichuanActionPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.sichuanAction(player, payload);
    }

    public static void receive(ServerPlayer player, SichuanNextHandPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.confirmSichuanNextHand(player, payload);
    }

    public static void receive(ServerPlayer player, SichuanRulesPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.configureSichuanRules(player, payload);
    }

    public static void receive(ServerPlayer player, TableRoomActionPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.actRoom(player, payload);
    }

    public static void receive(ServerPlayer player, McrNextHandPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.confirmMcrNextHand(player, payload);
    }

    public static void receive(ServerPlayer player, TableVariantPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.configureVariant(player, payload);
    }

    public static void receive(ServerPlayer player, MatchAutomationPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.matchAutomation(player, payload);
    }

    public static void receive(ServerPlayer player, RiichiControlPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.riichiControl(player, payload);
    }

    public static void receive(ServerPlayer player, TableSessionControlPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.sessionControl(player, payload);
    }

    public static void receive(ServerPlayer player, RiichiHandOrderPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.reorderHand(player, payload);
    }

    public static void receive(ServerPlayer player, TableSeatPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.autoSeat(player, payload);
    }

    public static void receive(ServerPlayer player, RiichiRulesPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.configureRules(player, payload);
    }

    public static void receive(ServerPlayer player, RiichiVisibilityPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.configureVisibility(player, payload);
    }

    private static boolean canReach(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        return player.isAlive() && !player.isSpectator()
            && player.level().getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 36;
    }
}
