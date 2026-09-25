package top.skyeyefast.mchjong.mixin;

import java.io.ByteArrayInputStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.skyeyefast.mchjong.client.VoicePresets;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Supplies config ZIP recordings to the native sound buffer without a resource pack. */
@Mixin(SoundBufferLibrary.class)
public abstract class VoiceSoundLibraryMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static ResourceProvider mchjong$voiceProvider(ResourceProvider delegate) {
        return location -> {
            byte[] bytes = VoicePresets.audio(location);
            if (bytes == null) return delegate.getResource(location);
            return delegate.getResource(MahjongContent.id("sounds.json"))
                .map(reference -> new Resource(reference.source(), () -> new ByteArrayInputStream(bytes)));
        };
    }
}
