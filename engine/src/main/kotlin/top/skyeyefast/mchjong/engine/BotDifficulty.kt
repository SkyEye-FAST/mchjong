package top.skyeyefast.mchjong.engine

import java.util.Locale

enum class BotDifficulty {
    EASY,
    HARD,
    ;

    fun translationKey(): String = "bot.mchjong." + name.lowercase(Locale.ROOT)
}
