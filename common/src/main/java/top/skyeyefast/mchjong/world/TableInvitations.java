package top.skyeyefast.mchjong.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.engine.Game;

/** Recipient-bound requests; only world policy can permit safe, explicit invitation teleportation. */
public final class TableInvitations {
    private static final Map<MinecraftServer, TableInvitations> SERVERS = new WeakHashMap<>();
    private static final long LIFETIME = 60 * 20;
    private static final long COOLDOWN = 5 * 20;
    private final Map<UUID, Invitation> pending = new HashMap<>();
    private final Map<UUID, Long> lastSent = new HashMap<>();

    record Invitation(UUID sender, UUID recipient, UUID tableId, ResourceKey<Level> dimension,
                      BlockPos pos, long expiresAt, boolean teleportOffered) {
        boolean validFor(UUID player, long now) { return recipient.equals(player) && now < expiresAt; }
    }

    private TableInvitations() {}

    private static TableInvitations of(MinecraftServer server) {
        TableInvitations invitations = SERVERS.computeIfAbsent(server, ignored -> new TableInvitations());
        long now = server.overworld().getGameTime();
        invitations.pending.values().removeIf(invitation -> now >= invitation.expiresAt());
        invitations.lastSent.values().removeIf(sent -> now - sent >= COOLDOWN);
        return invitations;
    }

    public static int invite(ServerPlayer sender, ServerPlayer recipient) throws CommandSyntaxException {
        MahjongTableBlockEntity table = TableCommands.table(sender);
        Game game = table.participantGame(sender);
        if (game.phase() != Game.Phase.LOBBY || recipient == sender || recipient.isSpectator()
            || game.seatOf(recipient.getUUID()) >= 0 || game.view(null).seats().stream().allMatch(seat -> seat.occupied()))
            throw TableCommands.error("message.mchjong.invite_unavailable");
        var server = sender.level().getServer();
        var inbox = of(server);
        if (inbox.lastSent.containsKey(sender.getUUID()) || inbox.pending.size() >= 1024)
            throw TableCommands.error("message.mchjong.invite_cooldown");
        long now = server.overworld().getGameTime();
        inbox.lastSent.put(sender.getUUID(), now);
        inbox.pending.values().removeIf(invitation -> invitation.sender().equals(sender.getUUID())
            && invitation.recipient().equals(recipient.getUUID()));
        UUID token = UUID.randomUUID();
        BlockPos pos = table.getBlockPos();
        inbox.pending.put(token, new Invitation(sender.getUUID(), recipient.getUUID(), game.tableId(),
            sender.level().dimension(), pos, now + LIFETIME, WorldSettings.of(server).policy().invitationTeleport()));
        Component accept = Component.translatable("ui.mchjong.invite_accept").withStyle(style -> style
            .withColor(ChatFormatting.GREEN).withUnderlined(true)
            .withClickEvent(new ClickEvent.RunCommand("/mchjong accept " + token)));
        Component decline = Component.translatable("ui.mchjong.invite_decline").withStyle(style -> style
            .withColor(ChatFormatting.GRAY).withUnderlined(true)
            .withClickEvent(new ClickEvent.RunCommand("/mchjong decline " + token)));
        recipient.sendSystemMessage(Component.translatable("message.mchjong.invitation", sender.getDisplayName(),
            pos.getX(), pos.getY(), pos.getZ(), sender.level().dimension().identifier().toString())
            .append(" ").append(Component.translatable(WorldSettings.of(server).policy().invitationTeleport()
                ? "message.mchjong.invite_teleport_enabled" : "message.mchjong.invite_teleport_disabled"))
            .append(" ").append(accept).append("  ").append(decline));
        sender.sendSystemMessage(Component.translatable("message.mchjong.invite_sent", recipient.getDisplayName()));
        return 1;
    }

