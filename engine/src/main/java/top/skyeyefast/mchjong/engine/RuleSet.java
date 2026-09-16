package top.skyeyefast.mchjong.engine;

import java.util.Locale;

/** Standard ari-aka hanchan presets. M.League does not define a three-player game. */
public enum RuleSet {
    MAHJONG_SOUL_4(4), MAHJONG_SOUL_3(3), TENHOU_4(4), TENHOU_3(3), M_LEAGUE(4);

    private final int players;

    RuleSet(int players) { this.players = players; }
    public int players() { return players; }
    public boolean sanma() { return players == 3; }
    public boolean mLeague() { return this == M_LEAGUE; }
    public boolean tenhou() { return this == TENHOU_3 || this == TENHOU_4; }
    public boolean mahjongSoul() { return this == MAHJONG_SOUL_3 || this == MAHJONG_SOUL_4; }
    public int startingPoints() { return sanma() ? 35000 : 25000; }
    public int returnPoints() { return mahjongSoul() ? startingPoints() : sanma() ? 40000 : 30000; }
    public int targetPoints() { return sanma() ? 40000 : 30000; }
    public boolean headBump() { return mLeague(); }
    public boolean tripleRonDraw() { return tenhou(); }
    public boolean kiriageMangan() { return mLeague(); }
    public boolean kazoeYakuman() { return !mLeague(); }
    public boolean doubleYakuman() { return mahjongSoul(); }
    public boolean doubleWindPairFu() { return !mLeague(); }
    public boolean bankruptcy() { return !mLeague(); }
    public boolean abortiveDraws() { return !mLeague(); }
    public boolean nagashiMangan() { return !mLeague(); }
    public boolean agariYame() { return !mLeague(); }
    public boolean westExtension() { return !mLeague(); }
    public boolean robConcealedKan() { return mahjongSoul(); }
    public boolean robNorthWithoutKokushi() { return sanma(); }
    public boolean delayedOpenKanDora() { return !mLeague(); }
    public int minRiichiWall() { return mLeague() ? 1 : players; }
    public boolean needsRiichiDeposit() { return !mLeague(); }
    public int replacementCapacity() { return sanma() ? 8 : 4; }
    public boolean suukantsuPao() { return mLeague(); }
    public boolean wholeHandPao() { return tenhou(); }
    public boolean nagashiAllowsCalls() { return tenhou(); }
    public int[] placementBonus() {
        if (sanma()) return mahjongSoul() ? new int[]{15, 0, -15} : new int[]{20, 0, -20};
        if (mLeague()) return new int[]{30, 10, -10, -30};
        return mahjongSoul() ? new int[]{15, 5, -5, -15} : new int[]{20, 10, -10, -20};
    }
    public String translationKey() { return "rule.mchjong." + name().toLowerCase(Locale.ROOT); }
}
