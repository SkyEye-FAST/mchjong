package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import top.skyeyefast.mchjong.engine.McrDiscard;

/** Six-column, face-up MCR discard grid in unscaled seat-local tile units. */
public final class McrRiverLayout {
    public static final int COLUMNS = 6;
    public record Part(int tile, int historyIndex, int column, int row) {}
    private McrRiverLayout() {}

    public static List<Part> of(List<McrDiscard> river) {
        var parts = new ArrayList<Part>();
        for (int history = 0; history < river.size(); history++) {
            var discard = river.get(history);
            if (discard.called()) continue;
            int column = parts.size() % COLUMNS, row = parts.size() / COLUMNS;
            parts.add(new Part(discard.tile(), history, column, row));
        }
        return List.copyOf(parts);
    }
}
