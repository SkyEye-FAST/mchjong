package top.skyeyefast.mchjong.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.YakuSet;
import top.skyeyefast.mchjong.engine.YakuSetCodec;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Public definitions available for new table configurations. Active rules retain their own snapshots. */
public record YakuSetsPayload(List<YakuSet> sets) implements CustomPacketPayload {
    public static final int MAX_SETS = 32, MAX_TOTAL_CHARS = 262144;
    public YakuSetsPayload {
        sets = List.copyOf(sets);
        if (sets.size() > MAX_SETS || sets.stream().anyMatch(YakuSet::isEmpty)
            || sets.stream().map(YakuSet::id).distinct().count() != sets.size()
            || sets.stream().mapToInt(s -> YakuSetCodec.encode(s).length()).sum() > MAX_TOTAL_CHARS)
            throw new IllegalArgumentException("Invalid custom award catalog");
    }
    public static final Type<YakuSetsPayload> TYPE = new Type<>(MahjongContent.id("yaku_sets"));
    public static final StreamCodec<RegistryFriendlyByteBuf, YakuSetsPayload> CODEC = new StreamCodec<>() {
        @Override public YakuSetsPayload decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt(), total = 0;
            if (count < 0 || count > MAX_SETS) throw new IllegalArgumentException("Too many custom award sets");
            var sets = new ArrayList<YakuSet>();
            for (int i = 0; i < count; i++) {
                String json = buffer.readUtf(YakuSetCodec.MAX_CHARS);
                total += json.length();
                if (total > MAX_TOTAL_CHARS) throw new IllegalArgumentException("Custom award catalog exceeds limit");
                sets.add(YakuSetCodec.decode(json));
            }
            return new YakuSetsPayload(sets);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, YakuSetsPayload value) {
            buffer.writeVarInt(value.sets().size());
            for (var set : value.sets()) buffer.writeUtf(YakuSetCodec.encode(set), YakuSetCodec.MAX_CHARS);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
