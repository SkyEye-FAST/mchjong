package top.skyeyefast.mchjong.smoke;

import java.util.HashSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import top.skyeyefast.mchjong.client.*;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Native navigation against a live room, without submitting the local test draft. */
final class RuleEditorSmoke {
    private static final List<String> CATEGORIES = List.of("basic", "points", "scoring", "flow", "calls", "payments");
    private RuleEditorSmoke() {}
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void click(Minecraft client, String key) {
        var button = LobbySmoke.find(client, Component.translatable(key).getString());
        require(button != null && button.active, "Missing rule control: " + key); button.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
    }
    private static EditBox field(Minecraft client) {
        return client.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
            .filter(w -> w.getMessage().getString().equals(Component.translatable("rules.mchjong.option.starting_points").getString())).findFirst().orElseThrow();
    }
    static void presets(Minecraft client) {
        var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(w -> w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("rules.mchjong.preset")).findFirst().orElseThrow();
        client.screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(button.getX() + 5, button.getY() + 5, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
        require(client.screen.getTitle().getString().equals(Component.translatable("rules.mchjong.choose_preset").getString()), "Missing preset child page");
    }
    static void categories(Minecraft client) {
        for (String category : CATEGORIES) {
            var name = Component.translatable("rules.mchjong.group." + category).getString();
            require(client.screen.children().stream().anyMatch(c -> c instanceof AbstractWidget w && w.visible && w.active && w.getMessage().getString().equals(name)),
                "Rule category disappeared: " + category);
        }
    }
    static void layout(Minecraft client, RiichiRules rules) {
        var screen = client.screen; int width = screen.width, height = screen.height;
        for (var size : List.of(new int[]{320, 240}, new int[]{640, 400})) {
            screen.init(size[0], size[1]);
            var explanations = new HashSet<String>();
            for (String category : CATEGORIES) {
                click(client, "rules.mchjong.group." + category);
                for (int page = 0; page < 20; page++) {
                    categories(client); AutomationControlsSmoke.checkBounds(client);
                    for (var child : screen.children()) if (child instanceof AbstractWidget widget
                        && widget.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("rules.mchjong.explanation"))
                        explanations.add(widget.getMessage().getString());
                    var next = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(w -> w.getMessage().getString().equals(Component.translatable("rules.mchjong.next").getString())).findFirst().orElseThrow();
                    if (!next.active) break;
                    screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(next.getX() + 5, next.getY() + 5, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                }
            }
            for (var option : RiichiRuleOption.values()) if (option.visible(rules)) {
                var name = option.floatingPlayers() < 0 ? Component.translatable(option.translationKey(), option.placementRank())
                    : Component.translatable(option.translationKey(), option.floatingPlayers(), option.placementRank());
                require(explanations.contains(Component.translatable("rules.mchjong.explanation", name).getString()), "Missing migrated rule: " + option);
            }
        }
        screen.init(width, height); click(client, "rules.mchjong.group.basic");
    }
    static void draft(Minecraft client, MahjongTableBlockEntity table) {
        var editor = (RiichiRulesScreen) client.screen;
        var parent = editor.tableScreen(); var original = table.clientRiichiSettings().rules();
        categories(client);
        click(client, "rules.mchjong.group.points"); field(client).setValue("-");
        RuleExplanationSmoke.check(client, Component.translatable("rules.mchjong.option.starting_points"));
        require(field(client).getValue().equals("-"), "Help erased incomplete input");
        click(client, "rules.mchjong.group.basic"); presets(client); click(client, "gui.cancel");
        require(client.screen == editor, "Preset cancellation closed the editor");
        presets(client); click(client, original.preset().presetKey());
        click(client, "rules.mchjong.group.points"); require(field(client).getValue().equals("-"), "Reselecting the preset reset draft input");
        int width = editor.width, height = editor.height;
        editor.init(320, 240); categories(client); AutomationControlsSmoke.checkBounds(client);
        require(field(client).getValue().equals("-"), "Resizing erased incomplete input");
        editor.init(width, height);
        click(client, "rules.mchjong.group.scoring");
        LobbySmoke.find(client, ClientYakuSets.selectionLabel(original.yakuSelection()).getString()).onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
        click(client, "gui.back"); categories(client);
        require(LobbySmoke.find(client, ClientYakuSets.selectionLabel(original.yakuSelection()).getString()) != null, "Award return changed category");
        require(!LobbySmoke.find(client, Component.translatable("rules.mchjong.apply").getString()).active, "Hidden incomplete input enabled Apply");
        click(client, "rules.mchjong.group.points"); require(field(client).getValue().equals("-"), "Award navigation erased incomplete input");
        click(client, "gui.cancel"); require(table.clientRiichiSettings().rules().equals(original), "Cancel changed confirmed rules");
        client.setScreen(new RiichiRulesScreen(parent, table.clientRoom(), table.clientRiichiSettings()));
        presets(client); click(client, RiichiPreset.JPML_A.presetKey());
        click(client, "ui.mchjong.players.3");
        require(client.screen.getTitle().getString().equals(Component.translatable("rules.mchjong.choose_preset").getString()), "Unsupported count silently replaced preset");
        click(client, "gui.cancel");
        click(client, "rules.mchjong.group.points"); require(field(client).getValue().equals("30000"), "Count cancellation replaced the draft");
        layout(client, RiichiPreset.JPML_A.config());
        click(client, "gui.cancel"); require(table.clientRiichiSettings().rules().equals(original), "Count draft changed the server");
        client.setScreen(new TableOptionsScreen(parent, table.getBlockPos()));
        click(client, "rules.mchjong.title"); categories(client); click(client, "gui.cancel");
        client.setScreen(new RiichiRulesScreen(parent, table.clientRoom(), table.clientRiichiSettings()));
    }
}
