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
        int width = horizontal ? expanded ? (screenWidth - 40 - count * 4) / count : 28 : width(screenWidth) - 20;
        // Keep all five 20-pixel controls below the HUD and above the private hand at 320 x 240.
        int gap = horizontal ? 4 : 0;
        int height = horizontal ? 20 : count * 20 + (count - 1) * gap, top = bottom - height;
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
            var button = new MahjongButton(8 + (horizontal ? option.ordinal() * (width + 4) : 0),
                top + (horizontal ? 0 : option.ordinal() * (20 + gap)), width, 20, label, ignored -> {
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
                    int markerX = getX() + (horizontal ? 4 : 2), markerSize = horizontal ? 5 : 4;
                    if (enabled) graphics.fill(markerX, getY() + 8, markerX + markerSize, getY() + 8 + markerSize, color);
                    else graphics.renderOutline(markerX, getY() + 8, markerSize, markerSize, color);
                    var caption = Component.translatable(expanded ? key : key + ".short");
                    if (expanded && font.width(caption) > getWidth() - 17) {
                        var lines = font.split(caption, getWidth() - 17);
                        int count = Math.min(2, lines.size());
                        for (int line = 0; line < count; line++)
                            graphics.drawString(font, lines.get(line), getX() + 13 + (getWidth() - 17 - font.width(lines.get(line))) / 2,
                                getY() + (getHeight() - count * font.lineHeight) / 2 + line * font.lineHeight, color, false);
                    } else MahjongUi.text(graphics, font, caption, getX() + (horizontal ? 13 : 8), getY() + 6,
                        getWidth() - (horizontal ? 17 : 10), color, true);
                }
            }.selected(enabled);
            button.active = !pending;
            buttons.add(button);
        }
        buttons.add(new MahjongButton(8 + (horizontal ? count : 1) * (width + (horizontal ? 4 : 0)), top + (height - 20) / 2, 20, 20,
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
                        getX() + 4, getY() + 6, getWidth() - 8, isHoveredOrFocused() ? MahjongUi.ACCENT : MahjongUi.MUTED, true);
                }
            });
        return buttons;
    }
}
