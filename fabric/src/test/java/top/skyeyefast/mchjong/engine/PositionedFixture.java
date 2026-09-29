package top.skyeyefast.mchjong.engine;

/** Client geometry fixtures begin after the separately tested wind lottery. */
public final class PositionedFixture {
    private PositionedFixture() {}
    public static void assign(RiichiGame game) { game.seating.positioned(game.rules.players()); }
}
