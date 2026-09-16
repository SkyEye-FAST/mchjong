package top.skyeyefast.mchjong.replay;

import java.util.UUID;
import top.skyeyefast.mchjong.network.ReplayPayload;

/** One bounded transfer at a time; partial data is never parsed or written to disk. */
public final class ReplayTransfer {
    public record Completed(ReplayPayload.Kind kind, String text) {}
    private UUID id;
    private ReplayPayload.Kind kind;
    private int next;
    private int parts;
    private long started;
    private final StringBuilder text = new StringBuilder();

    public Completed accept(ReplayPayload chunk, long now) {
        expire(now);
        if (chunk.part() == 0) {
            reset(); id = chunk.transfer(); kind = chunk.kind(); parts = chunk.parts(); started = now;
        }
        if (!chunk.transfer().equals(id) || kind != chunk.kind() || next != chunk.part() || parts != chunk.parts()) {
            reset();
            throw new IllegalArgumentException("Replay chunks arrived out of sequence");
        }
        text.append(chunk.text());
        if (++next < parts) return null;
        var result = new Completed(kind, text.toString());
        reset();
        return result;
    }
    public void expire(long now) { if (id != null && now - started >= 30_000) reset(); }
    public void reset() { id = null; next = parts = 0; text.setLength(0); }
}
