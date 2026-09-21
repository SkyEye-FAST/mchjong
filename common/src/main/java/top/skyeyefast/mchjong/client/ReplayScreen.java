package top.skyeyefast.mchjong.client;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.ReplayHand;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.ReplayPlayback;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Table-oriented replay viewer. Raw recorder details are hidden behind semantic replay steps. */
public final class ReplayScreen extends Screen {
    private static final String[] WINDS = {"east", "south", "west", "north"};
    private static final double[] SPEEDS = {0.5, 1.0, 2.0, 4.0};
    private static final TileFacePreset PRESET = TileFacePreset.KANSAI;
    private final Screen parent;
    private final ReplayMatch match;
    private int handIndex;
    private int cursor;
    private int viewer = -1;
    private int speedIndex = 1;
    private double playbackClock;
    private boolean playing;
    private boolean roundsOpen;
    private ReplayPlayback.Timeline playback;
    private TableBoard board;
    private TableHand viewerHand;
    private Timeline timeline;
    private Button play;
    private Button viewerButton;
    private Button speedButton;
    private Button roundButton;
    private RoundList rounds;
    private ReplayResultPanel result;
    private Component status = Component.empty();

    public ReplayScreen(Screen parent, ReplayMatch match) {
        super(Component.translatable("replay.mchjong.title"));
        this.parent = parent;
        this.match = match;
    }

    public ReplayMatch match() { return match; }
    public int cursor() { return cursor; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}

    private ReplayHand hand() { return match.hands().get(handIndex); }
    private ReplayPlayback.Frame frame() { return playback.frames().get(cursor); }
    private int steps() { return playback.frames().size() - 1; }

