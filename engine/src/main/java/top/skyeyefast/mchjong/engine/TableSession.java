package top.skyeyefast.mchjong.engine;

import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** The closed, server-owned room boundary shared by the two built-in rule runtimes. */
public abstract sealed class TableSession permits RiichiGame, McrSession {
    public enum Lifecycle { LOBBY, PLAYING, FINISHED }
    public static final int AWAY_GRACE_TICKS = 5 * 20;

    UUID tableId;
    MahjongVariant variant;
    int capacity;
    UUID hostId;
    Participant[] participants = new Participant[4];
    RoomSeating seating = new RoomSeating();
    Lifecycle lifecycle = Lifecycle.LOBBY;
    long revision = 1;
    long decision = 1;
    long seed;
    boolean manual;
    ExitVote exitVote;
    UUID pendingLeaveDecision;
    long exitVoteSequence;
    int exitCooldown;
    transient WorldPolicy worldPolicy = WorldPolicy.DEFAULT;
    transient UUID incarnation = UUID.randomUUID();

    protected TableSession(UUID tableId, MahjongVariant variant, int capacity, long seed) {
        this.tableId = Objects.requireNonNull(tableId);
        this.variant = Objects.requireNonNull(variant);
        this.capacity = capacity;
        this.seed = seed;
        for (int seat = 0; seat < 4; seat++) participants[seat] = new Participant();
        validateRoom();
    }

    public UUID tableId() { return tableId; }
    public UUID incarnation() { return incarnation; }
    public MahjongVariant variant() { return variant; }
    public int capacity() { return capacity; }
    public Lifecycle lifecycle() { return lifecycle; }
    public long revision() { return revision; }
    public long decision() { return decision; }
    public boolean lobby() { return lifecycle == Lifecycle.LOBBY; }
    public boolean manual() { return manual; }
    public void configureWorld(WorldPolicy policy) { worldPolicy = Objects.requireNonNull(policy); }
    public int host() { return seatOf(hostId); }
    public boolean isHost(UUID actor) { return actor != null && actor.equals(hostId) && seatOf(actor) >= 0; }

    public int seatOf(UUID id) {
        if (id != null) for (int seat = 0; seat < capacity; seat++)
            if (id.equals(participants[seat].id)) return seat;
        return -1;
    }

    public boolean occupied(int seat) {
        return seat >= 0 && seat < capacity && participants[seat].id != null;
    }

    public boolean full() {
        for (int seat = 0; seat < capacity; seat++) if (!occupied(seat)) return false;
        return true;
    }

    public boolean join(UUID id, String name, int seat) {
        if (id == null || name == null || name.isBlank() || seat < 0 || seat >= capacity) return false;
        int existing = seatOf(id);
        if (existing >= 0) {
            if (existing != seat) return false;
            if (participants[seat].presence != PlayerPresence.SEATED) {
                participants[seat].presence = PlayerPresence.SEATED;
                participants[seat].awayTicks = 0;
                participants[seat].presenceKnown = true;
                pendingLeaveDecision = null;
                changed(lobby());
            }
            return true;
        }
        if (!lobby() || exitVote != null || occupied(seat) || name.chars().anyMatch(Character::isISOControl)) return false;
        Participant participant = participants[seat];
        participant.id = id;
        participant.name = name.substring(0, Math.min(32, name.length()));
        participant.presence = PlayerPresence.SEATED;
        participant.presenceKnown = true;
        if (hostId == null) hostId = id;
        rosterChanged();
        changed(true);
        return true;
    }

