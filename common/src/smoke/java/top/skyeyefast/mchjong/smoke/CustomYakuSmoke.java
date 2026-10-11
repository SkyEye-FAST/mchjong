package top.skyeyefast.mchjong.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.ClientYakuSets;
import top.skyeyefast.mchjong.client.RiichiRulesScreen;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.YakuSet;
import top.skyeyefast.mchjong.network.RiichiRulesPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Real server data-pack reload, catalog delivery, draft selection and stale-definition rejection. */
final class CustomYakuSmoke {
    private int stage, ticks;
    private Path definition;
    private List<String> originalPacks;
    private CompletableFuture<Void> pending;
    private YakuSet selected;
    private final MahjongVariant variant;
    CustomYakuSmoke(MahjongVariant variant) { this.variant = variant; }
    private boolean taiwan() { return variant == MahjongVariant.TAIWAN; }
    private boolean lobby(Minecraft client) { return taiwan() ? client.screen instanceof top.skyeyefast.mchjong.client.TaiwanLobbyScreen : client.screen instanceof RiichiTableScreen; }
    private void openRules(Minecraft client, MahjongTableBlockEntity table) {
        if (taiwan()) client.setScreen(new top.skyeyefast.mchjong.client.TaiwanRulesScreen(client.screen, table.getBlockPos()));
        else click(client, Component.translatable("rules.mchjong.title").getString());
    }
    private void apply(Minecraft client) { click(client, Component.translatable(taiwan() ? "gui.done" : "rules.mchjong.apply").getString()); }

    boolean tick(Minecraft client, MahjongTableBlockEntity table) {
        if (++ticks > 1000) throw new IllegalStateException("Custom yaku smoke timed out at " + stage);
        if (pending != null && pending.isCompletedExceptionally()) pending.join();
        var server = client.getSingleplayerServer();
        var set = taiwan() ? table.clientTaiwanSettings().rules().yakuSet() : table.clientRiichiSettings().rules().yakuSet();
        if (stage == 0) {
            pending = server.submit(() -> {
                try {
                    originalPacks = List.copyOf(server.getPackRepository().getSelectedIds());
                    Path pack = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("mchjong-custom-yaku-smoke");
                    definition = pack.resolve("data/smoke/mchjong/yaku_sets/custom.json");
                    Files.createDirectories(definition.getParent());
                    Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":" + net.minecraft.SharedConstants.getCurrentVersion()
                        .getPackVersion(net.minecraft.server.packs.PackType.SERVER_DATA) + ",\"description\":\"Custom yaku smoke\"}}");
                    Files.writeString(definition, json(2));
                    server.getPackRepository().reload();
                    var packs = new java.util.ArrayList<>(originalPacks); packs.add("file/mchjong-custom-yaku-smoke");
                    return server.reloadResources(packs);
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }).thenCompose(value -> value);
            stage = 1;
        } else if (stage == 1 && pending.isDone() && ClientYakuSets.available(variant).stream().anyMatch(s -> s.id().equals("smoke:custom"))) {
            selected = ClientYakuSets.available(variant).stream().filter(s -> s.id().equals("smoke:custom")).findFirst().orElseThrow();
            openRules(client, table);
            AutomationControlsSmoke.checkBounds(client);
            click(client, Component.translatable("rules.mchjong.yaku_set.select", ClientYakuSets.text(set.name())).getString());
            AutomationControlsSmoke.checkBounds(client);
            click(client, ClientYakuSets.text(selected.name()).getString());
            require(taiwan() ? client.screen instanceof top.skyeyefast.mchjong.client.TaiwanRulesScreen : client.screen instanceof RiichiRulesScreen, "Yaku selection lost its parent draft");
            require(set.isEmpty(), "Selecting a draft changed server rules before Apply");
            apply(client);
            stage = 2;
        } else if (stage == 2 && lobby(client) && set.equals(selected)) {
            pending = server.submit(() -> {
                try { Files.writeString(definition, json(3)); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                return server.reloadResources(server.getPackRepository().getSelectedIds());
            }).thenCompose(value -> value);
            stage = 3;
        } else if (stage == 3 && pending.isDone() && ClientYakuSets.available(variant).stream()
            .anyMatch(s -> s.id().equals("smoke:custom") && !s.equals(selected))) {
            require(set.equals(selected), "Reload changed a table's frozen award definitions");
            var id = client.player.getUUID(); var pos = table.getBlockPos();
            pending = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(id);
                var actual = (MahjongTableBlockEntity) player.serverLevel().getBlockEntity(pos);
                if (taiwan()) {
                    var session = (top.skyeyefast.mchjong.engine.TaiwanSession) actual.participantRoom(player);
                    var before = session.roomSettings().rules(); long token = session.decision();
                    actual.configureTaiwanRules(player, new top.skyeyefast.mchjong.network.TaiwanRulesPayload(pos, session.tableId(), session.incarnation(), token, before));
                    require(session.roomSettings().rules().equals(before) && session.decision() == token, "Stale Taiwan award digest was accepted");
                    actual.configureTaiwanRules(player, new top.skyeyefast.mchjong.network.TaiwanRulesPayload(pos, session.tableId(), session.incarnation(), token,
                        before.withYakuSet(YakuSet.empty(variant)), "smoke:forged", "0".repeat(64)));
                    require(session.roomSettings().rules().equals(before) && session.decision() == token, "Unknown Taiwan award set was accepted");
                    return;
                }
                var session = actual.participantSession(player);
                var before = session.rules(); long token = session.decision();
                actual.configureRules(player, new RiichiRulesPayload(pos, session.tableId(), token, before));
                require(session.rules().equals(before) && session.decision() == token, "Stale award digest was accepted");
                actual.configureRules(player, new RiichiRulesPayload(pos, session.tableId(), token, before.preset(), before.settings(), "smoke:forged", "0".repeat(64)));
                require(session.rules().equals(before) && session.decision() == token, "Unknown award set was accepted");
            });
            stage = 4;
        } else if (stage == 4 && pending.isDone()) {
            openRules(client, table);
            click(client, Component.translatable("rules.mchjong.yaku_set.select", ClientYakuSets.text(selected.name())).getString());
            click(client, ClientYakuSets.text(YakuSet.empty(variant).name()).getString());
            apply(client);
            stage = 5;
        } else if (stage == 5 && lobby(client) && set.isEmpty()) {
            pending = server.submit(() -> server.reloadResources(originalPacks)).thenCompose(value -> value);
            stage = 6;
        } else if (stage == 6 && pending.isDone() && ClientYakuSets.available(variant).stream().noneMatch(s -> s.id().equals("smoke:custom"))) {
            return true;
        }
        return false;
    }
    private String json(int value) {
        String text = "{\"en_us\":\"Smoke Awards\",\"ja_jp\":\"Smoke Awards\",\"zh_cn\":\"Smoke Awards\",\"zh_tw\":\"Smoke Awards\"}";
        String reward = taiwan() ? "{\"type\":\"tai\",\"value\":" + value + "}" : "{\"type\":\"han\",\"closed\":" + value + ",\"open\":1}";
        return "{\"variant\":\"" + (taiwan() ? "taiwan" : "riichi") + "\",\"name\":" + text + ",\"awards\":[{\"id\":\"smoke:regular\",\"name\":" + text + ",\"description\":" + text
            + ",\"when\":{\"type\":\"shape\",\"values\":[\"regular\"]},\"award\":" + reward + "}]}";
    }
    private static void click(Minecraft client, String label) {
        AbstractWidget button = LobbySmoke.find(client, label);
        require(button.active, "Disabled custom yaku control: " + label);
        client.screen.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
