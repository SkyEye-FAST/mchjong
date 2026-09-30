package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.McrReplayPlayback;
import top.skyeyefast.mchjong.engine.McrSettlement;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.item.McrDeck;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

/** Read-only MCR table, advanced one recorded event at a time. */
public final class McrReplayScreen extends Screen {
    private static final McrDeck DECK = new McrDeck(TileMaterial.BONE, DyeColor.GREEN,
        TileFacePreset.KANSAI, TileBackPresets.DEFAULT);
    private final Screen parent;
    private final ReplayMatch match;
    private int handIndex;
    private int cursor;
    private int viewer;
    private int settlementPage;
    private McrReplayPlayback.Timeline timeline;
    private McrImmersiveTable board;
    private record ResultRow(Component text, int color) {}

    public McrReplayScreen(Screen parent, ReplayMatch match) {
        super(Component.translatable("replay.mchjong.title"));
        this.parent = parent;
        this.match = match;
    }

    public ReplayMatch match() { return match; }
    public int cursor() { return cursor; }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override protected void init() {
        clearWidgets();
        timeline = McrReplayPlayback.timeline(match, handIndex);
        cursor = Math.clamp(cursor, 0, timeline.frames().size() - 1);
        board = new McrImmersiveTable(viewer);
        int span = Math.min(240, Math.max(90, (width - 24) / 3));
        addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> changeHand(-1))
            .bounds(8, 7, 26, 20).build()).active = handIndex > 0;
        addRenderableWidget(MahjongButton.create(Component.translatable("mcr.mchjong.hand", handIndex + 1,
            timeline.frames().get(cursor).view().remaining()), ignored -> changeHand(1))
            .bounds(38, 7, span, 20).build()).active = handIndex + 1 < match.mcr().hands().size();
        addRenderableWidget(MahjongButton.create(Component.translatable("replay.mchjong.view", match.participants().get(viewer).name()),
            ignored -> changeViewer()).bounds(42 + span, 7, Math.min(160, width - span - 56), 20).build());
        int cell = Math.max(34, Math.min(104, (width - 32) / 4));
        int base = (width - (4 * cell + 12)) / 2;
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.back"), ignored -> onClose())
            .bounds(base, height - 28, cell, 20).build());
        addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> seek(cursor - 1))
            .bounds(base + cell + 4, height - 28, cell, 20).build()).active = cursor > 0;
        addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> seek(cursor + 1))
            .bounds(base + 2 * (cell + 4), height - 28, cell, 20).build()).active = cursor + 1 < timeline.frames().size();
        addRenderableWidget(MahjongButton.create(Component.literal((cursor + 1) + " / " + timeline.frames().size()), ignored -> seek(0))
            .bounds(base + 3 * (cell + 4), height - 28, cell, 20).build());
    }

    private void changeHand(int offset) {
        handIndex = Math.clamp(handIndex + offset, 0, match.mcr().hands().size() - 1);
        cursor = 0;
        settlementPage = 0;
        rebuildWidgets();
    }

    private void changeViewer() { viewer = (viewer + 1) % 4; board = new McrImmersiveTable(viewer); rebuildWidgets(); }
    private void seek(int step) {
        cursor = Math.clamp(step, 0, timeline.frames().size() - 1);
        settlementPage = 0;
        rebuildWidgets();
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> seek(cursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> seek(cursor + 1);
            case GLFW.GLFW_KEY_UP -> changeHand(-1);
            case GLFW.GLFW_KEY_DOWN -> changeHand(1);
            case GLFW.GLFW_KEY_HOME -> seek(0);
            case GLFW.GLFW_KEY_END -> seek(timeline.frames().size() - 1);
            case GLFW.GLFW_KEY_V -> changeViewer();
            case GLFW.GLFW_KEY_PAGE_UP -> settlementPage = Math.max(0, settlementPage - 1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> settlementPage++;
            default -> { return super.keyPressed(key, scan, modifiers); }
        }
        return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        var canvas = new TableCanvas(width, height, true);
        if (canvas.contains(x, y) && canvas.localX(x) >= 380 && canvas.localX(x) < 900
            && canvas.localY(y) >= 160 && canvas.localY(y) < 640
            && timeline.frames().get(cursor).view().result() != null) {
            settlementPage = Math.max(0, settlementPage + (vertical < 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var frame = timeline.frames().get(cursor);
        var view = frame.view();
        var canvas = new TableCanvas(width, height, true);
        canvas.begin(graphics);
        try {
            board.render(graphics, view, DECK, DyeColor.GREEN);
            for (int seat = 0; seat < 4; seat++) {
                int side = Math.floorMod(seat - viewer, 4);
                var card = TableCanvas.card(side);
                graphics.fill(card.left(), card.top(), card.right(), card.bottom(), seat == viewer ? MahjongUi.SELECTED : MahjongUi.SURFACE);
                if (seat == viewer) graphics.renderOutline(card.left(), card.top(), card.width(), card.height(), MahjongUi.ACCENT);
                text2x(graphics, Component.literal(match.participants().get(seat).name()),
                    card.left() + 6, card.top() + 4, card.width() - 12, MahjongUi.TEXT, false);
                text2x(graphics, Component.literal(Integer.toString(view.seats().get(seat).points())),
                    card.left() + 6, card.top() + 25, card.width() - 12, MahjongUi.MUTED, false);
            }
            if (view.result() != null) renderSettlement(graphics, view);
        } finally { canvas.end(graphics); }
        graphics.fill(0, 0, width, 54, MahjongUi.PANEL);
        graphics.fill(0, height - 32, width, height, MahjongUi.PANEL);
        var event = frame.event();
        Component caption = event == null ? Component.translatable("replay.mchjong.initial")
            : Component.literal(match.participants().get(event.seat()).name() + " · ").append(Component.translatable(switch (event.kind()) {
                case DRAW -> "mcr.mchjong.action.draw";
                case FLOWER_REPLACEMENT -> "mcr.mchjong.action.replace_flower";
                case DISCARD -> "mcr.mchjong.action.discard";
                case RESPONSE -> "mcr.mchjong.responded";
                case CHOW -> "mcr.mchjong.action.chow";
                case PUNG -> "mcr.mchjong.action.pung";
                case KONG -> "mcr.mchjong.action.melded_kong";
                case WIN -> "mcr.mchjong.action.win";
                case PASS -> "mcr.mchjong.action.pass";
                case WRONG_WIN -> "mcr.mchjong.win_forbidden";
                case SETTLEMENT -> "mcr.mchjong.results";
            }));
        MahjongUi.text(graphics, font, caption, 8, 31, width - 16, MahjongUi.ACCENT, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSettlement(GuiGraphics graphics, top.skyeyefast.mchjong.engine.McrView view) {
        int left = 380, top = 160, right = 900, bottom = 640;
        graphics.fill(left, top, right, bottom, 0xff18292e);
        graphics.renderOutline(left, top, right - left, bottom - top, MahjongUi.ACCENT);
        text2x(graphics, Component.translatable("mcr.mchjong.results"), left + 16, top + 16,
            right - left - 32, MahjongUi.ACCENT, true);
        var rows = new java.util.ArrayList<ResultRow>();
        if (view.result() instanceof McrSettlement.Win win) {
            rows.add(new ResultRow(Component.translatable("mcr.mchjong.winner",
                match.participants().get(win.winner()).name()), MahjongUi.TEXT));
            for (var fan : win.score().fans()) {
                rows.add(new ResultRow(Component.translatable("mcr.mchjong.fan." + fan.id().toLowerCase(java.util.Locale.ROOT))
                    .append(" ×" + fan.count() + "  " + fan.points()), MahjongUi.MUTED));
            }
            rows.add(new ResultRow(Component.translatable("mcr.mchjong.total_fan", win.score().totalFan()), MahjongUi.ACCENT));
        } else rows.add(new ResultRow(Component.translatable("mcr.mchjong.draw_result"), MahjongUi.TEXT));
        for (int seat = 0; seat < 4; seat++) {
            rows.add(new ResultRow(Component.literal(match.participants().get(seat).name() + "  "
                + view.result().deltas().get(seat) + "  →  " + view.seats().get(seat).points()), MahjongUi.TEXT));
        }
        for (var penalty : view.penalties()) if (penalty.handNumber() == view.handNumber())
            rows.add(new ResultRow(Component.translatable("mcr.mchjong.penalty",
                match.participants().get(penalty.offender()).name(), penalty.deltas().get(penalty.offender())), MahjongUi.NEGATIVE));
        int pageSize = 18;
        int pages = (rows.size() + pageSize - 1) / pageSize;
        settlementPage = Math.clamp(settlementPage, 0, pages - 1);
        for (int index = settlementPage * pageSize; index < Math.min(rows.size(), (settlementPage + 1) * pageSize); index++) {
            var row = rows.get(index);
            text2x(graphics, row.text(), left + 18, top + 46 + (index % pageSize) * 21,
                right - left - 36, row.color(), false);
        }
        if (pages > 1) text2x(graphics, Component.literal("PgUp / PgDn   " + (settlementPage + 1) + " / " + pages),
            left + 16, bottom - 27, right - left - 32, MahjongUi.MUTED, true);
    }

    private void text2x(GuiGraphics graphics, Component message, int x, int y, int width, int color, boolean centered) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(2, 2, 1);
        MahjongUi.text(graphics, font, message, 0, 0, width / 2, color, centered, false);
        graphics.pose().popPose();
    }
}
