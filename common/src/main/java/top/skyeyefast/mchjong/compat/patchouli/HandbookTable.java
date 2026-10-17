package top.skyeyefast.mchjong.compat.patchouli;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import vazkii.patchouli.api.ICustomComponent;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

/**
 * A page of tables read from one translation key. Blank lines separate tables. In each table a
 * line is a row, {@code |} separates cells, {@code $(br)} breaks a line inside a cell, the first
 * row is the header and a leading line without {@code |} is the caption. Column widths follow the
 * cell text and long cells wrap, so the author keeps the tables within one page.
 */
public final class HandbookTable implements ICustomComponent {
    private static final int LEFT = 4;
    private static final int TOP = 4;
    private static final int WIDTH = 108;
    private static final int GAP = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 1;
    private static final int BLOCK_GAP = 6;

    private record Block(Component caption, List<List<List<FormattedCharSequence>>> rows, int[] widths) {}

    private transient String text;
    private transient List<Block> blocks = List.of();

    @Override public void onVariablesAvailable(UnaryOperator<IVariable> lookup) {
        text = lookup.apply(IVariable.wrap("#text#")).asString();
    }

    @Override public void build(int x, int y, int pageNum) {}

    @Override public void onDisplayed(IComponentRenderContext context) {
        blocks = new ArrayList<>();
        for (String block : Component.translatable(text).getString().strip().split("\n\\s*\n")) blocks.add(layout(block, context.getFont()));
    }

    @Override public void render(GuiGraphics graphics, IComponentRenderContext context, float ticks, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int y = TOP;
        for (Block block : blocks) {
            if (block.caption != null) {
                graphics.drawString(font, block.caption, LEFT, y, context.getHeaderColor(), false);
                y += LINE_HEIGHT + PADDING;
            }
            for (int r = 0; r < block.rows.size(); r++) {
                int height = 0;
                int x = LEFT;
                for (int c = 0; c < block.widths.length; c++) {
                    var lines = block.rows.get(r).get(c);
                    for (int i = 0; i < lines.size(); i++)
                        graphics.drawString(font, lines.get(i), x, y + PADDING + i * LINE_HEIGHT, context.getTextColor(), false);
                    height = Math.max(height, lines.size() * LINE_HEIGHT);
                    x += block.widths[c] + GAP;
                }
                y += height + PADDING * 2;
                graphics.fill(LEFT, y - 1, LEFT + WIDTH, y, r == 0 ? 0xaa4c686b : 0x554c686b);
            }
            y += BLOCK_GAP;
        }
    }

    private static Block layout(String block, Style base) {
        var font = Minecraft.getInstance().font;
        List<String> lines = new ArrayList<>(List.of(block.split("\n")));
        Component caption = null;
        if (!lines.get(0).contains("|")) caption = Component.literal(lines.remove(0).strip()).withStyle(base).withStyle(ChatFormatting.BOLD);
        List<List<String[]>> cells = new ArrayList<>();
        int columns = 1;
        for (String line : lines) {
            List<String[]> row = new ArrayList<>();
            for (String cell : line.split("\\|", -1)) row.add(cell.strip().split("\\$\\(br\\)"));
            columns = Math.max(columns, row.size());
            cells.add(row);
        }
        int[] natural = new int[columns];
        for (int r = 0; r < cells.size(); r++)
            for (int c = 0; c < cells.get(r).size(); c++)
                for (String line : cells.get(r).get(c)) natural[c] = Math.max(natural[c], font.width(cell(line, r == 0, base)));
        int[] widths = share(natural, WIDTH - GAP * (columns - 1));
        List<List<List<FormattedCharSequence>>> rows = new ArrayList<>();
        for (int r = 0; r < cells.size(); r++) {
            List<List<FormattedCharSequence>> row = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                List<FormattedCharSequence> wrapped = new ArrayList<>();
                if (c < cells.get(r).size()) for (String line : cells.get(r).get(c)) wrapped.addAll(font.split(cell(line, r == 0, base), widths[c]));
                row.add(wrapped);
            }
            rows.add(row);
        }
        return new Block(caption, rows, widths);
    }

    private static FormattedText cell(String text, boolean header, Style base) {
        return FormattedText.of(text, header ? base.withBold(true) : base);
    }

    /** Gives narrow columns their natural width and splits the rest evenly among the wide ones. */
    private static int[] share(int[] natural, int available) {
        int[] widths = new int[natural.length];
        boolean[] fixed = new boolean[natural.length];
        int open = natural.length;
        boolean changed = true;
        while (changed && open > 0) {
            changed = false;
            int each = available / open;
            for (int c = 0; c < natural.length; c++) {
                if (!fixed[c] && natural[c] <= each) {
                    widths[c] = natural[c];
                    fixed[c] = true;
                    available -= natural[c];
                    open--;
                    changed = true;
                }
            }
        }
        for (int c = 0; c < natural.length; c++) if (!fixed[c]) widths[c] = available / open;
        return widths;
    }
}
