package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.SichuanAction;
import top.skyeyefast.mchjong.engine.SichuanSession;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.network.SichuanActionPayload;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Seated world interaction and the fixed immersive canvas share server-issued Sichuan decisions. */
public final class SichuanTableScreen extends Screen {
    private final BlockPos pos;
    private long shownRevision = -1, decision = -1, lastClickAt;
    private int selected = Tile.ABSENT, hovered = Tile.ABSENT, lastClicked = Tile.ABSENT;
    private boolean pending;
    private final TableViewController presentation = new TableViewController();
    private float framePartial;
    private TableHand hand;
    private TableBoard board;
    private TableTurnClock turnClock;
    private final List<AbstractWidget> decisionControls = new ArrayList<>();
    private int actionTop, actionHeight;
    private final TableHints hints = new TableHints();
    private final TableAutomation automation = new TableAutomation(this, this::automationOptions, this::rebuild);

    private List<TableAutomation.Toggle> automationOptions() {
        var current = view();
        if (current == null || (current.game().phase() != top.skyeyefast.mchjong.engine.SichuanGame.Phase.TURN
            && current.game().phase() != top.skyeyefast.mchjong.engine.SichuanGame.Phase.REACTION)) return List.of();
        return TableAutomation.common(pos, table().clientTableRoom(), current.game().decision());
    }
    public void receivedControlReply() { automation.receivedControlReply(); }


    public SichuanTableScreen(BlockPos pos) { this(pos, false); }
    public SichuanTableScreen(BlockPos pos, boolean immersive) {
        super(Component.translatable("sichuan.mchjong.title"));
        this.pos = pos.immutable(); presentation.immersive(immersive);
    }
    public BlockPos tablePos() { return pos; }
    public static SichuanTableScreen active(Screen screen) {
        screen = TableChildScreen.root(screen);
        return screen instanceof SichuanTableScreen table ? table : null;
    }
    public static boolean isOpen(Screen screen) {
        screen = TableChildScreen.root(screen);
        return active(screen) != null || screen instanceof SichuanResultsScreen || screen instanceof SichuanLobbyScreen
            || screen instanceof SichuanRulesScreen;
    }
    public boolean immersive() { return presentation.immersive(); }
    public boolean inspecting() { return presentation.inspecting(); }
    @Override public Component getNarrationMessage() {
        var message = super.getNarrationMessage().copy();
        var current = view();
        var room = current == null ? null : table().clientTableRoom();
        if (current != null && current.game() != null && room != null) for (int seat = 0; seat < 4; seat++)
            message.append("\n").append(room.seats().get(seat).participant().name())
                .append(" ").append(SichuanTableScene.status(current.game().seats().get(seat)));
        return message;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int positionX, int positionY, float partialTick) {}
    @Override public void removed() { presentation.clearInput(); }
    private TableCanvas canvas() { return presentation.canvas(width, height); }
    private int uiWidth() { return canvas().width(); }
    private int uiHeight() { return canvas().height(); }
    private int contentScale() { return immersive() ? 2 : 1; }
    private double canvasX(double positionX) { return canvas().localX(positionX); }
    private double canvasY(double positionY) { return canvas().localY(positionY); }
    private MahjongTableBlockEntity table() {
        return minecraft != null && minecraft.level != null
            && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table ? table : null;
    }
    private SichuanSession.View view() { return table() == null ? null : table().clientSichuanView(); }
    public void receivedView() { pending = false; rebuild(); }
    @Override protected void init() { rebuild(); }

