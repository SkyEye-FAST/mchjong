package top.skyeyefast.mchjong.world;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.engine.BotPosition;
import top.skyeyefast.mchjong.engine.Game;

/** Nonblocking server-thread bridge to one configured local bot service. */
final class BotServiceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("mchjong");
    private static final Gson JSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final URI DECISIONS = endpoint();
    private final UUID sessionId = UUID.randomUUID();
    private final Pending[] pending = new Pending[4];
    private final long[] retryAt = new long[4];
    private long ticks;

    private record Answer(UUID tableId, UUID sessionId, int handNumber, int seat,
                          long decision, int actionIndex, String actionId) {}
    private record Pending(BotPosition position, CompletableFuture<HttpResponse<String>> future) {}

    static boolean enabled() { return DECISIONS != null; }

    private static URI endpoint() {
        String configured = System.getProperty("mchjong.botServiceEndpoint", "").trim();
        if (configured.isEmpty()) return null;
        URI uri = URI.create(configured.replaceAll("/+$", "") + "/v1/decisions");
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
            throw new IllegalArgumentException("Bot service endpoint must use HTTP or HTTPS");
        return uri;
    }

    static String requestBody(BotPosition position) { return JSON.toJson(position); }

    static int validatedIndex(BotPosition issued, String body) {
        Answer answer = JSON.fromJson(body, Answer.class);
        if (answer == null || !issued.tableId().equals(answer.tableId())
            || !issued.sessionId().equals(answer.sessionId())
            || issued.handNumber() != answer.handNumber() || issued.seat() != answer.seat()
            || issued.decision() != answer.decision() || answer.actionId() != null
            || answer.actionIndex() < 0 || answer.actionIndex() >= issued.legalActions().size())
            throw new IllegalStateException("Bot service returned a mismatched or illegal action");
        return answer.actionIndex();
    }

    void tick(Game game) {
        if (!enabled()) return;
        ticks++;
        for (int seat = 0; seat < game.rules().players(); seat++) {
            BotPosition position = game.botPosition(seat, sessionId);
            Pending current = pending[seat];
            if (current != null && (position == null || current.position().decision() != position.decision()
                || current.position().handNumber() != position.handNumber())) {
                pending[seat] = null;
                current = null;
            }
            if (current != null && current.future().isDone()) {
                pending[seat] = null;
                try {
                    HttpResponse<String> response = current.future().join();
                    if (response.statusCode() != 200)
                        throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
                    BotPosition issued = current.position();
                    game.actBot(seat, issued.decision(), validatedIndex(issued, response.body()));
                    continue;
                } catch (RuntimeException failure) {
                    LOGGER.warn("Bot service decision failed for table {} seat {}", game.tableId(), seat, failure);
                    retryAt[seat] = ticks + 100;
                }
            }
            if (position == null || pending[seat] != null || ticks < retryAt[seat]) continue;
            HttpRequest request = HttpRequest.newBuilder(DECISIONS)
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(position))).build();
            pending[seat] = new Pending(position, HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }
    }
}
