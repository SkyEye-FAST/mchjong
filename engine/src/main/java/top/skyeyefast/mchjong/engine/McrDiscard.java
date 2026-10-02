package top.skyeyefast.mchjong.engine;

/** One MCR discard, including its public claim and draw provenance. */
public record McrDiscard(int tile, boolean called, boolean tsumogiri) {
    public McrDiscard markCalled() { return new McrDiscard(tile, true, tsumogiri); }
}
