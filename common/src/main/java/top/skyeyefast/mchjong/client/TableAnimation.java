package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Recipient-safe, interruptible presentation. Sampling never advances the game or sends a packet. */
public final class TableAnimation {
    public static final long DEAL_MILLIS = Game.DEAL_TICKS * 50L;
    private static final Map<MahjongTableBlockEntity, TableAnimation> TABLES = new WeakHashMap<>();
    public record Frame(TableScene.Piece piece, float pitch) {}
    public record Cue(int seat, String key, long started) {}
    private record Key(TableScene.Area area, int seat, int identity) {}
    private record Motion(Frame from, Frame to, long start, long duration, double arc, long staged) {
        Frame at(long now) {
            if (now >= start + duration) return to;
            if (now <= start) {
                if (staged < 0) return moved(to, from.piece().position(), from.piece().yaw(), from.pitch(), from.piece().tile(), from.piece().back());
                double rise = ease((now - staged) / 360.0);
                return moved(to, from.piece().position().add(0, -0.32 * (1 - rise), 0), from.piece().yaw(),
                    from.pitch(), Tile.HIDDEN, true);
            }
            double fraction = Math.clamp((now - start) / (double) duration, 0, 1);
            double progress = ease(fraction);
            Vec3 position = from.piece().position().lerp(to.piece().position(), progress).add(0, Math.sin(Math.PI * fraction) * arc, 0);
            float yaw = from.piece().yaw() + (float) (angle(to.piece().yaw() - from.piece().yaw()) * progress);
            float pitch = from.pitch() + (float) ((to.pitch() - from.pitch()) * progress);
            boolean sourceFace = fraction < 0.45 && from.piece().tile() < 0;
            return moved(to, position, yaw, pitch, sourceFace ? Tile.HIDDEN : to.piece().tile(),
                sourceFace || (fraction < 0.5 ? from.piece().back() : to.piece().back()));
        }
    }

    private TableView view;
    private Map<Key, Motion> motions = Map.of();
    private List<Frame> settled = List.of();
    private long ending;
    private long openingUntil;
    private final long[] riichiStarted = new long[4];
    private List<Cue> cues = List.of();

    public static TableAnimation of(MahjongTableBlockEntity table) {
        return TABLES.computeIfAbsent(table, ignored -> new TableAnimation());
    }

    public List<Frame> settled() { return settled; }
    public boolean dealing(long now) { return now < openingUntil; }
    public boolean moving(long now) { return now < ending; }
    public List<Cue> cues(long now) { return cues.stream().filter(cue -> now - cue.started() < 1100).toList(); }
    public double riichiProgress(int seat, long now) { return ease((now - riichiStarted[seat]) / 450.0); }

