package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.ReplayHand;
import top.skyeyefast.mchjong.engine.ReplayDecisionAnalysis;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.Tile;

/** Read-only candidate list for the decision at the currently displayed replay position. */
final class ReplayDecisionPanel extends AbstractWidget {
    private final Font font;
    private final ReplayMatch match;
    private ReplayHand.Decision decision;
    private List<ReplayDecisionAnalysis.Candidate> analysis = List.of();
    private final List<Hit> hits = new ArrayList<>();
    private record Hit(int x, int y, int width, int height, Component tooltip) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }

    ReplayDecisionPanel(Font font, ReplayMatch match, int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("replay.mchjong.decision"));
        this.font = font;
        this.match = match;
    }

    void show(ReplayHand.Decision decision, List<ReplayDecisionAnalysis.Candidate> analysis) {
        this.decision = decision;
        this.analysis = analysis == null ? List.of() : analysis;
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (decision == null) return;
        hits.clear();
        MahjongUi.panel(graphics, getX(), getY(), width, height);
        var title = Component.translatable("replay.mchjong.decision_for", match.participants().get(decision.seat()).name());
        MahjongUi.text(graphics, font, title, getX() + 7, getY() + 6, width - 14, MahjongUi.ACCENT, false);
        int count = decision.options().size();
        int columns = count > 7 && width >= 130 ? 2 : 1;
        int rows = (count + columns - 1) / columns;
        int colWidth = (width - 12) / columns;
        int rowHeight = Math.max(11, Math.min(17, (height - 25) / Math.max(1, rows)));
        for (int index = 0; index < count; index++) {
            int col = index % columns, row = index / columns;
            int x = getX() + 6 + col * colWidth, y = getY() + 20 + row * rowHeight;
            boolean selected = index == decision.selected();
            if (selected) graphics.fill(x, y, x + colWidth - 3, y + rowHeight - 1, MahjongUi.SELECTED);
            var label = label(decision.options().get(index));
            var candidate = index < analysis.size() ? analysis.get(index) : null;
            if (candidate != null) label = label.copy().append(" · ").append(shape(candidate));
            MahjongUi.text(graphics, font, label, x + 3, y + 2, colWidth - 9,
                selected ? MahjongUi.ACCENT : MahjongUi.TEXT, false);
            if (candidate != null && !candidate.improving().isEmpty()) hits.add(new Hit(x, y, colWidth - 3, rowHeight - 1, detail(candidate)));
        }
        for (var hit : hits) if (hit.contains(mouseX, mouseY)) {
            graphics.renderTooltip(font, font.split(hit.tooltip(), Math.min(300, graphics.guiWidth() - 24)), mouseX, mouseY);
            break;
        }
    }

    private Component shape(ReplayDecisionAnalysis.Candidate candidate) {
        return candidate.shanten() == 0
            ? Component.translatable("replay.mchjong.tenpai_live", candidate.live())
            : Component.translatable("replay.mchjong.shanten_live", candidate.shanten(), candidate.live());
    }

    private Component detail(ReplayDecisionAnalysis.Candidate candidate) {
        var text = Component.translatable("replay.mchjong.improving");
        for (var improvement : candidate.improving()) text = text.copy().append(" ")
            .append(Component.literal(Tile.notation(improvement.kind()) + "×" + improvement.remaining()));
        return text;
    }

    private Component label(Action action) {
        var text = Component.translatable(action.translationKey());
        if (action.tiles().isEmpty()) return text;
        var suffix = new StringBuilder();
        for (int tile : action.tiles()) {
            if (suffix.length() > 0) suffix.append(' ');
            int kind = Tile.kind(tile);
            if (Tile.red(tile) && kind < 27 && kind % 9 == 4) suffix.append('0').append("mps".charAt(kind / 9));
            else suffix.append(Tile.notation(kind));
        }
        return text.copy().append(" · ").append(suffix.toString());
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        if (decision == null) return;
        var summary = Component.translatable("replay.mchjong.decision_for", match.participants().get(decision.seat()).name());
        summary.append(". ").append(Component.translatable("replay.mchjong.selected"))
            .append(" ").append(label(decision.options().get(decision.selected())));
        output.add(NarratedElementType.TITLE, summary);
    }
}
