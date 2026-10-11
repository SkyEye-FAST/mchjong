package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.YakuSet;
import top.skyeyefast.mchjong.network.YakuSetsPayload;

public final class ClientYakuSets {
    private static ClientPacketListener connection;
    private static List<YakuSet> sets = List.of();
    private ClientYakuSets() {}
    public static void receive(YakuSetsPayload payload) {
        connection = Minecraft.getInstance().getConnection();
        sets = payload.sets();
    }
    public static List<YakuSet> available(MahjongVariant variant) {
        if (connection != Minecraft.getInstance().getConnection()) { connection = null; sets = List.of(); }
        return sets.stream().filter(s -> s.variant() == variant).toList();
    }
    public static Component text(YakuSet.Text text) { return Component.literal(text.get(Minecraft.getInstance().options.languageCode)); }
    public static Component awardName(YakuSet set, String id, String translationKey) {
        if (id != null && id.contains(":")) {
            var award = set.find(id);
            return award == null ? Component.literal(id) : text(award.name());
        }
        return Component.translatable(translationKey);
    }
}
