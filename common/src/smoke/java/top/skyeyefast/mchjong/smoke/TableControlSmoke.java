package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableAnimation;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.client.TableRulesScreen;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RedFives;
import top.skyeyefast.mchjong.engine.RuleOption;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.network.TableRulesPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Uses the live integrated server and real control packets before any display-only fixtures. */
final class TableControlSmoke {
    private static final int DEFAULT_TIMEOUT_TICKS = 400;
    private static final int GAME_START_TIMEOUT_TICKS = 800;
    private int stage, ticks;
    private boolean remainingHidden;
    private CompletableFuture<Void> reseated;
    private int originalWidth, originalHeight, originalScale;
    private static final String[] RULE_LANGUAGES = {"zh_cn", "zh_tw", "ja_jp", "en_us"};
    private int ruleLanguage;
    private String originalLanguage;
    private CompletableFuture<Void> languageReload;
    private top.skyeyefast.mchjong.world.WorldSettings.Policy originalWorldPolicy;
    private final RoomPreparationSmoke preparation = new RoomPreparationSmoke();

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        ticks++;
        int timeout = stage == 7 ? GAME_START_TIMEOUT_TICKS : DEFAULT_TIMEOUT_TICKS;
        if (ticks > timeout) throw new IllegalStateException("Table controls timed out at stage " + stage);
        if (reseated != null && reseated.isDone()) reseated.join();
        var view = table.clientView();
        var settings = TableSettings.get();
        if (stage == 0) {
            remainingHidden = !settings.show(TableSettings.Information.REMAINING);
            if (!remainingHidden) settings.toggle(TableSettings.Information.REMAINING);
            settings.showRiver = false;
            require(settings.show(TableSettings.Information.REMAINING), "Hidden river removed the remaining wall count");
            client.setScreen(new TableScreen(table.getBlockPos()));
            next(1);
        } else if (stage == 1 && ticks > 10) {
            capture(client, output, "23-hidden-river.png");
            click(client, "ui.mchjong.exit");
            next(2);
        } else if (stage == 2 && view.phase() == Game.Phase.LOBBY && view.viewerSeat() < 0 && !client.player.isPassenger()) {
            require(client.screen == null, "Exiting the table did not close its controls");
            require(view.seats().stream().noneMatch(seat -> seat.occupied()), "Exiting left a seat reserved");
            require(view.wall().isEmpty(), "Exiting left the old wall on the table");
            capture(client, output, "24-exited-table.png");
            UUID id = client.player.getUUID();
            var pos = table.getBlockPos();
            reseated = new CompletableFuture<>();
            client.getSingleplayerServer().execute(() -> {
                try {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                    ((MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos)).sit(player, 0);
                    reseated.complete(null);
                } catch (Throwable failure) { reseated.completeExceptionally(failure); }
            });
            next(3);
        } else if (stage == 3 && client.screen instanceof TableScreen && view.viewerSeat() == 0 && ticks > 10) {
            if (!view.rules().sanma()) click(client, "ui.mchjong.players.3");
            next(4);
        } else if (stage == 4 && view.rules().players() == 3) {
            require(view.rules().preset().players() == 3, "Four-player preset selected in the three-player lobby");
            capture(client, output, "25-three-player-lobby.png");
            AutomationControlsSmoke.checkOptions(client, 0);
            click(client, "ui.mchjong.players.4");
            next(5);
        } else if (stage == 5 && view.rules().players() == 4 && selectPreset(client, view, RuleSet.JPML_A)) {
            require(view.seats().stream().allMatch(seat -> seat.points() == 30000), "League A initial points");
            capture(client, output, "25a-league-a-lobby.png");
            next(8);
        } else if (stage == 8 && selectPreset(client, view, RuleSet.WRC)) {
            require((table.clientRedOptions() & 1) != 0, "WRC rejected the default no-red box");
            capture(client, output, "25b-wrc-lobby.png");
            next(9);
        } else if (stage == 9 && selectPreset(client, view, RuleSet.MAHJONG_SOUL_4)) {
            next(11);
        } else if (stage == 11 && view.rules().preset() == RuleSet.MAHJONG_SOUL_4 && ticks > 5) {
            click(client, "rules.mchjong.title");
            next(12);
        } else if (stage == 12 && client.screen instanceof TableRulesScreen && ticks > 5) {
            require(!widget(client, RedFives.THREE.translationKey()).active && !widget(client, RedFives.FOUR.translationKey()).active,
                "Unavailable red choices are enabled");
            require(client.screen.children().stream().noneMatch(EditBox.class::isInstance), "Preset options exposed fixed numeric editors");
            AutomationControlsSmoke.checkBounds(client);
            capture(client, output, "25c-preset-options.png");
            originalWidth = client.getWindow().getScreenWidth(); originalHeight = client.getWindow().getScreenHeight();
            originalScale = client.options.guiScale().get();
            originalLanguage = client.getLanguageManager().getSelected();
            selectRuleLanguage(client, RULE_LANGUAGES[ruleLanguage]);
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
            next(20);
        } else if (stage == 20 && ticks > 5 && languageReload.isDone() && client.getOverlay() == null) {
            languageReload.join();
            require(client.screen.width == 320 && client.screen.height == 240, "Preset options minimum viewport");
            AutomationControlsSmoke.checkBounds(client);
            var unavailable = widget(client, RedFives.THREE.translationKey());
            double scale = client.getWindow().getGuiScale();
            long window = client.getWindow().getWindow();
            // Drive the installed callback without moving or capturing the user's desktop cursor.
            var cursor = org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback(window, null);
            require(cursor != null, "Missing native cursor callback");
            try { cursor.invoke(window, (unavailable.getX() + 5) * scale, (unavailable.getY() + 5) * scale); }
            finally { org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback(window, cursor); }
            next(21);
        } else if (stage == 21 && ticks > 10) {
            require(widget(client, RedFives.THREE.translationKey()).isHovered(), "Disabled red choice was not hovered");
            capture(client, output, "25h-insufficient-reds-" + RULE_LANGUAGES[ruleLanguage] + "-320x240.png");
            var nextPage = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(">")).findFirst().orElseThrow();
            client.screen.mouseClicked(nextPage.getX() + 5, nextPage.getY() + 5, 0);
            click(client, "rules.mchjong.option.min_han.4");
            click(client, "rules.mchjong.option.match_length.1");
            next(29);
        } else if (stage == 29 && ticks > 5) {
            AutomationControlsSmoke.checkBounds(client);
            capture(client, output, "25l-match-options-" + RULE_LANGUAGES[ruleLanguage] + "-320x240.png");
            var previousPage = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals("<")).findFirst().orElseThrow();
            client.screen.mouseClicked(previousPage.getX() + 5, previousPage.getY() + 5, 0);
            if (++ruleLanguage < RULE_LANGUAGES.length) {
                selectRuleLanguage(client, RULE_LANGUAGES[ruleLanguage]);
                next(20);
                return false;
            }
            selectRuleLanguage(client, originalLanguage);
            client.getWindow().setWindowed(originalWidth, originalHeight);
            client.options.guiScale().set(originalScale);
            client.resizeDisplay();
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            reseated = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                var game = serverTable.participantGame(player);
                var original = game.rules();
                long decision = game.view(id).decision();
                serverTable.configureRules(player, new TableRulesPayload(pos, game.tableId(), decision,
                    original.with(RuleOption.RED_FIVES, RedFives.FOUR.ordinal())));
                require(game.rules().equals(original) && game.view(id).decision() == decision,
                    "Forged red selection bypassed physical stock checks");
                var box = serverTable.equipment().boxes().getItem(0).copy();
                var items = MahjongSupplies.contents(box);
                for (int suit = 0; suit < 3; suit++) {
                    int face = suit * 9 + 4;
                    var normal = items.stream().filter(stack -> !stack.isEmpty() && MahjongSupplies.tile(stack).face() == face)
                        .findFirst().orElseThrow();
                    var red = normal.copyWithCount(suit == 1 ? 2 : 1);
                    red.set(MahjongComponents.TILE, MahjongSupplies.tile(normal).engraved(face, true));
                    items.set(34 + suit, red);
                }
                box.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
                serverTable.equipment().boxes().setItem(0, box);
                require(MahjongSupplies.tileCount(MahjongSupplies.contents(box)) == 140, "Surplus fixture count");
            });
            next(22);
        } else if (stage == 22 && table.clientRedOptions() == 63 && ticks > 5 && languageReload.isDone() && client.getOverlay() == null) {
            languageReload.join();
            require(widget(client, RedFives.THREE.translationKey()).active, "Red stock change was not synchronized");
            click(client, RedFives.FOUR.translationKey());
            var label = Component.translatable("rules.mchjong.option.kuitan").getString();
            var kuitan = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().contains(label)).findFirst().orElseThrow();
            client.screen.mouseClicked(kuitan.getX() + 5, kuitan.getY() + 5, 0);
            capture(client, output, "25i-red-stock-options.png");
            click(client, "rules.mchjong.apply");
            next(23);
        } else if (stage == 23 && client.screen instanceof TableScreen && view.rules().redFives() == RedFives.FOUR && ticks > 5) {
            require(!view.rules().custom() && !view.rules().kuitan(), "Preset variants were classified as custom");
            require(view.rules().minHan() == 4 && view.rules().matchLength() == 1, "Match options were not acknowledged");
            require((table.clientRedOptions() & 1) != 0, "Surplus box cannot start play");
            click(client, "rules.mchjong.title");
            click(client, "rules.mchjong.mode.details");
            click(client, "rules.mchjong.group.scoring");
            next(25);
        } else if (stage == 25 && ticks > 5) {
            require(client.screen.children().stream().noneMatch(EditBox.class::isInstance), "Rule details exposed numeric editors");
            AutomationControlsSmoke.checkBounds(client);
            capture(client, output, "25j-rule-details.png");
            click(client, "rules.mchjong.mode.custom");
            click(client, "rules.mchjong.group.points");
            next(24);
        } else if (stage == 24 && client.screen instanceof TableRulesScreen && ticks > 5) {
            var start = field(client, "rules.mchjong.option.starting_points");
            start.setValue("32101");
            client.screen.tick();
            require(!widget(client, "rules.mchjong.apply").active, "Invalid point increment could be applied");
            start.setValue("32100");
            field(client, "rules.mchjong.option.return_points").setValue("33300");
            AutomationControlsSmoke.checkBounds(client);
            next(13);
        } else if (stage == 13 && ticks > 5) {
            capture(client, output, "25d-custom-points.png");
            click(client, "rules.mchjong.group.scoring");
            next(14);
        } else if (stage == 14 && ticks > 5) {
            String label = Component.translatable("rules.mchjong.option.ippatsu").getString();
            var toggle = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().contains(label)).findFirst().orElseThrow();
            client.screen.mouseClicked(toggle.getX() + 5, toggle.getY() + 5, 0);
            originalWidth = client.getWindow().getScreenWidth(); originalHeight = client.getWindow().getScreenHeight();
            originalScale = client.options.guiScale().get();
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
            next(15);
        } else if (stage == 15 && ticks > 5) {
            require(client.screen.width == 320 && client.screen.height == 240, "Custom rules minimum viewport");
            AutomationControlsSmoke.checkBounds(client);
            capture(client, output, "25e-custom-scoring-320x240.png");
            client.getWindow().setWindowed(originalWidth, originalHeight);
            client.options.guiScale().set(originalScale);
            client.resizeDisplay();
            next(16);
        } else if (stage == 16 && ticks > 5) {
            capture(client, output, "25f-custom-scoring.png");
            click(client, "rules.mchjong.group.flow");
            String label = Component.translatable("rules.mchjong.option.bankruptcy").getString();
            var bankruptcy = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().contains(label)).findFirst().orElseThrow();
            client.screen.mouseClicked(bankruptcy.getX() + 5, bankruptcy.getY() + 5, 0);
            click(client, "rules.mchjong.apply");
            next(17);
        } else if (stage == 17 && client.screen instanceof TableScreen && view.rules().custom() && ticks > 5) {
            require(view.rules().startingPoints() == 32100 && view.rules().returnPoints() == 33300 && !view.rules().ippatsu(),
                "Custom rule proposal was not synchronized");
            require(!view.rules().bankruptcy(), "Custom bankruptcy setting was not acknowledged");
            require(view.seats().stream().allMatch(seat -> seat.points() == 32100), "Custom starting points not applied");
            capture(client, output, "25g-custom-lobby.png");
            click(client, "rules.mchjong.title");
            next(18);
        } else if (stage == 18 && client.screen instanceof TableRulesScreen && ticks > 5) {
            field(client, "rules.mchjong.option.starting_points").setValue("40000");
            click(client, "gui.cancel");
            next(19);
        } else if (stage == 19 && client.screen instanceof TableScreen && ticks > 5) {
            require(view.rules().startingPoints() == 32100, "Cancel changed server rules");
            click(client, "settings.mchjong.scopes");
            click(client, "settings.mchjong.scope.world");
            next(26);
        } else if (stage == 26 && ticks > 10) {
            String label = Component.translatable("settings.mchjong.invitation_teleport").getString();
            var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().contains(label)).findFirst().orElseThrow();
            require(button.active, "Administrator cannot edit invitation teleport in the World tab");
            AutomationControlsSmoke.checkBounds(client);
            capture(client, output, "25k-world-settings.png");
            client.screen.onClose();
            var id = client.player.getUUID();
            var pos = table.getBlockPos();
            reseated = client.getSingleplayerServer().submit(() -> {
                var server = client.getSingleplayerServer();
                var player = server.getPlayerList().getPlayer(id);
                var commands = server.getCommands().getDispatcher();
                try {
                    var policy = top.skyeyefast.mchjong.world.WorldSettings.of(server);
                    var before = policy.policy();
                    originalWorldPolicy = before;
                    var low = commands.parse("mchjong world invitationTeleport true", player.createCommandSourceStack().withPermission(0));
                    try { commands.execute(low); throw new IllegalStateException("Non-admin changed world settings"); }
                    catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { /* Permission denied. */ }
                    require(before.equals(policy.policy()), "Denied command mutated world settings");
                    commands.execute("mchjong world invitationTeleport true", server.createCommandSourceStack());
                    commands.execute("mchjong world reload", server.createCommandSourceStack());
                    require(policy.policy().invitationTeleport(), "World policy did not persist");
                    var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                    InvitationSmoke.verify(player, serverTable);
                    var game = serverTable.participantGame(player);
                    require(game.configureHandVisibility(id, game.view(id).decision(), top.skyeyefast.mchjong.engine.HandVisibility.OPEN),
                        "Host cannot configure room hand visibility");
                    require(game.configureClock(id, game.view(id).timeControl()), "Cannot configure room clock");
                    require(game.view(id).handVisibility() == top.skyeyefast.mchjong.engine.HandVisibility.OPEN
                        && game.roomView().invitationTeleport(), "Room setting replaced world policy");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | java.io.IOException failure) {
                    throw new IllegalStateException(failure);
                }
            });
            next(6);
        } else if (stage == 6 && view.handVisibility() == top.skyeyefast.mchjong.engine.HandVisibility.OPEN) {
            click(client, "room.mchjong.start_bots");
            next(28);
        } else if (stage == 28) {
            if (preparation.tick(client, table, output, "26-room")) next(7);
        } else if (stage == 7 && view.phase() == Game.Phase.TURN && ticks > 60
            && !TableAnimation.of(table).dealing(net.minecraft.Util.getMillis())) {
            require(view.seats().stream().flatMap(seat -> seat.hand().stream()).allMatch(tile -> tile >= 0),
                "Server did not deliver the agreed open hands to the seated player");
            capture(client, output, "26-open-hands.png");
            settings.showRiver = true;
            if (!remainingHidden) settings.toggle(TableSettings.Information.REMAINING);
            reseated = client.getSingleplayerServer().submit(() -> {
                try {
                    var policy = top.skyeyefast.mchjong.world.WorldSettings.of(client.getSingleplayerServer());
                    policy.set("invitationTeleport", originalWorldPolicy.invitationTeleport());
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
            next(27);
        } else if (stage == 27 && reseated.isDone() && table.clientRoom().invitationTeleport() == originalWorldPolicy.invitationTeleport()) {
            return true;
        }
        return false;
    }

    private void selectRuleLanguage(Minecraft client, String language) {
        client.getLanguageManager().setSelected(language);
        languageReload = client.reloadResourcePacks();
    }
    private void next(int value) { stage = value; ticks = 0; }
    private boolean selectPreset(Minecraft client, top.skyeyefast.mchjong.engine.TableView view, RuleSet target) {
        require(view.rules().preset() != RuleSet.M_LEAGUE, "Preset cycle selected unavailable red fives");
        require(view.rules().preset().players() == target.players(), "Preset cycle changed player count");
        if (ticks < 10) return false;
        if (view.rules().preset() == target) return true;
        if (ticks % 10 == 0) {
            String label = Component.translatable("rules.mchjong.preset",
                Component.translatable(view.rules().preset().presetKey())).getString();
            var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
            require(button.active, "Host preset cycle is disabled");
            client.screen.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
        }
        return false;
    }
    private static EditBox field(Minecraft client, String key) { return (EditBox) widget(client, key); }
    private static AbstractWidget widget(Minecraft client, String key) {
        String label = Component.translatable(key).getString();
        return client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static void click(Minecraft client, String key) {
        String label = Component.translatable(key).getString();
        var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
        require(button.active, "Disabled control: " + key);
        client.screen.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
    }
    private static void capture(Minecraft client, Path output, String file) {
        Screenshot.grab(output.toFile(), file, client.getMainRenderTarget(), ignored -> {});
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