    @Override protected void init() {
        if (viewer < 0) viewer = localViewer();
        playback = ReplayPlayback.timeline(match, handIndex);
        cursor = Math.clamp(cursor, 0, steps());

        var previousHand = addRenderableWidget(MahjongButton.create(Component.literal("<"), ignored -> changeHand(-1))
            .bounds(10, 29, 30, 20).build());
        previousHand.active = handIndex > 0;
        int speedWidth = width < 420 ? 58 : 72;
        int viewWidth = width < 420 ? 78 : Math.min(150, Math.max(92, width / 4));
        int roundWidth = Math.clamp(width - 104 - speedWidth - viewWidth, 64, 150);
        roundButton = addRenderableWidget(MahjongButton.create(roundLabel(handIndex), ignored -> toggleRounds())
            .bounds(44, 29, roundWidth, 20).build());
        int nextX = 48 + roundButton.getWidth();
        var nextHand = addRenderableWidget(MahjongButton.create(Component.literal(">"), ignored -> changeHand(1))
            .bounds(nextX, 29, 30, 20).build());
        nextHand.active = handIndex + 1 < match.hands().size();

        speedButton = addRenderableWidget(MahjongButton.create(Component.empty(), ignored -> cycleSpeed())
            .bounds(width - 10 - speedWidth, 29, speedWidth, 20).build());
        viewerButton = addRenderableWidget(MahjongButton.create(Component.empty(), ignored -> cycleViewer(1))
            .bounds(width - 14 - speedWidth - viewWidth, 29, viewWidth, 20).build());

        timeline = addRenderableWidget(new Timeline());
        int span = (width - 36) / 5;
        addRenderableWidget(MahjongButton.create(Component.literal("|<"), ignored -> seek(0)).bounds(10, height - 52, span, 20).build());
        addRenderableWidget(MahjongButton.create(Component.literal("<"), ignored -> seek(cursor - 1)).bounds(14 + span, height - 52, span, 20).build());
        play = addRenderableWidget(MahjongButton.create(Component.empty(), ignored -> togglePlay()).bounds(18 + span * 2, height - 52, span, 20).build().primary());
        addRenderableWidget(MahjongButton.create(Component.literal(">"), ignored -> seek(cursor + 1)).bounds(22 + span * 3, height - 52, span, 20).build());
        addRenderableWidget(MahjongButton.create(Component.literal(">|"), ignored -> seek(steps())).bounds(26 + span * 4, height - 52, span, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.back"), ignored -> onClose())
            .bounds(10, height - 28, width / 2 - 14, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("replay.mchjong.export"), ignored -> export())
            .bounds(width / 2 + 4, height - 28, width / 2 - 14, 20).build());

        result = addRenderableWidget(new ReplayResultPanel(font, match, hand(), PRESET, 10, 67, width - 20,
            Math.max(72, height - 151), viewer));
        int listWidth = Math.min(240, width - 28);
        rounds = addRenderableWidget(new RoundList((width - listWidth) / 2, 54, listWidth, Math.min(190, Math.max(70, height - 146))));
        refresh();
    }

    private int localViewer() {
        if (minecraft != null && minecraft.player != null) {
            var id = minecraft.player.getUUID();
            for (int seat = 0; seat < match.participants().size(); seat++)
                if (match.participants().get(seat).id().equals(id)) return seat;
        }
        return Math.clamp(match.initialDealer(), 0, match.rules().players() - 1);
    }

    private void layoutFrame() {
        var frame = frame();
        int controlsTop = height - 78;
        viewerHand = new TableHand(frame.seats().get(viewer), viewer, width, controlsTop, height < 360 ? 17 : 21);
        int boardBottom = Math.max(120, viewerHand.top() - 5);
        board = new TableBoard(TableBoardState.replay(match, hand(), frame, viewer), 8, width - 8, 67, boardBottom, boardBottom);
        result.visible = result.active = frame.settled();
        result.setViewer(viewer);
    }

    private void refresh() {
        layoutFrame();
        timeline.sync();
        play.setMessage(Component.translatable(playing ? "replay.mchjong.pause" : "replay.mchjong.play"));
        viewerButton.setMessage(Component.translatable("replay.mchjong.view", match.participants().get(viewer).name()));
        speedButton.setMessage(Component.translatable("replay.mchjong.speed", speedText()));
        roundButton.setMessage(roundLabel(handIndex));
        rounds.visible = rounds.active = roundsOpen;
    }

    public void seek(int step) {
        playing = false;
        playbackClock = 0;
        cursor = Math.clamp(step, 0, steps());
        refresh();
    }

    private void advance() {
        if (cursor < steps()) cursor++;
        if (cursor >= steps()) playing = false;
        refresh();
    }

    private void changeHand(int offset) { changeHandTo(handIndex + offset); }

    private void changeHandTo(int index) {
        int next = Math.clamp(index, 0, match.hands().size() - 1);
        if (next == handIndex && playback != null) { roundsOpen = false; refresh(); return; }
        handIndex = next;
        cursor = 0;
        playing = false;
        playbackClock = 0;
        roundsOpen = false;
        rebuildWidgets();
    }

    private void toggleRounds() {
        roundsOpen = !roundsOpen;
        rounds.visible = rounds.active = roundsOpen;
        if (frame().settled()) result.active = !roundsOpen;
    }

    private void cycleViewer(int offset) {
        viewer = Math.floorMod(viewer + offset, match.rules().players());
        refresh();
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        refresh();
    }

    private String speedText() {
        double speed = SPEEDS[speedIndex];
        return speed == Math.rint(speed) ? Integer.toString((int) speed) : String.format(Locale.ROOT, "%.1f", speed);
    }

    private void togglePlay() {
        if (cursor == steps()) cursor = 0;
        playing = !playing;
        playbackClock = 0;
        refresh();
    }

    @Override public void tick() {
        if (!playing) return;
        playbackClock += SPEEDS[speedIndex] * 0.1;
        while (playing && playbackClock >= 1) {
            playbackClock -= 1;
            advance();
        }
    }

    private void export() {
        try {
            var path = ClientReplays.export(match);
            status = Component.translatable("replay.mchjong.exported");
            minecraft.gui.getChat().addMessage(Component.translatable("replay.mchjong.export_path", path.toString()));
        } catch (java.io.IOException | RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger("mchjong").error("Cannot export replay {}", match.id(), failure);
            status = Component.translatable("replay.mchjong.export_failed");
        }
    }

    private Component roundLabel(int index) {
        var replayHand = match.hands().get(index);
        int wind = Math.min(3, replayHand.round() / match.rules().players());
        return Component.translatable("ui.mchjong.round", Component.translatable("wind.mchjong." + WINDS[wind]),
            replayHand.round() % match.rules().players() + 1, replayHand.honba());
    }

    private Component eventName() {
        var frame = frame();
        if (frame.settled()) return Component.translatable("result.mchjong." + hand().result());
        if (frame.event() == null) return Component.translatable("replay.mchjong.initial");
        var event = frame.event();
        String key = switch (event.kind()) {
            case DRAW -> "replay.mchjong.draw";
            case DISCARD -> event.riichi() ? "action.mchjong.riichi" : "action.mchjong.discard";
            case NUKI -> "action.mchjong.nuki";
            case MELD -> "action.mchjong." + event.meld().type().name().toLowerCase(Locale.ROOT);
            case RIICHI -> "replay.mchjong.deposit";
            case DORA -> "ui.mchjong.result_indicators";
        };
        return event.seat() < 0 ? Component.translatable(key) : Component.literal(match.participants().get(event.seat()).name())
            .append(" · ").append(Component.translatable(key));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, width - 24);
        MahjongUi.text(graphics, font, status.getString().isEmpty() ? title : status, 12, 11, width - 24, MahjongUi.TEXT, true);
        MahjongUi.text(graphics, font, eventName(), 12, 54, width - 24, MahjongUi.ACCENT, true);
        if (!frame().settled()) {
            board.render(graphics, TableBoardState.replay(match, hand(), frame(), viewer), PRESET);
            renderPlayerCards(graphics);
            viewerHand.render(graphics, Tile.ABSENT, ignored -> 0, PRESET);
            renderDora(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayerCards(GuiGraphics graphics) {
        for (int seat = 0; seat < match.rules().players(); seat++) {
            var card = board.card(seat);
            var player = frame().seats().get(seat);
            graphics.fill(card.x(), card.y(), card.right(), card.bottom(), seat == viewer ? MahjongUi.SELECTED : MahjongUi.SURFACE);
            if (seat == viewer) graphics.renderOutline(card.x(), card.y(), card.width(), card.height(), MahjongUi.ACCENT);
            int inset = PlayerPortrait.draw(graphics, player, card.x() + 4, card.y() + 3, 10);
            MahjongUi.text(graphics, font, Component.literal(player.name()), card.x() + 5 + inset, card.y() + 4,
                card.width() - 9 - inset, MahjongUi.TEXT, false);
            if (board.scoresOnCards()) MahjongUi.text(graphics, font, Component.translatable("ui.mchjong.points", player.points()),
                card.x() + 5, card.y() + 17, card.width() - 10, MahjongUi.MUTED, false);
        }
    }

    private void renderDora(GuiGraphics graphics) {
        var dora = frame().dora();
        if (dora.isEmpty()) return;
        Component label = Component.translatable("ui.mchjong.dora_short");
        int tileWidth = 9;
        int span = font.width(label) + 6 + dora.size() * (tileWidth + 2);
        int x = width - 14 - span;
        graphics.drawString(font, label, x, 70, MahjongUi.MUTED, false);
        x += font.width(label) + 6;
        for (int tile : dora) {
            TileGui.tile(graphics, tile, x, 66, tileWidth, false, false, false, PRESET);
            x += tileWidth + 2;
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && !frame().settled() && board != null) {
            for (int seat = 0; seat < match.rules().players(); seat++) {
                var card = board.card(seat);
                if (mouseX >= card.x() && mouseX < card.right() && mouseY >= card.y() && mouseY < card.bottom()) {
                    viewer = seat;
                    refresh();
                    return true;
                }
            }
        }
        return false;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> seek(cursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> seek(cursor + 1);
            case GLFW.GLFW_KEY_UP -> changeHand(-1);
            case GLFW.GLFW_KEY_DOWN -> changeHand(1);
            case GLFW.GLFW_KEY_HOME -> seek(0);
            case GLFW.GLFW_KEY_END -> seek(steps());
            case GLFW.GLFW_KEY_SPACE -> togglePlay();
            case GLFW.GLFW_KEY_V -> cycleViewer(1);
            default -> { return super.keyPressed(key, scan, modifiers); }
        }
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }

    private final class Timeline extends MahjongSlider {
        Timeline() { super(10, ReplayScreen.this.height - 78, ReplayScreen.this.width - 20, 20, Component.empty(), 0); }
        void sync() { value = steps() == 0 ? 0 : (double) cursor / steps(); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.translatable("replay.mchjong.step", cursor, steps())); }
        @Override protected void applyValue() {
            cursor = (int) Math.round(value * steps());
            playing = false;
            playbackClock = 0;
            layoutFrame();
            if (play != null) play.setMessage(Component.translatable("replay.mchjong.play"));
            updateMessage();
        }
    }

    private final class RoundList extends AbstractWidget {
        private static final int ROW = 25;
        private int scroll;
        RoundList(int x, int y, int width, int height) {
            super(x, y, width, height, Component.translatable("replay.mchjong.rounds"));
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            MahjongUi.panel(graphics, getX(), getY(), width, height);
            graphics.enableScissor(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2);
            for (int i = 0; i < match.hands().size(); i++) {
                int y = getY() + i * ROW - scroll;
                if (y + ROW < getY() || y > getY() + height) continue;
                graphics.fill(getX() + 4, y + 3, getX() + width - 7, y + ROW - 2, i == handIndex ? MahjongUi.SELECTED : MahjongUi.SURFACE);
                var label = roundLabel(i).copy().append(" · ").append(Component.translatable("result.mchjong." + match.hands().get(i).result()));
                MahjongUi.text(graphics, font, label, getX() + 9, y + 8, width - 20, i == handIndex ? MahjongUi.ACCENT : MahjongUi.TEXT, false);
            }
            graphics.disableScissor();
            int content = match.hands().size() * ROW;
            if (content > height) {
                int thumb = Math.max(14, height * height / content);
                int y = getY() + scroll * (height - thumb) / (content - height);
                graphics.fill(getX() + width - 5, getY() + 3, getX() + width - 3, getY() + height - 3, MahjongUi.EDGE);
                graphics.fill(getX() + width - 5, y, getX() + width - 3, y + thumb, MahjongUi.ACCENT);
            }
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
            int entry = (int) (mouseY - getY() + scroll) / ROW;
            if (entry >= 0 && entry < match.hands().size()) changeHandTo(entry);
            return true;
        }

        @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            scroll = Math.clamp(scroll - (int) Math.round(vertical * ROW), 0, Math.max(0, match.hands().size() * ROW - height));
            return true;
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}
