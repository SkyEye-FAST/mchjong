package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.TableAnimation;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Real world-space pointer gestures travel through the normal client/server action packets. */
final class ManualTableSmoke {
    private static final BlockPos CENTER = new BlockPos(10, 64, 0);
    private int stage, ticks, totalTicks, packets, remaining;
    private CompletableFuture<Void> serverWork;
    private ItemStack installedBox;
    private final PointStickInterfaceSmoke drawers = new PointStickInterfaceSmoke();
    private final DepositVisualSmoke deposits = new DepositVisualSmoke();

    boolean tick(Minecraft client, Path output) {
        ticks++;
        if (++totalTicks > 1800) {
            capture(client, output, "39-manual-failure.png");
            throw new IllegalStateException("Manual client smoke timed out at stage " + stage
                + ", screen=" + client.screen + ", player=" + client.player.position());
        }
        if (serverWork != null) {
            if (!serverWork.isDone()) return false;
            serverWork.join();
            serverWork = null;
        }
        if (stage == 0) {
            // ReplayScreen.onClose returns to its pausing list; leave the display fixtures entirely.
            client.setScreen(null);
            serverWork = onServer(client, this::prepare);
            next(1);
            return false;
        }
        if (!(client.level.getBlockEntity(CENTER) instanceof MahjongTableBlockEntity table)) return false;
        if (stage == 1) {
            // Seating produces the first authorized snapshot; do not wait for it before seating.
            if (ticks < 15 || client.player.distanceToSqr(CENTER.getX() + .5, CENTER.getY(), CENTER.getZ() + .5) > 36) return false;
            serverWork = onServer(client, player -> {
                var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(CENTER);
                serverTable.sit(player, 0);
                check(serverTable.participantGame(player) != null, "Manual fixture did not seat the real player");
            });
            next(15);
            return false;
        }
        var view = table.clientView();
        if (view == null) return false;
        privateHands(view);
        switch (stage) {
            case 15 -> {
                if (!(client.screen instanceof TableScreen) || view.viewerSeat() != 0 || ticks < 15) return false;
                check(!table.automatic() && table.wood() == FurnitureWood.WARPED, "Ordinary table appearance did not synchronize");
                check(view.timeControl().equals(top.skyeyefast.mchjong.engine.TimeControl.MANUAL), "Ordinary lobby clock defaults differ from the server");
                var parent = (TableScreen) client.screen;
                client.setScreen(new top.skyeyefast.mchjong.client.TableSettingsScreen(parent));
                check(client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .noneMatch(widget -> widget.getMessage().getString().equals(Component.translatable("settings.mchjong.tab.4").getString())),
                    "Ordinary table exposed the automatic-only settings page");
                client.setScreen(parent);
                check(table.equipment().clothColor() == DyeColor.RED && table.equipment().material() == TileMaterial.GLASS,
                    "Ordinary table lost equipment appearance");
                check(table.equipment().drawer(0).isEmpty() && view.riichiSticks() == 0,
                    "Private drawer contents leaked through the appearance update");
                capture(client, output, "30-manual-lobby.png");
                client.screen.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
                next(16);
            }
            case 16 -> {
                if (!(client.screen instanceof top.skyeyefast.mchjong.client.PointStickScreen) || ticks < 10) return false;
                if (!drawers.tick(client, output)) return false;
                check(client.player.containerMenu instanceof top.skyeyefast.mchjong.item.PointStickMenu menu
                    && menu.totalPoints(0) == 3000 && menu.slots.size() == 72, "Native drawer screen did not synchronize all four rows");
                capture(client, output, "30a-point-drawers.png");
                client.screen.onClose();
                next(17);
            }
            case 17 -> {
                if (!(client.screen instanceof TableScreen) || ticks < 10) return false;
                click(client, "ui.mchjong.practice_short");
                next(2);
            }
            case 2 -> {
                if (view.phase() != Game.Phase.SHUFFLE || ticks < 40 || !hasControl(client, "action.mchjong.shuffle")) return false;
                check(view.wall().isEmpty() && view.seats().stream().allMatch(seat -> seat.hand().isEmpty()), "Ordinary table shuffled itself");
                capture(client, output, "31-manual-shuffle.png");
                click(client, "action.mchjong.shuffle");
                next(3);
            }
            case 3 -> {
                if (view.phase() != Game.Phase.BUILD_WALL || ticks < 15 || !hasControl(client, "action.mchjong.build_wall")) return false;
                check(view.seats().stream().allMatch(seat -> seat.hand().isEmpty()), "Ordinary table dealt before walls were built");
                capture(client, output, "32-manual-build-wall.png");
                click(client, "action.mchjong.build_wall");
                next(4);
            }
            case 4 -> {
                if (!offered(view, Action.Type.TAKE_PACKET) || ticks < 10 || !hasControl(client, "action.mchjong.take_packet")) return false;
                click(client, "action.mchjong.take_packet");
                next(5);
            }
            case 5 -> {
                int expected = new int[]{4, 8, 12, 13}[packets];
                if (view.seats().getFirst().hand().size() != expected) return false;
                capture(client, output, "33-manual-packet-" + (packets + 1) + ".png");
                packets++;
                next(packets == 4 ? 6 : 4);
            }
            case 6 -> {
                if (!offered(view, Action.Type.DRAW) || ticks < 40) return false;
                check(view.seats().stream().allMatch(seat -> seat.hand().size() == 13), "Packet dealing did not leave thirteen tiles each");
                remaining = view.remaining();
                capture(client, output, "34-manual-waiting-draw.png");
                serverWork = onServer(client, player -> verifySavedHandling(player, CENTER));
                next(7);
            }
            case 7 -> {
                check(view.phase() == Game.Phase.DRAW && view.remaining() == remaining, "Draw advanced without the player's action");
                if (!hasControl(client, "action.mchjong.draw")) return false;
                click(client, "action.mchjong.draw");
                next(8);
            }
            case 8 -> {
                if (view.phase() != Game.Phase.TURN || view.turn() != 0 || TableAnimation.of(table).dealing(net.minecraft.Util.getMillis())) return false;
                check(view.seats().getFirst().hand().size() == 14 && view.remaining() == remaining - 1, "Explicit dealer draw changed the wrong number of tiles");
                capture(client, output, "35-manual-dealer-draw.png");
                TableSettings.get().discardMode = TableSettings.DiscardMode.CONFIRM;
                client.screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                next(9);
            }
            case 9 -> {
                if (ticks < 10) return false;
                click(client, "action.mchjong.discard");
                next(10);
            }
            case 10 -> {
                if (offered(view, Action.Type.PASS) && hasControl(client, "action.mchjong.pass")) click(client, "action.mchjong.pass");
                if (!offered(view, Action.Type.DRAW)) return false;
                check(view.seats().getFirst().river().size() == 1 && view.seats().getFirst().hand().size() == 13,
                    "Manual discard/draw cycle did not reach the server");
                remaining = view.remaining();
                next(11);
            }
            case 11 -> {
                check(view.phase() == Game.Phase.DRAW && view.remaining() == remaining, "Normal draw ran automatically on an ordinary table");
                if (ticks < 40 || !hasControl(client, "action.mchjong.draw")) return false;
                capture(client, output, "36-manual-normal-draw.png");
                click(client, "action.mchjong.draw");
                next(12);
            }
            case 12 -> {
                if (view.phase() != Game.Phase.TURN || view.seats().getFirst().hand().size() != 14 || ticks < 10) return false;
                check(view.remaining() == remaining - 1, "Normal draw did not consume exactly one wall tile");
                capture(client, output, "37-manual-complete-turn.png");
                click(client, "ui.mchjong.exit");
                next(13);
            }
            case 13 -> {
                if (view.phase() != Game.Phase.LOBBY || view.viewerSeat() >= 0 || client.player.isPassenger()) return false;
                check(view.wall().isEmpty(), "Exiting left a playable manual wall behind");
                capture(client, output, "38-manual-exited.png");
                serverWork = onServer(client, player -> {
                    var serverTable = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(CENTER);
                    check(ItemStack.matches(installedBox, serverTable.equipment().boxes().getItem(0)), "Playing consumed or altered the physical set");
                    TableStorageSmoke.take(player, serverTable, 0);
                    check(serverTable.equipment().boxes().isEmpty(), "Removed box remained stored");
                    check(java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
                        .mapToObj(player.getInventory()::getItem).anyMatch(stack -> ItemStack.matches(installedBox, stack)),
                        "The complete set was not returned after exit");
                    player.setShiftKeyDown(false);
                });
                next(14);
            }
            case 14 -> {
                serverWork = onServer(client, player ->
                    ((MahjongTableBlockEntity) player.serverLevel().getBlockEntity(CENTER)).sit(player, 0));
                next(18);
            }
            case 18 -> {
                if (!client.player.isPassenger() || view.viewerSeat() < 0 || ticks < 10) return false;
                return deposits.tick(client, table, output);
            }
            default -> throw new IllegalStateException("Unknown manual smoke stage");
        }
        return false;
    }

