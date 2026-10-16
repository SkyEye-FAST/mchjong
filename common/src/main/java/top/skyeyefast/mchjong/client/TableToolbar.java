package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TableRoomView;

/** Shared match navigation; rule-specific actions remain beside the private hand. */
final class TableToolbar {
    private TableToolbar() {}

    static MahjongButton drawer(BlockPos pos, int side, int x, int y, int scale, Runnable before) {
        var client = Minecraft.getInstance();
        boolean cards = client.level != null && client.level.getBlockEntity(pos) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table
            && table.clientVariant() != top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI;
        var caption = Component.translatable(cards ? "cards.mchjong.drawer" : "ui.mchjong.drawer");
        int width = cards ? Math.max(72, client.font.width(caption) + 12) : 72;
        return MahjongButton.create(caption, ignored -> {
            before.run();
            var location = top.skyeyefast.mchjong.world.TableGeometry.world(pos,
                top.skyeyefast.mchjong.world.TableGeometry.drawerBounds(side).getCenter());
            client.gameMode.useItemOn(client.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(location, top.skyeyefast.mchjong.world.TableGeometry.SIDES[side], BlockPos.containing(location), false));
        }).bounds(x, y, width * scale, 20 * scale).build().textScale(scale);
    }

    static List<MahjongButton> build(Screen parent, BlockPos pos, TableRoomView room, int width,
                                     boolean immersive, Runnable switchView) {
        var client = Minecraft.getInstance();
        int scale = immersive ? 2 : 1, right = width - 8 * scale, y = 8 * scale;
        var buttons = new ArrayList<MahjongButton>();
        if (room.exitVote() != null) return buttons;
        String[] keys = {"ui.mchjong.exit", "settings.mchjong.scopes", immersive ? "ui.mchjong.view_seated" : "ui.mchjong.view_immersive", "replay.mchjong.title"};
        Runnable[] actions = {() -> TableExitControls.request(parent, pos, room),
            () -> client.setScreen(new TableOptionsScreen(parent, pos)), switchView, () -> ClientReplays.list(0, "", false)};
        for (int index = 0; index < keys.length; index++) {
            int current = index;
            var fullCaption = Component.translatable(index == 0 ? "ui.mchjong.exit_hint" : keys[index]);
            boolean compact = !immersive && width < 560;
            var caption = Component.translatable(keys[index] + (compact && index == 3 ? ".short" : ""));
            int span = (client.font.width(caption) + (compact ? 8 : 14)) * scale;
            var button = MahjongButton.create(caption, ignored -> actions[current].run())
                .bounds(right - span, y, span, 20 * scale).tooltip(Tooltip.create(fullCaption)).build().textScale(scale);
            buttons.add(button);
            right -= span + 4 * scale;
        }
        return buttons;
    }
}
