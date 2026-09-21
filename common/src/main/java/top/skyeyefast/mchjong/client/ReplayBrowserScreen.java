package top.skyeyefast.mchjong.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.ReplayMatch;

public final class ReplayBrowserScreen extends Screen {
    private final Screen parent;
    private final ReplayMatch.Index index;
    private MahjongEditBox search;
    private MatchList matches;
    private int selected;
    public ReplayBrowserScreen(Screen parent, ReplayMatch.Index index) {
        super(Component.translatable("replay.mchjong.title"));
        this.parent = parent;
        this.index = java.util.Objects.requireNonNull(index);
    }
    public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}
    @Override protected void init() {
        int span = Math.min(650, width - 20), left = (width - span) / 2;
        String text = search == null ? index.search() : search.getValue();
        search = addRenderableWidget(new MahjongEditBox(font, left, 42, span - 140, 20,
            Component.translatable("replay.mchjong.search")));
        search.setMaxLength(80);
        search.setHint(Component.translatable("replay.mchjong.search_hint"));
        search.setValue(text);
        addRenderableWidget(MahjongButton.create(Component.translatable("replay.mchjong.search"), ignored -> refresh(0, index.oldestFirst()))
            .bounds(left + span - 136, 42, 58, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable(index.oldestFirst()
                ? "replay.mchjong.oldest" : "replay.mchjong.newest"), ignored -> refresh(0, !index.oldestFirst()))
            .bounds(left + span - 74, 42, 74, 20).build());
        selected = Math.clamp(selected, 0, Math.max(0, index.matches().size() - 1));
        matches = addRenderableWidget(new MatchList(left, 68, span, height - 132));
        matches.move(0);
        setInitialFocus(matches);
        var open = addRenderableWidget(MahjongButton.create(Component.translatable("replay.mchjong.open"), ignored -> matches.activate())
            .bounds(left, height - 58, (span - 6) / 2, 20).build());
        var delete = addRenderableWidget(MahjongButton.create(Component.translatable("replay.mchjong.delete"), ignored -> confirmDelete())
            .bounds(left + (span + 6) / 2, height - 58, (span - 6) / 2, 20).build());
        open.active = delete.active = !index.matches().isEmpty();
        var previous = addRenderableWidget(MahjongButton.create(Component.literal("<"), ignored -> refresh(index.page() - 1, index.oldestFirst()))
            .bounds(left, height - 30, 35, 20).build());
        previous.active = index.page() > 0;
        var next = addRenderableWidget(MahjongButton.create(Component.literal(">"), ignored -> refresh(index.page() + 1, index.oldestFirst()))
            .bounds(left + span - 35, height - 30, 35, 20).build());
        next.active = index.more();
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(width / 2 - 55, height - 30, 110, 20).build());
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, 650);
        MahjongUi.text(graphics, font, title, 12, 11, width - 24, MahjongUi.TEXT, true);
        graphics.drawCenteredString(font, Component.translatable("replay.mchjong.archive_note", index.page() + 1), width / 2, 25, MahjongUi.MUTED);
        super.render(graphics, x, y, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }

    private void refresh(int page, boolean oldestFirst) { ClientReplays.list(page, search.getValue(), oldestFirst); }

    private void confirmDelete() {
        if (!index.matches().isEmpty()) minecraft.setScreen(new DeleteReplayScreen(this, index.matches().get(selected)));
    }

    void delete(java.util.UUID id) { ClientReplays.delete(id, index.search(), index.oldestFirst()); }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (search.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            refresh(0, index.oldestFirst());
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private final class MatchList extends AbstractWidget {
        private static final int ROW = 60;
        private int scroll;
        private long lastClick;
        MatchList(int x, int y, int width, int height) { super(x, y, width, height, title); }
        private Component label(int entry) {
            var match = index.matches().get(entry);
            return Component.literal(String.join(" · ", match.names()));
        }
        private Component standings(ReplayMatch.Header match) {
            if (match.finalRanks().isEmpty()) return Component.empty();
            var seats = java.util.stream.IntStream.range(0, match.names().size()).boxed()
                .sorted(java.util.Comparator.comparingInt(seat -> match.finalRanks().get(seat))).toList();
            var text = Component.empty();
            for (int i = 0; i < seats.size(); i++) {
                int seat = seats.get(i);
                if (i > 0) text.append(" · ");
                text.append(Component.literal(match.finalRanks().get(seat) + ". " + match.names().get(seat) + " "
                    + String.format(java.util.Locale.ROOT, "%+.1f", match.finalScores().get(seat))));
            }
            return text;
        }
        private void move(int delta) {
            selected = Math.clamp(selected + delta, 0, Math.max(0, index.matches().size() - 1));
            scroll = Math.clamp(scroll, Math.max(0, (selected + 1) * ROW - height), selected * ROW);
        }
        private void activate() { if (!index.matches().isEmpty()) ClientReplays.open(index.matches().get(selected).id()); }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            MahjongUi.panel(graphics, getX(), getY(), width, height);
            graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
            if (index.matches().isEmpty()) graphics.drawCenteredString(font, Component.translatable("replay.mchjong.empty"),
                getX() + width / 2, getY() + 18, MahjongUi.MUTED);
            for (int i = 0; i < index.matches().size(); i++) {
                int y = getY() + i * ROW - scroll;
                if (y + ROW < getY() || y > getY() + height) continue;
                var match = index.matches().get(i);
                graphics.fill(getX() + 3, y + 2, getX() + width - 3, y + ROW - 2, i == selected ? MahjongUi.SELECTED : MahjongUi.SURFACE);
                if (isFocused() && i == selected) graphics.renderOutline(getX() + 3, y + 2, width - 6, ROW - 4, MahjongUi.ACCENT);
                MahjongUi.text(graphics, font, label(i), getX() + 9, y + 7, width - 22, MahjongUi.TEXT, false);
                String date = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(match.startedAt()));
                var info = Component.translatable("replay.mchjong.entry", date, match.hands(),
                    Component.translatable(match.complete() ? "replay.mchjong.finished" : "replay.mchjong.ongoing"));
                MahjongUi.text(graphics, font, info, getX() + 9, y + 21, width - 22, MahjongUi.MUTED, false);
                graphics.drawString(font, Component.translatable(match.rules().translationKey()), getX() + 9, y + 33, MahjongUi.ACCENT);
                MahjongUi.text(graphics, font, standings(match), getX() + 9, y + 46, width - 22, MahjongUi.MUTED, false);
            }
            graphics.disableScissor();
            int content = index.matches().size() * ROW;
            if (content > height) {
                int track = height - 8, thumb = Math.max(16, track * height / content);
                int y = getY() + 4 + scroll * (track - thumb) / (content - height);
                graphics.fill(getX() + width - 5, getY() + 4, getX() + width - 3, getY() + height - 4, MahjongUi.EDGE);
                graphics.fill(getX() + width - 5, y, getX() + width - 3, y + thumb, MahjongUi.ACCENT);
            }
        }
        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!super.mouseClicked(mouseX, mouseY, button)) return false;
            int entry = (int) (mouseY - getY() + scroll) / ROW;
            if (entry >= 0 && entry < index.matches().size()) {
                long now = net.minecraft.Util.getMillis();
                boolean open = entry == selected && now - lastClick < 250;
                selected = entry;
                lastClick = now;
                if (open) activate();
            }
            return true;
        }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            if (!isMouseOver(x, y)) return false;
            scroll = Math.clamp(scroll - (int) Math.round(vertical * ROW), 0, Math.max(0, index.matches().size() * ROW - height));
            return true;
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (!isFocused()) return false;
            switch (key) {
                case GLFW.GLFW_KEY_UP -> move(-1);
                case GLFW.GLFW_KEY_DOWN -> move(1);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE -> activate();
                case GLFW.GLFW_KEY_DELETE -> confirmDelete();
                default -> { return super.keyPressed(key, scan, modifiers); }
            }
            return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, index.matches().isEmpty() ? Component.translatable("replay.mchjong.empty") : label(selected));
        }
    }
}
