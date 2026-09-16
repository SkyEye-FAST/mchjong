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

/** Short-lived, recipient-bound requests. Acceptance never teleports or force-loads a chunk. */
public final class TableInvitations {
    private static final Map<MinecraftServer, TableInvitations> SERVERS = new WeakHashMap<>();
    private static final long LIFETIME = 60 * 20;
    private static final long COOLDOWN = 5 * 20;
    private final Map<UUID, Invitation> pending = new HashMap<>();
    private final Map<UUID, Long> lastSent = new HashMap<>();

    record Invitation(UUID sender, UUID recipient, UUID tableId, ResourceKey<Level> dimension,
                      BlockPos pos, long expiresAt) {
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
        var server = sender.server;
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
            sender.serverLevel().dimension(), pos, now + LIFETIME));
        Component accept = Component.translatable("ui.mchjong.invite_accept").withStyle(style -> style
            .withColor(ChatFormatting.GREEN).withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mchjong accept " + token)));
        Component decline = Component.translatable("ui.mchjong.invite_decline").withStyle(style -> style
            .withColor(ChatFormatting.GRAY).withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mchjong decline " + token)));
        recipient.sendSystemMessage(Component.translatable("message.mchjong.invitation", sender.getDisplayName(),
            pos.getX(), pos.getY(), pos.getZ(), sender.serverLevel().dimension().location().toString())
            .append(" ").append(accept).append(" · ").append(decline));
        sender.sendSystemMessage(Component.translatable("message.mchjong.invite_sent", recipient.getDisplayName()));
        return 1;
    }

    public static int respond(ServerPlayer recipient, UUID token, boolean accept) throws CommandSyntaxException {
        var server = recipient.server;
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
        if (recipient.serverLevel() != level || recipient.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 36)
            throw TableCommands.error("message.mchjong.invite_approach");
        var view = table.participantGame(sender).view(null);
        int nearest = TableGeometry.nearestSide(recipient.position().subtract(pos.getCenter()));
        for (int offset = 0; offset < 4; offset++) {
            int seat = (nearest + offset) % 4;
            if (seat >= view.seats().size() || view.seats().get(seat).occupied()
                || !level.getBlockState(TableGeometry.stool(pos, seat)).is(MahjongContent.STOOL)) continue;
            table.sit(recipient, seat);
            if (table.participantGame(recipient) != null) {
                inbox.pending.remove(token);
                sender.sendSystemMessage(Component.translatable("message.mchjong.invite_joined", recipient.getDisplayName()));
                return 1;
            }
        }
        throw TableCommands.error("message.mchjong.invite_unavailable");
    }
}
