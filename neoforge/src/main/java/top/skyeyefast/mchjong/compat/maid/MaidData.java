package top.skyeyefast.mchjong.compat.maid;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class MaidData {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "mchjong");
    private static final Supplier<AttachmentType<Optional<MaidBinding>>> BINDING = TYPES.register("maid_table",
        () -> AttachmentType.builder(() -> Optional.<MaidBinding>empty()).serialize(MaidBinding.CODEC.optionalFieldOf("binding")).build());
    private MaidData() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
    public static MaidBinding get(Entity maid) { return maid.hasData(BINDING.get()) ? maid.getData(BINDING.get()).orElse(null) : null; }
    public static void set(Entity maid, MaidBinding binding) { maid.setData(BINDING.get(), Optional.of(binding)); }
    public static void clear(Entity maid) { maid.removeData(BINDING.get()); }
}
