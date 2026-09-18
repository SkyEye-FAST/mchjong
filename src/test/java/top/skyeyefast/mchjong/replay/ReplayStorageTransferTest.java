package top.skyeyefast.mchjong.replay;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.skyeyefast.mchjong.engine.ReplayHand;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.network.ReplayPayload;
import static org.junit.jupiter.api.Assertions.*;

class ReplayStorageTransferTest {
    @TempDir Path directory;
    private static final UUID PLAYER = new UUID(1,1);
    private final Gson json = new Gson();

    @Test void canonicalArchiveRoundTripsButForeignAndBotAccessAreDenied() throws Exception {
        var store = new ReplayStore(directory, json);
        var match = fixture();
        store.save(match);
        assertEquals(match, store.load(PLAYER, match.id()));
        UUID outsider = UUID.randomUUID();
        assertTrue(store.list(outsider,0,"",false).matches().isEmpty());
        assertThrows(IOException.class, () -> store.load(outsider, match.id()));
        assertThrows(IOException.class, () -> store.load(new UUID(1,3), match.id()));
        // A forged or stale index is not sufficient to bypass the actual participant ACL.
        Path forged = directory.resolve("by-player").resolve(outsider.toString()).resolve(match.id() + ".json");
        Files.createDirectories(forged.getParent());
        Files.writeString(forged,json.toJson(match.header()));
        assertThrows(IOException.class, () -> store.load(outsider, match.id()));
    }

    @Test void indexesPageAtTwelveAndRepeatedSavesDoNotDuplicateEntries() throws Exception {
        var store = new ReplayStore(directory, json);
        var repeated = fixture();
        store.save(repeated); store.save(repeated);
        for (int i = 0; i < 12; i++) store.save(fixture());
        var first = store.list(PLAYER,0,"",false);
        assertEquals(12,first.matches().size()); assertTrue(first.more());
        var second = store.list(PLAYER,1,"",false);
        assertEquals(1,second.matches().size()); assertFalse(second.more());
        assertTrue(store.list(PLAYER,2,"",false).matches().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.list(PLAYER,-1,"",false));
    }

    @Test void malformedAndOversizedArchivesAreRejectedWithoutReturningPartialData() throws Exception {
        var store = new ReplayStore(directory,json);
        var match = fixture(); store.save(match);
        Path file = directory.resolve(match.id() + ".json");
        Files.writeString(file,"null");
        assertThrows(IOException.class, () -> store.load(PLAYER,match.id()));
        Files.writeString(file,"{");
        assertThrows(IOException.class, () -> store.load(PLAYER,match.id()));
        Files.write(file,new byte[ReplayStore.MAX_BYTES + 1]);
        assertThrows(IOException.class, () -> store.load(PLAYER,match.id()));
    }

    @Test void splitPayloadPreservesSupplementaryCharactersAndReassemblesOnlyAtCompletion() {
        String original = "x".repeat(ReplayPayload.CHUNK_SIZE - 1) + "🀄" + "牌".repeat(ReplayPayload.CHUNK_SIZE);
        var chunks = ReplayPayload.split(ReplayPayload.Kind.MATCH, original);
        var transfer = new ReplayTransfer();
        for (int i = 0; i < chunks.size() - 1; i++) assertNull(transfer.accept(chunks.get(i),i));
        assertEquals(original,transfer.accept(chunks.getLast(),10).text());
        for (var chunk : chunks) assertFalse(Character.isHighSurrogate(chunk.text().charAt(chunk.text().length()-1)));
    }

    @Test void mismatchedOutOfOrderAndExpiredTransfersAreDroppedAndNextRequestCanSucceed() {
        var chunks = ReplayPayload.split(ReplayPayload.Kind.MATCH,"a".repeat(ReplayPayload.CHUNK_SIZE + 1));
        var transfer = new ReplayTransfer();
        assertThrows(IllegalArgumentException.class, () -> transfer.accept(chunks.get(1),0));
        assertNull(transfer.accept(chunks.getFirst(),0));
        transfer.expire(30_000);
        assertThrows(IllegalArgumentException.class, () -> transfer.accept(chunks.get(1),30_000));
        assertNull(transfer.accept(chunks.getFirst(),40_000));
        var wrong = new ReplayPayload(chunks.getFirst().transfer(),ReplayPayload.Kind.INDEX,1,2,"x");
        assertThrows(IllegalArgumentException.class, () -> transfer.accept(wrong,40_001));
        assertEquals("{}",transfer.accept(ReplayPayload.split(ReplayPayload.Kind.INDEX,"{}").getFirst(),50_000).text());
        assertThrows(IllegalArgumentException.class, () -> new ReplayPayload(UUID.randomUUID(),ReplayPayload.Kind.MATCH,0,513,""));
        assertThrows(IllegalArgumentException.class, () -> new ReplayPayload(UUID.randomUUID(),ReplayPayload.Kind.MATCH,0,1,"x".repeat(ReplayPayload.CHUNK_SIZE+1)));
    }

    private ReplayMatch fixture() {
        var players = new ArrayList<ReplayMatch.Participant>();
        var hands = new ArrayList<List<Integer>>();
        var seats = new ArrayList<TableView.Seat>();
        for (int seat = 0; seat < 3; seat++) {
            players.add(new ReplayMatch.Participant(new UUID(1,seat+1),"Player " + seat, seat == 2));
            hands.add(Tile.set(true).subList(seat*13,seat*13+13));
            seats.add(new TableView.Seat("Player " + seat,true,seat==2,false,35000,hands.get(seat),-2,List.of(),List.of(),List.of(),false,false));
        }
        var hand = new ReplayHand(1,0,0,0,0,Collections.nCopies(3,35000),hands,List.of(132),List.of(),seats,
            List.of(),"exhaustive",Collections.nCopies(3,0),List.of(132),List.of(),List.of(),List.of());
        return new ReplayMatch(UUID.randomUUID(),UUID.randomUUID(),1,2,RuleSet.TENHOU_3,0,players,List.of(hand),false,
            top.skyeyefast.mchjong.engine.RedFives.THREE);
    }
}
