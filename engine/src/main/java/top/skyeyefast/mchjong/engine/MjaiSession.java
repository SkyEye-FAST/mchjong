package top.skyeyefast.mchjong.engine;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded background inference; the server thread only submits and polls immutable positions. */
final class MjaiSession implements AutoCloseable {
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(32), task -> { var thread = new Thread(task, "mchjong-mjai"); thread.setDaemon(true); return thread; });
    private final BotPreset preset;
    private volatile MjaiClient client;
    private volatile boolean closed;
    private CompletableFuture<Integer> pending;
    private CompletableFuture<Void> notifications = CompletableFuture.completedFuture(null);
    private long decision = -1;

    MjaiSession(BotPreset preset) { this.preset = preset; }
    boolean stale(long token) { return pending != null && decision != token; }
    com.google.gson.JsonObject response() { return client == null ? null : client.response(); }
    com.google.gson.JsonObject reachDiscardResponse() { return client == null ? null : client.reachDiscardResponse(); }

    int poll(MjaiProtocol.Position position) {
        if (pending == null) {
            decision = position.view().decision();
            pending = notifications.thenApplyAsync(ignored -> {
                try {
                    if (client == null) client = new MjaiClient(preset, position.seat());
                    if (closed) { client.close(); throw new IllegalStateException("Mjai session closed"); }
                    return client.choose(position);
                } catch (java.io.IOException error) { throw new java.util.concurrent.CompletionException(error); }
            }, WORKERS).orTimeout(preset.timeoutSeconds(), TimeUnit.SECONDS);
            pending.whenComplete((value, error) -> { if (error != null) close(); });
        }
        if (decision != position.view().decision()) throw new IllegalStateException("Stale mjai decision");
        if (!pending.isDone()) return -1;
        int action = pending.join();
        pending = null;
        return action;
    }

    void finish(int number, int seat, java.util.List<java.util.Map<String, Object>> events,
                java.util.List<java.util.Map<String, Object>> result) {
        if (client == null) return;
        notifications = notifications.thenRunAsync(() -> {
            try { client.finish(number, seat, events, result); }
            catch (java.io.IOException error) { throw new java.util.concurrent.CompletionException(error); }
        }, WORKERS).orTimeout(preset.timeoutSeconds(), TimeUnit.SECONDS);
        notifications.whenComplete((ignored, error) -> { if (error != null) close(); });
    }

    @Override public void close() {
        closed = true;
        var running = client;
        if (running != null) running.close();
    }
}
