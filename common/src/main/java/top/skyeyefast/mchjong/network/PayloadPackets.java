package top.skyeyefast.mchjong.network;

import io.netty.buffer.Unpooled;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;

/** Loader wire encoding; payload contents and authorization remain shared. */
public final class PayloadPackets {
    private static Function<MahjongPayload, Packet<?>> serverbound = payload ->
        new ServerboundCustomPayloadPacket(payload.id(), encode(payload));
    private static Function<MahjongPayload, Packet<?>> clientbound = payload ->
        new ClientboundCustomPayloadPacket(payload.id(), encode(payload));

    private PayloadPackets() {}

    public static void initialize(Function<MahjongPayload, Packet<?>> serverbound,
            Function<MahjongPayload, Packet<?>> clientbound) {
        PayloadPackets.serverbound = Objects.requireNonNull(serverbound);
        PayloadPackets.clientbound = Objects.requireNonNull(clientbound);
    }

    public static Packet<?> serverbound(MahjongPayload payload) { return serverbound.apply(payload); }
    public static Packet<?> clientbound(MahjongPayload payload) { return clientbound.apply(payload); }

    private static FriendlyByteBuf encode(MahjongPayload payload) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            payload.write(buffer);
            return buffer;
        } catch (RuntimeException failure) {
            buffer.release();
            throw failure;
        }
    }
}
