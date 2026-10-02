package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Riichi room configuration, equipment, participants and match lifecycle. */
public final class RiichiSession extends TableSession {
    private RiichiGame game;
    @Override protected long matchDecision() { return decision; }
    RiichiRules rules;
    List<Integer> suppliedTiles;
    TimeControl timeControl = TimeControl.DEFAULT;
    PlayerHandVisibility playerHandVisibility = PlayerHandVisibility.SELF;
    boolean openHands;
    private transient List<ExternalBot> externalBots = List.of();
    final List<ReplayMatch> archiveQueue = new ArrayList<>();
    final Map<UUID, Integer> pendingExperience = new java.util.HashMap<>();
    private int lobbyTicks;

    public RiichiSession(UUID tableId, RiichiPreset preset, long seed) {
        this(tableId, preset.config(), seed);
    }

    public RiichiSession(UUID tableId, RiichiRules rules, long seed) {
        super(tableId, MahjongVariant.RIICHI, rules.players(), seed);
        this.rules = Objects.requireNonNull(rules);
        suppliedTiles = Tile.set(rules.sanma(), rules.redFives());
    }

    public RiichiGame game() { return game; }
    public RiichiRules rules() { return rules; }
    public TimeControl timeControl() { return timeControl; }
    public PlayerHandVisibility playerHandVisibility() { return playerHandVisibility; }
    public boolean openHands() { return openHands; }
    public List<ExternalBot> externalBots() { return externalBots == null ? List.of() : externalBots; }

    public RiichiRoomSettings roomSettings() {
        return new RiichiRoomSettings(rules, timeControl, playerHandVisibility, openHands,
            externalBots());
    }

    public RiichiView view(UUID recipient) { return game == null ? null : game.view(recipient); }

    public RiichiView spectatorView(SpectatorHandVisibility visibility) {
        return game == null ? null : game.spectatorView(visibility);
    }

    public void configureExternalBots(List<ExternalBot> bots) {
        var available = List.copyOf(bots);
        if (available.equals(externalBots())) return;
        externalBots = available;
        if (lobby()) changed(true);
    }

    @Override protected boolean pauseForAbsence() { return !hasSeatedHuman(); }
    @Override protected boolean allowsCompanionPlayers() { return worldPolicy.allowCompanionPlayers(); }
    @Override protected boolean allowsBots() { return worldPolicy.allowBots(); }

    @Override protected void addBotChoices(List<RoomAction> actions, int target, Participant member) {
        for (var difficulty : BotDifficulty.values())
            if (!member.bot || member.externalBotId != null || difficulty != member.botDifficulty)
                actions.add(new RoomAction(RoomAction.Type.SET_BOT, List.of(target, difficulty.ordinal())));
        if (!manual) for (int bot = 0; bot < externalBots().size(); bot++) {
            var choice = externalBots().get(bot);
            if (choice.supports(rules) && !choice.id().equals(member.externalBotId))
                actions.add(new RoomAction(RoomAction.Type.SET_BOT, List.of(target, BotDifficulty.values().length + bot)));
        }
    }

    @Override protected void setBotChoice(int target, int choice) {
        if (choice < BotDifficulty.values().length) setBot(target, BotDifficulty.values()[choice]);
        else setExternalBot(target, externalBots().get(choice - BotDifficulty.values().length));
    }

    @Override protected boolean canStartMatch() {
        if (!worldPolicy.permitsRules(rules)) return false;
        for (int seat = 0; seat < capacity; seat++) {
            String botId = externalBotId(seat);
            if (botId != null && (manual || externalBots().stream()
                .noneMatch(bot -> bot.id().equals(botId) && bot.supports(rules)))) return false;
        }
        return true;
    }

    @Override public boolean equipped() {
        return suppliedTiles.size() == (rules.sanma() ? 108 : 136) && rules.allows(RedFives.of(suppliedTiles));
    }

    @Override public boolean configureEquipment(boolean manual, List<Integer> tiles) {
        Objects.requireNonNull(tiles);
        if (!lobby() || exitVote != null) return false;
        if (!tiles.isEmpty() && (!Tile.validSet(tiles) || tiles.size() != (rules.sanma() ? 108 : 136)))
            throw new IllegalArgumentException("Equipment must contain one complete physical tile set");
        if (this.manual == manual && suppliedTiles.equals(tiles)) return true;
        if (this.manual != manual) {
            seating = new RoomSeating();
            timeControl = manual ? TimeControl.MANUAL : TimeControl.DEFAULT;
        }
        this.manual = manual;
        suppliedTiles = List.copyOf(tiles);
        resetReadiness();
        changed(true);
        return true;
    }

