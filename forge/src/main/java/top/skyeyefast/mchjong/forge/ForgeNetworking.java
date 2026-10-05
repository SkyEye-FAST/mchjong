package top.skyeyefast.mchjong.forge;

import top.skyeyefast.mchjong.platform.ResourceIds;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import top.skyeyefast.mchjong.network.*;

final class ForgeNetworking {
    private ForgeNetworking() {}

    static void register() {
        var channel = NetworkRegistry.ChannelBuilder.named(ResourceIds.of("mchjong", "play"))
            .networkProtocolVersion(() -> "19").clientAcceptedVersions("19"::equals).serverAcceptedVersions("19"::equals).simpleChannel();
        channel.messageBuilder(VoiceChoicePayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder(VoiceChoicePayload::write).decoder(VoiceChoicePayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) payload.handle(context.get().getSender());
            }).add();
        channel.messageBuilder(BoxPrintPayload.class, 1, NetworkDirection.PLAY_TO_SERVER)
            .encoder(BoxPrintPayload::write).decoder(BoxPrintPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) payload.handle(context.get().getSender());
            }).add();
        channel.messageBuilder(StickChoicePayload.class, 2, NetworkDirection.PLAY_TO_SERVER)
            .encoder(StickChoicePayload::write).decoder(StickChoicePayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) payload.handle(context.get().getSender());
            }).add();
        channel.messageBuilder(RiichiActionPayload.class, 3, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RiichiActionPayload::write).decoder(RiichiActionPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TableRoomActionPayload.class, 4, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TableRoomActionPayload::write).decoder(TableRoomActionPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(McrNextHandPayload.class, 5, NetworkDirection.PLAY_TO_SERVER)
            .encoder(McrNextHandPayload::write).decoder(McrNextHandPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(McrActionPayload.class, 6, NetworkDirection.PLAY_TO_SERVER)
            .encoder(McrActionPayload::write).decoder(McrActionPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(SichuanActionPayload.class, 7, NetworkDirection.PLAY_TO_SERVER)
            .encoder(SichuanActionPayload::write).decoder(SichuanActionPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(SichuanNextHandPayload.class, 8, NetworkDirection.PLAY_TO_SERVER)
            .encoder(SichuanNextHandPayload::write).decoder(SichuanNextHandPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TableVariantPayload.class, 9, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TableVariantPayload::write).decoder(TableVariantPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(MatchAutomationPayload.class, 10, NetworkDirection.PLAY_TO_SERVER)
            .encoder(MatchAutomationPayload::write).decoder(MatchAutomationPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(RiichiControlPayload.class, 11, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RiichiControlPayload::write).decoder(RiichiControlPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TableSessionControlPayload.class, 12, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TableSessionControlPayload::write).decoder(TableSessionControlPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(RiichiHandOrderPayload.class, 13, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RiichiHandOrderPayload::write).decoder(RiichiHandOrderPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TableSeatPayload.class, 14, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TableSeatPayload::write).decoder(TableSeatPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(RiichiRulesPayload.class, 15, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RiichiRulesPayload::write).decoder(RiichiRulesPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(SichuanRulesPayload.class, 16, NetworkDirection.PLAY_TO_SERVER)
            .encoder(SichuanRulesPayload::write).decoder(SichuanRulesPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(RiichiVisibilityPayload.class, 17, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RiichiVisibilityPayload::write).decoder(RiichiVisibilityPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(VoiceAppearancePayload.class, 18, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(VoiceAppearancePayload::write).decoder(VoiceAppearancePayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.VoicePresets.receive(payload)).add();
        channel.messageBuilder(RiichiViewPayload.class, 19, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(RiichiViewPayload::write).decoder(RiichiViewPayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.ClientRiichiNetworking.receive(payload)).add();
        channel.messageBuilder(McrViewPayload.class, 20, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(McrViewPayload::write).decoder(McrViewPayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.ClientMcrNetworking.receive(payload)).add();
        channel.messageBuilder(SichuanViewPayload.class, 21, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SichuanViewPayload::write).decoder(SichuanViewPayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.ClientSichuanNetworking.receive(payload)).add();
        channel.messageBuilder(ReplayPayload.class, 22, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ReplayPayload::write).decoder(ReplayPayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.ClientReplays.receive(payload)).add();
        channel.messageBuilder(PresetBundlePayload.class, 23, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(PresetBundlePayload::write).decoder(PresetBundlePayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.TileFacePresets.receive(payload)).add();
        channel.messageBuilder(StickAppearancePayload.class, 24, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(StickAppearancePayload::write).decoder(StickAppearancePayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.RiichiStickPresets.receive(payload)).add();
        channel.messageBuilder(TaiwanActionPayload.class, 25, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TaiwanActionPayload::write).decoder(TaiwanActionPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TaiwanNextHandPayload.class, 26, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TaiwanNextHandPayload::write).decoder(TaiwanNextHandPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TaiwanRulesPayload.class, 27, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TaiwanRulesPayload::write).decoder(TaiwanRulesPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TaiwanClockPayload.class, 28, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TaiwanClockPayload::write).decoder(TaiwanClockPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TaiwanViewPayload.class, 29, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(TaiwanViewPayload::write).decoder(TaiwanViewPayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.ClientTaiwanNetworking.receive(payload)).add();
        PayloadPackets.initialize(payload -> channel.toVanillaPacket(payload, NetworkDirection.PLAY_TO_SERVER),
            payload -> channel.toVanillaPacket(payload, NetworkDirection.PLAY_TO_CLIENT));
    }
}
