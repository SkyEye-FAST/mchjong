package top.skyeyefast.mchjong.compat.patchouli;

import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import top.skyeyefast.mchjong.client.TileDiagram;
import top.skyeyefast.mchjong.item.TileFacePreset;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

/** Two ordinary Patchouli template pages share one native-size explanation and tile row. */
public final class HandbookExample implements ICustomComponent {
    public static final int TEXT_WIDTH = 108;
    public static final int TILE_WIDTH = 100;
    private transient String text;
    private transient TileDiagram diagram;
    private transient int side;
    private transient List<FormattedCharSequence> lines = List.of();

    @Override public void onVariablesAvailable(UnaryOperator<IVariable> lookup, HolderLookup.Provider registries) {
        text = lookup.apply(IVariable.wrap("#text#", registries)).asString();
        diagram = TileDiagram.parse(lookup.apply(IVariable.wrap("#tiles#", registries)).asString(),
            new TileFacePreset(ResourceLocation.parse(lookup.apply(IVariable.wrap("#face#", registries)).asString())));
    }
    @Override public void build(int x, int y, int pageNum) { side = pageNum % 2; }
    @Override public void onDisplayed(IComponentRenderContext context) {
        String translated = Component.translatable(text).getString().replace("$(br2)", "\n\n")
            .replace("$(br)", "\n").replace("$(li)", "\n• ").replaceAll("\\$\\([^)]*\\)", "").strip();
        int heading = translated.indexOf('\n');
        var content = Component.empty().withStyle(context.getFont());
        if (heading > 0) content.append(Component.literal(translated.substring(0, heading)).withStyle(net.minecraft.ChatFormatting.BOLD))
            .append(translated.substring(heading));
        else content.append(translated);
        lines = new java.util.ArrayList<>(Minecraft.getInstance().font.split(content, TEXT_WIDTH));
        for (int i = 1; i < lines.size(); i++) {
            String current = plain(lines.get(i)), previous = plain(lines.get(i - 1));
            if (current.matches("[。，、！？：；）】」』]+") && !previous.isBlank()) {
                int last = previous.offsetByCodePoints(previous.length(), -1);
                lines.set(i - 1, FormattedCharSequence.forward(previous.substring(0, last), context.getFont()));
                lines.set(i, FormattedCharSequence.forward(previous.substring(last) + current, context.getFont()));
            }
        }
    }
    @Override public void render(GuiGraphics graphics, IComponentRenderContext context, float ticks, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        var spread = diagram.spread(TILE_WIDTH);
        boolean wrapped = diagram.width(0, spread.split(), spread.tileWidth()) > TILE_WIDTH
            || diagram.width(spread.split(), diagram.parts().size(), spread.tileWidth()) > TILE_WIDTH;
        int textRows = wrapped ? 7 : 9;
        int leftRows = lines.size() > textRows ? Math.min(textRows, (lines.size() + 1) / 2) : lines.size();
        int offset = side == 0 ? 0 : leftRows;
        int count = side == 0 ? leftRows : Math.min(textRows, lines.size() - offset);
        for (int row = 0; row < count; row++)
            graphics.drawString(font, lines.get(offset + row), 4, 4 + row * 10, context.getTextColor(), false);
        if (spread.across() || side == 1) {
            int start = side == 0 ? 0 : spread.split();
            int end = side == 0 ? spread.split() : diagram.parts().size();
            int captionY = wrapped ? 79 : 99;
            graphics.fill(8, captionY - 3, 108, captionY - 2, 0x554c686b);
            graphics.drawString(font, Component.translatable("rules.mchjong.example"), 8, captionY, context.getHeaderColor(), false);
            int y = wrapped ? 100 : 122;
            while (start < end) {
                int rowEnd = diagram.rowEnd(start, end, spread.tileWidth(), TILE_WIDTH);
                int x = 8 + (TILE_WIDTH - diagram.width(start, rowEnd, spread.tileWidth())) / 2;
                diagram.render(graphics, x, y, spread.tileWidth(), start, rowEnd);
                start = rowEnd;
                y += 30;
            }
        }
    }

    public int textLines() { return lines.size(); }
    public int textCapacity() {
        var spread = diagram.spread(TILE_WIDTH);
        return diagram.width(0, spread.split(), spread.tileWidth()) > TILE_WIDTH
            || diagram.width(spread.split(), diagram.parts().size(), spread.tileWidth()) > TILE_WIDTH ? 14 : 18;
    }
    private static String plain(FormattedCharSequence line) {
        var text = new StringBuilder();
        line.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
        return text.toString();
    }
}
