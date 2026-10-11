package top.skyeyefast.mchjong.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.YakuSet;
import top.skyeyefast.mchjong.engine.YakuSetCodec;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Public definitions available for new table configurations. Active rules retain their own snapshots. */
public record YakuSetsPayload(List<YakuSet> sets) implements MahjongPayload {
    public static final int MAX_SETS = top.skyeyefast.mchjong.engine.YakuSelection.MAX_SETS,
        MAX_TOTAL_CHARS = top.skyeyefast.mchjong.engine.YakuSelection.MAX_DEFINITION_CHARS;
    public YakuSetsPayload {
        sets = List.copyOf(sets);
        if (sets.size() > MAX_SETS
        || sets.stream().map(YakuSet::id).distinct().count() != sets.size()
        || sets.stream().flatMap(s -> s.awards().stream()).map(YakuSet.Definition::id).distinct().count() != sets.stream().mapToInt(s -> s.awards().size()).sum()
        || sets.stream().mapToInt(s -> YakuSetCodec.encode(s).length()).sum() > MAX_TOTAL_CHARS
        || sets.stream().mapToInt(s -> YakuSetCodec.encode(s).getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum() > top.skyeyefast.mchjong.engine.YakuSelection.MAX_DEFINITION_BYTES)
        throw new IllegalArgumentException("Invalid custom award catalog");
    }
    public static final ResourceLocation TYPE = top.skyeyefast.mchjong.platform.ResourceIds.of(MahjongContent.MOD_ID, "yaku_sets");
    public static YakuSetsPayload decode(FriendlyByteBuf buffer) {
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
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.sets().size());
        for (var set : this.sets()) buffer.writeUtf(YakuSetCodec.encode(set), YakuSetCodec.MAX_CHARS);
    }
    @Override public ResourceLocation id() { return TYPE; }
}
