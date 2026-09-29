package top.skyeyefast.mchjong.world;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import top.skyeyefast.mchjong.engine.McrCodec;
import top.skyeyefast.mchjong.engine.McrSession;

/** Minecraft mount and identity adapter around one independent MCR session. */
public final class McrTableHost {
    private final McrSession session;

    private McrTableHost(McrSession session) { this.session = session; }

    public static McrTableHost start(UUID tableId, List<McrSession.Participant> roster, long seed, List<Integer> stock) {
        return new McrTableHost(McrSession.start(tableId, roster, seed, stock));
    }

    public static McrTableHost restore(String json) { return new McrTableHost(McrCodec.restoreSession(json)); }

    public String save() { return McrCodec.saveSession(session); }
    public UUID tableId() { return session.tableId(); }
    public int seatOf(UUID player) { return session.seatOf(player); }
    public long revision() { return session.view(null).revision(); }
    public McrSession.View view(UUID recipient) { return session.view(recipient); }

    /** Observe live stools for every request and snapshot; saved presence is never trusted. */
    public boolean synchronizeSeats(ServerLevel level, BlockPos pos) {
        var mounted = new HashMap<UUID, Integer>();
        for (SeatEntity mount : level.getEntitiesOfClass(SeatEntity.class, new AABB(pos).inflate(4))) {
            if (!mount.tablePos().equals(pos) || mount.isRemoved() || mount.seat() < 0 || mount.seat() > 3
                || !(mount.getFirstPassenger() instanceof ServerPlayer player)
                || !player.isAlive() || player.isSpectator() || player.serverLevel() != level
                || !level.getBlockState(TableGeometry.stool(pos, mount.seat())).is(MahjongContent.STOOL)) continue;
            mounted.put(player.getUUID(), mount.seat());
        }
        return session.synchronizeSeats(mounted);
    }

    public boolean act(ServerPlayer player, UUID tableId, UUID incarnation, long decision, int index) {
        return session.act(player.getUUID(), tableId, incarnation, decision, index);
    }

    public boolean confirm(ServerPlayer player, UUID tableId, UUID incarnation, long decision) {
        return session.confirmNextHand(player.getUUID(), tableId, incarnation, decision);
    }
}
