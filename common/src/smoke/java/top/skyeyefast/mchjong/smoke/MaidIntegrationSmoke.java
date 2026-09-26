package top.skyeyefast.mchjong.smoke;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import top.skyeyefast.mchjong.compat.maid.MaidMahjongTask;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.BotDifficulty;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.TrainingBot;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** The mainline maid fixture, adapted only at Minecraft entity save and input boundaries. */
final class MaidIntegrationSmoke {
    private int step, ticks, stageTicks;
    private UUID maidId;
    private CompoundTag saved;
    private int maidSeat;
    private boolean capturedLobby;
    private CompletableFuture<Boolean> work;

    boolean tick(Minecraft client, BlockPos center, Path output) {
        if (++ticks > 1600) throw new IllegalStateException("Maid smoke timed out at " + step);
        if (++stageTicks < 20) return false;
        if (work != null) {
            if (!work.isDone()) return false;
            boolean done = work.join();
            work = null;
            if (!done) { stageTicks = 0; return false; }
            step++;
            stageTicks = 0;
            return false;
        }
        if (step == 2 || step == 7 || step == 8) {
            if (step == 2) require(!Component.translatable("model.touhou_little_maid.hakurei_reimu.name").getString().startsWith("model."),
                "Default maid model name is not localized");
            if (step == 2) {
                var clientMaid = client.level.getEntitiesOfClass(EntityMaid.class, new net.minecraft.world.phys.AABB(center).inflate(10))
                    .stream().filter(entity -> entity.getUUID().equals(maidId)).findFirst().orElseThrow();
                var vehicle = clientMaid.getVehicle();
                require(vehicle instanceof SeatEntity, "Client maid is not mounted on a Mahjong seat");
                require(clientMaid.position().distanceTo(vehicle.position()) < 0.3,
                    "Client maid is away from seat: maid=" + clientMaid.position() + ", seat=" + vehicle.position());
            }
            if (step == 8) require(client.screen.width == 320 && client.screen.height == 240, "Maid small viewport is not 320x240");
            SmokeScreenshots.grab(output.toFile(), step == 2 && capturedLobby ? "maid-seating.png" : "maid-" + step + ".png",
                client.getMainRenderTarget(), 1, ignored -> {});
            if (step == 2 && !capturedLobby) {
                capturedLobby = true;
                client.setScreen(null);
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                stageTicks = 0;
                return false;
            }
            if (step == 2) client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            if (step == 7) {
                client.getWindow().setWindowed(960, 720);
                client.options.guiScale().set(3);
                client.resizeGui();
            }
            if (step == 8) {
                client.getWindow().setWindowed(1280, 800);
                client.options.guiScale().set(2);
                client.resizeGui();
            }
            step++;
            stageTicks = 0;
            return false;
        }
        if (step == 11) return true;
        UUID ownerId = client.player.getUUID();
        int currentStep = step;
        work = client.getSingleplayerServer().submit(() -> {
            ServerPlayer owner = client.getSingleplayerServer().getPlayerList().getPlayer(ownerId);
            var level = owner.level();
            var table = (MahjongTableBlockEntity) level.getBlockEntity(center);
            Game game = table.participantGame(owner);
            if (currentStep == 0) {
                require(game != null, "Owner is not a participant");
                var task = TaskManager.findTask(MaidMahjongTask.ID).orElseThrow(() -> new IllegalStateException("Maid task extension not discovered"));
                var maid = new EntityMaid(level);
                maid.tame(owner);
                maid.setRideable(true);
                maid.setModelId("touhou_little_maid:hakurei_reimu");
                var stool = TableGeometry.stool(center, 1);
                maid.snapTo(stool.getX() + 1.5, stool.getY(), stool.getZ() + .5, 90, 0);
                maid.setSchedule(MaidSchedule.ALL);
                maid.setTask(task);
                maidId = maid.getUUID();
                level.addFreshEntity(maid);
                return true;
            }
            if (currentStep == 4) {
                var restored = new EntityMaid(level);
                restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
                require(restored.getUUID().equals(maidId), "Maid save lost identity");
                level.addFreshEntity(restored);
                return true;
            }
            var maid = (EntityMaid) level.getEntity(maidId);
            require(maid != null, "Maid entity disappeared");
            if (currentStep == 1 || currentStep == 5) {
                if (!(maid.getVehicle() instanceof SeatEntity seat)) return false;
                require(game.entityBot(maidId) && game.seatOf(maidId) == seat.seat(), "Maid mount and game membership differ");
                require(!game.view(null).seats().get(seat.seat()).name().isBlank(), "Maid model name lost");
                if (currentStep == 5) {
                    act(table, owner, Action.Type.FILL_BOTS);
                    act(table, owner, Action.Type.BEGIN_SEATING);
                }
                return true;
            }
            if (currentStep == 3) {
                table.open(owner);
                var data = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                maid.saveWithoutId(data);
                saved = data.buildResult();
                var seat = maid.getVehicle();
                maid.stopRiding();
                seat.discard();
                maid.discard();
                return true;
            }
            if (currentStep == 6) {
                if (game == null) return false;
                require(maid.getVehicle() instanceof SeatEntity seat && seat.seat() == game.seatOf(maidId),
                    "Wind assignment failed to move the maid: assigned=" + game.seatOf(maidId) + ", vehicle=" + maid.getVehicle());
                if (game.phase() == Game.Phase.LOBBY) {
                    act(table, owner, Action.Type.READY);
                    return false;
                }
                var view = game.view(ownerId);
                if (!view.actions().isEmpty()) table.act(owner, new TableActionPayload(center, game.tableId(), view.decision(), TrainingBot.choose(view, BotDifficulty.EASY)));
                maidSeat = game.seatOf(maidId);
                game.validate();
                return !game.view(null).seats().get(maidSeat).river().isEmpty();
            }
            if (currentStep == 9) {
                maid.setTask(TaskManager.getIdleTask());
                return true;
            }
            if (currentStep == 10) {
                if (maid.isPassenger()) return false;
                require(!game.entityBot(maidId) && game.seatOf(maidId) < 0 && game.trainingSeat(maidSeat), "Task change did not release the maid while preserving play");
                require(maid.isRideable(), "Task cleanup did not restore vehicle-follow preference");
                game.validate();
                return true;
            }
            throw new IllegalStateException("Unexpected maid smoke step " + currentStep);
        });
        return false;
    }

    private static void act(MahjongTableBlockEntity table, ServerPlayer owner, Action.Type type) {
        var game = table.participantGame(owner);
        var view = game.view(owner.getUUID());
        for (int index = 0; index < view.actions().size(); index++) if (view.actions().get(index).type() == type) {
            table.act(owner, new TableActionPayload(table.getBlockPos(), game.tableId(), view.decision(), index));
            return;
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
