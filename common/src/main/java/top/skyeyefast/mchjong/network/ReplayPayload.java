package top.skyeyefast.mchjong.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Bounded S2C chunks, sent only in response to a participant's explicit replay request. */
public record ReplayPayload(UUID transfer, Kind kind, int part, int parts, String text) implements CustomPacketPayload {
    public enum Kind { INDEX, MATCH }
    public static final int CHUNK_SIZE = 16_384;
    public static final int MAX_PARTS = 512;
    public static final Type<ReplayPayload> TYPE = new Type<>(net.minecraft.resources.ResourceLocation
        .fromNamespaceAndPath(MahjongContent.MOD_ID, "replay"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReplayPayload> CODEC = new StreamCodec<>() {
        @Override public ReplayPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ReplayPayload(buffer.readUUID(), buffer.readEnum(Kind.class), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(CHUNK_SIZE));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, ReplayPayload value) {
            buffer.writeUUID(value.transfer()); buffer.writeEnum(value.kind()); buffer.writeVarInt(value.part());
            buffer.writeVarInt(value.parts()); buffer.writeUtf(value.text(), CHUNK_SIZE);
        }
    };

    public ReplayPayload {
        java.util.Objects.requireNonNull(transfer); java.util.Objects.requireNonNull(kind);
        if (parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts || text.length() > CHUNK_SIZE)
            throw new IllegalArgumentException("Invalid replay chunk");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static List<ReplayPayload> split(Kind kind, String text) {
        var chunks = new ArrayList<String>();
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            // Do not split a supplementary character across independent UTF-8 encodings.
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            chunks.add(text.substring(start, end));
            if (chunks.size() > MAX_PARTS) throw new IllegalArgumentException("Replay is too large to transfer");
            start = end;
        }
        if (chunks.isEmpty()) chunks.add("");
        UUID id = UUID.randomUUID();
        var result = new ArrayList<ReplayPayload>();
        for (int i = 0; i < chunks.size(); i++) result.add(new ReplayPayload(id, kind, i, chunks.size(), chunks.get(i)));
        return result;
    }
}
