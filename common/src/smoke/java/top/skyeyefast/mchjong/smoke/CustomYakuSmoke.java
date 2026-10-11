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
import top.skyeyefast.mchjong.engine.YakuSelection;
import top.skyeyefast.mchjong.network.YakuSelectionRequest;
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
        click(client, Component.translatable(taiwan() ? "taiwan.mchjong.payment_rules" : "rules.mchjong.group.scoring").getString());
    }
    private void apply(Minecraft client) { click(client, Component.translatable("rules.mchjong.apply").getString()); }

    boolean tick(Minecraft client, MahjongTableBlockEntity table) {
        if (++ticks > 1000) throw new IllegalStateException("Custom yaku smoke timed out at " + stage);
        if (pending != null && pending.isCompletedExceptionally()) pending.join();
        var server = client.getSingleplayerServer();
        var set = taiwan() ? table.clientTaiwanSettings().rules().yakuSelection() : table.clientRiichiSettings().rules().yakuSelection();
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
            var draft = set;
            click(client, ClientYakuSets.selectionLabel(draft).getString());
            if (!taiwan()) {
                click(client, toggle(Component.translatable("rules.mchjong.yaku_set.ancient"), false));
                draft = draft.withSet(top.skyeyefast.mchjong.engine.BuiltinYakuSets.MAHJONG_SOUL_ANCIENT, true);
                clickAward(client, toggle(ClientYakuSets.text(top.skyeyefast.mchjong.engine.BuiltinYakuSets.MAHJONG_SOUL_ANCIENT.awards().get(0).name()), true));
                click(client, Component.translatable("settings.mchjong.toggle", Component.translatable("rules.mchjong.yaku_set.ancient"),
                    Component.translatable("rules.mchjong.yaku_set.partial")).getString());
            }
            AutomationControlsSmoke.checkBounds(client);
            String nativeId = YakuSelection.nativeIds(variant).stream().sorted().findFirst().orElseThrow();
            click(client, toggle(nativeName(nativeId), true));
            clickAward(client, toggle(ClientYakuSets.text(selected.awards().get(0).name()), false));
            click(client, Component.translatable("gui.back").getString());
            require(taiwan() ? client.screen instanceof top.skyeyefast.mchjong.client.TaiwanRulesScreen : client.screen instanceof RiichiRulesScreen, "Yaku selection lost its parent draft");
            require(set.isDefault(), "Selecting a draft changed server rules before Apply");
            apply(client);
            stage = 2;
        } else if (stage == 2 && lobby(client) && set.sets().contains(selected) && set.disabledNative().size() == 1) {
            require(taiwan() || set.enabledCustom().size() == 15, "Ancient preset and data-pack award did not combine");
            pending = server.submit(() -> {
                try { Files.writeString(definition, json(3)); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                return server.reloadResources(server.getPackRepository().getSelectedIds());
            }).thenCompose(value -> value);
            stage = 3;
        } else if (stage == 3 && pending.isDone() && ClientYakuSets.available(variant).stream()
            .anyMatch(s -> s.id().equals("smoke:custom") && !s.equals(selected))) {
            require(set.sets().contains(selected), "Reload changed a table's frozen award definitions");
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
                        before.withYakuSelection(YakuSelection.defaults(variant)), forged()));
                    require(session.roomSettings().rules().equals(before) && session.decision() == token, "Unknown Taiwan award set was accepted");
                    return;
                }
                var session = actual.participantSession(player);
                var before = session.rules(); long token = session.decision();
                actual.configureRules(player, new RiichiRulesPayload(pos, session.tableId(), token, before));
                require(session.rules().equals(before) && session.decision() == token, "Stale award digest was accepted");
                actual.configureRules(player, new RiichiRulesPayload(pos, session.tableId(), token, before.preset(), before.settings(), forged()));
                require(session.rules().equals(before) && session.decision() == token, "Unknown award set was accepted");
            });
            stage = 4;
        } else if (stage == 4 && pending.isDone()) {
            openRules(client, table);
            click(client, ClientYakuSets.selectionLabel(set).getString());
            String nativeId = YakuSelection.nativeIds(variant).stream().sorted().findFirst().orElseThrow();
            click(client, toggle(nativeName(nativeId), false));
            click(client, Component.translatable("gui.back").getString());
            apply(client); stage = 7;
        } else if (stage == 7 && client.screen.children().stream().anyMatch(c -> c instanceof AbstractWidget w && w.visible
            && w.getMessage().getString().equals(Component.translatable("rules.mchjong.reload").getString()))) {
            require(set.disabledNative().size() == 1, "Rejected application changed server awards");
            String nativeId = YakuSelection.nativeIds(variant).stream().sorted().findFirst().orElseThrow();
            var retained = set.withNative(nativeId, true);
            click(client, ClientYakuSets.selectionLabel(retained).getString());
            click(client, Component.translatable("rules.mchjong.yaku_set.defaults").getString());
            click(client, Component.translatable("gui.back").getString());
            apply(client); stage = 5;
        } else if (stage == 5 && lobby(client) && set.isDefault()) {
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
    private static String toggle(Component name, boolean enabled) {
        return Component.translatable("settings.mchjong.toggle", name, Component.translatable(enabled ? "rules.mchjong.yes" : "rules.mchjong.no")).getString();
    }
    private static Component nativeName(String id) {
        return Component.translatable(id.startsWith("mchjong:riichi/") ? "yaku.mchjong." + id.substring("mchjong:riichi/".length())
            : "taiwan.mchjong.pattern." + id.substring("mchjong:taiwan/".length()));
    }
    private static void clickAward(Minecraft client, String label) {
        for (int page = 0; page < 100; page++) {
            if (client.screen.children().stream().anyMatch(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals(label))) {
                click(client, label); return;
            }
            click(client, "›");
        }
        throw new IllegalStateException("Missing custom award: " + label);
    }
    private static YakuSelectionRequest forged() {
        return new YakuSelectionRequest(java.util.Map.of("smoke:forged", new YakuSelectionRequest.Choice("0".repeat(64), 1)), java.util.Set.of());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
