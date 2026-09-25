package top.skyeyefast.mchjong.forge;

import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.ForgePayload;
import net.minecraftforge.network.NetworkProtocol;
import top.skyeyefast.mchjong.network.*;

final class ForgeNetworking {
    private ForgeNetworking() {}

    static void register() {
        var channel = ChannelBuilder.named("mchjong:play").networkProtocolVersion(10)
            .payloadChannel().protocol(NetworkProtocol.PLAY)
            .serverbound()
            .addMain(BoxPrintPayload.TYPE, BoxPrintPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) payload.handle(context.getSender());
            })
            .addMain(BoxBackPayload.TYPE, BoxBackPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) payload.handle(context.getSender());
            })
            .addMain(StickChoicePayload.TYPE, StickChoicePayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) payload.handle(context.getSender());
            })
            .addMain(TableActionPayload.TYPE, TableActionPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(TableControlPayload.TYPE, TableControlPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(TableSeatPayload.TYPE, TableSeatPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(TableRulesPayload.TYPE, TableRulesPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(TableVisibilityPayload.TYPE, TableVisibilityPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .clientbound()
            .addMain(TableViewPayload.TYPE, TableViewPayload.CODEC,
                (payload, context) -> top.skyeyefast.mchjong.client.ClientTableNetworking.receive(payload))
            .addMain(ReplayPayload.TYPE, ReplayPayload.CODEC,
                (payload, context) -> top.skyeyefast.mchjong.client.ClientReplays.receive(payload))
            .addMain(PresetBundlePayload.TYPE, PresetBundlePayload.CODEC,
                (payload, context) -> top.skyeyefast.mchjong.client.TileFacePresets.receive(payload))
            .addMain(StickAppearancePayload.TYPE, StickAppearancePayload.CODEC,
                (payload, context) -> top.skyeyefast.mchjong.client.RiichiStickPresets.receive(payload))
            .build();
        PayloadPackets.initialize(payload -> ForgePayload.create(payload.type().id(), buffer -> channel.encode(buffer, payload)));
    }
}
