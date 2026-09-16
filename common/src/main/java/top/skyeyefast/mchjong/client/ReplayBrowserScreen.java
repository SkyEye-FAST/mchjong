package top.skyeyefast.mchjong.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.ReplayMatch;

public final class ReplayBrowserScreen extends Screen {
    private final Screen parent;
    private final ReplayMatch.Index index;
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
        setInitialFocus(addRenderableWidget(new MatchList(left, 40, span, height - 80)));
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> ClientReplays.list(index.page() - 1))
            .bounds(left, height - 30, 35, 20).build());
        previous.active = index.page() > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> ClientReplays.list(index.page() + 1))
            .bounds(left + span - 35, height - 30, 35, 20).build());
        next.active = index.more();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(width / 2 - 55, height - 30, 110, 20).build());
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, 0xf518292e);
        graphics.drawCenteredString(font, title, width / 2, 11, 0xfff2cf86);
        graphics.drawCenteredString(font, Component.translatable("replay.mchjong.archive_note", index.page() + 1), width / 2, 25, 0xffa8c5bc);
        super.render(graphics, x, y, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }

    private final class MatchList extends AbstractWidget {
        private static final int ROW = 48;
        private int selected, scroll;
        MatchList(int x, int y, int width, int height) { super(x, y, width, height, title); }
        private Component label(int entry) {
            var match = index.matches().get(entry);
            return Component.literal(String.join(" · ", match.names()));
        }
        private void move(int delta) {
            selected = Math.clamp(selected + delta, 0, Math.max(0, index.matches().size() - 1));
            scroll = Math.clamp(scroll, Math.max(0, (selected + 1) * ROW - height), selected * ROW);
        }
        private void activate() { if (!index.matches().isEmpty()) ClientReplays.open(index.matches().get(selected).id()); }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xff20373a);
            graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
            if (index.matches().isEmpty()) graphics.drawCenteredString(font, Component.translatable("replay.mchjong.empty"),
                getX() + width / 2, getY() + 18, 0xffd1e4d9);
            for (int i = 0; i < index.matches().size(); i++) {
                int y = getY() + i * ROW - scroll;
                if (y + ROW < getY() || y > getY() + height) continue;
                var match = index.matches().get(i);
                graphics.fill(getX() + 3, y + 2, getX() + width - 3, y + ROW - 2, i == selected ? 0xff345356 : 0xff273f42);
                if (isFocused() && i == selected) graphics.renderOutline(getX() + 3, y + 2, width - 6, ROW - 4, 0xfff2cf86);
                graphics.drawString(font, font.split(label(i), width - 18).getFirst(), getX() + 9, y + 7, 0xffe8e6d8);
                String date = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(match.updatedAt()));
                var info = Component.translatable("replay.mchjong.entry", date, match.hands(),
                    Component.translatable(match.complete() ? "replay.mchjong.finished" : "replay.mchjong.ongoing"));
                graphics.drawString(font, font.split(info, width - 18).getFirst(), getX() + 9, y + 21, 0xffa8c5bc);
                graphics.drawString(font, Component.translatable(match.rules().translationKey()), getX() + 9, y + 33, 0xfff2cf86);
            }
            graphics.disableScissor();
        }
        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!super.mouseClicked(mouseX, mouseY, button)) return false;
            int entry = (int) (mouseY - getY() + scroll) / ROW;
            if (entry < index.matches().size()) { selected = entry; activate(); }
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
                default -> { return super.keyPressed(key, scan, modifiers); }
            }
            return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, index.matches().isEmpty() ? Component.translatable("replay.mchjong.empty") : label(selected));
        }
    }
}
