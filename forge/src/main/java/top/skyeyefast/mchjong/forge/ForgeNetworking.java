package top.skyeyefast.mchjong.forge;

import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.ForgePayload;
import net.minecraftforge.network.NetworkProtocol;
import top.skyeyefast.mchjong.network.*;

final class ForgeNetworking {
    private ForgeNetworking() {}

    static void register() {
        var channel = ChannelBuilder.named("mchjong:play").networkProtocolVersion(16)
            .payloadChannel().protocol(NetworkProtocol.PLAY)
            .serverbound()
            .addMain(VoiceChoicePayload.TYPE, VoiceChoicePayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) payload.handle(context.getSender());
            })
            .addMain(BoxPrintPayload.TYPE, BoxPrintPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) payload.handle(context.getSender());
            })
            .addMain(StickChoicePayload.TYPE, StickChoicePayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) payload.handle(context.getSender());
            })
            .addMain(RiichiActionPayload.TYPE, RiichiActionPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(TableRoomActionPayload.TYPE, TableRoomActionPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(McrNextHandPayload.TYPE, McrNextHandPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(McrActionPayload.TYPE, McrActionPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(SichuanActionPayload.TYPE, SichuanActionPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(SichuanNextHandPayload.TYPE, SichuanNextHandPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(TableVariantPayload.TYPE, TableVariantPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(RiichiControlPayload.TYPE, RiichiControlPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(TableSessionControlPayload.TYPE, TableSessionControlPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(RiichiHandOrderPayload.TYPE, RiichiHandOrderPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(TableSeatPayload.TYPE, TableSeatPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(RiichiRulesPayload.TYPE, RiichiRulesPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .addMain(RiichiVisibilityPayload.TYPE, RiichiVisibilityPayload.CODEC, (payload, context) -> {
                if (context.getSender() != null) TableNetworking.receive(context.getSender(), payload);
            })
            .clientbound()
            .addMain(VoiceAppearancePayload.TYPE, VoiceAppearancePayload.CODEC,
                (payload, context) -> top.skyeyefast.mchjong.client.VoicePresets.receive(payload))
            .addMain(RiichiViewPayload.TYPE, RiichiViewPayload.CODEC,
                (payload, context) -> top.skyeyefast.mchjong.client.ClientRiichiNetworking.receive(payload))
            .addMain(McrViewPayload.TYPE, McrViewPayload.CODEC,
                (payload, context) -> top.skyeyefast.mchjong.client.ClientMcrNetworking.receive(payload))
            .addMain(SichuanViewPayload.TYPE, SichuanViewPayload.CODEC,
                (payload, context) -> top.skyeyefast.mchjong.client.ClientSichuanNetworking.receive(payload))
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
