package top.skyeyefast.mchjong.network;

import top.skyeyefast.mchjong.platform.ResourceIds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Bounded S2C chunks, sent only in response to a participant's explicit replay request. */
public record ReplayPayload(UUID transfer, Kind kind, int part, int parts, String text) implements MahjongPayload {
    public enum Kind { INDEX, MATCH }
    public static final int CHUNK_SIZE = 16_384;
    public static final int MAX_PARTS = 512;
    public static final ResourceLocation TYPE = ResourceIds.of(MahjongContent.MOD_ID, "replay");
    public static ReplayPayload decode(FriendlyByteBuf buffer) {
        return new ReplayPayload(buffer.readUUID(), buffer.readEnum(Kind.class), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readUtf(CHUNK_SIZE));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(transfer()); buffer.writeEnum(kind()); buffer.writeVarInt(part());
        buffer.writeVarInt(parts()); buffer.writeUtf(text(), CHUNK_SIZE);
    }

    public ReplayPayload {
        java.util.Objects.requireNonNull(transfer); java.util.Objects.requireNonNull(kind);
        if (parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts || text.length() > CHUNK_SIZE)
            throw new IllegalArgumentException("Invalid replay chunk");
    }
    @Override public ResourceLocation id() { return TYPE; }

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
