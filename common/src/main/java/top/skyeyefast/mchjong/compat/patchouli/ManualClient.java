package top.skyeyefast.mchjong.compat.patchouli;

import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableSettings;

/** The absent-dependency path deliberately contains no Patchouli API references. */
public final class ManualClient {
    private static boolean recommended;

    private ManualClient() {}

    public static void tick(boolean installed) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.screen != null) return;
        if (!installed && !recommended && TableSettings.get().recommendPatchouli) {
            recommended = true;
            recommend(client);
        }
    }

    private static void recommend(Minecraft client) {
        client.gui.getChat().addClientSystemMessage(Component.translatable("manual.mchjong.recommend.text")
            .append(" ").append(Component.translatable("manual.mchjong.recommend.download")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create("https://modrinth.com/mod/patchouli/versions"))))));
    }
}
