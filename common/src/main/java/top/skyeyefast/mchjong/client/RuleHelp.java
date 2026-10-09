package top.skyeyefast.mchjong.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import top.skyeyefast.mchjong.engine.RiichiRuleOption;
import top.skyeyefast.mchjong.engine.SichuanRuleOption;
import top.skyeyefast.mchjong.platform.ResourceIds;

/** Short help and precise handbook destinations share the handbook's existing resource pages. */
public final class RuleHelp {
    record Topic(String textKey, String entry, int page, ResourceLocation image) {
        Component description(boolean award) {
            String text = Component.translatable(textKey).getString();
            if (award && text.startsWith("$(bold)") && text.contains("$(br2)")) text = text.substring(text.indexOf("$(br2)") + 6);
            return Component.literal(plain(text));
        }
    }
    private static Map<String, Topic> pages = Map.of();
    private static Map<String, Topic> awards = Map.of();
    private static java.util.function.BiConsumer<String, Integer> handbook;
    private static java.util.function.Predicate<Screen> handbookScreen;
    private static Screen returnTo;
    private RuleHelp() {}

    /** The optional adapter registers navigation only after its loader checks availability. */
    public static void handbook(java.util.function.BiConsumer<String, Integer> open, java.util.function.Predicate<Screen> screen) {
        handbook = open; handbookScreen = screen;
    }
    public static void tickHandbook() {
        var client = Minecraft.getInstance();
        if (returnTo != null && !handbookScreen.test(client.screen)) {
            var parent = returnTo; returnTo = null;
            if (client.screen == null && client.level != null) client.setScreen(parent);
        }
    }
    static Screen manualParent(Screen screen) { return returnTo != null && handbookScreen.test(screen) ? returnTo : null; }
    private static void openManual(Topic topic) {
        returnTo = Minecraft.getInstance().screen;
        handbook.accept(topic.entry(), topic.page());
    }

    public static void reload(ResourceManager resources) {
        var nextPages = new HashMap<String, Topic>();
        var nextAwards = new HashMap<String, Topic>();
        resources.listResources("patchouli_books/guide/en_us/entries", id -> id.getNamespace().equals("mchjong") && id.getPath().endsWith(".json"))
            .forEach((id, resource) -> {
                try (var reader = resource.openAsReader()) {
                    String path = id.getPath();
                    addEntry(path.substring(path.lastIndexOf('/') + 1, path.length() - 5), JsonParser.parseReader(reader).getAsJsonObject(), nextPages, nextAwards);
                } catch (IOException failure) { throw new IllegalStateException("Cannot read rule help " + id, failure); }
            });
        alias(nextAwards, "mcr.mchjong.fan.mixed_kong_pair", nextAwards.get("mcr.mchjong.fan.two_melded_kongs"));
        alias(nextAwards, "sichuan.mchjong.fan.root_with_kong", nextPages.get("sichuan_payments:2"));
        alias(nextAwards, "sichuan.mchjong.fan.basic", nextPages.get("sichuan_flow:2"));
        for (String bonus : List.of("dora", "ura_dora", "red_dora", "nuki_dora"))
            alias(nextAwards, "yaku.mchjong." + bonus, nextPages.get("riichi_points:5"));
        alias(nextAwards, "ui.mchjong.dora", nextPages.get("riichi_points:5"));
        pages = Map.copyOf(nextPages); awards = Map.copyOf(nextAwards);
    }

