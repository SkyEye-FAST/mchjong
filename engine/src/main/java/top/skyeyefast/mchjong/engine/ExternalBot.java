package top.skyeyefast.mchjong.engine;

import java.util.List;

/** Server-advertised identity and exact rule compatibility for an external opponent. */
public record ExternalBot(String id, String name, int playerCount, List<RiichiPreset> presets) {
    public ExternalBot {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,63}") || name == null || name.isBlank()
            || name.length() > 64 || playerCount < 3 || playerCount > 4 || presets == null || presets.isEmpty()
            || presets.stream().anyMatch(preset -> preset == null || preset.players() != playerCount))
            throw new IllegalArgumentException("Invalid external bot description");
        presets = List.copyOf(presets);
    }

    public boolean supports(RiichiRules rules) {
        return playerCount == rules.players() && presets.contains(rules.preset());
    }
}
