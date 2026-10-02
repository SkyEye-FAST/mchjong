package top.skyeyefast.mchjong.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.SpectatorHandVisibility;
import top.skyeyefast.mchjong.engine.TimeControl;

/** Common Brigadier handlers; loader entry points only register them. */
public final class TableCommands {
    private TableCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        top.skyeyefast.mchjong.replay.ReplayServer.register(dispatcher);
        var world = Commands.literal("world").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .executes(context -> showWorld(context.getSource()))
            .then(Commands.literal("reload").executes(context -> {
                try { WorldSettings.of(context.getSource().getServer()).reload(); }
                catch (java.io.IOException failure) { throw error("message.mchjong.world_settings_failed"); }
                return showWorld(context.getSource());
            }));
        for (String setting : new String[]{"invitationTeleport", "invitationsEnabled", "spectatingEnabled",
                "allowConvenienceHints", "allowExperienceRewards", "deductNegativeExperience", "replaysEnabled",
                "allowBots", "allowCompanionPlayers", "allowCustomRules"})
            world.then(Commands.literal(setting).then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                .executes(context -> {
                    try { WorldSettings.of(context.getSource().getServer()).set(setting,
                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled")); }
                    catch (java.io.IOException failure) { throw error("message.mchjong.world_settings_failed"); }
                    return showWorld(context.getSource());
                })));
        world.then(Commands.literal("spectatorHandVisibility")
            .then(Commands.argument("visibility", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(SpectatorHandVisibility.values())
                        .map(value -> value.name().toLowerCase(java.util.Locale.ROOT)).toList(), builder))
                .executes(context -> {
                    try {
                        String value = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "visibility");
                        WorldSettings.of(context.getSource().getServer()).setSpectatorHandVisibility(
                            SpectatorHandVisibility.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
                    } catch (java.io.IOException | IllegalArgumentException failure) {
                        throw error("message.mchjong.world_settings_failed");
                    }
                    return showWorld(context.getSource());
                })));
        world.then(Commands.literal("maxExperienceChange")
            .then(Commands.argument("amount", IntegerArgumentType.integer(0, top.skyeyefast.mchjong.engine.WorldPolicy.MAX_EXPERIENCE_LIMIT))
                .executes(context -> {
                    try { WorldSettings.of(context.getSource().getServer()).setMaxExperienceChange(
                        IntegerArgumentType.getInteger(context, "amount")); }
                    catch (java.io.IOException | IllegalArgumentException failure) {
                        throw error("message.mchjong.world_settings_failed");
                    }
                    return showWorld(context.getSource());
                })));
        world.then(Commands.literal("forcedPreset")
            .then(Commands.argument("preset", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    java.util.stream.Stream.concat(java.util.stream.Stream.of("none"), java.util.Arrays.stream(RiichiPreset.values())
                        .map(value -> value.name().toLowerCase(java.util.Locale.ROOT))).toList(), builder))
                .executes(context -> {
                    try {
                        String value = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "preset");
                        WorldSettings.of(context.getSource().getServer()).setForcedPreset(value.equalsIgnoreCase("none") ? null
                            : RiichiPreset.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
                    } catch (java.io.IOException | IllegalArgumentException failure) {
                        throw error("message.mchjong.world_settings_failed");
                    }
                    return showWorld(context.getSource());
                })));
        dispatcher.register(Commands.literal("mchjong")
            .then(world)
            .then(Commands.literal("host").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ServerPlayer successor = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
                    MahjongTableBlockEntity table = table(player);
                    if (table.participantSession(successor) == null
                        || !table.participantSession(player).transferHost(player.getUUID(), successor.getUUID()))
                        throw error("message.mchjong.host_transfer_failed");
                    table.setChanged();
                    player.sendSystemMessage(Component.translatable("message.mchjong.host_transferred", successor.getDisplayName()));
                    successor.sendSystemMessage(Component.translatable("message.mchjong.host_transferred", successor.getDisplayName()));
                    table.open(player);
                    return 1;
                })))
            .then(Commands.literal("invite").then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    context.getSource().getServer().getPlayerNames(), builder))
                .executes(context -> {
                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player");
                    var players = context.getSource().getServer().getPlayerList();
                    ServerPlayer target;
                    try { target = players.getPlayer(java.util.UUID.fromString(name)); }
                    catch (IllegalArgumentException ignored) { target = players.getPlayerByName(name); }
                    if (target == null) throw error("message.mchjong.invite_unavailable");
                    return TableInvitations.invite(context.getSource().getPlayerOrException(), target);
                })))
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
                        MahjongTableBlockEntity table = clockTable(player);
                        boolean changed = table.participantRoom(player).configureClock(player.getUUID(), new TimeControl(
                            IntegerArgumentType.getInteger(context, "reserve"), IntegerArgumentType.getInteger(context, "move")));
                        if (!changed) throw error("message.mchjong.host_lobby");
                        table.setChanged();
                        table.open(player);
                        return 1;
                    })))));
    }

    private static int showWorld(CommandSourceStack source) {
        var policy = WorldSettings.of(source.getServer()).policy();
        source.sendSuccess(() -> Component.translatable("message.mchjong.world_settings"), false);
        show(source, "settings.mchjong.invitations_enabled", policy.invitationsEnabled());
        show(source, "settings.mchjong.invitation_teleport", policy.invitationTeleport());
        show(source, "settings.mchjong.spectating_enabled", policy.spectatingEnabled());
        source.sendSuccess(() -> Component.translatable("settings.mchjong.spectator_hand_visibility",
            Component.translatable("settings.mchjong.spectator_hand_visibility."
                + policy.spectatorHandVisibility().name().toLowerCase(java.util.Locale.ROOT))), false);
        show(source, "settings.mchjong.allow_convenience_hints", policy.allowConvenienceHints());
        show(source, "settings.mchjong.allow_experience_rewards", policy.allowExperienceRewards());
        show(source, "settings.mchjong.deduct_negative_experience", policy.deductNegativeExperience());
        source.sendSuccess(() -> Component.translatable("settings.mchjong.max_experience_change", policy.maxExperienceChange()), false);
        show(source, "settings.mchjong.replays_enabled", policy.replaysEnabled());
        show(source, "settings.mchjong.allow_bots", policy.allowBots());
        show(source, "settings.mchjong.allow_companion_players", policy.allowCompanionPlayers());
        show(source, "settings.mchjong.allow_custom_rules", policy.allowCustomRules());
        source.sendSuccess(() -> Component.translatable("settings.mchjong.forced_preset",
            policy.forcedPreset() == null ? Component.translatable("settings.mchjong.none")
                : Component.translatable(policy.forcedPreset().translationKey())), false);
        return 1;
    }

    private static void show(CommandSourceStack source, String key, boolean enabled) {
        source.sendSuccess(() -> Component.translatable("settings.mchjong.toggle", Component.translatable(key),
            Component.translatable(enabled ? "options.on" : "options.off")), false);
    }

    static CommandSyntaxException error(String key) {
        return new SimpleCommandExceptionType(Component.translatable(key)).create();
    }
    static MahjongTableBlockEntity table(ServerPlayer player) throws CommandSyntaxException {
        var table = clockTable(player);
        if (table.participantSession(player) != null) return table;
        throw error("message.mchjong.seat_required");
    }
    private static MahjongTableBlockEntity clockTable(ServerPlayer player) throws CommandSyntaxException {
        if (player.isAlive() && !player.isSpectator() && player.getVehicle() instanceof SeatEntity seat
            && player.level().getBlockEntity(seat.tablePos()) instanceof MahjongTableBlockEntity table
            && table.participantRoom(player) != null) return table;
        throw error("message.mchjong.seat_required");
    }
}
