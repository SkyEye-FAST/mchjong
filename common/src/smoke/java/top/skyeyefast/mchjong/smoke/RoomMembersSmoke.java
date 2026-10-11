package top.skyeyefast.mchjong.smoke;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableParticipantsScreen;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** The lobby issues room actions; active membership uses the authenticated host command. */
final class RoomMembersSmoke {
    private final boolean match;
    private int stage, ticks;
    private Screen original;
    private CompletableFuture<?> restore;
    RoomMembersSmoke(boolean match) { this.match = match; }
    boolean tick(Minecraft client, MahjongTableBlockEntity table) {
        if (++ticks > 200) throw new IllegalStateException("Member transfer timed out: " + match + "/" + stage);
        var room = table.clientTableRoom();
        if (stage == 0) {
            original = client.screen;
            require(room.viewerSeat() == room.host(), "Member fixture has no host");
            if (match) {
                press(client, original.width < 560 ? "settings.mchjong.scopes.short" : "settings.mchjong.scopes");
                press(client, "room.mchjong.participants");
                require(client.screen instanceof TableParticipantsScreen, "Active members are unreachable");
                require(room.actions().stream().noneMatch(a -> a.type() == RoomAction.Type.TRANSFER_HOST), "Active transfer unexpectedly uses lobby actions");
            } else press(client, "room.mchjong.participants");
            AutomationControlsSmoke.checkBounds(client);
            press(client, "room.mchjong.transfer"); stage = 1;
        } else if (stage == 1 && room.host() != room.viewerSeat()) {
            var successor = room.seats().get(room.host()).participant().id();
            var actor = client.player.getUUID(); var pos = table.getBlockPos();
            restore = client.getSingleplayerServer().submit(() -> {
                var server = client.getSingleplayerServer();
                var host = server.getPlayerList().getPlayers().stream().filter(p -> p.getUUID().equals(successor)).findFirst().orElseThrow();
                var player = server.getPlayerList().getPlayer(actor);
                var target = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                if (match) {
                    try {
                        server.getCommands().getDispatcher().execute("mchjong host " + player.getGameProfile().getName(), host.createCommandSourceStack());
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) { throw new IllegalStateException(failure); }
                } else {
                    var view = target.roomView(host);
                    int seat = target.participantRoom(player).seatOf(actor);
                    int action = view.actions().indexOf(new RoomAction(RoomAction.Type.TRANSFER_HOST, java.util.List.of(seat)));
                    require(action >= 0, "Restored lobby host has no transfer action");
                    top.skyeyefast.mchjong.network.TableNetworking.receive(host, new top.skyeyefast.mchjong.network.TableRoomActionPayload(pos,
                        view.tableId(), view.incarnation(), view.decision(), action));
                }
                target.open(player);
            }); stage = 2;
        } else if (stage == 2 && restore.isDone()) {
            restore.join();
            if (room.host() == room.viewerSeat()) { client.setScreen(original); stage = 3; }
        }
        return stage == 3;
    }
    private static void press(Minecraft client, String key) {
        var button = LobbySmoke.find(client, Component.translatable(key).getString());
        require(button != null && button.active, "Unavailable member action: " + key); button.onPress();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
