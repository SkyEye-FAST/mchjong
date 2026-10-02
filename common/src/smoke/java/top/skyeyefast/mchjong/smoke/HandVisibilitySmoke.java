package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableOptionsScreen;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.PlayerHandVisibility;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Real room proposals, participant permissions and hidden spectator packets on both loaders. */
final class HandVisibilitySmoke {
    private int mode, stage, ticks;
    private RoomPreparationSmoke preparation = new RoomPreparationSmoke();
    private CompletableFuture<?> work;
    private int seat;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        if (mode == PlayerHandVisibility.values().length) return true;
        ticks++;
        if (work != null) {
            if (!work.isDone() || client.getOverlay() != null) return false;
            work.join(); work = null;
        }
        var view = table.clientView();
        var room = table.clientRoom();
        var configuration = table.clientRiichiSettings();
        if (room == null || configuration == null || view == null && stage >= 5) return false;
        var visibility = PlayerHandVisibility.values()[mode];
        if (stage == 0) {
            if (room.viewerSeat() < 0 || !room.lobby()) return false;
            var parent = new RiichiTableScreen(table.getBlockPos());
            client.setScreen(parent);
            client.setScreen(new TableOptionsScreen(parent, parent.tablePos()));
            var label = Component.translatable("settings.mchjong.hand_visibility", Component.translatable(
                "settings.mchjong.hand_visibility." + configuration.playerHandVisibility().name().toLowerCase(java.util.Locale.ROOT))).getString();
            var button = LobbySmoke.find(client, label);
            require(button.active, "Host visibility control is disabled");
            if (configuration.playerHandVisibility() != visibility) button.onPress();
            next(1);
        } else if (stage == 1 && configuration.playerHandVisibility() == visibility && ticks > 5) {
            next(2);
        } else if (stage == 2) {
            resize(client, true);
            var parent = new RiichiTableScreen(table.getBlockPos());
            client.setScreen(parent);
            client.setScreen(new TableOptionsScreen(parent, parent.tablePos()));
            next(3);
        } else if (stage == 3 && ticks > 10) {
            AutomationControlsSmoke.checkBounds(client);
            var label = Component.translatable("settings.mchjong.hand_visibility", Component.translatable(
                "settings.mchjong.hand_visibility." + visibility.name().toLowerCase(java.util.Locale.ROOT))).getString();
            require(LobbySmoke.find(client, label).active, "Missing room visibility control");
            capture(client, output, "room-" + visibility + "-small.png");
            resize(client, false);
            client.setScreen(new RiichiTableScreen(table.getBlockPos()));
            next(4);
        } else if (stage == 4) {
            if (preparation.tick(client, table, output, "visibility-" + visibility)) next(5);
        } else if (stage == 5 && view.phase() == RiichiView.Phase.TURN && ticks > 25
            && !top.skyeyefast.mchjong.client.RiichiAnimation.of(table).dealing(net.minecraft.Util.getMillis())) {
            seat = view.viewerSeat();
            require(seat >= 0, "Missing seated snapshot");
            capture(client, output, "seated-" + visibility + ".png");
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
            next(9);
        } else if (stage == 9 && ticks > 10) {
            require(client.screen instanceof RiichiTableScreen screen && screen.immersive(), "Missing immersive view");
            capture(client, output, "immersive-" + visibility + ".png");
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            work = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                player.stopRiding();
                player.teleportTo(player.serverLevel(), pos.getX() + .5, pos.getY() + .3, pos.getZ() + 2.6, 180, 35);
                require(serverTable.participantSession(player) == null, "Unmounted observer retained action authority");
                serverTable.open(player);
            });
            next(6);
        } else if (stage == 6 && view.viewerSeat() < 0 && !client.player.isPassenger() && ticks > 15) {
            for (var player : view.seats()) if (!player.exposed()) {
                require(!player.hand().isEmpty(), "Observer received no concealed hand geometry");
                require(player.hand().stream().allMatch(tile -> tile == Tile.HIDDEN),
                    "Observer received wrong hand permissions: " + visibility);
            }
            require(view.actions().isEmpty(), "Observer received game actions");
            client.setScreen(null);
            next(7);
        } else if (stage == 7 && ticks > 10) {
            capture(client, output, "observer-" + visibility + ".png");
            resize(client, true);
            next(8);
        } else if (stage == 8 && ticks > 10) {
            capture(client, output, "observer-" + visibility + "-small.png");
            resize(client, false);
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            work = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                serverTable.sit(player, seat);
                var game = serverTable.participantSession(player);
                require(game != null && game.requestExit(id), "Cannot finish visibility fixture");
                player.stopRiding();
                serverTable.sit(player, 0);
            });
            mode++;
            preparation = new RoomPreparationSmoke();
            next(0);
        }
        return false;
    }

    private void next(int value) { stage = value; ticks = 0; }
    private static void resize(Minecraft client, boolean small) {
        client.getWindow().setWindowed(small ? 960 : 1280, small ? 720 : 800);
        client.options.guiScale().set(small ? 3 : 2);
        client.resizeDisplay();
    }
    private static void capture(Minecraft client, Path output, String name) {
        SmokeScreenshots.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
