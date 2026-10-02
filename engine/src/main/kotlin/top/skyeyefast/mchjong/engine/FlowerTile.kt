package top.skyeyefast.mchjong.engine

/** One physical tile per flower; these identities are not ordinary 34-kind faces. */
enum class FlowerTile(private val physicalId: Int) {
    SPRING(136), SUMMER(137), AUTUMN(138), WINTER(139),
    PLUM(140), ORCHID(141), BAMBOO(142), CHRYSANTHEMUM(143);

    fun id(): Int = physicalId

    companion object {
        private val byId = entries.associateBy { it.physicalId }

        @JvmStatic
        fun of(id: Int): FlowerTile? = byId[id]
    }
}
