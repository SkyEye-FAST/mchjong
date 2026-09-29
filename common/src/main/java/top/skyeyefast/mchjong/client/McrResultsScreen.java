package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.McrGame;
import top.skyeyefast.mchjong.engine.McrSession;
import top.skyeyefast.mchjong.engine.McrSettlement;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.network.TableLifecyclePayload;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** MCR fan and payment receipt, independent of Riichi result terminology. */
public final class McrResultsScreen extends Screen {
    private final BlockPos pos;
    private long shownRevision = -1;
    private boolean pending;
    private int page;
    private record Row(Component text, int color) {}

    public McrResultsScreen(BlockPos pos) {
        super(Component.translatable("mcr.mchjong.results"));
        this.pos = pos.immutable();
    }

    public BlockPos tablePos() { return pos; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private McrSession.View view() {
        if (minecraft.level == null || !(minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table)) return null;
        return table.clientMcrView();
    }

    public void receivedView() {
        var view = view();
        if (view != null) { pending = false; rebuild(); }
    }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        clearWidgets();
        var view = view();
        if (view == null) return;
        if (view.revision() != shownRevision) pending = false;
        shownRevision = view.revision();
        int pages = Math.max(1, (rows(view).size() + rowsPerPage() - 1) / rowsPerPage());
        page = Math.min(page, pages - 1);
        if (pages > 1) {
            addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> {
                page = Math.floorMod(page - 1, pages); rebuild();
            }).bounds((width - 60) / 2, height - 56, 26, 20).build());
            addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> {
                page = (page + 1) % pages; rebuild();
            }).bounds((width - 60) / 2 + 34, height - 56, 26, 20).build());
        }
        if (view.game().phase() == McrGame.Phase.MATCH_END) {
            var table = (MahjongTableBlockEntity) minecraft.level.getBlockEntity(pos);
            var room = table.clientTableRoom();
            int action = room == null ? -1 : room.actions().indexOf(new RoomAction(RoomAction.Type.RETURN_TO_LOBBY));
            if (action >= 0) {
                var button = MahjongButton.create(Component.translatable("action.mchjong.return_to_lobby"), ignored -> {
                    if (pending || minecraft.getConnection() == null) return;
                    pending = true;
                    RoomLobbyControls.send(pos, room, action);
                    rebuild();
                }).bounds((width - Math.min(260, width - 24)) / 2, height - 32,
                    Math.min(260, width - 24), 22).build().primary();
                button.active = !pending;
                addRenderableWidget(button);
            }
            return;
        }
        if (!view.canConfirmNextHand()) return;
        var button = MahjongButton.create(Component.translatable("mcr.mchjong.next_hand"), ignored -> {
            if (pending || minecraft.getConnection() == null) return;
            pending = true;
            minecraft.getConnection().send(PayloadPackets.serverbound(new TableLifecyclePayload(pos, view.tableId(),
                view.incarnation(), view.game().decision(), TableLifecyclePayload.Operation.CONFIRM_NEXT_HAND)));
            rebuild();
        }).bounds((width - Math.min(260, width - 24)) / 2, height - 32, Math.min(260, width - 24), 22).build().primary();
        button.active = !pending;
        addRenderableWidget(button);
    }

    private int rowsPerPage() { return Math.max(4, (height - 100) / 12); }

    private java.util.List<Row> rows(McrSession.View view) {
        var game = view.game();
        var rows = new java.util.ArrayList<Row>();
        if (game.result() instanceof McrSettlement.Win win) {
            rows.add(new Row(Component.translatable("mcr.mchjong.winner", view.participants().get(win.winner()).name()), MahjongUi.TEXT));
            for (var fan : win.score().fans()) {
                String name = java.util.Arrays.stream(fan.id().toLowerCase(java.util.Locale.ROOT).split("_"))
                    .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                    .collect(java.util.stream.Collectors.joining(" "));
                rows.add(new Row(Component.literal(name + " ×" + fan.count() + "  " + fan.points()), MahjongUi.MUTED));
            }
            rows.add(new Row(Component.translatable("mcr.mchjong.total_fan", win.score().totalFan()), MahjongUi.ACCENT));
            rows.add(new Row(Component.translatable("mcr.mchjong.flowers", game.seats().get(win.winner()).flowers().size()), MahjongUi.TEXT));
        } else rows.add(new Row(Component.translatable("mcr.mchjong.draw_result"), MahjongUi.TEXT));
        for (int seat = 0; seat < 4; seat++) rows.add(new Row(Component.literal(view.participants().get(seat).name() + "  "
            + game.result().deltas().get(seat) + "  →  " + game.seats().get(seat).points()),
            seat == game.viewerSeat() ? MahjongUi.ACCENT : MahjongUi.TEXT));
        for (var penalty : game.penalties()) if (penalty.handNumber() == game.handNumber())
            rows.add(new Row(Component.translatable("mcr.mchjong.penalty", view.participants().get(penalty.offender()).name(),
                penalty.deltas().get(penalty.offender())), MahjongUi.NEGATIVE));
        return rows;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff0b1418);
        graphics.fill(0, 0, width, height, MahjongUi.PANEL);
        MahjongUi.backdrop(graphics, width, height, 420);
        var view = view();
        if (view == null) return;
        var game = view.game();
        int span = Math.min(400, width - 24), x = (width - span) / 2;
        graphics.drawCenteredString(font, Component.translatable(game.phase() == McrGame.Phase.MATCH_END
            ? "mcr.mchjong.match_end" : "mcr.mchjong.results"), width / 2, 10, MahjongUi.ACCENT);
        var rows = rows(view);
        for (int index = page * rowsPerPage(); index < rows.size() && index < (page + 1) * rowsPerPage(); index++) {
            var row = rows.get(index);
            MahjongUi.text(graphics, font, row.text(), x, 28 + (index % rowsPerPage()) * 12,
                span, row.color(), false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
