package top.skyeyefast.mchjong.network;

import java.util.Objects;
import java.util.function.UnaryOperator;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Loader wire encoding; payload contents and authorization remain shared. */
public final class PayloadPackets {
    private static UnaryOperator<CustomPacketPayload> encoder = UnaryOperator.identity();

    private PayloadPackets() {}

    public static void initialize(UnaryOperator<CustomPacketPayload> encoder) {
        PayloadPackets.encoder = Objects.requireNonNull(encoder);
    }

    public static ServerboundCustomPayloadPacket serverbound(CustomPacketPayload payload) {
        return new ServerboundCustomPayloadPacket(encoder.apply(payload));
    }

    public static ClientboundCustomPayloadPacket clientbound(CustomPacketPayload payload) {
        return new ClientboundCustomPayloadPacket(encoder.apply(payload));
    }
}
