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

    int width(int screenWidth) { return expanded ? Math.min(132, Math.max(104, (screenWidth - 28) / 3)) : 52; }

    int focusedIndex(GuiEventListener focused) { return focused == null ? -1 : buttons.indexOf(focused); }

    void restoreFocus(int index) {
        if (index >= 0 && index < buttons.size()) parent.setFocused(buttons.get(index));
    }

    void receivedControlReply() {
        if (!pending) return;
        pending = false;
        rebuild.run();
    }

    List<MahjongButton> build(TableView view, int screenWidth, int bottom) {
        buttons = new ArrayList<>();
        if (!available(view)) { pending = false; return buttons; }
        int width = width(screenWidth) - 24;
        int count = view.rules().sanma() ? 5 : 4;
        // Keep all five 20-pixel controls below the HUD and above the overhead hand at 320 x 240.
        int gap = Math.min(4, Math.max(0, (bottom - 70 - count * 20) / (count - 1)));
        int height = count * 20 + (count - 1) * gap, top = bottom - height;
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
            var button = new MahjongButton(8, top + option.ordinal() * (20 + gap), width, 20, label, ignored -> {
                var current = parent.view();
                if (pending || !available(current)) return;
                pending = true;
                parent.control(current, operation, current.decision(), !current.autoPlay().enabled(option));
                rebuild.run();
            }) {
                @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    var font = Minecraft.getInstance().font;
                    renderSurface(graphics);
                    int color = !active ? MahjongUi.DISABLED : enabled ? MahjongUi.POSITIVE : MahjongUi.MUTED;
                    if (enabled) graphics.fill(getX() + 4, getY() + 8, getX() + 9, getY() + 13, color);
                    else graphics.renderOutline(getX() + 4, getY() + 8, 5, 5, color);
                    var caption = Component.translatable(expanded ? key : key + ".short");
                    if (expanded && font.width(caption) > getWidth() - 17) {
                        var lines = font.split(caption, getWidth() - 17);
                        int count = Math.min(2, lines.size());
                        for (int line = 0; line < count; line++)
                            graphics.drawString(font, lines.get(line), getX() + 13 + (getWidth() - 17 - font.width(lines.get(line))) / 2,
                                getY() + (getHeight() - count * font.lineHeight) / 2 + line * font.lineHeight, color, false);
                    } else MahjongUi.text(graphics, font, caption, getX() + 13, getY() + 6,
                        getWidth() - 17, color, true);
                }
            }.selected(enabled);
            button.active = !pending;
            buttons.add(button);
        }
        buttons.add(new MahjongButton(8 + width + 4, top + (height - 20) / 2, 20, 20,
            Component.translatable(expanded ? "ui.mchjong.automation_hide" : "ui.mchjong.automation_show"),
            ignored -> { expanded = !expanded; rebuild.run(); }) {
                @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    renderSurface(graphics);
                    MahjongUi.text(graphics, Minecraft.getInstance().font, Component.literal(expanded ? "‹" : "›"),
                        getX() + 4, getY() + 6, getWidth() - 8, MahjongUi.ACCENT, true);
                }
            });
        return buttons;
    }
}
