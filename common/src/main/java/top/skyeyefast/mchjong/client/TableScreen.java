package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.mixin.GameRendererAccessor;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** A transparent, non-pausing interaction layer over the actual world, not a second table renderer. */
public final class TableScreen extends Screen {
    private static final String[] WINDS = {"east", "south", "west", "north"};
    private final BlockPos pos;
    private final List<CalloutButton> callouts = new ArrayList<>();
    private List<TableScene.Piece> scene = List.of();
    private int selectedTile = Tile.ABSENT;
    private int hoveredTile = Tile.ABSENT;
    private int scroll;
    private long lastRevision = -1;
    private boolean dragging;
    private float framePartial;

    public TableScreen(BlockPos pos) { super(Component.translatable("ui.mchjong.title")); this.pos = pos.immutable(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private TableView view() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientView() : null;
    }

    public static Component roundName(TableView view) {
        Component wind = Component.translatable("wind.mchjong." + WINDS[Math.min(3, view.round() / view.rules().players())]);
        return Component.translatable("ui.mchjong.round", wind, view.round() % view.rules().players() + 1, view.honba());
    }

    public boolean selected(BlockPos table, TableScene.Piece piece) {
        TableView view = view();
        return pos.equals(table) && view != null && piece.area() == TableScene.Area.HAND && piece.seat() == view.viewerSeat()
            && piece.tile() >= 0 && (piece.tile() == selectedTile || piece.tile() == hoveredTile);
    }

    @Override protected void init() { lastRevision = -1; rebuild(); }

    @Override public void tick() {
        if (minecraft.player == null || minecraft.level == null || minecraft.player.distanceToSqr(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5) > 36
            || !(minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity)) onClose();
    }

    private boolean tileChoice(Action action) {
        return switch (action.type()) {
            case DISCARD, RIICHI, CLOSED_KAN, ADDED_KAN, NUKI -> true;
            default -> false;
        };
    }

    private void rebuild() {
        clearWidgets();
        callouts.clear();
        TableView view = view();
        if (view == null) return;
        lastRevision = view.revision();
        scene = TableScene.build(view);
        if (view.viewerSeat() < 0 || !view.seats().get(view.viewerSeat()).hand().contains(selectedTile)) selectedTile = Tile.ABSENT;
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i < view.actions().size(); i++) {
            Action action = view.actions().get(i);
            if (!tileChoice(action) || action.tiles().contains(selectedTile)) choices.add(i);
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, choices.size() - 1));
        int boxWidth = Math.min(220, Math.max(140, width / 3));
        int x = width - boxWidth - 12;
        int y = Math.max(52, height / 5);
        for (int n = scroll; n < choices.size(); n++) {
            int index = choices.get(n);
            Action action = view.actions().get(index);
            Component label = action.type() == Action.Type.CHANGE_RULE
                ? Component.translatable(action.translationKey(), Component.translatable(RuleSet.values()[action.tiles().getFirst()].translationKey()))
                : Component.translatable(action.translationKey());
            int iconWidth = showsConsumed(action) ? action.tiles().size() * 12 + 4 : 0;
            int buttonHeight = Math.max(23, font.wordWrapHeight(label, boxWidth - iconWidth - 16) + 10);
            if (y + buttonHeight > height - 22) break;
            final TableView snapshot = view;
            CalloutButton button = new CalloutButton(x, y, boxWidth, buttonHeight, label, action, index,
                () -> send(snapshot, index));
            addRenderableWidget(button);
            callouts.add(button);
            y += buttonHeight + 6;
        }
    }

    private void send(TableView snapshot, int index) {
        if (minecraft.getConnection() == null) return;
        minecraft.getConnection().send(new ServerboundCustomPayloadPacket(new TableActionPayload(pos, snapshot.tableId(), snapshot.decision(), index)));
        callouts.forEach(button -> button.active = false);
    }

    private static boolean showsConsumed(Action action) {
        return action.type() == Action.Type.CHI || action.type() == Action.Type.PON || action.type() == Action.Type.OPEN_KAN;
    }

    private Vec3 anchor(TableView view, Action action) {
        if (view.focus() != null) {
            TableScene.Area area = view.focus().declaration() ? TableScene.Area.HAND : TableScene.Area.RIVER;
            for (TableScene.Piece piece : scene)
                if (piece.area() == area && piece.seat() == view.focus().seat() && piece.index() == view.focus().index()) return piece.position();
        }
        if (tileChoice(action) || action.type() == Action.Type.TSUMO) {
            for (TableScene.Piece piece : scene)
                if (piece.area() == TableScene.Area.HAND && piece.seat() == view.viewerSeat()
                    && (action.tiles().contains(piece.tile()) || action.type() == Action.Type.TSUMO
                        && piece.tile() == view.seats().get(view.viewerSeat()).drawn())) return piece.position();
        }
        if (action.type() == Action.Type.ABORT_NINE && view.viewerSeat() >= 0)
            return TableGeometry.orient(0, TableGeometry.FELT_Y + 0.08, 1.24, view.viewerSeat());
        return new Vec3(0, TableGeometry.FELT_Y + 0.045, 0);
    }

    private record Projected(double x, double y, double scale) {}

    private Projected project(Vec3 relative) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 delta = TableGeometry.world(pos, relative).subtract(camera.getPosition());
        double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
        Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(forward);
        double depth = delta.dot(forward);
        if (depth <= 0.05) return null;
        double fov = ((GameRendererAccessor) minecraft.gameRenderer).mchjong$getFov(camera, framePartial, true);
        double scale = height / (2 * Math.tan(Math.toRadians(fov) / 2)) / depth;
        return new Projected(width / 2.0 + delta.dot(right) * scale, height / 2.0 - delta.dot(up) * scale, scale);
    }

    private TableScene.Piece pick(double mouseX, double mouseY) {
        TableView view = view();
        if (view == null || view.viewerSeat() < 0) return null;
        TableScene.Piece best = null;
        double closest = Double.MAX_VALUE;
        for (TableScene.Piece piece : scene) {
            if (piece.area() != TableScene.Area.HAND || piece.seat() != view.viewerSeat()) continue;
            Projected point = project(piece.position());
            if (point == null) continue;
            double dx = Math.abs(mouseX - point.x), dy = Math.abs(mouseY - point.y);
            if (dx <= Math.max(5, point.scale * 0.06) && dy <= Math.max(9, point.scale * 0.11) && dx*dx + dy*dy < closest) {
                closest = dx*dx + dy*dy;
                best = piece;
            }
        }
        return best;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        framePartial = partialTick;
        TableView view = view();
        if (view == null) return;
        if (view.revision() != lastRevision) rebuild();
        TableScene.Piece hovered = pick(mouseX, mouseY);
        hoveredTile = hovered == null ? Tile.ABSENT : hovered.tile();
        Component heading = Component.translatable(view.rules().translationKey());
        graphics.fill(8, 8, Math.min(width - 8, Math.max(190, font.width(heading) + 24)), 47, 0xd9182a2d);
        graphics.fill(8, 8, 11, 47, 0xffc4a469);
        graphics.drawString(font, heading, 17, 15, 0xfff0dec1, false);
        graphics.drawString(font, roundName(view), 17, 30, 0xffadd8c4, false);
        for (CalloutButton button : callouts) {
            Projected point = project(anchor(view, button.action));
            if (point != null) elbow(graphics, point, button, button.isHoveredOrFocused() ? 0xffffdc89 : 0xff8fbca9);
            if (showsConsumed(button.action) && button.isHoveredOrFocused()) {
                for (TableScene.Piece piece : scene) if (piece.area() == TableScene.Area.HAND && piece.seat() == view.viewerSeat()
                    && button.action.tiles().contains(piece.tile())) {
                    Projected own = project(piece.position());
                    if (own != null) elbow(graphics, own, button, 0xffdfc98e);
                }
            }
        }
        if (selectedTile >= 0) for (TableScene.Piece piece : scene) if (piece.area() == TableScene.Area.HAND
            && piece.seat() == view.viewerSeat() && piece.tile() == selectedTile) {
            Projected point = project(piece.position());
            if (point != null) {
                int radius = Math.max(5, (int)(point.scale * 0.055));
                graphics.renderOutline((int)point.x-radius, (int)point.y-radius, radius*2, radius*2, 0xfff6d483);
            }
        }
        int lineY = 57;
        if (view.phase() == Game.Phase.HAND_END || view.phase() == Game.Phase.MATCH_END) {
            Component result = Component.translatable("result.mchjong." + view.result());
            graphics.drawString(font, result, 12, lineY, 0xffffd487, true); lineY += 15;
            for (TableView.Win win : view.wins()) {
                Component name = playerName(view, win.seat());
                Component score = win.score().yakuman() > 0 ? Component.translatable("ui.mchjong.yakuman", win.score().yakuman())
                    : Component.translatable("ui.mchjong.han_fu", win.score().han(), win.score().fu());
                graphics.drawString(font, name.copy().append(" · ").append(score), 12, lineY, 0xfff6e5c8, true); lineY += 12;
                for (String yaku : win.score().yaku()) {
                    graphics.drawString(font, Component.translatable("yaku.mchjong." + yaku.toLowerCase(Locale.ROOT)), 20, lineY, 0xffc7dcca, true);
                    lineY += 11;
                }
                if (win.score().dora() > 0) {
                    graphics.drawString(font, Component.translatable("ui.mchjong.dora", win.score().dora()), 20, lineY, 0xffd4c39c, true); lineY += 11;
                }
            }
            for (int i = 0; i < view.seats().size(); i++) {
                String change = i < view.deltas().size() ? String.format(Locale.ROOT, "%+d", view.deltas().get(i)) : "";
                Component line = playerName(view, i).copy().append("  " + view.seats().get(i).points() + "  " + change);
                if (i < view.finalScores().size()) line = line.copy().append("  ").append(Component.translatable("ui.mchjong.final_score", String.format(Locale.ROOT, "%+.1f", view.finalScores().get(i))));
                graphics.drawString(font, line, 12, lineY, 0xffd1dece, true); lineY += 12;
            }
        } else if (view.phase() == Game.Phase.LOBBY) {
            for (int i = 0; i < view.seats().size(); i++) {
                Component status = Component.translatable(view.seats().get(i).ready() ? "ui.mchjong.ready" : "ui.mchjong.not_ready");
                graphics.drawString(font, playerName(view, i).copy().append(" · ").append(status), 12, lineY, 0xffd0decb, true);
                lineY += 14;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        Component help = Component.translatable(selectedTile < 0 && view.viewerSeat() >= 0 ? "ui.mchjong.help" : "ui.mchjong.pan");
        graphics.drawString(font, help, 10, height - 13, 0xffe0deca, true);
    }

    public static Component playerName(TableView view, int seat) {
        TableView.Seat player = view.seats().get(seat);
        if (!player.occupied()) return Component.translatable("ui.mchjong.empty");
        return player.bot() ? Component.translatable("ui.mchjong.bot", seat + 1) : Component.literal(player.name());
    }

    private static void elbow(GuiGraphics graphics, Projected point, CalloutButton button, int color) {
        int x = (int) point.x, y = (int) point.y;
        int endX = button.getX() - 4, endY = button.getY() + button.getHeight() / 2;
        int bendX = endX - 18;
        graphics.hLine(Math.min(x, bendX), Math.max(x, bendX), y, color);
        graphics.vLine(bendX, Math.min(y, endY), Math.max(y, endY), color);
        graphics.hLine(bendX, endX, endY, color);
        graphics.fill(x - 2, y - 2, x + 2, y + 2, color);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) { dragging = true; return true; }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            TableScene.Piece piece = pick(mouseX, mouseY);
            selectedTile = piece == null ? Tile.ABSENT : piece.tile();
            scroll = 0;
            rebuild();
            return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) { dragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && minecraft.player != null) {
            minecraft.player.setYRot(minecraft.player.getYRot() + (float) dx * 0.35f);
            minecraft.player.setXRot(Mth.clamp(minecraft.player.getXRot() + (float) dy * 0.35f, -25, 85));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical));
        rebuild();
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        TableView view = view();
        if ((key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) && view != null && view.viewerSeat() >= 0) {
            List<Integer> hand = view.seats().get(view.viewerSeat()).hand();
            if (!hand.isEmpty()) {
                int index = hand.indexOf(selectedTile);
                selectedTile = hand.get(Math.floorMod(index + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1), hand.size()));
                scroll = 0; rebuild();
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private final class CalloutButton extends Button {
        final Action action;
        final int actionIndex;
        CalloutButton(int x, int y, int w, int h, Component label, Action action, int actionIndex, Runnable click) {
            super(x, y, w, h, label, ignored -> click.run(), DEFAULT_NARRATION);
            this.action = action; this.actionIndex = actionIndex;
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border = isHoveredOrFocused() ? 0xffe9c780 : active ? 0xff709b90 : 0xff405e5a;
            graphics.fill(getX(), getY(), getX()+width, getY()+height, 0xf01b2f32);
            graphics.renderOutline(getX(), getY(), width, height, border);
            graphics.fill(getX()+2, getY()+2, getX()+4, getY()+height-2, border);
            int icons = showsConsumed(action) ? action.tiles().size() * 12 + 4 : 0;
            int y = getY() + 5;
            for (var line : font.split(getMessage(), width - icons - 16)) {
                graphics.drawString(font, line, getX()+9, y, active ? 0xfff0e7d2 : 0xff9ba99e, false);
                y += 9;
            }
            if (icons > 0) for (int i = 0; i < action.tiles().size(); i++) {
                int face = TileMesh.face(action.tiles().get(i));
                graphics.blit(TileMesh.ATLAS, getX()+width-icons+i*12, getY()+4, 10, 15,
                    face % 8 * TileMesh.TILE_WIDTH, face / 8 * TileMesh.TILE_HEIGHT,
                    TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT, TileMesh.ATLAS_SIZE, TileMesh.ATLAS_SIZE);
            }
        }
    }
}
