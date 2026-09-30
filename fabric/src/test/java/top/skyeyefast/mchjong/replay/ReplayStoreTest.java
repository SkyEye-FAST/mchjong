package top.skyeyefast.mchjong.replay;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.skyeyefast.mchjong.engine.ReplayHand;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.ReplayWall;
import top.skyeyefast.mchjong.engine.RiichiReplay;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.engine.SichuanAction;
import top.skyeyefast.mchjong.engine.SichuanGame;
import top.skyeyefast.mchjong.engine.SichuanPreset;
import top.skyeyefast.mchjong.engine.SichuanReplay;
import top.skyeyefast.mchjong.engine.SichuanReplayRecorder;
import top.skyeyefast.mchjong.engine.SichuanRules;
import static org.junit.jupiter.api.Assertions.*;

class ReplayStoreTest {
    @TempDir Path directory;
    private final Gson json = new Gson();
    private final UUID owner = new UUID(1, 1), other = new UUID(1, 2), bot = new UUID(1, 3);

    private ReplayMatch match(UUID id, int hands) {
        var players = List.of(new ReplayMatch.Participant(owner, "Owner", false),
            new ReplayMatch.Participant(other, "Guest", false), new ReplayMatch.Participant(bot, "Bot", true));
        List<List<Integer>> dealt = new ArrayList<>();
        List<RiichiView.Seat> seats = new ArrayList<>();
        var available = Tile.set(true);
        for (int seat = 0; seat < 3; seat++) {
            var tiles = List.copyOf(available.subList(seat * 13, (seat + 1) * 13));
            dealt.add(tiles);
            seats.add(new RiichiView.Seat(false, players.get(seat).name(), true, seat == 2, false, 35000, tiles, Tile.ABSENT,
                List.of(), List.of(), List.of(), false, false, false));
        }
        var wall = wall();
        var records = IntStream.range(0, hands).mapToObj(number -> new ReplayHand(number + 1, 0, 0, number, 0,
            List.of(35000,35000,35000), dealt, List.of(132), wall, List.of(), List.of(), seats, List.of(), "nine_terminals",
            List.of(0,0,0), List.of(132), List.of(), List.of(), List.of())).toList();
        return new ReplayMatch(id, new UUID(2, 1), 1, 2, players, MahjongVariant.RIICHI, false,
            new RiichiReplay(RiichiPreset.TENHOU_3.config(), 0, top.skyeyefast.mchjong.engine.RedFives.THREE, records), null, null);
    }

    private ReplayWall wall() {
        var tiles = Tile.set(true);
        int end = tiles.size();
        return new ReplayWall(tiles, 0, IntStream.range(0, RiichiPreset.TENHOU_3.replacementCapacity())
            .map(i -> end - 1 - i % 4).boxed().toList(),
            List.of(end - 5, end - 7, end - 9, end - 11, end - 13),
            List.of(end - 6, end - 8, end - 10, end - 12, end - 14));
    }

    @Test void onlyHumanParticipantsAreIndexedAndAuthorizedEvenWithAForgedIndex() throws Exception {
        var store = new ReplayStore(directory, json);
        var match = match(UUID.randomUUID(), 1);
        store.save(match);
        assertEquals(match, store.load(owner, match.id()));
        assertEquals(match, store.load(other, match.id()));
        assertTrue(store.list(bot, 0, "", false).matches().isEmpty());
        UUID stranger = UUID.randomUUID();
        assertThrows(IOException.class, () -> store.load(stranger, match.id()));
        Path forged = directory.resolve("by-player").resolve(stranger.toString()).resolve(match.id() + ".json");
        Files.createDirectories(forged.getParent());
        Files.copy(directory.resolve("by-player").resolve(owner.toString()).resolve(match.id() + ".json"), forged);
        assertThrows(IOException.class, () -> store.load(stranger, match.id()));
        assertThrows(IOException.class, () -> store.load(bot, match.id()));
        assertThrows(IOException.class, () -> store.delete(stranger, match.id()));
        assertThrows(IOException.class, () -> store.delete(bot, match.id()));
        assertFalse(Files.exists(forged.resolveSibling(match.id() + ".deleted")));
        assertEquals(match, store.load(owner, match.id()));
    }

