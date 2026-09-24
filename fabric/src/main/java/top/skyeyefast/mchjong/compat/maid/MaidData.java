package top.skyeyefast.mchjong.compat.maid;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class MaidData {
    private static final AttachmentType<MaidBinding> BINDING = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath("mchjong", "maid_table"), builder -> builder.persistent(MaidBinding.CODEC));
    private MaidData() {}
    public static void register() {}
    public static MaidBinding get(Entity maid) { return maid.getAttached(BINDING); }
    public static void set(Entity maid, MaidBinding binding) { maid.setAttached(BINDING, binding); }
    public static void clear(Entity maid) { maid.removeAttached(BINDING); }
}
