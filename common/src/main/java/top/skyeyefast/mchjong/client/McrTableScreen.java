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
import top.skyeyefast.mchjong.engine.McrAction;
import top.skyeyefast.mchjong.engine.McrSession;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.network.McrActionPayload;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Seated world interaction and the fixed immersive canvas share server-issued MCR decisions. */
public final class McrTableScreen extends Screen {
    private final BlockPos pos;
    private long shownRevision = -1, decision = -1, lastClickAt;
    private int page, selected = Tile.ABSENT, hovered = Tile.ABSENT, lastClicked = Tile.ABSENT;
    private boolean pending;
    private final TableViewController presentation = new TableViewController();
    private float framePartial;
    private TableHand hand;
    private McrImmersiveTable board;
    private TableTurnClock turnClock;
    private final List<AbstractWidget> decisionControls = new ArrayList<>();
    private int actionTop;

    public McrTableScreen(BlockPos pos) { this(pos, false); }
    public McrTableScreen(BlockPos pos, boolean immersive) {
        super(Component.translatable("mcr.mchjong.title"));
        this.pos = pos.immutable(); presentation.immersive(immersive);
    }
    public BlockPos tablePos() { return pos; }
    public static McrTableScreen active(Screen screen) {
        if (screen instanceof McrTableScreen table) return table;
        return screen instanceof TableSettingsScreen settings ? settings.mcrTableScreen() : null;
    }
    public static boolean isOpen(Screen screen) {
        return active(screen) != null || screen instanceof McrResultsScreen || screen instanceof McrLobbyScreen;
    }
    public boolean immersive() { return presentation.immersive(); }
    public boolean inspecting() { return presentation.inspecting(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float partialTick) {}
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
    private McrSession.View view() { return table() == null ? null : table().clientMcrView(); }
    public void receivedView() { pending = false; rebuild(); }
    @Override protected void init() { rebuild(); }

    private void rebuild() {
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
            decision = game.decision(); selected = hovered = lastClicked = Tile.ABSENT; page = 0;
        }
        hand = null;
        board = immersive() ? new McrImmersiveTable(game.viewerSeat()) : null;
        if (immersive() && game.viewerSeat() >= 0) {
            var player = game.seats().get(game.viewerSeat());
            var tiles = new ArrayList<>(player.hand());
            if (player.drawn() >= 0 && tiles.remove(Integer.valueOf(player.drawn()))) tiles.add(player.drawn());
            hand = new TableHand(tiles, player.drawn(), List.of(), game.viewerSeat(), TableCanvas.WIDTH, TableCanvas.HEIGHT - 20, 52, true);
        }
        if (room.exitVote() != null) {
            TableExitControls.voteButtons(pos, room, uiWidth(), uiHeight(), contentScale()).forEach(this::addRenderableWidget);
            return;
        }
        int s = contentScale(), w = uiWidth();
        turnClock = addRenderableWidget(new TableTurnClock());
        int x = toolbar("ui.mchjong.view_" + (immersive() ? "seated" : "immersive"), 8, this::toggleView);
        toolbar("settings.mchjong.title", x, () -> minecraft.setScreen(new TableSettingsScreen(this)));
        if (room.viewerSeat() >= 0) addRenderableWidget(new MahjongButton(w - 60 * s, 6, 52 * s, 20 * s,
            Component.translatable("ui.mchjong.exit"), ignored -> TableExitControls.send(pos, room,
                TableSessionControlPayload.Operation.REQUEST_EXIT, room.decision(), false)).textScale(s));
        var choices = new ArrayList<Integer>();
        if (discardAction(selected) >= 0) choices.add(discardAction(selected));
        for (int i = 0; i < game.actions().size(); i++)
            if (game.actions().get(i).type() != McrAction.Type.DISCARD
                && game.actions().get(i).type() != McrAction.Type.DRAW
                && game.actions().get(i).type() != McrAction.Type.REPLACE_FLOWER) choices.add(i);
        int pages = Math.max(1, (choices.size() + 3) / 4);
        page = Math.min(page, pages - 1);
        int cell = immersive() ? 224 : Math.min(110, (w - 28) / 3);
        int actionY = immersive() ? 562 : uiHeight() - 112;
        actionTop = actionY;
        for (int slot = 0; slot < 4 && page * 4 + slot < choices.size(); slot++) {
            int index = choices.get(page * 4 + slot);
            var button = new ActionButton(w - 8 - (2 - slot % 2) * (cell + 4), actionY + slot / 2 * 28 * s,
                cell, 26 * s, game.actions().get(index), () -> send(view, index));
            button.active = !pending && !view.paused(); addRenderableWidget(button);
            decisionControls.add(button);
        }
        if (pages > 1) {
            decisionControls.add(addRenderableWidget(new MahjongButton(w - 68 * s, actionY - 22 * s, 28 * s, 20 * s,
                Component.literal("‹"), ignored -> { page = Math.floorMod(page - 1, pages); rebuild(); }).textScale(s)));
            decisionControls.add(addRenderableWidget(new MahjongButton(w - 36 * s, actionY - 22 * s, 28 * s, 20 * s,
                Component.literal("›"), ignored -> { page = (page + 1) % pages; rebuild(); }).textScale(s)));
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
    public boolean selected(McrTableScene.Piece piece) { return !immersive() && ownHand(piece) && piece.tile() == selected; }
    private boolean ownHand(McrTableScene.Piece piece) {
        return view() != null && piece.area() == McrTableScene.Area.HAND && piece.seat() == view().game().viewerSeat();
    }
    public int highlight(McrTableScene.Piece piece) {
        if (immersive() || !TableSettings.get().highlightTiles || !ownHand(piece)) return 0;
        int focus = getFocused() instanceof HandTarget target ? target.tile : hovered;
        return piece.tile() == selected ? MahjongUi.ACCENT : piece.tile() == focus ? MahjongUi.POSITIVE : 0;
    }
    private boolean overWidget(double x, double y) {
        return children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .anyMatch(widget -> !(widget instanceof HandTarget) && widget.visible
                && x >= widget.getX() && x < widget.getRight() && y >= widget.getY() && y < widget.getBottom());
    }
    private SeatedTableProjection projection() { return SeatedTableProjection.capture(pos, width, height, framePartial); }
    private int pick(double x, double y) {
        var view = view();
        if (view == null || view.game().viewerSeat() < 0 || table().clientTableRoom().exitVote() != null || overWidget(x, y)) return Tile.ABSENT;
        if (hand != null) return hand.pick(x, y, selected);
        if (immersive()) return Tile.ABSENT;
        var pointer = projection().pointer(x, y);
        double closest = Double.POSITIVE_INFINITY; int tile = Tile.ABSENT;
        for (var piece : McrTableScene.build(view.game())) if (ownHand(piece)) {
            double distance = TilePicking.distanceSquared(piece, pointer.origin(), pointer.ray(), selected(piece));
            if (distance < closest) { closest = distance; tile = piece.tile(); }
        }
        return tile;
    }
    private TableHand.Point point(int tile) {
        if (hand != null) return hand.point(tile, selected, hovered);
        var view = view(); if (view == null) return null;
        for (var piece : McrTableScene.build(view.game())) if (ownHand(piece) && piece.tile() == tile) {
            var point = projection().project(piece.position().add(0, selected(piece) ? .035 : 0, 0), .01);
            return point == null ? null : new TableHand.Point((int) point.x(), (int) point.y());
        }
        return null;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        framePartial = partialTick;
        var view = view(); if (view == null || table().clientMcrDeck() == null) return;
        if (view.revision() != shownRevision) rebuild();
        if (turnClock != null && view.game().viewerSeat() >= 0) {
            var clock = view.clocks().get(view.game().viewerSeat()).after(table().clientViewAgeMillis());
            int s = contentScale(), bottom = clockBottom();
            int nextTop = bottom - 84 * s;
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
                board.render(g, view.game(), table().clientMcrDeck(), table().clientMcrCloth());
            }
            hovered = transform.contains(mouseX, mouseY) ? pick(mx, my) : Tile.ABSENT;
            int s = contentScale();
            g.pose().pushPose(); g.pose().translate(0, 0, 400); g.pose().scale(s, s, 1);
            int w = uiWidth() / s;
            g.drawCenteredString(font, Component.translatable("mcr.mchjong.hand", view.game().handNumber(), view.game().remaining()),
                w / 2, immersive() ? 170 : 32, MahjongUi.TEXT);
            if (view.paused()) g.drawCenteredString(font, Component.translatable("mcr.mchjong.paused"), w / 2,
                immersive() ? 183 : 43, MahjongUi.NEGATIVE);
            else if (view.game().responded()) g.drawCenteredString(font, Component.translatable("mcr.mchjong.responded"),
                w / 2, immersive() ? 183 : 43, MahjongUi.MUTED);
            else if (view.game().phase() == top.skyeyefast.mchjong.engine.McrGame.Phase.INITIAL_FLOWERS
                || view.game().phase() == top.skyeyefast.mchjong.engine.McrGame.Phase.REPLACE_FLOWER)
                g.drawCenteredString(font, Component.translatable("mcr.mchjong.flower_replacement",
                    view.participants().get(view.game().turn()).name()), w / 2, immersive() ? 183 : 43, MahjongUi.ACCENT);
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
            if (table().clientTableRoom().exitVote() == null && (turnClock == null || !turnClock.visible)
                && TableSettings.get().show(TableSettings.Information.HELP)) {
                var help = Component.translatable("mcr.mchjong.help." + TableSettings.get().discardMode.name().toLowerCase(java.util.Locale.ROOT),
                    TableKeys.VIEW.getTranslatedKeyMessage(), TableKeys.PASS.getTranslatedKeyMessage());
                g.pose().pushPose(); g.pose().scale(s, s, 1);
                MahjongUi.text(g, font, help, immersive() ? 106 : 8, uiHeight() / s - 11,
                    uiWidth() / s - (immersive() ? 118 : 16), MahjongUi.MUTED, false);
                g.pose().popPose();
            }
            if (hovered >= 0 && table().clientTableRoom().exitVote() == null) g.renderTooltip(font, tileLabel(hovered), mx, my);
            g.pose().popPose();
        } finally { transform.end(g); }
    }
    private void renderSeats(GuiGraphics g, McrSession.View view, int mouseX, int mouseY) {
        int viewer = Math.max(0, view.game().viewerSeat());
        for (int seat = 0; seat < 4; seat++) {
            int side = Math.floorMod(seat - viewer, 4), s = contentScale();
            int cardWidth = immersive() ? 180 : Math.min(120, width / 3), x, y;
            if (immersive()) { var rect = TableCanvas.card(side); x = rect.left(); y = rect.top(); }
            else { x = side == 1 || side == 2 ? width - cardWidth - 8 : 8; y = side == 0 || side == 1 ? 82 : 54; }
            var player = view.game().seats().get(seat); var participant = view.participants().get(seat);
            int cardHeight = (immersive() ? 34 : 24) * s;
            g.fill(x, y, x + cardWidth, y + cardHeight, MahjongUi.PANEL);
            if (view.game().turn() == seat) g.fill(x, y, x + 2 * s, y + cardHeight, MahjongUi.ACCENT);
            PlayerPortrait.draw(g, participant, x + 4, y + 4, 10 * s);
            g.pose().pushPose(); g.pose().translate(x + 4, y + 4, 0); g.pose().scale(s, s, 1);
            MahjongUi.text(g, font, Component.literal(participant.name()), 14, 0, cardWidth / s - 20, MahjongUi.TEXT, false);
            var wind = Component.translatable("wind.mchjong." + new String[]{"east", "south", "west", "north"}[player.wind() - Tile.EAST]);
            MahjongUi.text(g, font, wind.copy().append("  " + player.points()), 0, 11, cardWidth / s - 8, MahjongUi.TEXT, false);
            var state = Component.translatable(player.winForbidden() ? "mcr.mchjong.win_forbidden" : "mcr.mchjong.flowers", player.flowers().size());
            if (immersive()) MahjongUi.text(g, font, state, 0, 22, cardWidth / s - 8,
                player.winForbidden() ? MahjongUi.NEGATIVE : MahjongUi.MUTED, false);
            g.pose().popPose();
            if (!immersive() && player.winForbidden()) g.fill(x, y + cardHeight - 2, x + cardWidth, y + cardHeight, MahjongUi.NEGATIVE);
            if (mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight)
                g.renderTooltip(font, Component.literal(participant.name()).append(" · ").append(wind).append(" · " + player.points())
                    .append(" · ").append(state), mouseX, mouseY);
        }
    }
    private int clockBottom() {
        if (hand != null) return hand.top() - 12;
        var projection = projection();
        double top = Double.POSITIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        for (var piece : McrTableScene.build(view().game())) if (ownHand(piece)) {
            var transform = new org.joml.Matrix4f().translation((float) piece.position().x,
                (float) piece.position().y, (float) piece.position().z)
                .rotateY((float) Math.toRadians(piece.yaw())).scale(piece.scale());
            for (int x = -1; x <= 1; x += 2) for (int y = -1; y <= 1; y += 2) for (int z = -1; z <= 1; z += 2) {
                var corner = transform.transformPosition(new org.joml.Vector3f(x * TileMesh.WIDTH / 2,
                    y * TileMesh.HEIGHT / 2, z * TileMesh.DEPTH / 2));
                var point = projection.project(new net.minecraft.world.phys.Vec3(corner.x, corner.y + .035, corner.z), .01);
                if (point != null) { top = Math.min(top, point.y()); bottom = Math.max(bottom, point.y()); }
            }
        }
        if (!Double.isFinite(top) || top >= height || bottom <= 0 || top < 196 && bottom + 90 <= height - 24)
            return height - 24;
        return Math.max(190, Math.min(height - 24, (int) top - 6));
    }
    private void toggleView() { presentation.toggle(); rebuild(); }
    public void resetView() { presentation.reset(); }
    @Override public void tick() { presentation.tick(); }
    private void cancelSelection() { selected = lastClicked = Tile.ABSENT; rebuild(); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (presentation.mouseBinding(button, this::toggleView, this::resetView)) return true;
        if (TableKeys.PASS.matchesMouse(button)) { pass(); return true; }
        if (!canvas().contains(x, y)) return false;
        x = canvasX(x); y = canvasY(y);
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 1) {
            if (presentation.cameraEnabled()) presentation.startDrag();
            else cancelSelection();
            return true;
        }
        if (button == 0) { int tile = pick(x, y); if (tile >= 0) { choose(tile); return true; } }
        return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (presentation.releaseInspect(button)) return true;
        if (presentation.releaseDrag(button, this::cancelSelection)) return true;
        return super.mouseReleased(canvasX(x), canvasY(y), button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (presentation.drag(button, dx, dy, hasShiftDown())) return true;
        double scale = canvas().scale();
        return super.mouseDragged(canvasX(x), canvasY(y), button, dx / scale, dy / scale);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!canvas().contains(x, y)) return false;
        x = canvasX(x); y = canvasY(y);
        if (!overWidget(x, y) && presentation.scroll(vertical, hasShiftDown())) return true;
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private void pass() {
        var view = view();
        if (view != null) for (int i = 0; i < view.game().actions().size(); i++)
            if (view.game().actions().get(i).type() == McrAction.Type.PASS) { send(view, i); return; }
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (presentation.keyPressed(key, scanCode, this::toggleView, this::resetView)) return true;
        if (TableKeys.PASS.matches(key, scanCode)) { pass(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE && selected >= 0) { selected = lastClicked = Tile.ABSENT; rebuild(); return true; }
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