    private static Frame frame(TableScene.Piece piece) {
        // Face-down tiles are physically flipped; repainting the face leaves the colored shell underneath.
        return new Frame(piece, piece.flat() ? piece.back() ? 90 : -90 : 0);
    }
    private static Frame moved(Frame target, Vec3 position, float yaw, float pitch, int tile, boolean back) {
        var piece = target.piece();
        return new Frame(new TableScene.Piece(tile, piece.seat(), piece.area(), piece.index(), position, yaw, piece.flat(), back), pitch);
    }
    private static double ease(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
    private static double angle(double degrees) { return degrees - Math.floor((degrees + 180) / 360) * 360; }
    private static Key key(TableScene.Piece piece) {
        if (piece.area() == TableScene.Area.WALL || piece.area() == TableScene.Area.LOOSE)
            return new Key(TableScene.Area.WALL, -1, piece.index());
        if (piece.tile() >= 0) return new Key(null, -1, piece.tile());
        return new Key(piece.area(), piece.seat(), piece.index());
    }

    public List<Frame> sample(long now) {
        if (now >= ending) return settled;
        return settled.stream().map(target -> motions.get(key(target.piece())).at(now)).toList();
    }

    public void accept(TableView next, long now) {
        if (next == null) return;
        if (view != null && view.tableId().equals(next.tableId()) && next.revision() < view.revision()) return;
        boolean sameViewer = view != null && view.tableId().equals(next.tableId())
            && view.viewerSeat() == next.viewerSeat() && view.rules().equals(next.rules())
            && view.handVisibility() == next.handVisibility()
            && (view.handVisibility() != top.skyeyefast.mchjong.engine.HandVisibility.RIICHI || view.viewerSeat() < 0
                || view.seats().get(view.viewerSeat()).riichi() == next.seats().get(next.viewerSeat()).riichi());
        if (sameViewer && next.revision() <= view.revision()) return;
        List<Frame> before = sample(now);
        List<Frame> targets = TableScene.build(next).stream().map(TableAnimation::frame).toList();
        boolean newHand = sameViewer && next.handNumber() != view.handNumber()
            && next.phase() == Game.Phase.TURN && next.seats().stream().allMatch(seat -> seat.river().isEmpty());
        if (!sameViewer || next.phase() == Game.Phase.LOBBY || next.handNumber() != view.handNumber() && !newHand) {
            // Rejoining or changing viewing permission must not replay or expose the old private hand.
            settled = targets;
            motions = Map.of();
            ending = openingUntil = now;
            cues = List.of();
            java.util.Arrays.fill(riichiStarted, now - 1000);
        } else if (newHand) {
            deal(next, targets, now);
        } else {
            transition(next, before, targets, now);
            announce(next, now);
        }
        view = next;
    }

    private void deal(TableView next, List<Frame> targets, long now) {
        var updates = new HashMap<Key, Motion>();
        int players = next.rules().players();
        for (Frame target : targets) {
            var piece = target.piece();
            Motion motion;
            if (piece.area() == TableScene.Area.HAND) {
                int offset = Math.floorMod(piece.seat() - next.dealer(), players);
                int index = piece.index();
                int slot = index < 12 ? index / 4 * players * 4 + offset * 4 + index % 4
                    : index == 12 ? 12 * players + offset : 13 * players;
                int packet = index < 12 ? index / 4 * players + offset : index == 12 ? 3 * players + offset : 4 * players;
                Frame source = frame(TableScene.wallPiece(next, slot, true));
                motion = new Motion(source, target, now + 480 + packet * 110L + (index < 12 ? index % 4 * 12 : 0), 300, 0.16, now);
            } else {
                Frame source = moved(target, piece.position().add(0, -0.32, 0), piece.yaw(), target.pitch(), piece.tile(), piece.back());
                motion = new Motion(source, target, now + piece.index() % 18 * 8L, 360, 0, -1);
            }
            updates.put(key(piece), motion);
        }
        settled = targets;
        motions = updates;
        ending = openingUntil = now + DEAL_MILLIS;
        cues = List.of();
        java.util.Arrays.fill(riichiStarted, now - 1000);
    }

    private void transition(TableView next, List<Frame> before, List<Frame> targets, long now) {
        Map<Key, Frame> sources = new HashMap<>();
        Map<Vec3, Frame> wallSlots = new HashMap<>();
        // The key must describe the authoritative tile, not the hidden face mid-flip.
        for (int i = 0; i < settled.size(); i++) {
            sources.put(key(settled.get(i).piece()), before.get(i));
            if (view.wallBreak() != next.wallBreak() && settled.get(i).piece().area() == TableScene.Area.WALL)
                wallSlots.put(settled.get(i).piece().position(), before.get(i));
        }
        Set<Key> targetKeys = new HashSet<>();
        targets.forEach(target -> targetKeys.add(key(target.piece())));
        List<Frame> drawn = new ArrayList<>();
        List<Frame> concealed = new ArrayList<>();
        for (var entry : sources.entrySet()) if (!targetKeys.contains(entry.getKey())) {
            Frame source = entry.getValue();
            if (source.piece().area() == TableScene.Area.WALL) drawn.add(source);
            else if (source.piece().area() == TableScene.Area.HAND && source.piece().tile() < 0) concealed.add(source);
        }
        drawn.sort(java.util.Comparator.comparingInt(source -> source.piece().index()));
        var updates = new HashMap<Key, Motion>();
        long finish = now;
        int handled = 0;
        for (Frame target : targets) {
            Key key = key(target.piece());
            Motion prior = motions.get(key);
            if (prior != null && prior.to().equals(target) && prior.start() + prior.duration() > now) {
                updates.put(key, prior);
                finish = Math.max(finish, prior.start() + prior.duration());
                continue;
            }
            Frame source = sources.get(key);
            if (target.piece().area() == TableScene.Area.WALL && !wallSlots.isEmpty()) {
                Frame physical = wallSlots.get(target.piece().position());
                if (physical != null) source = moved(target, physical.piece().position(), physical.piece().yaw(), physical.pitch(),
                    target.piece().tile(), target.piece().back());
            }
            Discard discard = null;
            if (target.piece().area() == TableScene.Area.RIVER) {
                int seat = target.piece().seat(), index = target.piece().index();
                if (index >= view.seats().get(seat).river().size()) {
                    discard = next.seats().get(seat).river().get(index);
                    if (source == null) {
                        // Hidden hands expose only the distinction, never a guessed physical identity.
                        int count = view.seats().get(seat).hand().size();
                        int slot = discard.tsumogiri() ? count - 1 : Math.max(0, (count - 1) / 2);
                        source = sources.get(new Key(TableScene.Area.HAND, seat, slot));
                    }
                }
            }
            if (source == null && target.piece().area() == TableScene.Area.HAND && view.handling() != null
                && view.handling().sourceSlot() >= 0 && target.piece().seat() == view.turn()) {
                // The dead-wall slot can be refilled in the same snapshot; it need not disappear to be the source.
                if (handled < view.handling().packetSize()) {
                    source = sources.get(new Key(TableScene.Area.WALL, -1, view.handling().sourceSlot() + handled++));
                    if (source != null) drawn.remove(source);
                }
            }
            if (source == null && target.piece().area() == TableScene.Area.HAND && !drawn.isEmpty()) source = drawn.removeFirst();
            if (source == null && target.piece().area() != TableScene.Area.WALL) {
                int owner = target.piece().seat();
                for (int i = concealed.size() - 1; i >= 0; i--) if (concealed.get(i).piece().seat() == owner) {
                    source = concealed.remove(i);
                    break;
                }
            }
            if (source == null) source = target;
            boolean moving = source.piece().position().distanceToSqr(target.piece().position()) > 0.0025;
            long duration = source.equals(target) ? 0 : discard != null ? discard.tsumogiri() ? 300 : 480
                : target.piece().area() == TableScene.Area.MELD ? 440 : 300;
            double arc = discard != null ? discard.tsumogiri() ? 0.045 : 0.16 : 0.10;
            Motion motion = new Motion(source, target, now, duration, moving ? arc : 0, -1);
            updates.put(key, motion);
            finish = Math.max(finish, now + duration);
        }
        settled = targets;
        motions = updates;
        ending = Math.max(finish, openingUntil);
        if (next.phase() == Game.Phase.HAND_END || next.phase() == Game.Phase.MATCH_END) openingUntil = now;
    }

    private void announce(TableView next, long now) {
        var announcements = new ArrayList<>(cues(now));
        for (int seat = 0; seat < next.seats().size(); seat++) {
            var old = view.seats().get(seat);
            var player = next.seats().get(seat);
            if (player.riichi() && !old.riichi()) riichiStarted[seat] = now;
            if (player.river().size() > old.river().size() && player.river().getLast().riichi())
                announcements.add(new Cue(seat, "action.mchjong.riichi", now));
            for (int i = 0; i < player.melds().size(); i++) {
                var meld = player.melds().get(i);
                if (i >= old.melds().size() || meld.type() != old.melds().get(i).type())
                    announcements.add(new Cue(seat, "action.mchjong." + meld.type().name().toLowerCase(java.util.Locale.ROOT), now));
            }
            if (player.norths().size() > old.norths().size()) announcements.add(new Cue(seat, "action.mchjong.nuki", now));
        }
        cues = List.copyOf(announcements);
    }
}