    /** A successful mount is observed by the world adapter, not asserted by a network request. */
    public boolean synchronizeSeats(Map<UUID, Integer> mounted, Set<UUID> connected) {
        var observed = Map.copyOf(mounted);
        boolean changed = false;
        for (int seat = 0; seat < capacity; seat++) {
            Participant participant = participants[seat];
            if (participant.id == null || participant.bot && !participant.entityBot) continue;
            int expected = seat;
            boolean unique = observed.values().stream().filter(value -> value == expected).count() == 1;
            PlayerPresence previous = participant.presence;
            if (unique && Objects.equals(observed.get(participant.id), seat)) {
                participant.presence = PlayerPresence.SEATED;
                participant.awayTicks = 0;
            } else if (!participant.entityBot && !connected.contains(participant.id)) {
                participant.presence = PlayerPresence.DISCONNECTED;
                participant.awayTicks = 0;
            } else if (previous == PlayerPresence.SEATED || !participant.presenceKnown) {
                participant.presence = PlayerPresence.AWAY;
                participant.awayTicks = AWAY_GRACE_TICKS;
            }
            participant.presenceKnown = true;
            if (participant.presence == PlayerPresence.DISCONNECTED && participant.id.equals(pendingLeaveDecision))
                pendingLeaveDecision = null;
            if (previous != participant.presence) {
                if (lobby()) participant.ready = false;
                changed = true;
            }
        }
        if (changed) changed(lobby());
        if (hasSeatedHuman()) pendingLeaveDecision = null;
        return changed;
    }

    public int seated() {
        int mask = 0;
        for (int seat = 0; seat < capacity; seat++)
            if (occupied(seat) && participants[seat].presence == PlayerPresence.SEATED) mask |= 1 << seat;
        return mask;
    }

    public boolean hasSeatedHuman() {
        for (int seat = 0; seat < capacity; seat++) {
            Participant participant = participants[seat];
            if (participant.id != null && !participant.bot && participant.presence == PlayerPresence.SEATED) return true;
        }
        return false;
    }

    public boolean paused() {
        if (lobby() || lifecycle == Lifecycle.FINISHED) return false;
        return variant == MahjongVariant.MCR ? seated() != (1 << capacity) - 1 : !hasSeatedHuman();
    }

    public int viewerSeat(UUID recipient) {
        int seat = seatOf(recipient);
        return seat >= 0 && participants[seat].presence == PlayerPresence.SEATED ? seat : -1;
    }

    /** A decision is meaningful only inside this table's current live incarnation. */
    public int authorize(UUID actor, UUID expectedTable, UUID expectedIncarnation, long expectedDecision,
                         long currentDecision, boolean requireAllSeated) {
        if (!tableId.equals(expectedTable) || !incarnation.equals(expectedIncarnation)
            || expectedDecision != currentDecision || requireAllSeated && seated() != (1 << capacity) - 1) return -1;
        return viewerSeat(actor);
    }

    void changed(boolean invalidateDecision) {
        revision = Math.addExact(revision, 1);
        if (invalidateDecision) decision = Math.addExact(decision, 1);
    }

    void resetReadiness() {
        for (Participant participant : participants) participant.ready = participant.bot;
    }

    void renewIncarnation() {
        incarnation = UUID.randomUUID();
        changed(true);
    }

    /** Reload never restores a mount or a reusable request token. */
    void restored() {
        validateRoom();
        renewIncarnation();
        for (Participant participant : participants) {
            participant.presence = participant.bot && !participant.entityBot
                ? PlayerPresence.SEATED : PlayerPresence.DISCONNECTED;
            participant.awayTicks = 0;
            participant.presenceKnown = false;
        }
    }

    /** Change the built-in ruleset while retaining only the prepared human roster. */
    public TableSession selectVariant(UUID actor, long expectedDecision, MahjongVariant selected) {
        if (selected == null || selected == variant || !lobby() || !isHost(actor)
            || expectedDecision != decision || exitVote != null || manual
            || seating.stage != RoomSeating.Stage.GATHERING
            || selected == MahjongVariant.MCR && capacity != 4) return null;
        for (Participant participant : participants) if (participant.bot) return null;
        TableSession replacement = selected == MahjongVariant.MCR
            ? new McrSession(tableId, seed)
            : new RiichiGame(tableId, RiichiPreset.MAHJONG_SOUL_4, seed);
        for (int seat = 0; seat < 4; seat++)
            replacement.participants[seat] = Participant.restore(participants[seat].snapshot());
        replacement.hostId = hostId;
        replacement.worldPolicy = worldPolicy;
        replacement.revision = revision;
        replacement.decision = decision;
        replacement.renewIncarnation();
        replacement.rosterChanged();
        return replacement;
    }

