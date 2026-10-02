package top.skyeyefast.mchjong.engine;

public enum SichuanPreset {
    SBR_2025(new SichuanRules(3, 1, 2, 2, 1, 24, false, true, 8, true, true, true, true)),
    TFMJ_2024(new SichuanRules(3, 1, 2, 2, 1, 24, true, true, 8, false, false, false, false));

    private final SichuanRules rules;
    SichuanPreset(SichuanRules rules) { this.rules = rules; }
    public SichuanRules config() { return rules; }
    public String translationKey() { return "sichuan.mchjong.rules.preset." + name().toLowerCase(java.util.Locale.ROOT); }
    public static SichuanPreset match(SichuanRules rules) {
        for (var preset : values()) if (preset.rules.equals(rules)) return preset;
        return null;
    }
}
