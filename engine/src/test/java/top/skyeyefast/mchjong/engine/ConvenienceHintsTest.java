package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConvenienceHintsTest {
    @Test void onlyRiichiRoomsOfferConvenienceHints() {
        UUID host = UUID.randomUUID();
        for (TableSession room : List.of(new McrSession(UUID.randomUUID(), 1),
                new SichuanSession(UUID.randomUUID(), 1), new TaiwanSession(UUID.randomUUID(), 1))) {
            room.join(host, "Host", 0);
            assertFalse(room.configureConvenienceHints(host, room.decision(), true));
            assertFalse(room.roomView(host).allowConvenienceHints());
            assertFalse(TableSessionCodec.restore(TableSessionCodec.save(room)).convenienceHints());
        }
    }

    @Test void changingVariantDropsRiichiHintsAndWorldPolicyCanDisableThem() {
        UUID host = UUID.randomUUID();
        var room = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 1);
        room.join(host, "Host", 0);
        assertTrue(room.configureConvenienceHints(host, room.decision(), true));
        var next = room.selectVariant(host, room.incarnation(), room.decision(), MahjongVariant.MCR);
        assertNotNull(next);
        assertFalse(next.convenienceHints());
        assertFalse(next.roomView(host).allowConvenienceHints());
        var restored = TableSessionCodec.restore(TableSessionCodec.save(room));
        assertTrue(restored.convenienceHints());
        restored.configureWorld(new WorldPolicy(false, false, true, 5000, true, true, true, true, null));
        assertFalse(restored.convenienceHints());
        assertFalse(restored.configureConvenienceHints(host, restored.decision(), true));
    }
}
