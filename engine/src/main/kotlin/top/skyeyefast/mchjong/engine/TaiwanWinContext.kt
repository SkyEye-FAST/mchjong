package top.skyeyefast.mchjong.engine

class TaiwanWinContext(
    val method: Method,
    val seatWind: Int,
    val roundWind: Int,
    val flowerNumber: Int,
    flowers: Set<FlowerTile>,
    val drawOrigin: DrawOrigin,
    val lastTile: Boolean,
    val opening: Opening,
    val ready: Ready,
) {
    enum class Method { DISCARD, SELF_DRAW, ROBBING_KONG }
    enum class DrawOrigin { ORDINARY, KONG_REPLACEMENT, FLOWER_REPLACEMENT }
    enum class Opening { NONE, HEAVENLY, EARTHLY, HUMAN }
    enum class Ready { NONE, ORDINARY, HEAVENLY, EARTHLY }
    val flowers: Set<FlowerTile> = java.util.Set.copyOf(flowers)
    init {
        require(seatWind in Tile.EAST..Tile.NORTH && roundWind in Tile.EAST..Tile.NORTH && flowerNumber in 1..4)
        require(method == Method.SELF_DRAW || drawOrigin == DrawOrigin.ORDINARY)
        require(method != Method.ROBBING_KONG || !lastTile)
        require(opening != Opening.HEAVENLY || method == Method.SELF_DRAW && seatWind == Tile.EAST)
        require(opening != Opening.EARTHLY || method == Method.SELF_DRAW && seatWind != Tile.EAST)
        require(opening != Opening.HUMAN || method == Method.DISCARD && seatWind != Tile.EAST)
        require(opening == Opening.NONE || ready == Ready.NONE)
        require(ready != Ready.HEAVENLY || seatWind == Tile.EAST)
        require(ready != Ready.EARTHLY || seatWind != Tile.EAST)
    }
}