    @Test void aLaterFinishedHandReplacesTheArchiveWithoutLeakingAnActiveHand() throws Exception {
        var store = new ReplayStore(directory, json);
        UUID id = UUID.randomUUID();
        store.save(match(id, 1));
        var earlier = store.load(owner, id);
        store.save(match(id, 2));
        assertEquals(1, earlier.handCount());
        assertEquals(2, store.load(owner, id).handCount());
        assertEquals(2, store.list(owner, 0, "", false).matches().getFirst().hands());
        String text = Files.readString(directory.resolve(id + ".json"));
        assertFalse(text.contains("\"seed\""));
        assertTrue(text.contains("\"wall\""), "Completed hands retain their sealed physical wall");
        assertFalse(text.contains("\"recorder\""));
        assertFalse(text.contains("\"options\""));
        try (var files = Files.walk(directory)) { assertTrue(files.noneMatch(path -> path.toString().endsWith(".tmp"))); }
    }

    @Test void archiveListsArePaginatedAndEmptyMatchesAreNeverSaved() throws Exception {
        var store = new ReplayStore(directory, json);
        for (int i = 0; i < 13; i++) store.save(match(new UUID(3, i), 1));
        store.save(match(new UUID(3, 0), 1));
        var first = store.list(owner, 0, "", false);
        var second = store.list(owner, 1, "", false);
        assertEquals(12, first.matches().size()); assertTrue(first.more());
        assertEquals(1, second.matches().size()); assertFalse(second.more());
        assertTrue(first.matches().stream().noneMatch(header -> header.id().equals(second.matches().getFirst().id())));
        assertTrue(store.list(owner, 2, "", false).matches().isEmpty());
        assertTrue(store.list(UUID.randomUUID(), 0, "", false).matches().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.list(owner, -1, "", false));
        assertThrows(IllegalArgumentException.class, () -> store.save(match(UUID.randomUUID(), 0)));
    }

    @Test void deletionIsPrivateDurableAndNotUndoneByLaterHands() throws Exception {
        var store = new ReplayStore(directory, json);
        var original = match(UUID.randomUUID(), 1);
        store.save(original);
        store.delete(owner, original.id());
        assertTrue(store.list(owner, 0, "", false).matches().isEmpty());
        assertThrows(IOException.class, () -> store.load(owner, original.id()));
        assertEquals(original, store.load(other, original.id()), "Other participants keep their archive");
        var restarted = new ReplayStore(directory, json);
        restarted.save(match(original.id(), 2));
        assertTrue(restarted.list(owner, 0, "", false).matches().isEmpty());
        assertThrows(IOException.class, () -> restarted.load(owner, original.id()));
        assertEquals(2, restarted.load(other, original.id()).handCount());
        restarted.delete(other, original.id());
        assertFalse(Files.exists(directory.resolve(original.id() + ".json")), "Last reference releases the shared archive");
        restarted.save(match(original.id(), 3));
        assertFalse(Files.exists(directory.resolve(original.id() + ".json")));
    }

    @Test void searchingAndSortingUseMatchMetadataBeforePagination() throws Exception {
        var store = new ReplayStore(directory, json);
        for (int number = 0; number < 14; number++) {
            var value = match(new UUID(7, number), 1);
            store.save(new ReplayMatch(value.id(), value.tableId(), 1, 20 + number, value.participants(),
                value.variant(), false, value.riichi(), null, null));
        }
        assertEquals(new UUID(7, 13), store.list(owner, 0, "oWnEr", false).matches().getFirst().id());
        assertEquals(new UUID(7, 0), store.list(owner, 0, "  GUEST  ", true).matches().getFirst().id());
        assertEquals(2, store.list(owner, 1, "Owner", true).matches().size());
        var specific = store.list(owner, 0, new UUID(7, 3).toString(), false);
        assertEquals(1, specific.matches().size());
        assertEquals(new UUID(7, 3), specific.matches().getFirst().id());
        assertEquals(specific, json.fromJson(json.toJson(specific), ReplayMatch.Index.class));
        assertTrue(store.list(owner, 0, "no such player", false).matches().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.list(owner, 0, "x".repeat(81), false));
        assertThrows(IllegalArgumentException.class, () -> store.list(owner, 100_001, "", true));
    }