    public abstract boolean equipped();
    public abstract boolean configureEquipment(boolean manual, List<Integer> tiles);
    public abstract void tick();
    protected abstract void startMatch();
    protected abstract void clearMatch();

    /** Rebind rule-only seat state after a roster replacement or permutation. */
    protected void rosterChanged() {}
    protected boolean canStartMatch() { return true; }
    protected List<ExternalBot> externalBots() { return List.of(); }
    protected boolean supportsExternalBot(ExternalBot bot) { return false; }

    public boolean trainingSeat(int seat) { return occupied(seat) && participants[seat].bot; }
    public boolean entityBot(UUID id) { int seat = seatOf(id); return seat >= 0 && participants[seat].entityBot; }
    public String externalBotId(int seat) { return trainingSeat(seat) ? participants[seat].externalBotId : null; }

    public boolean joinEntityBot(UUID owner, UUID id, String name, int seat) {
        if (variant != MahjongVariant.RIICHI || !worldPolicy.allowCompanionPlayers()
            || owner == null || id == null || owner.equals(id) || name == null || name.isBlank()
            || name.chars().anyMatch(Character::isISOControl) || seat < 0 || seat >= capacity || seatOf(id) >= 0
            || hostId != null && seatOf(owner) < 0 || seatOf(owner) >= 0 && participants[seatOf(owner)].bot
            || !lobby() || exitVote != null || occupied(seat)) return false;
        setBot(seat, BotDifficulty.EASY);
        var bot = participants[seat];
        bot.id = id;
        bot.name = name.substring(0, Math.min(256, name.length()));
        bot.entityBot = true;
        bot.presence = PlayerPresence.SEATED;
        bot.presenceKnown = true;
        resetReadiness();
        changed(true);
        return true;
    }

    public void leaveEntityBot(UUID id) {
        int seat = seatOf(id);
        if (seat < 0 || !participants[seat].entityBot) return;
        if (lobby()) removeMember(seat);
        else {
            var bot = participants[seat];
            bot.entityBot = false;
            bot.id = UUID.randomUUID();
            bot.name = "Bot " + (seat + 1);
            bot.presence = PlayerPresence.SEATED;
            bot.awayTicks = 0;
            changed(false);
        }
    }

    public void unseat(UUID actor) { unseat(actor, true); }

    public void unseat(UUID actor, boolean voluntary) {
        int seat = seatOf(actor);
        if (seat < 0 || participants[seat].bot) return;
        if (lobby()) {
            if (exitVote != null) cancelExit();
            removeMember(seat);
            return;
        }
        if (participants[seat].presence == PlayerPresence.DISCONNECTED) return;
        if (participants[seat].presence == PlayerPresence.SEATED) {
            participants[seat].presence = PlayerPresence.AWAY;
            participants[seat].awayTicks = AWAY_GRACE_TICKS;
        }
        pendingLeaveDecision = voluntary && !hasSeatedHuman() ? actor : null;
        changed(false);
    }

    protected final void removeMember(int seat) {
        UUID former = participants[seat].id;
        participants[seat] = new Participant();
        if (Objects.equals(former, hostId)) {
            hostId = null;
            for (Participant remaining : participants) if (remaining.id != null && !remaining.bot) {
                hostId = remaining.id;
                break;
            }
        }
        resetReadiness();
        rosterChanged();
        if (hostId == null && Arrays.stream(participants).noneMatch(member -> member.entityBot)) closeMatch();
        changed(true);
    }

    protected final void setBot(int seat, BotDifficulty difficulty) {
        var bot = participants[seat];
        if (!bot.bot) {
            bot = participants[seat] = new Participant();
            bot.id = UUID.randomUUID();
        }
        if (!bot.entityBot) bot.name = "Bot " + (seat + 1);
        bot.bot = bot.ready = true;
        bot.botDifficulty = difficulty;
        bot.externalBotId = null;
        bot.presence = PlayerPresence.SEATED;
        bot.presenceKnown = true;
        rosterChanged();
    }

