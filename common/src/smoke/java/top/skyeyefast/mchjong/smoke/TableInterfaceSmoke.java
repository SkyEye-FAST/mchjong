package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.ClientRiichiNetworking;
import top.skyeyefast.mchjong.client.MahjongButton;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.ExitVote;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TimeControl;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.RiichiViewPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Display-only fixtures for both table views; never submit their fabricated game actions. */
final class TableInterfaceSmoke {
    private record Sample(String language, int state, boolean immersive, boolean small) {}
    private static final Sample[] SAMPLES = {
        new Sample("en_us", 0, true, false),
        new Sample("zh_cn", 1, true, true),
        new Sample("en_us", 2, true, false),
        new Sample("zh_cn", 3, false, true),
        new Sample("en_us", 4, false, false),
        new Sample("zh_cn", 5, true, true)
    };
    private int sample, ticks;
    private CompletableFuture<Void> reload;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        if (sample == 0 && ticks == 0) {
            require(table.clientRoom() != null && table.clientRiichiSettings() != null, "Missing interface room snapshot");
            TableSettings.get().animations = false;
        }
        if (sample == SAMPLES.length) return true;
        var current = SAMPLES[sample];
        int state = current.state();
        boolean immersive = current.immersive(), small = current.small();
        if (ticks == 0) {
            client.getLanguageManager().setSelected(current.language());
            reload = client.reloadResourcePacks();
            ticks++;
            return false;
        }
        if (!reload.isDone() || client.getOverlay() != null) return false;
        reload.join();
        if (ticks == 1) {
            client.getWindow().setWindowed(small ? 960 : 1280, small ? 720 : 800);
            client.options.guiScale().set(small ? 3 : 2);
            client.resizeDisplay();
            var view = snapshot(table, state);
            var room = table.clientRoom();
            table.acceptRoom(new top.skyeyefast.mchjong.engine.TableRoomView(room.tableId(), room.incarnation(),
                view.revision(), view.decision(), room.variant(), top.skyeyefast.mchjong.engine.TableSession.Lifecycle.PLAYING,
                room.host(), view.viewerSeat(), room.manual(), room.equipped(), false, room.seating(),
                room.availableWinds(), room.seats(), List.of(), view.exitVote(), false, room.convenienceHints(), room.allowConvenienceHints(), view.viewerSeat() < 0 ? null : top.skyeyefast.mchjong.engine.MatchAutomation.DEFAULT, List.of()));
            SettlementSmoke.acceptFixture(table, view);
            var settings = TableSettings.get();
            settings.camera().reset(settings.cameraDistance, settings.cameraHeight);
            if (!immersive && state == 3) settings.camera().look(0, 85 - settings.camera().pitch());
            client.setScreen(new RiichiTableScreen(table.getBlockPos()));
            if (immersive) client.screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
            require(((RiichiTableScreen) client.screen).immersive() == immersive, "Fixture entered wrong table view");
            if (state == 1) {
                RiichiTableScreen opened = (RiichiTableScreen) client.screen;
                ClientRiichiNetworking.receive(new RiichiViewPayload(table.getBlockPos(),
                    TableNetworking.JSON.toJson(table.clientView()), true, false, false,
                    table.clientRedOptions(), table.clientRoom(), table.clientRiichiSettings(),
                    table.clientBotService(), table.clientWorldPolicy(), table.clientVariant()));
                require(client.screen == opened && opened.immersive(),
                    "Opening the exit vote replaced the immersive table screen");
            }
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
                require(button.getBottom() < clock.getY(), "RiichiAction overlaps turn clock");
        }
        String stateName = switch (state) { case 0 -> "riichi"; case 1 -> "vote"; case 2 -> "results";
            case 3 -> "reserve"; case 4 -> "meld"; default -> "reaction"; };
        SmokeScreenshots.grab(output.toFile(), "60-" + (immersive ? "immersive-" : "seated-")
            + current.language() + "-" + stateName
            + (small ? "-small.png" : ".png"), client.getMainRenderTarget(), ignored -> {});
        sample++; ticks = 0;
        return false;
    }

    private RiichiView snapshot(MahjongTableBlockEntity table, int state) {
        var room = table.clientRoom();
        var settings = table.clientRiichiSettings();
        var seats = new ArrayList<RiichiView.Seat>();
        var hand = List.of(0, 4, 8, 36, 40, 44, 72, 76, 80, 108, 109, 124, 125, 126);
        if (state == 4) hand = hand.subList(3, 14);
        if (state == 5) hand = hand.subList(0, 13);
        for (int i = 0; i < 4; i++) seats.add(new RiichiView.Seat(false, "Player " + (i + 1), true, false, false, 25000,
            i == 0 ? hand : Collections.nCopies(13, Tile.HIDDEN), i == 0 && state != 5 ? 126 : Tile.ABSENT,
            i == 0 && state == 4 ? List.of(new Meld(Meld.Type.SEQUENCE, List.of(0, 4, 8), 3, 8)) : List.of(),
            List.of(), List.of(), false, false, false));
        var actions = state == 5 ? List.of(new RiichiAction(RiichiAction.Type.PON, List.of(108, 109)),
            new RiichiAction(RiichiAction.Type.PASS, List.of())) : state == 4 ? List.<RiichiAction>of()
            : List.of(new RiichiAction(RiichiAction.Type.RIICHI, List.of(126)));
        var normal = new RiichiView(room.tableId(), Long.MAX_VALUE / 2 + sample * 100000, room.decision() + sample + 1,
            1, settings.rules(), RiichiView.Phase.TURN, 0, 0, 0, 0, 0, 0, 70, 12, Collections.nCopies(136, Tile.HIDDEN),
            null, seats, actions, List.of(), "playing", List.of(), List.of(), List.of(),
            settings.timeControl(), List.of(new TimeControl.Clock(state == 3 ? 0 : 160, state == 3 ? 100 : 400, true),
                new TimeControl.Clock(0, 0, false), new TimeControl.Clock(0, 0, false), new TimeControl.Clock(0, 0, false)), List.of(), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false,
            state == 1 ? new ExitVote(1, 1, 400, 4, List.of(1)) : null, null,
            top.skyeyefast.mchjong.engine.RiichiAutoPlay.DEFAULT, false, 1, java.util.Map.of(), List.of(), 0, 0);
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
