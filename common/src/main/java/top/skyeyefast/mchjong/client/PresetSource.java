package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Shared source tabs and identity tooltips for the four preset selectors. */
public enum PresetSource {
    BUILTIN, SERVER, LOCAL;

    private static int revision;
    public static int revision() { return revision; }
    static void changed() { revision++; }
    public static PresetSource of(boolean server, boolean local) {
        return server ? SERVER : local ? LOCAL : BUILTIN;
    }
    public Component label() {
        return Component.translatable("preset.mchjong.source." + name().toLowerCase(java.util.Locale.ROOT));
    }
    public boolean includes(PresetSource origin) { return this == origin || this == SERVER && origin == BUILTIN; }
    public List<MahjongButton> tabs(int left, int top, int width, Consumer<PresetSource> choose) {
        var buttons = new ArrayList<MahjongButton>();
        int span = (width - 4) / 2;
        for (var source : List.of(SERVER, LOCAL)) buttons.add(MahjongButton.create(source.label(), ignored -> choose.accept(source))
            .bounds(left + (source == SERVER ? 0 : span + 4), top, span, 20).build().selected(source == this));
        return buttons;
    }
    public Tooltip tooltip(Component name, ResourceLocation id) {
        var text = name.copy();
        if (this == BUILTIN) text.append(Component.translatable("preset.mchjong.source.builtin.annotation").withStyle(net.minecraft.ChatFormatting.GRAY));
        return Tooltip.create(text.append("\n").append(Component.literal(id.toString()).withStyle(net.minecraft.ChatFormatting.GRAY)));
    }
}
