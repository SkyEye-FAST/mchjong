package top.skyeyefast.mchjong.mixin;

import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundBufferLibrary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.skyeyefast.mchjong.client.VoicePresets;

/** Observe the actual channel; SoundManager.isActive also includes deletion bookkeeping. */
@Mixin(SoundEngine.class)
public abstract class VoicePlaybackMixin {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow @Final private SoundBufferLibrary soundBuffers;

    @Inject(method = "play", at = @At("RETURN"))
    private void mchjong$trackVoice(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
        VoicePresets.trackChannel(sound, instanceToChannel.get(sound), soundBuffers);
    }
}
