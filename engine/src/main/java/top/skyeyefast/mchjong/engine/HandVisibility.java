package top.skyeyefast.mchjong.engine;

/** Room-owned access to concealed faces; only OPEN changes their physical pose. */
public enum HandVisibility {
    OPEN, ALL, RIICHI, SELF;

    public boolean reveals(boolean riichiViewer) {
        return this == OPEN || this == ALL || this == RIICHI && riichiViewer;
    }
}
