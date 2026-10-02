package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.engine.RiichiAutoPlay;
import top.skyeyefast.mchjong.engine.MatchAutomation;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Exercise every preference through its real button, C2S packet and authoritative S2C snapshot. */
final class AutomationControlsSmoke {
    private static final String[] KEYS = {"ui.mchjong.auto_sort", "ui.mchjong.auto_win",
        "ui.mchjong.no_calls", "ui.mchjong.auto_discard", "ui.mchjong.auto_kita"};
    private int stage, ticks, totalTicks, toggle;
    private long decision;
    private RiichiAutoPlay initial, expected;
    private MatchAutomation initialCommon, expectedCommon;
    private final RoomPreparationSmoke opening = new RoomPreparationSmoke();
    private final RoomPreparationSmoke preparation = new RoomPreparationSmoke();
    private CompletableFuture<Void> reseated;
    private boolean sanma;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        if (stage == 11) return true;
        require(++totalTicks < 2400, "Automatic controls timed out at stage " + stage + ", toggle " + toggle);
        ticks++;
        var view = table.clientView();
        if (stage == 0 && view == null) {
            if (RiichiTableScreen.active(client.screen) == null) client.setScreen(new RiichiTableScreen(table.getBlockPos()));
            if (!opening.tick(client, table, output, "52-automatic-room")) return false;
            view = table.clientView();
        }
        require(view != null || stage >= 8, "Automatic controls lost their table view");
        if (stage < 8) require(view.autoPlay() != null, "Seated automatic-table preferences are missing");
        if (stage == 0) {
            initial = view.autoPlay();
            initialCommon = table.clientTableRoom().automation();
            var parent = new RiichiTableScreen(table.getBlockPos());
            client.setScreen(parent);
            parent.resetView();
            next(1);
        } else if (stage == 1 && ticks > 12) {
            checkBounds(client);
            checkOptions(client, sanma ? 5 : 4);
            capture(client, output, "52", "collapsed");
            click(client, Component.translatable("ui.mchjong.automation_show").getString());
            next(6);
        } else if (stage == 6 && ticks > 12) {
            checkBounds(client);
            var immersiveButton = client.screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(
                    Component.translatable("ui.mchjong.view_immersive").getString()))
                .findFirst().orElseThrow();
            if (!immersiveButton.active) return false;
            capture(client, output, "52", "expanded");
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
            next(2);
        } else if (stage == 2 && ticks > 12) {
            checkBounds(client);
            require(client.screen.width == 320 && client.screen.height == 240, "Automatic controls did not reach 320x240");
            require(((RiichiTableScreen) client.screen).immersive(), "Small viewport disabled the fixed immersive canvas");
            capture(client, output, "53", "expanded-small-letterbox");
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
            next(3);
        } else if (stage == 3 && ticks > 2) {
            int option = toggle / 2;
            expected = view.autoPlay();
            expectedCommon = table.clientTableRoom().automation();
            if (option == 0 || option == 4) {
                var riichiOption = option == 0 ? RiichiAutoPlay.Option.SORT : RiichiAutoPlay.Option.KITA;
                expected = expected.with(riichiOption, !expected.enabled(riichiOption));
            } else {
                var commonOption = MatchAutomation.Option.values()[option - 1];
                expectedCommon = expectedCommon.with(commonOption, !expectedCommon.enabled(commonOption));
            }
            decision = view.decision();
            var button = optionButton(client, option);
            if ((button.getWidth() == 24) != (toggle % 2 == 1)) {
                // Catch-up client ticks can run before the next render rebuilds the controls.
                require(ticks < 40, "Wrong automatic control presentation at toggle " + toggle);
                return false;
            }
            if (toggle % 2 == 0) click(client, button.getMessage().getString());
            else {
                if (toggle == 3) capture(client, output, "53", "collapsed-small");
                client.screen.setFocused(button);
                require(client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0), "Compact option rejected keyboard activation");
            }
            next(4);
        } else if (stage == 4 && ticks > 2) {
            if (!expected.equals(view.autoPlay()) || !expectedCommon.equals(table.clientTableRoom().automation())) {
                // A live opponent may advance the decision between the rendered frame and the packet.
                if (decision != view.decision()) next(3);
                return false;
            }
            checkBounds(client);
            require(client.screen.getFocused() == optionButton(client, toggle / 2),
                "Automatic option lost keyboard focus on server acknowledgement");
            if (++toggle == (sanma ? 10 : 8)) {
                require(initial.equals(view.autoPlay()) && initialCommon.equals(table.clientTableRoom().automation()), "Preference round-trip changed another option");
                click(client, Component.translatable(sanma ? "ui.mchjong.automation_show" : "ui.mchjong.exit").getString());
                next(sanma ? 5 : 8);
                return false;
            }
            click(client, Component.translatable(toggle % 2 == 0 ? "ui.mchjong.automation_show" : "ui.mchjong.automation_hide").getString());
            next(3);
        } else if (stage == 5 && ticks > 3) {
            click(client, Component.translatable("ui.mchjong.automation_hide").getString());
            next(7);
        } else if (stage == 7 && ticks > 3) {
            checkOptions(client, 5);
            checkBounds(client);
            next(11);
            client.screen.onClose();
            return true;
        } else if (stage == 8 && table.clientRoom() != null && table.clientRoom().lobby()
            && table.clientRoom().viewerSeat() < 0 && !client.player.isPassenger()) {
            client.setScreen(new RiichiTableScreen(table.getBlockPos()));
            checkOptions(client, 0);
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            reseated = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                ((MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos)).sit(player, 0);
            });
            next(9);
        } else if (stage == 9 && reseated.isDone() && table.clientRoom().viewerSeat() == 0 && ticks > 5) {
            reseated.join();
            checkOptions(client, 0);
            LobbySmoke.find(client, Component.translatable("ui.mchjong.players.3").getString()).onPress();
            next(10);
        } else if (stage == 10 && table.clientRiichiSettings().rules().sanma()
            && preparation.tick(client, table, output, "53-sanma-controls")) {
            sanma = true;
            toggle = 0;
            client.getWindow().setWindowed(1280, 800);
            client.options.guiScale().set(2);
            client.resizeDisplay();
            next(0);
        }
        return false;
    }

    private void next(int value) { stage = value; ticks = 0; }

    private void capture(Minecraft client, Path output, String prefix, String mode) {
        SmokeScreenshots.grab(output.toFile(), prefix + "-automatic-controls-" + (sanma ? "3p-" : "4p-") + mode + ".png",
            client.getMainRenderTarget(), ignored -> {});
    }

    static AbstractWidget optionButton(Minecraft client, int option) {
        String name = Component.translatable(KEYS[option]).getString();
        return client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().startsWith(name)).findFirst().orElseThrow();
    }

    static void checkOptions(Minecraft client, int count) {
        for (int i = 0; i < KEYS.length; i++) {
            String name = Component.translatable(KEYS[i]).getString();
            boolean present = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .anyMatch(widget -> widget.getMessage().getString().startsWith(name));
            require(present == (i < count), "Incorrect automatic option visibility: " + name);
        }
    }

    static void click(Minecraft client, String label) {
        var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast).filter(widget -> widget.getMessage().getString().equals(label))
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing automatic control: " + label));
        require(button.active, "Automatic control is disabled: " + label);
        double x = button.getX() + button.getWidth() / 2.0, y = button.getY() + 10;
        if (client.screen instanceof RiichiTableScreen table && table.immersive()) {
            double scale = Math.min(client.screen.width / (double) top.skyeyefast.mchjong.client.TableCanvas.WIDTH,
                client.screen.height / (double) top.skyeyefast.mchjong.client.TableCanvas.HEIGHT);
            x = (client.screen.width - top.skyeyefast.mchjong.client.TableCanvas.WIDTH * scale) / 2.0 + x * scale;
            y = (client.screen.height - top.skyeyefast.mchjong.client.TableCanvas.HEIGHT * scale) / 2.0 + y * scale;
        }
        client.screen.mouseClicked(x, y, 0);
        client.screen.mouseReleased(x, y, 0);
    }

    static void checkBounds(Minecraft client) {
        var widgets = client.screen.children().stream().filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast).filter(widget -> widget.visible).toList();
        int boundWidth = client.screen instanceof RiichiTableScreen table && table.immersive()
            ? top.skyeyefast.mchjong.client.TableCanvas.WIDTH : client.screen.width;
        int boundHeight = client.screen instanceof RiichiTableScreen table && table.immersive()
            ? top.skyeyefast.mchjong.client.TableCanvas.HEIGHT : client.screen.height;
        for (int i = 0; i < widgets.size(); i++) {
            var a = widgets.get(i);
            require(a.getX() >= 0 && a.getY() >= 0 && (a.getX() + a.getWidth()) <= boundWidth
                && (a.getY() + a.getHeight()) <= boundHeight, "Automatic control exceeds its layout canvas");
            for (int j = i + 1; j < widgets.size(); j++) {
                var b = widgets.get(j);
                require((a.getX() + a.getWidth()) <= b.getX() || (b.getX() + b.getWidth()) <= a.getX()
                    || (a.getY() + a.getHeight()) <= b.getY() || (b.getY() + b.getHeight()) <= a.getY(), "Automatic controls overlap");
            }
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
