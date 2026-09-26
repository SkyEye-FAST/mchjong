package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.TenpaiHints;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Dense HUDs and thirteen waits through pointer hover and native keyboard focus. */
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
        if (sample == 12) return ++ticks >= 10;
        if (ticks == 0) {
            if (sample % 3 == 2) {
                // Hover only: moving to the button must keep this discard without a selected tile.
                InputSmoke.pointerHand(client, (TableScreen) client.screen, fixture, 125);
            } else InputSmoke.pointer(client, 4, 4);
        }
        if (ticks == 3) {
            var button = hintButton(client);
            InputSmoke.pointerWidget(client, button);
        }
        if (ticks == 10 || ticks == 20) {
            var button = hintButton(client);
            if (table.clientView() != fixture)
                throw new IllegalStateException("Hint fixture was replaced before hover/focus verification");
            if (!button.visible || !button.active) {
                int waits = new TenpaiHints().waits(fixture, sample % 3 == 2 ? 125 : Tile.ABSENT).size();
                throw new IllegalStateException("Wait preview became unavailable on hover/focus: waits=" + waits
                    + " button=" + button.getX() + "," + button.getY() + " screen=" + client.screen.width + "x" + client.screen.height);
            }
            if (!button.isHoveredOrFocused())
                throw new IllegalStateException("Wait preview lost hover/focus at " + button.getX() + "," + button.getY());
            if (sample % 3 == 2 && (button.getWidth() != 40 || button.getHeight() != 32))
                throw new IllegalStateException("Immersive wait preview must use the enlarged focus target");
            if (button.getMessage().getString().split("\n").length != 15)
                throw new IllegalStateException("Thirteen waits are missing from native narration");
            AutomationControlsSmoke.checkBounds(client);
            if (sample % 3 == 2) for (var child : client.screen.children())
                if (child instanceof AbstractWidget widget && widget.visible && widget.getY() == 16
                        && client.font.width(widget.getMessage()) * 2 > widget.getWidth() - 12)
                    throw new IllegalStateException("Immersive toolbar caption is clipped: " + widget.getMessage().getString());
            Screenshot.grab(output.toFile(), "58-tenpai-" + LANGUAGES[sample / 3]
                + switch (sample % 3) { case 0 -> "-640x400-seated"; case 1 -> "-320x240-seated"; default -> "-480x300-immersive-preview"; }
                + (ticks == 10 ? "-hover.png" : "-keyboard.png"), client.getMainRenderTarget(), ignored -> {});
            if (ticks == 10) {
                InputSmoke.pointer(client, 4, 4);
                client.screen.setFocused(null);
                for (int i = 0; i < 30 && client.screen.getFocused() != button; i++)
                    client.screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                if (client.screen.getFocused() != button) throw new IllegalStateException("Tab cannot reach wait preview");
            }
        }
        if (++ticks <= 20) return false;
        if (++sample < 12) show(client, table);
        else {
            settings.convenienceHints = enabled; settings.animations = animations; settings.discardMode = discardMode;
            client.getWindow().setWindowed(width, height); client.options.guiScale().set(scale); client.resizeDisplay();
            client.getLanguageManager().setSelected(language); reload = client.reloadResourcePacks(); ticks = 0;
            table.acceptView(new TableView(original.tableId(), original.revision() + 13, original.decision(),
                original.handNumber(), original.rules(), original.phase(), original.viewerSeat(), original.dealer(),
                original.round(), original.honba(), original.riichiSticks(), original.turn(), original.remaining(),
                original.wallBreak(), original.wall(), original.focus(), original.seats(), original.actions(), original.wins(),
                original.result(), original.deltas(), original.finalScores(), original.finalUma(), original.timeControl(), original.clocks(),
                original.finalRanks(), original.handVisibility(), original.exitVote(), original.handling(), original.autoPlay(), original.ronBlocked(), original.riichiHan()));
            client.setScreen(new TableScreen(table.getBlockPos()));
        }
        return false;
    }

    private void show(Minecraft client, MahjongTableBlockEntity table) {
        boolean preview = sample % 3 == 2, small = sample % 3 == 1;
        client.getWindow().setWindowed(preview || small ? 960 : 1280, preview ? 600 : small ? 720 : 800);
        client.options.guiScale().set(small ? 3 : 2); client.resizeDisplay();
        var hand = new ArrayList<>(List.of(0, 32, 36, 68, 72, 104, 108, 112, 116, 120, 124, 128, 132));
        if (preview) hand.add(125);
        var seats = new ArrayList<>(original.seats());
        seats.set(0, new TableView.Seat(false, "Player", true, false, false, 25000, hand, preview ? 125 : Tile.ABSENT,
            List.of(), List.of(), List.of(), false, false, false));
        for (int seat = 1; seat < seats.size(); seat++) {
            int owner = seat;
            var melds = java.util.stream.IntStream.range(0, seat == 1 ? 4 : 1).mapToObj(i -> {
                int tile = 4 + i * 4;
                return new Meld(seatType(owner), List.of(tile, tile + 1, tile + 2, tile + 3),
                    owner >= 2 ? owner : 0, owner >= 2 ? Tile.ABSENT : tile);
            }).toList();
            seats.set(seat, new TableView.Seat(false, "Long player name " + seat, true, false, false, 25000,
                java.util.Collections.nCopies(13 - melds.size() * 3, Tile.HIDDEN), Tile.ABSENT,
                melds, List.of(), List.of(), seat == seats.size() - 1, false, false));
        }
        var wall = new ArrayList<>(java.util.Collections.nCopies(136, Tile.HIDDEN));
        for (int i = 0; i < 5; i++) wall.set(131 - i * 2, 40 + i * 4);
        // Advance the display snapshot so rendering updates while live lobby heartbeats stay stale.
        fixture = new TableView(original.tableId(), original.revision() + sample + 1, original.decision(), original.handNumber(),
            original.rules(), Game.Phase.TURN, 0, 0, 0, 3, 4, 0, 70,
            original.wallBreak(), wall, null, seats, preview ? List.of(new Action(Action.Type.DISCARD, 125), new Action(Action.Type.DISCARD, 0)) : List.of(),
            List.of(), "playing", List.of(), List.of(), List.of(), original.timeControl(), List.of(), List.of(), top.skyeyefast.mchjong.engine.HandVisibility.SELF, null, null, original.autoPlay(), true, 1);
        if (new TenpaiHints().waits(fixture, preview ? 125 : Tile.ABSENT).size() != 13)
            throw new IllegalStateException("Thirteen-way hint fixture is not ready");
        table.acceptView(fixture); client.setScreen(new TableScreen(table.getBlockPos()));
        if (preview) {
            client.screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
        }
        AutomationControlsSmoke.click(client, Component.translatable("ui.mchjong.automation_show").getString());
        client.screen.setFocused(null);
        // Also cover a prior selection: the last hovered discard must win when entering the diamond.
        if (preview && sample >= 6) InputSmoke.clickHand((TableScreen) client.screen, fixture, 0);
        client.getLanguageManager().setSelected(LANGUAGES[sample / 3]); reload = client.reloadResourcePacks(); ticks = 0;
    }

    private static Meld.Type seatType(int seat) { return seat >= 2 ? Meld.Type.CLOSED_KAN : Meld.Type.OPEN_KAN; }

    private static AbstractWidget hintButton(Minecraft client) {
        String title = Component.translatable("hints.mchjong.button").getString();
        return client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().startsWith(title)).findFirst().orElseThrow();
    }

}
