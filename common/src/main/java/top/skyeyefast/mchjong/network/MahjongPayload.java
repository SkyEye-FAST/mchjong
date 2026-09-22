package top.skyeyefast.mchjong.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Shared play messages; loaders own channel registration and thread dispatch. */
public interface MahjongPayload {
    ResourceLocation id();
    void write(FriendlyByteBuf buffer);
}
