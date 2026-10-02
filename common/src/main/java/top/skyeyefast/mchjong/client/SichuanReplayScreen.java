package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.SichuanReplayPlayback;
import top.skyeyefast.mchjong.item.SichuanDeck;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

public final class SichuanReplayScreen extends Screen {
    private static final SichuanDeck DECK = new SichuanDeck(TileMaterial.BONE, DyeColor.GREEN,
        TileFacePreset.KANSAI, TileBackPresets.DEFAULT);
    private final Screen parent;
    private final ReplayMatch match;
    private int handIndex, cursor, viewer, resultPage;
    private SichuanReplayPlayback.Timeline timeline;
    private SichuanImmersiveTable board;

    public SichuanReplayScreen(Screen parent, ReplayMatch match) {
        super(Component.translatable("replay.mchjong.title"));
        this.parent = parent; this.match = match;
    }
    public ReplayMatch match() { return match; }
    public int cursor() { return cursor; }
    public int handIndex() { return handIndex; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics) {}
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override protected void init() {
        clearWidgets();
        if (timeline == null) timeline = SichuanReplayPlayback.timeline(match, handIndex);
        board = new SichuanImmersiveTable(viewer);
        int span = Math.min(240, Math.max(90, (width - 24) / 3));
        addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> changeHand(-1))
            .bounds(8, 7, 26, 20).build()).active = handIndex > 0;
        addRenderableWidget(MahjongButton.create(Component.translatable("sichuan.mchjong.hand", handIndex + 1,
            match.sichuan().rules().matchHands(), 108 - timeline.frames().get(cursor).state().wall().cursor()),
            ignored -> changeHand(1)).bounds(38, 7, span, 20).build())
            .active = handIndex + 1 < match.handCount();
        addRenderableWidget(MahjongButton.create(Component.translatable("replay.mchjong.view", match.participants().get(viewer).name()),
            ignored -> { viewer = (viewer + 1) % 4; rebuildWidgets(); })
            .bounds(42 + span, 7, Math.min(160, width - span - 56), 20).build());
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
        if (timeline.frames().get(cursor).state().result() != null) {
            addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> resultPage = Math.max(0, resultPage - 1))
                .bounds(width / 2 - 30, height - 54, 26, 20).build());
            addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> resultPage++)
                .bounds(width / 2 + 4, height - 54, 26, 20).build());
        }
    }
    private void changeHand(int offset) {
        handIndex = net.minecraft.util.Mth.clamp(handIndex + offset, 0, match.handCount() - 1);
        cursor = 0; resultPage = 0; timeline = null; rebuildWidgets();
    }
    private void seek(int step) {
        cursor = net.minecraft.util.Mth.clamp(step, 0, timeline.frames().size() - 1); resultPage = 0; rebuildWidgets();
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> seek(cursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> seek(cursor + 1);
            case GLFW.GLFW_KEY_UP -> changeHand(-1);
            case GLFW.GLFW_KEY_DOWN -> changeHand(1);
            case GLFW.GLFW_KEY_HOME -> seek(0);
            case GLFW.GLFW_KEY_END -> seek(timeline.frames().size() - 1);
            case GLFW.GLFW_KEY_V -> { viewer = (viewer + 1) % 4; rebuildWidgets(); }
            case GLFW.GLFW_KEY_PAGE_UP -> resultPage = Math.max(0, resultPage - 1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> resultPage++;
            default -> { return super.keyPressed(key, scan, modifiers); }
        }
        return true;
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var frame = timeline.frames().get(cursor);
        var canvas = new TableCanvas(width, height, true);
        canvas.begin(graphics);
        try {
            board.renderReplay(graphics, frame, DECK, DyeColor.GREEN);
            for (int seat = 0; seat < 4; seat++) {
                var card = TableCanvas.card(Math.floorMod(seat - viewer, 4));
                graphics.fill(card.left(), card.top(), card.right(), card.bottom(), seat == viewer ? MahjongUi.SELECTED : MahjongUi.SURFACE);
                text2x(graphics, Component.literal(match.participants().get(seat).name()), card.left() + 6, card.top() + 4,
                    card.width() - 12, MahjongUi.TEXT);
                var state = SichuanTableScene.status(frame.seats().get(seat));
                if (seat == frame.state().wall().dealer()) state = Component.translatable("ui.mchjong.annotation", state, Component.translatable("sichuan.mchjong.dealer"));
                var status = Component.translatable("ui.mchjong.annotation", frame.scores().get(seat), state);
                text2x(graphics, status,
                    card.left() + 6, card.top() + 25, card.width() - 12, MahjongUi.MUTED);
            }
            if (frame.state().result() != null) renderResults(graphics, frame);
        } finally { canvas.end(graphics); }
        graphics.fill(0, 0, width, 54, MahjongUi.PANEL);
        graphics.fill(0, height - 32, width, height, MahjongUi.PANEL);
        var event = frame.event();
        Component caption = event == null ? Component.translatable("replay.mchjong.initial")
            : Component.translatable("replay.mchjong.player_action", match.participants().get(event.seat()).name(), Component.translatable(switch (event.kind()) {
                case VOID_SUIT, VOID_SUITS -> "sichuan.mchjong.action.void_suit";
                case DRAW -> "sichuan.mchjong.action.draw";
                case DISCARD -> "sichuan.mchjong.action.discard";
                case RESPONSE -> "sichuan.mchjong.responded";
                case PASS -> "sichuan.mchjong.action.pass";
                case PUNG -> "sichuan.mchjong.action.pung";
                case KONG -> "sichuan.mchjong.action." + frame.state().players().get(event.seat()).melds().stream()
                    .filter(meld -> meld.quad() && meld.tiles().contains(event.tile())).findFirst().map(meld -> switch (meld.type()) {
                        case CONCEALED_QUAD -> "concealed_kong";
                        case ADDED_QUAD -> "added_kong";
                        default -> "discard_kong";
                    }).orElseThrow();
                case ADDED_KONG -> "sichuan.mchjong.action.added_kong";
                case WIN -> "sichuan.mchjong.action.win";
                case PAYMENT -> "sichuan.mchjong.payment." + frame.state().ledger().get(event.ledgerId()).type().name().toLowerCase(Locale.ROOT);
                case SETTLEMENT -> "sichuan.mchjong.results";
            }));
        if (event != null && event.kind() == top.skyeyefast.mchjong.engine.SichuanReplayHand.Kind.SETTLEMENT)
            caption = Component.translatable("sichuan.mchjong.results", handIndex + 1);
        MahjongUi.text(graphics, font, caption, 8, 31, width - 16, MahjongUi.ACCENT, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    private void renderResults(GuiGraphics graphics, SichuanReplayPlayback.Frame frame) {
        int left = 380, top = 150, span = 520;
        graphics.fill(left, top, left + span, 630, MahjongUi.PANEL);
        graphics.renderOutline(left, top, span, 480, MahjongUi.ACCENT);
        var rows = new ArrayList<SichuanResults.Row>();
        if (match.complete() && handIndex + 1 == match.handCount()) {
            rows.add(new SichuanResults.Row(Component.translatable("sichuan.mchjong.match_end", match.handCount()), MahjongUi.ACCENT));
            var header = match.header();
            for (int seat = 0; seat < 4; seat++) rows.add(new SichuanResults.Row(Component.literal("#" + header.finalRanks().get(seat)
                + "  " + match.participants().get(seat).name() + "  " + frame.scores().get(seat)), MahjongUi.TEXT));
        }
        rows.addAll(SichuanResults.rows(frame.state().rules(), frame.state().result(), frame.seats(), frame.scores(), viewer,
            match.participants().stream().map(ReplayMatch.Participant::name).toList()));
        int pageSize = 18;
        int pages = Math.max(1, (rows.size() + pageSize - 1) / pageSize);
        resultPage = net.minecraft.util.Mth.clamp(resultPage, 0, pages - 1);
        for (int index = resultPage * pageSize; index < Math.min(rows.size(), (resultPage + 1) * pageSize); index++) {
            var row = rows.get(index);
            int y = top + 40 + index % pageSize * 22;
            if (row.tiles().isEmpty()) text2x(graphics, row.text(), left + 16, y, span - 32, row.color());
            else for (int tile = 0; tile < row.tiles().size(); tile++) TileGui.tile(graphics, row.tiles().get(tile),
                left + 16 + tile * 20, y, 18, false, false, false, false, DECK.preset(), DECK.material(), DECK.back(), DECK.backPreset());
        }
        text2x(graphics, Component.translatable("ui.mchjong.annotation", Component.translatable("sichuan.mchjong.results", handIndex + 1), (resultPage + 1) + "/" + pages),
            left + 16, top + 12, span - 32, MahjongUi.ACCENT);
    }
    private void text2x(GuiGraphics graphics, Component text, int x, int y, int span, int color) {
        graphics.pose().pushPose(); graphics.pose().translate(x, y, 0); graphics.pose().scale(2, 2, 1);
        MahjongUi.text(graphics, font, text, 0, 0, span / 2, color, false, false);
        graphics.pose().popPose();
    }
}
