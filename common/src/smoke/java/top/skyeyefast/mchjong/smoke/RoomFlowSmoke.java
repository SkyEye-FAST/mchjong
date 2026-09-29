package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.PlayerHandVisibility;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Real room controls and server timers, with a saved end-of-hand fixture for bounded runtime. */
final class RoomFlowSmoke {
    private static final String[] LANGUAGES = {"en_us", "ja_jp", "zh_cn", "zh_tw"};
    private final RoomPreparationSmoke preparation = new RoomPreparationSmoke();
    private int stage, ticks, locale, hand;
    private CompletableFuture<?> work;
    private top.skyeyefast.mchjong.engine.RiichiPreset originalPreset;
    private boolean capturedHand, capturedFinal, capturedImmersive;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        ticks++;
        if (work != null) {
            if (!work.isDone() || client.getOverlay() != null) return false;
            work.join(); work = null;
        }
        var view = table.clientView();
        if (view == null || table.clientRoom() == null) return false;
        if (stage == 0) {
            client.getLanguageManager().setSelected(LANGUAGES[locale]);
            work = client.reloadResourcePacks();
            next(1);
        } else if (stage == 1) {
            resize(client, false);
            client.setScreen(new TableScreen(table.getBlockPos()));
            next(2);
        } else if (stage == 2 && ticks > 10) {
            check(client);
            capture(client, output, "lobby-" + LANGUAGES[locale] + ".png");
            resize(client, true);
            next(3);
        } else if (stage == 3 && ticks > 10) {
            check(client);
            capture(client, output, "lobby-" + LANGUAGES[locale] + "-small.png");
            for (var key : List.of("rules.mchjong.title", "ui.mchjong.clock_settings", "ui.mchjong.invite.short", "ui.mchjong.invite",
                    "room.mchjong.participants", "settings.mchjong.scopes")) {
                click(client, key);
                require(!(client.screen instanceof TableScreen), "Room shortcut failed: " + key);
                AutomationControlsSmoke.checkBounds(client);
                client.screen.onClose();
                require(client.screen instanceof TableScreen, "Room shortcut lost its parent: " + key);
            }
            if (++locale < LANGUAGES.length) next(0);
            else {
                click(client, "settings.mchjong.hand_visibility", Component.translatable("settings.mchjong.hand_visibility.self"));
                next(4);
            }
        } else if (stage == 4 && view.playerHandVisibility() == PlayerHandVisibility.RIICHI) {
            originalPreset = view.rules().preset();
            clickText(client, Component.translatable("rules.mchjong.preset", Component.translatable(originalPreset.presetKey())).append(" ▼").getString());
            var nextPreset = java.util.Arrays.stream(top.skyeyefast.mchjong.engine.RiichiPreset.values())
                .filter(preset -> preset.players() == originalPreset.players() && preset != originalPreset).findFirst().orElseThrow();
            click(client, nextPreset.presetKey());
            click(client, "rules.mchjong.apply");
            next(5);
        } else if (stage == 5 && view.rules().preset() != originalPreset && client.screen instanceof TableScreen) {
            click(client, "ui.mchjong.players.3");
            next(6);
        } else if (stage == 6 && view.rules().players() == 3) {
            check(client);
            capture(client, output, "lobby-three-small.png");
            click(client, "ui.mchjong.players.4");
            next(7);
        } else if (stage == 7 && view.rules().players() == 4) {
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            var rules = view.rules().preset();
            work = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                serverTable.equipment().boxes().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
                var game = serverTable.participantGame(player);
                game.configureRules(id, game.view(id).decision(), rules.config());
            });
            resize(client, false);
            next(8);
        } else if (stage == 8 && ticks > 10 && view.actions().stream().anyMatch(action -> action.type()
            == top.skyeyefast.mchjong.engine.Action.Type.FILL_BOTS)) {
            click(client, "room.mchjong.start_bots");
            next(9);
        } else if (stage == 9 && view.seats().stream().allMatch(seat -> seat.occupied())) {
            require(view.actions().stream().noneMatch(action -> action.type()
                == top.skyeyefast.mchjong.engine.Action.Type.BEGIN_SEATING), "Empty box allowed seat confirmation");
            var blocked = buttonOrNull(client, "ui.mchjong.equipment_needed");
            require(blocked != null && !blocked.active, "Lobby did not explain missing equipment");
            check(client);
            capture(client, output, "equipment-needed.png");
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            var rules = view.rules().preset();
            work = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                serverTable.equipment().boxes().setItem(0, top.skyeyefast.mchjong.item.MahjongSupplies.stockedBox(rules.defaultRedFives()));
            });
            next(10);
        } else if (stage == 10 && preparation.tick(client, table, output, "room")) {
            hand = view.handNumber();
            work = settlement(client, table, false);
            next(11);
        } else if (stage == 11 && view.phase() == RiichiGame.Phase.HAND_END) {
            require(table.clientRoom().settlementTicks() > 0 && table.clientRoom().settlementTicks() <= RiichiGame.SETTLEMENT_TICKS,
                "Wrong hand settlement duration");
            if (!capturedHand && ticks > 10) {
                check(client);
                capture(client, output, "hand-countdown.png");
                capturedHand = true;
            }
        } else if (stage == 11 && capturedHand && view.phase() == RiichiGame.Phase.TURN && view.handNumber() > hand) {
            work = settlement(client, table, true);
            capturedHand = false;
            next(12);
        } else if (stage == 12 && view.phase() == RiichiGame.Phase.MATCH_END) {
            int remaining = table.clientRoom().settlementTicks();
            require(buttonOrNull(client, "room.mchjong.dissolve") == null && buttonOrNull(client, "ui.mchjong.exit") == null,
                "Settlement exposes room termination");
            if (!capturedHand && remaining > RiichiGame.SETTLEMENT_TICKS && ticks > 10) {
                check(client);
                capture(client, output, "match-hand-countdown.png");
                client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                capturedHand = true;
            } else if (capturedHand && !capturedImmersive && remaining > RiichiGame.SETTLEMENT_TICKS && ticks > 25) {
                require(((TableScreen) client.screen).immersive(), "Settlement cannot enter immersive view");
                check(client);
                capture(client, output, "match-hand-immersive.png");
                client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                resize(client, true);
                capturedImmersive = true;
            } else if (capturedImmersive && !capturedFinal && remaining <= RiichiGame.SETTLEMENT_TICKS && remaining > 20) {
                check(client);
                capture(client, output, "final-standings-small.png");
                resize(client, false);
                capturedFinal = true;
            } else if (capturedFinal && remaining <= 100 && remaining > 60) {
                capture(client, output, "final-standings.png");
                next(13);
            }
        } else if ((stage == 12 || stage == 13) && view.phase() == RiichiGame.Phase.LOBBY
            && buttonOrNull(client, "action.mchjong.leave_room") != null) {
            require(capturedFinal, "Final standings were skipped");
            require(view.viewerSeat() >= 0 && view.seats().stream().filter(seat -> seat.occupied()).count() == 4,
                "Returning to lobby lost the room roster");
            require(table.clientRoom().host() == view.viewerSeat(), "Returning to lobby lost its host");
            check(client);
            capture(client, output, "returned-lobby.png");
            click(client, "action.mchjong.leave_room");
            next(14);
        } else if (stage == 14 && view.viewerSeat() < 0) {
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            work = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                serverTable.sit(player, 0);
            });
            next(15);
        } else if (stage == 15 && view.viewerSeat() >= 0) {
            click(client, "room.mchjong.dissolve");
            next(16);
        } else if (stage == 16 && view.seats().stream().noneMatch(seat -> seat.occupied())) {
            if (client.player.isPassenger()) {
                require(ticks < 40, "Dissolved room retained the physical seat");
                return false;
            }
            return true;
        }
        return false;
    }

    private static CompletableFuture<?> settlement(Minecraft client, MahjongTableBlockEntity table, boolean end) {
        var id = client.player.getUUID();
        var pos = table.getBlockPos();
        return client.getSingleplayerServer().submit(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
            var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
            var game = serverTable.participantGame(player);
            require(game != null, "Settlement fixture has no participant");
            var saved = serverTable.saveWithoutMetadata(player.registryAccess());
            var envelope = com.google.gson.JsonParser.parseString(saved.getString("session")).getAsJsonObject();
            var json = envelope.getAsJsonObject("state");
            json.addProperty("phase", end ? "MATCH_END" : "HAND_END");
            json.addProperty("lifecycle", end ? "FINISHED" : "PLAYING");
            json.addProperty("age", 0);
            json.addProperty("revision", game.revision() + 100);
            json.addProperty("decision", game.view(id).decision() + 1);
            json.addProperty("result", "exhaustive");
            json.add("wins", TableNetworking.JSON.toJsonTree(List.of()));
            json.add("deltas", TableNetworking.JSON.toJsonTree(List.of(0, 0, 0, 0)));
            json.add("finalScores", TableNetworking.JSON.toJsonTree(end ? List.of(0.0, 0.0, 0.0, 0.0) : List.of()));
            json.add("finalRanks", TableNetworking.JSON.toJsonTree(end ? List.of(1, 2, 3, 4) : List.of()));
            saved.putString("session", envelope.toString());
            serverTable.loadWithComponents(saved, player.registryAccess());
            require(serverTable.participantGame(player).phase() == (end ? RiichiGame.Phase.MATCH_END : RiichiGame.Phase.HAND_END),
                "Saved settlement fixture did not load");
            serverTable.open(player);
        });
    }

    private void next(int value) { stage = value; ticks = 0; }
    private static void resize(Minecraft client, boolean small) {
        client.getWindow().setWindowed(small ? 960 : 1280, small ? 720 : 800);
        client.options.guiScale().set(small ? 3 : 2);
        client.resizeDisplay();
    }
    private static AbstractButton buttonOrNull(Minecraft client, String key, Object... arguments) {
        String text = Component.translatable(key, arguments).getString();
        return client.screen.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
            .filter(button -> button.getMessage().getString().equals(text)).findFirst().orElse(null);
    }
    private static void click(Minecraft client, String key, Object... arguments) {
        clickText(client, Component.translatable(key, arguments).getString());
    }
    private static void clickText(Minecraft client, String text) {
        var button = client.screen.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
            .filter(control -> control.getMessage().getString().equals(text)).findFirst().orElse(null);
        require(button != null && button.active, "Missing active lobby control: " + text);
        client.screen.mouseClicked(button.getX() + 4, button.getY() + 4, 0);
    }
    private static void check(Minecraft client) {
        AutomationControlsSmoke.checkBounds(client);
        if (client.screen instanceof TableScreen screen && screen.immersive()) return;
        for (var child : client.screen.children()) if (child instanceof AbstractButton button && button.visible)
            require(client.font.width(button.getMessage()) <= button.getWidth() - 12,
                "Truncated room control: " + button.getMessage().getString());
    }
    private static void capture(Minecraft client, Path output, String name) {
        SmokeScreenshots.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
