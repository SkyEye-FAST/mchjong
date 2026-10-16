package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.McrView;
import top.skyeyefast.mchjong.engine.McrWallLayout;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Physical gestures and targets derived exclusively from recipient-safe snapshots. */
public final class TableHandling {
    private TableHandling() {}

    static void renderTarget(net.minecraft.client.gui.GuiGraphicsExtractor graphics, SeatedTableProjection.Point target,
                             int width, int mouseX, int mouseY) {
        if (target == null) return;
        int halfWidth = Math.clamp((int) Math.round(target.scale() * .45), 36, Math.max(36, Math.min(120, width / 4)));
        int halfHeight = Math.clamp((int) Math.round(target.scale() * .12), 12, 28);
        graphics.fill((int) target.x() - halfWidth, (int) target.y() - halfHeight,
            (int) target.x() + halfWidth, (int) target.y() + halfHeight, 0x443c8c68);
        graphics.outline((int) target.x() - halfWidth, (int) target.y() - halfHeight,
            halfWidth * 2, halfHeight * 2, MahjongUi.POSITIVE);
        graphics.horizontalLine(Math.min(mouseX, (int) target.x()), Math.max(mouseX, (int) target.x()), mouseY, MahjongUi.POSITIVE);
        graphics.verticalLine((int) target.x(), Math.min(mouseY, (int) target.y()), Math.max(mouseY, (int) target.y()), MahjongUi.POSITIVE);
    }

    record Context(String phase, int viewerSeat, List<String> actions, top.skyeyefast.mchjong.engine.ManualHandlingView handling,
                   List<Integer> sources, double wallZ, double handZ) {
        int action() {
            if (handling == null || viewerSeat < 0) return -1;
            for (int i = 0; i < actions.size(); i++) if (physical(actions.get(i))) return i;
            return -1;
        }
        String type() { int index = action(); return index < 0 ? "" : actions.get(index); }
        boolean source(String area, int seat, int index, int tile) {
            return switch (type()) {
                case "SHUFFLE" -> area.equals("LOOSE");
                case "BUILD_WALL" -> area.equals("LOOSE") && seat == viewerSeat;
                case "TAKE_PACKET", "DRAW" -> area.equals("WALL") && sources.contains(index) && tile < 0;
                case "NEXT" -> seat == viewerSeat && !area.equals("WALL");
                default -> false;
            };
        }
        Vec3 destination() { return TableGeometry.orient(0, TableGeometry.FELT_Y, destinationZ(), viewerSeat); }
        double destinationZ() { return type().equals("BUILD_WALL") ? wallZ : type().equals("TAKE_PACKET") || type().equals("DRAW") ? handZ : 0; }
        boolean completes(Vec3 start, Vec3 end) {
            if (action() < 0 || start == null || end == null || !Double.isFinite(start.x) || !Double.isFinite(start.z)
                || !Double.isFinite(end.x) || !Double.isFinite(end.z) || start.distanceToSqr(end) < .0144) return false;
            if (type().equals("SHUFFLE")) return Math.abs(end.x) < .8 && Math.abs(end.z) < .8 && start.distanceToSqr(end) >= .09;
            if (type().equals("NEXT")) return Math.abs(end.x) < .65 && Math.abs(end.z) < .65;
            Vec3 local = TableGeometry.orient(end.x, end.y, end.z, (4 - viewerSeat) % 4);
            return Math.abs(local.x) <= 1.05 && Math.abs(local.z - destinationZ()) <= .30;
        }
        String help() {
            return switch (phase) {
                case "SHUFFLE" -> "handling.mchjong.shuffle";
                case "BUILD_WALL" -> "handling.mchjong.wall";
                case "DEAL", "DRAW", "INITIAL_FLOWERS", "REPLACE_FLOWER" -> "handling.mchjong.take";
                case "HAND_END", "MATCH_END" -> "handling.mchjong.collect";
                default -> "handling.mchjong.wait";
            };
        }
    }

    private static boolean physical(String type) {
        return switch (type) { case "SHUFFLE", "BUILD_WALL", "TAKE_PACKET", "DRAW", "NEXT" -> true; default -> false; };
    }

    static Context context(RiichiView view) {
        if (view == null) return null;
        var handling = view.handling();
        var sources = handling == null ? List.<Integer>of() : java.util.stream.IntStream.range(handling.sourceSlot(), handling.sourceSlot() + handling.packetSize()).boxed().toList();
        return new Context(view.phase().name(), view.exitVote() != null ? -1 : view.viewerSeat(),
            view.actions().stream().map(action -> action.type().name()).toList(), handling, sources, RiichiTableScene.WALL_Z, RiichiTableScene.HAND_Z);
    }

    static Context context(McrView view) {
        if (view == null) return null;
        var handling = view.handling();
        var sources = new java.util.ArrayList<Integer>();
        if (handling != null && handling.packetSize() > 0) {
            int source = handling.sourceSlot();
            if (view.phase() == top.skyeyefast.mchjong.engine.McrGame.Phase.DEAL) {
                var plan = McrWallLayout.initialDeal(view.opening());
                int offset = java.util.stream.IntStream.range(0, plan.size()).filter(i -> plan.get(i).slot() == source).findFirst().orElseThrow();
                for (int i = offset; i < plan.size() && plan.get(i).seat() == view.turn(); i++) sources.add(plan.get(i).slot());
            } else sources.add(source);
        }
        return new Context(view.phase().name(), view.viewerSeat(), view.actions().stream().map(action -> action.type().name()).toList(),
            handling, sources, McrTableScene.WALL_Z, McrTableScene.HAND_Z);
    }

