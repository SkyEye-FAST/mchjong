package top.skyeyefast.mchjong.engine;

import java.util.Objects;

/** Authoritative preparation settings. Presets are derived from the complete rules. */
public record TaiwanRoomSettings(TaiwanGameState.Rules rules, TimeControl timeControl, boolean rulesEditable) {
    public TaiwanRoomSettings { Objects.requireNonNull(rules); Objects.requireNonNull(timeControl); }
    public String presetKey() {
        for (var preset : TaiwanPreset.values()) if (rules.equals(TaiwanGameState.Rules.of(preset.rules())))
            return "taiwan.mchjong.preset." + preset.name().toLowerCase(java.util.Locale.ROOT);
        return "rules.mchjong.custom";
    }
}
