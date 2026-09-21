package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.AutoPlay;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.network.TableControlPayload;

/** Collapsible match controls backed by the seated player's authoritative preferences. */
final class TableAutomation {
    private final TableScreen parent;
    private final Runnable rebuild;
    private boolean expanded;
    private boolean pending;
    private List<MahjongButton> buttons = List.of();

    TableAutomation(TableScreen parent, Runnable rebuild) {
        this.parent = parent;
        this.rebuild = rebuild;
    }

    static boolean available(TableView view) {
        return view != null && view.viewerSeat() >= 0 && view.autoPlay() != null
            && (view.phase() == Game.Phase.TURN || view.phase() == Game.Phase.REACTION) && view.exitVote() == null;
    }

    int width(int screenWidth) { return expanded ? Math.min(124, Math.max(96, (screenWidth - 28) / 3)) : 44; }

    int focusedIndex(GuiEventListener focused) { return focused == null ? -1 : buttons.indexOf(focused); }

    void restoreFocus(int index) {
        if (index >= 0 && index < buttons.size()) parent.setFocused(buttons.get(index));
    }

    void receivedControlReply() {
        if (!pending) return;
        pending = false;
        rebuild.run();
    }

    List<MahjongButton> build(TableView view, int screenWidth, int bottom, boolean horizontal) {
        buttons = new ArrayList<>();
        if (!available(view)) { pending = false; return buttons; }
        int count = view.rules().sanma() ? 5 : 4;
        int width = horizontal ? expanded ? Math.min(180, (screenWidth - 96 - count * 8) / count) : 46 : width(screenWidth) - 20;
        int gap = horizontal ? 8 : 0;
        int buttonHeight = horizontal ? 36 : 20;
        int toggleWidth = horizontal ? 36 : 20;
        int height = horizontal ? buttonHeight : count * 20 + (count - 1) * gap, top = bottom - height;
        int horizontalSpan = count * width + count * gap + toggleWidth;
        int origin = horizontal ? Math.max(8, (screenWidth - horizontalSpan) / 2) : 8;
        for (var option : AutoPlay.Option.values()) {
            if (option == AutoPlay.Option.KITA && !view.rules().sanma()) continue;
            String key = switch (option) {
                case SORT -> "ui.mchjong.auto_sort";
                case WIN -> "ui.mchjong.auto_win";
                case NO_CALLS -> "ui.mchjong.no_calls";
                case DISCARD -> "ui.mchjong.auto_discard";
                case KITA -> "ui.mchjong.auto_kita";
            };
            var operation = switch (option) {
                case SORT -> TableControlPayload.Operation.AUTO_SORT;
                case WIN -> TableControlPayload.Operation.AUTO_WIN;
                case NO_CALLS -> TableControlPayload.Operation.NO_CALLS;
                case DISCARD -> TableControlPayload.Operation.AUTO_DISCARD;
                case KITA -> TableControlPayload.Operation.AUTO_KITA;
            };
            boolean enabled = view.autoPlay().enabled(option);
            var label = Component.translatable("settings.mchjong.toggle", Component.translatable(key),
                Component.translatable(enabled ? "options.on" : "options.off"));
            var button = new MahjongButton(origin + (horizontal ? option.ordinal() * (width + gap) : 0),
                top + (horizontal ? 0 : option.ordinal() * (20 + gap)), width, buttonHeight, label, ignored -> {
                var current = parent.view();
                if (pending || !available(current)) return;
                pending = true;
                parent.control(current, operation, current.decision(), !current.autoPlay().enabled(option));
                rebuild.run();
            }) {
                @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    var font = Minecraft.getInstance().font;
                    if (horizontal) renderSurface(graphics);
                    else MahjongUi.control(graphics, getX(), getY() + 2, getWidth(), 16,
                        active, isHovered(), isFocused(), enabled, false);
                    int color = !active ? MahjongUi.DISABLED : enabled ? MahjongUi.POSITIVE : MahjongUi.MUTED;
                    int markerX = getX() + (horizontal ? 8 : 2), markerSize = horizontal ? 8 : 4;
                    int markerY = getY() + (getHeight() - markerSize) / 2;
                    if (enabled) graphics.fill(markerX, markerY, markerX + markerSize, markerY + markerSize, color);
                    else graphics.renderOutline(markerX, markerY, markerSize, markerSize, color);
                    var caption = Component.translatable(expanded ? key : key + ".short");
                    int captionInset = horizontal ? 22 : expanded ? 13 : 8;
                    float scale = horizontal ? 2 : 1;
                    int captionWidth = (int) ((getWidth() - captionInset - (horizontal || expanded ? 6 : 2)) / scale);
                    graphics.pose().pushPose();
                    graphics.pose().translate(getX() + captionInset, getY() + getHeight() / 2f, 0);
                    graphics.pose().scale(scale, scale, 1);
                    if (expanded && font.width(caption) > captionWidth) {
                        var lines = font.split(caption, captionWidth);
                        int count = Math.min(2, lines.size());
                        for (int line = 0; line < count; line++)
                            graphics.drawString(font, lines.get(line), (captionWidth - font.width(lines.get(line))) / 2,
                                -count * font.lineHeight / 2 + line * font.lineHeight, color, false);
                    } else MahjongUi.text(graphics, font, caption, 0, -font.lineHeight / 2, captionWidth, color, true);
                    graphics.pose().popPose();
                }
            }.selected(enabled);
            button.active = !pending;
            buttons.add(button);
        }
        buttons.add(new MahjongButton(origin + (horizontal ? count * (width + gap) : width),
            top + (height - buttonHeight) / 2, toggleWidth, buttonHeight,
            Component.translatable(expanded ? "ui.mchjong.automation_hide" : "ui.mchjong.automation_show"),
            ignored -> { expanded = !expanded; rebuild.run(); }) {
                @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    if (horizontal) renderSurface(graphics);
                    else {
                        graphics.fill(getX() + 6, getY() + 2, getX() + 14, getY() + 18,
                            isHoveredOrFocused() ? MahjongUi.HOVER : MahjongUi.PANEL);
                        if (isFocused()) graphics.renderOutline(getX() + 5, getY() + 1, 10, 18, MahjongUi.ACCENT);
                    }
                    MahjongUi.text(graphics, Minecraft.getInstance().font, Component.literal(expanded ? "‹" : "›"),
                        getX() + 4, getY() + (getHeight() - Minecraft.getInstance().font.lineHeight) / 2,
                        getWidth() - 8, isHoveredOrFocused() ? MahjongUi.ACCENT : MahjongUi.MUTED, true);
                }
            });
        return buttons;
    }
}
