package top.skyeyefast.mchjong.engine;

/** Called-away tiles remain in the history for furiten and nagashi validation. */
public record Discard(int tile, boolean riichi, boolean called, boolean tsumogiri) {
    public Discard markCalled() { return new Discard(tile, riichi, true, tsumogiri); }
}
