package top.skyeyefast.mchjong.network;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import top.skyeyefast.mchjong.engine.*;

/** Client proposals carry definition digests and switches; only the server supplies definitions. */
public record YakuSelectionRequest(Map<String, Choice> sets, Set<String> disabledNative) {
    /** The digest binds these 16 award bits to the exact catalog order the client reviewed. */
    public record Choice(String digest, int awards) {
        public Choice {
            if (digest == null || !digest.matches("[0-9a-f]{64}") || awards < 1 || awards > 65535)
                throw new IllegalArgumentException("Invalid award choice");
        }
    }
    public YakuSelectionRequest {
        sets = java.util.Collections.unmodifiableMap(new TreeMap<>(sets));
        disabledNative = java.util.Collections.unmodifiableSet(new TreeSet<>(disabledNative));
        if (sets.size() > YakuSelection.MAX_SETS || disabledNative.size() > 64)
            throw new IllegalArgumentException("Award selection exceeds limits");
        sets.forEach((id, choice) -> {
            YakuSet.requireId(id);
            java.util.Objects.requireNonNull(choice);
        });
        disabledNative.forEach(YakuSet::requireId);
    }
    public static YakuSelectionRequest of(YakuSelection selection) {
        var sets = new TreeMap<String, Choice>();
        selection.sets().forEach(s -> {
            int bits = 0;
            for (int i = 0; i < s.awards().size(); i++) if (selection.enabled(s.awards().get(i).id())) bits |= 1 << i;
            sets.put(s.id(), new Choice(YakuSetCodec.digest(s), bits));
        });
        return new YakuSelectionRequest(sets, selection.disabledNative());
    }
    public YakuSelection resolve(MahjongVariant variant, Function<String, YakuSet> catalog) {
        var definitions = new ArrayList<YakuSet>();
        var enabledCustom = new TreeSet<String>();
        for (var entry : sets.entrySet()) {
            var set = catalog.apply(entry.getKey());
            if (set == null || !YakuSetCodec.digest(set).equals(entry.getValue().digest())
                || entry.getValue().awards() >= 1 << set.awards().size()) return null;
            for (int i = 0; i < set.awards().size(); i++) if ((entry.getValue().awards() & 1 << i) != 0) enabledCustom.add(set.awards().get(i).id());
            definitions.add(set);
        }
        try { return new YakuSelection(variant, definitions, disabledNative, enabledCustom); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    public static YakuSelectionRequest read(RegistryFriendlyByteBuf buffer) {
        int count = count(buffer, YakuSelection.MAX_SETS);
        var sets = new TreeMap<String, Choice>();
        for (int i = 0; i < count; i++) if (sets.put(buffer.readUtf(128), new Choice(buffer.readUtf(64), buffer.readVarInt())) != null)
            throw new IllegalArgumentException("Duplicate set ID");
        return new YakuSelectionRequest(sets, ids(buffer, 64));
    }
    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(sets.size());
        sets.forEach((id, choice) -> { buffer.writeUtf(id, 128); buffer.writeUtf(choice.digest(), 64); buffer.writeVarInt(choice.awards()); });
        buffer.writeVarInt(disabledNative.size()); disabledNative.forEach(id -> buffer.writeUtf(id, 128));
    }
    private static int count(RegistryFriendlyByteBuf buffer, int maximum) {
        int value = buffer.readVarInt();
        if (value < 0 || value > maximum) throw new IllegalArgumentException("Invalid award selection count");
        return value;
    }
    private static Set<String> ids(RegistryFriendlyByteBuf buffer, int maximum) {
        int count = count(buffer, maximum);
        var ids = new TreeSet<String>();
        for (int i = 0; i < count; i++) if (!ids.add(buffer.readUtf(128))) throw new IllegalArgumentException("Duplicate award ID");
        return ids;
    }
}
