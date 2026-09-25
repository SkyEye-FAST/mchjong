package top.skyeyefast.mchjong.engine;

/** Room-owned access to concealed faces; only OPEN changes their physical pose. */
public enum HandVisibility {
    SELF, RIICHI, ALL, OPEN;

    public boolean reveals(boolean riichiViewer) {
        return this == OPEN || this == ALL || this == RIICHI && riichiViewer;
    }
}
