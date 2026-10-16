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
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.PayloadPackets;
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
    private final TableHandling.Drag handlingDrag = new TableHandling.Drag();
    private long handlingDecision = -1;
    private final TableDice dice = new TableDice(() -> {
        var current = view();
        if (current != null) for (int i = 0; i < current.game().actions().size(); i++)
            if (current.game().actions().get(i).type() == SichuanAction.Type.PICK_UP_DICE) { send(current, i); return; }
    });
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
    @Override public void renderBackground(GuiGraphics graphics) {}
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
        clearWidgets();
        turnClock = null;
        decisionControls.clear();
        var view = view();
        if (view == null || table().clientTableRoom() == null) return;
        var room = table().clientTableRoom(); var game = view.game();
        if (shownRevision != view.revision()) pending = false;
        shownRevision = view.revision();
        if (decision != game.decision()) {
            handlingDrag.clear();
            decision = game.decision(); selected = hovered = lastClicked = Tile.ABSENT;
        }
        hand = null;
        board = immersive() ? new TableBoard(TableBoardState.live(game), 20, 1260, 68, 620, 800, true) : null;
        if (immersive()) {
            var player = game.seats().get(game.viewerSeat());
            var tiles = new ArrayList<>(player.hand());
            if (player.drawn() >= 0 && tiles.remove(Integer.valueOf(player.drawn()))) tiles.add(player.drawn());
            hand = new TableHand(tiles, player.drawn(), player.melds(), 0, game.viewerSeat(), TableCanvas.WIDTH, TableCanvas.HEIGHT - 60, 58, true, top.skyeyefast.mchjong.engine.MahjongVariant.SICHUAN);
        }
        if (room.exitVote() != null) {
            TableExitControls.voteButtons(pos, room, uiWidth(), uiHeight(), contentScale()).forEach(this::addRenderableWidget);
            return;
        }
        turnClock = addRenderableWidget(new TableTurnClock());
        TableToolbar.build(this, pos, room, uiWidth(), immersive(), this::toggleView)
            .forEach(this::addRenderableWidget);
        addRenderableWidget(dice);
        // As at Riichi mahjong tables, seated payments use the drawer button on the results screen.
        if (immersive() && game.handling() != null && game.viewerSeat() >= 0)
            addRenderableWidget(TableToolbar.drawer(pos, game.viewerSeat(), 16, 16, 2, handlingDrag::clear));
        automation.build(uiWidth(), immersive() ? uiHeight() - 32 : uiHeight() - 17, immersive()).forEach(this::addRenderableWidget);
        automation.restoreFocus(automationFocus);
        var choices = new ArrayList<Integer>();
        if (game.phase() == top.skyeyefast.mchjong.engine.SichuanGame.Phase.VOIDING && discardAction(selected) >= 0)
            choices.add(discardAction(selected));
        for (int actionIndex = 0; actionIndex < game.actions().size(); actionIndex++)
            if (game.actions().get(actionIndex).type() != SichuanAction.Type.DISCARD
                && game.actions().get(actionIndex).type() != SichuanAction.Type.DRAW
                && (immersive() || game.handling() == null || switch (game.actions().get(actionIndex).type()) {
                    case SHUFFLE, BUILD_WALL, TAKE_PACKET, PICK_UP_DICE -> false;
                    default -> true;
                })
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
    }
    private void send(SichuanSession.View snapshot, int index) {
        var current = view();
        if (pending || current == null || snapshot == null || current.paused() || minecraft.getConnection() == null
            || !snapshot.tableId().equals(current.tableId()) || !snapshot.incarnation().equals(current.incarnation())
            || snapshot.game().decision() != current.game().decision() || index < 0 || index >= current.game().actions().size()
            || table().clientTableRoom().exitVote() != null) return;
        pending = true;
        minecraft.getConnection().send(PayloadPackets.serverbound(new TableActionPayload(pos, current.tableId(),
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
        int index = discardAction(tile);
        if (index < 0) return;
        if (view().game().phase() == top.skyeyefast.mchjong.engine.SichuanGame.Phase.VOIDING) {
            selected = tile;
            rebuild(); setFocused(null);
        } else {
            long now = Util.getMillis();
            if (TableSettings.get().discardMode == TableSettings.DiscardMode.SINGLE_CLICK
                || lastClicked == tile && now - lastClickAt <= 450) send(view(), index);
            else {
                selected = lastClicked = tile; lastClickAt = now;
                rebuild(); setFocused(null);
            }
        }
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
        if (!immersive() && view() != null && TableSettings.get().highlightTiles) {
            if (handlingDrag.active()) return handlingOffset(piece).lengthSqr() > 0 ? MahjongUi.POSITIVE : 0;
            if (TableHandling.context(view().game()).source(piece.area().name(), piece.seat(), piece.index(), piece.tile())) return MahjongUi.ACCENT;
        }
        if (immersive() || !TableSettings.get().highlightTiles || !ownHand(piece)) return 0;
        int focus = hovered;
        return piece.tile() == selected ? MahjongUi.ACCENT : piece.tile() == focus ? MahjongUi.POSITIVE : 0;
    }
    private boolean overWidget(double positionX, double positionY) {
        return children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .anyMatch(widget -> widget.visible
                && positionX >= widget.getX() && positionX < (widget.getX() + widget.getWidth()) && positionY >= widget.getY() && positionY < (widget.getY() + widget.getHeight()));
    }
    private SeatedTableProjection projection() { return SeatedTableProjection.capture(pos, width, height, framePartial); }
    public net.minecraft.world.phys.Vec3 handlingOffset(SichuanTableScene.Piece piece) {
        return handlingDrag.offset(piece.area().name(), piece.seat(), piece.index(), piece.tile(), piece.position());
    }
    private net.minecraft.world.phys.Vec3 tablePoint(double x, double y) {
        var pointer = projection().pointer(x, y);
        double distance = (top.skyeyefast.mchjong.world.TableGeometry.FELT_Y + .05 - pointer.origin().y) / pointer.ray().y;
        return distance > 0 && Double.isFinite(distance) ? pointer.origin().add(pointer.ray().scale(distance)) : null;
    }
    private boolean beginHandling(double x, double y) {
        if (immersive() || pending || view() == null || view().paused() || overWidget(x, y)) return false;
        var context = TableHandling.context(view().game());
        if (context.action() < 0) return false;
        var pointer = projection().pointer(x, y);
        double nearest = Double.POSITIVE_INFINITY;
        SichuanTableScene.Piece picked = null;
        var scene = SichuanTableScene.build(view().game());
        for (var piece : scene) {
            double distance = TilePicking.distanceSquared(TableAnimation.of(table()).worldPose(piece, Util.getMillis()), piece.dimensions(), pointer.origin(), pointer.ray(), false);
            if (distance < nearest) { nearest = distance; picked = piece; }
        }
        if (picked == null) return false;
        var hit = picked;
        // A lone lower tile can hide behind the next full stack, so that neighbouring stack also starts the drag.
        if (!context.source(hit.area().name(), hit.seat(), hit.index(), hit.tile()) && (hit.area() != SichuanTableScene.Area.WALL
            || scene.stream().noneMatch(piece -> context.source(piece.area().name(), piece.seat(), piece.index(), piece.tile())
                && piece.position().distanceToSqr(hit.position()) < .03))) return false;
        handlingDecision = view().game().decision();
        handlingDrag.begin(context, tablePoint(x, y));
        setFocused(null);
        return true;
    }
    private int pick(double positionX, double positionY) {
        var view = view();
        if (view == null || table().clientTableRoom().exitVote() != null || overWidget(positionX, positionY)) return Tile.ABSENT;
        if (hand != null) return hand.pick(positionX, positionY, selected);
        if (immersive()) return Tile.ABSENT;
        var pointer = projection().pointer(positionX, positionY);
        double closest = Double.POSITIVE_INFINITY; int tile = Tile.ABSENT;
        for (var piece : SichuanTableScene.build(view.game())) if (ownHand(piece)) {
            double distance = TilePicking.distanceSquared(TableAnimation.of(table()).worldPose(piece, Util.getMillis()), piece.dimensions(), pointer.origin(), pointer.ray(), selected(piece));
            if (distance < closest) { closest = distance; tile = piece.tile(); }
        }
        return tile;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        framePartial = partialTick;
        var view = view(); if (view == null || table().clientSichuanDeck() == null) return;
        if (view.revision() != shownRevision) rebuild();
        if (turnClock != null) {
            var clock = view.clocks().get(view.game().viewerSeat()).after(table().clientViewAgeMillis());
            int textScale = contentScale(), bottom = clockBottom();
            int nextTop = bottom - (clock.active() ? 28 * contentScale() : 0) - actionHeight;
            for (var widget : decisionControls) widget.setY(widget.getY() + nextTop - actionTop);
            actionTop = nextTop;
            turnClock.update(clock, uiWidth() - 16 * textScale, bottom, textScale);
        }
        var transform = canvas();
        int mx = (int) Math.floor(transform.localX(mouseX)), my = (int) Math.floor(transform.localY(mouseY));
        transform.begin(graphics);
        try {
            var dicePoint = immersive() ? null : projection().project(new net.minecraft.world.phys.Vec3(0, top.skyeyefast.mchjong.world.TableGeometry.FELT_Y + .075, -.06), .01);
            int diceWidth = dicePoint == null ? 24 : Math.max(24, (int) (dicePoint.scale() * .24));
            dice.update(view.game(), dicePoint == null ? -100 : (int) dicePoint.x() - diceWidth / 2,
                dicePoint == null ? -100 : (int) dicePoint.y() - 10, diceWidth, 20, !immersive(), pending);
            if (!immersive() && handlingDrag.active()) TableHandling.renderTarget(graphics,
                projection().project(TableHandling.context(view.game()).destination(), .01), width, mx, my);
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
                int focus = hovered;
                hand.render(graphics, selected, focus, tile -> tile == selected ? MahjongUi.ACCENT : 0, TableAnimation.of(table()).handSuppressed(Util.getMillis()),
                    deck.preset(), deck.material(), deck.back(), deck.backPreset(), TableAnimation.of(table()), board.drawSource(), Util.getMillis(), tile -> TileMesh.artwork(deck.tile(tile)));
            }
            if (board != null) {
                var deck = table().clientSichuanDeck();
                TableAnimation.of(table()).render(graphics, board, hand, TableCanvas.HEIGHT - 60, Util.getMillis(),
                    deck.preset(), deck.material(), deck.back(), deck.backPreset(), tile -> TileMesh.artwork(deck.tile(tile)));
            }
            graphics.pose().pushPose(); graphics.pose().translate(0, 0, 600);
            TableExitControls.renderVote(graphics, font, table().clientTableRoom(), uiWidth(), uiHeight(), textScale);
            super.render(graphics, mx, my, partialTick);
            if (table().clientTableRoom().exitVote() == null && (turnClock == null || !turnClock.visible)
                && TableSettings.get().show(TableSettings.Information.HELP)) {
                var help = Component.translatable(view.game().handling() != null && TableHandling.context(view.game()).action() >= 0
                    ? TableHandling.context(view.game()).help() : "ui.mchjong.help." + TableSettings.get().discardMode.name().toLowerCase(java.util.Locale.ROOT));
                graphics.pose().pushPose(); graphics.pose().scale(textScale, textScale, 1);
                MahjongUi.text(graphics, font, help, immersive() ? 106 : 8, uiHeight() / textScale - 11,
                    uiWidth() / textScale - (immersive() ? 118 : 16), MahjongUi.MUTED, false);
                graphics.pose().popPose();
            }
            if (hovered >= 0 && table().clientTableRoom().exitVote() == null) graphics.renderTooltip(font, tileLabel(hovered), mx, my);
            dice.renderTooltip(graphics, mx, my);
            graphics.pose().popPose();
        } finally { transform.end(graphics); }
    }
    private void renderSeats(GuiGraphics g, SichuanSession.View view, int mouseX, int mouseY) {
        var deck = table().clientSichuanDeck();
        TableHud.render(font, g, TableBoardState.live(view.game()), table().clientTableRoom(), view.game().seats().stream().map(SichuanTableScene::status).toList(),
            uiWidth(), uiHeight(), immersive(), TableHud.exclusions(this, privateHandBounds(), null, board),
            deck.preset(), deck.material(), deck.back(), deck.backPreset(), mouseX, mouseY, tile -> TileMesh.artwork(deck.tile(tile)));
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
                .rotateY((float) Math.toRadians(pose.yaw())).rotateX((float) Math.toRadians(pose.pitch()));
            for (int positionX = -1; positionX <= 1; positionX += 2) for (int positionY = -1; positionY <= 1; positionY += 2) for (int z = -1; z <= 1; z += 2) {
                var corner = transform.transformPosition(new org.joml.Vector3f(positionX * piece.dimensions().width() / 2,
                    positionY * piece.dimensions().height() / 2, z * piece.dimensions().depth() / 2));
                var point = projection.project(new net.minecraft.world.phys.Vec3(corner.x, corner.y + .035, corner.z), .01);
                if (point != null) { top = Math.min(top, point.y()); bottom = Math.max(bottom, point.y()); }
            }
        }
        if (!Double.isFinite(top) || top >= height || bottom <= 0 || top < 196 && bottom + 90 <= height - 24)
            return height - 24;
        return Math.max(190, Math.min(height - 24, (int) top - 6));
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
    private void toggleView() { handlingDrag.clear(); presentation.toggle(); rebuild(); }
    public void resetView() { presentation.reset(); }
    @Override public void tick() { presentation.tick(); }
    private void cancelSelection() { selected = lastClicked = Tile.ABSENT; rebuild(); }
    @Override public boolean mouseClicked(double positionX, double positionY, int button) {
        if (!canvas().contains(positionX, positionY)) return false;
        positionX = canvasX(positionX); positionY = canvasY(positionY);
        if (super.mouseClicked(positionX, positionY, button)) return true;
        if (button == 1) {
            if (presentation.cameraEnabled()) presentation.startDrag();
            else cancelSelection();
            return true;
        }
        if (button == 0) {
            if (beginHandling(positionX, positionY)) return true;
            int tile = pick(positionX, positionY); if (tile >= 0) { choose(tile); return true; }
        }
        return false;
    }
    @Override public boolean mouseReleased(double positionX, double positionY, int button) {
        if (button == 0 && handlingDrag.active()) {
            double localX = canvasX(positionX), localY = canvasY(positionY);
            int action = handlingDrag.release(overWidget(localX, localY) ? null : tablePoint(localX, localY));
            if (view() != null && view().game().decision() == handlingDecision && action >= 0) send(view(), action);
            return true;
        }
        if (presentation.releaseDrag(button, this::cancelSelection)) return true;
        return super.mouseReleased(canvasX(positionX), canvasY(positionY), button);
    }
    @Override public boolean mouseDragged(double positionX, double positionY, int button, double dx, double dy) {
        if (button == 0 && handlingDrag.active()) { handlingDrag.move(tablePoint(canvasX(positionX), canvasY(positionY))); return true; }
        if (presentation.drag(button, dx, dy, hasShiftDown())) return true;
        double scale = canvas().scale();
        return super.mouseDragged(canvasX(positionX), canvasY(positionY), button, dx / scale, dy / scale);
    }
    @Override public boolean mouseScrolled(double positionX, double positionY, double vertical) {
        if (!canvas().contains(positionX, positionY)) return false;
        positionX = canvasX(positionX); positionY = canvasY(positionY);
        if (!overWidget(positionX, positionY) && presentation.scroll(vertical, hasShiftDown())) return true;
        return super.mouseScrolled(positionX, positionY, vertical);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && handlingDrag.active()) { handlingDrag.clear(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE && selected >= 0) { cancelSelection(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
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
            MahjongUi.text(graphics, font, getMessage(), 0, 5, textWidth, captionColor(), false);
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
