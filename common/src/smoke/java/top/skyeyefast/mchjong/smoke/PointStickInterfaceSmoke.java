package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.PointStickScreen;
import top.skyeyefast.mchjong.item.PointStickMenu;

/** Native mouse payments and one small-window rendering. */
final class PointStickInterfaceSmoke {
    private static final String[] LANGUAGES = {"zh_cn"};
    private int stage, ticks, languageIndex, scale, width, height;
    private String language;
    private CompletableFuture<Void> reload;

    boolean tick(Minecraft client, Path output) {
        if (++ticks > 600) throw new IllegalStateException("Point-stick interface timed out at stage " + stage);
        if (stage == 0 && ticks >= 10 && client.screen instanceof PointStickScreen) {
            check(menu(client).totalPoints(0) == 3000 && menu(client).totalPoints(1) == 0, "Drawer contents did not synchronize");
            var bounds = ((PointStickScreen) client.screen).browserBounds();
            client.screen.mouseScrolled(bounds.left() + 10, bounds.top() + 30, 0, -1);
            check(menu(client).recipientSide() == 1, "Mouse wheel did not select the recipient row");
            clickSlot(client, 0, 1);
            next(1);
        } else if (stage == 1 && ticks >= 5) {
            check(menu(client).recipientSide() == 1, "Recipient row selection did not synchronize");
            check(menu(client).getCarried().getCount() == 2, "Native right-click did not split the chosen payment");
            clickSlot(client, 10, 0);
            next(2);
        } else if (stage == 2 && ticks >= 10) {
            var menu = menu(client);
            check(menu.totalPoints(0) == 1000 && menu.totalPoints(1) == 2000 && menu.getCarried().isEmpty(), "Native hand payment did not reach the recipient");
            capture(client, output, "53-drawer-payment.png");
            clickSlot(client, 10, 0);
            next(3);
        } else if (stage == 3 && ticks >= 5) {
            clickSlot(client, 0, 0);
            next(4);
        } else if (stage == 4 && ticks >= 10) {
            check(menu(client).totalPoints(0) == 3000 && menu(client).totalPoints(1) == 0, "Drawer fixture did not return the exact payment");
            language = client.getLanguageManager().getSelected();
            scale = client.options.guiScale().get();
            width = client.getWindow().getScreenWidth();
            height = client.getWindow().getScreenHeight();
            client.options.guiScale().set(3);
            GLFW.glfwSetWindowSize(client.getWindow().handle(), 960, 720);
            client.resizeGui();
            select(client, LANGUAGES[languageIndex]);
            next(5);
        } else if (stage == 5 && reload.isDone() && client.getOverlay() == null && ticks >= 15) {
            reload.join();
            check(client.screen instanceof PointStickScreen && client.screen.width == 320 && client.screen.height == 240,
                "Point-stick interface did not retain its 320x240 native layout");
            check(menu(client).slots.size() == 76, "Drawer rows or player inventory changed during reload");
            check(128 + client.font.split(Component.translatable("sticks.mchjong.deliver"), 94).size() * 10 <= 200,
                "Point-stick instructions overlap the balance legend");
            capture(client, output, "54-drawer-" + LANGUAGES[languageIndex] + "-small.png");
            if (++languageIndex < LANGUAGES.length) { select(client, LANGUAGES[languageIndex]); ticks = 0; }
            else {
                client.options.guiScale().set(scale);
                GLFW.glfwSetWindowSize(client.getWindow().handle(), width, height);
                client.resizeGui();
                select(client, language);
                next(6);
            }
        } else if (stage == 6 && reload.isDone() && client.getOverlay() == null && ticks >= 15) {
            reload.join();
            return true;
        }
        return false;
    }

    private static PointStickMenu menu(Minecraft client) {
        check(client.player.containerMenu instanceof PointStickMenu, "Native point-stick menu was replaced");
        return (PointStickMenu) client.player.containerMenu;
    }

    private static void clickSlot(Minecraft client, int index, int button) {
        var slot = menu(client).getSlot(index);
        var bounds = ((PointStickScreen) client.screen).browserBounds();
        double x = bounds.left() + slot.x + 8;
        double y = bounds.top() + slot.y + 8;
        var event = new net.minecraft.client.input.MouseButtonEvent(
            x, y, new net.minecraft.client.input.MouseButtonInfo(button, 0));
        client.screen.mouseClicked(event, false);
        client.screen.mouseReleased(event);
    }

    private void next(int value) { stage = value; ticks = 0; }
    private void select(Minecraft client, String value) {
        client.getLanguageManager().setSelected(value);
        reload = client.reloadResourcePacks();
    }

    private static void capture(Minecraft client, Path output, String name) {
        SmokeScreenshots.grab(output.toFile(), name, client.getMainRenderTarget(), 1, ignored -> {});
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
