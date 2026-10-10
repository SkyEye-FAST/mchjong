package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

/** Handbook and in-game explanations draw the playing atlas directly, at readable tile sizes. */
public record TileDiagram(List<Part> parts, TileFacePreset preset) {
    public record Part(int face, boolean groupStart, String marker) {}
    public record Spread(int split, int tileWidth) {
        public boolean across() { return split > 0; }
    }

    public static TileDiagram parse(String notation, TileFacePreset preset) {
        var parts = new ArrayList<Part>();
        String marker = "";
        for (String group : notation.replace(";", " ; ").split("\\s+")) {
            if (group.equals("+") || group.equals("/") || group.equals(";")) { marker = group; continue; }
            char suit = group.charAt(group.length() - 1);
            int base = switch (suit) { case 'm' -> 0; case 'p' -> 9; case 's' -> 18; case 'z' -> 27; case 'q' -> 37;
                default -> throw new IllegalArgumentException("Unknown diagram suit: " + group); };
            for (int i = 0; i < group.length() - 1; i++) {
                int number = group.charAt(i) - '0';
                int face = number == 0 ? 34 + base / 9 : base + number - 1;
                parts.add(new Part(face, i == 0 && !parts.isEmpty(), i == 0 ? marker : ""));
            }
            marker = "";
        }
        return new TileDiagram(List.copyOf(parts), preset);
    }

    public int width(int start, int end, int tileWidth) {
        int width = (end - start) * tileWidth;
        for (int i = start + 1; i < end; i++) if (parts.get(i).groupStart()) width++;
        return width;
    }

    public Spread spread(int pageWidth) {
        if (width(0, parts.size(), 18) <= pageWidth) return new Spread(0, 18);
        for (int tileWidth = 18; tileWidth >= 12; tileWidth--) {
            int best = -1, difference = Integer.MAX_VALUE;
            for (int split = 1; split < parts.size(); split++) {
                int left = width(0, split, tileWidth), right = width(split, parts.size(), tileWidth);
                if (left > pageWidth || right > pageWidth) continue;
                int cost = Math.abs(left - right) + (parts.get(split).groupStart() ? 0 : 1000);
                if (cost < difference) { best = split; difference = cost; }
            }
            if (best > 0) return new Spread(best, tileWidth);
        }
        int split = parts.size() / 2;
        for (int i = 1; i < parts.size(); i++)
            if (parts.get(i).groupStart() && Math.abs(i * 2 - parts.size()) < Math.abs(split * 2 - parts.size()) + 2)
                split = i;
        return new Spread(split, 12);
    }

    public int rowEnd(int start, int end, int tileWidth, int available) {
        int fit = start + 1;
        while (fit < end && width(start, fit + 1, tileWidth) <= available) fit++;
        if (fit == end) return end;
        for (int i = fit; i > start; i--) if (parts.get(i).groupStart()) return i;
        return fit;
    }

    public void render(GuiGraphicsExtractor graphics, int x, int y, int tileWidth, int start, int end) {
        for (int i = start; i < end; i++) {
            var part = parts.get(i);
            if (i > start && part.groupStart()) x++;
            TileGui.tileArtwork(graphics, part.face(), x, y, tileWidth, false, false, part.marker().equals("+"), false,
                0, preset, TileMaterial.BONE, null, TileBackPresets.DEFAULT, face -> face);
            if (!part.marker().isEmpty()) graphics.text(net.minecraft.client.Minecraft.getInstance().font,
                part.marker(), x + (tileWidth - 5) / 2, y - 9, 0xff365851, false);
            x += tileWidth;
        }
    }
}