    static Context context(top.skyeyefast.mchjong.engine.TaiwanView view) {
        if (view == null) return null;
        var handling = view.handling();
        int size = view.wall().size();
        var sources = new java.util.ArrayList<Integer>();
        // Front packets run on through the dealer's opening, so a packet may wrap past the last slot.
        if (handling != null) for (int i = 0; i < handling.packetSize(); i++) {
            int slot = (handling.sourceSlot() + i) % size;
            sources.add(slot);
            // Replacements take a lower tile first; its stack is grabbed through the upper tile that covers it.
            if (top.skyeyefast.mchjong.engine.TaiwanWallLayout.layer(slot) == top.skyeyefast.mchjong.engine.TaiwanWallLayout.Layer.LOWER
                && view.wall().get(slot - 1) != top.skyeyefast.mchjong.engine.Tile.ABSENT && !sources.contains(slot - 1)) sources.add(slot - 1);
        }
        return new Context(view.phase().name(), view.recipient(), view.actions().stream().map(action -> action.type().name()).toList(),
            handling, sources, TaiwanTableScene.wallZ(size), TaiwanTableScene.HAND_Z);
    }

    static Context context(top.skyeyefast.mchjong.engine.SichuanView view) {
        if (view == null) return null;
        var handling = view.handling();
        var sources = new java.util.ArrayList<Integer>();
        var wall = view.wall();
        if (handling != null && handling.packetSize() > 0 && wall.die1() > 0) {
            var traversal = top.skyeyefast.mchjong.engine.SichuanWallLayout.traversal(wall.dealer(), wall.die1(), wall.die2(), wall.eastWestLongWall());
            // The dealer's jump takes the first and third tiles beyond the last full packet.
            if (handling.packetSize() == 2) { sources.add(traversal.get(48)); sources.add(traversal.get(52)); }
            else for (int index = 0; index < traversal.size() && sources.size() < handling.packetSize(); index++)
                if (wall.slots().get(traversal.get(index)) != top.skyeyefast.mchjong.engine.Tile.ABSENT) sources.add(traversal.get(index));
        }
        return new Context(view.phase().name(), view.viewerSeat(), view.actions().stream().map(action -> action.type().name()).toList(),
            handling, sources, SichuanTableScene.WALL_Z, SichuanTableScene.HAND_Z);
    }

    public static boolean physical(RiichiView view, RiichiAction action) { return view.handling() != null && physical(action.type().name()); }
    public static int action(RiichiView view) { return view == null ? -1 : context(view).action(); }
    public static boolean source(RiichiView view, RiichiTableScene.Piece piece) {
        return piece != null && view != null && context(view).source(piece.area().name(), piece.seat(), piece.index(), piece.tile());
    }
    public static RiichiTableScene.Piece source(RiichiView view, List<RiichiTableScene.Piece> scene) {
        return scene.stream().filter(piece -> source(view, piece)).max(java.util.Comparator.comparingDouble(piece -> piece.position().y)).orElse(null);
    }
    public static Vec3 grip(RiichiTableScene.Piece piece, Vec3 eye) {
        double height = piece.flat() ? TileDimensions.SMALL.depth() : TileDimensions.SMALL.height();
        double depth = piece.flat() ? TileDimensions.SMALL.height() : TileDimensions.SMALL.depth();
        double yaw = Math.toRadians(piece.yaw());
        Vec3 normal = new Vec3(Math.sin(yaw), 0, Math.cos(yaw));
        double edge = Math.copySign(depth / 2 - .003, eye.subtract(piece.position()).dot(normal));
        return piece.position().add(normal.scale(edge)).add(0, height / 2 - .001, 0);
    }
    public static Vec3 destination(RiichiView view) { return context(view).destination(); }
    public static boolean completes(RiichiView view, Vec3 start, Vec3 end) { return context(view).completes(start, end); }
    public static String help(RiichiView view) { return context(view).help(); }

    /** A held group keeps its source snapshot until the server changes the decision. */
    static final class Drag {
        private Context context;
        private Vec3 start, pointer;
        boolean active() { return context != null; }
        void begin(Context context, Vec3 point) { this.context = context; start = pointer = point; }
        void move(Vec3 point) { pointer = point; }
        void clear() { context = null; start = pointer = null; }
        int release(Vec3 end) {
            int result = active() && context.completes(start, end) ? context.action() : -1;
            clear();
            return result;
        }
        Vec3 offset(String area, int seat, int index, int tile, Vec3 position) {
            if (!active() || start == null || pointer == null) return Vec3.ZERO;
            boolean held = context.type().equals("SHUFFLE") ? area.equals("LOOSE") && position.distanceToSqr(start) < .2
                : context.source(area, seat, index, tile);
            return held ? pointer.subtract(start).add(0, .05, 0) : Vec3.ZERO;
        }
    }
}
