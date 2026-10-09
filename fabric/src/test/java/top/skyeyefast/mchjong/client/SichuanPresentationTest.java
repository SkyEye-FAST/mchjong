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
        for (boolean eastWestLongWall : new boolean[]{false, true}) verifyWall(eastWestLongWall);
    }

    private static void verifyWall(boolean eastWestLongWall) {
        var pieces = SichuanTableScene.fullWall(eastWestLongWall);
        assertEquals(108, pieces.size());
        assertEquals(108, pieces.stream().map(SichuanTableScene.Piece::index).distinct().count());
        int firstStacks = SichuanWallLayout.stacks(0, eastWestLongWall);
        var firstDirection = pieces.get(0).position().subtract(pieces.get((firstStacks - 1) * 2).position()).normalize();
        for (int seat = 0; seat < 4; seat++) {
            int owner = seat;
            assertEquals(((seat % 2 == 0) == eastWestLongWall ? 28 : 26), pieces.stream().filter(piece -> piece.seat() == owner).count());
            int stacks = SichuanWallLayout.stacks(seat, eastWestLongWall);
            var upper = new ArrayList<SichuanTableScene.Piece>();
            for (int column = 0; column < stacks; column++) {
                int upperSlot = SichuanWallLayout.slot(seat, column, 0, eastWestLongWall);
                int lowerSlot = SichuanWallLayout.slot(seat, column, 1, eastWestLongWall);
                var top = pieces.stream().filter(piece -> piece.index() == upperSlot).findFirst().orElseThrow();
                var bottom = pieces.stream().filter(piece -> piece.index() == lowerSlot).findFirst().orElseThrow();
                assertEquals(top.position().x, bottom.position().x);
                assertEquals(top.position().z, bottom.position().z);
                assertEquals(SichuanTableScene.DEPTH, top.position().y - bottom.position().y, 1e-8);
                assertEquals(top.yaw(), bottom.yaw());
                upper.add(top);
            }
            WallGeometryAssertions.tiltedSide(upper.stream().map(SichuanTableScene.Piece::position).toList(),
                upper.stream().map(SichuanTableScene.Piece::yaw).toList(), seat, firstDirection, SichuanTableScene.WIDTH);
            WallGeometryAssertions.liftRail(upper.stream().map(SichuanTableScene.Piece::position).toList(), seat, SichuanTableScene.HEIGHT);
        }
        for (var piece : pieces) {
            assertEquals(Tile.HIDDEN, piece.tile());
            assertTrue(piece.back());
            var bounds = bounds(piece);
            assertTrue(Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)) < TableGeometry.FELT_HALF_WIDTH);
            assertTrue(Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ)) < TableGeometry.FELT_HALF_WIDTH);
            WallGeometryAssertions.clearCenter(piece.position(), piece.yaw(), SichuanTableScene.WIDTH,
                SichuanTableScene.HEIGHT, SichuanTableScene.DEPTH);
        }
        for (int first = 0; first < pieces.size(); first++) for (int second = first + 1; second < pieces.size(); second++)
            assertFalse(WallGeometryAssertions.intersects(pieces.get(first).position(), pieces.get(first).yaw(),
                pieces.get(second).position(), pieces.get(second).yaw(), SichuanTableScene.WIDTH,
                SichuanTableScene.HEIGHT, SichuanTableScene.DEPTH));
    }

    @Test void shorterSichuanWallsTightenTheOuterFootprint() {
        var mcr = McrTableScene.fullWall();
        for (boolean eastWestLongWall : new boolean[]{false, true}) {
            var sichuan = SichuanTableScene.fullWall(eastWestLongWall);
            for (int seat = 0; seat < 4; seat++) {
                int owner = seat;
                var expected = mcr.stream().filter(piece -> piece.seat() == owner)
                    .map(McrTableScene.Piece::position).reduce(net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3::add)
                    .scale(1.0 / 36);
                var actual = sichuan.stream().filter(piece -> piece.seat() == owner)
                    .map(SichuanTableScene.Piece::position).reduce(net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3::add)
                    .scale(1.0 / (2 * SichuanWallLayout.stacks(seat, eastWestLongWall)));
                assertTrue(actual.length() < expected.length(), "Shorter walls must move inward");
            }
        }
    }

    @Test void realGamesConserveStockAndClearAllPhysicalZonesInBothAssignments() {
        assertEquals(TileDimensions.LARGE, SichuanTableScene.DIMENSIONS);
        for (boolean eastWest : new boolean[]{true, false}) for (long seed : new long[]{1, 2, 3, 4, 5, 6, 19, 20, 21, 22, 42, 43, 711, 712, 2025, 2026}) {
            top.skyeyefast.mchjong.fixture.ChineseGameplayFixtures.sichuan(seed, eastWest, -1, 0, false, view -> {
                var scene = SichuanTableScene.build(view);
                assertEquals(108, scene.size(), "Physical stock must survive every accepted action");
                assertTrue(scene.stream().allMatch(piece -> piece.dimensions().equals(TileDimensions.LARGE)));
                for (var piece : scene) WallGeometryAssertions.onFelt(WallGeometryAssertions.solid(piece));
                WallGeometryAssertions.leftMeldsAndMinimalHandShift(scene.stream().map(piece ->
                    new WallGeometryAssertions.PublicPiece(WallGeometryAssertions.solid(piece), piece.seat(),
                        piece.area() == SichuanTableScene.Area.HAND, piece.area() == SichuanTableScene.Area.MELD ? piece.index() / 4 : -1)).toList(),
                    view.seats().stream().map(player -> player.hand().size() * SichuanTableScene.WIDTH
                        + (player.drawn() != Tile.ABSENT && !player.hand().isEmpty() ? RiichiTableScene.DRAW_GAP : 0)).toList(), SichuanTableScene.HAND_Z);
                assertDoesNotThrow(() -> { for (int i=0; i<scene.size(); i++) for(int j=i+1;j<scene.size();j++)
                    assertFalse(WallGeometryAssertions.intersects(WallGeometryAssertions.solid(scene.get(i)), WallGeometryAssertions.solid(scene.get(j))), scene.get(i)+" / "+scene.get(j)); },
                    "Sichuan seed=" + seed + " eastWest=" + eastWest + " revision=" + view.revision());
            });
        }
    }

    @Test void bloodBattleWinnersAndAddedKongsRetainFixedPhysicalGeometry() {
        var observed = java.util.EnumSet.noneOf(Meld.Type.class);
        boolean[] won = {false};
        for (boolean eastWest : new boolean[]{true, false}) for (long seed = 1; seed <= 16; seed++) {
            var finalView = top.skyeyefast.mchjong.fixture.ChineseGameplayFixtures.sichuan(seed, eastWest, -1, 0, true, view -> {
                var scene = SichuanTableScene.build(view);
                assertEquals(108, scene.size());
                for (var seat : view.seats()) for (var meld : seat.melds()) observed.add(meld.type());
                won[0] |= !view.winners().isEmpty();
                for (var piece : scene) WallGeometryAssertions.onFelt(WallGeometryAssertions.solid(piece));
                WallGeometryAssertions.noIntersections(scene.stream().map(WallGeometryAssertions::solid).toList());
            });
            assertTrue(finalView.phase() == SichuanGame.Phase.HAND_END || finalView.phase() == SichuanGame.Phase.MATCH_END);
        }
        assertTrue(won[0], "Fixtures must include real blood-battle winners");
        assertTrue(observed.contains(Meld.Type.ADDED_QUAD), "Fixtures must exercise the real added-kong forward tile");
    }

    @Test void scenesConsumeOnlyRecipientSafeViewsAndKeepPublicClaimIdentity() {
        var game = new SichuanGame(711);
        var declaration = game.view(0);
        assertNull(declaration.result());
        assertEquals("—", TableBoardState.live(declaration).indicator().seats().get(1).getString());
        assertTrue(TableBoardState.live(declaration).seats().get(1).hand().stream().allMatch(tile -> tile == Tile.HIDDEN));
        var pieces = SichuanTableScene.build(declaration);
        assertTrue(pieces.stream().filter(piece -> piece.area() == SichuanTableScene.Area.HAND && piece.seat() != 0)
            .allMatch(piece -> piece.tile() == Tile.HIDDEN));
        assertEquals("sichuan.mchjong.void_pending", key(SichuanTableScene.status(declaration.seats().get(1))));
        var seats = new ArrayList<>(declaration.seats());
        var kong = new Meld(Meld.Type.CONCEALED_QUAD, List.of(Tile.HIDDEN, 1, 2, Tile.HIDDEN), 1, Tile.ABSENT);
        seats.set(1, new SichuanView.Seat(Collections.nCopies(10, Tile.HIDDEN), List.of(kong), List.of(), 2, true, Tile.HIDDEN, Tile.ABSENT));
        seats.set(0, new SichuanView.Seat(List.of(8, 4), List.of(), List.of(
            new SichuanPlayerState.Discard(16, true), new SichuanPlayerState.Discard(20, false)), 2, false, 4, Tile.ABSENT));
        var score = new SichuanSettlement.Score(0, 1, List.of());
        var winners = List.of(new SichuanView.Winner(1, 0, 16, false, false, score), new SichuanView.Winner(2, 0, 16, false, false, score));
        var live = view(SichuanGame.Phase.TURN, seats, winners, List.of(), null);
        var shared = TableBoardState.live(live);
        assertEquals("sichuan.mchjong.suit.2.short", key(shared.indicator().seats().get(0)));
        assertEquals("sichuan.mchjong.suit.2", key(SichuanTableScene.status(live.seats().get(0))));
        assertEquals("sichuan.mchjong.won", key(shared.indicator().seats().get(1)));
        assertFalse(shared.seats().get(1).exposed(), "A live winner must retain recipient visibility");
        assertEquals(List.of(Tile.HIDDEN, 1, 2, Tile.HIDDEN), shared.seats().get(1).layout(kong, 1).parts().stream().map(MeldLayout.Part::tile).toList());
        assertEquals(List.of(true, false, false, true), shared.seats().get(1).layout(kong, 1).parts().stream().map(MeldLayout.Part::back).toList());
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
        var score = new SichuanSettlement.Score(4, 8, List.of(SichuanSettlement.Fan.FULL_FLUSH, SichuanSettlement.Fan.ROOT, SichuanSettlement.Fan.KONG));
        var win = new SichuanSettlement.Win(1, 0, 16, false, false, score);
        var result = new SichuanSettlement.Result(List.of(win), ledger, List.of(SichuanSettlement.DrawStatus.NOT_READY,
            SichuanSettlement.DrawStatus.WON, SichuanSettlement.DrawStatus.READY, SichuanSettlement.DrawStatus.ACTIVE_FLOWER_PIG), true);
        var seats = Collections.nCopies(4, new SichuanView.Seat(List.of(16), List.of(), List.of(), 2, false, Tile.ABSENT, Tile.ABSENT));
        var ended = view(SichuanGame.Phase.HAND_END, seats, List.of(), ledger, result);
        var receipt = receipt(ended);
        assertEquals(result.deltas(), receipt.deltas());
        assertEquals(100, receipt.seats().get(0).points());
        var paymentRows = receipt.payments();
        assertEquals(ledger.size(), paymentRows.size());
        for (int index = 0; index < ledger.size(); index++) {
            var entry = ledger.get(index);
            var text = paymentRows.get(index);
            if (entry.relatedEntry() >= 0) {
                var annotation = (TranslatableContents) text.getContents();
                var related = (net.minecraft.network.chat.Component) annotation.getArgs()[1];
                assertEquals("sichuan.mchjong.related", key(related));
                assertEquals(entry.relatedEntry() + 1, ((TranslatableContents) related.getContents()).getArgs()[0]);
                text = (net.minecraft.network.chat.Component) annotation.getArgs()[0];
            }
            var args = ((TranslatableContents) text.getContents()).getArgs();
            assertEquals(entry.id() + 1, args[0]);
            assertEquals("sichuan.mchjong.payment." + entry.type().name().toLowerCase(java.util.Locale.ROOT),
                key((net.minecraft.network.chat.Component) args[1]));
            assertEquals("player" + entry.payer(), args[2]);
            var recipient = (net.minecraft.network.chat.Component) args[3];
            if (entry.recipient() < 0) assertEquals("sichuan.mchjong.competition", key(recipient));
            else assertEquals("player" + entry.recipient(), recipient.getString());
            assertEquals(entry.amount(), args[4]);
        }
        var rows = receipt.wins().getFirst().rows();
        assertTrue(rows.stream().anyMatch(row -> key(row.label()).equals("sichuan.mchjong.fan.full_flush")));
        assertTrue(rows.stream().anyMatch(row -> key(row.label()).equals("sichuan.mchjong.fan.root")));
        assertTrue(rows.stream().anyMatch(row -> key(row.label()).equals("sichuan.mchjong.fan.kong")));
        var tfmj = TableResultState.sichuan(SichuanPreset.TFMJ_2024.config(), result, seats, ended.scores(), 0,
            room(false).seats().stream().map(TableRoomView.Seat::participant).toList());
        assertTrue(tfmj.wins().getFirst().rows().stream().anyMatch(row -> key(row.label()).equals("sichuan.mchjong.fan.root_with_kong")));
        assertFalse(tfmj.wins().getFirst().rows().stream().anyMatch(row -> key(row.label()).equals("sichuan.mchjong.fan.root")));
        assertEquals(List.of(16), receipt.seats().get(1).hand());
        var finalView = view(SichuanGame.Phase.MATCH_END, seats, List.of(), ledger, result);
        assertEquals(8, finalView.handNumber());
        assertEquals(receipt, receipt(finalView));
    }

    private static TableResultState receipt(SichuanView view) {
        return TableResultState.sichuan(view.rules(), view.result(), view.seats(), view.scores(), view.viewerSeat(),
            room(false).seats().stream().map(TableRoomView.Seat::participant).toList());
    }
    private static String key(net.minecraft.network.chat.Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }
    private static SichuanView view(SichuanGame.Phase phase, List<SichuanView.Seat> seats, List<SichuanView.Winner> winners,
                                   List<SichuanSettlement.Entry> ledger, SichuanSettlement.Result result) {
        return new SichuanView(1, 1, new SichuanGame(711).rules(), phase, phase == SichuanGame.Phase.MATCH_END ? 8 : 1, 0,
            List.of(100, 200, 300, 400), 0, 0, new SichuanView.Wall(Collections.nCopies(108, Tile.ABSENT), 0, 1, 1, true),
            seats, Tile.ABSENT, -1, false, false, List.of(), winners, ledger, result, -1);
    }
    private static TableRoomView room(boolean finished) {
        var seats = java.util.stream.IntStream.range(0, 4).mapToObj(seat -> new TableRoomView.Seat(
            new TableParticipant(new UUID(42, seat + 1), "player" + seat), PlayerPresence.SEATED, seat)).toList();
        return new TableRoomView(new UUID(41, 1), new UUID(41, 2), 1, 1, MahjongVariant.SICHUAN,
            finished ? TableSession.Lifecycle.FINISHED : TableSession.Lifecycle.PLAYING, 0, 0, false, true, false,
            RoomSeating.Stage.POSITIONING, List.of(), seats, List.of(), null, false, false, true, top.skyeyefast.mchjong.engine.MatchAutomation.DEFAULT, List.of());
    }
    private static AABB bounds(SichuanTableScene.Piece piece) {
        double cosine = Math.abs(Math.cos(Math.toRadians(piece.yaw())));
        double sine = Math.abs(Math.sin(Math.toRadians(piece.yaw())));
        double horizontal = SichuanTableScene.WIDTH * cosine + SichuanTableScene.HEIGHT * sine;
        double depth = SichuanTableScene.WIDTH * sine + SichuanTableScene.HEIGHT * cosine;
        var position = piece.position();
        return new AABB(position.x - horizontal / 2, position.y - SichuanTableScene.DEPTH / 2, position.z - depth / 2,
            position.x + horizontal / 2, position.y + SichuanTableScene.DEPTH / 2, position.z + depth / 2);
    }
}
