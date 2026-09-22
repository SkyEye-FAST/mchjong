package top.skyeyefast.mchjong.smoke;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableInvitations;
import top.skyeyefast.mchjong.world.WorldSettings;

/** One recipient on a non-networked connection exercises the real invitation and teleport handlers. */
final class InvitationSmoke {
    private InvitationSmoke() {}

    static void verify(ServerPlayer sender, MahjongTableBlockEntity table) throws java.io.IOException, CommandSyntaxException {
        var worldCommand = sender.server.getCommands().getDispatcher().getRoot().getChild("mchjong").getChild("world");
        check(!worldCommand.canUse(sender.createCommandSourceStack().withPermission(1)), "Non-administrator can access world settings");
        check(worldCommand.canUse(sender.createCommandSourceStack().withPermission(2)), "Administrator cannot access world settings");
        var policy = WorldSettings.of(sender.server);
        var original = policy.policy();
        var recipient = new Recipient(sender);
        var dispatcher = sender.server.getCommands().getDispatcher();
        for (String target : new String[]{sender.getUUID().toString(), sender.getGameProfile().getName(), UUID.randomUUID().toString()}) {
            var parsed = dispatcher.parse("mchjong invite " + target, sender.createCommandSourceStack());
            check(parsed.getExceptions().isEmpty() && !parsed.getReader().canRead(), "Invitation target failed command parsing: " + target);
            try {
                dispatcher.execute(parsed);
                throw new IllegalStateException("Self/offline invitation was accepted");
            } catch (CommandSyntaxException expected) {
                check(expected.getRawMessage().getString().equals(Component.translatable("message.mchjong.invite_unavailable").getString()),
                    "Invitation failed in selector parsing instead of player validation: " + expected.getMessage());
            }
        }
        var game = table.participantGame(sender);
        try {
            recipient.setPos(sender.getX() + 32, sender.getY(), sender.getZ());
            policy.set("invitationTeleport", true);
            policy.reload();
            check(policy.policy().invitationTeleport(), "Reload did not preserve the saved world policy");
            TableInvitations.invite(sender, recipient);
            check(recipient.token != null, "Invitation did not contain a recipient-bound acceptance link");
            var before = recipient.position();
            policy.set("invitationTeleport", false);
            try {
                TableInvitations.respond(recipient, recipient.token, true);
                throw new IllegalStateException("Disabled invitation teleportation still transported the recipient");
            } catch (CommandSyntaxException expected) { /* Administrators can revoke travel after sending. */ }
            check(recipient.position().equals(before), "Denied invitation moved the recipient");
            policy.set("invitationTeleport", true);
            check(TableInvitations.respond(recipient, recipient.token, true) == 1, "Invitation was not accepted");
            check(recipient.distanceToSqr(table.getBlockPos().getCenter()) < 36 && !recipient.position().equals(before),
                "Recipient did not arrive beside the table");
            check(!recipient.isPassenger() && game.seatOf(recipient.getUUID()) < 0, "Travel joined the room without sitting");
            check(recipient.fallDistance == 0 && recipient.getDeltaMovement().lengthSqr() == 0, "Arrival retained fall or movement velocity");
            try {
                TableInvitations.respond(recipient, recipient.token, true);
                throw new IllegalStateException("An accepted invitation could be replayed");
            } catch (CommandSyntaxException expected) { /* Consumed tokens cannot travel twice. */ }
            int seat = java.util.stream.IntStream.range(0, game.rules().players())
                .filter(index -> !game.view(null).seats().get(index).occupied()).findFirst().orElseThrow();
            table.sit(recipient, seat);
            check(game.seatOf(recipient.getUUID()) == seat && recipient.isPassenger(), "Sitting did not join the invited room");
            check(game.transferHost(sender.getUUID(), recipient.getUUID()), "Host transfer rejected an invited participant");
            check(game.transferHost(recipient.getUUID(), sender.getUUID()), "New host did not acquire ownership permissions");
        } finally {
            recipient.stopRiding();
            var view = game.view(recipient.getUUID());
            for (int index = 0; index < view.actions().size(); index++) {
                if (view.actions().get(index).type() == top.skyeyefast.mchjong.engine.Action.Type.LEAVE_ROOM) {
                    game.act(recipient.getUUID(), view.decision(), index);
                    break;
                }
            }
            recipient.discard();
            policy.set("invitationTeleport", original.invitationTeleport());
        }
    }

    private static final class Recipient extends ServerPlayer {
        UUID token;
        Recipient(ServerPlayer sender) {
            super(sender.server, sender.serverLevel(), new GameProfile(UUID.randomUUID(), "InvitedTest"));
            connection = new ServerGamePacketListenerImpl(sender.server, new Connection(PacketFlow.SERVERBOUND), this) {
                // The recipient exists only on the server; it has no client or negotiated payload channels.
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            };
        }
        @Override public void sendSystemMessage(Component message) { readLink(message); }
        private void readLink(Component message) {
            var click = message.getStyle().getClickEvent();
            if (click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND && click.getValue().startsWith("/mchjong accept "))
                token = UUID.fromString(click.getValue().substring("/mchjong accept ".length()));
            for (var child : message.getSiblings()) readLink(child);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