    public boolean configureRules(UUID actor, long expectedDecision, RiichiRules config) {
        if (config == null || !worldPolicy.permitsRules(config) || !lobby() || exitVote != null || !isHost(actor)
            || expectedDecision != decision || rules.equals(config) || config.players() == 3 && participants[3].id != null) return false;
        applyRules(config);
        changed(true);
        return true;
    }

    public boolean configureStockRedFives(RedFives reds) {
        if (!lobby() || exitVote != null || !rules.preset().allows(reds) || rules.redFives() == reds) return false;
        applyRules(rules.with(RiichiRuleOption.RED_FIVES, reds.ordinal()));
        changed(true);
        return true;
    }

    private void applyRules(RiichiRules config) {
        if (rules.players() != config.players()) seating = new RoomSeating();
        rules = config;
        capacity = config.players();
        resetReadiness();
    }

    @Override public void configureWorld(WorldPolicy policy) {
        Objects.requireNonNull(policy);
        WorldPolicy previous = worldPolicy;
        boolean changed = !policy.equals(previous);
        boolean decisionChanged = lobby()
            && (policy.allowBots() != previous.allowBots() || policy.forcedPreset() != previous.forcedPreset());
        super.configureWorld(policy);
        if (!policy.allowExperienceRewards() && !pendingExperience.isEmpty()) {
            pendingExperience.clear();
            changed = true;
        }
        if (!policy.replaysEnabled() && (game != null && game.replay != null
            || game != null && game.recorder != null && !hasExternalBot() || !archiveQueue.isEmpty())) {
            if (game != null) {
                game.replay = null;
                if (!hasExternalBot()) game.recorder = null;
            }
            archiveQueue.clear();
            changed = true;
        }
        if (lobby()) {
            boolean rosterChanged = false;
            for (int seat = 0; seat < rules.players(); seat++) {
                Participant member = participants[seat];
                if (member.bot && (!member.entityBot && !policy.allowBots()
                    || member.entityBot && !policy.allowCompanionPlayers())) {
                    participants[seat] = new Participant();
                    rosterChanged = true;
                }
            }
            if (rosterChanged) {
                seating = new RoomSeating();
                resetReadiness();
                changed = true;
                decisionChanged = true;
            }
            RiichiRules constrained = rules;
            if (policy.forcedPreset() != null && rules.preset() != policy.forcedPreset()
                && (policy.forcedPreset().players() == rules.players()
                    || java.util.stream.IntStream.range(policy.forcedPreset().players(), rules.players())
                        .allMatch(seat -> participants[seat].id == null)))
                constrained = rules.withPreset(policy.forcedPreset());
            if (!policy.allowCustomRules() && constrained.custom())
                constrained = constrained.withPreset(constrained.preset());
            if (!rules.equals(constrained)) {
                applyRules(constrained);
                changed = true;
                decisionChanged = true;
            }
        }
        if (decisionChanged) changed(true);
        else if (changed) changed(false);
    }

    public boolean configureHandVisibility(UUID actor, long expectedDecision, PlayerHandVisibility visibility) {
        if (visibility == null || visibility == playerHandVisibility || !lobby() || exitVote != null
            || !isHost(actor) || expectedDecision != decision) return false;
        playerHandVisibility = visibility;
        resetReadiness();
        changed(true);
        return true;
    }

    public boolean configureOpenHands(UUID actor, long expectedDecision, boolean enabled) {
        if (openHands == enabled || !lobby() || exitVote != null
            || !isHost(actor) || expectedDecision != decision) return false;
        openHands = enabled;
        resetReadiness();
        changed(true);
        return true;
    }

    public boolean configureClock(UUID actor, TimeControl control) {
        if (!lobby() || exitVote != null || !isHost(actor)) return false;
        timeControl = Objects.requireNonNull(control);
        resetReadiness();
        changed(true);
        return true;
    }

