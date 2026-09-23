package top.skyeyefast.mchjong.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Native client input and use-block packets, including the sneak/dismount race. */
final class StoolInteractionSmoke {
    private int step, ticks;
    private CompletableFuture<Void> verification;

    boolean tick(Minecraft client, BlockPos center, Path output) throws java.io.IOException {
        if (step == 0) {
            client.setScreen(null);
            client.player.getInventory().setSelectedSlot(8);
            require(client.player.getMainHandItem().isEmpty() && client.player.getOffhandItem().isEmpty(),
                "Stool interaction needs empty hands to exercise vanilla sneaking block use");
            client.options.keyShift.setDown(true);
            step = 1;
        }
        if (step >= 2 && step <= 4)
            require(!client.player.isPassenger() && client.screen == null, "Sneak-click mounted or opened table controls");
        if (++ticks < 10) return false;
        if (verification != null) {
            if (!verification.isDone()) return false;
            verification.join();
            verification = null;
        }
        var stool = TableGeometry.stool(center, 0);
        if (step == 1 || step == 3) {
            require(client.player.isShiftKeyDown(), "Sneak input did not reach the client");
            client.player.setDeltaMovement(step == 3 ? new Vec3(0, 0, -.3) : Vec3.ZERO);
            use(client, stool);
            step++;
        } else if (step == 2 || step == 4) {
            require(!client.player.isPassenger() && client.screen == null, "Sneak-click mounted or opened table controls");
            require(client.level.noCollision(client.player), "Sneak-click left the client inside furniture");
            var id = client.player.getUUID();
            verification = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                var level = player.level();
                require(!player.isPassenger(), "Sneak-click mounted the server player");
                require(((MahjongTableBlockEntity) level.getBlockEntity(center)).participantGame(player) == null,
                    "Sneak-click joined the room");
                require(level.getEntitiesOfClass(SeatEntity.class, new AABB(stool).inflate(1)).isEmpty(),
                    "Sneak-click created a seat entity");
                require(level.noCollision(player), "Sneak-click left the server player inside furniture");
            });
            step++;
        } else if (step == 5) {
            net.minecraft.client.Screenshot.grab(output.toFile(), "00-stool-sneak-momentum.png",
                client.getMainRenderTarget(), 1, ignored -> {});
            client.options.keyShift.setDown(false);
            step = 6;
        } else if (step == 6) {
            require(!client.player.isShiftKeyDown(), "Sneak input did not release");
            use(client, stool);
            step = 7;
        } else if (step == 7) {
            require(client.player.getVehicle() instanceof SeatEntity && client.screen instanceof TableScreen,
                "Ordinary stool click did not mount and open controls");
            var view = ((MahjongTableBlockEntity) client.level.getBlockEntity(center)).clientView();
            require(view != null && view.viewerSeat() == 0, "Ordinary stool click opened a spectator view");
            var id = client.player.getUUID();
            verification = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                require(player.getVehicle() instanceof SeatEntity, "Ordinary click did not mount the server player");
                var mount = player.getVehicle();
                require(Math.abs(mount.getY() - center.getY() - TableGeometry.STOOL_HEIGHT) < 1e-6,
                    "Seat anchor differs from cushion height");
                ((MahjongTableBlockEntity) player.level().getBlockEntity(center)).sit(player, 0);
                require(player.getVehicle() == mount, "Reopening the stool created a duplicate mount");
            });
            step = 8;
        } else if (step == 8) {
            Files.writeString(output.resolve("stool-interaction.txt"),
                "PASS: empty-hand sneak-click at rest and with momentum preserves collision, creates no mount or room membership, and opens no UI. Releasing sneak permits ordinary click to mount and open the seated view. Reopening retains the same mount.\n");
            return true;
        }
        ticks = 0;
        return false;
    }

    private static void use(Minecraft client, BlockPos stool) {
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(stool), Direction.UP, stool, false));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
