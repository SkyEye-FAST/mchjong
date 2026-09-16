package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.MahjongBoxScreen;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;

/** Actual synchronized box, native language reloads, and a 320x240 logical viewport. */
final class BoxInterfaceSmoke {
    private static final String[] LANGUAGES = {"en_us", "ja_jp", "zh_cn", "zh_tw"};
    private int sample = -1, settled, scale, windowWidth, windowHeight;
    private String language;
    private CompletableFuture<Void> reload;

    boolean tick(Minecraft client, Path output) {
        if (sample == -1) {
            language = client.getLanguageManager().getSelected();
            scale = client.options.guiScale().get();
            windowWidth = client.getWindow().getScreenWidth();
            windowHeight = client.getWindow().getScreenHeight();
            client.options.guiScale().set(3);
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 960, 720);
            client.resizeDisplay();
            sample = 0;
            select(client, LANGUAGES[sample]);
            return false;
        }
        if (!reload.isDone() || client.getOverlay() != null) return false;
        reload.join();
        if (++settled < 10) return false;
        if (sample == LANGUAGES.length) return true;
        require(client.screen instanceof MahjongBoxScreen, "Language reload replaced the box screen");
        require(client.screen.width == 320 && client.screen.height == 240, "Small-box fixture is not a 320x240 logical viewport");
        UiControlsSmoke.verify(client);
        var menu = (MahjongBoxMenu) client.player.containerMenu;
        require(menu.ownerSlot() == 0 && menu.slots.size() == 90, "Box menu lost its synchronized layout or carrier index");
        require(!menu.slots.get(81).mayPickup(client.player), "Client allows moving the open carrier");
        int header = client.font.split(Component.translatable("box.mchjong.contents"), 94).size() * 10;
        int counts = client.font.split(Component.translatable("box.mchjong.tiles", 3456), 94).size() * 10
            + client.font.split(Component.translatable("box.mchjong.sticks", 3456), 94).size() * 10
            + client.font.split(Component.translatable("box.mchjong.slots", 54, 54), 94).size() * 10;
        int status = client.font.split(Component.translatable("box.mchjong.incomplete"), 94).size() * 10;
        require(26 + header + counts + 8 + 4 + 4 + 6 + 12 + status <= 147, "Translated packing summary overlaps the help section");
        require(151 + client.font.split(Component.translatable("box.mchjong.help"), 94).size() * 10 <= 224,
            "Translated case instructions extend below the panel");
        Screenshot.grab(output.toFile(), "41-box-" + LANGUAGES[sample] + "-small.png", client.getMainRenderTarget(), ignored -> {});
        sample++;
        if (sample == LANGUAGES.length) {
            client.options.guiScale().set(scale);
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), windowWidth, windowHeight);
            client.resizeDisplay();
            select(client, language);
        } else select(client, LANGUAGES[sample]);
        return false;
    }

    private void select(Minecraft client, String language) {
        client.getLanguageManager().setSelected(language);
        reload = client.reloadResourcePacks();
        settled = 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
