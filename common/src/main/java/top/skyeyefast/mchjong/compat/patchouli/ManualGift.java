package top.skyeyefast.mchjong.compat.patchouli;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import top.skyeyefast.mchjong.world.MahjongContent;
import vazkii.patchouli.api.PatchouliAPI;

/** Server-only, loaded by the loader adapter after checking Patchouli's presence. */
public final class ManualGift extends SavedData {
    private final Set<UUID> recipients = new HashSet<>();

    public static void give(ServerPlayer player) {
        var data = player.server.overworld().getDataStorage().computeIfAbsent(
            ManualGift::load, ManualGift::new, "mchjong_manual_gifts");
        if (data.recipients.contains(player.getUUID())) return;
        var book = PatchouliAPI.get().getBookStack(MahjongContent.id("guide"));
        if (book.isEmpty()) return;
        if (!player.getInventory().add(book)) player.drop(book, false);
        data.recipients.add(player.getUUID());
        data.setDirty();
    }

    public static ManualGift load(CompoundTag tag) {
        var data = new ManualGift();
        var list = tag.getList("recipients", net.minecraft.nbt.Tag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) data.recipients.add(UUID.fromString(list.getString(index)));
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        var list = new ListTag();
        recipients.stream().sorted().forEach(id -> list.add(StringTag.valueOf(id.toString())));
        tag.put("recipients", list);
        return tag;
    }
}
