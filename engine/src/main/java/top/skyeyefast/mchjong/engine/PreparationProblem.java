package top.skyeyefast.mchjong.engine;

/** Public admission failures; carries no case contents or private game information. */
public enum PreparationProblem {
    CLOTH, CASE, INVALID_CASE, TILES, RED_FIVES, FLOWERS, MATERIAL, BACK, FACE, UNIFORM_SET,
    AUTOMATIC_TABLE, DICE, POINT_STICKS, BUST_STICKS, DRAWER_SPACE;

    public String translationKey() { return "room.mchjong.equipment." + name().toLowerCase(java.util.Locale.ROOT); }
}
