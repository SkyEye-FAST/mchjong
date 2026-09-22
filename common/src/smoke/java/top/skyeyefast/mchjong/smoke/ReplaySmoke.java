package top.skyeyefast.mchjong.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.ClientReplays;
import top.skyeyefast.mchjong.client.ReplayBrowserScreen;
import top.skyeyefast.mchjong.client.ReplayScreen;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.ReplayPlayback;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.replay.ReplayServer;
import top.skyeyefast.mchjong.world.MahjongSounds;

/** Actual engine-generated fixture, server archive, command, S2C chunks and export button. */
final class ReplaySmoke {
    private CompletableFuture<ReplayMatch> fixture;
    private ReplayMatch match;
    private CompletableFuture<Boolean> deletion;
    private ReplayInterfaceSmoke presentation;
    private int stage, ticks;

    boolean tick(Minecraft client, Path output) throws Exception {
        ticks++;
        if (fixture == null) {
            for (var sound : MahjongSounds.EVENTS.values())
                require(client.getSoundManager().getSoundEvent(sound.getLocation()) != null, "Missing sound event " + sound.getLocation());
            fixture = new CompletableFuture<>();
            UUID viewer = client.player.getUUID();
            client.getSingleplayerServer().execute(() -> {
                try {
                    Game game = createHand(viewer);
                    ReplayMatch record = game.pendingReplays().getFirst();
                    ReplayServer.flush(client.getSingleplayerServer(), game);
                    require(game.pendingReplays().isEmpty(), "Archive was not acknowledged");
                    fixture.complete(record);
                } catch (Throwable failure) { fixture.completeExceptionally(failure); }
            });
        }
        if (stage == 0 && fixture.isDone()) {
            match = fixture.join();
            ClientReplays.list(0, "", false);
            stage = 1; ticks = 0;
        } else if (stage == 1 && ticks > 15 && client.screen instanceof ReplayBrowserScreen) {
            checkBounds(client);
            capture(client,output,"19-replay-browser.png");
            ClientReplays.open(match.id());
            stage = 2; ticks = 0;
        } else if (stage == 2 && ticks > 15 && client.screen instanceof ReplayScreen replay) {
            require(replay.match().equals(match), "Replay transport changed the record");
            require(replay.cursor() == 0, "Replay did not start at the initial deal");
            require(Tile.validSet(match.hands().getFirst().wall().tiles()), "Replay transfer lost the physical wall");
            require(!match.hands().getFirst().decisions().isEmpty(), "Replay transfer lost decision points");
            checkBounds(client);
            capture(client,output,"20-replay-initial.png");
            replay.keyPressed(GLFW.GLFW_KEY_RIGHT_BRACKET,0,0);
            require(replay.cursor() > 0, "Decision navigation failed");
            replay.keyPressed(GLFW.GLFW_KEY_W,0,0);
            replay.keyPressed(GLFW.GLFW_KEY_W,0,0);
            replay.keyPressed(GLFW.GLFW_KEY_RIGHT,0,0);
            replay.keyPressed(GLFW.GLFW_KEY_END,0,0);
            stage = 3; ticks = 0;
        } else if (stage == 3 && ticks > 15 && client.screen instanceof ReplayScreen replay) {
            require(replay.cursor() == ReplayPlayback.timeline(match, 0).frames().size() - 1, "Cannot seek to settlement");
            capture(client,output,"21-replay-settlement.png");
            client.options.guiScale().set(3); client.resizeDisplay();
            stage = 4; ticks = 0;
        } else if (stage == 4 && ticks > 10 && client.screen instanceof ReplayScreen) {
            checkBounds(client);
            capture(client,output,"22-replay-small.png");
            var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().contains("Tenhou")).findFirst().orElseThrow();
            client.screen.mouseClicked(button.getX()+5,button.getY()+5,0);
            stage = 5; ticks = 0;
        } else if (stage == 5 && ticks > 5) {
            Path file = client.gameDirectory.toPath().resolve("replays/mchjong").resolve(match.id()+".json");
            var json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            require(json.get("ver").getAsString().equals("2.3"), "Export has the wrong format");
            require(json.get("ref").getAsString().equals(match.id().toString()), "Export has the wrong match identity");
            require(json.getAsJsonArray("log").size() == match.hands().size(), "Export is incomplete");
            require(!json.has("seed") && !json.has("recorder"), "Export contains live internal state");
            ClientReplays.list(0, "Replay player", true);
            stage = 6; ticks = 0;
        } else if (stage == 6 && ticks > 15 && client.screen instanceof ReplayBrowserScreen) {
            checkBounds(client);
            var search = client.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
            require(search.getValue().equals("Replay player"), "Replay search was not retained by the server");
            capture(client, output, "23-replay-manager-small.png");
            click(client, "replay.mchjong.delete");
            stage = 7; ticks = 0;
        } else if (stage == 7 && ticks > 5 && client.screen instanceof top.skyeyefast.mchjong.client.DeleteReplayScreen) {
            checkBounds(client);
            capture(client, output, "24-replay-delete-confirm.png");
            client.screen.onClose();
            require(client.screen instanceof ReplayBrowserScreen, "Cancelling deletion lost the browser");
            click(client, "replay.mchjong.delete");
            click(client, "replay.mchjong.delete");
            stage = 8; ticks = 0;
        } else if (stage == 8 && ticks > 15 && client.screen instanceof ReplayBrowserScreen) {
            var button = button(client, "replay.mchjong.delete");
            require(!button.active, "Deleted replay remains in the filtered archive");
            capture(client, output, "25-replay-deleted.png");
            var server = client.getSingleplayerServer();
            UUID viewer = client.player.getUUID();
            deletion = server.submit(() -> {
                var store = new top.skyeyefast.mchjong.replay.ReplayStore(server.getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/mchjong/replays"),
                    top.skyeyefast.mchjong.network.TableNetworking.JSON);
                try {
                    require(store.list(viewer, 0, match.id().toString(), false).matches().isEmpty(), "Deletion did not reach disk");
                    UUID other = match.participants().stream().map(ReplayMatch.Participant::id).filter(id -> !id.equals(viewer)).findFirst().orElseThrow();
                    require(store.load(other, match.id()).equals(match), "Deletion affected another participant");
                    store.save(match);
                    require(store.list(viewer, 0, match.id().toString(), false).matches().isEmpty(), "Later saves resurrected a deleted replay");
                    return true;
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
            stage = 9; ticks = 0;
        } else if (stage == 9 && deletion.isDone()) {
            require(deletion.join(), "Deletion verification failed");
            require(Files.isRegularFile(client.gameDirectory.toPath().resolve("replays/mchjong").resolve(match.id() + ".json")),
                "Deleting a server archive removed the local export");
            presentation = new ReplayInterfaceSmoke(match.header());
            stage = 10; ticks = 0;
        } else if (stage == 10) {
            if (!presentation.tick(client, output)) return false;
            client.options.guiScale().set(2); client.resizeDisplay();
            return true;
        }
        if (ticks > 300) throw new IllegalStateException("Replay smoke timed out at stage " + stage);
        return false;
    }

    private static Game createHand(UUID viewer) {
        Game game = new Game(UUID.randomUUID(),RuleSet.TENHOU_4,73519);
        UUID[] ids = {viewer,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()};
        for (int seat = 0; seat < 4; seat++) {
            require(game.join(ids[seat], "Replay player " + (seat+1),seat),"Cannot join replay fixture");
        }
        SeatingFixtures.startPositioned(game, ids);
        for (int step = 0; step < 2000; step++) {
            if (game.phase() == Game.Phase.HAND_END || game.phase() == Game.Phase.MATCH_END) return game;
            boolean acted = false;
            for (UUID id : ids) {
                TableView view = game.view(id);
                int action = find(view,Action.Type.PASS);
                if (action < 0) action = find(view,Action.Type.TSUMO);
                if (action < 0) action = find(view,Action.Type.DISCARD);
                if (action >= 0) { require(game.act(id,view.decision(),action),"Replay fixture rejected a legal move"); acted = true; break; }
            }
            require(acted,"Replay fixture deadlocked");
        }
        throw new IllegalStateException("Replay fixture never settled");
    }
    private static int find(TableView view, Action.Type type) {
        for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == type) return i;
        return -1;
    }
    private static void checkBounds(Minecraft client) {
        for (var child : client.screen.children()) if (child instanceof AbstractWidget widget)
            require(widget.getX() >= 0 && widget.getY() >= 0 && (widget.getX() + widget.getWidth()) <= client.screen.width
                && (widget.getY() + widget.getHeight()) <= client.screen.height, "Replay control exceeds the window: " + widget.getMessage().getString());
    }
    private static AbstractWidget button(Minecraft client, String key) {
        String title = net.minecraft.network.chat.Component.translatable(key).getString();
        return client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(title)).findFirst().orElseThrow();
    }
    private static void click(Minecraft client, String key) {
        var button = button(client, key);
        require(button.active, "Replay control is disabled: " + key);
        client.screen.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
    }
    private static void capture(Minecraft client, Path output, String name) {
        Screenshot.grab(output.toFile(),name,client.getMainRenderTarget(),ignored -> {});
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
