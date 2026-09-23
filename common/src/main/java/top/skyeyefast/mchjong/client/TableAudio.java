package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.world.MahjongSounds;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Client-local effects and optional resource-pack recordings. Never invokes a speech backend. */
public final class TableAudio {
    private static final Map<MahjongTableBlockEntity, TableView> VIEWS = new WeakHashMap<>();
    private static final ArrayList<Speech> SPEECH = new ArrayList<>();
    private record Speech(long tick, String voice) {}
    private static ClientLevel level;
    private static long ticks;
    private static UUID clockTable;
    private static long clockDecision = -1;
    private static int lastSecond = -1;
    private TableAudio() {}

    private static void world() {
        var current = Minecraft.getInstance().level;
        if (current == level) return;
        VIEWS.clear();
        SPEECH.clear();
        clockTable = null;
        clockDecision = -1;
        lastSecond = -1;
        level = current;
    }

    public static void accept(MahjongTableBlockEntity table, TableView view) {
        world();
        TableView before = VIEWS.put(table, view);
        if (before != null && (!before.tableId().equals(view.tableId()) || before.viewerSeat() != view.viewerSeat())) {
            SPEECH.clear();
        }
        for (var cue : TableAudioEvents.between(before, view)) {
            effect(cue.sound(), table.getBlockPos(), cue.delay());
            if (view.viewerSeat() >= 0 && cue.voice() != null)
                SPEECH.add(new Speech(ticks + cue.delay(), cue.voice()));
        }
    }

    public static void tick() {
        world();
        ticks++;
        var client = Minecraft.getInstance();
        if (client.player == null || !(client.player.getVehicle() instanceof SeatEntity seat)) {
            SPEECH.clear();
            clockTable = null;
            return;
        }
        SPEECH.removeIf(speech -> {
            if (speech.tick() > ticks) return false;
            speak(speech.voice());
            return true;
        });
        if (client.level.getBlockEntity(seat.tablePos()) instanceof MahjongTableBlockEntity table && table.clientView() != null) {
            var view = table.clientView();
            int own = view.viewerSeat();
            if (own < 0 || own >= view.clocks().size()) return;
            var clock = view.clocks().get(own).after(table.clientViewAgeMillis());
            int seconds = (clock.moveTicks() + clock.reserveTicks() + 19) / 20;
            if (!view.tableId().equals(clockTable) || view.decision() != clockDecision) {
                clockTable = view.tableId(); clockDecision = view.decision(); lastSecond = -1;
            }
            if (clock.active() && seconds > 0 && seconds <= 5 && seconds != lastSecond) {
                lastSecond = seconds;
                if (TableSettings.get().countdownSounds) effect("countdown", null, 0);
            }
        }
    }

    private static void effect(String name, BlockPos pos, int delay) {
        float volume = (float) TableSettings.get().effectsVolume;
        if (volume <= 0) return;
        var sound = pos == null ? SimpleSoundInstance.forUI(MahjongSounds.effect(name), 1, volume)
            : new SimpleSoundInstance(MahjongSounds.effect(name), SoundSource.BLOCKS, volume, 1, RandomSource.create(), pos);
        Minecraft.getInstance().getSoundManager().playDelayed(sound, delay);
    }

    private static void speak(String event) {
        var settings = TableSettings.get();
        switch (settings.voiceSource) {
            case OFF -> { }
            case RESOURCE_PACK -> {
                if (settings.voiceVolume > 0) Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(MahjongSounds.voice(event), 1, (float) settings.voiceVolume));
            }
        }
    }

    public static void settingsChanged() {
        SPEECH.clear();
        for (String name : MahjongSounds.VOICES)
            Minecraft.getInstance().getSoundManager().stop(MahjongSounds.voice(name).location(), null);
    }

    public static void preview() {
        effect("ron", null, 0);
        speak("ron");
    }

    public static void close() {
        VIEWS.clear(); SPEECH.clear(); level = null;
    }
}
