package top.skyeyefast.mchjong.engine;

public enum SichuanPreset {
    SBR_2025(new SichuanRules(3, 1, 2, 2, 1, 24, true, true));

    private final SichuanRules rules;
    SichuanPreset(SichuanRules rules) { this.rules = rules; }
    public SichuanRules config() { return rules; }
}
