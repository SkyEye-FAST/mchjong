package top.skyeyefast.mchjong.world;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.engine.BotPosition;
import top.skyeyefast.mchjong.engine.ExternalBot;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RiichiSession;

/** Nonblocking server-thread bridge to the administrator-configured bot service. */
public final class BotServiceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("mchjong");
    private static final Gson JSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static volatile Settings settings;
    private static volatile List<ExternalBot> bots = List.of();
    private static volatile String discoveryError;
    private final UUID sessionId = UUID.randomUUID();
    private final Pending[] pending = new Pending[4];
    private final long[] failedDecision = new long[4];
    private final int[] failedHand = new int[4];
    private final String[] errors = new String[4];
    private final String[] selectedBots = new String[4];

    private record Settings(URI endpoint, Duration timeout) {}
    private record Config(String endpoint, int timeoutMs) {}
    private record Answer(int protocolVersion, String botId, UUID tableId, UUID sessionId,
                          int handNumber, int seat, long decision, int actionIndex) {}
    private record Pending(BotPosition position, CompletableFuture<HttpResponse<String>> future) {}

    public static void load(Path configDirectory) {
        settings = null;
        bots = List.of();
        discoveryError = null;
        Path file = configDirectory.resolve("mchjong").resolve("bot-service.json");
        if (!Files.exists(file)) return;
        try {
            Config config = JSON.fromJson(Files.readString(file), Config.class);
            if (config == null || config.endpoint() == null || config.endpoint().isBlank()
                || config.timeoutMs() < 100 || config.timeoutMs() > 60_000)
                throw new IllegalArgumentException("endpoint and timeout_ms (100–60000) are required");
            URI endpoint = URI.create(config.endpoint().replaceAll("/+$", ""));
            if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                || endpoint.getFragment() != null)
                throw new IllegalArgumentException("invalid Bot Service endpoint");
            settings = new Settings(endpoint, Duration.ofMillis(config.timeoutMs()));
            Settings configured = settings;
            discoveryError = "discovering";
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "/v1/bots"))
                .timeout(configured.timeout()).GET().build();
            HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, failure) -> {
                if (settings != configured) return;
                try {
                    if (failure != null || response.statusCode() != 200)
                        throw new IllegalStateException("Bot Service discovery failed", failure);
                    bots = parseBots(response.body());
                    discoveryError = null;
                } catch (RuntimeException error) {
                    bots = List.of();
                    discoveryError = error.getMessage() != null && error.getMessage().contains("protocol")
                        ? "incompatible_protocol" : "unavailable";
                    LOGGER.warn("Bot Service discovery failed", error);
                }
            });
        } catch (IOException | RuntimeException error) {
            discoveryError = "configuration";
            LOGGER.warn("Invalid Bot Service configuration {}", file, error);
        }
    }

    static List<ExternalBot> parseBots(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("protocol_version") || root.get("protocol_version").getAsInt() != 1)
            throw new IllegalStateException("incompatible Bot Service protocol version");
        var found = new ArrayList<ExternalBot>();
        var ids = new HashSet<String>();
        for (var element : root.getAsJsonArray("bots")) {
            JsonObject value = element.getAsJsonObject();
            var presets = new ArrayList<RiichiPreset>();
            for (var preset : value.getAsJsonArray("presets")) presets.add(RiichiPreset.valueOf(preset.getAsString()));
            var bot = new ExternalBot(value.get("id").getAsString(), value.get("name").getAsString(),
                value.get("player_count").getAsInt(), presets);
            if (!ids.add(bot.id())) throw new IllegalArgumentException("Duplicate Bot Service ID");
            found.add(bot);
        }
        return List.copyOf(found);
    }

    public static List<ExternalBot> availableBots() { return bots; }
    static String requestBody(BotPosition position) { return JSON.toJson(position); }

    static int validatedIndex(BotPosition issued, String body) {
        JsonObject fields = JsonParser.parseString(body).getAsJsonObject();
        for (String required : List.of("protocol_version", "bot_id", "table_id", "session_id",
            "hand_number", "seat", "decision", "action_index"))
            if (!fields.has(required) || fields.get(required).isJsonNull())
                throw new IllegalStateException("Bot Service omitted " + required);
        if (fields.has("action_id") && !fields.get("action_id").isJsonNull())
            throw new IllegalStateException("Bot Service supplied an unknown action ID");
        Answer answer = JSON.fromJson(body, Answer.class);
        if (answer == null || answer.protocolVersion() != 1)
            throw new IllegalStateException("incompatible Bot Service protocol version");
        if (!issued.botId().equals(answer.botId())
            || !issued.tableId().equals(answer.tableId()) || !issued.sessionId().equals(answer.sessionId())
            || issued.handNumber() != answer.handNumber() || issued.seat() != answer.seat()
            || issued.decision() != answer.decision()
            || answer.actionIndex() < 0 || answer.actionIndex() >= issued.legalActions().size())
            throw new IllegalStateException("Bot service returned a mismatched or illegal action");
        return answer.actionIndex();
    }

    BotServiceState state(RiichiSession game) {
        var seatErrors = new ArrayList<String>();
        for (int seat = 0; seat < game.rules().players(); seat++) {
            String id = game.externalBotId(seat);
            seatErrors.add(id == null ? null : errors[seat] != null ? errors[seat]
                : bots.stream().noneMatch(bot -> bot.id().equals(id) && bot.supports(game.rules())) ? "unavailable" : null);
        }
        return new BotServiceState(discoveryError, seatErrors);
    }

    void tick(RiichiSession session) {
        RiichiGame game = session.game();
        for (int seat = 0; seat < session.rules().players(); seat++) {
            String selected = session.externalBotId(seat);
            if (!java.util.Objects.equals(selectedBots[seat], selected)) {
                selectedBots[seat] = selected;
                errors[seat] = null;
            }
            BotPosition position = game == null ? null : game.botPosition(seat, sessionId);
            Pending current = pending[seat];
            if (current != null && (position == null || current.position().decision() != position.decision()
                || current.position().handNumber() != position.handNumber()
                || !current.position().botId().equals(position.botId()))) {
                current.future().cancel(true);
                pending[seat] = null;
                current = null;
            }
            if (position == null) continue;
            if (failedHand[seat] != position.handNumber() || failedDecision[seat] != position.decision()) errors[seat] = null;
            if (current != null && current.future().isDone()) {
                pending[seat] = null;
                try {
                    HttpResponse<String> response = current.future().join();
                    if (response.statusCode() != 200)
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    if (!game.actBot(seat, position.decision(), validatedIndex(position, response.body())))
                        throw new IllegalStateException("Decision expired");
                    errors[seat] = null;
                    continue;
                } catch (RuntimeException failure) {
                    LOGGER.warn("Bot Service decision failed for table {} seat {}", game.tableId(), seat, failure);
                    failedHand[seat] = position.handNumber();
                    failedDecision[seat] = position.decision();
                    Throwable reason = failure instanceof CompletionException ? failure.getCause() : failure;
                    errors[seat] = reason instanceof java.net.http.HttpTimeoutException ? "timeout"
                        : reason instanceof IllegalStateException && (reason.getMessage().contains("protocol")
                            || reason.getMessage().startsWith("HTTP 400")) ? "incompatible_protocol"
                        : reason instanceof IllegalStateException && reason.getMessage().startsWith("HTTP 503") ? "unavailable"
                        : reason instanceof IllegalStateException ? "invalid_response" : "unavailable";
                }
            }
            Settings configured = settings;
            if (pending[seat] != null || errors[seat] != null || configured == null
                || bots.stream().noneMatch(bot -> bot.id().equals(position.botId()) && bot.supports(game.rules()))) continue;
            HttpRequest request = HttpRequest.newBuilder(URI.create(configured.endpoint() + "/v1/decisions"))
                .timeout(configured.timeout()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(position))).build();
            pending[seat] = new Pending(position, HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }
    }
}
