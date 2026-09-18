package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableAnimation;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.client.TableRulesScreen;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Uses the live integrated server and real control packets before any display-only fixtures. */
final class TableControlSmoke {
    private static final int DEFAULT_TIMEOUT_TICKS = 400;
    private static final int GAME_START_TIMEOUT_TICKS = 800;
    private int stage, ticks;
    private boolean remainingHidden;
    private CompletableFuture<Void> reseated;
    private int originalWidth, originalHeight, originalScale;

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
            click(client, "preset.mchjong.jpml_a");
            next(8);
        } else if (stage == 8 && view.rules().equals(RuleSet.JPML_A.config()) && ticks > 5) {
            require(view.seats().stream().allMatch(seat -> seat.points() == 30000), "League A initial points");
            capture(client, output, "25a-league-a-lobby.png");
            click(client, "preset.mchjong.wrc");
            next(9);
        } else if (stage == 9 && view.rules().equals(RuleSet.WRC.config()) && ticks > 5) {
            require(view.actions().stream().noneMatch(action -> action.type() == Action.Type.PRACTICE || action.type() == Action.Type.READY),
                "WRC accepted a three-red box");
            capture(client, output, "25b-wrc-lobby.png");
            click(client, "preset.mchjong.m_league");
            next(10);
        } else if (stage == 10 && view.rules().equals(RuleSet.M_LEAGUE.config()) && ticks > 5) {
            require(view.actions().stream().anyMatch(action -> action.type() == Action.Type.PRACTICE), "M.League rejected a three-red box");
            capture(client, output, "25c-m-league-lobby.png");
            click(client, "preset.mchjong.mahjong_soul");
            next(11);
        } else if (stage == 11 && view.rules().equals(RuleSet.MAHJONG_SOUL_4.config()) && ticks > 5) {
            click(client, "rules.mchjong.title");
            next(12);
        } else if (stage == 12 && client.screen instanceof TableRulesScreen && ticks > 5) {
            var start = field(client, "rules.mchjong.option.starting_points");
            start.setValue("32101");
            client.screen.tick();
            require(!widget(client, "rules.mchjong.apply").active, "Invalid point increment could be applied");
            start.setValue("32100");
            field(client, "rules.mchjong.option.return_points").setValue("33300");
            AutomationControlsSmoke.checkBounds(client);
            next(13);
        } else if (stage == 13 && ticks > 5) {
            capture(client, output, "25d-custom-points.png");
            click(client, "rules.mchjong.group.scoring");
            next(14);
        } else if (stage == 14 && ticks > 5) {
            String label = Component.translatable("rules.mchjong.option.ippatsu").getString();
            var toggle = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().contains(label)).findFirst().orElseThrow();
            client.screen.mouseClicked(toggle.getX() + 5, toggle.getY() + 5, 0);
            originalWidth = client.getWindow().getScreenWidth(); originalHeight = client.getWindow().getScreenHeight();
            originalScale = client.options.guiScale().get();
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
            next(15);
        } else if (stage == 15 && ticks > 5) {
            require(client.screen.width == 320 && client.screen.height == 240, "Custom rules minimum viewport");
            AutomationControlsSmoke.checkBounds(client);
            capture(client, output, "25e-custom-scoring-320x240.png");
            client.getWindow().setWindowed(originalWidth, originalHeight);
            client.options.guiScale().set(originalScale);
            client.resizeDisplay();
            next(16);
        } else if (stage == 16 && ticks > 5) {
            capture(client, output, "25f-custom-scoring.png");
            click(client, "rules.mchjong.apply");
            next(17);
        } else if (stage == 17 && client.screen instanceof TableScreen && view.rules().custom() && ticks > 5) {
            require(view.rules().startingPoints() == 32100 && view.rules().returnPoints() == 33300 && !view.rules().ippatsu(),
                "Custom rule proposal was not synchronized");
            require(view.seats().stream().allMatch(seat -> seat.points() == 32100), "Custom starting points not applied");
            capture(client, output, "25g-custom-lobby.png");
            click(client, "rules.mchjong.title");
            next(18);
        } else if (stage == 18 && client.screen instanceof TableRulesScreen && ticks > 5) {
            field(client, "rules.mchjong.option.starting_points").setValue("40000");
            click(client, "gui.cancel");
            next(19);
        } else if (stage == 19 && client.screen instanceof TableScreen && ticks > 5) {
            require(view.rules().startingPoints() == 32100, "Cancel changed server rules");
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
    private static EditBox field(Minecraft client, String key) { return (EditBox) widget(client, key); }
    private static AbstractWidget widget(Minecraft client, String key) {
        String label = Component.translatable(key).getString();
        return client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
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
