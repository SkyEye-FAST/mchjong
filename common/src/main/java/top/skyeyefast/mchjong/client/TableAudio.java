package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import top.skyeyefast.mchjong.engine.McrView;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.SichuanView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongSounds;

/** Shared positional tile effects; UI/receipt accents use a relative UI source. */
public final class TableAudio {
    private TableAudio() {}

    public static void effect(String name, BlockPos pos, int delay) {
        float volume = (float) TableSettings.get().effectsVolume;
        if (volume <= 0) return;
        var event = MahjongSounds.effect(name);
        var sound = pos == null ? SimpleSoundInstance.forUI(event, 1, volume)
            : new SimpleSoundInstance(event, SoundSource.BLOCKS, volume, 1, RandomSource.create(),
                pos.getX() + .5, pos.getY() + .94, pos.getZ() + .5);
        Minecraft.getInstance().getSoundManager().playDelayed(sound, delay);
    }

    static void opening(BlockPos pos, boolean automatic) {
        if (automatic) effect("table_mechanical", pos, 0);
        effect("dice", pos, 4);
        effect("tile_call", pos, 10);
    }

    static List<String> between(McrView before, McrView after) {
        if (before == null || after == null || after.revision() <= before.revision()
            || before.viewerSeat() != after.viewerSeat() || before.handNumber() != after.handNumber()) return List.of();
        var effects = new ArrayList<String>();
        for (int seat = 0; seat < 4; seat++) {
            var old = before.seats().get(seat); var next = after.seats().get(seat);
            tiles(effects, old.river().size(), next.river().size(), old.drawn(), next.drawn(),
                old.hand().size(), next.hand().size(), old.melds(), next.melds());
            if (next.flowers().size() > old.flowers().size()) effects.add("tile_call");
        }
        if (before.result() == null && after.result() != null) effects.add("score_reveal");
        return List.copyOf(effects);
    }

    static List<String> between(SichuanView before, SichuanView after) {
        if (before == null || after == null || after.revision() <= before.revision()
            || before.viewerSeat() != after.viewerSeat() || before.handNumber() != after.handNumber()) return List.of();
        var effects = new ArrayList<String>();
        for (int seat = 0; seat < 4; seat++) {
            var old = before.seats().get(seat); var next = after.seats().get(seat);
            tiles(effects, old.river().size(), next.river().size(), old.drawn(), next.drawn(),
                old.hand().size(), next.hand().size(), old.melds(), next.melds());
        }
        if (after.winners().size() > before.winners().size())
            effects.add(after.winners().get(before.winners().size()).selfDraw() ? "tsumo" : "ron");
        if (before.result() == null && after.result() != null) effects.add("score_reveal");
        return List.copyOf(effects);
    }

    private static void tiles(List<String> effects, int oldRiver, int river, int oldDraw, int draw,
                              int oldHand, int hand, List<Meld> oldMelds, List<Meld> melds) {
        if (river > oldRiver) effects.add("tile_discard");
        if (draw != Tile.ABSENT && (oldDraw == Tile.ABSENT || hand > oldHand)) effects.add("tile_draw");
        for (int i = 0; i < melds.size(); i++) {
            var meld = melds.get(i);
            // Revealing concealed identities at settlement is not another physical call.
            if (i < oldMelds.size() && meld.type() == oldMelds.get(i).type()) continue;
            effects.add(switch (meld.type()) {
                case SEQUENCE, TRIPLET -> "tile_call";
                case OPEN_QUAD, CONCEALED_QUAD, ADDED_QUAD -> "tile_kong";
            });
        }
    }
}
