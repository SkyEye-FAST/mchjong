package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.McrAction;
import top.skyeyefast.mchjong.engine.McrSession;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.network.McrActionPayload;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** MCR-only live table, driven by server-issued actions and physical scene poses. */
public final class McrTableScreen extends Screen {
    private final BlockPos pos;
    private long shownRevision = -1;
    private int page;
    private boolean pending;

    public McrTableScreen(BlockPos pos) {
        super(Component.translatable("mcr.mchjong.title"));
        this.pos = pos.immutable();
    }

    public BlockPos tablePos() { return pos; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private MahjongTableBlockEntity table() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table ? table : null;
    }

    private McrSession.View view() {
        var table = table();
        return table == null ? null : table.clientMcrView();
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
        if (shownRevision != view.revision()) pending = false;
        shownRevision = view.revision();
        var actions = view.game().actions();
        int pages = Math.max(1, (actions.size() + 7) / 8);
        page = Math.min(page, pages - 1);
        int span = Math.min(320, width - 20), x = (width - span) / 2;
        int y = height - 58;
        int cell = (span - 12) / 4;
        for (int slot = 0; slot < 8; slot++) {
            int index = page * 8 + slot;
            if (index >= actions.size()) break;
            McrAction action = actions.get(index);
            var caption = Component.translatable("mcr.mchjong.action." + action.type().name().toLowerCase(java.util.Locale.ROOT));
            if (!action.tiles().isEmpty()) caption = caption.copy().append(" ").append(Component.literal(
                action.tiles().stream().map(tile -> Tile.notation(Tile.kind(tile))).collect(java.util.stream.Collectors.joining(" "))));
            var button = MahjongButton.create(caption, ignored -> send(view, index))
                .bounds(x + slot % 4 * (cell + 4), y + slot / 4 * 22, cell, 20).build();
            button.active = !pending;
            addRenderableWidget(button);
        }
        if (pages > 1) {
            addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> { page = Math.floorMod(page - 1, pages); rebuild(); })
                .bounds(x, y - 22, 28, 20).build());
            addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> { page = (page + 1) % pages; rebuild(); })
                .bounds(x + span - 28, y - 22, 28, 20).build());
        }
    }

    private void send(McrSession.View view, int index) {
        if (pending || minecraft.getConnection() == null || index < 0 || index >= view.game().actions().size()) return;
        pending = true;
        minecraft.getConnection().send(PayloadPackets.serverbound(new McrActionPayload(pos, view.tableId(),
            view.incarnation(), view.game().decision(), index)));
        rebuild();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff0b1418);
        graphics.fill(0, 0, width, height, MahjongUi.PANEL);
        var table = table();
        var view = view();
        if (table == null || view == null || table.clientMcrDeck() == null) return;
        var game = view.game();
        graphics.flush();
        var pose = graphics.pose();
        float scale = (float) Math.min(width / (2 * TableGeometry.OUTER_HALF_WIDTH + .4),
            (height - 90) / (2 * TableGeometry.OUTER_HALF_WIDTH + .4));
        pose.pushPose();
        pose.translate(width / 2.0, (height - 58) / 2.0 + 15, 500);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(64));
        pose.translate(0, -TableGeometry.FELT_Y, 0);
        RenderSystem.enableDepthTest();
        FurnitureMesh.table(pose, graphics.bufferSource(), 0xf000f0, FurnitureWood.OAK, table.clientMcrCloth(), true);
        McrSceneRenderer.render(McrTableScene.immersive(game),
            table.clientMcrDeck(), pose, graphics.bufferSource(), 0xf000f0);
        graphics.flush();
        pose.popPose();
        var header = Component.translatable("mcr.mchjong.hand", game.handNumber(), game.remaining());
        graphics.drawCenteredString(font, header, width / 2, 8, MahjongUi.TEXT);
        if (view.paused()) graphics.drawCenteredString(font, Component.translatable("mcr.mchjong.paused"), width / 2, 20, MahjongUi.NEGATIVE);
        game.penalties().stream().filter(penalty -> penalty.handNumber() == game.handNumber()).reduce((first, last) -> last)
            .ifPresent(penalty -> graphics.drawCenteredString(font, Component.translatable("mcr.mchjong.penalty",
                view.participants().get(penalty.offender()).name(), penalty.deltas().get(penalty.offender())),
                width / 2, height - 82, MahjongUi.NEGATIVE));
        for (int seat = 0; seat < 4; seat++) {
            var participant = view.participants().get(seat);
            var player = game.seats().get(seat);
            int x = seat % 2 == 0 ? 8 : width - Math.min(150, width / 3) - 8;
            int y = 34 + seat / 2 * 34;
            var name = Component.literal(participant.name() + " · " + player.points());
            MahjongUi.text(graphics, font, name, x, y, Math.min(150, width / 3), MahjongUi.TEXT, false);
            var wind = Component.translatable("wind.mchjong." + new String[]{"east", "south", "west", "north"}[player.wind() - Tile.EAST]);
            var state = wind.copy().append(" · ").append(Component.translatable("mcr.mchjong.flowers", player.flowers().size()));
            if (player.winForbidden()) state.append(" · ").append(Component.translatable("mcr.mchjong.win_forbidden"));
            MahjongUi.text(graphics, font, state, x, y + 11, Math.min(150, width / 3), player.winForbidden() ? MahjongUi.NEGATIVE : MahjongUi.MUTED, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
