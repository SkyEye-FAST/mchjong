package top.skyeyefast.mchjong.mixin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.skyeyefast.mchjong.client.VoicePresets;

/** Supplies config ZIP recordings to the native sound buffer without a resource pack. */
@Mixin(SoundBufferLibrary.class)
public abstract class VoiceSoundLibraryMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static ResourceProvider mchjong$voiceProvider(ResourceProvider delegate) {
        return new ResourceProvider() {
            @Override public Optional<Resource> getResource(Identifier location) {
                return delegate.getResource(location);
            }
            @Override public InputStream open(Identifier location) throws IOException {
                byte[] bytes = VoicePresets.audio(location);
                // The native sound cache contains only Ogg files, not sounds.json pack metadata.
                return bytes == null ? delegate.open(location) : new ByteArrayInputStream(bytes);
            }
        };
    }
}