    private void prepare(ServerPlayer player) {
        if (player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat) {
            var previous = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(seat.tablePos());
            var game = previous.participantGame(player);
            if (game != null) previous.control(player, new TableControlPayload(previous.getBlockPos(), game.tableId(),
                TableControlPayload.Operation.REQUEST_EXIT, game.view(player.getUUID()).decision(), false));
        }
        player.stopRiding();
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        var level = player.serverLevel();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            level.setBlock(CENTER.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        level.setBlock(CENTER, MahjongContent.TABLE.defaultBlockState(), 3);
        var furniture = new ItemStack(MahjongContent.TABLE_ITEM);
        furniture.set(MahjongComponents.WOOD, FurnitureWood.WARPED);
        MahjongContent.TABLE.setPlacedBy(level, CENTER, MahjongContent.TABLE.defaultBlockState(), player, furniture);
        var table = (MahjongTableBlockEntity) level.getBlockEntity(CENTER);
        installedBox = MahjongSupplies.completeBox(TileMaterial.GLASS, DyeColor.CYAN);
        var box = installedBox.copy();
        player.teleportTo(level, CENTER.getX() + .5, 64, 3.5, 180, 30);
        TableStorageSmoke.put(player, table, 0, box);
        check(box.isEmpty(), "Storage transfer did not move the physical box");
        var cloth = new ItemStack(MahjongContent.CLOTH_ITEM);
        cloth.set(DataComponents.BASE_COLOR, DyeColor.RED);
        table.useEquipment(player, cloth);
        check(cloth.isEmpty(), "Survival installation did not consume the cloth");
        // Pin only the server fixture's initial seed so the real human is the initial dealer.
        long seed = 0;
        while (new Random(seed).nextInt(4) != 0) seed++;
        var game = new Game(UUID.randomUUID(), RuleSet.MAHJONG_SOUL_4, seed);
        game.configureEquipment(true, table.equipment().deck().tiles(false));
        var saved = table.saveWithoutMetadata(level.registryAccess());
        saved.putString("game", TableNetworking.JSON.toJson(game));
        table.loadWithComponents(saved, level.registryAccess());
        var stool = new ItemStack(MahjongContent.STOOL_ITEM);
        stool.set(MahjongComponents.WOOD, FurnitureWood.WARPED);
        stool.set(DataComponents.BASE_COLOR, DyeColor.RED);
        for (int side = 0; side < 4; side++) {
            var pos = TableGeometry.stool(CENTER, side);
            level.setBlock(pos, MahjongContent.STOOL.defaultBlockState(), 3);
            MahjongContent.STOOL.setPlacedBy(level, pos, MahjongContent.STOOL.defaultBlockState(), player, stool);
        }
        player.teleportTo(level, CENTER.getX() + .5, 64, 3.5, 180, 30);
        var sticks = new ItemStack(MahjongContent.POINT_STICK, 3);
        sticks.set(MahjongComponents.POINTS, 1000);
        PointStickMenuSmoke.put(player, table, 0, sticks);
        check(sticks.isEmpty(), "Manual fixture did not transfer its physical drawer sticks");
    }

    private static void verifySavedHandling(ServerPlayer player, BlockPos pos) {
        var table = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
        var game = table.participantGame(player);
        check(game != null && game.phase() == Game.Phase.DRAW && game.manual(), "Manual server was not waiting for the draw");
        game.validate();
        var saved = table.saveWithoutMetadata(player.registryAccess());
        var loaded = new MahjongTableBlockEntity(pos, table.getBlockState());
        loaded.setLevel(player.serverLevel());
        loaded.loadWithComponents(saved, player.registryAccess());
        check(saved.equals(loaded.saveWithoutMetadata(player.registryAccess())), "Manual handling or equipment did not survive world serialization");
        table.loadWithComponents(table.getUpdateTag(player.registryAccess()), player.registryAccess());
        check(saved.equals(table.saveWithoutMetadata(player.registryAccess())), "Public appearance update erased private game/equipment state");
        check(!TableNetworking.JSON.toJson(game.view(null)).contains("suppliedTiles"), "Physical/private wall leaked to spectators");
    }

    private static void privateHands(TableView view) {
        for (int seat = 0; seat < view.seats().size(); seat++) if (seat != view.viewerSeat() && !view.seats().get(seat).exposed())
            check(view.seats().get(seat).hand().stream().allMatch(tile -> tile == Tile.HIDDEN), "Unauthorized manual hand reached the client");
    }

    private static CompletableFuture<Void> onServer(Minecraft client, Consumer<ServerPlayer> action) {
        UUID id = client.player.getUUID();
        var result = new CompletableFuture<Void>();
        client.getSingleplayerServer().execute(() -> {
            try { action.accept(client.getSingleplayerServer().getPlayerList().getPlayer(id)); result.complete(null); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    private static boolean offered(TableView view, Action.Type type) { return view.actions().stream().anyMatch(action -> action.type() == type); }
    private void next(int value) { stage = value; ticks = 0; }
    private static boolean hasControl(Minecraft client, String key) {
        if (!(client.screen instanceof TableScreen)) return false;
        String label = Component.translatable(key).getString();
        return client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .anyMatch(button -> button.getMessage().getString().equals(label) && button.active && button.visible);
    }
    private static void click(Minecraft client, String key) {
        if (client.level.getBlockEntity(CENTER) instanceof MahjongTableBlockEntity table) {
            var view = table.clientView();
            int index = top.skyeyefast.mchjong.client.TableHandling.action(view);
            if (index >= 0 && view.actions().get(index).translationKey().equals(key)) {
                dragTiles(client, table, view);
                return;
            }
        }
        String label = Component.translatable(key).getString();
        var widget = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(button -> button.getMessage().getString().equals(label) && button.active).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing live manual control: " + key));
        client.screen.mouseClicked(widget.getX() + 5, widget.getY() + 5, 0);
    }

    private static void dragTiles(Minecraft client, MahjongTableBlockEntity table, TableView view) {
        var screen = (TableScreen) client.screen;
        var frames = TableAnimation.of(table).sample(net.minecraft.Util.getMillis());
        var camera = client.gameRenderer.getMainCamera().getPosition().subtract(TableGeometry.world(CENTER, net.minecraft.world.phys.Vec3.ZERO));
        for (var frame : frames) {
            var piece = frame.piece();
            if (!top.skyeyefast.mchjong.client.TableHandling.source(view, piece)) continue;
            var destination = top.skyeyefast.mchjong.client.TableHandling.destination(view);
            if (view.phase() == Game.Phase.SHUFFLE)
                destination = new net.minecraft.world.phys.Vec3(piece.position().x < 0 ? .6 : -.6, TableGeometry.FELT_Y, 0);
            var start = project(client, top.skyeyefast.mchjong.client.TableHandling.grip(piece, camera));
            var end = project(client, destination);
            if (start.x < 0 || start.x >= screen.width || start.y < 0 || start.y >= screen.height) continue;
            screen.mouseClicked(start.x, start.y, 0);
            screen.mouseDragged(end.x, end.y, 0, end.x - start.x, end.y - start.y);
            boolean holding = frames.stream().anyMatch(value -> screen.handlingOffset(CENTER, value.piece()).lengthSqr() > 0);
            screen.mouseReleased(end.x, end.y, 0);
            if (holding) return;
        }
        var sources = frames.stream().filter(frame -> top.skyeyefast.mchjong.client.TableHandling.source(view, frame.piece()))
            .map(frame -> {
                var ray = top.skyeyefast.mchjong.client.TableHandling.grip(frame.piece(), camera).subtract(camera);
                var nearest = frames.stream().min(java.util.Comparator.comparingDouble(other ->
                    top.skyeyefast.mchjong.client.TilePicking.distanceSquared(other, camera, ray, false))).orElseThrow();
                return frame.piece() + " screen=" + project(client, frame.piece().position()) + " nearest=" + nearest.piece();
            }).toList();
        throw new IllegalStateException("No physical source could be picked for " + view.phase() + " moving="
            + TableAnimation.of(table).moving(net.minecraft.Util.getMillis()) + " sources=" + sources);
    }

    private static net.minecraft.world.phys.Vec3 project(Minecraft client, net.minecraft.world.phys.Vec3 point) {
        var camera = client.gameRenderer.getMainCamera();
        double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
        var forward = new net.minecraft.world.phys.Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        var right = new net.minecraft.world.phys.Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        var delta = TableGeometry.world(CENTER, point).subtract(camera.getPosition());
        double fov = ((top.skyeyefast.mchjong.mixin.GameRendererAccessor) client.gameRenderer).mchjong$getFov(camera, 1, true);
        double scale = client.screen.height / (2 * Math.tan(Math.toRadians(fov) / 2)) / delta.dot(forward);
        return new net.minecraft.world.phys.Vec3(client.screen.width / 2.0 + delta.dot(right) * scale,
            client.screen.height / 2.0 - delta.dot(right.cross(forward)) * scale, 0);
    }
    private static void capture(Minecraft client, Path output, String file) {
        Screenshot.grab(output.toFile(), file, client.getMainRenderTarget(), ignored -> {});
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
