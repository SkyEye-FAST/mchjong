package top.skyeyefast.mchjong.compat.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Loaded only from client entry points after the loader confirms Ponder is present. */
public final class MchjongPonder implements PonderPlugin {
    private static final ResourceLocation TABLES = MahjongContent.id("mahjong");

    private final PonderPlugin extension;

    private MchjongPonder(PonderPlugin extension) { this.extension = extension; }

    public static void register() {
        register(null);
    }

    public static void register(PonderPlugin extension) {
        PonderIndex.addPlugin(new MchjongPonder(extension));
    }

    @Override public String getModId() { return MahjongContent.MOD_ID; }

    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<Item> items = helper.withKeyFunction(BuiltInRegistries.ITEM::getKey);
        for (Item item : new Item[] {MahjongContent.TABLE_ITEM, MahjongContent.AUTO_TABLE_ITEM, MahjongContent.STOOL_ITEM})
            items.addStoryBoard(item, "table", MahjongScenes::placement, TABLES);
        for (Item item : new Item[] {MahjongContent.TABLE_ITEM, MahjongContent.AUTO_TABLE_ITEM, MahjongContent.BOX_ITEM,
                MahjongContent.CLOTH_ITEM, MahjongContent.TILE_ITEM, MahjongContent.POINT_STICK})
            items.addStoryBoard(item, "table", MahjongScenes::equipment, TABLES);
        for (Item item : new Item[] {MahjongContent.TABLE_ITEM, MahjongContent.AUTO_TABLE_ITEM, MahjongContent.STOOL_ITEM})
            items.addStoryBoard(item, "table", MahjongScenes::playing, TABLES);
        if (extension != null) extension.registerScenes(helper);
    }

    @Override public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(TABLES).title("Mahjong")
            .description("Tables, supplies and seated play")
            .item(MahjongContent.TABLE_ITEM).addToIndex().register();
        for (Item item : new Item[] {MahjongContent.TABLE_ITEM, MahjongContent.AUTO_TABLE_ITEM, MahjongContent.STOOL_ITEM,
                MahjongContent.BOX_ITEM, MahjongContent.CLOTH_ITEM, MahjongContent.TILE_ITEM, MahjongContent.POINT_STICK})
            helper.addTagToComponent(BuiltInRegistries.ITEM.getKey(item), TABLES);
        if (extension != null) extension.registerTags(helper);
    }
}
