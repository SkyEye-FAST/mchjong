package top.skyeyefast.mchjong.engine;

import java.util.Locale;

/** Named hanchan presets. Source versions and settlement conventions are in docs/RULES.md. */
public enum RuleSet {
    MAHJONG_SOUL_4(4), MAHJONG_SOUL_3(3), TENHOU_4(4), TENHOU_3(3), M_LEAGUE(4), JPML_A(4), WRC(4);

    private final int players;

    RuleSet(int players) { this.players = players; }
    public int players() { return players; }
    public boolean sanma() { return players == 3; }
    public boolean mLeague() { return this == M_LEAGUE; }
    public boolean tenhou() { return this == TENHOU_3 || this == TENHOU_4; }
    public boolean mahjongSoul() { return this == MAHJONG_SOUL_3 || this == MAHJONG_SOUL_4; }
    private boolean online() { return tenhou() || mahjongSoul(); }
    public int startingPoints() { return this == JPML_A || this == WRC ? 30000 : sanma() ? 35000 : 25000; }
    public int returnPoints() { return mahjongSoul() ? startingPoints() : sanma() ? 40000 : 30000; }
    public int targetPoints() { return sanma() ? 40000 : 30000; }
    public boolean headBump() { return !online(); }
    public boolean tripleRonDraw() { return tenhou(); }
    public boolean kiriageMangan() { return mLeague() || this == WRC; }
    public boolean kazoeYakuman() { return !mLeague(); }
    public boolean doubleYakuman() { return mahjongSoul(); }
    public boolean ippatsu() { return this != JPML_A; }
    public boolean uraDora() { return this != JPML_A; }
    public boolean kanDora() { return this != JPML_A; }
    public boolean renhouMangan() { return this == WRC; }
    public boolean doubleWindPairFu() { return online(); }
    public boolean bankruptcy() { return online(); }
    public boolean abortiveDraws() { return online(); }
    public boolean nagashiMangan() { return online(); }
    public boolean agariYame() { return online(); }
    public boolean westExtension() { return online(); }
    public boolean robConcealedKan() { return mahjongSoul(); }
    public boolean robNorthWithoutKokushi() { return sanma(); }
    public boolean delayedOpenKanDora() { return online(); }
    public int minRiichiWall() { return this == WRC ? 0 : online() ? players : 1; }
    public boolean needsRiichiDeposit() { return online(); }
    public boolean riichiKanKeepsMelds() { return !online(); }
    public int replacementCapacity() { return sanma() ? 8 : 4; }
    public boolean suukantsuPao() { return !online(); }
    public boolean wholeHandPao() { return tenhou(); }
    public boolean nagashiAllowsCalls() { return tenhou(); }
    public boolean sharedRanks() { return !online(); }
    public boolean awardFinalDeposits() { return online() || mLeague(); }
    public boolean paoRonHonbaByDiscarder() { return this == JPML_A || this == WRC; }
    public RedFives defaultRedFives() { return online() || mLeague() ? RedFives.THREE : RedFives.NONE; }
    public boolean allows(RedFives redFives) {
        return redFives != null && (online() || redFives == defaultRedFives());
    }
    public int[] placementBonus() {
        if (sanma()) return mahjongSoul() ? new int[]{15, 0, -15} : new int[]{20, 0, -20};
        if (mLeague()) return new int[]{30, 10, -10, -30};
        if (this == WRC) return new int[]{15, 5, -5, -15};
        if (this == JPML_A) throw new IllegalStateException("League A placement depends on the number of floating players");
        return mahjongSoul() ? new int[]{15, 5, -5, -15} : new int[]{20, 10, -10, -20};
    }
    public int[] placementBonus(int floatingPlayers) {
        if (this != JPML_A) return placementBonus();
        return switch (floatingPlayers) {
            case 0, 4 -> new int[4];
            case 1 -> new int[]{12, -1, -3, -8};
            case 2 -> new int[]{8, 4, -4, -8};
            case 3 -> new int[]{8, 3, 1, -12};
            default -> throw new IllegalArgumentException("Invalid floating player count");
        };
    }
    public String translationKey() { return "rule.mchjong." + name().toLowerCase(Locale.ROOT); }
    public String presetKey() { return "preset.mchjong." + (online() ? tenhou() ? "tenhou" : "mahjong_soul" : name().toLowerCase(Locale.ROOT)); }
}
