package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.TenpaiHints;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** The thirteen-wait rail at both viewports, with real fonts and pointer discard selection. */
final class TenpaiHintsSmoke {
    private static final String[] LANGUAGES = {"zh_cn", "zh_tw", "ja_jp", "en_us"};
    private int sample = -1, ticks, width, height, scale;
    private boolean enabled, animations;
    private TableSettings.DiscardMode discardMode;
    private String language;
    private CompletableFuture<Void> reload;
    private TableView original, fixture;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        var settings = TableSettings.get();
        if (sample < 0) {
            width = client.getWindow().getScreenWidth(); height = client.getWindow().getScreenHeight();
            scale = client.options.guiScale().get(); language = client.getLanguageManager().getSelected();
            enabled = settings.convenienceHints; animations = settings.animations; discardMode = settings.discardMode;
            settings.convenienceHints = true; settings.animations = false; settings.discardMode = TableSettings.DiscardMode.CONFIRM;
            original = table.clientView();
            sample = 0;
            show(client, table);
            return false;
        }
        if (!reload.isDone() || client.getOverlay() != null) return false;
        reload.join();
        if (ticks == 0 && sample < 8) {
            long window = client.getWindow().getWindow();
            var cursor = GLFW.glfwSetCursorPosCallback(window, null);
            if (cursor == null) throw new IllegalStateException("Missing native cursor callback");
            try { cursor.invoke(window, 4, 4); }
            finally { GLFW.glfwSetCursorPosCallback(window, cursor); }
        }
        if (++ticks < 10) return false;
        if (sample == 8) return true;
        if (table.clientView() != fixture) throw new IllegalStateException("Hint fixture was replaced before capture");
        AutomationControlsSmoke.checkBounds(client);
        Screenshot.grab(output.toFile(), "58-tenpai-" + LANGUAGES[sample / 2]
            + (sample % 2 == 0 ? "-640x400-seated.png" : "-480x300-immersive-preview.png"), client.getMainRenderTarget(), ignored -> {});
        if (++sample < 8) show(client, table);
        else {
            settings.convenienceHints = enabled; settings.animations = animations; settings.discardMode = discardMode;
            client.getWindow().setWindowed(width, height); client.options.guiScale().set(scale); client.resizeDisplay();
            client.getLanguageManager().setSelected(language); reload = client.reloadResourcePacks(); ticks = 0;
            table.acceptView(new TableView(original.tableId(), original.revision() + 9, original.decision(),
                original.handNumber(), original.rules(), original.phase(), original.viewerSeat(), original.dealer(),
                original.round(), original.honba(), original.riichiSticks(), original.turn(), original.remaining(),
                original.wallBreak(), original.wall(), original.focus(), original.seats(), original.actions(), original.wins(),
                original.result(), original.deltas(), original.finalScores(), original.timeControl(), original.clocks(),
                original.finalRanks(), original.openHands(), original.exitVote(), original.handling(), original.autoPlay(), original.ronBlocked(), original.riichiHan()));
            client.setScreen(new TableScreen(table.getBlockPos()));
        }
        return false;
    }

    private void show(Minecraft client, MahjongTableBlockEntity table) {
        boolean preview = sample % 2 == 1;
        client.getWindow().setWindowed(preview ? 960 : 1280, preview ? 600 : 800);
        client.options.guiScale().set(2); client.resizeDisplay();
        var hand = new ArrayList<>(List.of(0, 32, 36, 68, 72, 104, 108, 112, 116, 120, 124, 128, 132));
        if (preview) hand.add(125);
        var seats = new ArrayList<>(original.seats());
        seats.set(0, new TableView.Seat("Player", true, false, false, 25000, hand, preview ? 125 : Tile.ABSENT,
            List.of(), List.of(), List.of(), false, false));
        // Advance the display snapshot so rendering updates while live lobby heartbeats stay stale.
        fixture = new TableView(original.tableId(), original.revision() + sample + 1, original.decision(), original.handNumber(),
            original.rules(), Game.Phase.TURN, 0, 0, 0, 0, 0, 0, 70,
            original.wallBreak(), original.wall(), null, seats, preview ? List.of(new Action(Action.Type.DISCARD, 125)) : List.of(),
            List.of(), "playing", List.of(), List.of(), original.timeControl(), List.of(), List.of(), false, null, null, original.autoPlay(), false, 1);
        if (new TenpaiHints().waits(fixture, preview ? 125 : Tile.ABSENT).size() != 13)
            throw new IllegalStateException("Thirteen-way hint fixture is not ready");
        table.acceptView(fixture); client.setScreen(new TableScreen(table.getBlockPos()));
        if (preview) {
            client.screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
            AutomationControlsSmoke.click(client, net.minecraft.network.chat.Component.translatable("ui.mchjong.automation_show").getString());
            client.screen.setFocused(null);
            InputSmoke.clickHand((TableScreen) client.screen, fixture, 125);
        }
        client.getLanguageManager().setSelected(LANGUAGES[sample / 2]); reload = client.reloadResourcePacks(); ticks = 0;
    }
}
