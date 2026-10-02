package top.skyeyefast.mchjong.world;

import java.util.List;

/** Public service availability; contains no endpoint or model details. */
public record BotServiceState(String discoveryError, List<String> seatErrors) {
    public BotServiceState { seatErrors = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(seatErrors)); }
}
