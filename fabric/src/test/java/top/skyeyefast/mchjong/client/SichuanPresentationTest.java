package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class SichuanPresentationTest {
    @Test void wallUsesAll108PhysicalSlotsWithoutCornerIntersections() {
        var pieces = SichuanTableScene.fullWall();
        assertEquals(108, pieces.size());
        assertEquals(108, pieces.stream().map(SichuanTableScene.Piece::index).distinct().count());
        for (int seat = 0; seat < 4; seat++) {
            int owner = seat;
            assertEquals(SichuanWallLayout.stacks(seat) * 2, pieces.stream().filter(piece -> piece.seat() == owner).count());
        }
        for (var piece : pieces) {
            assertEquals(Tile.HIDDEN, piece.tile());
            assertTrue(piece.back());
            var bounds = bounds(piece);
            assertTrue(Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)) < TableGeometry.FELT_HALF_WIDTH);
            assertTrue(Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ)) < TableGeometry.FELT_HALF_WIDTH);
        }
        for (int first = 0; first < pieces.size(); first++) for (int second = first + 1; second < pieces.size(); second++)
            assertFalse(bounds(pieces.get(first)).deflate(1e-7).intersects(bounds(pieces.get(second)).deflate(1e-7)));
    }

    @Test void scenesConsumeOnlyRecipientSafeViewsAndKeepPublicClaimIdentity() {
        var game = new SichuanGame(711);
        var declaration = game.view(0);
        assertTrue(SichuanResults.rows(declaration, room(false)).isEmpty());
        var pieces = SichuanTableScene.build(declaration);
        assertTrue(pieces.stream().filter(piece -> piece.area() == SichuanTableScene.Area.HAND && piece.seat() != 0)
            .allMatch(piece -> piece.tile() == Tile.HIDDEN));
        assertEquals("sichuan.mchjong.void_pending", key(SichuanTableScene.status(declaration.seats().get(1))));
        var seats = new ArrayList<>(declaration.seats());
        var kong = new Meld(Meld.Type.CONCEALED_QUAD, List.of(Tile.HIDDEN, 1, 2, Tile.HIDDEN), 1, Tile.ABSENT);
        seats.set(1, new SichuanView.Seat(Collections.nCopies(10, Tile.HIDDEN), List.of(kong), List.of(), 2, true, Tile.HIDDEN));
        seats.set(0, new SichuanView.Seat(List.of(8, 4), List.of(), List.of(
            new SichuanPlayerState.Discard(16, true), new SichuanPlayerState.Discard(20, false)), 2, false, 4));
        var score = new SichuanSettlement.Score(0, 1, List.of());
        var winners = List.of(new SichuanView.Winner(1, 0, 16, false, false, score), new SichuanView.Winner(2, 0, 16, false, false, score));
        var live = view(SichuanGame.Phase.TURN, seats, winners, List.of(), null);
        pieces = SichuanTableScene.build(live);
        var meld = pieces.stream().filter(piece -> piece.area() == SichuanTableScene.Area.MELD).toList();
        assertEquals(List.of(true, false, false, true), meld.stream().map(SichuanTableScene.Piece::back).toList());
        assertTrue(pieces.stream().filter(piece -> piece.area() == SichuanTableScene.Area.HAND && piece.seat() == 1)
            .allMatch(piece -> piece.tile() == Tile.HIDDEN && !piece.flat()));
        assertEquals(List.of(0, 1), pieces.stream().filter(piece -> piece.area() == SichuanTableScene.Area.HAND && piece.seat() == 0)
            .map(SichuanTableScene.Piece::index).toList());
        var river = pieces.stream().filter(piece -> piece.area() == SichuanTableScene.Area.RIVER).toList();
        assertEquals(1, river.size());
        assertEquals(1, river.getFirst().index());
        assertEquals(List.of(new SichuanTableScene.Claim(16, 0, List.of(1, 2))), SichuanTableScene.claims(live));
        assertTrue(SichuanTableScene.immersive(live).stream().noneMatch(piece -> piece.area() == SichuanTableScene.Area.WALL));
    }

    @Test void resultsPresentEveryLedgerTypeAndLinkWithoutRecomputingPayments() {
        var ledger = new ArrayList<SichuanSettlement.Entry>();
        for (var type : SichuanSettlement.Type.values()) {
            int related = type == SichuanSettlement.Type.KONG_REFUND || type == SichuanSettlement.Type.KONG_TRANSFER
                || type == SichuanSettlement.Type.KONG_TRANSFER_TOP_UP ? 2 : -1;
            ledger.add(new SichuanSettlement.Entry(ledger.size(), type, 0, type == SichuanSettlement.Type.FLOWER_PIG ? -1 : 1, 4, related));
        }
        var score = new SichuanSettlement.Score(3, 8, List.of(SichuanSettlement.Fan.FULL_FLUSH, SichuanSettlement.Fan.ROOT));
        var win = new SichuanSettlement.Win(1, 0, 16, false, false, score);
        var result = new SichuanSettlement.Result(List.of(win), ledger, List.of(SichuanSettlement.DrawStatus.NOT_READY,
            SichuanSettlement.DrawStatus.WON, SichuanSettlement.DrawStatus.READY, SichuanSettlement.DrawStatus.ACTIVE_FLOWER_PIG), true);
        var seats = Collections.nCopies(4, new SichuanView.Seat(List.of(16), List.of(), List.of(), 2, false, Tile.ABSENT));
        var ended = view(SichuanGame.Phase.HAND_END, seats, List.of(), ledger, result);
        var rows = SichuanResults.rows(ended, room(false));
        assertEquals(result.deltas().get(0).toString(), ((TranslatableContents) rows.get(1).text().getContents()).getArgs()[1]);
        assertEquals(100, ((TranslatableContents) rows.get(1).text().getContents()).getArgs()[2]);
        var paymentRows = rows.stream().filter(row -> row.text().getString().startsWith("#")).toList();
        assertEquals(ledger.size(), paymentRows.size());
        for (int index = 0; index < ledger.size(); index++) {
            var entry = ledger.get(index);
            assertEquals("sichuan.mchjong.payment." + entry.type().name().toLowerCase(java.util.Locale.ROOT),
                key(paymentRows.get(index).text().getSiblings().getFirst()));
            if (entry.relatedEntry() >= 0) assertTrue(paymentRows.get(index).text().getSiblings().stream()
                .anyMatch(part -> key(part).equals("sichuan.mchjong.related")));
        }
        assertTrue(rows.stream().anyMatch(row -> key(row.text()).equals("sichuan.mchjong.fan.full_flush")));
        assertTrue(rows.stream().anyMatch(row -> row.tiles().equals(List.of(16))));
        var finalView = view(SichuanGame.Phase.MATCH_END, seats, List.of(), ledger, result);
        assertEquals(8, finalView.handNumber());
        assertEquals(rows, SichuanResults.rows(finalView, room(true)));
    }

    private static String key(net.minecraft.network.chat.Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }
    private static SichuanView view(SichuanGame.Phase phase, List<SichuanView.Seat> seats, List<SichuanView.Winner> winners,
                                   List<SichuanSettlement.Entry> ledger, SichuanSettlement.Result result) {
        return new SichuanView(1, 1, new SichuanGame(711).rules(), phase, phase == SichuanGame.Phase.MATCH_END ? 8 : 1, 0,
            List.of(100, 200, 300, 400), 0, 0, new SichuanView.Wall(Collections.nCopies(108, Tile.ABSENT), 0, 1, 1),
            seats, Tile.ABSENT, -1, false, false, List.of(), winners, ledger, result);
    }
    private static TableRoomView room(boolean finished) {
        var seats = java.util.stream.IntStream.range(0, 4).mapToObj(seat -> new TableRoomView.Seat(
            new TableParticipant(new UUID(42, seat + 1), "player" + seat), PlayerPresence.SEATED, seat)).toList();
        return new TableRoomView(new UUID(41, 1), new UUID(41, 2), 1, 1, MahjongVariant.SICHUAN,
            finished ? TableSession.Lifecycle.FINISHED : TableSession.Lifecycle.PLAYING, 0, 0, false, true, false,
            RoomSeating.Stage.POSITIONING, List.of(), seats, List.of(), null, false);
    }
    private static AABB bounds(SichuanTableScene.Piece piece) {
        double horizontal = piece.seat() % 2 == 0 ? SichuanTableScene.WIDTH : SichuanTableScene.HEIGHT;
        double depth = piece.seat() % 2 == 0 ? SichuanTableScene.HEIGHT : SichuanTableScene.WIDTH;
        var position = piece.position();
        return new AABB(position.x - horizontal / 2, position.y - SichuanTableScene.DEPTH / 2, position.z - depth / 2,
            position.x + horizontal / 2, position.y + SichuanTableScene.DEPTH / 2, position.z + depth / 2);
    }
}