    public static int respond(ServerPlayer recipient, UUID token, boolean accept) throws CommandSyntaxException {
        var server = recipient.level().getServer();
        var inbox = of(server);
        Invitation invitation = inbox.pending.get(token);
        if (invitation == null || !invitation.validFor(recipient.getUUID(), server.overworld().getGameTime()))
            throw TableCommands.error("message.mchjong.invite_expired");
        ServerPlayer sender = server.getPlayerList().getPlayer(invitation.sender());
        if (!accept) {
            inbox.pending.remove(token);
            if (sender != null) sender.sendSystemMessage(Component.translatable("message.mchjong.invite_declined", recipient.getDisplayName()));
            recipient.sendSystemMessage(Component.translatable("message.mchjong.invite_dismissed"));
            return 1;
        }
        ServerLevel level = server.getLevel(invitation.dimension());
        BlockPos pos = invitation.pos();
        if (level == null || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || !(level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table) || sender == null
            || table.participantGame(sender) == null || !table.participantGame(sender).tableId().equals(invitation.tableId())
            || table.participantGame(sender).phase() != Game.Phase.LOBBY) {
            inbox.pending.remove(token);
            throw TableCommands.error("message.mchjong.invite_expired");
        }
        if (!recipient.isAlive() || recipient.isSpectator() || recipient.isPassenger())
            throw TableCommands.error("message.mchjong.invite_unavailable");
        var view = table.participantGame(sender).view(null);
        boolean remote = recipient.level() != level || recipient.distanceToSqr(pos.getCenter()) > 36;
        if (remote && (!invitation.teleportOffered() || !WorldSettings.of(server).policy().invitationTeleport()))
            throw TableCommands.error("message.mchjong.invite_approach");
        int nearest = TableGeometry.nearestSide(recipient.position().subtract(pos.getCenter()));
        for (int offset = 0; offset < 4; offset++) {
            int seat = (nearest + offset) % 4;
            if (seat >= view.seats().size() || view.seats().get(seat).occupied()
                || !level.getBlockState(TableGeometry.stool(pos, seat)).is(MahjongContent.STOOL)) continue;
            if (remote) {
                var safe = safeArrival(level, pos, seat, recipient);
                if (safe == null) continue;
                // Acceptance grants travel only. Joining still requires actually sitting down.
                recipient.teleportTo(level, safe.x, safe.y, safe.z, java.util.Set.of(), TableGeometry.yaw(seat), 0, false);
                recipient.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                recipient.fallDistance = 0;
                inbox.pending.remove(token);
                recipient.sendSystemMessage(Component.translatable("message.mchjong.invite_arrived"));
                return 1;
            }
            table.sit(recipient, seat);
            if (table.participantGame(recipient) != null) {
                inbox.pending.remove(token);
                sender.sendSystemMessage(Component.translatable("message.mchjong.invite_joined", recipient.getDisplayName()));
                return 1;
            }
        }
        throw TableCommands.error("message.mchjong.invite_unavailable");
    }

    private static net.minecraft.world.phys.Vec3 safeArrival(ServerLevel level, BlockPos table, int seat, ServerPlayer player) {
        BlockPos outward = table.relative(TableGeometry.SIDES[seat], TableGeometry.STOOL_DISTANCE + 1);
        for (BlockPos candidate : new BlockPos[]{outward, outward.above(),
                outward.relative(TableGeometry.SIDES[seat].getClockWise()), outward.relative(TableGeometry.SIDES[seat].getCounterClockWise())}) {
            if (!loadedAround(level, candidate)
                || !level.getWorldBorder().isWithinBounds(candidate)) continue;
            var safe = net.minecraft.world.entity.vehicle.DismountHelper.findSafeDismountLocation(player.getType(), level, candidate, true);
            if (safe != null && level.getWorldBorder().isWithinBounds(player.getBoundingBox().move(safe.subtract(player.position())))
                && level.noCollision(player, player.getBoundingBox().move(safe.subtract(player.position())))
                && !level.containsAnyLiquid(player.getBoundingBox().move(safe.subtract(player.position())))) return safe;
        }
        return null;
    }

    private static boolean loadedAround(ServerLevel level, BlockPos pos) {
        for (int x = (pos.getX() - 1) >> 4; x <= (pos.getX() + 1) >> 4; x++)
            for (int z = (pos.getZ() - 1) >> 4; z <= (pos.getZ() + 1) >> 4; z++)
                if (!level.getChunkSource().hasChunk(x, z)) return false;
        return true;
    }
}
