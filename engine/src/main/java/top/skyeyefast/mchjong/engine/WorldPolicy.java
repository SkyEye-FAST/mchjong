package top.skyeyefast.mchjong.engine;

/** Minecraft-independent runtime restrictions imposed by the hosting world. */
public record WorldPolicy(boolean allowConvenienceHints, boolean allowExperienceRewards,
                          boolean deductNegativeExperience, int maxExperienceChange,
                          boolean replaysEnabled, boolean allowBots, boolean allowCompanionPlayers,
                          boolean allowCustomRules, RuleSet forcedPreset) {
    public static final int MAX_EXPERIENCE_LIMIT = 100_000;
    public static final WorldPolicy DEFAULT = new WorldPolicy(
        true, false, true, 5_000, true, true, true, true, null);

    public WorldPolicy {
        if (maxExperienceChange < 0 || maxExperienceChange > MAX_EXPERIENCE_LIMIT)
            throw new IllegalArgumentException("Invalid experience limit");
    }

    public boolean permitsRules(RuleConfig rules) {
        if (forcedPreset != null && rules.preset() != forcedPreset) return false;
        return allowCustomRules || !rules.custom();
    }

    public int experienceChange(double uma) {
        int raw = Math.toIntExact(Math.round(Math.abs(uma) * 100)) * (uma < 0 ? -1 : 1);
        return limitExperienceChange(raw);
    }

    public int limitExperienceChange(int amount) {
        if (!allowExperienceRewards || amount < 0 && !deductNegativeExperience) return 0;
        return Math.max(-maxExperienceChange, Math.min(amount, maxExperienceChange));
    }
}
