package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.MatchAutomation;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.MatchAutomationPayload;
import top.skyeyefast.mchjong.network.PayloadPackets;

/** Collapsible match controls backed by the seated player's authoritative preferences. */
final class TableAutomation {
    private final Screen parent;
    private final Supplier<List<Toggle>> options;
    record Toggle(String key, boolean enabled, Runnable send) {}
    private final Runnable rebuild;
    private boolean expanded;
    private boolean pending;
    private List<MahjongButton> buttons = List.of();

    TableAutomation(Screen parent, Supplier<List<Toggle>> options, Runnable rebuild) {
        this.parent = parent;
        this.options = options;
        this.rebuild = rebuild;
    }

    boolean available() { return !options.get().isEmpty(); }

    static List<Toggle> common(BlockPos pos, TableRoomView room, long decision) {
        if (room == null || room.automation() == null || room.paused() || room.exitVote() != null) return List.of();
        var toggles = new ArrayList<Toggle>();
        for (var option : MatchAutomation.Option.values()) {
            String key = switch (option) {
                case WIN -> "ui.mchjong.auto_win";
                case NO_CALLS -> "ui.mchjong.no_calls";
                case DISCARD -> "ui.mchjong.auto_discard";
            };
            boolean enabled = room.automation().enabled(option);
            toggles.add(new Toggle(key, enabled, () -> {
                var connection = Minecraft.getInstance().getConnection();
                if (connection != null) connection.send(PayloadPackets.serverbound(new MatchAutomationPayload(
                    pos, room.tableId(), room.incarnation(), decision, option, !enabled)));
            }));
        }
        return toggles;
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

    List<MahjongButton> build(int screenWidth, int bottom, boolean horizontal) {
        buttons = new ArrayList<>();
        var choices = options.get();
        if (choices.isEmpty()) { pending = false; return buttons; }
        int count = choices.size();
        int width = horizontal ? expanded ? Math.min(180, (screenWidth - 96 - count * 8) / count) : 46 : width(screenWidth) - 20;
        int gap = horizontal ? 8 : 0;
        int buttonHeight = horizontal ? 36 : 20;
        int toggleWidth = horizontal ? 36 : 20;
        int height = horizontal ? buttonHeight : count * 20 + (count - 1) * gap, top = bottom - height;
        int horizontalSpan = count * width + count * gap + toggleWidth;
        int origin = horizontal ? Math.max(8, (screenWidth - horizontalSpan) / 2) : 8 + MahjongUi.OFFSET;
        for (int index = 0; index < choices.size(); index++) {
            var choice = choices.get(index);
            String key = choice.key();
            boolean enabled = choice.enabled();
            var label = Component.translatable("settings.mchjong.toggle", Component.translatable(key),
                Component.translatable(enabled ? "options.on" : "options.off"));
            var button = new MahjongButton(origin + (horizontal ? index * (width + gap) : 0),
                top + (horizontal ? -MahjongUi.step(index) : index * (20 + gap)), width, buttonHeight, label, ignored -> {
                if (pending || !available()) return;
                pending = true;
                choice.send().run();
                rebuild.run();
            }) {
                @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    var font = Minecraft.getInstance().font;
                    if (horizontal) renderSurface(graphics);
                    else MahjongUi.control(graphics, getX(), getY() + 1, getWidth(), 18,
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
                        graphics.fill(getX() + 6, getY() + 1, getX() + 14, getY() + 19,
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
