package top.skyeyefast.mchjong.client;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MahjongUiTest {
    @Test void primaryAndSupportingTextStayReadableAcrossActiveControlStates() {
        for (int foreground : new int[]{MahjongUi.TEXT, MahjongUi.MUTED}) {
            for (int background : new int[]{MahjongUi.PANEL, MahjongUi.SURFACE, MahjongUi.HOVER, MahjongUi.SELECTED, MahjongUi.INPUT}) {
                double contrast = (luminance(foreground) + 0.05) / (luminance(background) + 0.05);
                assertTrue(contrast >= 4.5, "Insufficient text contrast: " + contrast);
            }
        }
    }

    @Test void projectScreensUseProjectControlsAndBoxMenu() throws Exception {
        Path root = Path.of("..");
        Path client = root.resolve("common/src/main/java/top/skyeyefast/mchjong/client");
        try (var paths = Files.list(client)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith("Screen.java")).toList()) {
                String source = Files.readString(path);
                for (String stock : new String[]{"Button.builder(", "new Button(", "new EditBox(", "extends AbstractSliderButton"})
                    assertFalse(source.contains(stock), path + " must use the project controls, not " + stock);
            }
        }
        String menu = Files.readString(root.resolve("common/src/main/java/top/skyeyefast/mchjong/item/MahjongBoxMenu.java"));
        assertFalse(menu.contains("GENERIC_9x6"));
        assertTrue(menu.contains("MahjongContent.BOX_MENU"));
        assertTrue(Files.readString(root.resolve("AGENTS.md")).contains("docs/UI_STYLE.md"));
    }

    private static double luminance(int color) {
        return channel(color >> 16) * 0.2126 + channel(color >> 8) * 0.7152 + channel(color) * 0.0722;
    }
    private static double channel(int channel) {
        double value = (channel & 255) / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
