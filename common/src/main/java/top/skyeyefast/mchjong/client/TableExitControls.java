package top.skyeyefast.mchjong.client;

import top.skyeyefast.mchjong.text.CountedText;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;

/** Shared session exit controls for rule-specific room and match screens. */
final class TableExitControls {
    private TableExitControls() {}

    static void send(BlockPos pos, TableRoomView room, TableSessionControlPayload.Operation operation, long token, boolean enabled) {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection != null) connection.send(PayloadPackets.serverbound(
            new TableSessionControlPayload(pos, room.tableId(), operation, token, enabled)));
    }

    static List<MahjongButton> voteButtons(BlockPos pos, TableRoomView room, int width, int height) {
        return voteButtons(pos, room, width, height, 1);
    }

    static List<MahjongButton> voteButtons(BlockPos pos, TableRoomView room, int width, int height, int scale) {
        width /= scale;
        height /= scale;
        var vote = room.exitVote();
        if (vote == null || room.viewerSeat() < 0) return List.of();
        int span = Math.min(320, width - 24), left = (width - span) / 2, y = height / 2 + 30;
        var agree = MahjongButton.create(Component.translatable("ui.mchjong.exit_agree"), ignored ->
            send(pos, room, TableSessionControlPayload.Operation.ANSWER_EXIT, vote.id(), true))
            .bounds(left, y, (span - 4) / 2, 20).build();
        agree.active = !vote.agreed().contains(room.viewerSeat());
        var reject = MahjongButton.create(Component.translatable("ui.mchjong.exit_reject"), ignored ->
            send(pos, room, TableSessionControlPayload.Operation.ANSWER_EXIT, vote.id(), false))
            .bounds(left + (span + 4) / 2, y, (span - 4) / 2, 20).build();
        for (var button : List.of(agree, reject)) {
            button.setX(button.getX() * scale); button.setY(button.getY() * scale);
            button.setWidth(button.getWidth() * scale); button.setHeight(button.getHeight() * scale);
            button.textScale(scale);
        }
        return List.of(agree, reject);
    }

    static void renderVote(GuiGraphicsExtractor graphics, Font font, TableRoomView room, int width, int height) {
        renderVote(graphics, font, room, width, height, 1);
    }

    static void renderVote(GuiGraphicsExtractor graphics, Font font, TableRoomView room, int width, int height, int scale) {
        var vote = room.exitVote();
        if (vote == null || room.viewerSeat() < 0) return;
        width /= scale;
        height /= scale;
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        int span = Math.min(320, width - 24), left = (width - span) / 2, top = height / 2 - 64;
        graphics.fill(left - 6, top, left + span + 6, height / 2 + 56, MahjongUi.PANEL);
        graphics.outline(left - 6, top, span + 12, 120, MahjongUi.ACCENT);
        graphics.centeredText(font, Component.translatable("ui.mchjong.exit_title"), width / 2, top + 9, MahjongUi.ACCENT);
        var requester = room.seats().get(vote.requester()).participant().name();
        int requesterY = top + 25;
        for (var line : font.split(Component.translatable("ui.mchjong.exit_requester", requester), span - 12)) {
            if (requesterY > top + 38) break;
            graphics.centeredText(font, line, width / 2, requesterY, MahjongUi.TEXT);
            requesterY += 10;
        }
        graphics.centeredText(font, CountedText.of("ui.mchjong.exit_status", 2,
            vote.agreed().size(), vote.required(), vote.secondsLeft()), width / 2, top + 50, MahjongUi.ACCENT);
        int y = top + 65;
        for (var line : font.split(Component.translatable("ui.mchjong.exit_paused"), span - 12)) {
            graphics.centeredText(font, line, width / 2, y, MahjongUi.MUTED);
            y += 10;
        }
        graphics.pose().popMatrix();
    }
}
