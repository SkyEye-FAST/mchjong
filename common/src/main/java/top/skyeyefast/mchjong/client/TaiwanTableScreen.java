package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.TaiwanAction;
import top.skyeyefast.mchjong.engine.TaiwanSession;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.network.TaiwanActionPayload;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Seated world interaction and the fixed immersive canvas share server-issued Taiwan decisions. */
public final class TaiwanTableScreen extends Screen {
    private final BlockPos pos;
    private long shownRevision = -1, decision = -1, lastClickAt;
    private int selected = Tile.ABSENT, hovered = Tile.ABSENT, lastClicked = Tile.ABSENT;
    private boolean pending;
    private final TableViewController presentation = new TableViewController();
    private float framePartial;
    private TableHand hand;
    private TableBoard board;
    private TableTurnClock turnClock;
    private final TableHints hints = new TableHints();
    private final List<AbstractWidget> decisionControls = new ArrayList<>();
    private int actionTop, actionHeight;
    public TaiwanTableScreen(BlockPos pos) { this(pos, false); }
    public TaiwanTableScreen(BlockPos pos, boolean immersive) {
        super(Component.translatable("taiwan.mchjong.title"));
        this.pos = pos.immutable(); presentation.immersive(immersive);
    }
    public BlockPos tablePos() { return pos; }
    public static TaiwanTableScreen active(Screen screen) {
        screen = TableChildScreen.root(screen);
        return screen instanceof TaiwanTableScreen table ? table : null;
    }
    public static boolean isOpen(Screen screen) {
        screen = TableChildScreen.root(screen);
        return active(screen) != null || screen instanceof TaiwanResultsScreen || screen instanceof TaiwanLobbyScreen;
    }
    public boolean immersive() { return presentation.immersive(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float partialTick) {}
    @Override public void removed() { presentation.clearInput(); }
    private TableCanvas canvas() { return presentation.canvas(width, height); }
    private int uiWidth() { return canvas().width(); }
    private int uiHeight() { return canvas().height(); }
    private int contentScale() { return immersive() ? 2 : 1; }
    private double canvasX(double x) { return canvas().localX(x); }
    private double canvasY(double y) { return canvas().localY(y); }
    private MahjongTableBlockEntity table() {
        return minecraft != null && minecraft.level != null
            && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table ? table : null;
    }
    private TaiwanSession.View view() { return table() == null ? null : table().clientTaiwanView(); }
    public void receivedView() { pending = false; rebuild(); }
    @Override protected void init() { rebuild(); }

    private void rebuild() {
        boolean hintFocused = getFocused() == hints;
        clearWidgets();
        turnClock = null;
        decisionControls.clear();
        var view = view();
        if (view == null || table().clientTableRoom() == null) return;
        var room = table().clientTableRoom(); var game = view.game();
        if (shownRevision != view.revision()) pending = false;
        shownRevision = view.revision();
        if (decision != game.decision()) {
            decision = game.decision(); selected = hovered = lastClicked = Tile.ABSENT;
            hints.clearPreview();
        }
        hand = null;
        board = immersive() ? new TableBoard(TableBoardState.live(view), 20, 1260, 68, 620, 800, true) : null;
        if (immersive() && game.recipient() >= 0) {
            var player = game.seats().get(game.recipient());
            var tiles = new ArrayList<>(player.concealed());
            if (player.drawn() >= 0 && tiles.remove(Integer.valueOf(player.drawn()))) tiles.add(player.drawn());
            hand = new TableHand(tiles, player.drawn(), player.melds(), player.flowers().size(), game.recipient(), TableCanvas.WIDTH, TableCanvas.HEIGHT - 60, 58, true, top.skyeyefast.mchjong.engine.MahjongVariant.TAIWAN);
        }
        if (room.exitVote() != null) {
            hints.clearPreview();
            TableExitControls.voteButtons(pos, room, uiWidth(), uiHeight(), contentScale()).forEach(this::addRenderableWidget);
            return;
        }
        turnClock = addRenderableWidget(new TableTurnClock());
        TableToolbar.build(this, pos, room, uiWidth(), immersive(), this::toggleView)
            .forEach(this::addRenderableWidget);
        var choices = new ArrayList<Integer>();
        if (discardAction(selected) >= 0) choices.add(discardAction(selected));
        for (int i = 0; i < game.actions().size(); i++)
            if (game.actions().get(i).type() != TaiwanAction.Type.DISCARD) choices.add(i);
        var grid = TableActionLayout.of(choices.size(), uiWidth(), uiHeight(), hand == null ? -1 : hand.top(),
            immersive(), 0);
        actionTop = grid.top(); actionHeight = grid.spanHeight();
        for (int slot = 0; slot < choices.size(); slot++) {
            int index = choices.get(slot);
            var button = new ActionButton(grid.x(slot), grid.y(slot), grid.width(), grid.height(),
                game.actions().get(index), () -> send(view, index));
            button.active = !pending && !view.paused(); addRenderableWidget(button);
            decisionControls.add(button);
        }
        addRenderableWidget(hints);
        updateHints();
        if (hintFocused && hints.visible) setFocused(hints);
    }
    private void send(TaiwanSession.View snapshot, int index) {
        var current = view();
        if (pending || current == null || snapshot == null || current.paused() || minecraft.getConnection() == null
            || !snapshot.tableId().equals(current.tableId()) || !snapshot.incarnation().equals(current.incarnation())
            || snapshot.game().decision() != current.game().decision() || index < 0 || index >= current.game().actions().size()
            || table().clientTableRoom().exitVote() != null) return;
        pending = true;
        minecraft.getConnection().send(PayloadPackets.serverbound(new TaiwanActionPayload(pos, current.tableId(),
            current.incarnation(), current.game().decision(), index)));
        rebuild();
    }
    private int discardAction(int tile) {
        var view = view();
        if (view == null || tile < 0) return -1;
        for (int i = 0; i < view.game().actions().size(); i++) {
            var action = view.game().actions().get(i);
            if (action.type() == TaiwanAction.Type.DISCARD && action.tiles().contains(tile)) return i;
        }
        return -1;
    }
    private void choose(int tile) {
        if (pending) return;
        selected = tile; long now = Util.getMillis(); var mode = TableSettings.get().discardMode;
        if (!MahjongUi.shiftDown() && (mode == TableSettings.DiscardMode.SINGLE_CLICK
            || mode == TableSettings.DiscardMode.DOUBLE_CLICK && lastClicked == tile && now - lastClickAt <= 450)) {
            int index = discardAction(tile);
            if (index >= 0) { send(view(), index); return; }
        }
        lastClicked = tile; lastClickAt = now; rebuild(); setFocused(null);
    }
    private Component tileLabel(int tile) {
        return table().clientTaiwanDeck().tile(tile).label(TableSettings.get().tileLabels == TableSettings.TileLabels.MPSZ,
            table().clientTaiwanDeck().preset());
    }
    public boolean selected(TaiwanTableScene.Piece piece) { return !immersive() && ownHand(piece) && piece.tile() == selected; }
    private boolean ownHand(TaiwanTableScene.Piece piece) {
        return view() != null && piece.area() == TaiwanTableScene.Area.HAND && piece.seat() == view().game().recipient();
    }
    public int highlight(TaiwanTableScene.Piece piece) {
        if (immersive() || !TableSettings.get().highlightTiles || !ownHand(piece)) return 0;
        int focus = hovered;
        return piece.tile() == selected ? MahjongUi.ACCENT : piece.tile() == focus ? MahjongUi.POSITIVE : 0;
    }
    private boolean overWidget(double x, double y) {
        return children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .anyMatch(widget -> widget.visible
                && x >= widget.getX() && x < widget.getRight() && y >= widget.getY() && y < widget.getBottom());
    }
    private SeatedTableProjection projection() { return SeatedTableProjection.capture(pos, width, height, framePartial); }
    private int pick(double x, double y) {
        var view = view();
        if (view == null || view.game().recipient() < 0 || table().clientTableRoom().exitVote() != null || overWidget(x, y)) return Tile.ABSENT;
        if (hand != null) return hand.pick(x, y, selected);
        if (immersive()) return Tile.ABSENT;
        var pointer = projection().pointer(x, y);
        double closest = Double.POSITIVE_INFINITY; int tile = Tile.ABSENT;
        for (var piece : TaiwanTableScene.build(view.game())) if (ownHand(piece)) {
            double distance = TilePicking.distanceSquared(TableAnimation.of(table()).worldPose(piece, Util.getMillis()), piece.dimensions(), pointer.origin(), pointer.ray(), selected(piece));
            if (distance < closest) { closest = distance; tile = piece.tile(); }
        }
        return tile;
    }
    private TableHand.Point point(int tile) {
        if (hand != null) return hand.point(tile, selected, hovered);
        var view = view(); if (view == null) return null;
        for (var piece : TaiwanTableScene.build(view.game())) if (ownHand(piece) && piece.tile() == tile) {
            var point = projection().project(TableAnimation.of(table()).worldPose(piece, Util.getMillis()).position().add(0, selected(piece) ? .035 : 0, 0), .01);
            return point == null ? null : new TableHand.Point((int) point.x(), (int) point.y());
        }
        return null;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        framePartial = partialTick;
        var view = view(); if (view == null || table().clientTaiwanDeck() == null) return;
        if (view.revision() != shownRevision) rebuild();
        if (turnClock != null && view.game().recipient() >= 0) {
            var clock = view.clock().after(table().clientViewAgeMillis());
            int s = contentScale(), bottom = clockBottom();
            int nextTop = bottom - (clock.active() ? 28 * contentScale() : 0) - actionHeight;
            for (var widget : decisionControls) widget.setY(widget.getY() + nextTop - actionTop);
            actionTop = nextTop;
            turnClock.update(clock, uiWidth() - 16 * s, bottom, s);
        }
        var transform = canvas();
        int mx = (int) Math.floor(transform.localX(mouseX)), my = (int) Math.floor(transform.localY(mouseY));
        transform.begin(g);
        try {
            if (immersive()) {
                g.fill(0, 0, TableCanvas.WIDTH, TableCanvas.HEIGHT, MahjongUi.INPUT);
                var deck = table().clientTaiwanDeck(); var animation = TableAnimation.of(table()); long now = Util.getMillis();
                board.render(g, TableBoardState.live(view), deck.preset(), animation.riverSuppressed(now), deck.material(), deck.back(), deck.backPreset(),
                    table().clientTaiwanCloth(), animation, now, tile -> TileMesh.artwork(deck.tile(tile)));
            }
            hovered = transform.contains(mouseX, mouseY) ? pick(mx, my) : Tile.ABSENT;
            updateHints();
            int s = contentScale();
            g.pose().pushMatrix(); g.pose().translate(0, 0); g.pose().scale(s, s);
            int w = uiWidth() / s;
            if (view.paused()) g.centeredText(font, Component.translatable("taiwan.mchjong.paused"), w / 2,
                immersive() ? 183 : 43, MahjongUi.NEGATIVE);
            else if (view.game().responded()) g.centeredText(font, Component.translatable("taiwan.mchjong.responded"),
                w / 2, immersive() ? 183 : 43, MahjongUi.MUTED);
            g.pose().popMatrix();
            renderSeats(g, view, mx, my);
            if (hand != null) {
                var deck = table().clientTaiwanDeck();
                int focus = hovered;
                hand.render(g, selected, focus, tile -> tile == selected ? MahjongUi.ACCENT : 0, TableAnimation.of(table()).handSuppressed(Util.getMillis()),
                    deck.preset(), deck.material(), deck.back(), deck.backPreset(), TableAnimation.of(table()), board.drawSource(), Util.getMillis(), tile -> TileMesh.artwork(deck.tile(tile)));
            }
            if (board != null) {
                var deck = table().clientTaiwanDeck();
                TableAnimation.of(table()).render(g, board, hand, TableCanvas.HEIGHT - 60, Util.getMillis(),
                    deck.preset(), deck.material(), deck.back(), deck.backPreset(), tile -> TileMesh.artwork(deck.tile(tile)));
            }
            g.pose().pushMatrix(); g.pose().translate(0, 0);
            TableExitControls.renderVote(g, font, table().clientTableRoom(), uiWidth(), uiHeight(), s);
            super.extractRenderState(g, mx, my, partialTick);
            if (table().clientTableRoom().exitVote() == null && (turnClock == null || !turnClock.visible)
                && TableSettings.get().show(TableSettings.Information.HELP)) {
                var help = Component.translatable("ui.mchjong.help." + TableSettings.get().discardMode.name().toLowerCase(java.util.Locale.ROOT));
                g.pose().pushMatrix(); g.pose().scale(s, s);
                MahjongUi.text(g, font, help, immersive() ? 106 : 8, uiHeight() / s - 11,
                    uiWidth() / s - (immersive() ? 118 : 16), MahjongUi.MUTED, false);
                g.pose().popMatrix();
            }
            if (hovered >= 0 && table().clientTableRoom().exitVote() == null) g.setTooltipForNextFrame(font, tileLabel(hovered), mx, my);
            hints.renderPopup(g, font, table().clientTaiwanDeck().preset());
            g.pose().popMatrix();
        } finally { transform.end(g); }
    }
    private void renderSeats(GuiGraphicsExtractor g, TaiwanSession.View view, int mouseX, int mouseY) {
        var deck = table().clientTaiwanDeck();
        TableHud.render(font, g, TableBoardState.live(view), table().clientTableRoom(), java.util.stream.IntStream.range(0, 4).<Component>mapToObj(seat -> {
                var player = view.game().seats().get(seat);
                return Component.translatable("wind.mchjong." + new String[]{"east", "south", "west", "north"}[Math.floorMod(seat - view.game().opening().dealer(), 4)])
                    .append("  ").append(Component.translatable("taiwan.mchjong.flowers", player.flowers().size()))
                    .append(player.ready() == top.skyeyefast.mchjong.engine.TaiwanWinContext.Ready.NONE ? Component.empty() : Component.literal("\n").append(Component.translatable("taiwan.mchjong.ready")));
            }).toList(),
            uiWidth(), immersive(), deck.preset(), deck.material(), deck.back(), deck.backPreset(), mouseX, mouseY, tile -> TileMesh.artwork(deck.tile(tile)));
    }
    private int clockBottom() {
        if (hand != null) return hand.top() - 12;
        var projection = projection();
        double top = Double.POSITIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        for (var piece : TaiwanTableScene.build(view().game())) if (ownHand(piece)) {
            var pose = TableAnimation.of(table()).worldPose(piece, Util.getMillis());
            var transform = new org.joml.Matrix4f().translation((float) pose.position().x,
                (float) pose.position().y, (float) pose.position().z)
                .rotateY((float) Math.toRadians(pose.yaw())).rotateX((float) Math.toRadians(pose.pitch()));
            for (int x = -1; x <= 1; x += 2) for (int y = -1; y <= 1; y += 2) for (int z = -1; z <= 1; z += 2) {
                var corner = transform.transformPosition(new org.joml.Vector3f(x * piece.dimensions().width() / 2,
                    y * piece.dimensions().height() / 2, z * piece.dimensions().depth() / 2));
                var point = projection.project(new net.minecraft.world.phys.Vec3(corner.x, corner.y + .035, corner.z), .01);
                if (point != null) { top = Math.min(top, point.y()); bottom = Math.max(bottom, point.y()); }
            }
        }
        if (!Double.isFinite(top) || top >= height || bottom <= 0 || top < 196 && bottom + 90 <= height - 24)
            return height - 24;
        return Math.max(190, Math.min(height - 24, (int) top - 6));
    }
    private void updateHints() {
        var room = table().clientTableRoom();
        if (!room.convenienceHints() || !room.allowConvenienceHints() || pending || room.exitVote() != null
            || view().paused() || view().game().recipient() < 0) {
            hints.clearPreview();
            return;
        }
        int scale = contentScale();
        var bounds = privateHandBounds();
        if (bounds == null) { hints.clearPreview(); return; }
        int center = hand == null ? bounds.left() + bounds.width() / 2 : hand.centerX();
        int bottom = bounds.top() - 4 * scale;
        int half = Math.min(center - 8, uiWidth() - 8 - center);
        for (var widget : decisionControls) if (widget.visible && widget.getY() < bottom
            && widget.getBottom() > bottom - 100 * scale) {
            if (widget.getX() > center) half = Math.min(half, widget.getX() - center - 4);
            else if (widget.getRight() < center) half = Math.min(half, center - widget.getRight() - 4);
        }
        int focus = hovered;
        hints.update(view().game(), focus, selected, font, center, bottom, Math.max(0, half), 38, scale);
        hints.setX(uiWidth() - 30 * scale);
        hints.setY(uiHeight() - 16 * scale);
    }

    private net.minecraft.client.gui.navigation.ScreenRectangle privateHandBounds() {
        if (hand != null) return new net.minecraft.client.gui.navigation.ScreenRectangle(0, hand.top(), uiWidth(), uiHeight() - hand.top());
        var projection = projection();
        double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        for (var piece : TaiwanTableScene.build(view().game())) if (ownHand(piece)) {
            var pose = TableAnimation.of(table()).worldPose(piece, Util.getMillis());
            var transform = new org.joml.Matrix4f().translation((float) pose.position().x,
                (float) pose.position().y, (float) pose.position().z)
                .rotateY((float) Math.toRadians(pose.yaw())).rotateX((float) Math.toRadians(pose.pitch()));
            for (int x = -1; x <= 1; x += 2) for (int y = -1; y <= 1; y += 2) for (int z = -1; z <= 1; z += 2) {
                var corner = transform.transformPosition(new org.joml.Vector3f(x * piece.dimensions().width() / 2,
                    y * piece.dimensions().height() / 2, z * piece.dimensions().depth() / 2));
                var point = projection.project(new net.minecraft.world.phys.Vec3(corner.x, corner.y + .035, corner.z), .01);
                if (point != null) {
                    left = Math.min(left, point.x()); right = Math.max(right, point.x());
                    top = Math.min(top, point.y()); bottom = Math.max(bottom, point.y());
                }
            }
        }
        if (!Double.isFinite(top) || top >= height || bottom <= 0 || left >= width || right <= 0) return null;
        int x = (int) Math.max(0, left), y = (int) Math.max(0, top);
        return new net.minecraft.client.gui.navigation.ScreenRectangle(x, y,
            (int) Math.min(width, Math.ceil(right)) - x, (int) Math.min(height, Math.ceil(bottom)) - y);
    }
    private void toggleView() { presentation.toggle(); rebuild(); }
    public void resetView() { presentation.reset(); }
    @Override public void tick() { presentation.tick(); }
    private void cancelSelection() { selected = lastClicked = Tile.ABSENT; hints.clearPreview(); rebuild(); }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y(); int button = event.button();
        if (!canvas().contains(x, y)) return false;
        x = canvasX(x); y = canvasY(y);
        if (super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y, event.buttonInfo()), doubleClick)) return true;
        if (button == 1) {
            if (presentation.cameraEnabled()) presentation.startDrag();
            else cancelSelection();
            return true;
        }
        if (button == 0) { int tile = pick(x, y); if (tile >= 0) { choose(tile); return true; } }
        return false;
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double x = event.x(), y = event.y(); int button = event.button();
        if (presentation.releaseDrag(button, this::cancelSelection)) return true;
        return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(canvasX(x), canvasY(y), event.buttonInfo()));
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double x = event.x(), y = event.y(); int button = event.button();
        if (presentation.drag(button, dx, dy, MahjongUi.shiftDown())) return true;
        double scale = canvas().scale();
        return super.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(canvasX(x), canvasY(y), event.buttonInfo()), dx / scale, dy / scale);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!canvas().contains(x, y)) return false;
        x = canvasX(x); y = canvasY(y);
        if (!overWidget(x, y) && presentation.scroll(vertical, MahjongUi.shiftDown())) return true;
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE && selected >= 0) { cancelSelection(); return true; }
        return super.keyPressed(event);
    }
    private final class ActionButton extends MahjongButton {
        private final top.skyeyefast.mchjong.engine.TaiwanGameState.Action action;
        ActionButton(int x, int y, int w, int h, top.skyeyefast.mchjong.engine.TaiwanGameState.Action action, Runnable press) {
            super(x, y, w, h, actionLabel(action), ignored -> press.run());
            this.action = action;
            if (action.type() == TaiwanAction.Type.WIN) primary();
            var label = getMessage().copy(); for (int tile : action.tiles()) label.append("\n").append(tileLabel(tile));
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            renderSurface(g);
            int s = contentScale(), icons = TableSettings.get().actionTiles ? action.tiles().size() * 10 : 0;
            g.pose().pushMatrix(); g.pose().translate(getX() + 4, getY() + 4 * s); g.pose().scale(s, s);
            int textWidth = width / s - icons - 8;
            MahjongUi.text(g, font, getMessage(), 0, 5, textWidth, captionColor(), false);
            var deck = table().clientTaiwanDeck();
            for (int i = 0; icons > 0 && i < action.tiles().size(); i++) TileGui.tileArtwork(g, action.tiles().get(i), textWidth + i * 10, 0, 9,
                false, false, false, false, 0, deck.preset(), deck.material(), deck.back(), deck.backPreset(), tile -> TileMesh.artwork(deck.tile(tile)));
            g.pose().popMatrix();
        }
    }

    private Component actionLabel(top.skyeyefast.mchjong.engine.TaiwanGameState.Action action) {
        String name = action.type().name().toLowerCase(java.util.Locale.ROOT);
        return Component.translatable("taiwan.mchjong.action." + name);
    }
}
