package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.core.registries.BuiltInRegistries;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongSounds;

/** Verify native registration, resource resolution and decoding of every original effect. */
final class AudioEffectsSmoke {
    static void verify(Minecraft client) throws java.io.IOException {
        for (String name : MahjongSounds.EFFECTS) {
            var id = MahjongContent.id("table." + name);
            require(BuiltInRegistries.SOUND_EVENT.containsKey(id), "Unregistered effect: " + id);
            var event = client.getSoundManager().getSoundEvent(id);
            require(event != null && event.getWeight() > 0, "Unresolved effect: " + id);
            try (var input = client.getResourceManager().open(MahjongContent.id("sounds/table/" + name + ".ogg"));
                 var stream = new OggAudioStream(input)) {
                require(stream.getFormat().getChannels() == 1, "Positional effect must be mono: " + id);
                int rate = Math.round(stream.getFormat().getSampleRate());
                require(rate == 44100, "Unexpected effect sample rate: " + id);
                long[] samples = {0};
                float[] peak = {0};
                var pcm = stream.read(rate * stream.getFormat().getFrameSize() + 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                while (pcm.remaining() >= 2) {
                    float value = pcm.getShort() / 32768f;
                    samples[0]++;
                    peak[0] = Math.max(peak[0], Math.abs(value));
                }
                require(samples[0] >= rate / 25 && samples[0] <= rate, "Effect duration outside short accent range: " + id);
                require(peak[0] > .01f && peak[0] < .5f, "Effect is silent or exceeds the shared headroom: " + id);
            }
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
