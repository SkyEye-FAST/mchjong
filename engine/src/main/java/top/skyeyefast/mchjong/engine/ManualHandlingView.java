package top.skyeyefast.mchjong.engine;

/** Public physical positions and dice, without concealed wall identities or seeds. */
public record ManualHandlingView(int builtWalls, int sourceSlot, int packetSize, int diceOne, int diceTwo, boolean diceHeld) {
    public ManualHandlingView {
        if (builtWalls < 0 || builtWalls > 15 || sourceSlot < -1 || sourceSlot >= 144 || packetSize < 0 || packetSize > 4
            || diceOne < 0 || diceOne > 6 || diceTwo < 0 || diceTwo > 6 || (diceOne == 0) != (diceTwo == 0))
            throw new IllegalArgumentException("Invalid physical handling view");
    }
}
