package top.skyeyefast.mchjong.replay;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.network.ReplayPayload;
import static org.junit.jupiter.api.Assertions.*;

class ReplayTransferTest {
    @Test void chunkingRoundTripsSupplementaryUnicodeAtPacketBoundaries() {
        String original = "a".repeat(ReplayPayload.CHUNK_SIZE - 1) + "𠮷" + "牌譜🀄".repeat(10_000);
        var transfer = new ReplayTransfer();
        ReplayTransfer.Completed completed = null;
        var chunks = ReplayPayload.split(ReplayPayload.Kind.MATCH, original);
        assertTrue(chunks.size() > 2);
        for (var chunk : chunks) {
            assertEquals(chunk.text(), new String(chunk.text().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            completed = transfer.accept(chunk, 100);
            if (chunk.part() + 1 < chunks.size()) assertNull(completed);
        }
        assertNotNull(completed);
        assertEquals(original, completed.text());
        assertEquals(ReplayPayload.Kind.MATCH, completed.kind());
    }

    @Test void outOfOrderExpiredOrMixedTransfersCannotProduceAReplay() {
        var chunks = ReplayPayload.split(ReplayPayload.Kind.MATCH, "x".repeat(ReplayPayload.CHUNK_SIZE + 1));
        var transfer = new ReplayTransfer();
        assertThrows(IllegalArgumentException.class, () -> transfer.accept(chunks.get(1), 0));
        assertNull(transfer.accept(chunks.get(0), 0));
        assertThrows(IllegalArgumentException.class, () -> transfer.accept(chunks.get(1), 30_001));
        assertNull(transfer.accept(chunks.get(0), 0));
        var wrongKind = new ReplayPayload(chunks.getFirst().transfer(), ReplayPayload.Kind.INDEX, 1, 2, "x");
        assertThrows(IllegalArgumentException.class, () -> transfer.accept(wrongKind, 1));
        assertNull(transfer.accept(chunks.get(0), 0));
        transfer.reset();
        assertThrows(IllegalArgumentException.class, () -> transfer.accept(chunks.get(1), 1));
    }

    @Test void oneNewTransferSupersedesTheOldOneAndMalformedHeadersAreRejected() {
        var transfer = new ReplayTransfer();
        var chunks = ReplayPayload.split(ReplayPayload.Kind.MATCH, "x".repeat(ReplayPayload.CHUNK_SIZE + 1));
        assertNull(transfer.accept(chunks.getFirst(), 0));
        assertEquals("new", transfer.accept(ReplayPayload.split(ReplayPayload.Kind.INDEX, "new").getFirst(), 1).text());
        assertThrows(IllegalArgumentException.class, () -> transfer.accept(chunks.get(1), 2));
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new ReplayPayload(id, ReplayPayload.Kind.MATCH, -1, 1, ""));
        assertThrows(IllegalArgumentException.class, () -> new ReplayPayload(id, ReplayPayload.Kind.MATCH, 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new ReplayPayload(id, ReplayPayload.Kind.MATCH, 0, ReplayPayload.MAX_PARTS + 1, ""));
        assertThrows(IllegalArgumentException.class, () -> new ReplayPayload(id, ReplayPayload.Kind.MATCH, 0, 1, "a".repeat(ReplayPayload.CHUNK_SIZE + 1)));
    }
}
