package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.ReplayMatch;

public final class ReplayScreen extends Screen {
    private final Screen parent;
    private final ReplayMatch match;
    private int hand, cursor, ticks;
    private boolean playing;
    private ReplayBoard board;
    private Timeline timeline;
    private Button play;
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
    private int steps() { return match.hands().get(hand).events().size() + 1; }

    @Override protected void init() {
        var previousHand = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changeHand(-1))
            .bounds(10, 30, 30, 20).build());
        previousHand.active = hand > 0;
        var nextHand = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changeHand(1))
            .bounds(width - 40, 30, 30, 20).build());
        nextHand.active = hand + 1 < match.hands().size();
        board = addRenderableWidget(new ReplayBoard(font, match, 10, 56, width - 20, height - 144));
        timeline = addRenderableWidget(new Timeline());
        int span = (width - 36) / 5;
        addRenderableWidget(Button.builder(Component.literal("|<"), ignored -> seek(0)).bounds(10, height - 52, span, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), ignored -> seek(cursor - 1)).bounds(14 + span, height - 52, span, 20).build());
        play = addRenderableWidget(Button.builder(Component.empty(), ignored -> togglePlay()).bounds(18 + span * 2, height - 52, span, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), ignored -> seek(cursor + 1)).bounds(22 + span * 3, height - 52, span, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">|"), ignored -> seek(steps())).bounds(26 + span * 4, height - 52, span, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
            .bounds(10, height - 28, width / 2 - 14, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("replay.mchjong.export"), ignored -> export())
            .bounds(width / 2 + 4, height - 28, width / 2 - 14, 20).build());
        refresh();
    }
    private void refresh() {
        board.show(hand, cursor);
        timeline.sync();
        play.setMessage(Component.translatable(playing ? "replay.mchjong.pause" : "replay.mchjong.play"));
    }
    public void seek(int step) {
        playing = false;
        cursor = Math.clamp(step, 0, steps());
        refresh();
    }
    private void changeHand(int offset) {
        hand = Math.clamp(hand + offset, 0, match.hands().size() - 1);
        cursor = 0; playing = false;
        rebuildWidgets();
    }
    private void togglePlay() {
        if (cursor == steps()) cursor = 0;
        playing = !playing;
        ticks = 0;
        refresh();
    }
    @Override public void tick() {
        if (!playing || ++ticks < 10) return;
        ticks = 0;
        if (cursor < steps()) cursor++;
        if (cursor == steps()) playing = false;
        refresh();
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
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff18292e);
        graphics.drawCenteredString(font, status.getString().isEmpty() ? title : status, width / 2, 12, 0xfff2cf86);
        var round = match.hands().get(hand);
        var heading = Component.translatable("ui.mchjong.round",
            Component.translatable("wind.mchjong." + new String[]{"east", "south", "west", "north"}[round.round() / match.rules().players()]),
            round.round() % match.rules().players() + 1, round.honba());
        graphics.drawCenteredString(font, font.split(heading, width - 96).getFirst(), width / 2, 36, 0xffd1e4d9);
        super.render(graphics, x, y, partialTick);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> seek(cursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> seek(cursor + 1);
            case GLFW.GLFW_KEY_HOME -> seek(0);
            case GLFW.GLFW_KEY_END -> seek(steps());
            case GLFW.GLFW_KEY_SPACE -> togglePlay();
            default -> { return super.keyPressed(key, scan, modifiers); }
        }
        return true;
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }

    private final class Timeline extends AbstractSliderButton {
        Timeline() { super(10, ReplayScreen.this.height - 78, ReplayScreen.this.width - 20, 20, Component.empty(), 0); }
        void sync() { value = (double) cursor / steps(); updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.translatable("replay.mchjong.step", cursor, steps())); }
        @Override protected void applyValue() {
            cursor = (int) Math.round(value * steps()); playing = false;
            if (board != null) board.show(hand, cursor);
            if (play != null) play.setMessage(Component.translatable("replay.mchjong.play"));
        }
    }
}
