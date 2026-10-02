package top.skyeyefast.mchjong.engine;

/** Called-away tiles remain in the history for furiten and nagashi validation. */
public record RiichiDiscard(int tile, boolean riichi, boolean called, boolean tsumogiri) {
    public RiichiDiscard markCalled() { return new RiichiDiscard(tile, riichi, true, tsumogiri); }
}