    private void rebuild() {
        int automationFocus = automation.focusedIndex(getFocused());
        boolean hintFocused = getFocused() == hints;
        int focusedTile = getFocused() instanceof HandTarget target ? target.tile : Tile.ABSENT;
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
        board = immersive() ? new TableBoard(TableBoardState.live(game), 20, 1260, 68, 620, 800, true) : null;
        if (immersive() && game.viewerSeat() >= 0) {
            var player = game.seats().get(game.viewerSeat());
            var tiles = new ArrayList<>(player.hand());
            if (player.drawn() >= 0 && tiles.remove(Integer.valueOf(player.drawn()))) tiles.add(player.drawn());
            hand = new TableHand(tiles, player.drawn(), player.melds(), game.viewerSeat(), TableCanvas.WIDTH, TableCanvas.HEIGHT - (automation.available() ? 60 : 30), 58, true, top.skyeyefast.mchjong.engine.MahjongVariant.SICHUAN);
        }
        if (room.exitVote() != null) {
            TableExitControls.voteButtons(pos, room, uiWidth(), uiHeight(), contentScale()).forEach(this::addRenderableWidget);
            return;
        }
        int textScale = contentScale(), canvasWidth = uiWidth();
        turnClock = addRenderableWidget(new TableTurnClock());
        TableToolbar.build(this, pos, room, uiWidth(), immersive(), this::toggleView)
            .forEach(this::addRenderableWidget);
        automation.build(uiWidth(), immersive() ? uiHeight() - 32 : uiHeight() - 17, immersive()).forEach(this::addRenderableWidget);
        automation.restoreFocus(automationFocus);
        var choices = new ArrayList<Integer>();
        if (discardAction(selected) >= 0) choices.add(discardAction(selected));
        for (int actionIndex = 0; actionIndex < game.actions().size(); actionIndex++)
            if (game.actions().get(actionIndex).type() != SichuanAction.Type.DISCARD
                && game.actions().get(actionIndex).type() != SichuanAction.Type.DRAW
                && (game.actions().get(actionIndex).type() != SichuanAction.Type.VOID_SUIT
                    || game.actions().get(actionIndex).tiles().isEmpty())) choices.add(actionIndex);
        var grid = TableActionLayout.of(choices.size(), uiWidth(), uiHeight(), hand == null ? -1 : hand.top(),
            immersive(), automation.available() && !immersive() ? automation.width(uiWidth()) + 8 : 0);
        actionTop = grid.top(); actionHeight = grid.spanHeight();
        for (int slot = 0; slot < choices.size(); slot++) {
            int index = choices.get(slot);
            var button = new ActionButton(grid.x(slot), grid.y(slot), grid.width(), grid.height(),
                game.actions().get(index), () -> send(view, index));
            button.active = !pending && !view.paused(); addRenderableWidget(button);
            decisionControls.add(button);
        }
        if (game.viewerSeat() >= 0) for (int tile : game.seats().get(game.viewerSeat()).hand()) {
            var target = addRenderableWidget(new HandTarget(tile));
            if (tile == focusedTile) setFocused(target);
        }
        addRenderableWidget(hints);
        if (hintFocused && hints.visible) setFocused(hints);
    }
    private void send(SichuanSession.View snapshot, int index) {
        var current = view();
        if (pending || current == null || snapshot == null || current.paused() || minecraft.getConnection() == null
            || !snapshot.tableId().equals(current.tableId()) || !snapshot.incarnation().equals(current.incarnation())
            || snapshot.game().decision() != current.game().decision() || index < 0 || index >= current.game().actions().size()
            || table().clientTableRoom().exitVote() != null) return;
        pending = true;
        minecraft.getConnection().send(PayloadPackets.serverbound(new SichuanActionPayload(pos, current.tableId(),
            current.incarnation(), current.game().decision(), index)));
        rebuild();
    }
    private int discardAction(int tile) {
        var view = view();
        if (view == null || tile < 0) return -1;
        for (int actionIndex = 0; actionIndex < view.game().actions().size(); actionIndex++) {
            var action = view.game().actions().get(actionIndex);
            if ((action.type() == SichuanAction.Type.DISCARD || action.type() == SichuanAction.Type.VOID_SUIT)
                && action.tiles().contains(tile)) return actionIndex;
        }
        return -1;
    }
    private void choose(int tile) {
        if (pending) return;
        selected = tile; long now = Util.getMillis(); var mode = TableSettings.get().discardMode;
        if (view() != null && view().game().phase() != top.skyeyefast.mchjong.engine.SichuanGame.Phase.VOIDING
            && !hasShiftDown() && (mode == TableSettings.DiscardMode.SINGLE_CLICK
            || mode == TableSettings.DiscardMode.DOUBLE_CLICK && lastClicked == tile && now - lastClickAt <= 450)) {
            int index = discardAction(tile);
            if (index >= 0) { send(view(), index); return; }
        }
        lastClicked = tile; lastClickAt = now; rebuild(); setFocused(null);
    }
    private Component tileLabel(int tile) {
        return table().clientSichuanDeck().tile(tile).label(TableSettings.get().tileLabels == TableSettings.TileLabels.MPSZ,
            table().clientSichuanDeck().preset());
    }
    public boolean selected(SichuanTableScene.Piece piece) { return !immersive() && ownHand(piece) && piece.tile() == selected; }
    private boolean ownHand(SichuanTableScene.Piece piece) {
        return view() != null && piece.area() == SichuanTableScene.Area.HAND && piece.seat() == view().game().viewerSeat();
    }
    public int highlight(SichuanTableScene.Piece piece) {
        if (immersive() || !TableSettings.get().highlightTiles || !ownHand(piece)) return 0;
        int focus = getFocused() instanceof HandTarget target ? target.tile : hovered;
        return piece.tile() == selected ? MahjongUi.ACCENT : piece.tile() == focus ? MahjongUi.POSITIVE : 0;
    }
    private boolean overWidget(double positionX, double positionY) {
        return children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .anyMatch(widget -> !(widget instanceof HandTarget) && widget.visible
                && positionX >= widget.getX() && positionX < widget.getRight() && positionY >= widget.getY() && positionY < widget.getBottom());
    }
    private SeatedTableProjection projection() { return SeatedTableProjection.capture(pos, width, height, framePartial); }
    private int pick(double positionX, double positionY) {
        var view = view();
        if (view == null || view.game().viewerSeat() < 0 || table().clientTableRoom().exitVote() != null || overWidget(positionX, positionY)) return Tile.ABSENT;
        if (hand != null) return hand.pick(positionX, positionY, selected);
        if (immersive()) return Tile.ABSENT;
        var pointer = projection().pointer(positionX, positionY);
        double closest = Double.POSITIVE_INFINITY; int tile = Tile.ABSENT;
        for (var piece : SichuanTableScene.build(view.game())) if (ownHand(piece)) {
            double distance = TilePicking.distanceSquared(TableAnimation.of(table()).worldPose(piece, Util.getMillis()), piece.scale(), pointer.origin(), pointer.ray(), selected(piece));
            if (distance < closest) { closest = distance; tile = piece.tile(); }
        }
        return tile;
    }
    private TableHand.Point point(int tile) {
        if (hand != null) return hand.point(tile, selected, hovered);
        var view = view(); if (view == null) return null;
        for (var piece : SichuanTableScene.build(view.game())) if (ownHand(piece) && piece.tile() == tile) {
            var point = projection().project(TableAnimation.of(table()).worldPose(piece, Util.getMillis()).position().add(0, selected(piece) ? .035 : 0, 0), .01);
            return point == null ? null : new TableHand.Point((int) point.x(), (int) point.y());
        }
        return null;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        framePartial = partialTick;
        var view = view(); if (view == null || table().clientSichuanDeck() == null) return;
        if (view.revision() != shownRevision) rebuild();
        if (turnClock != null && view.game().viewerSeat() >= 0) {
            var clock = view.clocks().get(view.game().viewerSeat()).after(table().clientViewAgeMillis());
            int textScale = contentScale(), bottom = clockBottom();
            int nextTop = bottom - actionHeight;
            for (var widget : decisionControls) widget.setY(widget.getY() + nextTop - actionTop);
            actionTop = nextTop;
            turnClock.update(clock, uiWidth() - 16 * textScale, bottom, textScale);
        }
        var transform = canvas();
        int mx = (int) Math.floor(transform.localX(mouseX)), my = (int) Math.floor(transform.localY(mouseY));
        transform.begin(graphics);
        try {
            if (immersive()) {
                graphics.fill(0, 0, TableCanvas.WIDTH, TableCanvas.HEIGHT, MahjongUi.INPUT);
                var deck = table().clientSichuanDeck(); var animation = TableAnimation.of(table()); long now = Util.getMillis();
                board.render(graphics, TableBoardState.live(view.game()), deck.preset(), animation.riverSuppressed(now), deck.material(), deck.back(), deck.backPreset(),
                    table().clientSichuanCloth(), animation, now, tile -> TileMesh.artwork(deck.tile(tile)));
            }
            hovered = transform.contains(mouseX, mouseY) ? pick(mx, my) : Tile.ABSENT;
            int textScale = contentScale();
            graphics.pose().pushPose(); graphics.pose().translate(0, 0, 400); graphics.pose().scale(textScale, textScale, 1);
            int canvasWidth = uiWidth() / textScale;
            if (view.paused()) graphics.drawCenteredString(font, Component.translatable("sichuan.mchjong.paused"), canvasWidth / 2,
                immersive() ? 183 : 43, MahjongUi.NEGATIVE);
            else if (view.game().submitted()) graphics.drawCenteredString(font, Component.translatable("sichuan.mchjong.responded"),
                canvasWidth / 2, immersive() ? 183 : 43, MahjongUi.MUTED);
            else if (view.game().phase() == top.skyeyefast.mchjong.engine.SichuanGame.Phase.VOIDING && view.game().rules().selectFirstDiscard())
                graphics.drawCenteredString(font, Component.translatable("sichuan.mchjong.void_first_discard"),
                    canvasWidth / 2, immersive() ? 183 : 43, MahjongUi.MUTED);
            graphics.pose().popPose();
            renderSeats(graphics, view, mx, my);
            renderClaims(graphics, view);
            if (hand != null) {
                var deck = table().clientSichuanDeck();
                int focus = getFocused() instanceof HandTarget target ? target.tile : hovered;
                hand.render(graphics, selected, focus, tile -> tile == selected ? MahjongUi.ACCENT : 0, TableAnimation.of(table()).handSuppressed(Util.getMillis()),
                    deck.preset(), deck.material(), deck.back(), deck.backPreset(), TableAnimation.of(table()), board.drawSource(), Util.getMillis(), tile -> TileMesh.artwork(deck.tile(tile)));
            }
            if (board != null) {
                var deck = table().clientSichuanDeck();
                TableAnimation.of(table()).render(graphics, board, hand, TableCanvas.HEIGHT - (automation.available() ? 60 : 30), Util.getMillis(),
                    deck.preset(), deck.material(), deck.back(), deck.backPreset(), tile -> TileMesh.artwork(deck.tile(tile)));
            }
            updateHints();
            graphics.pose().pushPose(); graphics.pose().translate(0, 0, 600);
            TableExitControls.renderVote(graphics, font, table().clientTableRoom(), uiWidth(), uiHeight(), textScale);
            super.render(graphics, mx, my, partialTick);
            hints.renderPopup(graphics, font, table().clientSichuanDeck().preset());
            if (table().clientTableRoom().exitVote() == null && (turnClock == null || !turnClock.visible)
                && TableSettings.get().show(TableSettings.Information.HELP)) {
                var help = Component.translatable("sichuan.mchjong.help." + TableSettings.get().discardMode.name().toLowerCase(java.util.Locale.ROOT),
                    TableKeys.VIEW.getTranslatedKeyMessage(), TableKeys.PASS.getTranslatedKeyMessage());
                graphics.pose().pushPose(); graphics.pose().scale(textScale, textScale, 1);
                MahjongUi.text(graphics, font, help, immersive() ? 106 : 8, uiHeight() / textScale - 11,
                    uiWidth() / textScale - (immersive() ? 118 : 16), MahjongUi.MUTED, false);
                graphics.pose().popPose();
            }
            if (hovered >= 0 && table().clientTableRoom().exitVote() == null) graphics.renderTooltip(font, tileLabel(hovered), mx, my);
            graphics.pose().popPose();
        } finally { transform.end(graphics); }
    }
    private void renderSeats(GuiGraphics g, SichuanSession.View view, int mouseX, int mouseY) {
        var deck = table().clientSichuanDeck();
        TableHud.render(font, g, TableBoardState.live(view.game()), table().clientTableRoom(), view.game().seats().stream().map(SichuanTableScene::status).toList(),
            uiWidth(), immersive(), deck.preset(), deck.material(), deck.back(), deck.backPreset(), mouseX, mouseY, tile -> TileMesh.artwork(deck.tile(tile)));
    }
    private void renderClaims(GuiGraphics graphics, SichuanSession.View view) {
        var claims = SichuanTableScene.claims(view.game()).stream().filter(claim -> claim.winners().size() > 1).toList();
        int scale = contentScale();
        graphics.pose().pushPose(); graphics.pose().scale(scale, scale, 1);
        int top = immersive() ? 198 : 40;
        for (var claim : claims) {
            String names = String.join(" / ", claim.winners().stream()
                .map(seat -> table().clientTableRoom().seats().get(seat).participant().name()).toList());
            MahjongUi.text(graphics, font, Component.translatable("ui.mchjong.annotation",
                    Component.translatable("sichuan.mchjong.multiple_winners", names), tileLabel(claim.tile())),
                uiWidth() / scale / 4, top, uiWidth() / scale / 2, MahjongUi.POSITIVE, true);
            top += 11;
        }
        graphics.pose().popPose();
    }

