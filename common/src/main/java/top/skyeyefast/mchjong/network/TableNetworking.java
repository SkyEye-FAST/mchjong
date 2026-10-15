package top.skyeyefast.mchjong.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/**
 * Serverbound table requests shared by all loaders. Receivers must run on the logical server's game thread.
 * Reach checks inspect only loaded chunks; the table then validates request identity and player authority.
 */
public final class TableNetworking {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    /** Maximum UTF length of one serialized game view in a clientbound payload. */
    public static final int VIEW_LIMIT = 524288;
    private TableNetworking() {}

    public static void receive(ServerPlayer player, RiichiActionPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.act(player, payload);
    }

    public static void receive(ServerPlayer player, TableActionPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.tableAction(player, payload);
    }

    public static void receive(ServerPlayer player, NextHandPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.confirmNextHand(player, payload);
    }

    public static void receive(ServerPlayer player, SichuanRulesPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.configureSichuanRules(player, payload);
    }

    public static void receive(ServerPlayer player, TaiwanRulesPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.configureTaiwanRules(player, payload);
    }

    public static void receive(ServerPlayer player, TaiwanClockPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.configureTaiwanClock(player, payload);
    }

    public static void receive(ServerPlayer player, TableRoomActionPayload payload) {
        if (!canReach(player, payload.pos())) return;
        if (player.level().getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)
            table.actRoom(player, payload);
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
