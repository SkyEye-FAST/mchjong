package top.skyeyefast.mchjong.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Settlement stays on the same table canvas, with shared receipt, points and standings tabs. */
abstract class TableResultsScreen extends Screen {
    record Presentation(long revision, TableBoardState board, TableResultState result, TableRoomView room,
                        TileFacePreset preset, TileMaterial material, DyeColor back, ResourceLocation backPreset,
                        DyeColor cloth, java.util.function.IntUnaryOperator artwork, boolean finished) {}
    private final BlockPos pos;
    private final TableViewController camera = new TableViewController();
    protected boolean pending;
    private long revision = -1;
    private final long started = Util.getMillis();
    private boolean expanded = true;
    private TableResults.Page page = TableResults.Page.HAND;
    private TableResults results;
    private int winner;
    private ResultReadout readout;
    TableResultsScreen(BlockPos pos, boolean immersive, Component title) {
        super(title); this.pos = pos.immutable(); camera.immersive(immersive);
    }
    public BlockPos tablePos() { return pos; }
    public boolean immersive() { return camera.immersive(); }
    public void resetView() { camera.reset(); }
    protected MahjongTableBlockEntity table() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity t ? t : null;
    }
    protected abstract Presentation snapshot();
    protected abstract void nextHandButton(int width, int height, int scale);
    protected Component progressText() { return Component.empty(); }
    public void receivedView() { pending = false; rebuild(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float partial) {}
    @Override public void removed() { camera.clearInput(); }
    @Override public void tick() { camera.tick(); if (readout != null) readout.tick(Util.getMillis(), false); }
    private TableCanvas canvas() { return camera.canvas(width, height); }
    @Override protected void init() { rebuild(); }
    private void toggleView() { camera.toggle(); rebuild(); }
    protected final void rebuild() {
        if (results != null) winner = results.selectedWinner();
        results = null; clearWidgets();
        var p = snapshot(); if (p == null) return;
        if (revision != p.revision()) pending = false;
        revision = p.revision();
        int w = canvas().width(), h = canvas().height(), s = immersive() ? 2 : 1;
        if (p.room().exitVote() != null) {
            TableExitControls.voteButtons(pos, p.room(), w, h, s).forEach(this::addRenderableWidget); return;
        }
        TableToolbar.build(this, pos, p.room(), w, immersive(), this::toggleView).forEach(this::addRenderableWidget);
        int span = Math.min(w - 20 * s, 520 * s), left = (w - span) / 2;
        if (TableSettings.get().show(TableSettings.Information.RESULTS)) {
            addRenderableWidget(new MahjongButton(10 * s, h - 48 * s, 100 * s, 20 * s,
                Component.translatable(expanded ? "ui.mchjong.view_table" : "ui.mchjong.view_results"), ignored -> {
                    if (readout != null) readout.finish(Util.getMillis()); expanded = !expanded; rebuild(); }).textScale(s));
            if (expanded) {
                var pages = new java.util.ArrayList<TableResults.Page>();
                pages.add(TableResults.Page.HAND); pages.add(TableResults.Page.POINTS);
                if (p.finished()) pages.add(TableResults.Page.MATCH);
                if (!p.result().payments().isEmpty()) pages.add(TableResults.Page.PAYMENTS);
                int tabWidth = span / pages.size();
                for (int i = 0; i < pages.size(); i++) { var tab = pages.get(i);
                    addRenderableWidget(new MahjongButton(left + i * tabWidth, 34 * s, tabWidth - 3 * s, 20 * s,
                        Component.translatable("ui.mchjong.result_page." + tab.ordinal()), ignored -> {
                            if (readout != null) readout.finish(Util.getMillis()); page = tab; rebuild(); }).selected(tab == page).textScale(s));
                }
                int top = 58 * s, panelHeight = h - top - 54 * s;
                if (page == TableResults.Page.POINTS || page == TableResults.Page.MATCH) panelHeight = Math.min(panelHeight, (48 + p.result().seats().size() * 26) * s);
                if (readout == null) { readout = new ResultReadout(p.result(), started); if (!TableSettings.get().animations) readout.finish(started); }
                results = addRenderableWidget(new TableResults(font, p.result(), p.preset(), p.material(), p.back(), p.backPreset(),
                    left, top, span, panelHeight, winner, page, started, s).readout(readout).artwork(p.artwork()));
            }
        }
        if (p.finished()) {
            int action = p.room().actions().indexOf(new RoomAction(RoomAction.Type.RETURN_TO_LOBBY));
            if (action >= 0) addRenderableWidget(new MahjongButton((w - 260 * s) / 2, h - 26 * s, 260 * s, 20 * s,
                Component.translatable("action.mchjong.return_to_lobby"), ignored -> {
                    if (pending) return; pending = true; RoomLobbyControls.send(pos, p.room(), action); rebuild(); }).primary().textScale(s)).active = !pending;
        } else nextHandButton(w, h, s);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        var p = snapshot(); if (p == null) return;
        if (revision != p.revision()) rebuild();
        var c = canvas(); int mx = (int) c.localX(mouseX), my = (int) c.localY(mouseY), s = immersive() ? 2 : 1;
        c.begin(g);
        try {
            if (immersive()) {
                g.fill(0, 0, c.width(), c.height(), MahjongUi.INPUT);
                var board = new TableBoard(p.board(), 20, c.width() - 20, 68, c.height() - 112, c.height(), true);
                board.render(g, p.board(), p.preset(), -2, p.material(), p.back(), p.backPreset(), p.cloth(), null, 0, p.artwork());
            }
            TableHud.render(font, g, p.board(), p.room(), p.result().seats().stream().map(TableResultState.Seat::status).toList(),
                c.width(), immersive(), p.preset(), p.material(), p.back(), p.backPreset(), mx, my, p.artwork());
            g.pose().pushPose(); g.pose().translate(0, 0, 600);
            TableExitControls.renderVote(g, font, p.room(), c.width(), c.height(), s);
            TableHud.text(font, g, progressText(), c.width() / 4, c.height() - 38 * s, c.width() / 2, MahjongUi.ACCENT, s);
            super.render(g, mx, my, partial);
            g.pose().popPose();
        } finally { c.end(g); }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!canvas().contains(x, y)) return false;
        if (super.mouseClicked(canvas().localX(x), canvas().localY(y), button)) return true;
        if (button == 1 && camera.cameraEnabled()) { camera.startDrag(); return true; }
        return false;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (camera.releaseDrag(button, () -> {})) return true;
        return super.mouseReleased(canvas().localX(x), canvas().localY(y), button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return camera.drag(button, dx, dy, hasShiftDown()) || super.mouseDragged(canvas().localX(x), canvas().localY(y), button, dx / canvas().scale(), dy / canvas().scale());
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!canvas().contains(x, y)) return false;
        if (super.mouseScrolled(canvas().localX(x), canvas().localY(y), horizontal, vertical)) return true;
        return camera.scroll(vertical, hasShiftDown());
    }
}
