package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.PlayerPresence;
import top.skyeyefast.mchjong.engine.TableRoomView;

/** Shared member labels for the preparation roster, match roster and HUD. */
final class RoomMembers {
    private RoomMembers() {}
    static Component name(TableRoomView room, int seat) {
        var player = room.seats().get(seat).participant();
        return player.id() == null ? Component.translatable("room.mchjong.empty") : Component.literal(player.name());
    }
    static Component status(TableRoomView room, List<top.skyeyefast.mchjong.engine.ExternalBot> bots, int seat) {
        var state = room.seats().get(seat); var player = state.participant();
        if (player.id() == null) return Component.empty();
        var result = (player.bot() ? botName(room, bots, seat) : presence(state.presence())).copy();
        if (player.bot() && state.presence() != PlayerPresence.SEATED) result.append("  ").append(presence(state.presence()));
        if (player.ready()) result.append("  ").append(Component.translatable("ui.mchjong.ready"));
        if (state.wind() >= 0) result = Component.translatable("room.mchjong.member", wind(state.wind()), result);
        if (seat == room.host()) result = Component.translatable("ui.mchjong.annotation", result, Component.translatable("room.mchjong.host.short"));
        return result;
    }
    static Component botName(TableRoomView room, List<top.skyeyefast.mchjong.engine.ExternalBot> externalBots, int seat) {
        var state = room.seats().get(seat);
        if (state.participant().externalBotId() != null) return externalBots.stream()
            .filter(bot -> bot.id().equals(state.participant().externalBotId()))
            .findFirst().<Component>map(bot -> Component.literal(bot.name()))
            .orElse(Component.literal(state.participant().externalBotId()));
        return Component.translatable(state.participant().difficulty().translationKey());
    }

    static Component wind(int wind) {
        return Component.translatable(wind < 0 ? "room.mchjong.wind_unknown"
            : "wind.mchjong." + new String[]{"east", "south", "west", "north"}[wind]);
    }

    static Component presence(PlayerPresence presence) {
        if (presence == null) return Component.translatable("room.mchjong.empty");
        return Component.translatable(presenceKey(presence));
    }

    private static String presenceKey(PlayerPresence presence) {
        return switch (presence) {
            case SEATED -> "room.mchjong.present";
            case AWAY -> "room.mchjong.away";
            case DISCONNECTED -> "room.mchjong.disconnected";
        };
    }

}
