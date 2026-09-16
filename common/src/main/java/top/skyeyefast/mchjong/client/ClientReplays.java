package top.skyeyefast.mchjong.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.ReplayPlayback;
import top.skyeyefast.mchjong.engine.TenhouReplay;
import top.skyeyefast.mchjong.network.ReplayPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.replay.ReplayStore;
import top.skyeyefast.mchjong.replay.ReplayTransfer;

public final class ClientReplays {
    private static final ReplayTransfer TRANSFER = new ReplayTransfer();
    private static Object connection;
    private ClientReplays() {}

    public static void tick() {
        Object current = Minecraft.getInstance().getConnection();
        if (connection != current) { TRANSFER.reset(); connection = current; }
        TRANSFER.expire(Util.getMillis());
    }

    public static void receive(ReplayPayload payload) {
        tick();
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;
        try {
            var completed = TRANSFER.accept(payload, Util.getMillis());
            if (completed == null) return;
            if (completed.kind() == ReplayPayload.Kind.INDEX) {
                var index = TableNetworking.JSON.fromJson(completed.text(), ReplayMatch.Index.class);
                Screen parent = client.screen instanceof ReplayBrowserScreen browser ? browser.parent() : client.screen;
                client.setScreen(new ReplayBrowserScreen(parent, index));
            } else {
                var match = TableNetworking.JSON.fromJson(completed.text(), ReplayMatch.class);
                if (match == null || match.hands().isEmpty()) throw new IllegalArgumentException("Empty replay");
                // Validate the timeline once before user-controlled seeking can render it.
                for (int hand = 0; hand < match.hands().size(); hand++)
                    ReplayPlayback.at(match, hand, match.hands().get(hand).events().size());
                client.setScreen(new ReplayScreen(client.screen, match));
            }
        } catch (RuntimeException failure) {
            TRANSFER.reset();
            LoggerFactory.getLogger("mchjong").warn("Rejected invalid replay transfer", failure);
            client.gui.getChat().addMessage(Component.translatable("message.mchjong.replay_unavailable"));
        }
    }

    public static void list(int page, String search, boolean oldestFirst) {
        var client = Minecraft.getInstance();
        if (client.getConnection() != null) client.getConnection().sendCommand("mchjong replays " + (page + 1)
            + " " + oldestFirst + " " + searchArgument(search));
    }
    public static void open(UUID id) {
        var client = Minecraft.getInstance();
        if (client.getConnection() != null) client.getConnection().sendCommand("mchjong replay " + id);
    }

    public static void delete(UUID id, String search, boolean oldestFirst) {
        var client = Minecraft.getInstance();
        if (client.getConnection() != null) client.getConnection().sendCommand("mchjong replay " + id + " delete "
            + oldestFirst + " " + searchArgument(search));
    }

    static String searchArgument(String search) {
        // Brigadier's escapeIfRequired returns an empty string unchanged; an argument still needs quotes.
        return search.isEmpty() ? "\"\"" : com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(search);
    }

    public static Path export(ReplayMatch match) throws IOException {
        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("replays/mchjong").resolve(match.id() + ".json");
        byte[] contents = TableNetworking.JSON.toJson(TenhouReplay.export(match)).getBytes(StandardCharsets.UTF_8);
        ReplayStore.atomicWrite(path, contents);
        return path.toAbsolutePath();
    }
}
