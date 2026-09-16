package top.skyeyefast.mchjong.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Development-only visual smoke: real integrated server, packets, world and UI. */
public final class TableClientSmoke {
    private static final Logger LOG = LoggerFactory.getLogger("mchjong-smoke");
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private final Path output = Path.of(System.getProperty("mchjong.smoke.output"));
    private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    private int step;
    private int ticks;
    private int entered;
    private boolean saved;
    private String patternedPack;
    private CompletableFuture<Void> resourceReload;
    private final SettlementSmoke settlementSmoke = new SettlementSmoke();
    private final AnimationSmoke animationSmoke = new AnimationSmoke();
    private final ReplaySmoke replaySmoke = new ReplaySmoke();
    private final TableControlSmoke controlSmoke = new TableControlSmoke();

    public void tick(Minecraft client) {
        try {
            ticks++;
            if (serverFailure.get() != null) throw new IllegalStateException("Server smoke failed", serverFailure.get());
            if (ticks > 4000) throw new IllegalStateException("Smoke timed out at step " + step + ", screen=" + client.screen);
            if (step == 0 && client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) {
                onboarding.onClose();
                return;
            }
            if (step == 0 && client.screen instanceof TitleScreen) {
                Files.createDirectories(output);
                Files.deleteIfExists(output.resolve("PASS.txt"));
                Files.deleteIfExists(output.resolve("FAIL.txt"));
                patternedPack = TileResourceSmoke.optionalPack(client);
                require(!client.getResourcePackRepository().getSelectedIds().contains(patternedPack),
                    "Patterned backs must be disabled by default");
                client.options.pauseOnLostFocus = false;
                client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                client.options.guiScale().set(2);
                client.options.renderDistance().set(5);
                client.options.simulationDistance().set(5);
                client.options.fov().set(70);
                client.resizeDisplay();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                client.createWorldOpenFlows().createFreshLevel("table-smoke-" + System.currentTimeMillis(),
                    new LevelSettings("MCjhong isolated smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(12345, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    new TitleScreen());
                step = 1;
                LOG.info("Created isolated smoke world");
            } else if (step == 1 && client.player != null && client.getSingleplayerServer() != null && client.level != null) {
                UUID id = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    try {
                        ServerPlayer player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                        if (player == null) throw new IllegalStateException("Missing server player");
                        var level = player.serverLevel();
                        level.setDayTime(6000);
                        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
                            level.setBlock(CENTER.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                        level.setBlock(CENTER, MahjongContent.TABLE.defaultBlockState(), 3);
                        MahjongContent.TABLE.setPlacedBy(level, CENTER, MahjongContent.TABLE.defaultBlockState(), player, new ItemStack(MahjongContent.TABLE_ITEM));
                        for (int seat = 0; seat < 4; seat++) level.setBlock(TableGeometry.stool(CENTER, seat), MahjongContent.STOOL.defaultBlockState(), 3);
                        player.teleportTo(level, 0.5, 64, 3.5, 180, 30);
                    } catch (Throwable failure) { serverFailure.set(failure); }
                });
                step = 2; entered = ticks;
            } else if (step == 2 && ticks - entered > 60 && client.level.getBlockEntity(CENTER) instanceof MahjongTableBlockEntity) {
                UUID id = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    try {
                        ServerPlayer player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                        var table = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(CENTER);
                        player.setShiftKeyDown(true);
                        table.interact(player);
                        require(!player.isPassenger(), "Crouch-click must spectate without seating");
                        player.setShiftKeyDown(false);
                        table.interact(player);
                        require(player.isPassenger(), "Table click did not seat the player on the nearest side");
                        var mount = player.getVehicle();
                        table.sit(player, 0);
                        require(player.getVehicle() == mount, "Reopening the stool created a duplicate mount");
                    } catch (Throwable failure) { serverFailure.set(failure); }
                });
                step = 3; entered = ticks;
            } else if (step == 3 && client.screen instanceof TableScreen && ticks - entered > 40) {
                require(client.player.isPassenger(), "Player did not mount the stool");
                capture(client, "01-lobby.png");
                for (var child : client.screen.children()) if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(
                    net.minecraft.network.chat.Component.translatable("ui.mchjong.practice_short").getString())) {
                    client.screen.mouseClicked(widget.getX()+8, widget.getY()+8, 0);
                    step = 4; entered = ticks;
                    return;
                }
                throw new IllegalStateException("Practice option missing from live lobby UI");
            } else if (step == 4 && ticks - entered > 40) {
                var table = (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER);
                if (!saved && table.clientView() != null && table.clientView().phase() == Game.Phase.LOBBY) {
                    for (var child : client.screen.children()) if (child instanceof AbstractWidget widget && widget.getMessage().getString().startsWith("Ready")) {
                        client.screen.mouseClicked(widget.getX()+8, widget.getY()+8, 0);
                        saved = true; entered = ticks;
                        return;
                    }
                }
                var view = table.clientView();
                if (view == null) return;
                if (top.skyeyefast.mchjong.client.TableAnimation.of(table).dealing(net.minecraft.Util.getMillis())) return;
                require(view.viewerSeat() == 0, "Private seat snapshot not delivered");
                // Initial dealership is randomized. Wait for the seated player's turn,
                // declining intervening calls through the actual UI rather than changing game state.
                if (view.phase() == Game.Phase.REACTION) {
                    for (var child : client.screen.children()) if (child instanceof AbstractWidget widget
                            && widget.getMessage().getString().equals("Pass") && widget.active) {
                        client.screen.mouseClicked(widget.getX()+8, widget.getY()+8, 0);
                        break;
                    }
                    return;
                }
                if (view.phase() != Game.Phase.TURN || view.turn() != view.viewerSeat()) return;
                require(view.seats().getFirst().hand().size() == 14, "Active player did not receive fourteen tiles");
                capture(client, "02-dealt-table.png");
                TableSettings.get().discardMode = TableSettings.DiscardMode.CONFIRM;
                client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
                step = 5; entered = ticks;
            } else if (step == 5 && ticks - entered > 15) {
                capture(client, "03-discard-confirm.png");
                for (var child : client.screen.children()) if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals("Discard")) {
                    client.screen.mouseClicked(widget.getX()+8, widget.getY()+8, 0);
                    client.screen.mouseClicked(widget.getX()+8, widget.getY()+8, 0);
                    client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                    step = 6; entered = ticks;
                    return;
                }
                throw new IllegalStateException("Confirm discard mode did not expose its standalone discard button");
            } else if (step == 6 && ticks - entered > 20) {
                var view = ((MahjongTableBlockEntity) client.level.getBlockEntity(CENTER)).clientView();
                require(view.seats().getFirst().river().size() == 1, "Discard confirmation did not reach the server");
                capture(client, "04-river.png");
                client.screen.onClose();
                client.player.setYRot(210); client.player.setXRot(35);
                step = 7; entered = ticks;
            } else if (step == 7 && ticks - entered > 20) {
                capture(client, "05-seated-world.png");
                TileResourceSmoke.verify(client, false);
                require(client.getResourcePackRepository().addPack(patternedPack), "Could not enable patterned backs");
                resourceReload = client.reloadResourcePacks();
                step = 8; entered = ticks;
            } else if (step == 8 && resourceReload.isDone() && client.getOverlay() == null && ticks - entered > 40) {
                resourceReload.join();
                TileResourceSmoke.verify(client, true);
                capture(client, "06-patterned-backs.png");
                require(client.getResourcePackRepository().removePack(patternedPack), "Could not disable patterned backs");
                resourceReload = client.reloadResourcePacks();
                step = 9; entered = ticks;
            } else if (step == 9 && resourceReload.isDone() && client.getOverlay() == null && ticks - entered > 40) {
                resourceReload.join();
                TileResourceSmoke.verify(client, false);
                capture(client, "07-restored-solid-backs.png");
                step = 15; entered = ticks;
            } else if (step == 15 && controlSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                step = 10; entered = ticks;
            } else if (step == 10 && settlementSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                step = 11; entered = ticks;
            } else if (step == 11 && animationSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                step = 12; entered = ticks;
            } else if (step == 12 && replaySmoke.tick(client, output)) {
                Files.writeString(output.resolve("PASS.txt"), "World placement, seating, private deal, standalone discard confirmation, river synchronization, HD texture filtering, optional back pack enable/disable, no-scroll multi-winner settlement, resize, collapse, keyboard navigation and rendered wall/deal/discard/pon/riichi/closed-kan transitions passed. Live control packets verified solo exit, complete seat release, rejoining, three/four-player preset selection and open hands. Hidden rivers retain the remaining wall count. Settlement and animation screenshots use display-only fixtures. Engine-generated replay archival, authorized command fetch, chunk reassembly, replay list, timeline keyboard seeking, resized replay UI, sound registry and Tenhou JSON export-button checks passed.\n");
                LOG.info("MCJHONG_CLIENT_SMOKE_PASS");
                entered = ticks;
                step = 13;
            } else if (step == 13 && ticks - entered > 30) {
                client.stop();
                step = 14;
            }
        } catch (Throwable failure) {
            LOG.error("MCJHONG_CLIENT_SMOKE_FAILED step={}", step, failure);
            try { Files.createDirectories(output); Files.writeString(output.resolve("FAIL.txt"), failure.toString()); }
            catch (Exception ignored) { LOG.error("Could not write smoke failure evidence"); }
            step = 14;
            client.stop();
        }
    }

    private void capture(Minecraft client, String name) {
        Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), message -> LOG.info("Screenshot: {}", message.getString()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
