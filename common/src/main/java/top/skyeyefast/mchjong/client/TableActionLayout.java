package top.skyeyefast.mchjong.client;

/** The action strip uses the same compact grid in every ruleset. */
record TableActionLayout(int columns, int width, int height, int gap, int left, int top, int spanHeight) {
    static TableActionLayout of(int count, int canvasWidth, int canvasHeight, int handTop,
                               boolean immersive, int automationWidth) {
        int columns, width, height, gap, left, top;
        if (immersive) {
            columns = Math.max(1, Math.min(3, count));
            width = Math.clamp((416 - (columns - 1) * 8) / columns, 104, 168);
            height = 40; gap = 8;
            left = canvasWidth - columns * width - (columns - 1) * gap - 32;
        } else {
            int available = canvasWidth - 20 - automationWidth;
            columns = Math.min(Math.max(1, count), Math.max(1, Math.min(3, available / 88)));
            width = Math.min(132, (available - (columns - 1) * 4) / columns);
            height = 26; gap = 4;
            left = canvasWidth - 10 - columns * (width + gap) + gap;
        }
        int rows = Math.max(1, (count + columns - 1) / columns);
        int span = rows * (height + gap) - gap;
        top = (handTop < 0 ? canvasHeight - (immersive ? 112 : 24) : handTop - 12) - span;
        return new TableActionLayout(columns, width, height, gap, left, top, span);
    }
    int x(int slot) { return left + slot % columns * (width + gap); }
    int y(int slot) { return top + slot / columns * (height + gap); }
}
