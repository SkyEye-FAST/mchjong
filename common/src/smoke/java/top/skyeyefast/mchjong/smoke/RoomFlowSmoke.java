package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.PlayerHandVisibility;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Real room controls and server timers, with a saved end-of-hand fixture for bounded runtime. */
final class RoomFlowSmoke {
    private static final String[] LANGUAGES = {"en_us", "ja_jp", "zh_cn", "zh_tw"};
    private final RoomPreparationSmoke preparation = new RoomPreparationSmoke();
    private final RoomPreparationSmoke reassignment = new RoomPreparationSmoke(2);
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
        var room = table.clientRoom();
        var configuration = table.clientRiichiSettings();
        if (room == null || configuration == null) return false;
        if (stage == 0) {
            client.getLanguageManager().setSelected(LANGUAGES[locale]);
            work = client.reloadResourcePacks();
            next(1);
        } else if (stage == 1) {
            resize(client, false);
            client.setScreen(new RiichiTableScreen(table.getBlockPos()));
            next(2);
        } else if (stage == 2 && ticks > 10) {
            check(client);
            capture(client, output, "lobby-" + LANGUAGES[locale] + ".png");
            resize(client, true);
            next(3);
        } else if (stage == 3 && ticks > 10) {
            check(client);
            capture(client, output, "lobby-" + LANGUAGES[locale] + "-small.png");
            if (!room.equipmentProblems().isEmpty()) {
                var help = buttonOrNull(client, room.equipmentProblems().getFirst().translationKey());
                require(help != null && help.active, "Missing preparation explanation");
                client.screen.setFocused(help);
                client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
                require(client.screen instanceof top.skyeyefast.mchjong.client.TableHelpScreen, "Keyboard equipment help failed");
                check(client);
                client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0));
            }
            for (var key : List.of("rules.mchjong.title", "ui.mchjong.clock_settings", "ui.mchjong.invite", "settings.mchjong.scopes")) {
                click(client, key);
                require(!(client.screen instanceof RiichiTableScreen), "Room shortcut failed: " + key);
                AutomationControlsSmoke.checkBounds(client);
                if (client.screen instanceof top.skyeyefast.mchjong.client.RiichiRulesScreen) {
                    RuleExplanationSmoke.check(client, Component.translatable(top.skyeyefast.mchjong.engine.RiichiRuleOption.KUITAN.translationKey()));
                }
                client.screen.onClose();
                require(client.screen instanceof RiichiTableScreen, "Room shortcut lost its parent: " + key);
            }
            if (++locale < LANGUAGES.length) next(0);
            else {
                click(client, "settings.mchjong.hand_visibility", Component.translatable("settings.mchjong.hand_visibility.self"));
                next(4);
            }
        } else if (stage == 4 && configuration.playerHandVisibility() == PlayerHandVisibility.RIICHI) {
            originalPreset = configuration.rules().preset();
            clickText(client, Component.translatable("rules.mchjong.preset", Component.translatable(originalPreset.presetKey())).getString());
            var nextPreset = java.util.Arrays.stream(top.skyeyefast.mchjong.engine.RiichiPreset.values())
                .filter(preset -> preset.players() == originalPreset.players() && preset != originalPreset).findFirst().orElseThrow();
            click(client, nextPreset.presetKey());
            click(client, "rules.mchjong.apply");
            next(5);
        } else if (stage == 5 && configuration.rules().preset() != originalPreset && client.screen instanceof RiichiTableScreen) {
            click(client, "ui.mchjong.players.3");
            next(6);
        } else if (stage == 6 && configuration.rules().players() == 3) {
            check(client);
            capture(client, output, "lobby-three-small.png");
            click(client, "ui.mchjong.players.4");
            next(7);
        } else if (stage == 7 && configuration.rules().players() == 4) {
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            var rules = configuration.rules().preset();
            work = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var serverTable = (MahjongTableBlockEntity) player.level().getBlockEntity(pos);
                serverTable.equipment().boxes().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
                var game = serverTable.participantSession(player);
                game.configureRules(id, game.roomView(id).decision(), rules.config());
            });
            resize(client, false);
            next(8);
        } else if (stage == 8 && ticks > 10 && table.clientRoom().actions().stream().anyMatch(action -> action.type()
            == top.skyeyefast.mchjong.engine.RoomAction.Type.FILL_BOTS)) {
            click(client, "action.mchjong.fill_bots");
            next(9);
        } else if (stage == 9 && room.seats().stream().allMatch(seat -> seat.participant().id() != null)) {
            require(table.clientRoom().actions().stream().noneMatch(action -> action.type()
                == top.skyeyefast.mchjong.engine.RoomAction.Type.BEGIN_SEATING), "Empty box allowed seat confirmation");
            require(!room.equipmentProblems().isEmpty(), "Lobby omitted server equipment reasons");
            var blocked = buttonOrNull(client, room.equipmentProblems().getFirst().translationKey());
            require(blocked != null && blocked.active, "Lobby did not expose equipment help");
            blocked.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
            require(client.screen instanceof top.skyeyefast.mchjong.client.TableHelpScreen, "Equipment help did not open");
            next(90);
        } else if (stage == 90 && ticks > 10) {
            capture(client, output, "equipment-help.png");
            client.screen.onClose();
            require(client.screen instanceof RiichiTableScreen, "Equipment help did not return to lobby");
            check(client);
            capture(client, output, "equipment-needed.png");
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            var rules = configuration.rules().preset();
            work = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var serverTable = (MahjongTableBlockEntity) player.level().getBlockEntity(pos);
                serverTable.equipment().boxes().setItem(0, top.skyeyefast.mchjong.item.MahjongSupplies.stockedBox(rules.defaultRedFives()));
            });
            next(10);
        } else if (stage == 10 && preparation.tick(client, table, output, "room")) {
            hand = table.clientView().handNumber();
            work = settlement(client, table, false);
            next(11);
        } else if (stage == 11 && view != null && view.phase() == RiichiView.Phase.HAND_END) {
            require(table.clientView().settlementTicks() > 0 && table.clientView().settlementTicks() <= RiichiGame.SETTLEMENT_TICKS,
                "Wrong hand settlement duration");
            if (!capturedHand && ticks > 10) {
                check(client);
                capture(client, output, "hand-countdown.png");
                capturedHand = true;
            }
        } else if (stage == 11 && capturedHand && view != null && view.phase() == RiichiView.Phase.TURN && view.handNumber() > hand) {
            work = settlement(client, table, true);
            capturedHand = false;
            next(12);
        } else if (stage == 12 && view != null && view.phase() == RiichiView.Phase.MATCH_END) {
            int remaining = table.clientView().settlementTicks();
            require(buttonOrNull(client, "room.mchjong.dissolve") == null && buttonOrNull(client, "ui.mchjong.exit") == null,
                "Settlement exposes room termination");
            if (!capturedHand && remaining > RiichiGame.SETTLEMENT_TICKS && ticks > 10) {
                check(client);
                capture(client, output, "match-hand-countdown.png");
                InputSmoke.switchView(client.screen);
                capturedHand = true;
            } else if (capturedHand && !capturedImmersive && remaining > RiichiGame.SETTLEMENT_TICKS && ticks > 25) {
                require(((RiichiTableScreen) client.screen).immersive(), "Settlement cannot enter immersive view");
                check(client);
                capture(client, output, "match-hand-immersive.png");
                InputSmoke.switchView(client.screen);
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
        } else if ((stage == 12 || stage == 13) && room.lobby()
            && buttonOrNull(client, "action.mchjong.leave_room") != null) {
            require(capturedFinal, "Final standings were skipped");
            require(room.viewerSeat() >= 0 && room.seats().stream().filter(seat -> seat.participant().id() != null).count() == 4,
                "Returning to lobby lost the room roster");
            require(room.host() == room.viewerSeat(), "Returning to lobby lost its host");
            check(client);
            capture(client, output, "returned-lobby.png");
            click(client, "action.mchjong.leave_room");
            next(14);
        } else if (stage == 14 && room.viewerSeat() < 0) {
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            work = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var serverTable = (MahjongTableBlockEntity) player.level().getBlockEntity(pos);
                serverTable.sit(player, 0);
            });
            next(15);
        } else if (stage == 15 && room.viewerSeat() >= 0) {
            click(client, "room.mchjong.dissolve");
            next(16);
        } else if (stage == 16 && room.seats().stream().noneMatch(seat -> seat.participant().id() != null)) {
            if (client.player.isPassenger()) {
                require(ticks < 40, "Dissolved room retained the physical seat");
                return false;
            }
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            work = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                ((MahjongTableBlockEntity) player.level().getBlockEntity(pos)).sit(player, 0);
            });
            next(17);
        } else if (stage == 17 && room.viewerSeat() == 0) {
            client.setScreen(new RiichiTableScreen(table.getBlockPos()));
            next(18);
        } else if (stage == 18) {
            if (room.seating() == top.skyeyefast.mchjong.engine.RoomSeating.Stage.POSITIONING) {
                // Automatic tables assign a random wind without exposing a wind draw.
                require(room.viewerSeat() >= 0, "Seat assignment lost the recipient");
                if (!table.automatic()) require(room.viewerSeat() == 2, "West draw did not change the recipient seat");
                if (room.seats().get(room.viewerSeat()).presence() == top.skyeyefast.mchjong.engine.PlayerPresence.SEATED) {
                    require(client.player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat
                        && seat.seat() == room.viewerSeat() && seat.tablePos().equals(table.getBlockPos()),
                        "Assigned-seat preparation did not mount the matching stool");
                    reassignment.restoreSettings();
                    capture(client, output, "room-reassigned-seated.png");
                    click(client, "room.mchjong.dissolve");
                    next(19);
                    return false;
                }
            }
            reassignment.tick(client, table, output, "room-reassigned");
        } else if (stage == 19 && room.seats().stream().noneMatch(seat -> seat.participant().id() != null)
            && !client.player.isPassenger()) {
            return true;
        }
        return false;
    }

    private static CompletableFuture<?> settlement(Minecraft client, MahjongTableBlockEntity table, boolean end) {
        var id = client.player.getUUID();
        var pos = table.getBlockPos();
        return client.getSingleplayerServer().submit(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
            var serverTable = (MahjongTableBlockEntity) player.level().getBlockEntity(pos);
            var game = serverTable.participantSession(player);
            require(game != null, "Settlement fixture has no participant");
            var saved = serverTable.saveWithoutMetadata(player.registryAccess());
            var envelope = com.google.gson.JsonParser.parseString(new String(saved.getByteArray("session").orElseThrow(),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var state = envelope.getAsJsonObject("state");
            var room = state.getAsJsonObject("room");
            var match = state.getAsJsonObject("game");
            match.addProperty("phase", end ? "MATCH_END" : "HAND_END");
            room.addProperty("lifecycle", end ? "FINISHED" : "PLAYING");
            match.addProperty("age", 0);
            room.addProperty("revision", game.revision() + 100);
            room.addProperty("decision", game.view(id).decision() + 1);
            match.addProperty("result", "exhaustive");
            match.add("wins", TableNetworking.JSON.toJsonTree(List.of()));
            match.add("deltas", TableNetworking.JSON.toJsonTree(List.of(0, 0, 0, 0)));
            match.add("finalScores", TableNetworking.JSON.toJsonTree(end ? List.of(0.0, 0.0, 0.0, 0.0) : List.of()));
            match.add("finalRanks", TableNetworking.JSON.toJsonTree(end ? List.of(1, 2, 3, 4) : List.of()));
            saved.putByteArray("session", envelope.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            serverTable.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), saved));
            require(serverTable.participantSession(player).view(id).phase() == (end ? RiichiView.Phase.MATCH_END : RiichiView.Phase.HAND_END),
                "Saved settlement fixture did not load");
            serverTable.open(player);
        });
    }

    private void next(int value) { stage = value; ticks = 0; }
    private static void resize(Minecraft client, boolean small) {
        client.getWindow().setWindowed(small ? 960 : 1280, small ? 720 : 800);
        client.options.guiScale().set(small ? 3 : 2);
        client.resizeGui();
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
        var button = LobbySmoke.find(client, text);
        require(button != null && button.active, "Missing active lobby control: " + text);
        client.screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(button.getX() + 4, button.getY() + 4, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
    }
    private static void check(Minecraft client) {
        AutomationControlsSmoke.checkBounds(client);
        if (client.screen instanceof RiichiTableScreen screen && screen.immersive()) return;
        for (var child : client.screen.children()) if (child instanceof AbstractButton button && button.visible)
            require(client.font.width(button.getMessage()) <= button.getWidth() - 12 || ((top.skyeyefast.mchjong.smoke.mixin.SmokeWidgetTooltipAccessor) button).mchjong$tooltip().get() != null,
                "Truncated room control has no full label: " + button.getMessage().getString());
    }
    private static void capture(Minecraft client, Path output, String name) {
        SmokeScreenshots.grab(output.toFile(), name, client.getMainRenderTarget(), 1, ignored -> {});
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