    protected final void setExternalBot(int seat, ExternalBot external) {
        setBot(seat, BotDifficulty.EASY);
        participants[seat].externalBotId = external.id();
    }

    protected final void assignSeats() {
        Participant[] assigned = participants.clone();
        for (int seat = 0; seat < capacity; seat++) {
            var participant = participants[seat];
            int destination = seating.winds[seat];
            if ((!participant.bot || participant.entityBot) && destination != seat) {
                participant.presence = PlayerPresence.AWAY;
                participant.awayTicks = AWAY_GRACE_TICKS;
            }
            participant.ready = participant.bot;
            if (participant.bot && !participant.entityBot) participant.name = "Bot " + (destination + 1);
            assigned[destination] = participant;
        }
        participants = assigned;
        seating.positioned(capacity);
        rosterChanged();
    }

    public boolean transferHost(UUID actor, UUID successor) {
        int seat = seatOf(successor);
        if (!isHost(actor) || actor.equals(successor) || seat < 0 || participants[seat].bot || exitVote != null) return false;
        hostId = successor;
        changed(true);
        return true;
    }

    public boolean leaveDecision(UUID actor) {
        return actor != null && actor.equals(pendingLeaveDecision) && !lobby() && !hasSeatedHuman();
    }

    public boolean resolveLeave(UUID actor, boolean retain) {
        if (!leaveDecision(actor)) return false;
        pendingLeaveDecision = null;
        if (retain) changed(false);
        else closeMatch();
        return true;
    }

    public boolean requestExit(UUID actor) {
        int seat = seatOf(actor);
        if (seat < 0 || participants[seat].bot || exitVote != null || lifecycle == Lifecycle.FINISHED) return false;
        if (lobby()) {
            if (!isHost(actor)) return false;
            closeMatch();
            return true;
        }
        int humans = (int) Arrays.stream(participants).filter(member -> member.id != null && !member.bot).count();
        if (humans == 1) { closeMatch(); return true; }
        if (exitCooldown > 0) return false;
        exitVote = new ExitVote(++exitVoteSequence, seat, ExitVote.DURATION_TICKS, humans, List.of(seat));
        changed(true);
        return true;
    }

    public boolean answerExit(UUID actor, long voteId, boolean agree) {
        int seat = seatOf(actor);
        if (seat < 0 || participants[seat].bot || exitVote == null || exitVote.id() != voteId) return false;
        if (!agree) { cancelExit(); return true; }
        if (exitVote.agreed().contains(seat)) return false;
        var agreed = new ArrayList<>(exitVote.agreed());
        agreed.add(seat);
        if (agreed.size() == exitVote.required()) closeMatch();
        else {
            exitVote = new ExitVote(voteId, exitVote.requester(), exitVote.ticksLeft(), exitVote.required(), agreed);
            changed(false);
        }
        return true;
    }

    private void cancelExit() {
        exitVote = null;
        exitCooldown = ExitVote.DURATION_TICKS;
        changed(true);
    }

    protected final void closeMatch() { returnToLobby(false); }

    protected final void returnToLobby(boolean retainParticipants) {
        exitVote = null;
        pendingLeaveDecision = null;
        exitCooldown = 0;
        if (!retainParticipants) {
            hostId = null;
            for (int seat = 0; seat < 4; seat++) participants[seat] = new Participant();
        }
        resetReadiness();
        seating = new RoomSeating();
        lifecycle = Lifecycle.LOBBY;
        seed += 0x9e3779b97f4a7c15L;
        clearMatch();
        rosterChanged();
        renewIncarnation();
    }

