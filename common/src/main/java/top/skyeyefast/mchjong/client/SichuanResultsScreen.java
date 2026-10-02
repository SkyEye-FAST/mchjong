package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.SichuanGame;
import top.skyeyefast.mchjong.engine.SichuanSession;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.SichuanNextHandPayload;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

public final class SichuanResultsScreen extends Screen {
    private final BlockPos pos;
    private final boolean immersive;
    private boolean pending;
    private int page;
    private long shownRevision = -1;
    private List<SichuanResults.Row> rows = List.of();

    public SichuanResultsScreen(BlockPos pos, boolean immersive) {
        super(Component.translatable("sichuan.mchjong.title"));
        this.pos = pos.immutable(); this.immersive = immersive;
    }
    public BlockPos tablePos() { return pos; }
    public boolean immersive() { return immersive; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics) {}
    private MahjongTableBlockEntity table() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table ? table : null;
    }
    private SichuanSession.View view() { return table() == null ? null : table().clientSichuanView(); }
    @Override protected void init() { rebuild(); }
    public void receivedView() { pending = false; rebuild(); }
    private int rowsPerPage() { return Math.max(4, (height - 118) / 18); }

    private void rebuild() {
        clearWidgets();
        var view = view();
        if (view == null || table().clientTableRoom() == null || view.game().result() == null) return;
        var room = table().clientTableRoom();
        shownRevision = view.revision();
        rows = SichuanResults.rows(view.game(), room);
        int pages = Math.max(1, (rows.size() + rowsPerPage() - 1) / rowsPerPage());
        page = Math.min(page, pages - 1);
        if (room.exitVote() != null) {
            TableExitControls.voteButtons(pos, room, width, height).forEach(this::addRenderableWidget);
            return;
        }
        if (room.viewerSeat() >= 0 && view.game().phase() == SichuanGame.Phase.HAND_END)
            addRenderableWidget(new MahjongButton(width - 60, 6, 52, 20, Component.translatable("ui.mchjong.exit"),
                ignored -> TableExitControls.send(pos, room, TableSessionControlPayload.Operation.REQUEST_EXIT, room.decision(), false)));
        if (pages > 1) {
            addRenderableWidget(new MahjongButton(width / 2 - 64, height - 76, 26, 20, Component.literal("‹"),
                ignored -> { page = Math.floorMod(page - 1, pages); rebuild(); }));
            addRenderableWidget(new MahjongButton(width / 2 + 38, height - 76, 26, 20, Component.literal("›"),
                ignored -> { page = (page + 1) % pages; rebuild(); }));
        }
        int span = Math.min(260, width - 24);
        if (view.game().phase() == SichuanGame.Phase.MATCH_END) {
            int action = room.actions().indexOf(new RoomAction(RoomAction.Type.RETURN_TO_LOBBY));
            if (action >= 0) {
                var button = new MahjongButton((width - span) / 2, height - 30, span, 20,
                    Component.translatable("action.mchjong.return_to_lobby"), ignored -> {
                        if (pending) return;
                        pending = true; RoomLobbyControls.send(pos, room, action); rebuild();
                    }).primary();
                button.active = !pending; addRenderableWidget(button);
            }
        } else if (view.game().viewerSeat() >= 0) {
            var button = new MahjongButton((width - span) / 2, height - 30, span, 20,
                Component.translatable((view.confirmed() & (1 << view.game().viewerSeat())) == 0
                    ? "sichuan.mchjong.next_hand" : "sichuan.mchjong.confirmed"), ignored -> {
                    var current = view();
                    if (pending || current == null || !current.canConfirmNextHand() || minecraft.getConnection() == null) return;
                    pending = true;
                    minecraft.getConnection().send(PayloadPackets.serverbound(new SichuanNextHandPayload(pos, current.tableId(),
                        current.incarnation(), current.game().decision())));
                    rebuild();
                }).primary();
            button.active = !pending && view.canConfirmNextHand(); addRenderableWidget(button);
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MahjongUi.INPUT);
        MahjongUi.backdrop(graphics, width, height, Math.min(520, width));
        var view = view();
        if (view == null) return;
        if (shownRevision != view.revision()) { pending = false; rebuild(); }
        int span = Math.min(496, width - 24), left = (width - span) / 2;
        graphics.drawCenteredString(font, Component.translatable(view.game().phase() == SichuanGame.Phase.MATCH_END
            ? "sichuan.mchjong.match_end" : "sichuan.mchjong.results", view.game().handNumber()), width / 2, 10, MahjongUi.ACCENT);
        for (int index = page * rowsPerPage(); index < Math.min(rows.size(), (page + 1) * rowsPerPage()); index++) {
            var row = rows.get(index);
            int top = 32 + (index % rowsPerPage()) * 18;
            if (row.tiles().isEmpty()) {
                MahjongUi.text(graphics, font, row.text(), left, top + 3, span, row.color(), false);
                if (mouseX >= left && mouseX < left + span && mouseY >= top && mouseY < top + 18)
                    graphics.renderTooltip(font, row.text(), mouseX, mouseY);
            } else if (table().clientSichuanDeck() != null) {
                var deck = table().clientSichuanDeck();
                for (int tileIndex = 0; tileIndex < row.tiles().size(); tileIndex++) TileGui.tile(graphics,
                    row.tiles().get(tileIndex), left + tileIndex * 10, top, 9, false, false, false, false,
                    deck.preset(), deck.material(), deck.back(), deck.backPreset());
            }
        }
        graphics.drawCenteredString(font, (page + 1) + " / " + Math.max(1, (rows.size() + rowsPerPage() - 1) / rowsPerPage()),
            width / 2, height - 70, MahjongUi.MUTED);
        if (view.game().phase() == SichuanGame.Phase.HAND_END) {
            long elapsed = view.paused() ? 0 : table().clientViewAgeMillis() / 50;
            int seconds = (int) (Math.max(0, view.settlementTicks() - elapsed) + 19) / 20;
            graphics.drawCenteredString(font, Component.translatable("sichuan.mchjong.reading", view.confirmedCount(), 4, seconds),
                width / 2, height - 48, MahjongUi.ACCENT);
            if (view.paused()) graphics.drawCenteredString(font, Component.translatable("sichuan.mchjong.paused"), width / 2, 21, MahjongUi.NEGATIVE);
        }
        TableExitControls.renderVote(graphics, font, table().clientTableRoom(), width, height);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
