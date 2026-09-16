package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
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
    private long lastClickAt;
    private int lastClickedTile = Tile.ABSENT;
    private TableResults results;
    private boolean resultsExpanded = true;
    private Game.Phase lastPhase;

    public TableScreen(BlockPos pos) { super(Component.translatable("ui.mchjong.title")); this.pos = pos.immutable(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    public BlockPos tablePos() { return pos; }

    public static TableScreen active(Screen screen) {
        if (screen instanceof TableScreen table) return table;
        if (screen instanceof TableSettingsScreen settings) return settings.tableScreen();
        return null;
    }

    public void resetView() {
        TableView view = view();
        if (minecraft == null || minecraft.player == null || view == null || view.viewerSeat() < 0) return;
        minecraft.player.setYRot(TableGeometry.yaw(view.viewerSeat()));
        minecraft.player.setXRot(52);
    }

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
        boolean newResult = lastPhase != view.phase() && TableResults.available(view);
        if (newResult) resultsExpanded = true;
        int resultScroll = results == null || newResult ? 0 : results.scrollAmount();
        results = null;
        lastPhase = view.phase();
        lastRevision = view.revision();
        scene = TableScene.build(view);
        if (view.viewerSeat() < 0 || !view.seats().get(view.viewerSeat()).hand().contains(selectedTile)) selectedTile = Tile.ABSENT;
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i < view.actions().size(); i++) {
            Action action = view.actions().get(i);
            if (action.type() == Action.Type.DISCARD) continue;
            if (!tileChoice(action) || action.tiles().contains(selectedTile)) choices.add(i);
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, choices.size() - 1));
        boolean settlement = TableResults.available(view);
        int boxWidth = settlement ? Math.min(160, (width - 36) / 2) : Math.min(220, Math.max(140, width / 3));
        int x = width - boxWidth - 12;
        int y = settlement ? height - 48 : Math.max(52, height / 5);
        for (int n = scroll; n < choices.size(); n++) {
            int index = choices.get(n);
            Action action = view.actions().get(index);
            Component label = action.type() == Action.Type.CHANGE_RULE
                ? Component.translatable(action.translationKey(), Component.translatable(RuleSet.values()[action.tiles().getFirst()].translationKey()))
                : Component.translatable(action.translationKey());
            int iconWidth = TableSettings.get().actionTiles ? actionPreviewWidth(view, action) : 0;
            int buttonHeight = Math.max(23, font.wordWrapHeight(label, boxWidth - iconWidth - 16) + 10);
            if (y + buttonHeight > height - 22) break;
            final TableView snapshot = view;
            CalloutButton button = new CalloutButton(x, y, boxWidth, buttonHeight, label, action, index,
                () -> send(snapshot, index));
            addRenderableWidget(button);
            callouts.add(button);
            y += buttonHeight + 6;
        }
        addRenderableWidget(Button.builder(Component.translatable("settings.mchjong.open"), ignored -> minecraft.setScreen(new TableSettingsScreen(this)))
            .bounds(width - 34, 8, 26, 20).build());
        if (TableResults.available(view) && TableSettings.get().show(TableSettings.Information.RESULTS)) {
            addRenderableWidget(Button.builder(Component.translatable(resultsExpanded ? "ui.mchjong.view_table" : "ui.mchjong.view_results"),
                ignored -> { resultsExpanded = !resultsExpanded; rebuild(); }).bounds(10, height - 48, boxWidth, 20).build());
            if (resultsExpanded) results = addRenderableWidget(new TableResults(font, view, 10, 38, width - 20, height - 96, resultScroll));
        }
        if (TableSettings.get().discardMode == TableSettings.DiscardMode.CONFIRM && selectedTile >= 0) {
            int discard = discardAction(view, selectedTile);
            if (discard >= 0) {
                final TableView snapshot = view;
                final int actionIndex = discard;
                addRenderableWidget(Button.builder(Component.translatable("action.mchjong.discard"), ignored -> send(snapshot, actionIndex))
                    .bounds(width / 2 - 55, height - 42, 110, 20).build());
            }
        }
    }

    private void send(TableView snapshot, int index) {
        if (minecraft.getConnection() == null) return;
        minecraft.getConnection().send(new ServerboundCustomPayloadPacket(new TableActionPayload(pos, snapshot.tableId(), snapshot.decision(), index)));
        callouts.forEach(button -> button.active = false);
    }

    private static boolean showsConsumed(Action action) {
        return ActionPreview.consumesHand(action);
    }

    private int actionPreviewWidth(TableView view, Action action) {
        ActionPreview preview = ActionPreview.of(view, action);
        if (preview.meld() != null) return TileGui.meldWidth(preview.meld(), view.viewerSeat(), 11) + 8;
        return preview.tiles().isEmpty() ? 0 : preview.tiles().size() * 13 + 6;
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
        if (!TableResults.available(view)) renderInformation(graphics, view);
        TableSettings settings = TableSettings.get();
        for (CalloutButton button : callouts) {
            boolean guide = settings.guideLines == TableSettings.GuideLines.ALWAYS
                || settings.guideLines == TableSettings.GuideLines.HOVER && button.isHoveredOrFocused();
            if (guide && ActionPreview.hasGuide(button.action)) {
                Projected point = project(anchor(view, button.action));
                if (point != null) elbow(graphics, point, button, button.isHoveredOrFocused() ? 0xffffdc89 : 0xff8fbca9);
            }
            if (settings.highlightTiles && showsConsumed(button.action) && button.isHoveredOrFocused()) {
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
        if (view.phase() == Game.Phase.LOBBY) {
            for (int i = 0; i < view.seats().size(); i++) {
                Component status = Component.translatable(view.seats().get(i).ready() ? "ui.mchjong.ready" : "ui.mchjong.not_ready");
                graphics.drawString(font, playerName(view, i).copy().append(" · ").append(status), 12, lineY, 0xffd0decb, true);
                lineY += 14;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (settings.show(TableSettings.Information.HELP)) {
            Component help = Component.translatable(TableResults.available(view) ? "ui.mchjong.result_scroll" : "ui.mchjong.help_direct");
            graphics.drawString(font, help, 10, height - 13, 0xffe0deca, true);
        }
    }

    private void renderInformation(GuiGraphics graphics, TableView view) {
        TableSettings settings = TableSettings.get();
        int y = 8;
        List<Component> tableLines = new ArrayList<>();
        if (settings.show(TableSettings.Information.RULES)) tableLines.add(Component.translatable(view.rules().translationKey()));
        if (settings.show(TableSettings.Information.ROUND)) tableLines.add(roundName(view));
        if (settings.show(TableSettings.Information.REMAINING)) tableLines.add(Component.translatable("ui.mchjong.remaining", view.remaining()));
        if (settings.show(TableSettings.Information.DEPOSITS)) tableLines.add(Component.translatable("ui.mchjong.sticks", view.riichiSticks()));
        if (settings.show(TableSettings.Information.TURN) && view.turn() >= 0 && view.turn() < view.seats().size())
            tableLines.add(Component.translatable("ui.mchjong.turn", playerName(view, view.turn())));
        if (settings.show(TableSettings.Information.FOCUS) && view.focus() != null)
            tableLines.add(Component.translatable("ui.mchjong.focus"));
        if (settings.show(TableSettings.Information.DORA)) {
            var indicators = view.wall().stream().filter(tile -> tile >= 0).toList();
            if (!indicators.isEmpty()) tableLines.add(Component.translatable("ui.mchjong.dora_indicators", indicators.size()));
        }
        if (!tableLines.isEmpty()) {
            int panelWidth = Math.min(width / 2, Math.max(185, tableLines.stream().mapToInt(font::width).max().orElse(160) + 18));
            graphics.fill(8, y, 8 + panelWidth, y + 8 + tableLines.size() * 12, 0xd9182a2d);
            graphics.fill(8, y, 11, y + 8 + tableLines.size() * 12, 0xffc4a469);
            for (int i = 0; i < tableLines.size(); i++) graphics.drawString(font, tableLines.get(i), 17, y + 6 + i * 12,
                i == 0 ? 0xfff0dec1 : 0xffadd8c4, false);
            y += 14 + tableLines.size() * 12;
        }

        if (!(settings.show(TableSettings.Information.NAMES) || settings.show(TableSettings.Information.WINDS)
                || settings.show(TableSettings.Information.POINTS) || settings.show(TableSettings.Information.RANKS)
                || settings.show(TableSettings.Information.STATUS) || settings.show(TableSettings.Information.COUNTS)
                || settings.show(TableSettings.Information.MELDS))) return;
        for (int seat = 0; seat < view.seats().size(); seat++) {
            TableView.Seat player = view.seats().get(seat);
            var parts = new ArrayList<Component>();
            if (settings.show(TableSettings.Information.NAMES)) parts.add(playerName(view, seat));
            if (settings.show(TableSettings.Information.WINDS)) {
                int wind = Math.floorMod(seat - view.dealer(), view.rules().players());
                parts.add(Component.translatable("wind.mchjong." + WINDS[Math.min(3, wind)]));
            }
            if (settings.show(TableSettings.Information.POINTS)) parts.add(Component.translatable("ui.mchjong.points", player.points()));
            if (settings.show(TableSettings.Information.RANKS)) {
                long ahead = view.seats().stream().filter(other -> other.points() > player.points()).count();
                parts.add(Component.translatable("ui.mchjong.rank", ahead + 1));
            }
            if (settings.show(TableSettings.Information.STATUS)) {
                if (seat == view.dealer()) parts.add(Component.translatable("ui.mchjong.dealer"));
                if (seat == view.turn()) parts.add(Component.translatable("ui.mchjong.current_turn"));
                if (player.riichi()) parts.add(Component.translatable("ui.mchjong.riichi_status"));
                if (player.exposed()) parts.add(Component.translatable("ui.mchjong.exposed"));
            }
            if (settings.show(TableSettings.Information.COUNTS))
                parts.add(Component.translatable("ui.mchjong.counts", player.hand().size(), player.river().size(), player.norths().size()));
            Component line = Component.empty();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) line = line.copy().append(" · ");
                line = line.copy().append(parts.get(i));
            }
            int panelWidth = Math.min(Math.max(220, font.width(line) + 18), Math.max(220, width / 2));
            int lineHeight = settings.show(TableSettings.Information.MELDS) && !player.melds().isEmpty() ? 35 : 21;
            graphics.fill(8, y, 8 + panelWidth, y + lineHeight, seat == view.viewerSeat() ? 0xdc22383b : 0xc9182a2d);
            graphics.drawString(font, line, 14, y + 6, seat == view.turn() ? 0xffffd487 : 0xffd1e4d9, false);
            if (settings.show(TableSettings.Information.MELDS) && !player.melds().isEmpty()) {
                int meldX = 14;
                for (var meld : player.melds()) {
                    TileGui.meld(graphics, meld, seat, meldX, y + 18, 8);
                    meldX += TileGui.meldWidth(meld, seat, 8) + 5;
                }
            }
            y += lineHeight + 3;
        }
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
        if (results != null && results.isMouseOver(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 1) { dragging = true; return true; }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            TableScene.Piece piece = pick(mouseX, mouseY);
            if (piece != null && discardFromClick(piece.tile())) return true;
            selectedTile = piece == null ? Tile.ABSENT : piece.tile();
            scroll = 0;
            rebuild();
            return true;
        }
        return false;
    }

    private boolean discardFromClick(int tile) {
        TableView view = view();
        if (view == null) return false;
        int action = discardAction(view, tile);
        if (action < 0) return false;
        TableSettings.DiscardMode mode = TableSettings.get().discardMode;
        long now = System.currentTimeMillis();
        if (mode == TableSettings.DiscardMode.SINGLE_CLICK) {
            send(view, action);
            return true;
        }
        if (mode == TableSettings.DiscardMode.DOUBLE_CLICK && lastClickedTile == tile && now - lastClickAt <= 450) {
            lastClickedTile = Tile.ABSENT;
            send(view, action);
            return true;
        }
        lastClickedTile = tile;
        lastClickAt = now;
        selectedTile = tile;
        rebuild();
        return true;
    }

    private static int discardAction(TableView view, int tile) {
        for (int i = 0; i < view.actions().size(); i++) {
            Action candidate = view.actions().get(i);
            if (candidate.type() == Action.Type.DISCARD && candidate.tiles().contains(tile)) return i;
        }
        return -1;
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
        if (results != null && results.mouseScrolled(mouseX, mouseY, horizontal, vertical)) return true;
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
            TableView view = view();
            int icons = view != null && TableSettings.get().actionTiles ? actionPreviewWidth(view, action) : 0;
            int y = getY() + 5;
            for (var line : font.split(getMessage(), width - icons - 16)) {
                graphics.drawString(font, line, getX()+9, y, active ? 0xfff0e7d2 : 0xff9ba99e, false);
                y += 9;
            }
            if (icons > 0 && view != null) {
                ActionPreview preview = ActionPreview.of(view, action);
                int x = getX() + width - icons + 3;
                if (preview.meld() != null) TileGui.meld(graphics, preview.meld(), view.viewerSeat(), x, getY() + 4, 9);
                else for (int i = 0; i < preview.tiles().size(); i++)
                    TileGui.tile(graphics, preview.tiles().get(i), x + i * 13, getY() + 4, 9, false, false, false);
            }
        }
    }
}
