package top.skyeyefast.mchjong.engine;

import java.util.Locale;

public enum BotDifficulty {
    EASY, HARD;

    public String translationKey() { return "bot.mchjong." + name().toLowerCase(Locale.ROOT); }
}
