package top.skyeyefast.mchjong.engine;

/** Room-owned access to concealed faces for match participants. Physical exposure is separate. */
public enum PlayerHandVisibility {
    SELF, RIICHI, ALL;

    public boolean reveals(boolean riichiViewer) {
        return this == ALL || this == RIICHI && riichiViewer;
    }
}
