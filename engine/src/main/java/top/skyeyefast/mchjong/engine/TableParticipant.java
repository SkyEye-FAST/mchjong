package top.skyeyefast.mchjong.engine;

import java.util.Objects;
import java.util.UUID;

/** Public roster metadata. Presence is observed separately and is never persisted here. */
public record TableParticipant(UUID id, String name, boolean bot, boolean entityBot,
                               BotDifficulty difficulty, String externalBotId, boolean ready) {
    public TableParticipant(UUID id, String name) {
        this(Objects.requireNonNull(id), name, false, false, BotDifficulty.EASY, null, false);
    }

    public TableParticipant {
        Objects.requireNonNull(name);
        Objects.requireNonNull(difficulty);
        if (name.length() > 256 || name.chars().anyMatch(Character::isISOControl)
            || id != null && name.isBlank() || id == null && (!name.isEmpty() || bot || entityBot || ready)
            || entityBot && !bot || externalBotId != null && (!bot || entityBot
                || !externalBotId.matches("[a-z0-9][a-z0-9_-]{0,63}")))
            throw new IllegalArgumentException("Invalid table participant");
    }
}