    /** Room clocks and votes run before the rule runtime advances a tick. */
    protected final boolean tickRoom() {
        for (int seat = 0; seat < capacity; seat++) {
            var participant = participants[seat];
            if (participant.id == null || participant.bot && !participant.entityBot) continue;
            if (participant.presence == PlayerPresence.AWAY && participant.awayTicks > 0) {
                participant.awayTicks--;
            }
            if (participant.presence == PlayerPresence.AWAY && participant.awayTicks == 0) {
                participant.presence = PlayerPresence.DISCONNECTED;
                if (participant.entityBot) { leaveEntityBot(participant.id); continue; }
                changed(lobby());
            }
        }
        if (lobby() && hostId != null && Arrays.stream(participants)
            .filter(member -> member.id != null && !member.bot)
            .allMatch(member -> member.presence == PlayerPresence.DISCONNECTED)) closeMatch();
        if (paused()) return false;
        if (exitCooldown > 0) exitCooldown--;
        if (exitVote == null) return true;
        if (exitVote.ticksLeft() <= 1) cancelExit();
        else {
            exitVote = new ExitVote(exitVote.id(), exitVote.requester(), exitVote.ticksLeft() - 1,
                exitVote.required(), exitVote.agreed());
            changed(false);
        }
        return false;
    }

    public List<RoomAction> roomActions(UUID actor) { return roomActions(seatOf(actor)); }

    public final TableRoomView roomView(UUID recipient) {
        int viewer = viewerSeat(recipient);
        var seats = new ArrayList<TableRoomView.Seat>();
        var available = new ArrayList<Integer>();
        for (int seat = 0; seat < capacity; seat++) {
            var participant = participants[seat];
            seats.add(new TableRoomView.Seat(participant.snapshot(), participant.presence, seating.winds[seat]));
            if ((seating.available & 1 << seat) != 0) available.add(seat);
        }
        return new TableRoomView(tableId, incarnation, revision, decision, variant, lifecycle,
            host(), viewer, manual, equipped(), paused(), seating.stage, available, seats,
            viewer < 0 ? List.of() : roomActions(viewer), exitVote, leaveDecision(recipient));
    }

    protected final List<RoomAction> roomActions(int seat) {
        if (!occupied(seat) || exitVote != null) return List.of();
        if (lifecycle == Lifecycle.FINISHED) return seat == host()
            ? List.of(new RoomAction(RoomAction.Type.RETURN_TO_LOBBY)) : List.of();
        if (!lobby()) return List.of();
        var actions = new ArrayList<RoomAction>();
        var participant = participants[seat];
        if (!participant.bot) actions.add(new RoomAction(RoomAction.Type.LEAVE_ROOM));
        if (seating.stage == RoomSeating.Stage.POSITIONING && (participant.bot || participant.presence == PlayerPresence.SEATED)
            && (!participant.bot || !participant.ready) && equipped()) actions.add(new RoomAction(RoomAction.Type.READY));
        if (seating.stage == RoomSeating.Stage.DRAWING && seating.winds[seat] < 0)
            for (int tile = 0; tile < capacity; tile++) if ((seating.available & 1 << tile) != 0)
                actions.add(new RoomAction(RoomAction.Type.DRAW_WIND, tile));
        if (seat != host()) return List.copyOf(actions);
        boolean bots = variant == MahjongVariant.RIICHI && worldPolicy.allowBots();
        if (!full() && bots) actions.add(new RoomAction(RoomAction.Type.FILL_BOTS));
        if (full() && seating.stage == RoomSeating.Stage.GATHERING && equipped())
            actions.add(new RoomAction(RoomAction.Type.BEGIN_SEATING));
        for (int target = 0; target < capacity; target++) {
            var member = participants[target];
            if (target != seat && (member.id == null || member.bot || member.presence == PlayerPresence.DISCONNECTED)) {
                if (bots) for (var difficulty : BotDifficulty.values())
                    if (!member.bot || member.externalBotId != null || difficulty != member.botDifficulty)
                        actions.add(new RoomAction(RoomAction.Type.SET_BOT, List.of(target, difficulty.ordinal())));
                if (bots && !manual) for (int bot = 0; bot < externalBots().size(); bot++) {
                    var choice = externalBots().get(bot);
                    if (supportsExternalBot(choice) && !choice.id().equals(member.externalBotId))
                        actions.add(new RoomAction(RoomAction.Type.SET_BOT, List.of(target, BotDifficulty.values().length + bot)));
                }
                if (member.bot) actions.add(new RoomAction(RoomAction.Type.REMOVE_BOT, target));
            }
            if (target != seat && member.id != null && !member.bot)
                actions.add(new RoomAction(RoomAction.Type.TRANSFER_HOST, target));
        }
        return List.copyOf(actions);
    }

