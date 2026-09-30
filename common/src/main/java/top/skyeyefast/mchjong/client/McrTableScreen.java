package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.McrAction;
import top.skyeyefast.mchjong.engine.McrSession;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.mixin.GameRendererAccessor;
import top.skyeyefast.mchjong.network.McrActionPayload;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Seated world interaction and the fixed immersive canvas share server-issued MCR decisions. */
public final class McrTableScreen extends Screen {
    private final BlockPos pos;
    private long shownRevision = -1, decision = -1, lastClickAt;
    private int page, selected = Tile.ABSENT, hovered = Tile.ABSENT, lastClicked = Tile.ABSENT;
    private boolean pending, immersive, inspecting, dragging;
    private double dragDistance;
    private float framePartial;
    private final boolean[] lookKeys = new boolean[4];
    private TableHand hand;
    private ImmersiveTable board;

    public McrTableScreen(BlockPos pos) { this(pos, false); }
    public McrTableScreen(BlockPos pos, boolean immersive) {
        super(Component.translatable("mcr.mchjong.title"));
        this.pos = pos.immutable(); this.immersive = immersive;
    }
    public BlockPos tablePos() { return pos; }
    public static McrTableScreen active(Screen screen) {
        if (screen instanceof McrTableScreen table) return table;
        return screen instanceof TableSettingsScreen settings ? settings.mcrTableScreen() : null;
    }
    public static boolean isOpen(Screen screen) {
        return active(screen) != null || screen instanceof McrResultsScreen || screen instanceof McrLobbyScreen;
    }
    public boolean immersive() { return immersive; }
    public boolean inspecting() { return inspecting && cameraEnabled(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float partialTick) {}
    @Override public void removed() { clearCameraInput(); }
    private int uiWidth() { return immersive ? 1280 : width; }
    private int uiHeight() { return immersive ? 800 : height; }
    private int contentScale() { return immersive ? 2 : 1; }
    private double canvasX(double x) {
        var canvas = TableScreen.immersiveCanvas(width, height);
        return immersive ? (x - canvas.x()) / canvas.scale() : x;
    }
    private double canvasY(double y) {
        var canvas = TableScreen.immersiveCanvas(width, height);
        return immersive ? (y - canvas.y()) / canvas.scale() : y;
    }
    private boolean inside(double x, double y) { return x >= 0 && x < uiWidth() && y >= 0 && y < uiHeight(); }
    private MahjongTableBlockEntity table() {
        return minecraft != null && minecraft.level != null
            && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table ? table : null;
    }
    private McrSession.View view() { return table() == null ? null : table().clientMcrView(); }
    public void receivedView() { pending = false; rebuild(); }
    @Override protected void init() { rebuild(); }

    private void rebuild() {
        int focusedTile = getFocused() instanceof HandTarget target ? target.tile : Tile.ABSENT;
        clearWidgets();
        var view = view();
        if (view == null || table().clientTableRoom() == null) return;
        var room = table().clientTableRoom(); var game = view.game();
        if (shownRevision != view.revision()) pending = false;
        shownRevision = view.revision();
        if (decision != game.decision()) {
            decision = game.decision(); selected = hovered = lastClicked = Tile.ABSENT; page = 0;
        }
        hand = null;
        board = immersive ? new ImmersiveTable(game.viewerSeat()) : null;
        if (immersive && game.viewerSeat() >= 0) {
            var player = game.seats().get(game.viewerSeat());
            var tiles = new ArrayList<>(player.hand());
            if (player.drawn() >= 0 && tiles.remove(Integer.valueOf(player.drawn()))) tiles.add(player.drawn());
            hand = new TableHand(tiles, player.drawn(), List.of(), game.viewerSeat(), 1280, 780, 52, true);
        }
        if (room.exitVote() != null) {
            TableExitControls.voteButtons(pos, room, uiWidth(), uiHeight(), contentScale()).forEach(this::addRenderableWidget);
            return;
        }
        int s = contentScale(), w = uiWidth();
        int x = toolbar("ui.mchjong.view_" + (immersive ? "seated" : "immersive"), 8, this::toggleView);
        toolbar("settings.mchjong.title", x, () -> minecraft.setScreen(new TableSettingsScreen(this)));
        if (room.viewerSeat() >= 0) addRenderableWidget(new MahjongButton(w - 60 * s, 6, 52 * s, 20 * s,
            Component.translatable("ui.mchjong.exit"), ignored -> TableExitControls.send(pos, room,
                TableSessionControlPayload.Operation.REQUEST_EXIT, room.decision(), false)).textScale(s));
        var choices = new ArrayList<Integer>();
        if (discardAction(selected) >= 0) choices.add(discardAction(selected));
        for (int i = 0; i < game.actions().size(); i++)
            if (game.actions().get(i).type() != McrAction.Type.DISCARD) choices.add(i);
        int pages = Math.max(1, (choices.size() + 3) / 4);
        page = Math.min(page, pages - 1);
        int cell = immersive ? 224 : Math.min(110, (w - 28) / 3);
        int actionY = immersive ? 562 : uiHeight() - 112;
        for (int slot = 0; slot < 4 && page * 4 + slot < choices.size(); slot++) {
            int index = choices.get(page * 4 + slot);
            var button = new ActionButton(w - 8 - (2 - slot % 2) * (cell + 4), actionY + slot / 2 * 28 * s,
                cell, 26 * s, game.actions().get(index), () -> send(view, index));
            button.active = !pending && !view.paused(); addRenderableWidget(button);
        }
        if (pages > 1) {
            addRenderableWidget(new MahjongButton(w - 68 * s, actionY - 22 * s, 28 * s, 20 * s,
                Component.literal("‹"), ignored -> { page = Math.floorMod(page - 1, pages); rebuild(); }).textScale(s));
            addRenderableWidget(new MahjongButton(w - 36 * s, actionY - 22 * s, 28 * s, 20 * s,
                Component.literal("›"), ignored -> { page = (page + 1) % pages; rebuild(); }).textScale(s));
        }
        if (game.viewerSeat() >= 0) for (int tile : game.seats().get(game.viewerSeat()).hand()) {
            var target = addRenderableWidget(new HandTarget(tile));
            if (tile == focusedTile) setFocused(target);
        }
    }
    private int toolbar(String key, int x, Runnable action) {
        int s = contentScale(); var caption = Component.translatable(key);
        int span = Math.min(uiWidth() / 3, (font.width(caption) + 14) * s);
        addRenderableWidget(new MahjongButton(x, 6, span, 20 * s, caption, ignored -> action.run()).textScale(s));
        return x + span + 4;
    }
    private void send(McrSession.View snapshot, int index) {
        var current = view();
        if (pending || current == null || snapshot == null || current.paused() || minecraft.getConnection() == null
            || !snapshot.tableId().equals(current.tableId()) || !snapshot.incarnation().equals(current.incarnation())
            || snapshot.game().decision() != current.game().decision() || index < 0 || index >= current.game().actions().size()
            || table().clientTableRoom().exitVote() != null) return;
        pending = true;
        minecraft.getConnection().send(PayloadPackets.serverbound(new McrActionPayload(pos, current.tableId(),
            current.incarnation(), current.game().decision(), index)));
        rebuild();
    }
    private int discardAction(int tile) {
        var view = view();
        if (view == null || tile < 0) return -1;
        for (int i = 0; i < view.game().actions().size(); i++) {
            var action = view.game().actions().get(i);
            if (action.type() == McrAction.Type.DISCARD && action.tiles().contains(tile)) return i;
        }
        return -1;
    }
    private void choose(int tile) {
        if (pending) return;
        selected = tile; long now = Util.getMillis(); var mode = TableSettings.get().discardMode;
        if (!hasShiftDown() && (mode == TableSettings.DiscardMode.SINGLE_CLICK
            || mode == TableSettings.DiscardMode.DOUBLE_CLICK && lastClicked == tile && now - lastClickAt <= 450)) {
            int index = discardAction(tile);
            if (index >= 0) { send(view(), index); return; }
        }
        lastClicked = tile; lastClickAt = now; rebuild(); setFocused(null);
    }
    private Component tileLabel(int tile) {
        return table().clientMcrDeck().tile(tile).label(TableSettings.get().tileLabels == TableSettings.TileLabels.MPSZ,
            table().clientMcrDeck().preset());
    }
    public boolean selected(McrTableScene.Piece piece) { return !immersive && ownHand(piece) && piece.tile() == selected; }
    private boolean ownHand(McrTableScene.Piece piece) {
        return view() != null && piece.area() == McrTableScene.Area.HAND && piece.seat() == view().game().viewerSeat();
    }
    public int highlight(McrTableScene.Piece piece) {
        if (immersive || !TableSettings.get().highlightTiles || !ownHand(piece)) return 0;
        int focus = getFocused() instanceof HandTarget target ? target.tile : hovered;
        return piece.tile() == selected ? MahjongUi.ACCENT : piece.tile() == focus ? MahjongUi.POSITIVE : 0;
    }
    private boolean overWidget(double x, double y) {
        return children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .anyMatch(widget -> !(widget instanceof HandTarget) && widget.visible
                && x >= widget.getX() && x < widget.getRight() && y >= widget.getY() && y < widget.getBottom());
    }
    private Vec3 forward() {
        var camera = minecraft.gameRenderer.getMainCamera();
        double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }
    private Vec3 right() {
        double yaw = Math.toRadians(minecraft.gameRenderer.getMainCamera().getYRot());
        return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
    }
    private double focal() {
        var camera = minecraft.gameRenderer.getMainCamera();
        double fov = ((GameRendererAccessor) minecraft.gameRenderer).mchjong$getFov(camera, framePartial, true);
        return height / (2 * Math.tan(Math.toRadians(fov) / 2));
    }
    private int pick(double x, double y) {
        var view = view();
        if (view == null || view.game().viewerSeat() < 0 || table().clientTableRoom().exitVote() != null || overWidget(x, y)) return Tile.ABSENT;
        if (hand != null) return hand.pick(x, y, selected);
        if (immersive) return Tile.ABSENT;
        var ray = forward().add(right().scale((x - width / 2.0) / focal())).add(right().cross(forward()).scale((height / 2.0 - y) / focal()));
        var origin = minecraft.gameRenderer.getMainCamera().getPosition().subtract(TableGeometry.world(pos, Vec3.ZERO));
        double closest = Double.POSITIVE_INFINITY; int tile = Tile.ABSENT;
        for (var piece : McrTableScene.build(view.game())) if (ownHand(piece)) {
            double distance = TilePicking.distanceSquared(piece, origin, ray, selected(piece));
            if (distance < closest) { closest = distance; tile = piece.tile(); }
        }
        return tile;
    }
    private TableHand.Point point(int tile) {
        if (hand != null) return hand.point(tile, selected, hovered);
        var view = view(); if (view == null) return null;
        for (var piece : McrTableScene.build(view.game())) if (ownHand(piece) && piece.tile() == tile) {
            var delta = TableGeometry.world(pos, piece.position().add(0, selected(piece) ? .035 : 0, 0))
                .subtract(minecraft.gameRenderer.getMainCamera().getPosition());
            double depth = delta.dot(forward()); if (depth <= .01) return null;
            return new TableHand.Point((int) (width / 2.0 + delta.dot(right()) * focal() / depth),
                (int) (height / 2.0 - delta.dot(right().cross(forward())) * focal() / depth));
        }
        return null;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        framePartial = partialTick;
        var view = view(); if (view == null || table().clientMcrDeck() == null) return;
        if (view.revision() != shownRevision) rebuild();
        int mx = (int) canvasX(mouseX), my = (int) canvasY(mouseY);
        if (immersive) {
            g.fill(0, 0, width, height, 0xff000000);
            var canvas = TableScreen.immersiveCanvas(width, height);
            g.pose().pushPose(); g.pose().translate(canvas.x(), canvas.y(), 0);
            g.pose().scale((float) canvas.scale(), (float) canvas.scale(), 1);
            g.fill(0, 0, 1280, 800, MahjongUi.INPUT);
            board.renderMcr(g, view.game(), table().clientMcrDeck(), table().clientMcrCloth());
        }
        try {
            hovered = inside(mx, my) ? pick(mx, my) : Tile.ABSENT;
            int s = contentScale();
            g.pose().pushPose(); g.pose().translate(0, 0, 400); g.pose().scale(s, s, 1);
            int w = uiWidth() / s;
            g.drawCenteredString(font, Component.translatable("mcr.mchjong.hand", view.game().handNumber(), view.game().remaining()),
                w / 2, immersive ? 170 : 32, MahjongUi.TEXT);
            if (view.paused()) g.drawCenteredString(font, Component.translatable("mcr.mchjong.paused"), w / 2,
                immersive ? 183 : 43, MahjongUi.NEGATIVE);
            else if (view.game().responded()) g.drawCenteredString(font, Component.translatable("mcr.mchjong.responded"),
                w / 2, immersive ? 183 : 43, MahjongUi.MUTED);
            else if (view.game().phase() == top.skyeyefast.mchjong.engine.McrGame.Phase.INITIAL_FLOWERS
                || view.game().phase() == top.skyeyefast.mchjong.engine.McrGame.Phase.REPLACE_FLOWER)
                g.drawCenteredString(font, Component.translatable("mcr.mchjong.flower_replacement",
                    view.participants().get(view.game().turn()).name()), w / 2, immersive ? 183 : 43, MahjongUi.ACCENT);
            g.pose().popPose();
            renderSeats(g, view, mx, my);
            if (hand != null) {
                var deck = table().clientMcrDeck();
                int focus = getFocused() instanceof HandTarget target ? target.tile : hovered;
                hand.render(g, selected, focus, tile -> tile == selected ? MahjongUi.ACCENT : 0, Tile.ABSENT,
                    deck.preset(), deck.material(), deck.back(), deck.backPreset(), null, null, 0);
            }
            g.pose().pushPose(); g.pose().translate(0, 0, 600);
            TableExitControls.renderVote(g, font, table().clientTableRoom(), uiWidth(), uiHeight(), s);
            super.render(g, mx, my, partialTick);
            if (table().clientTableRoom().exitVote() == null && TableSettings.get().show(TableSettings.Information.HELP)) {
                var help = Component.translatable("mcr.mchjong.help." + TableSettings.get().discardMode.name().toLowerCase(java.util.Locale.ROOT),
                    TableKeys.VIEW.getTranslatedKeyMessage(), TableKeys.PASS.getTranslatedKeyMessage());
                g.pose().pushPose(); g.pose().scale(s, s, 1);
                MahjongUi.text(g, font, help, immersive ? 106 : 8, uiHeight() / s - 11,
                    uiWidth() / s - (immersive ? 118 : 16), MahjongUi.MUTED, false);
                g.pose().popPose();
            }
            if (hovered >= 0 && table().clientTableRoom().exitVote() == null) g.renderTooltip(font, tileLabel(hovered), mx, my);
            g.pose().popPose();
        } finally { if (immersive) g.pose().popPose(); }
    }
    private void renderSeats(GuiGraphics g, McrSession.View view, int mouseX, int mouseY) {
        int viewer = Math.max(0, view.game().viewerSeat());
        for (int seat = 0; seat < 4; seat++) {
            int side = Math.floorMod(seat - viewer, 4), s = contentScale();
            int cardWidth = immersive ? 180 : Math.min(120, width / 3), x, y;
            if (immersive) { var rect = ImmersiveTable.card(side); x = rect.x(); y = rect.y(); }
            else { x = side == 1 || side == 2 ? width - cardWidth - 8 : 8; y = side == 0 || side == 1 ? 82 : 54; }
            var player = view.game().seats().get(seat); var participant = view.participants().get(seat);
            int cardHeight = (immersive ? 34 : 24) * s;
            g.fill(x, y, x + cardWidth, y + cardHeight, MahjongUi.PANEL);
            if (view.game().turn() == seat) g.fill(x, y, x + 2 * s, y + cardHeight, MahjongUi.ACCENT);
            PlayerPortrait.draw(g, participant, x + 4, y + 4, 10 * s);
            g.pose().pushPose(); g.pose().translate(x + 4, y + 4, 0); g.pose().scale(s, s, 1);
            MahjongUi.text(g, font, Component.literal(participant.name()), 14, 0, cardWidth / s - 20, MahjongUi.TEXT, false);
            var wind = Component.translatable("wind.mchjong." + new String[]{"east", "south", "west", "north"}[player.wind() - Tile.EAST]);
            MahjongUi.text(g, font, wind.copy().append("  " + player.points()), 0, 11, cardWidth / s - 8, MahjongUi.TEXT, false);
            var state = Component.translatable(player.winForbidden() ? "mcr.mchjong.win_forbidden" : "mcr.mchjong.flowers", player.flowers().size());
            if (immersive) MahjongUi.text(g, font, state, 0, 22, cardWidth / s - 8,
                player.winForbidden() ? MahjongUi.NEGATIVE : MahjongUi.MUTED, false);
            g.pose().popPose();
            if (!immersive && player.winForbidden()) g.fill(x, y + cardHeight - 2, x + cardWidth, y + cardHeight, MahjongUi.NEGATIVE);
            if (mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight)
                g.renderTooltip(font, Component.literal(participant.name()).append(" · ").append(wind).append(" · " + player.points())
                    .append(" · ").append(state), mouseX, mouseY);
        }
    }
    private void clearCameraInput() { inspecting = dragging = false; java.util.Arrays.fill(lookKeys, false); }
    private boolean cameraEnabled() {
        return !immersive && minecraft != null && minecraft.player != null
            && minecraft.player.getVehicle() instanceof SeatEntity && minecraft.options.getCameraType().isFirstPerson();
    }
    private void syncCamera() { if (minecraft.player.getVehicle() instanceof SeatEntity seat) SeatedCamera.sync(seat); }
    private void toggleView() { immersive = !immersive; clearCameraInput(); rebuild(); }
    private void resetView() {
        if (minecraft.player.getVehicle() instanceof SeatEntity seat) SeatedCamera.state(seat);
        var settings = TableSettings.get(); settings.camera().reset(settings.cameraDistance, settings.cameraHeight);
        clearCameraInput(); syncCamera();
    }
    @Override public void tick() {
        if (cameraEnabled()) {
            TableSettings.get().camera().look((lookKeys[1] ? 1 : 0) - (lookKeys[0] ? 1 : 0),
                (lookKeys[3] ? 1 : 0) - (lookKeys[2] ? 1 : 0)); syncCamera();
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (TableKeys.VIEW.matchesMouse(button)) { toggleView(); return true; }
        if (TableKeys.RESET.matchesMouse(button)) { resetView(); return true; }
        if (TableKeys.INSPECT.matchesMouse(button) && cameraEnabled()) { inspecting = true; return true; }
        if (TableKeys.PASS.matchesMouse(button)) { pass(); return true; }
        x = canvasX(x); y = canvasY(y);
        if (!inside(x, y)) return false;
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 1) {
            if (cameraEnabled()) { dragging = true; dragDistance = 0; }
            else { selected = lastClicked = Tile.ABSENT; rebuild(); }
            return true;
        }
        if (button == 0) { int tile = pick(x, y); if (tile >= 0) { choose(tile); return true; } }
        return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (TableKeys.INSPECT.matchesMouse(button)) { inspecting = false; return true; }
        if (button == 1 && dragging) {
            dragging = false;
            if (dragDistance < 4) { selected = lastClicked = Tile.ABSENT; rebuild(); }
            return true;
        }
        return super.mouseReleased(canvasX(x), canvasY(y), button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 1 && dragging && cameraEnabled()) {
            double before = dragDistance; dragDistance += Math.abs(dx) + Math.abs(dy);
            if (dragDistance > 4) {
                double fraction = before >= 4 ? 1 : (dragDistance - 4) / (dragDistance - before);
                if (hasShiftDown()) TableSettings.get().camera().pan(-dx * fraction * .004, -dy * fraction * .004);
                else TableSettings.get().camera().look(dx * fraction * .35, dy * fraction * .35);
                syncCamera();
            }
            return true;
        }
        double scale = immersive ? TableScreen.immersiveCanvas(width, height).scale() : 1;
        return super.mouseDragged(canvasX(x), canvasY(y), button, dx / scale, dy / scale);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (cameraEnabled() && !overWidget(x, y)) {
            if (hasShiftDown()) TableSettings.get().camera().raise(vertical); else TableSettings.get().camera().scroll(vertical);
            return true;
        }
        return super.mouseScrolled(canvasX(x), canvasY(y), horizontal, vertical);
    }
    private void pass() {
        var view = view();
        if (view != null) for (int i = 0; i < view.game().actions().size(); i++)
            if (view.game().actions().get(i).type() == McrAction.Type.PASS) { send(view, i); return; }
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (TableKeys.VIEW.matches(key, scanCode)) { toggleView(); return true; }
        if (TableKeys.RESET.matches(key, scanCode)) { resetView(); return true; }
        if (TableKeys.INSPECT.matches(key, scanCode) && cameraEnabled()) { inspecting = true; return true; }
        if (TableKeys.PASS.matches(key, scanCode)) { pass(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE && selected >= 0) { selected = lastClicked = Tile.ABSENT; rebuild(); return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && selected >= 0 && getFocused() == null) {
            send(view(), discardAction(selected)); return true;
        }
        int arrow = arrow(key);
        if (arrow >= 0 && cameraEnabled() && getFocused() == null) { lookKeys[arrow] = true; return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (TableKeys.INSPECT.matches(key, scanCode)) { inspecting = false; return true; }
        int arrow = arrow(key); if (arrow >= 0) lookKeys[arrow] = false;
        return super.keyReleased(key, scanCode, modifiers);
    }
    private static int arrow(int key) {
        return switch (key) { case GLFW.GLFW_KEY_LEFT -> 0; case GLFW.GLFW_KEY_RIGHT -> 1;
            case GLFW.GLFW_KEY_UP -> 2; case GLFW.GLFW_KEY_DOWN -> 3; default -> -1; };
    }
    private final class HandTarget extends MahjongButton {
        private final int tile;
        HandTarget(int tile) { super(0, 0, 20, 20, tileLabel(tile), ignored -> choose(tile)); this.tile = tile; setTooltip(null); }
        @Override protected boolean clicked(double x, double y) { return false; }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            var point = point(tile); active = !pending && view() != null && !view().paused();
            if (point == null) return;
            setX(point.x() - 10); setY(point.y() - 10);
            if (isFocused()) g.drawCenteredString(font, getMessage(), point.x(), point.y() - 28, MahjongUi.ACCENT);
        }
    }
    private final class ActionButton extends MahjongButton {
        private final McrAction action;
        ActionButton(int x, int y, int w, int h, McrAction action, Runnable press) {
            super(x, y, w, h, actionLabel(action), ignored -> press.run());
            this.action = action;
            if (action.type() == McrAction.Type.WIN) primary();
            var label = getMessage().copy(); for (int tile : action.tiles()) label.append(" · ").append(tileLabel(tile));
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderSurface(g);
            int s = contentScale(), icons = TableSettings.get().actionTiles ? action.tiles().size() * 10 : 0;
            g.pose().pushPose(); g.pose().translate(getX() + 4, getY() + 4 * s, 0); g.pose().scale(s, s, 1);
            int textWidth = width / s - icons - 8;
            MahjongUi.text(g, font, getMessage(), 0, 5, textWidth, active ? MahjongUi.TEXT : MahjongUi.DISABLED, false);
            var deck = table().clientMcrDeck();
            for (int i = 0; icons > 0 && i < action.tiles().size(); i++) TileGui.tile(g, action.tiles().get(i), textWidth + i * 10, 0, 9,
                false, false, false, false, deck.preset(), deck.material(), deck.back(), deck.backPreset());
            g.pose().popPose();
        }
    }

    private Component actionLabel(McrAction action) {
        String name = action.type().name().toLowerCase(java.util.Locale.ROOT);
        if (view().game().phase() == top.skyeyefast.mchjong.engine.McrGame.Phase.TURN) {
            if (action.type() == McrAction.Type.WIN) name = "self_drawn";
            if (action.type() == McrAction.Type.MELDED_KONG) name = "added_kong";
        }
        return Component.translatable("mcr.mchjong.action." + name);
    }
}
