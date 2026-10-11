package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/** Places captions and focus cards clear of the HUD, controls and private hand. */
final class TableStatusLayout {
    private TableStatusLayout() {}

    static ScreenRectangle of(int width, int height, int textWidth, int textHeight, int gap,
                              List<ScreenRectangle> occupied) {
        int center = width / 2;
        int preferred = height / 4 - textHeight / 2;
        ScreenRectangle best = null;
        for (int top = gap; top + textHeight <= height - gap; top++) {
            int half = Math.min(center - gap, width - gap - center);
            for (var bounds : occupied) {
                if (top + textHeight + gap <= bounds.top() || top >= bounds.bottom() + gap) continue;
                if (bounds.right() <= center) half = Math.min(half, center - bounds.right() - gap);
                else if (bounds.left() >= center) half = Math.min(half, bounds.left() - center - gap);
                else half = 0;
            }
            int span = Math.min(textWidth, 2 * Math.max(0, half));
            if (span == 0) continue;
            if (best == null || span > best.width()
                || span == best.width() && Math.abs(top - preferred) < Math.abs(best.top() - preferred))
                best = new ScreenRectangle(center - span / 2, top, span, textHeight);
        }
        return best;
    }

    static ScreenRectangle focus(int width, int height, int textWidth, int textHeight, int gap,
                                 int preferred, List<ScreenRectangle> occupied) {
        int span = Math.min(textWidth, width / 2 - gap);
        if (span <= 0) return null;
        ScreenRectangle best = null;
        for (int top = gap; top + textHeight <= height - gap; top++) {
            var bounds = new ScreenRectangle(width - gap - span, top, span, textHeight);
            if (occupied.stream().anyMatch(obstacle -> bounds.left() < obstacle.right() + gap
                && bounds.right() + gap > obstacle.left() && bounds.top() < obstacle.bottom() + gap
                && bounds.bottom() + gap > obstacle.top())) continue;
            if (best == null || Math.abs(top - preferred) < Math.abs(best.top() - preferred)) best = bounds;
        }
        return best;
    }
}
