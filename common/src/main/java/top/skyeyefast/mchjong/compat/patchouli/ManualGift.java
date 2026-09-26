package top.skyeyefast.mchjong.compat.patchouli;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import top.skyeyefast.mchjong.world.MahjongContent;
import vazkii.patchouli.api.PatchouliAPI;

/** Server-only, loaded by the loader adapter after checking Patchouli's presence. */
public final class ManualGift extends SavedData {
    public static final Codec<ManualGift> CODEC = UUIDUtil.CODEC.listOf().xmap(ids -> {
        var data = new ManualGift();
        data.recipients.addAll(ids);
        return data;
    }, data -> data.recipients.stream().sorted().toList()).fieldOf("recipients").codec();
    public static final net.minecraft.world.level.saveddata.SavedDataType<ManualGift> TYPE =
        new net.minecraft.world.level.saveddata.SavedDataType<>(MahjongContent.id("manual_gifts"), ManualGift::new, CODEC, null);
    private final Set<UUID> recipients = new HashSet<>();

    public static void give(ServerPlayer player) {
        var data = player.level().getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
        if (data.recipients.contains(player.getUUID())) return;
        var book = PatchouliAPI.get().getBookStack(MahjongContent.id("guide"));
        if (book.isEmpty()) return;
        if (!player.getInventory().add(book)) player.drop(book, false);
        data.recipients.add(player.getUUID());
        data.setDirty();
    }

}
