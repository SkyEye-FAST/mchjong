package top.skyeyefast.mchjong.engine;

/** Runtime presence for a human room member. Bots are always treated as seated. */
public enum PlayerPresence {
    SEATED,
    AWAY,
    DISCONNECTED
}
