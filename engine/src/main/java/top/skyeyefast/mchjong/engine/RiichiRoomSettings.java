package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

/** Public Riichi room configuration, separate from room lifecycle and live match state. */
public record RiichiRoomSettings(RiichiRules rules, TimeControl timeControl,
                                 PlayerHandVisibility playerHandVisibility, boolean openHands,
                                 boolean convenienceHints, List<ExternalBot> externalBots) {
    public RiichiRoomSettings {
        Objects.requireNonNull(rules);
        Objects.requireNonNull(timeControl);
        Objects.requireNonNull(playerHandVisibility);
        externalBots = List.copyOf(externalBots);
    }
}
