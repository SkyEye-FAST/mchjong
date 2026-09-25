package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.MahjongButton;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.ExitVote;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TimeControl;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Display-only fixtures for both table views; never submit their fabricated game actions. */
final class TableInterfaceSmoke {
    private static final String[] LANGUAGES = {"en_us", "ja_jp", "zh_cn", "zh_tw"};
    private int sample, ticks;
    private TableView base;
    private CompletableFuture<Void> reload;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        if (base == null) {
            base = table.clientView();
            require(base != null, "Missing interface base snapshot");
            TableSettings.get().animations = false;
        }
        int localizedSamples = LANGUAGES.length * 6;
        if (sample == localizedSamples + 16) return true;
        boolean clockSample = sample >= localizedSamples;
        int clockCase = (sample - localizedSamples) % 4;
        int state = clockSample ? (clockCase == 0 ? 0 : clockCase + 2) : sample % 3;
        boolean immersive = !clockSample || (sample - localizedSamples) / 4 % 2 == 0;
        boolean small = clockSample ? sample - localizedSamples >= 8 : sample % 6 >= 3;
        if (ticks == 0) {
            if ((!clockSample && sample % 6 == 0) || sample == localizedSamples) {
                client.getLanguageManager().setSelected(clockSample ? "zh_cn" : LANGUAGES[sample / 6]);
                reload = client.reloadResourcePacks();
            }
            ticks++;
            return false;
        }
        if (!reload.isDone() || client.getOverlay() != null) return false;
        reload.join();
        if (ticks == 1) {
            client.getWindow().setWindowed(small ? 960 : 1280, small ? 720 : 800);
            client.options.guiScale().set(small ? 3 : 2);
            client.resizeDisplay();
            SettlementSmoke.acceptFixture(table, snapshot(state));
            var settings = TableSettings.get();
            settings.camera().reset(settings.cameraDistance, settings.cameraHeight);
            if (!immersive && state == 3) settings.camera().look(0, 85 - settings.camera().pitch());
            client.setScreen(new TableScreen(table.getBlockPos()));
            if (immersive) client.screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
            require(((TableScreen) client.screen).immersive() == immersive, "Fixture entered wrong table view");
            if (state == 0) button(client, "action.mchjong.riichi").onPress();
        }
        if (++ticks < 12) return false;
        for (var child : client.screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            require(widget.getX() >= 0 && widget.getY() >= 0
                && widget.getRight() <= (immersive ? 1280 : client.screen.width)
                && widget.getBottom() <= (immersive ? 800 : client.screen.height),
                "Widget outside table viewport: " + widget.getMessage().getString());
            if (immersive && widget instanceof MahjongButton && widget.getHeight() >= 20)
                require(widget.getHeight() >= 32, "Unscaled immersive button: " + widget.getMessage().getString());
        }
        if (state != 1 && state != 2) {
            var clock = client.screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).filter(widget -> widget.getClass().getSimpleName().equals("TableTurnClock"))
                .findFirst().orElseThrow();
            require(clock.visible, "Active turn clock is missing");
            for (var child : client.screen.children()) if (child instanceof AbstractButton button && button.visible
                && button.getY() > 70 * (immersive ? 2 : 1) && button.getHeight() >= (immersive ? 40 : 26))
                require(button.getBottom() < clock.getY(), "Action overlaps turn clock");
        }
        String stateName = switch (state) { case 0 -> "riichi"; case 1 -> "vote"; case 2 -> "results";
            case 3 -> "reserve"; case 4 -> "meld"; default -> "reaction"; };
        Screenshot.grab(output.toFile(), (clockSample ? "61-" : "60-") + (immersive ? "immersive-" : "seated-")
            + (clockSample ? "zh_cn" : LANGUAGES[sample / 6]) + "-" + stateName
            + (small ? "-small.png" : ".png"), client.getMainRenderTarget(), ignored -> {});
        sample++; ticks = 0;
        return false;
    }

    private TableView snapshot(int state) {
        var seats = new ArrayList<TableView.Seat>();
        var hand = List.of(0, 4, 8, 36, 40, 44, 72, 76, 80, 108, 109, 124, 125, 126);
        if (state == 4) hand = hand.subList(3, 14);
        if (state == 5) hand = hand.subList(0, 13);
        for (int i = 0; i < 4; i++) seats.add(new TableView.Seat(false, "Player " + (i + 1), true, false, false, 25000,
            i == 0 ? hand : Collections.nCopies(13, Tile.HIDDEN), i == 0 && state != 5 ? 126 : Tile.ABSENT,
            i == 0 && state == 4 ? List.of(new Meld(Meld.Type.CHI, List.of(0, 4, 8), 3, 8)) : List.of(),
            List.of(), List.of(), false, false, false));
        var actions = state == 5 ? List.of(new Action(Action.Type.PON, List.of(108, 109)),
            new Action(Action.Type.PASS, List.of())) : state == 4 ? List.<Action>of()
            : List.of(new Action(Action.Type.RIICHI, List.of(126)));
        var normal = new TableView(base.tableId(), Long.MAX_VALUE / 2 + sample * 100000, base.decision() + sample + 1,
            1, base.rules(), Game.Phase.TURN, 0, 0, 0, 0, 0, 0, 70, 12, Collections.nCopies(136, Tile.HIDDEN),
            null, seats, actions, List.of(), "playing", List.of(), List.of(), List.of(),
            base.timeControl(), List.of(new TimeControl.Clock(state == 3 ? 0 : 160, state == 3 ? 100 : 400, true),
                new TimeControl.Clock(0, 0, false), new TimeControl.Clock(0, 0, false), new TimeControl.Clock(0, 0, false)), List.of(), top.skyeyefast.mchjong.engine.HandVisibility.SELF,
            state == 1 ? new ExitVote(1, 1, 400, 4, List.of(1)) : null, null, base.autoPlay(), false, 1);
        return state == 2 ? SettlementSmoke.fixture(normal) : normal;
    }

    private static AbstractButton button(Minecraft client, String key) {
        return client.screen.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(Component.translatable(key).getString())).findFirst().orElseThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
