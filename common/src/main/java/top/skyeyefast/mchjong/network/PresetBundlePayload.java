package top.skyeyefast.mchjong.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.config.PresetArchives;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Bounded binary chunks for one server cosmetic category. */
public record PresetBundlePayload(PresetArchives.Kind kind, UUID transfer, int part, int parts, byte[] data) implements CustomPacketPayload {
    public static final int CHUNK_SIZE = 16_384;
    public static final int MAX_PARTS = PresetArchives.MAX_ARCHIVE_BYTES / CHUNK_SIZE;
    public static final Type<PresetBundlePayload> TYPE = new Type<>(MahjongContent.id("preset_bundle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PresetBundlePayload> CODEC = new StreamCodec<>() {
        @Override public PresetBundlePayload decode(RegistryFriendlyByteBuf buffer) {
            return new PresetBundlePayload(buffer.readEnum(PresetArchives.Kind.class), buffer.readUUID(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(CHUNK_SIZE));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, PresetBundlePayload value) {
            buffer.writeEnum(value.kind()); buffer.writeUUID(value.transfer()); buffer.writeVarInt(value.part()); buffer.writeVarInt(value.parts());
            buffer.writeByteArray(value.data());
        }
    };

    public PresetBundlePayload {
        if (kind == null || transfer == null || parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts
            || data == null || data.length > CHUNK_SIZE)
            throw new IllegalArgumentException("Invalid preset bundle chunk");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

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
