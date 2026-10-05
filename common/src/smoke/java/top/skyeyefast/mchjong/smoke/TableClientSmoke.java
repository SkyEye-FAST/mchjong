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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Development-only visual smoke: real integrated server, packets, world and UI. */
public final class TableClientSmoke {
    private static final Logger LOG = LoggerFactory.getLogger("mchjong-smoke");
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private final Path output = Path.of(System.getProperty("mchjong.smoke.output"));
    private final McrDisplaySmoke mcrDisplay = Boolean.getBoolean("mchjong.smoke.mcrLayoutOnly") ? new McrDisplaySmoke() : null;
    private final boolean mcrAutoOnly = Boolean.getBoolean("mchjong.smoke.mcrAutoOnly");
    private final McrAutoTableSmoke mcrAutoSmoke = new McrAutoTableSmoke();
    private final boolean taiwanOnly = Boolean.getBoolean("mchjong.smoke.taiwanOnly");
    private final TaiwanTableSmoke taiwanSmoke = new TaiwanTableSmoke();
    private final boolean sichuanOnly = Boolean.getBoolean("mchjong.smoke.sichuanOnly");
    private final SichuanTableSmoke sichuanSmoke = new SichuanTableSmoke();
    private final boolean itemsOnly = Boolean.getBoolean("mchjong.smoke.itemsOnly");
    private final boolean paletteOnly = Boolean.getBoolean("mchjong.smoke.paletteOnly");
    private final boolean seatingOnly = Boolean.getBoolean("mchjong.smoke.seatingOnly");
    private final boolean interfaceOnly = Boolean.getBoolean("mchjong.smoke.interfaceOnly");
    private final boolean visibilityOnly = Boolean.getBoolean("mchjong.smoke.visibilityOnly");
    private final boolean roomOnly = Boolean.getBoolean("mchjong.smoke.roomOnly");
    private final boolean createOnly = Boolean.getBoolean("mchjong.smoke.createOnly");
    private final boolean settlementOnly = Boolean.getBoolean("mchjong.smoke.settlementOnly");
    private final boolean manualOnly = Boolean.getBoolean("mchjong.smoke.manualOnly");
    private final boolean browserOnly = Boolean.getBoolean("mchjong.smoke.browserOnly");
    private final boolean guidesOnly = Boolean.getBoolean("mchjong.smoke.ponderOnly") || browserOnly;
    private final boolean maidOnly = Boolean.getBoolean("mchjong.smoke.maid");
    private final MaidIntegrationSmoke maidSmoke = maidOnly ? new MaidIntegrationSmoke() : null;
    private final boolean visualOnly = itemsOnly || paletteOnly || seatingOnly || interfaceOnly || visibilityOnly || roomOnly || manualOnly || maidOnly || settlementOnly || mcrAutoOnly || sichuanOnly || taiwanOnly;
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
            if (mcrDisplay != null) {
                if (mcrDisplay.tick(client, output)) {
                    Files.writeString(output.resolve("PASS.txt"), "MCR wall and play display fixtures passed\n");
                    step = 14;
                    client.stop();
                }
                return;
            }
            require(!client.mouseHandler.isMouseGrabbed(), "Smoke client grabbed the desktop mouse");
            require(org.lwjgl.glfw.GLFW.glfwGetInputMode(client.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_CURSOR)
                == org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL, "Smoke client confined or hid the desktop cursor");
            ticks++;
            if (step == 43 || step == 44) client.options.keyShift.setDown(true);
            if (serverFailure.get() != null) throw new IllegalStateException("Server smoke failed", serverFailure.get());
            // Presence checks wait through real server grace periods in each automatic room.
            if (ticks > (Boolean.getBoolean("mchjong.smoke.ponder") ? 8600 : 6600))
                throw new IllegalStateException("Smoke timed out at step " + step + ", screen=" + client.screen);
            if (step == 0 && client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) {
                onboarding.onClose();
                return;
            }
            if (step == 0 && client.screen instanceof TitleScreen && client.getOverlay() == null) {
                AudioEffectsSmoke.verify(client);
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
                TableSettings.get().recommendPatchouli = Boolean.getBoolean("mchjong.smoke.patchouliOnly");
                client.resizeDisplay();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                client.createWorldOpenFlows().createFreshLevel("table-smoke-" + System.currentTimeMillis(),
                    new LevelSettings("MChjong isolated smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(12345, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                step = 1;
                LOG.info("Created isolated smoke world");
            } else if (step == 1 && client.player != null && client.getSingleplayerServer() != null && client.level != null) {
                if (Boolean.getBoolean("mchjong.smoke.patchouliOnly")) { step = 39; return; }
                if (createOnly) {
                    UUID playerId = client.player.getUUID();
                    survivalReady = client.getSingleplayerServer().submit(() -> {
                        try { CreateSmoke.verify(client.getSingleplayerServer().getPlayerList().getPlayer(playerId)); }
                        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                        return true;
                    });
                    step = 36; entered = ticks;
                    return;
                }
                if (guidesOnly) {
                    require(Boolean.getBoolean("mchjong.smoke.ponder") || !System.getProperty("mchjong.smoke.browser", "none").equals("none"),
                        "Choose an installed Ponder or recipe-browser profile for the focused guide checks");
                    step = 37;
                    entered = ticks;
                    return;
                }
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
                        var level = player.serverLevel();
                        if (!visualOnly) {
                            SurvivalSmoke.verify(player);
                            RecipeBrowserDataSmoke.verify(level);
                        }
                        level.setDayTime(6000);
                        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
                            level.setBlock(CENTER.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                        level.setBlock(CENTER, MahjongContent.AUTO_TABLE.defaultBlockState(), 3);
                        ItemStack furniture = new ItemStack(MahjongContent.AUTO_TABLE_ITEM);
                        top.skyeyefast.mchjong.item.MahjongComponents.wood(furniture, top.skyeyefast.mchjong.item.FurnitureWood.CHERRY);
                        MahjongContent.AUTO_TABLE.setPlacedBy(level, CENTER, MahjongContent.AUTO_TABLE.defaultBlockState(), player, furniture);
                        var table = (MahjongTableBlockEntity) level.getBlockEntity(CENTER);
                        table.equipment().boxes().setItem(0, mcrAutoOnly || sichuanOnly || taiwanOnly
                            ? top.skyeyefast.mchjong.item.MahjongSupplies.stockedBox(top.skyeyefast.mchjong.engine.RedFives.NONE)
                            : top.skyeyefast.mchjong.item.MahjongSupplies.engrave(
                            top.skyeyefast.mchjong.item.MahjongSupplies.completeBox(
                                seatingOnly ? top.skyeyefast.mchjong.item.TileMaterial.QUARTZ : top.skyeyefast.mchjong.item.TileMaterial.GLASS,
                                seatingOnly ? net.minecraft.world.item.DyeColor.PINK : net.minecraft.world.item.DyeColor.BLUE),
                            top.skyeyefast.mchjong.item.TileFacePreset.KANTO));
                        ItemStack cloth = new ItemStack(MahjongContent.CLOTH_ITEM);
                        top.skyeyefast.mchjong.item.MahjongComponents.color(cloth, net.minecraft.world.item.DyeColor.CYAN);
                        table.useEquipment(player, cloth);
                        player.getInventory().selected = 0;
                        player.getInventory().setItem(0, top.skyeyefast.mchjong.item.MahjongSupplies.completeBox(
                            top.skyeyefast.mchjong.item.TileMaterial.GLASS, net.minecraft.world.item.DyeColor.BLUE));
                        var flowerBox = player.getInventory().getItem(0);
                        var flowerContents = top.skyeyefast.mchjong.item.MahjongSupplies.contents(flowerBox);
                        for (int flower = 0; flower < 8; flower++) flowerContents.set(37 + flower,
                            top.skyeyefast.mchjong.item.MahjongSupplies.tile(new top.skyeyefast.mchjong.item.TileData(
                                34 + flower, top.skyeyefast.mchjong.item.TileMaterial.GLASS, false), net.minecraft.world.item.DyeColor.BLUE, 1));
                        flowerContents.set(top.skyeyefast.mchjong.item.MahjongSupplies.DYE_SLOT,
                            new ItemStack(MahjongContent.CREATIVE_MAHJONG_DYE));
                        top.skyeyefast.mchjong.item.MahjongSupplies.setContents(flowerBox, flowerContents);
                        player.getInventory().setItem(1, new ItemStack(MahjongContent.TABLE_ITEM));
                        player.getInventory().setItem(2, furniture.copy());
                        player.getInventory().setItem(3, cloth.copy());
                        player.getInventory().setItem(4, top.skyeyefast.mchjong.item.MahjongSupplies.tile(
                            new top.skyeyefast.mchjong.item.TileData(4, top.skyeyefast.mchjong.item.TileMaterial.GLASS, true),
                            net.minecraft.world.item.DyeColor.BLUE, 1));
                        ItemStack sticks = new ItemStack(MahjongContent.POINT_STICK, 8);
                        top.skyeyefast.mchjong.item.MahjongComponents.points(sticks, 1000);
                        player.getInventory().setItem(5, sticks);
                        player.getInventory().setItem(6, new ItemStack(MahjongContent.STOOL_ITEM));
                        player.getInventory().setItem(7, top.skyeyefast.mchjong.item.MahjongSupplies.tile(
                            new top.skyeyefast.mchjong.item.TileData(41, top.skyeyefast.mchjong.item.TileMaterial.GLASS, false),
                            net.minecraft.world.item.DyeColor.BLUE, 1));
                        player.getInventory().setItem(8, ItemStack.EMPTY);
                        player.getInventory().setChanged();
                        if (paletteOnly) {
                            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, furniture.copy());
                            player.setGameMode(GameType.SURVIVAL);
                        }
                        for (int seat = 0; seat < 4; seat++) level.setBlock(TableGeometry.stool(CENTER, seat), MahjongContent.STOOL.defaultBlockState(), 3);
                        player.teleportTo(level, 0.5, 64, 3.5, 180, 30);
                    } catch (Throwable failure) { serverFailure.set(failure); }
                });
                step = 2; entered = ticks;
            } else if (step == 2 && ticks - entered > 60 && client.level.getBlockEntity(CENTER) instanceof MahjongTableBlockEntity) {
                require(mcrAutoOnly || sichuanOnly || taiwanOnly || ((MahjongTableBlockEntity) client.level.getBlockEntity(CENTER)).equipment().preset()
                    .equals(top.skyeyefast.mchjong.item.TileFacePreset.KANTO), "Client table lost its synchronized face preset");
                if (seatingOnly) {
                    var equipment = ((MahjongTableBlockEntity) client.level.getBlockEntity(CENTER)).equipment();
                    require(equipment.material() == top.skyeyefast.mchjong.item.TileMaterial.QUARTZ
                        && equipment.back() == net.minecraft.world.item.DyeColor.PINK,
                        "Immersive fixture did not synchronize its opaque dyed tiles");
                }
                if (mcrAutoOnly) { step = 41; entered = ticks; return; }
                if (sichuanOnly) { step = 42; entered = ticks; return; }
                if (taiwanOnly) { step = 45; entered = ticks; return; }
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
            } else if (step == 45) {
                if (taiwanSmoke.tick(client, CENTER, output)) {
                    Files.writeString(output.resolve("PASS.txt"), "Taiwan four-variant switching, 136/144 equipment, real play, flowers/calls, recipient privacy, settlement/confirmations, NBT restoration, four winds, stale authority, shared replay browser/open/event/viewer/settlement/return and one-human/three-Bot replay standings passed\n");
                    LOG.info("MCHJONG_TAIWAN_SMOKE_PASS"); step = 14; client.stop();
                }
            } else if (step == 42) {
                if (sichuanSmoke.tick(client, CENTER, output)) {
                    Files.writeString(output.resolve("PASS.txt"), "Sichuan lobby, private declarations, NBT restore, seated/immersive picking and discard, exit vote, results, next-hand packets, eight-hand matches, native Bot room controls, one-human/three-Bot NBT restore, automatic confirmations and Bot replay standings passed\n");
                    LOG.info("MCHJONG_SICHUAN_SMOKE_PASS");
                    step = 14;
                    client.stop();
                }
            } else if (step == 41) {
                if (mcrAutoSmoke.tick(client, CENTER, output)) {
                    Files.writeString(output.resolve("PASS.txt"), "MCR automatic table network, view, screen and next-hand path passed\n");
                    LOG.info("MCHJONG_MCR_AUTO_SMOKE_PASS");
                    step = 14;
                    client.stop();
                }
            } else if (step == 31 && ticks - entered > 15) {
                capture(client, "00-material-palette.png");
                client.setScreen(paletteOnly ? new HeadEquipmentSmokeScreen(client.player)
                    : new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
                step = 16; entered = ticks;
            } else if (step == 16 && ticks - entered > 15) {
                capture(client, paletteOnly ? "00-table-head.png" : "00-equipment-inventory.png");
                if (paletteOnly) {
                    UUID id = client.player.getUUID();
                    client.getSingleplayerServer().execute(() -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(MahjongContent.STOOL_ITEM));
                    });
                    step = 41; entered = ticks;
                    return;
                }
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
                        require(menu.slots.size() == top.skyeyefast.mchjong.item.MahjongSupplies.BOX_SLOTS + 36, "Box compartment layout differs");
                        require(menu.quickMoveStack(player, top.skyeyefast.mchjong.item.MahjongSupplies.BOX_SLOTS + 27).isEmpty(), "Shift-click moved the open carrier box");
                        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.SWAP, player);
                        require(player.getMainHandItem() == box, "Hotbar swap replaced the open box");
                        require(!menu.slots.get(0).mayPlace(new ItemStack(MahjongContent.BOX_ITEM)), "Box accepts nested boxes");
                    } catch (Throwable failure) { serverFailure.set(failure); }
                });
                step = 17; entered = ticks;
            } else if (step == 41 && ticks - entered > 15) {
                capture(client, "00-stool-head.png");
                client.setScreen(null);
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                client.options.keyShift.setDown(true);
                client.player.getInventory().selected = 8;
                var id = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
                    player.getInventory().selected = 8;
                    player.teleportTo(player.serverLevel(), 1.4, 64, 2.31, 180, 11);
                });
                step = 43; entered = ticks;
            } else if (step == 43 && ticks - entered > 30) {
                require(client.level.noCollision(client.player), "Near-edge fixture intersects furniture");
                require(client.player.isCrouching(), "Near-edge fixture is not sneaking: flying=" + client.player.getAbilities().flying + ", shift=" + client.player.isShiftKeyDown()
                    + ", input=" + client.player.input.shiftKeyDown + ", pose=" + client.player.getPose()
                    + ", pos=" + client.player.position() + ", screen=" + client.screen);
                capture(client, "00-table-edge-sneaking.png");
                client.player.setYRot(181);
                client.player.yRotO = 181;
                step = 44; entered = ticks;
            } else if (step == 44 && ticks - entered > 10) {
                capture(client, "00-table-edge-sneaking-shift.png");
                client.options.keyShift.setDown(false);
                client.player.getInventory().selected = 1;
                var id = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                    player.setNoGravity(false);
                    player.teleportTo(player.serverLevel(), .5, 64, 3.5, 180, 15);
                    player.getInventory().selected = 1;
                });
                step = 45; entered = ticks;
            } else if (step == 45 && ticks - entered > 20) {
                capture(client, "00-table-first-person.png");
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                step = 46; entered = ticks;
            } else if (step == 46 && ticks - entered > 20) {
                capture(client, "00-table-third-person.png");
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                client.setScreen(new FurnitureContextsSmoke());
                step = 47; entered = ticks;
            } else if (step == 47 && ticks - entered > 15) {
                capture(client, "00-furniture-contexts.png");
                var stand = new net.minecraft.world.entity.decoration.ArmorStand(client.level, 0, 0, 0);
                stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(MahjongContent.TABLE_ITEM));
                client.setScreen(new HeadEquipmentSmokeScreen(stand));
                step = 48; entered = ticks;
            } else if (step == 48 && ticks - entered > 15) {
                capture(client, "00-armor-stand-head.png");
                Files.writeString(output.resolve("PASS.txt"), "Furniture palette, player HEAD, crouched table edge and item contexts passed.\n");
                LOG.info("MCHJONG_PALETTE_SMOKE_PASS");
                step = 13; entered = ticks;
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
                        var table = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(CENTER);
                        table.sit(player, 0);
                        return player.isPassenger();
                    });
                    step = 32; entered = ticks;
                    return;
                }
                step = 18; entered = ticks;
            } else if (step == 32 && fixtureSeat.isDone() && ticks - entered > 20) {
                require(fixtureSeat.join(), "Interface fixture did not obtain a physical seat");
                if (!interfaceSmoke.settings(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) return;
                step = 40; entered = ticks;
            } else if (step == 40) {
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
            } else if (step == 3 && client.screen instanceof RiichiTableScreen && ticks - entered > 40) {
                require(client.player.isPassenger(), "Player did not mount the stool");
                if (maidOnly) { step = 38; entered = ticks; return; }
                if (visibilityOnly) { step = 33; entered = ticks; return; }
                if (roomOnly) { step = 34; entered = ticks; return; }
                if (seatingOnly) {
                    var settings = TableSettings.get();
                    var expected = TableGeometry.world(CENTER, TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, 0));
                    require(client.gameRenderer.getMainCamera().getPosition().distanceTo(expected) < 1e-6,
                        "Open controls did not retain the standing-at-seat camera");
                }
                capture(client, "01-lobby.png");
                for (var child : client.screen.children()) if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(
                    net.minecraft.network.chat.Component.translatable("action.mchjong.fill_bots").getString())) {
                    client.screen.mouseClicked(widget.getX()+8, widget.getY()+8, 0);
                    step = 4; entered = ticks;
                    return;
                }
                throw new IllegalStateException("Fill-bots option missing from live lobby UI");
            } else if (step == 4 && ticks - entered > 40) {
                var table = (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER);
                if (!preparation.tick(client, table, output, "01-room")) return;
                var view = table.clientView();
                if (view == null) return;
                if (top.skyeyefast.mchjong.client.RiichiAnimation.of(table).dealing(net.minecraft.Util.getMillis())) return;
                require(view.viewerSeat() >= 0, "Private seat snapshot not delivered");
                // Initial dealership is randomized. Wait for the seated player's turn,
                // declining calls and continuing early abortive draws through the actual UI.
                if (view.phase() == RiichiView.Phase.HAND_END) return;
                if (view.phase() == RiichiView.Phase.REACTION) {
                    var key = "action.mchjong.pass";
                    for (var child : client.screen.children()) if (child instanceof AbstractWidget widget
                            && widget.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable(key).getString()) && widget.active) {
                        client.screen.mouseClicked(widget.getX()+8, widget.getY()+8, 0);
                        break;
                    }
                    return;
                }
                if (view.phase() != RiichiView.Phase.TURN || view.turn() != view.viewerSeat()) return;
                require(view.seats().get(view.viewerSeat()).hand().size() == 14, "Active player did not receive fourteen tiles");
                capture(client, "02-dealt-table.png");
                if (seatingOnly || settlementOnly) {
                    prepareDisplaySeat(client);
                    return;
                }
                TableSettings.get().discardMode = TableSettings.DiscardMode.CONFIRM;
                client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                client.getSingleplayerServer().execute(() -> {
                    var level = client.getSingleplayerServer().overworld();
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                        level.setBlockAndUpdate(CENTER.offset(x, 3, z), Blocks.STONE.defaultBlockState());
                });
                client.screen.mouseClicked(client.screen.width / 2.0, client.screen.height - 52, 0);
                step = 5; entered = ticks;
            } else if (step == 5 && ticks - entered > 15) {
                require(((RiichiTableScreen) client.screen).immersive(), "Immersive hand selection was not enabled");
                require(client.level.getBlockState(CENTER.above(3)).is(Blocks.STONE), "Occluding roof did not reach the client");
                var seat = (top.skyeyefast.mchjong.world.SeatEntity) client.player.getVehicle();
                require(client.gameRenderer.getMainCamera().getPosition().distanceTo(TableSettings.get().cameraPosition(seat)) < 1e-6,
                    "Immersive view moved the world camera");
                capture(client, "03-immersive-discard-under-roof.png");
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
                require(view.seats().get(view.viewerSeat()).river().size() == 1, "Discard confirmation did not reach the server");
                capture(client, "04-immersive-river-under-roof.png");
                client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                require(!((RiichiTableScreen) client.screen).immersive(), "Cannot return to the seated view");
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
                && interfaceSmoke.automation(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)
                && controlSmoke.tick(client, (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER), output)) {
                prepareDisplaySeat(client);
            } else if (step == 28 && fixtureSeat.isDone() && ticks - entered > 20) {
                require(fixtureSeat.join(), "Display-only fixture did not obtain its fixed seat");
                require(client.player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat && seat.seat() == 0,
                    "Fixed display seat has not reached the client");
                var table = (MahjongTableBlockEntity) client.level.getBlockEntity(CENTER);
                if (table.clientView() == null || table.clientView().viewerSeat() != 0) return;
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
                if (Boolean.getBoolean("mchjong.smoke.ponder") && !PonderSmoke.tick(client, output)) return;
                if (!Boolean.getBoolean("mchjong.smoke.ponder"))
                    Files.writeString(output.resolve("ponder-optional.txt"), "Base client gameplay passed with Ponder absent.\n");
                Files.writeString(output.resolve("survival-checks.txt"), "Real server menus: carrier lock, clicks, shift transfers, hotbar/offhand swaps, dragging, collection, invalidation and conservation. Native stonecutter: component cache invalidation, no re-engraving, preserved material/color and shift result conservation. Equipment: native placement, replacement, public/private updates, save/load, active locks, sanma full-set recovery, point-stick independence, root/placeholder destruction and explosions. Real ordinary-table client: shuffle, own wall, 4/4/4/1 packets, dealer and normal draws, discard, private hands, waiting without auto-handling, manual save/load and exit with exact box recovery.\n");
                Files.writeString(output.resolve("PASS.txt"), "World placement, seating, private deal, standalone discard confirmation, river synchronization, HD texture filtering and resource reload, no-scroll multi-winner settlement, resize, collapse, keyboard navigation and rendered wall/deal/discard/pon/riichi/closed-kan transitions passed. Live control packets verified solo exit, complete seat release, rejoining, three/four-player preset selection and open hands. Hidden rivers retain the remaining wall count. Settlement and animation screenshots use display-only fixtures. Engine-generated replay archival, authorized command fetch, chunk reassembly, replay list, timeline keyboard seeking, resized replay UI, sound registry and Tenhou JSON export-button checks passed.\n");
                LOG.info("MCHJONG_CLIENT_SMOKE_PASS");
                entered = ticks;
                step = 13;
            } else if (step == 26 && ticks - entered > 20) {
                var camera = client.gameRenderer.getMainCamera();
                var settings = TableSettings.get();
                var expected = TableGeometry.world(CENTER, TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, 0));
                require(camera.getPosition().distanceTo(expected) < 1e-6, "Closing controls moved the seated camera");
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
            } else if (step == 36) {
                if (!survivalReady.isDone()) return;
                survivalReady.join();
                if (!browserSmoke.tick(client, output)) return;
                if (Boolean.getBoolean("mchjong.smoke.ponder") && !PonderSmoke.tick(client, output)) return;
                Files.writeString(output.resolve("create-checks.txt"), "Native basin insertion, full-deck printing, plate return, blocked-output conservation, dye/undo, marking, packing, single-tile red/undo and box assembly passed.\n");
                Files.writeString(output.resolve("PASS.txt"), "Focused Create production and selected browser/tutorial checks passed.\n");
                LOG.info("MCHJONG_CREATE_SMOKE_PASS");
                step = 13; entered = ticks;
            } else if (step == 38 && maidSmoke.tick(client, CENTER, output)) {
                Files.writeString(output.resolve("PASS.txt"), "Maid task discovery, physical seating, saved binding recovery, seat assignment, legal bot play and task-change cleanup passed.\n");
                LOG.info("MCHJONG_MAID_SMOKE_PASS");
                step = 13; entered = ticks;
            } else if (step == 37) {
                if (!browserSmoke.tick(client, output)
                    || (Boolean.getBoolean("mchjong.smoke.ponder") && !PonderSmoke.tick(client, output))) return;
                Files.writeString(output.resolve("PASS.txt"), "Focused recipe-browser checks and requested Ponder registration, localization, playback, reload and replay checks passed.\n");
                LOG.info("MCHJONG_PONDER_SMOKE_PASS");
                step = 13; entered = ticks;
            } else if (step == 39 && ManualSmoke.tick(client, output)) {
                Files.writeString(output.resolve("PASS.txt"), "Patchouli manual: selected installed/absent profile, entry rendering and recommendation controls passed.\n");
                LOG.info("MCHJONG_PATCHOULI_SMOKE_PASS");
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
            var table = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(CENTER);
            var game = table.participantSession(player);
            require(game != null && game.requestExit(id) && game.lobby(), "Cannot finish live smoke match");
            player.stopRiding();
            table.sit(player, 0);
            SeatingFixtures.startPositioned(game, id);
            return game.seatOf(id) == 0 && game.game() != null
                && player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat && seat.seat() == 0;
        });
        step = 28; entered = ticks;
    }

    private void capture(Minecraft client, String name) {
        SmokeScreenshots.grab(output.toFile(), name, client.getMainRenderTarget(), message -> LOG.info("Screenshot: {}", message.getString()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
