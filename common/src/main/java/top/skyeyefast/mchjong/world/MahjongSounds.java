package top.skyeyefast.mchjong.world;

import top.skyeyefast.mchjong.platform.ResourceIds;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;

/** Resource-pack event names form the customization contract on both loaders. */
public final class MahjongSounds {
    public static final List<String> EFFECTS = List.of("wall", "deal", "draw", "tsumogiri", "tedashi", "riichi",
        "chi", "pon", "kan", "nuki", "ron", "tsumo", "draw_end", "match_end", "countdown", "turn");
    public static final List<String> VOICES = java.util.stream.Stream.concat(
        java.util.stream.Stream.of("riichi", "double_riichi", "chi", "pon", "kan", "nuki", "ron", "tsumo", "draw_end", "match_end"),
        top.skyeyefast.mchjong.engine.ScoreAnnouncements.SUBTITLES.keySet().stream()).toList();
    public static final Map<String, SoundEvent> EVENTS;
    static {
        var events = new LinkedHashMap<String, SoundEvent>();
        EFFECTS.forEach(name -> events.put("table." + name, SoundEvent.createVariableRangeEvent(
            ResourceIds.of(MahjongContent.MOD_ID, "table." + name))));
        VOICES.forEach(name -> events.put("voice." + name, SoundEvent.createVariableRangeEvent(
            ResourceIds.of(MahjongContent.MOD_ID, "voice." + name))));
        EVENTS = java.util.Collections.unmodifiableMap(events);
    }
    private MahjongSounds() {}
    public static SoundEvent effect(String name) { return java.util.Objects.requireNonNull(EVENTS.get("table." + name)); }
    public static SoundEvent voice(String name) { return java.util.Objects.requireNonNull(EVENTS.get("voice." + name)); }
}
