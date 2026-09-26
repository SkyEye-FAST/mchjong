package top.skyeyefast.mchjong.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import top.skyeyefast.mchjong.config.PresetArchives;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Bounded binary chunks for one server cosmetic category. */
public record PresetBundlePayload(PresetArchives.Kind kind, UUID transfer, int part, int parts, byte[] data) implements MahjongPayload {
    public static final int CHUNK_SIZE = 16_384;
    public static final int MAX_PARTS = PresetArchives.MAX_ARCHIVE_BYTES / CHUNK_SIZE;
    public static final net.minecraft.resources.ResourceLocation TYPE = MahjongContent.id("preset_bundle");
    public static PresetBundlePayload decode(FriendlyByteBuf buffer) {
            return new PresetBundlePayload(buffer.readEnum(PresetArchives.Kind.class), buffer.readUUID(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(CHUNK_SIZE));
        }
    @Override public void write(FriendlyByteBuf buffer) {
            buffer.writeEnum(kind()); buffer.writeUUID(transfer()); buffer.writeVarInt(part()); buffer.writeVarInt(parts());
            buffer.writeByteArray(data());
        }

    public PresetBundlePayload {
        if (kind == null || transfer == null || parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts
            || data == null || data.length > CHUNK_SIZE)
            throw new IllegalArgumentException("Invalid preset bundle chunk");
    }
    @Override public net.minecraft.resources.ResourceLocation id() { return TYPE; }

    public static List<PresetBundlePayload> split(byte[] bytes, PresetArchives.Kind kind) {
        if (bytes.length > PresetArchives.MAX_ARCHIVE_BYTES) throw new IllegalArgumentException("Preset bundle exceeds size limit");
        int parts = Math.max(1, (bytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
        var transfer = UUID.randomUUID();
        var result = new ArrayList<PresetBundlePayload>(parts);
        for (int part = 0; part < parts; part++)
            result.add(new PresetBundlePayload(kind, transfer, part, parts,
                Arrays.copyOfRange(bytes, part * CHUNK_SIZE, Math.min(bytes.length, (part + 1) * CHUNK_SIZE))));
        return result;
    }
}
