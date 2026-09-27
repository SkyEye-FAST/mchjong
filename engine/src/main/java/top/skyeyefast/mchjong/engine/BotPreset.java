package top.skyeyefast.mchjong.engine;

import java.nio.file.Path;
import java.util.List;

/** Administrator-owned process configuration; only Choice is public room metadata. */
public record BotPreset(String id, String name, RuleSet rules, List<String> command, String directory, int timeoutSeconds) {
    public BotPreset {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]{0,47}") || name == null || name.isBlank() || name.length() > 64
            || rules == null || rules.players() != 4 || command == null || command.isEmpty() || command.size() > 32
            || command.stream().anyMatch(arg -> arg == null || arg.length() > 4096 || arg.indexOf('\0') >= 0)
            || directory == null || timeoutSeconds < 1 || timeoutSeconds > 120)
            throw new IllegalArgumentException("Invalid mjai bot preset");
        command = List.copyOf(command);
        if (!Path.of(command.get(0)).isAbsolute() || !Path.of(directory).isAbsolute())
            throw new IllegalArgumentException("Mjai executable and directory must be absolute paths");
    }

    public boolean supports(RuleConfig config) { return rules.config().equals(config); }
    public Choice choice(RuleConfig config) { return new Choice(id, name, supports(config)); }
    public record Choice(String id, String name, boolean compatible) {}
}
