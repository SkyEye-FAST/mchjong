package top.skyeyefast.mchjong.forge;

import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import top.skyeyefast.mchjong.network.*;

final class ForgeNetworking {
    private ForgeNetworking() {}

    static void register() {
        var channel = NetworkRegistry.ChannelBuilder.named(new net.minecraft.resources.ResourceLocation("mchjong", "play"))
            .networkProtocolVersion(() -> "8").clientAcceptedVersions("8"::equals).serverAcceptedVersions("8"::equals).simpleChannel();
        channel.messageBuilder(BoxPrintPayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder(BoxPrintPayload::write).decoder(BoxPrintPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) payload.handle(context.get().getSender());
            }).add();
        channel.messageBuilder(TableActionPayload.class, 1, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TableActionPayload::write).decoder(TableActionPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TableControlPayload.class, 2, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TableControlPayload::write).decoder(TableControlPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TableSeatPayload.class, 3, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TableSeatPayload::write).decoder(TableSeatPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TableRulesPayload.class, 4, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TableRulesPayload::write).decoder(TableRulesPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TableVisibilityPayload.class, 5, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TableVisibilityPayload::write).decoder(TableVisibilityPayload::decode).consumerMainThread((payload, context) -> {
                if (context.get().getSender() != null) TableNetworking.receive(context.get().getSender(), payload);
            }).add();
        channel.messageBuilder(TableViewPayload.class, 6, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(TableViewPayload::write).decoder(TableViewPayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.ClientTableNetworking.receive(payload)).add();
        channel.messageBuilder(ReplayPayload.class, 7, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ReplayPayload::write).decoder(ReplayPayload::decode)
            .consumerMainThread((payload, context) -> top.skyeyefast.mchjong.client.ClientReplays.receive(payload)).add();
        PayloadPackets.initialize(payload -> channel.toVanillaPacket(payload, NetworkDirection.PLAY_TO_SERVER),
            payload -> channel.toVanillaPacket(payload, NetworkDirection.PLAY_TO_CLIENT));
    }
}
