package top.skyeyefast.mchjong.engine

/** Actions are selected from an issued decision; tile lists contain actual physical identities. */
class TaiwanAction(val type: Type, tiles: List<Int> = emptyList()) {
    enum class Type { DISCARD, READY_DISCARD, WIN, CHOW, PONG, OPEN_KONG, CONCEALED_KONG, ADDED_KONG, PASS }
    val tiles: List<Int> = java.util.List.copyOf(tiles)
}

class TaiwanDecision(val token: Long, val seat: Int, actions: List<TaiwanAction>) {
    val actions: List<TaiwanAction> = java.util.List.copyOf(actions)
}
