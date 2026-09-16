package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.MahjongBoxScreen;
import top.skyeyefast.mchjong.client.MahjongButton;
import top.skyeyefast.mchjong.client.MahjongEditBox;
import top.skyeyefast.mchjong.client.MahjongSlider;
import top.skyeyefast.mchjong.client.TableClockScreen;
import top.skyeyefast.mchjong.client.TableInviteScreen;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettingsScreen;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Real client menu packets and rendered controls; no screen-only inventory mutations. */
final class InterfaceSmoke {
    private int boxStage, boxTicks, settingsStage, settingsTicks;
    private int windowWidth, windowHeight, guiScale, originalTiles;
    private ItemStack moved = ItemStack.EMPTY;

    boolean box(Minecraft client, Path output) {
        boxTicks++;
        require(boxTicks < 400, "Box UI timed out at " + boxStage);
        require(client.screen instanceof MahjongBoxScreen, "Box screen was lost during inventory interaction");
        var menu = (MahjongBoxMenu) client.player.containerMenu;
        if (boxStage == 0) {
            windowWidth = client.getWindow().getWidth(); windowHeight = client.getWindow().getHeight();
            guiScale = client.options.guiScale().get();
            require(menu.ownerSlot() == 0, "Carrier index was not synchronized");
            require(!menu.slots.get(81).mayPickup(client.player), "Client exposes the locked carrier");
            require(MahjongSupplies.deck(menu.items()) != null, "Ready set is not reflected in client contents");
            originalTiles = MahjongSupplies.tileCount(menu.items());
            moved = menu.slots.getFirst().getItem().copy();
            capture(client, output, "30-box-complete.png");
            client.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, client.player);
            boxStage = 1; boxTicks = 0;
        } else if (boxStage == 1 && boxTicks > 10) {
            require(MahjongSupplies.tileCount(menu.items()) == originalTiles - moved.getCount(), "Client shift transfer lost items");
            require(MahjongSupplies.deck(menu.items()) == null, "Incomplete set stayed ready");
            capture(client, output, "31-box-incomplete.png");
            var destination = menu.slots.stream().filter(slot -> slot.index >= MahjongSupplies.BOX_SLOTS
                && ItemStack.isSameItemSameComponents(slot.getItem(), moved)).findFirst().orElseThrow();
            client.gameMode.handleInventoryMouseClick(menu.containerId, destination.index, 0, ClickType.QUICK_MOVE, client.player);
            boxStage = 2; boxTicks = 0;
        } else if (boxStage == 2 && boxTicks > 10) {
            require(MahjongSupplies.tileCount(menu.items()) == originalTiles && MahjongSupplies.deck(menu.items()) != null,
                "Returning a stack did not restore the complete set");
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
            boxStage = 3; boxTicks = 0;
        } else if (boxStage == 3 && boxTicks > 15) {
            require(client.screen.width == 320 && client.screen.height == 240, "Small case viewport was not 320x240");
            for (var slot : menu.slots) require(slot.x >= 0 && slot.y >= 0 && slot.x + 16 <= 304 && slot.y + 16 <= 232,
                "Case slot exceeds the panel");
            checkBounds(client);
            capture(client, output, "32-box-320x240.png");
            restoreWindow(client);
            boxStage = 4; boxTicks = 0;
        } else if (boxStage == 4 && boxTicks > 10) return true;
        return false;
    }

    boolean settings(Minecraft client, MahjongTableBlockEntity table, Path output) {
        settingsTicks++;
        if (settingsStage == 0) {
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
            client.setScreen(new TableSettingsScreen(new TableScreen(table.getBlockPos())));
            settingsStage = 1; settingsTicks = 0;
        } else if (settingsStage >= 1 && settingsStage <= 4 && settingsTicks > 12) {
            checkBounds(client);
            require(client.screen.width == 320 && client.screen.height == 240, "Settings viewport was not 320x240");
            client.screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
            require(client.screen.getFocused() != null, "Tab cannot focus a custom control");
            capture(client, output, "33-settings-tab-" + (settingsStage - 1) + ".png");
            if (settingsStage < 4) click(client, "settings.mchjong.tab." + settingsStage);
            else client.setScreen(new TableClockScreen(new TableScreen(table.getBlockPos()), table.clientView().timeControl()));
            settingsStage++; settingsTicks = 0;
        } else if (settingsStage == 5 && settingsTicks > 10) {
            checkBounds(client);
            var field = client.screen.children().stream().filter(MahjongEditBox.class::isInstance)
                .map(MahjongEditBox.class::cast).findFirst().orElseThrow();
            client.screen.mouseClicked(field.getX() + 3, field.getY() + 3, 0);
            require(field.isFocused(), "Padded field edge cannot receive focus");
            field.setValue("");
            settingsStage = 6; settingsTicks = 0;
        } else if (settingsStage == 6 && settingsTicks > 10) {
            require(!button(client, "gui.done").active, "Invalid numeric input leaves Apply enabled");
            require(client.screen.charTyped('6', 0), "Native numeric text entry failed");
            client.screen.charTyped('a', 0);
            var field = client.screen.children().stream().filter(MahjongEditBox.class::isInstance)
                .map(MahjongEditBox.class::cast).findFirst().orElseThrow();
            require(field.getValue().equals("6"), "Numeric filter accepted a letter");
            settingsStage = 7; settingsTicks = 0;
        } else if (settingsStage == 7 && settingsTicks > 10) {
            require(button(client, "gui.done").active, "Valid numeric input cannot be applied");
            capture(client, output, "34-clock-keyboard.png");
            client.screen.onClose(); // Do not send edited clock values into a running table.
            client.setScreen(new TableInviteScreen(new TableScreen(table.getBlockPos())));
            settingsStage = 8; settingsTicks = 0;
        } else if (settingsStage == 8 && settingsTicks > 10) {
            checkBounds(client);
            capture(client, output, "35-invite-small.png");
            client.screen.onClose();
            restoreWindow(client);
            settingsStage = 9; settingsTicks = 0;
        } else if (settingsStage == 9 && settingsTicks > 10) return true;
        return false;
    }

    private void restoreWindow(Minecraft client) {
        client.getWindow().setWindowed(windowWidth, windowHeight);
        client.options.guiScale().set(guiScale);
        client.resizeDisplay();
    }

    private static AbstractWidget button(Minecraft client, String key) {
        var label = Component.translatable(key).getString();
        return client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static void click(Minecraft client, String key) {
        var widget = button(client, key);
        require(widget.active, "Disabled control: " + key);
        client.screen.mouseClicked(widget.getX() + 5, widget.getY() + 5, 0);
    }

    private static void checkBounds(Minecraft client) {
        for (var child : client.screen.children()) if (child instanceof AbstractWidget widget) {
            require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= client.screen.width
                && widget.getBottom() <= client.screen.height, "Control exceeds viewport: " + widget.getMessage().getString());
            require(!(widget instanceof Button) || widget instanceof MahjongButton, "Stock button returned to a project screen");
            require(!(widget instanceof AbstractSliderButton) || widget instanceof MahjongSlider, "Stock slider returned to a project screen");
        }
    }

    private static void capture(Minecraft client, Path output, String name) {
        Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
