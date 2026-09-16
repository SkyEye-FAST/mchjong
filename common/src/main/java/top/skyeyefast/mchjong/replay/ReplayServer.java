package top.skyeyefast.mchjong.replay;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.network.ReplayPayload;
import top.skyeyefast.mchjong.network.TableNetworking;

public final class ReplayServer {
    private record State(ReplayStore store, Map<UUID, Long> requests) {}
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private ReplayServer() {}
    private static State state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new State(new ReplayStore(
            server.getWorldPath(LevelResource.ROOT).resolve("data/mchjong/replays"), TableNetworking.JSON), new HashMap<>()));
    }

    /** Failed writes remain in Game's persisted queue and can be retried without losing a finished hand. */
    public static boolean flush(MinecraftServer server, Game game) throws IOException {
        boolean changed = false;
        for (var match : game.pendingReplays()) {
            state(server).store().save(match);
            game.acknowledgeReplay(match.id());
            changed = true;
        }
        return changed;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mchjong")
            .then(Commands.literal("replays").executes(context -> send(context.getSource().getPlayerOrException(), null, 0))
                .then(Commands.argument("page", IntegerArgumentType.integer(1, 100_001)).executes(context ->
                    send(context.getSource().getPlayerOrException(), null, IntegerArgumentType.getInteger(context, "page") - 1))))
            .then(Commands.literal("replay").then(Commands.argument("match", UuidArgument.uuid()).executes(context ->
                send(context.getSource().getPlayerOrException(), UuidArgument.getUuid(context, "match"), 0)))));
    }

    private static int send(ServerPlayer player, UUID match, int page) throws CommandSyntaxException {
        State state = state(player.server);
        long now = System.nanoTime();
        state.requests().values().removeIf(time -> now - time >= 500_000_000L);
        if (state.requests().putIfAbsent(player.getUUID(), now) != null) {
            player.sendSystemMessage(Component.translatable("message.mchjong.replay_busy"));
            return 0;
        }
        try {
            Object value = match == null ? state.store().list(player.getUUID(), page) : state.store().load(player.getUUID(), match);
            for (var chunk : ReplayPayload.split(match == null ? ReplayPayload.Kind.INDEX : ReplayPayload.Kind.MATCH,
                    TableNetworking.JSON.toJson(value))) player.connection.send(new ClientboundCustomPayloadPacket(chunk));
            return 1;
        } catch (IOException | RuntimeException failure) {
            LoggerFactory.getLogger("mchjong").warn("Cannot read replay {} for {}", match, player.getUUID(), failure);
            player.sendSystemMessage(Component.translatable("message.mchjong.replay_unavailable"));
            return 0;
        }
    }
}
