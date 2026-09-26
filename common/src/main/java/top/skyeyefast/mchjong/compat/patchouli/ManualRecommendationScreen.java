package top.skyeyefast.mchjong.compat.patchouli;

import java.io.IOException;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import top.skyeyefast.mchjong.client.MahjongButton;
import top.skyeyefast.mchjong.client.MahjongUi;
import top.skyeyefast.mchjong.client.TableSettings;

public final class ManualRecommendationScreen extends Screen {
    private static final String DOWNLOAD = "https://modrinth.com/mod/patchouli/versions";
    private MultiLineLabel message = MultiLineLabel.EMPTY;
    private int left, top, span;

    public ManualRecommendationScreen() {
        super(Component.translatable("manual.mchjong.recommend.title"));
    }

    @Override protected void init() {
        span = Math.min(300, width - 32);
        left = (width - span) / 2;
        message = MultiLineLabel.create(font, Component.translatable("manual.mchjong.recommend.text"), span - 16);
        int body = message.getLineCount() * 10;
        int panelHeight = 114 + body;
        top = (height - panelHeight) / 2;
        int buttons = top + 32 + body;
        addRenderableWidget(new MahjongButton(left + 8, buttons, span - 16, 20,
            Component.translatable("manual.mchjong.recommend.download"), button -> minecraft.setScreen(new ConfirmLinkScreen(accepted -> {
                if (accepted) Util.getPlatform().openUri(DOWNLOAD);
                minecraft.setScreen(this);
            }, DOWNLOAD, true))).primary());
        addRenderableWidget(new MahjongButton(left + 8, buttons + 24, span - 16, 20,
            Component.translatable("manual.mchjong.recommend.continue"), button -> onClose()));
        addRenderableWidget(new MahjongButton(left + 8, buttons + 48, span - 16, 20,
            Component.translatable("manual.mchjong.recommend.dismiss"), button -> {
                TableSettings.get().recommendPatchouli = false;
                try { TableSettings.get().save(TableSettings.configPath()); }
                catch (IOException failure) { LoggerFactory.getLogger("mchjong").warn("Cannot save manual preference", failure); }
                onClose();
            }));
    }

    @Override public Component getNarrationMessage() {
        return super.getNarrationMessage().copy().append(". ").append(Component.translatable("manual.mchjong.recommend.text"));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MahjongUi.BACKDROP);
        MahjongUi.panel(graphics, left, top, span, 114 + message.getLineCount() * 10);
        MahjongUi.text(graphics, font, title, left + 8, top + 10, span - 16, MahjongUi.ACCENT, true);
        graphics.textWithWordWrap(font, Component.translatable("manual.mchjong.recommend.text"),
            left + 8, top + 26, span - 16, MahjongUi.TEXT, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