    static void addEntry(String entry, JsonObject data, Map<String, Topic> pages, Map<String, Topic> awards) {
        var values = data.getAsJsonArray("pages");
        String prefix = entry.startsWith("riichi_yaku") ? "yaku.mchjong." : entry.startsWith("mcr_fan_") ? "mcr.mchjong.fan."
            : entry.equals("sichuan_fan") ? "sichuan.mchjong.fan." : entry.equals("taiwan_tai") ? "taiwan.mchjong.pattern." : null;
        for (int index = 0; index < values.size(); index++) {
            var page = values.get(index).getAsJsonObject();
            if (!page.has("text")) continue;
            ResourceLocation image = null;
            if (index + 1 < values.size()) {
                var next = values.get(index + 1).getAsJsonObject();
                if (page.has("anchor") && next.has("images")) image = ResourceIds.of(next.getAsJsonArray("images").get(0).getAsString());
            }
            var topic = new Topic(page.get("text").getAsString(), entry, index, image);
            pages.put(entry + ":" + index, topic);
            if (prefix != null && page.has("anchor")) awards.put(prefix + page.get("anchor").getAsString(), topic);
        }
    }
    private static void alias(Map<String, Topic> map, String key, Topic topic) { if (topic != null) map.put(key, topic); }
    static String plain(String text) { return text.replace("$(br2)", "\n\n").replace("$(br)", "\n").replace("$(li)", "\n").replaceAll("\\$\\([^)]*\\)", "").strip(); }
    static Topic award(Component label) {
        return label.getContents() instanceof TranslatableContents translated ? awards.get(translated.getKey()) : null;
    }
    static Component hover(Component label) {
        var topic = award(label);
        return topic == null ? label : label.copy().append("\n").append(topic.description(true).getString().split("\n\n", 2)[0])
            .append("\n").append(Component.translatable("ui.mchjong.result_explanation"));
    }
    public static void openAward(Screen parent, Component label) {
        var topic = award(label);
        if (topic != null) open(parent, label, topic.description(true), topic);
    }
    private static void open(Screen parent, Component label, Component description, Topic topic) {
        Minecraft.getInstance().setScreen(new TableHelpScreen(parent, label, () -> List.of(description), topic == null ? null : topic.image(),
            topic != null && handbook != null ? () -> openManual(topic) : null));
    }
    static MahjongButton setting(Screen parent, Component label, Component description, String entry, int page, int x, int y) {
        var topic = pages.get(entry + ":" + page);
        var name = Component.translatable("rules.mchjong.explanation", label);
        var button = new MahjongButton(x, y, 20, 20, name, ignored -> open(parent, label, description, topic)).shortCaption(Component.literal("?"));
        button.setTooltip(Tooltip.create(label.copy().append("\n").append(description)));
        return button;
    }
    static MahjongButton setting(Screen parent, RiichiRuleOption option, Component label, Component description, int x, int y) {
        String target = switch (option) {
            case STARTING_POINTS, RETURN_POINTS, UMA_1, UMA_2, UMA_3, UMA_4 -> "riichi_presets:1";
            case TARGET_POINTS, DOUBLE_YAKUMAN, AGARI_YAME, EXTENSION -> "riichi_presets:2";
            case FLOATING_PLACEMENT, ROUND_SHARED_PLACEMENT -> "riichi_presets:4";
            case SHARED_RANKS, AWARD_FINAL_DEPOSITS, RENHOU_MANGAN, HEAD_BUMP -> "riichi_presets:5";
            case KUITAN, MIN_HAN, IPPATSU_COUNTS_TOWARD_MINIMUM -> "riichi_presets:0";
            case URA_DORA, KAN_DORA, RED_FIVES, DELAYED_OPEN_KAN_DORA -> "riichi_points:5";
            case KAZOE_YAKUMAN, COMPOUND_YAKUMAN -> "riichi_points:4";
            case KIRIAGE_MANGAN, DOUBLE_WIND_PAIR_FU, RIICHI_KAN_KEEPS_MELDS, RIICHI_KAN_KEEPS_YAKU,
                SUUKANTSU_PAO, WHOLE_HAND_PAO, PAO_RON_HONBA_BY_DISCARDER, PAO_TSUMO_HONBA_SHARED -> "riichi_presets:6";
            case IPPATSU, MIN_RIICHI_WALL, NEEDS_RIICHI_DEPOSIT -> "riichi:0";
            case CALLS_CLEAR_FURITEN, YAKULESS_FURITEN -> "riichi:1";
            case MATCH_LENGTH -> "riichi_flow:3";
            case BANKRUPTCY, TRIPLE_RON_DRAW, FORMAL_TENPAI_IGNORES_MELDS -> "riichi_flow:2";
            case ABORTIVE_DRAWS, NAGASHI_MANGAN, NAGASHI_ALLOWS_CALLS -> "riichi_flow:7";
            case ROB_CONCEALED_KAN -> "riichi_flow:1";
            case REPLACEMENT_CAPACITY -> "riichi_flow:0";
            case ROB_NORTH_WITHOUT_KOKUSHI -> null;
            default -> "riichi_presets:4"; // Floating placement matrix entries.
        };
        return setting(parent, label, description, target, x, y);
    }
    static MahjongButton setting(Screen parent, SichuanRuleOption option, Component label, Component description, int x, int y) {
        String target = switch (option) {
            case FAN_CAP, SELF_DRAW_BONUS -> "sichuan_flow:4";
            case MATCH_HANDS -> "sichuan_flow:5";
            case SELECT_FIRST_DISCARD -> "sichuan_flow:1";
            case CONCEALED_KONG_PAYMENT, DISCARD_KONG_PAYMENT, ADDED_KONG_PAYMENT -> "sichuan_payments:0";
            case TRANSFER_KONG_ON_SHOOT -> "sichuan_payments:1";
            case SEPARATE_KONG_FAN, ADDED_KONG_AFTER_KONG_IS_SHOOT, EAST_WEST_LONG_WALL -> "sichuan_payments:2";
            case REFUND_KONG_WHEN_NOT_READY -> "sichuan_payments:3";
            case ACTIVE_FLOWER_PIG_PENALTY -> "sichuan_payments:4";
        };
        return setting(parent, label, description, target, x, y);
    }
    private static MahjongButton setting(Screen parent, Component label, Component description, String target, int x, int y) {
        int separator = target == null ? -1 : target.indexOf(':');
        return setting(parent, label, description, separator < 0 ? "" : target.substring(0, separator),
            separator < 0 ? -1 : Integer.parseInt(target.substring(separator + 1)), x, y);
    }
    static Component pageDescription(String entry, int page) { return pages.get(entry + ":" + page).description(false); }
}
