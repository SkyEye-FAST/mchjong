package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.RiichiActionPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Client-local effects and optional recorded voices. Never invokes a speech backend. */
public final class RiichiAudio {
    private static final Map<MahjongTableBlockEntity, RiichiView> VIEWS = new WeakHashMap<>();
    private static final Map<MahjongTableBlockEntity, java.util.UUID> LOBBIES = new WeakHashMap<>();
    private static final ArrayList<Speech> SPEECH = new ArrayList<>();
    private record Speech(long tick, String voice, net.minecraft.resources.ResourceLocation preset, boolean remote) {}
    private static ClientLevel level;
    private static long ticks;
    private static UUID clockTable;
    private static long clockDecision = -1;
    private static int lastSecond = -1;
    private static ResultReadout result;
    private static boolean finalVoicePlayed;
    private static long acknowledged = -1;
    private static boolean seated;
    private RiichiAudio() {}

    private static void world() {
        var current = Minecraft.getInstance().level;
        if (current == level) return;
        VIEWS.clear();
        LOBBIES.clear();
        SPEECH.clear();
        VoicePresets.stop();
        result = null;
        clockTable = null;
        clockDecision = -1;
        lastSecond = -1;
        level = current;
    }

    public static void accept(MahjongTableBlockEntity table, RiichiView view) {
        world();
        if (view == null) {
            VIEWS.remove(table);
            var room = table.clientRoom();
            if (room != null && room.lobby()) LOBBIES.put(table, room.tableId());
            SPEECH.clear();
            VoicePresets.stop();
            result = null;
            return;
        }
        RiichiView before = VIEWS.put(table, view);
        boolean fromLobby = view.tableId().equals(LOBBIES.remove(table));
        if (before != null && (!before.tableId().equals(view.tableId()) || before.viewerSeat() != view.viewerSeat())) {
            SPEECH.clear();
        }
        if (view.viewerSeat() >= 0 && RiichiResults.available(view)) {
            if (result == null || !result.matches(view)) {
                SPEECH.clear();
                VoicePresets.stop();
                result = new ResultReadout(view, Util.getMillis());
                acknowledged = -1;
                finalVoicePlayed = before == null || RiichiResults.available(before);
                // Joining/reopening an already completed hand must not replay its announcements.
                if (finalVoicePlayed) result.finish(Util.getMillis());
            }
        } else if (result != null && before != null && result.matches(before)) {
            finishResult();
            SPEECH.clear();
            VoicePresets.stop();
            result = null;
        }
        for (var cue : fromLobby && before == null ? RiichiAudioEvents.opening(view) : RiichiAudioEvents.between(before, view)) {
            if (cue.sound() != null && (!cue.sound().equals("table_mechanical") || table.automatic()))
                TableAudio.effect(cue.sound(), cue.sound().equals("ui_accent") ? null : table.getBlockPos(), cue.delay());
            if (cue.voice() != null && Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(table.getBlockPos())) <= 256)
                SPEECH.add(speech(view, cue.seat(), cue.voice(), ticks + cue.delay()));
        }
    }

    public static void tick() {
        world();
        ticks++;
        var client = Minecraft.getInstance();
        VoicePresets.playing();
        if (TableSettings.get().voiceSource == TableSettings.VoiceSource.OFF || TableSettings.get().voiceVolume <= 0)
            VoicePresets.stop();
        boolean speaking = VoicePresets.playing();
        if (!speaking && !SPEECH.isEmpty() && SPEECH.get(0).tick() <= ticks) {
            speak(SPEECH.remove(0));
            speaking = VoicePresets.playing();
        }
        if (client.player == null || !(client.player.getVehicle() instanceof SeatEntity seat)) {
            if (seated) { finishResult(); VoicePresets.stop(); }
            seated = false;
            clockTable = null;
            return;
        }
        seated = true;
        if (client.level.getBlockEntity(seat.tablePos()) instanceof MahjongTableBlockEntity table && table.clientView() != null) {
            var view = table.clientView();
            if (result != null && result.matches(view)) {
                boolean finalStage = view.phase() == RiichiView.Phase.MATCH_END
                    && view.settlementTicks() <= RiichiGame.SETTLEMENT_TICKS;
                if (finalStage && !finalVoicePlayed) {
                    finishResult();
                    finalVoicePlayed = true;
                    TableAudio.effect("match_end", null, 0);
                    speak("match_end");
                } else if (!finalStage && SPEECH.isEmpty()) {
                    var event = result.tick(Util.getMillis(), speaking);
                    if (event != null) {
                        switch (event.stage()) {
                            case POINTS -> TableAudio.effect("score_reveal", null, 0);
                            case LIMIT -> TableAudio.effect("grade_reveal", null, 0);
                            default -> { }
                        }
                        if (event.voice() != null)
                            speak(speech(view, view.wins().get(event.winner()).seat(), event.voice(), ticks));
                    }
                }
                if (!finalStage && result.complete() && acknowledged != view.decision() && client.getConnection() != null) {
                    for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == RiichiAction.Type.SETTLEMENT_DONE) {
                        client.getConnection().send(PayloadPackets.serverbound(
                            new RiichiActionPayload(table.getBlockPos(), view.tableId(), view.decision(), i)));
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
                if (TableSettings.get().countdownSounds) TableAudio.effect("countdown", null, 0);
            }
        }
    }

    private static void speak(String event) {
        speak(new Speech(ticks, event, TableSettings.get().voicePreset, false));
    }

    private static Speech speech(RiichiView view, int seat, String event, long tick) {
        boolean remote = seat >= 0 && seat != view.viewerSeat();
        var preset = remote ? VoicePresets.forPlayer(view.seats().get(seat).name()) : TableSettings.get().voicePreset;
        return new Speech(tick, event, preset, remote);
    }

    private static void speak(Speech speech) {
        var settings = TableSettings.get();
        switch (settings.voiceSource) {
            case OFF -> { }
            case SELECTED -> {
                if (settings.voiceVolume > 0) VoicePresets.play(speech.voice(), (float) settings.voiceVolume, speech.preset(), speech.remote());
            }
        }
    }

    public static void settingsChanged() {
        SPEECH.clear();
        VoicePresets.stop();
    }

    public static ResultReadout result(RiichiView view) {
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
        TableAudio.effect("ron", null, 0);
        speak("ron");
    }

    public static void close() {
        VoicePresets.stop();
        VIEWS.clear(); SPEECH.clear(); result = null; level = null;
    }
}
