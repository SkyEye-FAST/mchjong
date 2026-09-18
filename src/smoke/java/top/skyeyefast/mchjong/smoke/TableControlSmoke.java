package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableAnimation;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Uses the live integrated server and real control packets before any display-only fixtures. */
final class TableControlSmoke {
    private static final int DEFAULT_TIMEOUT_TICKS = 400;
    private static final int GAME_START_TIMEOUT_TICKS = 800;
    private int stage, ticks;
    private boolean remainingHidden;
    private CompletableFuture<Void> reseated;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        ticks++;
        int timeout = stage == 7 ? GAME_START_TIMEOUT_TICKS : DEFAULT_TIMEOUT_TICKS;
        if (ticks > timeout) throw new IllegalStateException("Table controls timed out at stage " + stage);
        if (reseated != null && reseated.isDone()) reseated.join();
        var view = table.clientView();
        var settings = TableSettings.get();
        if (stage == 0) {
            remainingHidden = !settings.show(TableSettings.Information.REMAINING);
            if (!remainingHidden) settings.toggle(TableSettings.Information.REMAINING);
            settings.showRiver = false;
            require(settings.show(TableSettings.Information.REMAINING), "Hidden river removed the remaining wall count");
            client.setScreen(new TableScreen(table.getBlockPos()));
            next(1);
        } else if (stage == 1 && ticks > 10) {
            capture(client, output, "23-hidden-river.png");
            click(client, "ui.mchjong.exit");
            next(2);
        } else if (stage == 2 && view.phase() == Game.Phase.LOBBY && view.viewerSeat() < 0 && !client.player.isPassenger()) {
            require(client.screen == null, "Exiting the table did not close its controls");
            require(view.seats().stream().noneMatch(seat -> seat.occupied()), "Exiting left a seat reserved");
            require(view.wall().isEmpty(), "Exiting left the old wall on the table");
            capture(client, output, "24-exited-table.png");
            UUID id = client.player.getUUID();
            var pos = table.getBlockPos();
            reseated = new CompletableFuture<>();
            client.getSingleplayerServer().execute(() -> {
                try {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                    ((MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos)).sit(player, 0);
                    reseated.complete(null);
                } catch (Throwable failure) { reseated.completeExceptionally(failure); }
            });
            next(3);
        } else if (stage == 3 && client.screen instanceof TableScreen && view.viewerSeat() == 0 && ticks > 10) {
            click(client, "ui.mchjong.players.3");
            next(4);
        } else if (stage == 4 && view.rules().players() == 3) {
            require(client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .noneMatch(widget -> widget.getMessage().getString().equals(Component.translatable("preset.mchjong.m_league").getString())),
                "Four-player preset appeared in the three-player lobby");
            capture(client, output, "25-three-player-lobby.png");
            click(client, "ui.mchjong.players.4");
            next(5);
        } else if (stage == 5 && view.rules().players() == 4) {
            String label = Component.translatable("ui.mchjong.open_hands").getString();
            var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().contains(label)).findFirst().orElseThrow();
            require(button.active, "Host could not change hand visibility");
            client.screen.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
            next(6);
        } else if (stage == 6 && view.openHands()) {
            click(client, "ui.mchjong.practice_short");
            next(7);
        } else if (stage == 7 && view.phase() == Game.Phase.TURN && ticks > 60
            && !TableAnimation.of(table).dealing(net.minecraft.Util.getMillis())) {
            require(view.seats().stream().flatMap(seat -> seat.hand().stream()).allMatch(tile -> tile >= 0),
                "Server did not deliver the agreed open hands to the seated player");
            capture(client, output, "26-open-hands.png");
            settings.showRiver = true;
            if (!remainingHidden) settings.toggle(TableSettings.Information.REMAINING);
            return true;
        }
        return false;
    }

    private void next(int value) { stage = value; ticks = 0; }
    private static void click(Minecraft client, String key) {
        String label = Component.translatable(key).getString();
        var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
        require(button.active, "Disabled control: " + key);
        client.screen.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
    }
    private static void capture(Minecraft client, Path output, String file) {
        Screenshot.grab(output.toFile(), file, client.getMainRenderTarget(), ignored -> {});
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
