package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.engine.AutoPlay;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Exercise every preference through its real button, C2S packet and authoritative S2C snapshot. */
final class AutomationControlsSmoke {
    private static final String[] KEYS = {"ui.mchjong.auto_sort", "ui.mchjong.auto_win",
        "ui.mchjong.no_calls", "ui.mchjong.auto_discard", "ui.mchjong.auto_kita"};
    private int stage, ticks, totalTicks, toggle;
    private long decision;
    private AutoPlay initial, expected;
    private final RoomPreparationSmoke preparation = new RoomPreparationSmoke();
    private CompletableFuture<Void> reseated;
    private boolean sanma;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        require(++totalTicks < 2400, "Automatic controls timed out at stage " + stage + ", toggle " + toggle);
        ticks++;
        var view = table.clientView();
        require(view != null, "Automatic controls lost their table view");
        if (stage < 8) require(view.autoPlay() != null, "Seated automatic-table preferences are missing");
        if (stage == 0) {
            initial = view.autoPlay();
            var parent = new TableScreen(table.getBlockPos());
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
            client.resizeGui();
            client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0));
            next(2);
        } else if (stage == 2 && ticks > 12) {
            checkBounds(client);
            require(client.screen.width == 320 && client.screen.height == 240, "Automatic controls did not reach 320x240");
            require(((TableScreen) client.screen).immersive(), "Small viewport disabled the fixed immersive canvas");
            capture(client, output, "53", "expanded-small-letterbox");
            client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0));
            next(3);
        } else if (stage == 3 && ticks > 2) {
            var option = AutoPlay.Option.values()[toggle / 2];
            boolean enabled = view.autoPlay().enabled(option);
            expected = view.autoPlay().with(option, !enabled);
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
                require(client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0)), "Compact option rejected keyboard activation");
            }
            next(4);
        } else if (stage == 4 && ticks > 2) {
            if (!expected.equals(view.autoPlay())) {
                // A live opponent may advance the decision between the rendered frame and the packet.
                if (decision != view.decision()) next(3);
                return false;
            }
            checkBounds(client);
            require(client.screen.getFocused() == optionButton(client, AutoPlay.Option.values()[toggle / 2]),
                "Automatic option lost keyboard focus on server acknowledgement");
            if (++toggle == (sanma ? 10 : 8)) {
                require(initial.equals(view.autoPlay()), "Preference round-trip changed another option");
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
            client.screen.onClose();
            return true;
        } else if (stage == 8 && view.phase() == Game.Phase.LOBBY && view.viewerSeat() < 0 && !client.player.isPassenger()) {
            client.setScreen(new TableScreen(table.getBlockPos()));
            checkOptions(client, 0);
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            reseated = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                ((MahjongTableBlockEntity) player.level().getBlockEntity(pos)).sit(player, 0);
            });
            next(9);
        } else if (stage == 9 && reseated.isDone() && view.viewerSeat() == 0 && ticks > 5) {
            reseated.join();
            checkOptions(client, 0);
            click(client, Component.translatable("ui.mchjong.players.3").getString());
            next(10);
        } else if (stage == 10 && view.rules().sanma() && preparation.tick(client, table, output, "53-sanma-controls")) {
            sanma = true;
            toggle = 0;
            client.getWindow().setWindowed(1280, 800);
            client.options.guiScale().set(2);
            client.resizeGui();
            next(0);
        }
        return false;
    }

    private void next(int value) { stage = value; ticks = 0; }

    private void capture(Minecraft client, Path output, String prefix, String mode) {
        SmokeScreenshots.grab(output.toFile(), prefix + "-automatic-controls-" + (sanma ? "3p-" : "4p-") + mode + ".png",
            client.getMainRenderTarget(), 1, ignored -> {});
    }

    static AbstractWidget optionButton(Minecraft client, AutoPlay.Option option) {
        String name = Component.translatable(KEYS[option.ordinal()]).getString();
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
        if (client.screen instanceof TableScreen table && table.immersive()) {
            double scale = Math.min(client.screen.width / (double) TableScreen.IMMERSIVE_WIDTH,
                client.screen.height / (double) TableScreen.IMMERSIVE_HEIGHT);
            x = (client.screen.width - TableScreen.IMMERSIVE_WIDTH * scale) / 2.0 + x * scale;
            y = (client.screen.height - TableScreen.IMMERSIVE_HEIGHT * scale) / 2.0 + y * scale;
        }
        var event = new net.minecraft.client.input.MouseButtonEvent(
            x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0));
        client.screen.mouseClicked(event, false);
        client.screen.mouseReleased(event);
    }

    static void checkBounds(Minecraft client) {
        var widgets = client.screen.children().stream().filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast).filter(widget -> widget.visible).toList();
        int boundWidth = client.screen instanceof TableScreen table && table.immersive()
            ? TableScreen.IMMERSIVE_WIDTH : client.screen.width;
        int boundHeight = client.screen instanceof TableScreen table && table.immersive()
            ? TableScreen.IMMERSIVE_HEIGHT : client.screen.height;
        for (int i = 0; i < widgets.size(); i++) {
            var a = widgets.get(i);
            require(a.getX() >= 0 && a.getY() >= 0 && a.getRight() <= boundWidth
                && a.getBottom() <= boundHeight, "Automatic control exceeds its layout canvas");
            for (int j = i + 1; j < widgets.size(); j++) {
                var b = widgets.get(j);
                require(a.getRight() <= b.getX() || b.getRight() <= a.getX()
                    || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(),
                    "Automatic controls overlap: " + bounds(a) + " and " + bounds(b));
            }
        }
    }

    private static String bounds(AbstractWidget widget) {
        return widget.getClass().getSimpleName() + "[" + widget.getMessage().getString() + "] at "
            + widget.getX() + "," + widget.getY() + " " + widget.getWidth() + "x" + widget.getHeight();
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
