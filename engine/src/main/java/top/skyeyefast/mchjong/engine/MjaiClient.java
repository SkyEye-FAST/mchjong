package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One seat's line-oriented mjai process. Call choose on a worker, close from its owner. */
public final class MjaiClient implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private final Process process;
    private final BufferedReader input;
    private final BufferedWriter output;
    private int hand = -1;
    private int cursor;
    private boolean declared;
    private JsonObject response;
    private final StringBuilder errors = new StringBuilder();

    JsonObject response() { return response; }

    public MjaiClient(BotPreset preset, int seat) throws IOException {
        var command = preset.command().stream().map(arg -> arg.replace("{seat}", Integer.toString(seat))).toList();
        process = new ProcessBuilder(command).directory(Path.of(preset.directory()).toFile()).start();
        var stderr = new Thread(() -> {
            try (var reader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
                var buffer = new char[1024];
                for (int read; (read = reader.read(buffer)) >= 0;) synchronized (errors) {
                    errors.append(buffer, 0, read);
                    if (errors.length() > 8192) errors.delete(0, errors.length() - 8192);
                }
            } catch (IOException ignored) { /* Closing the owner process closes stderr. */ }
        }, "mchjong-mjai-stderr");
        stderr.setDaemon(true); stderr.start();
        input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        try { send(Map.of("type", "start_game", "id", seat, "names", List.of("", "", "", "")), false); }
        catch (IOException error) { close(); throw error; }
    }

    public int choose(MjaiProtocol.Position position) throws IOException {
        var view = position.view();
        if (view.actions().size() == 1 && view.actions().get(0).type() == Action.Type.PASS) return 0;
        if (hand != position.hand()) {
            if (hand >= 0) send(Map.of("type", "end_kyoku"), false);
            hand = position.hand(); cursor = 0; declared = false;
        }
        var events = position.events();
        if (cursor >= events.size()) throw new IOException("No new mjai decision event");
        for (; cursor < events.size(); cursor++) {
            var event = events.get(cursor);
            if (declared && event.get("type").equals("reach") && event.get("actor").equals(position.seat())) {
                declared = false;
                continue;
            }
            send(event, cursor == events.size() - 1);
        }
        JsonObject response = receive();
        this.response = response;
        boolean reach = response.get("type").getAsString().equals("reach");
        if (reach) {
            if (!response.has("actor") || response.get("actor").getAsInt() != position.seat()
                || view.actions().stream().noneMatch(action -> action.type() == Action.Type.RIICHI))
                throw new IOException("Illegal mjai reach");
            send(Map.of("type", "reach", "actor", position.seat()), true);
            response = receive();
            declared = true;
        }
        try { return MjaiProtocol.action(view, response, reach); }
        catch (RuntimeException error) { throw new IOException("Invalid mjai response", error); }
    }

    private void send(Map<String, Object> event, boolean canAct) throws IOException {
        var message = new LinkedHashMap<>(event);
        message.put("can_act", canAct);
        output.write(JSON.toJson(message)); output.newLine(); output.flush();
    }

    void finish(int number, int seat, List<Map<String, Object>> events, List<Map<String, Object>> result) throws IOException {
        if (hand != number) return;
        for (; cursor < events.size(); cursor++) {
            var event = events.get(cursor);
            if (declared && event.get("type").equals("reach") && event.get("actor").equals(seat)) {
                declared = false;
                continue;
            }
            send(event, false);
        }
        for (var event : result) send(event, false);
        hand = -1; cursor = 0;
    }

    private JsonObject receive() throws IOException {
        var line = new StringBuilder();
        for (int next; (next = input.read()) >= 0;) {
            if (next == '\n') {
                try { return JSON.fromJson(line.toString(), JsonObject.class); }
                catch (RuntimeException error) { throw new IOException("Malformed mjai response", error); }
            }
            if (line.length() >= 65536) throw new IOException("Mjai response exceeds 64 KiB");
            line.append((char) next);
        }
        synchronized (errors) { throw new IOException("Mjai process closed its output: " + errors); }
    }

    @Override public void close() {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