    public final boolean actRoom(UUID actor, UUID expectedTable, UUID expectedIncarnation, long expectedDecision, int index) {
        if (!tableId.equals(expectedTable) || !incarnation.equals(expectedIncarnation) || expectedDecision != decision) return false;
        var offered = roomActions(actor);
        return index >= 0 && index < offered.size() && actRoom(actor, expectedDecision, offered.get(index));
    }

    final boolean actRoom(UUID actor, long expectedDecision, RoomAction action) {
        if (expectedDecision != decision || !roomActions(actor).contains(action)) return false;
        int seat = seatOf(actor);
        switch (action.type()) {
            case READY -> participants[seat].ready = !participants[seat].ready;
            case LEAVE_ROOM -> removeMember(seat);
            case BEGIN_SEATING -> {
                seating.begin(capacity, manual, seed ^ decision);
                resetReadiness();
                if (!manual) assignSeats();
            }
            case DRAW_WIND -> {
                seating.draw(seat, action.arguments().get(0));
                if (seating.complete(capacity)) assignSeats();
            }
            case FILL_BOTS -> {
                for (int target = 0; target < capacity; target++) if (!occupied(target)) setBot(target, BotDifficulty.EASY);
            }
            case SET_BOT -> {
                int target = action.arguments().get(0), choice = action.arguments().get(1);
                if (choice < BotDifficulty.values().length) setBot(target, BotDifficulty.values()[choice]);
                else setExternalBot(target, externalBots().get(choice - BotDifficulty.values().length));
            }
            case REMOVE_BOT -> removeMember(action.arguments().get(0));
            case TRANSFER_HOST -> hostId = participants[action.arguments().get(0)].id;
            case RETURN_TO_LOBBY -> returnToLobby(true);
        }
        changed(true);
        if (allReady()) startMatch();
        return true;
    }

    protected final boolean allReady() {
        if (!lobby() || !equipped() || !canStartMatch() || seating.stage != RoomSeating.Stage.POSITIONING || !full()) return false;
        for (int seat = 0; seat < capacity; seat++) {
            var participant = participants[seat];
            if (!participant.ready || (!participant.bot || participant.entityBot) && participant.presence != PlayerPresence.SEATED)
                return false;
        }
        return true;
    }

    protected final void validateRoom() {
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(variant);
        Objects.requireNonNull(lifecycle);
        if (capacity != 3 && capacity != 4 || variant == MahjongVariant.MCR && capacity != 4
            || participants == null || participants.length != 4 || revision < 1 || decision < 1)
            throw new IllegalStateException("Invalid table room");
        Objects.requireNonNull(seating).validate(capacity);
        var ids = new HashSet<UUID>();
        for (int seat = 0; seat < 4; seat++) {
            Participant participant = Objects.requireNonNull(participants[seat]);
            if (participant.id != null && (seat >= capacity || !ids.add(participant.id)
                || participant.name == null || participant.name.isBlank() || participant.name.length() > 256
                || participant.name.chars().anyMatch(Character::isISOControl)))
                throw new IllegalStateException("Invalid room participant");
            if (participant.entityBot && (!participant.bot || participant.id == null)
                || participant.bot && participant.id == null
                || participant.externalBotId != null && (!participant.bot || participant.entityBot
                    || !participant.externalBotId.matches("[a-z0-9][a-z0-9_-]{0,63}")))
                throw new IllegalStateException("Invalid room bot");
            Objects.requireNonNull(participant.botDifficulty);
            if (participant.presence == null) participant.presence = PlayerPresence.DISCONNECTED;
        }
        if (hostId != null && (seatOf(hostId) < 0 || participants[seatOf(hostId)].bot))
            throw new IllegalStateException("Invalid room host");
        if (exitCooldown < 0 || exitCooldown > ExitVote.DURATION_TICKS || exitVoteSequence < 0
            || pendingLeaveDecision != null && (lobby() || seatOf(pendingLeaveDecision) < 0))
            throw new IllegalStateException("Invalid room controls");
        if (exitVote != null && (lobby() || exitVote.ticksLeft() < 1
            || exitVote.ticksLeft() > ExitVote.DURATION_TICKS || exitVote.id() > exitVoteSequence
            || exitVote.required() < 2
            || exitVote.required() != Arrays.stream(participants).limit(capacity)
                .filter(member -> member.id != null && !member.bot).count()
            || !exitVote.agreed().contains(exitVote.requester())
            || exitVote.agreed().size() >= exitVote.required()
            || new HashSet<>(exitVote.agreed()).size() != exitVote.agreed().size()
            || exitVote.agreed().stream().anyMatch(seat -> seat < 0 || seat >= capacity
                || participants[seat].id == null || participants[seat].bot)))
            throw new IllegalStateException("Invalid room exit vote");
    }

