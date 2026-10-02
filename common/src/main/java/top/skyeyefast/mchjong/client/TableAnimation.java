package top.skyeyefast.mchjong.client;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.List;
import java.util.HashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Recipient-safe deal, draw and discard choreography on the shared table canvas. */
final class TableAnimation implements TableDeal {
    private static final Map<MahjongTableBlockEntity, TableAnimation> TABLES = new WeakHashMap<>();
    static TableAnimation of(MahjongTableBlockEntity table) { return TABLES.computeIfAbsent(table, ignored -> new TableAnimation()); }
    private TableBoardState previous;
    private UUID table, incarnation;
    private long revision = -1, dealStarted = -10000;
    private int handNumber;
    private Discard discard;
    private Draw draw;
    record WorldTile(int tile, int seat, String area, int index, Vec3 position, float yaw, float pitch, boolean back) {}
    record Pose(Vec3 position, float yaw, float pitch, int tile, boolean back) {}
    private record Key(String area, int seat, int identity) {}
    private record Motion(Pose from, Pose to, long started, long duration, double arc) {
        Pose sample(long now) {
            if (now >= started + duration) return to;
            double fraction = net.minecraft.util.Mth.clamp((now - started) / (double) duration, 0, 1), progress = ImmersiveMotion.smooth(fraction);
            double angle = to.yaw() - from.yaw(); angle -= Math.floor((angle + 180) / 360) * 360;
            return new Pose(from.position().lerp(to.position(), progress).add(0, Math.sin(Math.PI * fraction) * arc, 0),
                from.yaw() + (float) (angle * progress), from.pitch() + (float) ((to.pitch() - from.pitch()) * progress),
                fraction < .45 && from.tile() < 0 ? Tile.HIDDEN : to.tile(), fraction < .5 ? from.back() : to.back());
        }
    }
    private Map<Key, WorldTile> world = Map.of();
    private Map<Key, Motion> worldMotions = Map.of();
    private static Key key(WorldTile tile) {
        return tile.tile() >= 0 && !tile.area().equals("WALL") ? new Key("", -1, tile.tile()) : new Key(tile.area(), tile.seat(), tile.index());
    }
    private static Pose pose(WorldTile tile) { return new Pose(tile.position(), tile.yaw(), tile.pitch(), tile.tile(), tile.back()); }
    static List<WorldTile> world(top.skyeyefast.mchjong.engine.McrView view) {
        return McrTableScene.build(view).stream().map(TableAnimation::world).toList();
    }
    static List<WorldTile> world(top.skyeyefast.mchjong.engine.SichuanView view) {
        return SichuanTableScene.build(view).stream().map(TableAnimation::world).toList();
    }
    private static WorldTile world(McrTableScene.Piece p) { return new WorldTile(p.tile(), p.seat(), p.area().name(), p.index(), p.position(), p.yaw(), p.flat() ? p.back() ? 90 : -90 : 0, p.back()); }
    private static WorldTile world(SichuanTableScene.Piece p) { return new WorldTile(p.tile(), p.seat(), p.area().name(), p.index(), p.position(), p.yaw(), p.flat() ? p.back() ? 90 : -90 : 0, p.back()); }
    Pose worldPose(McrTableScene.Piece piece, long now) { return worldPose(world(piece), now); }
    Pose worldPose(SichuanTableScene.Piece piece, long now) { return worldPose(world(piece), now); }
    private Pose worldPose(WorldTile tile, long now) {
        var motion = worldMotions.get(key(tile));
        return !TableSettings.get().animations || motion == null || !motion.to().equals(pose(tile)) ? pose(tile) : motion.sample(now);
    }
    private record Discard(int tile, int seat, boolean tsumogiri, long started, TableBoardState before) {}
    private record Draw(int tile, long started) {}

