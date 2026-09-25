package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.world.MahjongSounds;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Client-local effects and optional recorded voices. Never invokes a speech backend. */
public final class TableAudio {
    private static final Map<MahjongTableBlockEntity, TableView> VIEWS = new WeakHashMap<>();
    private static final ArrayList<Speech> SPEECH = new ArrayList<>();
    private record Speech(long tick, String voice) {}
    private static ClientLevel level;
    private static long ticks;
    private static UUID clockTable;
    private static long clockDecision = -1;
    private static int lastSecond = -1;
    private static ResultReadout result;
    private static boolean finalVoicePlayed;
    private static long acknowledged = -1;
    private static boolean seated;
    private TableAudio() {}

    private static void world() {
        var current = Minecraft.getInstance().level;
        if (current == level) return;
        VIEWS.clear();
        SPEECH.clear();
        VoicePresets.stop();
        result = null;
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
        if (view.viewerSeat() >= 0 && TableResults.available(view)) {
            if (result == null || !result.matches(view)) {
                SPEECH.clear();
                VoicePresets.stop();
                result = new ResultReadout(view, Util.getMillis());
                acknowledged = -1;
                finalVoicePlayed = before == null || TableResults.available(before);
                // Joining/reopening an already completed hand must not replay its announcements.
                if (finalVoicePlayed) result.finish(Util.getMillis());
            }
        } else if (result != null && before != null && result.matches(before)) {
            finishResult();
            SPEECH.clear();
            VoicePresets.stop();
            result = null;
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
        VoicePresets.playing();
        if (client.player == null || !(client.player.getVehicle() instanceof SeatEntity seat)) {
            SPEECH.clear();
            if (seated) { finishResult(); VoicePresets.stop(); }
            seated = false;
            clockTable = null;
            return;
        }
        seated = true;
        if (TableSettings.get().voiceSource == TableSettings.VoiceSource.OFF || TableSettings.get().voiceVolume <= 0)
            VoicePresets.stop();
        boolean speaking = VoicePresets.playing();
        if (!speaking && !SPEECH.isEmpty() && SPEECH.getFirst().tick() <= ticks) {
            speak(SPEECH.removeFirst().voice());
            speaking = VoicePresets.playing();
        }
        if (client.level.getBlockEntity(seat.tablePos()) instanceof MahjongTableBlockEntity table && table.clientView() != null) {
            var view = table.clientView();
            if (result != null && result.matches(view)) {
                boolean finalStage = view.phase() == Game.Phase.MATCH_END && table.clientRoom() != null
                    && table.clientRoom().settlementTicks() <= Game.SETTLEMENT_TICKS;
                if (finalStage && !finalVoicePlayed) {
                    finishResult();
                    finalVoicePlayed = true;
                    effect("match_end", null, 0);
                    speak("match_end");
                } else if (!finalStage && SPEECH.isEmpty()) {
                    String event = result.tick(Util.getMillis(), speaking);
                    if (event != null && view.wins().get(result.winner()).seat() == view.viewerSeat()) speak(event);
                }
                if (!finalStage && result.complete() && acknowledged != view.decision() && client.getConnection() != null) {
                    for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == Action.Type.SETTLEMENT_DONE) {
                        client.getConnection().send(PayloadPackets.serverbound(
                            new TableActionPayload(table.getBlockPos(), view.tableId(), view.decision(), i)));
                        acknowledged = view.decision();
                        break;
                    }
                }
            }
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
            case SELECTED -> {
                if (settings.voiceVolume > 0) VoicePresets.play(event, (float) settings.voiceVolume);
            }
        }
    }

    public static void settingsChanged() {
        SPEECH.clear();
        VoicePresets.stop();
    }

    public static ResultReadout result(TableView view) {
        return result != null && result.matches(view) ? result : null;
    }

    /** First skip reveals the receipt locally; only a subsequent skip advances the server. */
    public static boolean finishResult() {
        if (result == null) return false;
        boolean pending = !result.complete();
        result.finish(Util.getMillis());
        SPEECH.clear();
        VoicePresets.stop();
        return pending;
    }

    public static void preview() {
        effect("ron", null, 0);
        speak("ron");
    }

    public static void close() {
        VoicePresets.stop();
        VIEWS.clear(); SPEECH.clear(); result = null; level = null;
    }
}