    private int clockBottom() {
        if (hand != null) return hand.top() - 12;
        var projection = projection();
        double top = Double.POSITIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        for (var piece : SichuanTableScene.build(view().game())) if (ownHand(piece)) {
            var pose = TableAnimation.of(table()).worldPose(piece, Util.getMillis());
            var transform = new org.joml.Matrix4f().translation((float) pose.position().x,
                (float) pose.position().y, (float) pose.position().z)
                .rotateY((float) Math.toRadians(pose.yaw())).rotateX((float) Math.toRadians(pose.pitch())).scale(piece.scale());
            for (int positionX = -1; positionX <= 1; positionX += 2) for (int positionY = -1; positionY <= 1; positionY += 2) for (int z = -1; z <= 1; z += 2) {
                var corner = transform.transformPosition(new org.joml.Vector3f(positionX * TileMesh.WIDTH / 2,
                    positionY * TileMesh.HEIGHT / 2, z * TileMesh.DEPTH / 2));
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
        if (!room.convenienceHints() || pending || room.exitVote() != null || view().paused()) {
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
        int focus = getFocused() instanceof HandTarget target ? target.tile : hovered;
        hints.update(view().game(), focus, selected, font, center, bottom, Math.max(0, half),
            38, scale);
        hints.setX(uiWidth() - 30 * scale);
        hints.setY(uiHeight() - 16 * scale);
    }

    private net.minecraft.client.gui.navigation.ScreenRectangle privateHandBounds() {
        if (hand != null) return new net.minecraft.client.gui.navigation.ScreenRectangle(0, hand.top(), uiWidth(), uiHeight() - hand.top());
        var projection = projection();
        double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        for (var piece : SichuanTableScene.build(view().game())) if (ownHand(piece)) {
            var pose = TableAnimation.of(table()).worldPose(piece, Util.getMillis());
            var transform = new org.joml.Matrix4f().translation((float) pose.position().x,
                (float) pose.position().y, (float) pose.position().z)
                .rotateY((float) Math.toRadians(pose.yaw())).rotateX((float) Math.toRadians(pose.pitch())).scale(piece.scale());
            for (int x = -1; x <= 1; x += 2) for (int y = -1; y <= 1; y += 2) for (int z = -1; z <= 1; z += 2) {
                var corner = transform.transformPosition(new org.joml.Vector3f(x * TileMesh.WIDTH / 2,
                    y * TileMesh.HEIGHT / 2, z * TileMesh.DEPTH / 2));
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
    @Override public boolean mouseClicked(double positionX, double positionY, int button) {
        if (presentation.mouseBinding(button, this::toggleView, this::resetView)) return true;
        if (TableKeys.PASS.matchesMouse(button)) { pass(); return true; }
        if (!canvas().contains(positionX, positionY)) return false;
        positionX = canvasX(positionX); positionY = canvasY(positionY);
        if (super.mouseClicked(positionX, positionY, button)) return true;
        if (button == 1) {
            if (presentation.cameraEnabled()) presentation.startDrag();
            else cancelSelection();
            return true;
        }
        if (button == 0) { int tile = pick(positionX, positionY); if (tile >= 0) { choose(tile); return true; } }
        return false;
    }
    @Override public boolean mouseReleased(double positionX, double positionY, int button) {
        if (presentation.releaseInspect(button)) return true;
        if (presentation.releaseDrag(button, this::cancelSelection)) return true;
        return super.mouseReleased(canvasX(positionX), canvasY(positionY), button);
    }
    @Override public boolean mouseDragged(double positionX, double positionY, int button, double dx, double dy) {
        if (presentation.drag(button, dx, dy, hasShiftDown())) return true;
        double scale = canvas().scale();
        return super.mouseDragged(canvasX(positionX), canvasY(positionY), button, dx / scale, dy / scale);
    }
    @Override public boolean mouseScrolled(double positionX, double positionY, double horizontal, double vertical) {
        if (!canvas().contains(positionX, positionY)) return false;
        positionX = canvasX(positionX); positionY = canvasY(positionY);
        if (!overWidget(positionX, positionY) && presentation.scroll(vertical, hasShiftDown())) return true;
        return super.mouseScrolled(positionX, positionY, horizontal, vertical);
    }

    private void pass() {
        var view = view();
        if (view != null) for (int actionIndex = 0; actionIndex < view.game().actions().size(); actionIndex++)
            if (view.game().actions().get(actionIndex).type() == SichuanAction.Type.PASS) { send(view, actionIndex); return; }
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (presentation.keyPressed(key, scanCode, this::toggleView, this::resetView)) return true;
        if (TableKeys.PASS.matches(key, scanCode)) { pass(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE && selected >= 0) { cancelSelection(); return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && selected >= 0 && getFocused() == null) {
            send(view(), discardAction(selected)); return true;
        }
        if (getFocused() == null && presentation.lookPressed(key)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
        return presentation.keyReleased(key, scanCode) || super.keyReleased(key, scanCode, modifiers);
    }
    private final class HandTarget extends MahjongButton {
        private final int tile;
        HandTarget(int tile) { super(0, 0, 20, 20, tileLabel(tile), ignored -> choose(tile)); this.tile = tile; setTooltip(null); }
        @Override protected boolean clicked(double positionX, double positionY) { return false; }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var point = point(tile); active = !pending && view() != null && !view().paused();
            if (point == null) return;
            setX(point.x() - 10); setY(point.y() - 10);
            if (isFocused()) graphics.drawCenteredString(font, getMessage(), point.x(), point.y() - 28, MahjongUi.ACCENT);
        }
    }
    private final class ActionButton extends MahjongButton {
        private final SichuanAction action;
        ActionButton(int positionX, int positionY, int canvasWidth, int widgetHeight, SichuanAction action, Runnable press) {
            super(positionX, positionY, canvasWidth, widgetHeight, actionLabel(action), ignored -> press.run());
            this.action = action;
            if (action.type() == SichuanAction.Type.WIN) primary();
            var label = getMessage().copy(); for (int tile : action.tiles()) label.append("\n").append(tileLabel(tile));
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderSurface(graphics);
            int textScale = contentScale(), icons = TableSettings.get().actionTiles ? action.tiles().size() * 10 : 0;
            graphics.pose().pushPose(); graphics.pose().translate(getX() + 4, getY() + 4 * textScale, 0); graphics.pose().scale(textScale, textScale, 1);
            int textWidth = width / textScale - icons - 8;
            MahjongUi.text(graphics, font, getMessage(), 0, 5, textWidth, active ? MahjongUi.TEXT : MahjongUi.DISABLED, false);
            var deck = table().clientSichuanDeck();
            for (int actionIndex = 0; icons > 0 && actionIndex < action.tiles().size(); actionIndex++) TileGui.tileArtwork(graphics, action.tiles().get(actionIndex), textWidth + actionIndex * 10, 0, 9,
                false, false, false, false, 0, deck.preset(), deck.material(), deck.back(), deck.backPreset(), tile -> TileMesh.artwork(deck.tile(tile)));
            graphics.pose().popPose();
        }
    }

    private Component actionLabel(SichuanAction action) {
        var label = Component.translatable("sichuan.mchjong.action." + action.type().name().toLowerCase(java.util.Locale.ROOT));
        if (action.type() == SichuanAction.Type.VOID_SUIT)
            label = Component.translatable("sichuan.mchjong.void_choice", Component.translatable("sichuan.mchjong.suit." + action.suit()));
        return label;
    }
}