    public List<ReplayMatch> pendingReplays() { return List.copyOf(archiveQueue); }
    public void acknowledgeReplay(UUID id) { archiveQueue.removeIf(match -> match.id().equals(id)); }
    public Map<UUID, Integer> pendingExperience() { return Map.copyOf(pendingExperience); }
    public void validate() {
        validateRoom();
        if (game != null) game.validate();
    }
    public int takeExperience(UUID player) {
        Integer amount = pendingExperience.remove(player);
        return amount == null ? 0 : worldPolicy.limitExperienceChange(amount);
    }

    boolean hasExternalBot() {
        for (int seat = 0; seat < rules.players(); seat++) if (externalBotId(seat) != null) return true;
        return false;
    }

    public State save() {
        return new State(State.FORMAT, saveRoom(), rules, suppliedTiles, timeControl,
            playerHandVisibility, openHands, archiveQueue, pendingExperience,
            game == null ? null : game.save());
    }

    public static RiichiSession restore(State state) {
        Objects.requireNonNull(state);
        var session = new RiichiSession(state.room().tableId(), state.rules(), state.room().seed());
        session.restoreRoom(state.room());
        session.rules = state.rules();
        session.suppliedTiles = state.suppliedTiles();
        session.timeControl = state.timeControl();
        session.playerHandVisibility = state.playerHandVisibility();
        session.openHands = state.openHands();
        session.archiveQueue.addAll(state.archiveQueue());
        session.pendingExperience.putAll(state.pendingExperience());
        if (state.game() != null) session.game = RiichiGame.restore(session, state.game(), state.room().decision());
        return session;
    }

    public record State(int format, TableSession.State room, RiichiRules rules, List<Integer> suppliedTiles,
                        TimeControl timeControl, PlayerHandVisibility playerHandVisibility,
                        boolean openHands, List<ReplayMatch> archiveQueue,
                        Map<UUID, Integer> pendingExperience, RiichiGame.State game) {
        public static final int FORMAT = 4;

        public State {
            if (format != FORMAT) throw new IllegalArgumentException("Unsupported Riichi session format");
            Objects.requireNonNull(room);
            Objects.requireNonNull(rules);
            Objects.requireNonNull(timeControl);
            Objects.requireNonNull(playerHandVisibility);
            suppliedTiles = List.copyOf(suppliedTiles);
            archiveQueue = List.copyOf(archiveQueue);
            pendingExperience = Map.copyOf(pendingExperience);
            if (room.variant() != MahjongVariant.RIICHI || room.capacity() != rules.players()
                || !suppliedTiles.isEmpty() && (!Tile.validSet(suppliedTiles)
                    || suppliedTiles.size() != (rules.sanma() ? 108 : 136))
                || (room.lifecycle() == Lifecycle.LOBBY) != (game == null)
                || game != null && (room.lifecycle() == Lifecycle.FINISHED)
                    != (game.phase() == RiichiGame.Phase.MATCH_END))
                throw new IllegalArgumentException("Invalid Riichi session state");
        }
    }

    @Override protected void startMatch() {
        lifecycle = Lifecycle.PLAYING;
        renewIncarnation();
        game = new RiichiGame(this);
        game.startMatch();
    }

    @Override protected void clearMatch() { game = null; }

    void matchPhaseChanged(RiichiGame.Phase phase) {
        lifecycle = phase == RiichiGame.Phase.MATCH_END ? Lifecycle.FINISHED : Lifecycle.PLAYING;
        changed(true);
    }

    @Override public void tick() {
        if (game != null) { game.tick(); return; }
        if (!tickRoom()) return;
        if (++lobbyTicks % 12 != 0) return;
        if (seating.stage == RoomSeating.Stage.DRAWING)
            for (int seat = 0; seat < capacity; seat++)
                if (participants[seat].id != null && !participants[seat].bot && seating.winds[seat] < 0) return;
        for (int seat = 0; seat < capacity; seat++) if (participants[seat].bot && participants[seat].externalBotId == null) {
            var actions = roomActions(seat);
            int ready = actions.indexOf(new RoomAction(RoomAction.Type.READY));
            if (ready >= 0) { actRoom(participants[seat].id, decision, actions.get(ready)); return; }
            for (RoomAction action : actions) if (action.type() == RoomAction.Type.DRAW_WIND) {
                actRoom(participants[seat].id, decision, action);
                return;
            }
        }
    }

    @Override protected void rosterChanged() {
        // The room roster is authoritative; an active match holds the same participant objects.
        if (game != null) game.rebindPlayers();
    }
}
