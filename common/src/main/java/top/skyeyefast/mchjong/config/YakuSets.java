package top.skyeyefast.mchjong.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.YakuSet;
import top.skyeyefast.mchjong.engine.YakuSetCodec;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.YakuSetsPayload;

/** Reload replaces the selectable catalog atomically; tables hold detached, immutable rule snapshots. */
public final class YakuSets implements ResourceManagerReloadListener {
    private static final String DIRECTORY = "mchjong/yaku_sets/";
    private static volatile YakuSetsPayload catalog = new YakuSetsPayload(List.of());
    private static final Map<ServerPlayer, YakuSetsPayload> SENT = new WeakHashMap<>();

    @Override public void onResourceManagerReload(ResourceManager resources) {
        var entries = resources.listResources("mchjong/yaku_sets", id -> id.getPath().endsWith(".json"));
        if (entries.size() > YakuSetsPayload.MAX_SETS) throw new IllegalArgumentException("Too many custom award sets");
        var sets = new ArrayList<YakuSet>();
        for (var id : entries.keySet().stream().sorted().toList()) {
            String setId = id.getNamespace() + ":" + id.getPath().substring(DIRECTORY.length(), id.getPath().length() - 5);
            try (var reader = entries.get(id).openAsReader()) {
                char[] data = new char[YakuSetCodec.MAX_CHARS + 1];
                int length = 0, read;
                while (length < data.length && (read = reader.read(data, length, data.length - length)) != -1) length += read;
                sets.add(YakuSetCodec.parse(setId, new String(data, 0, length)));
            } catch (Exception invalid) {
                throw new IllegalArgumentException("Invalid custom award set " + id, invalid);
            }
        }
        catalog = new YakuSetsPayload(sets);
    }

    public static YakuSet find(String id, MahjongVariant variant) {
        if (id.equals("mchjong:none")) return YakuSet.empty(variant);
        return catalog.sets().stream().filter(s -> s.id().equals(id) && s.variant() == variant).findFirst().orElse(null);
    }

    /** Called on the server thread when a player receives table state, including after a data-pack reload. */
    public static void sendIfChanged(ServerPlayer player) {
        var snapshot = catalog;
        if (SENT.get(player) == snapshot) return;
        player.connection.send(PayloadPackets.clientbound(snapshot));
        SENT.put(player, snapshot);
    }
}
