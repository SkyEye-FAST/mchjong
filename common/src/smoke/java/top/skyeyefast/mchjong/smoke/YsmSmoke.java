package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Exercises YSM's real native runtime, model selection and synchronized seat vehicle. */
final class YsmSmoke {
    private CompletableFuture<Void> selection;
    private CompletableFuture<Void> seating;
    private int readyTicks, seatedTicks;

    boolean prepare(Minecraft client) throws ReflectiveOperationException {
        if (selection == null) {
            if (!Boolean.TRUE.equals(Class.forName("com.elfmcys.yesstevemodel.YesSteveModel")
                .getMethod("isAvailable").invoke(null)))
                throw new IllegalStateException("YSM native runtime did not initialize; an installed jar is not runtime acceptance");
            var id = client.player.getUUID();
            var server = client.getSingleplayerServer();
            selection = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(id);
                try {
                    server.getCommands().getDispatcher().execute("ysm model set @s default - true",
                        player.createCommandSourceStack().withPermission(4));
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                    throw new IllegalStateException("Cannot select the built-in YSM model", failure);
                }
            });
        }
        if (!selection.isDone()) return false;
        selection.join();
        return ++readyTicks >= 100;
    }

    boolean seat(Minecraft client, BlockPos tablePos, Path output) {
        if (seating == null) {
            var id = client.player.getUUID();
            var server = client.getSingleplayerServer();
            seating = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(id);
                var table = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(tablePos);
                table.sit(player, 0);
                if (!(player.getVehicle() instanceof SeatEntity))
                    throw new IllegalStateException("YSM fixture did not mount the real stool");
            });
        }
        if (!seating.isDone()) return false;
        seating.join();
        if (!(client.player.getVehicle() instanceof SeatEntity)) return false;
        client.setScreen(null);
        client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        client.player.setXRot(10);
        if (++seatedTicks < 40) return false;
        SmokeScreenshots.grab(output.toFile(), "07-ysm-seated.png", client.getMainRenderTarget(), ignored -> {});
        client.options.setCameraType(CameraType.FIRST_PERSON);
        return true;
    }
}
