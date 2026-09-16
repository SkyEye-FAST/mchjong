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
    private CompletableFuture<Void> resourceReload;
    private CompletableFuture<Boolean> survivalReady;
    private final SettlementSmoke settlementSmoke = new SettlementSmoke();
    private final AnimationSmoke animationSmoke = new AnimationSmoke();
    private final ReplaySmoke replaySmoke = new ReplaySmoke();
    private final TableControlSmoke controlSmoke = new TableControlSmoke();
    private final ManualTableSmoke manualSmoke = new ManualTableSmoke();
    private final ItemPresentationSmoke itemPresentationSmoke = new ItemPresentationSmoke();
    private final InterfaceSmoke interfaceSmoke = new InterfaceSmoke();
    private final BoxInterfaceSmoke boxInterfaceSmoke = new BoxInterfaceSmoke();

    public void tick(Minecraft client) {
        if (step == 14) return;
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
                client.options.pauseOnLostFocus = false;
                client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                client.options.guiScale().set(2);
                client.options.renderDistance().set(5);
                client.options.simulationDistance().set(5);
                client.options.fov().set(70);
                TableSettings.get().reset();
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
                if (survivalReady == null) {
                    var server = client.getSingleplayerServer();
                    survivalReady = server.submit(() -> SurvivalSmoke.ready(server.getPlayerList().getPlayer(id)));
                    return;
                }
                if (!survivalReady.isDone()) return;
                if (!survivalReady.join()) { survivalReady = null; return; }
                client.getSingleplayerServer().execute(() -> {
                    try {
                        ServerPlayer player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                        if (player == null) throw new IllegalStateException("Missing server player");
                        var level = player.serverLevel();
                        SurvivalSmoke.verify(player);
                        level.setDayTime(6000);
                        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
                            level.setBlock(CENTER.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                        level.setBlock(CENTER, MahjongContent.AUTO_TABLE.defaultBlockState(), 3);
                        ItemStack furniture = new ItemStack(MahjongContent.AUTO_TABLE_ITEM);
                        furniture.set(top.skyeyefast.mchjong.item.MahjongComponents.WOOD, top.skyeyefast.mchjong.item.FurnitureWood.CHERRY);
                        MahjongContent.AUTO_TABLE.setPlacedBy(level, CENTER, MahjongContent.AUTO_TABLE.defaultBlockState(), player, furniture);
                        var table = (MahjongTableBlockEntity) level.getBlockEntity(CENTER);
                        table.useEquipment(player, top.skyeyefast.mchjong.item.MahjongSupplies.completeBox(
                            top.skyeyefast.mchjong.item.TileMaterial.GLASS, net.minecraft.world.item.DyeColor.BLUE));
                        ItemStack cloth = new ItemStack(MahjongContent.CLOTH_ITEM);
                        cloth.set(net.minecraft.core.component.DataComponents.BASE_COLOR, net.minecraft.world.item.DyeColor.GREEN);
                        table.useEquipment(player, cloth);
                        player.getInventory().selected = 0;
                        player.getInventory().setItem(0, top.skyeyefast.mchjong.item.MahjongSupplies.completeBox(
                            top.skyeyefast.mchjong.item.TileMaterial.GLASS, net.minecraft.world.item.DyeColor.BLUE));
                        player.getInventory().setItem(1, new ItemStack(MahjongContent.TABLE_ITEM));
                        player.getInventory().setItem(2, furniture.copy());
                        player.getInventory().setItem(3, cloth.copy());
                        player.getInventory().setItem(4, top.skyeyefast.mchjong.item.MahjongSupplies.tile(
                            new top.skyeyefast.mchjong.item.TileData(4, top.skyeyefast.mchjong.item.TileMaterial.GLASS, true),
                            net.minecraft.world.item.DyeColor.BLUE, 1));
                        ItemStack sticks = new ItemStack(MahjongContent.POINT_STICK, 8);
                        sticks.set(top.skyeyefast.mchjong.item.MahjongComponents.POINTS, 1000);
                        player.getInventory().setItem(5, sticks);
                        player.getInventory().setItem(6, new ItemStack(MahjongContent.STOOL_ITEM));
                        player.getInventory().setChanged();
                        for (int seat = 0; seat < 4; seat++) level.setBlock(TableGeometry.stool(CENTER, seat), MahjongContent.STOOL.defaultBlockState(), 3);
                        player.teleportTo(level, 0.5, 64, 3.5, 180, 30);
                    } catch (Throwable failure) { serverFailure.set(failure); }
                });
                step = 2; entered = ticks;
            } else if (step == 2 && ticks - entered > 60 && client.level.getBlockEntity(CENTER) instanceof MahjongTableBlockEntity) {
                client.setScreen(new FurnitureGalleryScreen(false));
                step = 20; entered = ticks;
            } else if (step == 20 && ticks - entered > 20) {
                capture(client, "00-furniture-details.png");
                client.setScreen(new FurnitureGalleryScreen(true));
                step = 21; entered = ticks;
            } else if (step == 21 && ticks - entered > 20) {
                capture(client, "00-material-gallery.png");
                client.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
                step = 16; entered = ticks;
            } else if (step == 16 && ticks - entered > 15) {
                capture(client, "00-equipment-inventory.png");
                client.screen.onClose();
                UUID id = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                        var box = player.getMainHandItem();
                        require(box.is(MahjongContent.BOX_ITEM), "Box fixture was not synchronized into the main hand");
                        box.getItem().use(player.serverLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
                        require(player.containerMenu instanceof top.skyeyefast.mchjong.item.MahjongBoxMenu, "Box did not open its real menu");
                        var menu = player.containerMenu;
                        require(menu.slots.size() == 90, "Box must retain 54 storage and 36 player slots");
                        require(menu.quickMoveStack(player, 81).isEmpty(), "Shift-click moved the open carrier box");
                        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.SWAP, player);
                        require(player.getMainHandItem() == box, "Hotbar swap replaced the open box");
                        require(!menu.slots.getFirst().mayPlace(new ItemStack(MahjongContent.BOX_ITEM)), "Box accepts nested boxes");
                    } catch (Throwable failure) { serverFailure.set(failure); }
                });
                step = 17; entered = ticks;
            } else if (step == 17 && ticks - entered > 15 && client.screen instanceof top.skyeyefast.mchjong.client.MahjongBoxScreen) {
                if (!interfaceSmoke.box(client, output)) return;
                require(client.player.containerMenu.slots.size() == 90, "Client box slot layout differs from server");
                require(client.player.containerMenu instanceof top.skyeyefast.mchjong.item.MahjongBoxMenu menu
                    && menu.ownerSlot() == 0, "Client did not receive the server's carrier lock");
                capture(client, "00-physical-box.png");
                step = 22; entered = ticks;
            } else if (step == 22 && boxInterfaceSmoke.tick(client, output)) {
                client.screen.onClose();
                step = 18; entered = ticks;
            } else if (step == 18 && ticks - entered > 10) {
                if (!itemPresentationSmoke.tick(client, output)) return;
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
                    for (var child : client.screen.children()) if (child instanceof AbstractWidget widget && widget.active
                        && widget.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("action.mchjong.ready").getString())) {
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
                            && widget.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("action.mchjong.pass").getString()) && widget.active) {
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
                for (var child : client.screen.children()) if (child instanceof AbstractWidget widget
                    && widget.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("action.mchjong.discard").getString())) {
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
                TileResourceSmoke.verify(client);
                resourceReload = client.reloadResourcePacks();
                step = 8; entered = ticks;
            } else if (step == 8 && resourceReload.isDone() && client.getOverlay() == null && ticks - entered > 40) {
                resourceReload.join();
                TileResourceSmoke.verify(client);
                capture(client, "06-reloaded-solid-backs.png");
                step = 15; entered = ticks;
            } else if (step == 15 && interfaceSmoke.settings(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)
                && controlSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                step = 10; entered = ticks;
            } else if (step == 10 && settlementSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                step = 11; entered = ticks;
            } else if (step == 11 && animationSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                step = 12; entered = ticks;
            } else if (step == 12 && replaySmoke.tick(client, output)) {
                step = 19; entered = ticks;
            } else if (step == 19 && manualSmoke.tick(client, output)) {
                Files.writeString(output.resolve("survival-checks.txt"), "Real server menus: carrier lock, clicks, shift transfers, hotbar/offhand swaps, dragging, collection, invalidation and conservation. Native stonecutter: component cache invalidation, no re-engraving, preserved material/color and shift result conservation. Equipment: native placement, replacement, public/private updates, save/load, active locks, sanma full-set recovery, point-stick independence, root/placeholder destruction and explosions. Real ordinary-table client: shuffle, own wall, 4/4/4/1 packets, dealer and normal draws, discard, private hands, waiting without auto-handling, manual save/load and exit with exact box recovery.\n");
                Files.writeString(output.resolve("PASS.txt"), "World placement, seating, private deal, standalone discard confirmation, river synchronization, HD texture filtering and resource reload, no-scroll multi-winner settlement, resize, collapse, keyboard navigation and rendered wall/deal/discard/pon/riichi/closed-kan transitions passed. Live control packets verified solo exit, complete seat release, rejoining, three/four-player preset selection and open hands. Hidden rivers retain the remaining wall count. Settlement and animation screenshots use display-only fixtures. Engine-generated replay archival, authorized command fetch, chunk reassembly, replay list, timeline keyboard seeking, resized replay UI, sound registry and Tenhou JSON export-button checks passed.\n");
                LOG.info("MCJHONG_CLIENT_SMOKE_PASS");
                entered = ticks;
                step = 13;
            } else if (step == 13 && ticks - entered > 30) {
                client.stop();
                step = 14;
            }
        } catch (Throwable failure) {
            LOG.error("MCJHONG_CLIENT_SMOKE_FAILED step={}", step, failure);
            try {
                var trace = new java.io.StringWriter();
                failure.printStackTrace(new java.io.PrintWriter(trace));
                Files.createDirectories(output);
                Files.writeString(output.resolve("FAIL.txt"), trace.toString());
            }
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
