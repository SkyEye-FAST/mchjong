package top.skyeyefast.mchjong.engine;

/** Server policy for concealed faces shown to non-participants. */
public enum SpectatorHandVisibility {
    HIDDEN, FOLLOW_PLAYERS, ALL;

    public boolean reveals(PlayerHandVisibility players) {
        return this == ALL || this == FOLLOW_PLAYERS && players == PlayerHandVisibility.ALL;
    }
}
