package top.skyeyefast.mchjong.forge;

import top.skyeyefast.mchjong.platform.ResourceIds;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import top.skyeyefast.mchjong.network.*;

final class ForgeNetworking {
    private ForgeNetworking() {}

    static void register() {
        var channel = NetworkRegistry.ChannelBuilder.named(ResourceIds.of("mchjong", "play"))
            .networkProtocolVersion(() -> "22").clientAcceptedVersions("22"::equals).serverAcceptedVersions("22"::equals).simpleChannel();
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
        channel.messageBuilder(NextHandPayload.class, 5, NetworkDirection.PLAY_TO_SERVER)
            .encoder(NextHandPayload::write).decoder(NextHandPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TableActionPayload.class, 6, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TableActionPayload::write).decoder(TableActionPayload::decode).consumerMainThread((payload, context) -> {
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
        channel.messageBuilder(YakuSetsPayload.class, 30, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(YakuSetsPayload::write).decoder(YakuSetsPayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.ClientYakuSets.receive(payload)).add();
        PayloadPackets.initialize(payload -> channel.toVanillaPacket(payload, NetworkDirection.PLAY_TO_SERVER),
            payload -> channel.toVanillaPacket(payload, NetworkDirection.PLAY_TO_CLIENT));
    }
}
