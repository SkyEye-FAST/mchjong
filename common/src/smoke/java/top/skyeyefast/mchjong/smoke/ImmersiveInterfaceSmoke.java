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
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Fixed-canvas UI fixtures only; never submit their fabricated game actions. */
final class ImmersiveInterfaceSmoke {
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
        if (sample == LANGUAGES.length * 6) return true;
        if (ticks == 0) {
            if (sample % 6 == 0) {
                client.getLanguageManager().setSelected(LANGUAGES[sample / 6]);
                reload = client.reloadResourcePacks();
            }
            ticks++;
            return false;
        }
        if (!reload.isDone() || client.getOverlay() != null) return false;
        reload.join();
        if (ticks == 1) {
            boolean small = sample % 6 >= 3;
            client.getWindow().setWindowed(small ? 960 : 1280, small ? 720 : 800);
            client.options.guiScale().set(small ? 3 : 2);
            client.resizeDisplay();
            table.acceptView(snapshot(sample % 3));
            client.setScreen(new TableScreen(table.getBlockPos()));
            client.screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
            require(((TableScreen) client.screen).immersive(), "Fixture did not enter immersive view");
            if (sample % 3 == 0) button(client, "action.mchjong.riichi").onPress();
        }
        if (++ticks < 12) return false;
        for (var child : client.screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= 1280 && widget.getBottom() <= 800,
                "Widget outside immersive canvas: " + widget.getMessage().getString());
            if (widget instanceof MahjongButton && widget.getHeight() >= 20)
                require(widget.getHeight() >= 32, "Unscaled immersive button: " + widget.getMessage().getString());
        }
        String state = switch (sample % 3) { case 0 -> "riichi"; case 1 -> "vote"; default -> "results"; };
        Screenshot.grab(output.toFile(), "60-immersive-" + LANGUAGES[sample / 6] + "-" + state
            + (sample % 6 >= 3 ? "-small.png" : ".png"), client.getMainRenderTarget(), ignored -> {});
        sample++; ticks = 0;
        return false;
    }

    private TableView snapshot(int state) {
        var seats = new ArrayList<TableView.Seat>();
        var hand = List.of(0, 4, 8, 36, 40, 44, 72, 76, 80, 108, 109, 124, 125, 126);
        for (int i = 0; i < 4; i++) seats.add(new TableView.Seat("Player " + (i + 1), true, false, false, 25000,
            i == 0 ? hand : Collections.nCopies(13, Tile.HIDDEN), i == 0 ? 126 : Tile.ABSENT,
            List.of(), List.of(), List.of(), false, false));
        var normal = new TableView(base.tableId(), Long.MAX_VALUE / 2 + sample * 100000, base.decision() + sample + 1,
            1, base.rules(), Game.Phase.TURN, 0, 0, 0, 0, 0, 0, 70, 12, Collections.nCopies(136, Tile.HIDDEN),
            null, seats, List.of(new Action(Action.Type.RIICHI, List.of(126))), List.of(), "playing", List.of(), List.of(),
            base.timeControl(), base.clocks(), List.of(), false,
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
