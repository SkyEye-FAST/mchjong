package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    TableAutomation(TableScreen parent, Runnable rebuild) {
        this.parent = parent;
        this.rebuild = rebuild;
    }

    static boolean available(TableView view) {
        return view != null && view.viewerSeat() >= 0 && view.autoPlay() != null
            && view.phase() != Game.Phase.LOBBY && !TableResults.available(view) && view.exitVote() == null;
    }

    static int width(int screenWidth) { return Math.min(132, (screenWidth - 28) / 3); }

    void receivedControlReply() {
        if (!pending) return;
        pending = false;
        rebuild.run();
    }

    List<MahjongButton> build(TableView view, int screenWidth, int bottom) {
        if (!available(view)) { pending = false; return List.of(); }
        int width = width(screenWidth), y = bottom - 20;
        var buttons = new ArrayList<MahjongButton>();
        buttons.add(MahjongButton.create(Component.translatable(expanded ? "ui.mchjong.automation_hide" : "ui.mchjong.automation_show"),
            ignored -> { expanded = !expanded; rebuild.run(); }).bounds(8, y, width, 20).build());
        if (!expanded) return buttons;
        int count = view.rules().sanma() ? 5 : 4;
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
            var button = new MahjongButton(8, y - (count - option.ordinal()) * 22, width, 20, label, ignored -> {
                var current = parent.view();
                if (pending || !available(current)) return;
                pending = true;
                parent.control(current, operation, current.decision(), !current.autoPlay().enabled(option));
                rebuild.run();
            }) {
                @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    var font = Minecraft.getInstance().font;
                    if (font.width(getMessage()) <= getWidth() - 12) {
                        super.renderWidget(graphics, mouseX, mouseY, partialTick);
                        return;
                    }
                    renderSurface(graphics);
                    int color = active || enabled ? MahjongUi.TEXT : MahjongUi.DISABLED;
                    MahjongUi.text(graphics, font, Component.translatable(key), getX() + 6, getY() + 1, getWidth() - 12, color, true);
                    MahjongUi.text(graphics, font, Component.translatable(enabled ? "options.on" : "options.off"),
                        getX() + 6, getY() + 9, getWidth() - 12, color, true);
                }
            }.selected(enabled);
            button.active = !pending;
            buttons.add(button);
        }
        return buttons;
    }
}
