package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Opens the localized description of a rule option. */
final class RuleHelp {
    private RuleHelp() {}

    static MahjongButton setting(Screen parent, Component label, Component description, int x, int y) {
        var name = Component.translatable("rules.mchjong.explanation", label);
        var button = new MahjongButton(x, y, 20, 20, name, ignored ->
            Minecraft.getInstance().setScreen(new TableHelpScreen(parent, label, () -> List.of(description))))
            .shortCaption(Component.literal("?"));
        button.setTooltip(Tooltip.create(label.copy().append("\n").append(description)));
        return button;
    }
}