    @Test void failedWritesAndOversizedOrCorruptReadsAreReported() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "do not overwrite");
        assertThrows(IOException.class, () -> new ReplayStore(blocked, json).save(match(UUID.randomUUID(), 1)));
        assertEquals("do not overwrite", Files.readString(blocked));
        var store = new ReplayStore(directory.resolve("valid"), json);
        var match = match(UUID.randomUUID(), 1);
        store.save(match);
        Path archive = directory.resolve("valid").resolve(match.id() + ".json");
        for (String invalid : List.of("null", "{broken")) {
            Files.writeString(archive, invalid);
            assertThrows(IOException.class, () -> store.load(owner, match.id()));
        }
        Files.write(archive, new byte[ReplayStore.MAX_BYTES + 1]);
        assertThrows(IOException.class, () -> store.load(owner, match.id()));
    }

    @Test void sichuanArchivesAppearInTheSharedBrowserAndRejectTamperedEvents() throws Exception {
        var rules = SichuanPreset.SBR_2025.config();
        rules = new SichuanRules(rules.fanCap(), rules.selfDrawBonus(), rules.concealedKongPayment(), rules.discardKongPayment(),
            rules.addedKongPayment(), rules.activeFlowerPigPenalty(), rules.transferKongOnShoot(), rules.refundKongWhenNotReady(), 1);
        var game = new SichuanGame(711, rules, Tile.sichuanSet());
        var recorder = new SichuanReplayRecorder(game);
        for (int step = 0; step < 1000 && !game.ended(); step++) for (int seat = 0; seat < 4; seat++) {
            var actions = game.actions(seat);
            if (actions.isEmpty()) continue;
            int selected = 0;
            for (int index = 0; index < actions.size(); index++) if (actions.get(index).type() == SichuanAction.Type.PASS) {
                selected = index; break;
            }
            var before = game.save();
            assertTrue(game.act(seat, game.decision(), selected));
            recorder.accepted(before, seat, actions, selected, game);
            break;
        }
        assertTrue(game.ended());
        var players = List.of(new ReplayMatch.Participant(owner, "Owner", false), new ReplayMatch.Participant(other, "Guest", false),
            new ReplayMatch.Participant(bot, "Third", false), new ReplayMatch.Participant(new UUID(1, 4), "Fourth", false));
        var match = new ReplayMatch(UUID.randomUUID(), new UUID(2, 1), 1, 2, players, MahjongVariant.SICHUAN, true,
            null, null, new SichuanReplay(rules, List.of(recorder.finish(game))));
        var store = new ReplayStore(directory, json);
        store.save(match);
        var index = store.list(owner, 0, "Owner", false);
        assertEquals(List.of(match.header()), index.matches());
        assertEquals(MahjongVariant.SICHUAN, index.matches().getFirst().variant());
        assertEquals(game.scores().stream().map(Integer::doubleValue).toList(), index.matches().getFirst().finalScores());
        assertEquals(match, store.load(owner, match.id()));
        assertThrows(IOException.class, () -> store.load(UUID.randomUUID(), match.id()));
        var tree = com.google.gson.JsonParser.parseString(Files.readString(directory.resolve(match.id() + ".json"))).getAsJsonObject();
        tree.getAsJsonObject("sichuan").getAsJsonArray("hands").get(0).getAsJsonObject().getAsJsonArray("events")
            .get(0).getAsJsonObject().addProperty("seat", 3);
        Files.writeString(directory.resolve(match.id() + ".json"), tree.toString());
        assertThrows(IOException.class, () -> store.load(owner, match.id()));
    }
}
