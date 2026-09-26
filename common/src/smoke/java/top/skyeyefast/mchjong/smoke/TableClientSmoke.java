package top.skyeyefast.mchjong.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
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
    private final boolean itemsOnly = Boolean.getBoolean("mchjong.smoke.itemsOnly");
    private final boolean paletteOnly = Boolean.getBoolean("mchjong.smoke.paletteOnly");
    private final boolean seatingOnly = Boolean.getBoolean("mchjong.smoke.seatingOnly");
    private final boolean interfaceOnly = Boolean.getBoolean("mchjong.smoke.interfaceOnly");
    private final boolean visibilityOnly = Boolean.getBoolean("mchjong.smoke.visibilityOnly");
    private final boolean roomOnly = Boolean.getBoolean("mchjong.smoke.roomOnly");
    private final boolean settlementOnly = Boolean.getBoolean("mchjong.smoke.settlementOnly");
    private final boolean manualOnly = Boolean.getBoolean("mchjong.smoke.manualOnly");
    private final boolean browserOnly = Boolean.getBoolean("mchjong.smoke.browserOnly");
    private final boolean maidOnly = Boolean.getBoolean("mchjong.smoke.maid");
    private final MaidIntegrationSmoke maidSmoke = maidOnly ? new MaidIntegrationSmoke() : null;
    private final boolean visualOnly = itemsOnly || paletteOnly || seatingOnly || interfaceOnly || visibilityOnly || roomOnly || manualOnly || maidOnly || settlementOnly || browserOnly;
    private final RoomFlowSmoke roomSmoke = new RoomFlowSmoke();
    private final HandVisibilitySmoke visibilitySmoke = new HandVisibilitySmoke();
    private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    private int step;
    private int ticks;
    private int entered;
    private final RoomPreparationSmoke preparation = new RoomPreparationSmoke();
    private CompletableFuture<Void> resourceReload;
    private CompletableFuture<Boolean> survivalReady;
    private CompletableFuture<Boolean> fixtureSeat;
    private final SettlementSmoke settlementSmoke = new SettlementSmoke();
    private final AnimationSmoke animationSmoke = new AnimationSmoke(seatingOnly);
    private final CameraSmoke cameraSmoke = new CameraSmoke();
    private final TenpaiHintsSmoke hintsSmoke = new TenpaiHintsSmoke();
    private final ReplaySmoke replaySmoke = new ReplaySmoke();
    private final TableControlSmoke controlSmoke = new TableControlSmoke();
    private final ManualTableSmoke manualSmoke = new ManualTableSmoke();
    private final ItemPresentationSmoke itemPresentationSmoke = new ItemPresentationSmoke();
    private final InterfaceSmoke interfaceSmoke = new InterfaceSmoke();
    private final BoxInterfaceSmoke boxInterfaceSmoke = new BoxInterfaceSmoke();
    private final TableInterfaceSmoke tableInterfaceSmoke = new TableInterfaceSmoke();
    private final ResourcePackSmoke resourcePackSmoke = new ResourcePackSmoke();
    private final BrowserSmoke browserSmoke = new BrowserSmoke();
    private final StoolInteractionSmoke stoolInteractionSmoke = new StoolInteractionSmoke();

    public void tick(Minecraft client) {
        if (step == 14) return;
        try {
            if (step == 100) {
                if (maidSmoke.tick(client, CENTER, output)) {
                    Files.writeString(output.resolve("PASS.txt"), "Maid task discovery, seating, saved binding, legal play and cleanup passed.\n");
                    LOG.info("MCHJONG_MAID_SMOKE_PASS");
                    step = 13; entered = ticks;
                }
                return;
            }
            require(!client.mouseHandler.isMouseGrabbed(), "Smoke client grabbed the desktop mouse");
            require(org.lwjgl.glfw.GLFW.glfwGetInputMode(client.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR)
                == org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL, "Smoke client confined or hid the desktop cursor");
            ticks++;
            if (serverFailure.get() != null) throw new IllegalStateException("Server smoke failed", serverFailure.get());
            // Presence checks wait through real server grace periods in each automatic room.
            if (ticks > 6600)
                throw new IllegalStateException("Smoke timed out at step " + step + ", screen=" + client.screen);
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
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                TableSettings.get().reset();
                client.resizeGui();
                client.createWorldOpenFlows().createFreshLevel("table-smoke-" + System.currentTimeMillis(),
                    new LevelSettings("MChjong isolated smoke", GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                        true, WorldDataConfiguration.DEFAULT), new WorldOptions(12345, false, false),
                    access -> access.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    new TitleScreen());
                step = 1;
                LOG.info("Created isolated smoke world");
            } else if (step == 1 && client.player != null && client.getSingleplayerServer() != null && client.level != null) {
                if (manualOnly) { step = 19; entered = ticks; return; }
                if (browserOnly) { step = 25; entered = ticks; return; }
                UUID id = client.player.getUUID();
                if (!visualOnly && survivalReady == null) {
                    var server = client.getSingleplayerServer();
                    survivalReady = server.submit(() -> SurvivalSmoke.ready(server.getPlayerList().getPlayer(id)));
                    return;
                }
                if (!visualOnly && !survivalReady.isDone()) return;
                if (!visualOnly && !survivalReady.join()) { survivalReady = null; return; }
                client.getSingleplayerServer().execute(() -> {
                    try {
                        ServerPlayer player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                        if (player == null) throw new IllegalStateException("Missing server player");
                        var level = player.level();
                        if (!visualOnly) {
                            SurvivalSmoke.verify(player);
                            RecipeBrowserDataSmoke.verify(level);
                        }
                        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
                            level.setBlock(CENTER.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                        level.setBlock(CENTER, MahjongContent.AUTO_TABLE.defaultBlockState(), 3);
                        ItemStack furniture = new ItemStack(MahjongContent.AUTO_TABLE_ITEM);
                        furniture.set(top.skyeyefast.mchjong.item.MahjongComponents.WOOD, top.skyeyefast.mchjong.item.FurnitureWood.CHERRY);
                        MahjongContent.AUTO_TABLE.setPlacedBy(level, CENTER, MahjongContent.AUTO_TABLE.defaultBlockState(), player, furniture);
                        var table = (MahjongTableBlockEntity) level.getBlockEntity(CENTER);
                        table.equipment().boxes().setItem(0, top.skyeyefast.mchjong.item.MahjongSupplies.engrave(
                            top.skyeyefast.mchjong.item.MahjongSupplies.completeBox(top.skyeyefast.mchjong.item.TileMaterial.GLASS,
                                net.minecraft.world.item.DyeColor.BLUE), top.skyeyefast.mchjong.item.TileFacePreset.KANTO));
                        ItemStack cloth = new ItemStack(MahjongContent.CLOTH_ITEM);
                        cloth.set(net.minecraft.core.component.DataComponents.BASE_COLOR, net.minecraft.world.item.DyeColor.CYAN);
                        table.useEquipment(player, cloth);
                        player.getInventory().setSelectedSlot(0);
                        player.getInventory().setItem(0, top.skyeyefast.mchjong.item.MahjongSupplies.completeBox(
                            top.skyeyefast.mchjong.item.TileMaterial.GLASS, net.minecraft.world.item.DyeColor.BLUE));
                        var flowerBox = player.getInventory().getItem(0);
                        var flowerContents = top.skyeyefast.mchjong.item.MahjongSupplies.contents(flowerBox);
                        for (int flower = 0; flower < 8; flower++) flowerContents.set(37 + flower,
                            top.skyeyefast.mchjong.item.MahjongSupplies.tile(new top.skyeyefast.mchjong.item.TileData(
                                34 + flower, top.skyeyefast.mchjong.item.TileMaterial.GLASS, false), net.minecraft.world.item.DyeColor.BLUE, 1));
                        flowerContents.set(top.skyeyefast.mchjong.item.MahjongSupplies.DYE_SLOT,
                            new ItemStack(MahjongContent.CREATIVE_MAHJONG_DYE));
                        flowerBox.set(net.minecraft.core.component.DataComponents.CONTAINER,
                            net.minecraft.world.item.component.ItemContainerContents.fromItems(flowerContents));
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
                        player.getInventory().setItem(7, top.skyeyefast.mchjong.item.MahjongSupplies.tile(
                            new top.skyeyefast.mchjong.item.TileData(41, top.skyeyefast.mchjong.item.TileMaterial.GLASS, false),
                            net.minecraft.world.item.DyeColor.BLUE, 1));
                        player.getInventory().setItem(8, ItemStack.EMPTY);
                        player.getInventory().setChanged();
                        for (int seat = 0; seat < 4; seat++) level.setBlock(TableGeometry.stool(CENTER, seat), MahjongContent.STOOL.defaultBlockState(), 3);
                        player.teleportTo(level, 0.5, 64, 3.5, java.util.Set.of(), 180, 30, false);
                    } catch (Throwable failure) { serverFailure.set(failure); }
                });
                step = 2; entered = ticks;
            } else if (step == 2 && ticks - entered > 60 && client.level.getBlockEntity(CENTER) instanceof MahjongTableBlockEntity) {
                if (maidOnly) {
                    var id = client.player.getUUID();
                    client.getSingleplayerServer().execute(() -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                        ((MahjongTableBlockEntity) player.level().getBlockEntity(CENTER)).sit(player, 0);
                    });
                    step = 100; entered = ticks;
                    return;
                }
                require(((MahjongTableBlockEntity) client.level.getBlockEntity(CENTER)).equipment().preset()
                    .equals(top.skyeyefast.mchjong.item.TileFacePreset.KANTO), "Client table lost its synchronized face preset");
                if (manualOnly) { step = 19; entered = ticks; return; }
                if (paletteOnly) {
                    client.setScreen(new MaterialPaletteSmoke());
                    step = 31; entered = ticks;
                    return;
                }
                if (itemsOnly) {
                    client.setScreen(null);
                    step = 18; entered = ticks;
                    return;
                }
                if (seatingOnly || visibilityOnly || roomOnly || maidOnly || settlementOnly) {
                    step = 24; entered = ticks;
                    return;
                }
                client.setScreen(new MaterialPaletteSmoke());
                step = 31; entered = ticks;
            } else if (step == 31 && ticks - entered > 15) {
                capture(client, "00-material-palette.png");
                client.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
                step = 16; entered = ticks;
            } else if (step == 16 && ticks - entered > 15) {
                capture(client, "00-equipment-inventory.png");
                if (paletteOnly) {
                    Files.writeString(output.resolve("PASS.txt"), "All sixteen printed and blank tile materials and native inventory items rendered.\n");
                    LOG.info("MCHJONG_PALETTE_SMOKE_PASS");
                    step = 13; entered = ticks;
                    return;
                }
                client.screen.onClose();
                step = 36; entered = ticks;
            } else if (step == 36 && ticks - entered > 10) {
                UUID id = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                        var box = player.getMainHandItem();
                        require(box.is(MahjongContent.BOX_ITEM), "Box fixture was not synchronized into the main hand");
                        box.getItem().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
                        require(player.containerMenu instanceof top.skyeyefast.mchjong.item.MahjongBoxMenu, "Box did not open its real menu");
                        var menu = player.containerMenu;
                        require(menu.slots.size() == top.skyeyefast.mchjong.item.MahjongSupplies.BOX_SLOTS + 36, "Box compartment layout differs");
                        require(menu.quickMoveStack(player, top.skyeyefast.mchjong.item.MahjongSupplies.BOX_SLOTS + 27).isEmpty(), "Shift-click moved the open carrier box");
                        menu.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.SWAP, player);
                        require(player.getMainHandItem() == box, "Hotbar swap replaced the open box");
                        require(!menu.slots.getFirst().mayPlace(new ItemStack(MahjongContent.BOX_ITEM)), "Box accepts nested boxes");
                    } catch (Throwable failure) { serverFailure.set(failure); }
                });
                step = 17; entered = ticks;
            } else if (step == 17 && ticks - entered > 15 && client.screen instanceof top.skyeyefast.mchjong.client.MahjongBoxScreen) {
                if (!interfaceSmoke.box(client, output)) return;
                require(client.player.containerMenu.slots.size() == top.skyeyefast.mchjong.item.MahjongSupplies.BOX_SLOTS + 36, "Client box slot layout differs from server");
                require(client.player.containerMenu instanceof top.skyeyefast.mchjong.item.MahjongBoxMenu menu
                    && menu.ownerSlot() == 0, "Client did not receive the server's carrier lock");
                capture(client, "00-physical-box.png");
                step = 22; entered = ticks;
            } else if (step == 22 && boxInterfaceSmoke.tick(client, output)) {
                client.screen.onClose();
                if (interfaceOnly) {
                    var id = client.player.getUUID();
                    fixtureSeat = client.getSingleplayerServer().submit(() -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                        var table = (MahjongTableBlockEntity) player.level().getBlockEntity(CENTER);
                        table.sit(player, 0);
                        return player.isPassenger();
                    });
                    step = 32; entered = ticks;
                    return;
                }
                step = 18; entered = ticks;
            } else if (step == 32 && fixtureSeat.isDone() && ticks - entered > 20) {
                require(fixtureSeat.join(), "Interface fixture did not obtain a physical seat");
                if (!tableInterfaceSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) return;
                step = 35; entered = ticks;
            } else if (step == 35 && resourcePackSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                Files.writeString(output.resolve("PASS.txt"), "Box transfers, keyboard presets and dye actions; representative Latin/CJK controls, seated reserve clock and immersive riichi, meld and reaction layouts.\n");
                LOG.info("MCHJONG_INTERFACE_SMOKE_PASS");
                step = 13; entered = ticks;
            } else if (step == 18 && ticks - entered > 10) {
                if (!itemPresentationSmoke.tick(client, output)) return;
                if (itemsOnly) {
                    Files.writeString(output.resolve("PASS.txt"), "Verified native tile and point-stick grips for right and left main hands.\n");
                    LOG.info("MCHJONG_ITEM_PRESENTATION_PASS");
                    step = 13; entered = ticks;
                    return;
                }
                step = 23; entered = ticks;
            } else if (step == 23 && interfaceSmoke.storage(client, CENTER, output)) {
                step = 24; entered = ticks;
            } else if (step == 24 && stoolInteractionSmoke.tick(client, CENTER, output)) {
                step = 3; entered = ticks;
            } else if (step == 3 && client.screen instanceof TableScreen && ticks - entered > 40) {
                require(client.player.isPassenger(), "Player did not mount the stool");
                if (visibilityOnly) { step = 33; entered = ticks; return; }
                if (roomOnly) { step = 34; entered = ticks; return; }
                if (seatingOnly) {
                    var settings = TableSettings.get();
                    var expected = TableGeometry.world(CENTER, TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, 0));
                    require(client.gameRenderer.getMainCamera().position().distanceTo(expected) < 1e-6,
                        "Open controls did not retain the standing-at-seat camera");
                }
                capture(client, "01-lobby.png");
                for (var child : client.screen.children()) if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(
                    net.minecraft.network.chat.Component.translatable("room.mchjong.start_bots").getString())) {
                    press(client, widget.getX() + 8, widget.getY() + 8);
                    step = 4; entered = ticks;
                    return;
                }
                throw new IllegalStateException("Fill-bots option missing from live lobby UI");
            } else if (step == 4 && ticks - entered > 40) {
                var table = (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER);
                if (!preparation.tick(client, table, output, "01-room")) return;
                var view = table.clientView();
                if (view == null) return;
                if (top.skyeyefast.mchjong.client.TableAnimation.of(table).dealing(net.minecraft.util.Util.getMillis())) return;
                require(view.viewerSeat() >= 0, "Private seat snapshot not delivered");
                // Initial dealership is randomized. Wait for the seated player's turn,
                // declining calls and continuing early abortive draws through the actual UI.
                if (view.phase() == Game.Phase.HAND_END) return;
                if (view.phase() == Game.Phase.REACTION) {
                    var key = "action.mchjong.pass";
                    for (var child : client.screen.children()) if (child instanceof AbstractWidget widget
                            && widget.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable(key).getString()) && widget.active) {
                        press(client, widget.getX() + 8, widget.getY() + 8);
                        break;
                    }
                    return;
                }
                if (view.phase() != Game.Phase.TURN || view.turn() != view.viewerSeat()) return;
                require(view.seats().get(view.viewerSeat()).hand().size() == 14, "Active player did not receive fourteen tiles");
                capture(client, "02-dealt-table.png");
                if (seatingOnly || settlementOnly) {
                    prepareDisplaySeat(client);
                    return;
                }
                TableSettings.get().discardMode = TableSettings.DiscardMode.CONFIRM;
                client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0));
                client.getSingleplayerServer().execute(() -> {
                    var level = client.getSingleplayerServer().overworld();
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                        level.setBlockAndUpdate(CENTER.offset(x, 3, z), Blocks.STONE.defaultBlockState());
                });
                press(client, client.screen.width / 2.0, client.screen.height - 52);
                step = 5; entered = ticks;
            } else if (step == 5 && ticks - entered > 15) {
                require(((TableScreen) client.screen).immersive(), "Immersive hand selection was not enabled");
                require(client.level.getBlockState(CENTER.above(3)).is(Blocks.STONE), "Occluding roof did not reach the client");
                var seat = (top.skyeyefast.mchjong.world.SeatEntity) client.player.getVehicle();
                require(client.gameRenderer.getMainCamera().position().distanceTo(TableSettings.get().cameraPosition(seat)) < 1e-6,
                    "Immersive view moved the world camera");
                capture(client, "03-immersive-discard-under-roof.png");
                for (var child : client.screen.children()) if (child instanceof AbstractWidget widget
                    && widget.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("action.mchjong.discard").getString())) {
                    press(client, widget.getX() + 8, widget.getY() + 8);
                    press(client, widget.getX() + 8, widget.getY() + 8);
                    client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
                    step = 6; entered = ticks;
                    return;
                }
                throw new IllegalStateException("Confirm discard mode did not expose its standalone discard button");
            } else if (step == 6 && ticks - entered > 20) {
                var view = ((MahjongTableBlockEntity) client.level.getBlockEntity(CENTER)).clientView();
                require(view.seats().get(view.viewerSeat()).river().size() == 1, "Discard confirmation did not reach the server");
                capture(client, "04-immersive-river-under-roof.png");
                client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0));
                require(!((TableScreen) client.screen).immersive(), "Cannot return to the seated view");
                client.getSingleplayerServer().execute(() -> {
                    var level = client.getSingleplayerServer().overworld();
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                        level.setBlockAndUpdate(CENTER.offset(x, 3, z), Blocks.AIR.defaultBlockState());
                });
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
                prepareDisplaySeat(client);
            } else if (step == 28 && fixtureSeat.isDone() && ticks - entered > 20) {
                require(fixtureSeat.join(), "Display-only fixture did not obtain its fixed seat");
                require(client.player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat && seat.seat() == 0,
                    "Fixed display seat has not reached the client");
                step = seatingOnly ? 11 : 10; entered = ticks;
            } else if (step == 10 && settlementSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                if (settlementOnly) {
                    Files.writeString(output.resolve("PASS.txt"), "Sequential yaku, points and grade; multi-winner navigation, resize, collapse and final standings passed.\n");
                    LOG.info("MCHJONG_SETTLEMENT_SMOKE_PASS");
                    step = 13; entered = ticks;
                    return;
                }
                step = 11; entered = ticks;
            } else if (step == 11 && animationSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                step = 30; entered = ticks;
            } else if (step == 30 && cameraSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                step = 29; entered = ticks;
            } else if (step == 29 && hintsSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                if (seatingOnly) { client.screen.onClose(); step = 26; }
                else step = 12;
                entered = ticks;
            } else if (step == 12 && replaySmoke.tick(client, output)) {
                step = 19; entered = ticks;
            } else if (step == 19 && manualSmoke.tick(client, output)) {
                if (manualOnly) {
                    Files.writeString(output.resolve("PASS.txt"), "Ordinary table wall building, dice, packet dealing, draws and exit passed.\n");
                    LOG.info("MCHJONG_MANUAL_SMOKE_PASS");
                    step = 13; entered = ticks;
                    return;
                }
                step = 25; entered = ticks;
            } else if (step == 25) {
                if (!browserSmoke.tick(client, output)) return;
                if (browserOnly) {
                    Files.writeString(output.resolve("PASS.txt"), "Recipe viewer catalogue, lookups and container passed.\n");
                    LOG.info("MCHJONG_BROWSER_SMOKE_PASS");
                    step = 13; entered = ticks;
                    return;
                }
                Files.writeString(output.resolve("survival-checks.txt"), "Real server menus: carrier lock, clicks, shift transfers, hotbar/offhand swaps, dragging, collection, invalidation and conservation. Native stonecutter: component cache invalidation, no re-engraving, preserved material/color and shift result conservation. Equipment: native placement, replacement, public/private updates, save/load, active locks, sanma full-set recovery, point-stick independence, root/placeholder destruction and explosions. Real ordinary-table client: shuffle, own wall, 4/4/4/1 packets, dealer and normal draws, discard, private hands, waiting without auto-handling, manual save/load and exit with exact box recovery.\n");
                Files.writeString(output.resolve("PASS.txt"), "World placement, seating, private deal, standalone discard confirmation, river synchronization, HD texture filtering and resource reload, no-scroll multi-winner settlement, resize, collapse, keyboard navigation and rendered wall/deal/discard/pon/riichi/closed-kan transitions passed. Live control packets verified solo exit, complete seat release, rejoining, three/four-player preset selection and open hands. Hidden rivers retain the remaining wall count. Settlement and animation screenshots use display-only fixtures. Engine-generated replay archival, authorized command fetch, chunk reassembly, replay list, timeline keyboard seeking, resized replay UI, sound registry and Tenhou JSON export-button checks passed.\n");
                LOG.info("MCHJONG_CLIENT_SMOKE_PASS");
                entered = ticks;
                step = 13;
            } else if (step == 26 && ticks - entered > 20) {
                var camera = client.gameRenderer.getMainCamera();
                var settings = TableSettings.get();
                var expected = TableGeometry.world(CENTER, TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, 0));
                require(camera.position().distanceTo(expected) < 1e-6, "Closing controls moved the seated camera");
                require(client.player.getEyePosition().distanceTo(expected) < 1e-6
                    && client.player.getEyePosition(1).distanceTo(expected) < 1e-6, "Native picking differs from the seated camera");
                capture(client, "03-seated-world.png");
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                client.player.setYRot(145);
                client.player.setXRot(15);
                step = 27; entered = ticks;
            } else if (step == 27 && ticks - entered > 20) {
                capture(client, "04-cushion-third-person.png");
                Files.writeString(output.resolve("PASS.txt"), "Seating, private deal, camera clearance, immersive rivers and hand with expanded options, stable open/closed first-person camera and third-person view.\n");
                LOG.info("MCHJONG_SEATING_SMOKE_PASS");
                step = 13; entered = ticks;
            } else if (step == 33 && visibilitySmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                Files.writeString(output.resolve("PASS.txt"), "Four room visibility modes, host proposals, four-language small-window controls, seated and unmounted spectator snapshots and normal/small world captures passed.\n");
                LOG.info("MCHJONG_VISIBILITY_SMOKE_PASS");
                step = 13; entered = ticks;
            } else if (step == 34 && roomSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                Files.writeString(output.resolve("PASS.txt"), "Four-language lobby controls at normal and small sizes; direct room navigation and proposals; server settlement countdown, automatic final standings and retained lobby; leave and dissolve packets passed.\n");
                LOG.info("MCHJONG_ROOM_SMOKE_PASS");
                step = 13; entered = ticks;
            } else if (step == 13 && ticks - entered > 30) {
                client.stop();
                step = 14;
            }
        } catch (Throwable failure) {
            LOG.error("MCHJONG_CLIENT_SMOKE_FAILED step={}", step, failure);
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

    /** Finish the live randomized-seat check before the existing seat-zero rendering fixtures. */
    private void prepareDisplaySeat(Minecraft client) {
        var id = client.player.getUUID();
        fixtureSeat = client.getSingleplayerServer().submit(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
            var table = (MahjongTableBlockEntity) player.level().getBlockEntity(CENTER);
            var game = table.participantGame(player);
            require(game != null && game.requestExit(id) && game.phase() == Game.Phase.LOBBY, "Cannot finish live smoke match");
            player.stopRiding();
            table.sit(player, 0);
            return game.seatOf(id) == 0 && player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat && seat.seat() == 0;
        });
        step = 28; entered = ticks;
    }

    private void capture(Minecraft client, String name) {
        SmokeScreenshots.grab(output.toFile(), name, client.getMainRenderTarget(), 1, message -> LOG.info("Screenshot: {}", message.getString()));
    }

    private static void press(Minecraft client, double x, double y) {
        client.screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(
            x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
