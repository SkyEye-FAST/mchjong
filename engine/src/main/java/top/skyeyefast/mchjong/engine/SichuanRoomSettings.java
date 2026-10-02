package top.skyeyefast.mchjong.engine;

import java.util.Objects;

public record SichuanRoomSettings(SichuanRules rules, TimeControl timeControl, boolean rulesEditable) {
    public SichuanRoomSettings {
        Objects.requireNonNull(rules);
        Objects.requireNonNull(timeControl);
    }

    public SichuanPreset preset() { return SichuanPreset.match(rules); }
    public boolean custom() { return preset() == null; }
    public String presetKey() { return custom() ? "sichuan.mchjong.rules.custom" : preset().translationKey(); }
}