    void accept(TableBoardState next, List<WorldTile> pieces, UUID table, UUID incarnation, long revision, int handNumber, boolean opening, long now) {
        boolean same = table.equals(this.table) && incarnation.equals(this.incarnation)
            && previous != null && next != null && previous.viewerSeat() == next.viewerSeat();
        if (same && revision <= this.revision) return;
        boolean newHand = next != null && (opening || same && this.handNumber != handNumber);
        var targets = new HashMap<Key, WorldTile>(); pieces.forEach(tile -> targets.put(key(tile), tile));
        var motions = new HashMap<Key, Motion>();
        var removedWalls = world.entrySet().stream().filter(entry -> entry.getValue().area().equals("WALL") && !targets.containsKey(entry.getKey()))
            .map(Map.Entry::getValue).sorted(java.util.Comparator.comparingInt(WorldTile::index)).toList();
        for (var tile : pieces) {
            var source = same ? world.get(key(tile)) : null;
            if (!newHand && source != null && source.equals(tile) && worldMotions.containsKey(key(tile))) {
                motions.put(key(tile), worldMotions.get(key(tile))); continue;
            }
            Pose from = source == null ? pose(tile) : worldPose(source, now);
            long start = now, duration = 280; double arc = 0;
            if (newHand && tile.area().equals("HAND")) {
                int offset = Math.floorMod(tile.seat() - next.dealer(), next.players());
                int packet = tile.index() < 12 ? tile.index() / 4 * next.players() + offset : 3 * next.players() + offset;
                start += 480 + packet * 110L; duration = 300; arc = .16;
                from = new Pose(top.skyeyefast.mchjong.world.TableGeometry.orient(.2, top.skyeyefast.mchjong.world.TableGeometry.FELT_Y, .76, tile.seat()), tile.yaw(), 90, Tile.HIDDEN, true);
            } else if (same && source == null && tile.area().equals("HAND") && !removedWalls.isEmpty()) {
                from = pose(removedWalls.get(0)); duration = 340; arc = .12;
            } else if (same && source == null && tile.area().equals("RIVER")) {
                var hands = world.values().stream().filter(old -> old.area().equals("HAND") && old.seat() == tile.seat()).toList();
                if (!hands.isEmpty()) from = pose(hands.get(hands.size() / 2));
                duration = 500; arc = .1;
            }
            if (!same && !newHand) duration = 0;
            motions.put(key(tile), new Motion(from, pose(tile), start, duration, arc));
        }
        world = Map.copyOf(targets); worldMotions = Map.copyOf(motions);
        if (next == null || !same || this.handNumber != handNumber) { discard = null; draw = null; dealStarted = -10000; }
        if (next != null && (opening || same && this.handNumber != handNumber)) dealStarted = now;
        if (same && this.handNumber == handNumber) {
            int viewer = next.viewerSeat();
            if (viewer >= 0) {
                var before = previous.seats().get(viewer); var after = next.seats().get(viewer);
                if (after.hand().size() == before.hand().size() + 1 && after.drawn() >= 0) draw = new Draw(after.drawn(), now);
            }
            for (int seat = 0; seat < next.players(); seat++) {
                var before = previous.seats().get(seat).river(); var after = next.seats().get(seat).river();
                if (after.size() == before.size() + 1 && !after.get(after.size() - 1).called()) {
                    var tile = after.get(after.size() - 1); discard = new Discard(tile.tile(), seat, tile.tsumogiri(), now, previous); break;
                }
            }
        }
        this.table = table; this.incarnation = incarnation; this.revision = revision;
        this.handNumber = handNumber; previous = next;
    }
    @Override public boolean dealing(long now) { return TableSettings.get().animations && previous != null && now < dealStarted + RiichiAnimation.DEAL_MILLIS; }
    @Override public double dealProgress(int seat, int index, long now) {
        if (!dealing(now)) return 1;
        int players = previous.players(), offset = Math.floorMod(seat - previous.dealer(), players);
        int packet = index < 12 ? index / 4 * players + offset : index == 12 ? 3 * players + offset : 4 * players;
        long start = dealStarted + 480 + packet * 110L + (index < 12 ? index % 4 * 12L : 0);
        return net.minecraft.util.Mth.clamp((now - start) / 300.0, 0, 1);
    }
    private boolean discardActive(long now) { return TableSettings.get().animations && discard != null && now < discard.started() + ImmersiveMotion.duration(discard.tsumogiri()); }
    private boolean drawActive(long now) { return TableSettings.get().animations && draw != null && now < draw.started() + 340; }
    int riverSuppressed(long now) { return discardActive(now) ? discard.tile() : Tile.ABSENT; }
    int handSuppressed(long now) { return drawActive(now) ? draw.tile() : Tile.ABSENT; }
    void render(GuiGraphics g, TableBoard board, TableHand hand, int handHeight, long now,
                top.skyeyefast.mchjong.item.TileFacePreset preset, top.skyeyefast.mchjong.item.TileMaterial material,
                net.minecraft.world.item.DyeColor back, net.minecraft.resources.ResourceLocation backPreset,
                java.util.function.IntUnaryOperator artwork) {
        if (discardActive(now)) {
            var before = discard.before().seats().get(discard.seat());
            TableHand.Point source = null; int width = 30;
            if (discard.seat() == previous.viewerSeat()) {
                var old = new TableHand(before.hand(), before.drawn(), before.melds(), discard.seat(), TableCanvas.WIDTH, handHeight, 58, true, before.variant());
                source = old.point(discard.tile()); width = old.tileWidth();
            }
            double opponent = TableImmersiveTable.discardSourceX(before, discard.seat(), previous.viewerSeat(), previous.players(), discard.tile(), discard.tsumogiri());
            board.discard(g, discard.tile(), source, width, opponent, discard.tsumogiri(), false,
                net.minecraft.util.Mth.clamp((now - discard.started()) / (double) ImmersiveMotion.duration(discard.tsumogiri()), 0, 1));
        }
        if (drawActive(now) && hand != null) {
            var target = hand.point(draw.tile()); if (target == null) return;
            var source = board.drawSource(); double fraction = net.minecraft.util.Mth.clamp((now - draw.started()) / 340.0, 0, 1), progress = ImmersiveMotion.smooth(fraction);
            int width = Math.max(16, (int) Math.round(20 + (hand.tileWidth() - 20) * progress));
            int height = Math.round(width * TileMesh.HEIGHT / TileMesh.WIDTH);
            int x = (int) Math.round(source.x() + (target.x() - source.x()) * progress);
            int y = (int) Math.round(source.y() + (target.y() - source.y()) * progress - Math.sin(Math.PI * fraction) * 22);
            TileGui.tileArtwork(g, draw.tile(), x - width / 2, y - height / 2, width, false, false, false, false, Math.max(2, width / 8), preset, material, back, backPreset, artwork);
        }
    }
}
