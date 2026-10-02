package top.skyeyefast.mchjong.engine;

/** Private seat preferences; each rule independently maps them to issued actions. */
public record MatchAutomation(boolean win, boolean noCalls, boolean discard) {
    public static final MatchAutomation DEFAULT = new MatchAutomation(false, false, false);
    public enum Option { WIN, NO_CALLS, DISCARD }

    public boolean enabled(Option option) {
        return switch (option) { case WIN -> win; case NO_CALLS -> noCalls; case DISCARD -> discard; };
    }

    public MatchAutomation with(Option option, boolean enabled) {
        return new MatchAutomation(option == Option.WIN ? enabled : win,
            option == Option.NO_CALLS ? enabled : noCalls, option == Option.DISCARD ? enabled : discard);
    }
}