    /** Mutable only on the server thread. It contains no tiles, points or rule state. */
    static final class Participant {
        UUID id;
        String name = "";
        boolean bot;
        boolean entityBot;
        BotDifficulty botDifficulty = BotDifficulty.EASY;
        String externalBotId;
        boolean ready;
        transient PlayerPresence presence = PlayerPresence.DISCONNECTED;
        transient int awayTicks;
        transient boolean presenceKnown;

        TableParticipant snapshot() {
            return new TableParticipant(id, name, bot, entityBot, botDifficulty, externalBotId, ready);
        }

        static Participant restore(TableParticipant saved) {
            var participant = new Participant();
            participant.id = saved.id();
            participant.name = saved.name();
            participant.bot = saved.bot();
            participant.entityBot = saved.entityBot();
            participant.botDifficulty = saved.difficulty();
            participant.externalBotId = saved.externalBotId();
            participant.ready = saved.ready();
            return participant;
        }
    }

    public List<TableParticipant> participants() {
        return Arrays.stream(participants).limit(capacity).map(Participant::snapshot).toList();
    }

    /** Common private room state. Neither live presence nor request incarnations survive a reload. */
    public record State(UUID tableId, MahjongVariant variant, int capacity, UUID hostId,
                        List<TableParticipant> participants, RoomSeating.Saved seating,
                        Lifecycle lifecycle, long revision, long decision, long seed, boolean manual,
                        ExitVote exitVote, UUID pendingLeaveDecision, long exitVoteSequence, int exitCooldown) {
        public State {
            Objects.requireNonNull(tableId);
            Objects.requireNonNull(variant);
            Objects.requireNonNull(seating);
            Objects.requireNonNull(lifecycle);
            participants = List.copyOf(participants);
            if (capacity != 3 && capacity != 4 || variant == MahjongVariant.MCR && (capacity != 4 || manual)
                || participants.size() != 4 || revision < 1 || revision >= Long.MAX_VALUE - 1
                || decision < 1 || decision >= Long.MAX_VALUE - 1 || exitVoteSequence < 0
                || exitCooldown < 0 || exitCooldown > ExitVote.DURATION_TICKS)
                throw new IllegalArgumentException("Invalid table session state");
        }
    }

    protected final State saveRoom() {
        validateRoom();
        return new State(tableId, variant, capacity, hostId,
            Arrays.stream(participants).map(Participant::snapshot).toList(), seating.save(), lifecycle,
            revision, decision, seed, manual, exitVote, pendingLeaveDecision, exitVoteSequence, exitCooldown);
    }

    protected final void restoreRoom(State state) {
        tableId = state.tableId();
        variant = state.variant();
        capacity = state.capacity();
        hostId = state.hostId();
        participants = state.participants().stream().map(Participant::restore).toArray(Participant[]::new);
        seating = RoomSeating.restore(state.seating(), capacity);
        lifecycle = state.lifecycle();
        revision = state.revision();
        decision = state.decision();
        seed = state.seed();
        manual = state.manual();
        exitVote = state.exitVote();
        pendingLeaveDecision = state.pendingLeaveDecision();
        exitVoteSequence = state.exitVoteSequence();
        exitCooldown = state.exitCooldown();
        restored();
    }

    public final boolean synchronizeSeats(Map<UUID, Integer> mounted) {
        return synchronizeSeats(mounted, mounted.keySet());
    }
}
