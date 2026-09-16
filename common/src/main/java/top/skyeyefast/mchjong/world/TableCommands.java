package top.skyeyefast.mchjong.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.engine.TimeControl;

/** Common Brigadier handlers; loader entry points only register them. */
public final class TableCommands {
    private TableCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mchjong")
            .then(Commands.literal("invite").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                .executes(context -> TableInvitations.invite(context.getSource().getPlayerOrException(),
                    net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player")))))
            .then(Commands.literal("accept").then(Commands.argument("invitation", net.minecraft.commands.arguments.UuidArgument.uuid())
                .executes(context -> TableInvitations.respond(context.getSource().getPlayerOrException(),
                    net.minecraft.commands.arguments.UuidArgument.getUuid(context, "invitation"), true))))
            .then(Commands.literal("decline").then(Commands.argument("invitation", net.minecraft.commands.arguments.UuidArgument.uuid())
                .executes(context -> TableInvitations.respond(context.getSource().getPlayerOrException(),
                    net.minecraft.commands.arguments.UuidArgument.getUuid(context, "invitation"), false))))
            .then(Commands.literal("clock")
                .then(Commands.argument("reserve", IntegerArgumentType.integer(0, 600))
                    .then(Commands.argument("move", IntegerArgumentType.integer(1, 120)).executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        MahjongTableBlockEntity table = table(player);
                        boolean changed = table.participantGame(player).configureClock(player.getUUID(), new TimeControl(
                            IntegerArgumentType.getInteger(context, "reserve"), IntegerArgumentType.getInteger(context, "move")));
                        if (!changed) throw error("message.mchjong.host_lobby");
                        table.setChanged();
                        table.open(player);
                        return 1;
                    })))));
    }

    static CommandSyntaxException error(String key) {
        return new SimpleCommandExceptionType(Component.translatable(key)).create();
    }
    static MahjongTableBlockEntity table(ServerPlayer player) throws CommandSyntaxException {
        if (player.isAlive() && !player.isSpectator() && player.getVehicle() instanceof SeatEntity seat
            && player.serverLevel().getBlockEntity(seat.tablePos()) instanceof MahjongTableBlockEntity table
            && table.participantGame(player) != null) return table;
        throw error("message.mchjong.seat_required");
    }
}
