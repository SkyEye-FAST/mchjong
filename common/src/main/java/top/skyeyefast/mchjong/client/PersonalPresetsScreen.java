package top.skyeyefast.mchjong.client;

import java.io.IOException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.skyeyefast.mchjong.config.BuiltinPresets;

/** A player's local cosmetic choices. */
public final class PersonalPresetsScreen extends Screen {
    private final Screen parent;
    private int tab;
    private int page;
    private boolean saveFailed;
    private PresetSource source = PresetSource.SERVER;
    private int presetRevision;
    private int contentLeft, contentSpan, previewWidth;
    private SettingsLayout layout;

    public PersonalPresetsScreen(Screen parent) {
        super(Component.translatable("settings.mchjong.personal_presets"));
        this.parent = parent;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        presetRevision = PresetSource.revision();
        layout = SettingsLayout.of(width, height);
        contentLeft = layout.bodyLeft();
        contentSpan = layout.bodyWidth();
        previewWidth = tab == 0 ? Math.min(88, contentSpan / 4) : 0;
        for (int category = 0; category < 2; category++) {
            int index = category, top = 38 + category * 78;
            addRenderableWidget(MahjongButton.create(Component.translatable(category == 0 ? "settings.mchjong.stick_preset" : "settings.mchjong.voice_preset"), ignored -> {
                tab = index; page = 0; init();
            }).bounds(layout.left(), top, layout.rail(), 22).build().navigation());
            for (int origin = 0; origin < 2; origin++) {
                PresetSource choice = origin == 0 ? PresetSource.SERVER : PresetSource.LOCAL;
                addRenderableWidget(MahjongButton.create(choice.label(), ignored -> {
                    tab = index; source = choice; page = 0; init();
                }).bounds(layout.left() + 8, top + 24 + origin * 22, layout.rail() - 8, 20)
                    .build().navigation().selected(tab == index && source == choice));
            }
        }
        var choices = choices();
        int rows = layout.rows();
        page = Math.clamp(page, 0, Math.max(0, (choices.size() - 1) / rows));
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            ResourceLocation id = choices.get(page * rows + i);
            var origin = tab == 0 ? RiichiStickPresets.source(id) : VoicePresets.source(id);
            Component label = tab == 0 ? RiichiStickPresets.label(id) : VoicePresets.label(id);
            addRenderableWidget(MahjongButton.create(label,
                ignored -> choose(id)).bounds(contentLeft + previewWidth, 60 + i * 22,
                    contentSpan - previewWidth, 20)
                .tooltip(origin.tooltip(label, id)).build()
                .option(label, Component.empty()).checked(id.equals(tab == 0 ? TableSettings.get().riichiStickPreset : TableSettings.get().voicePreset)));
        }
        int navY = height - 56;
        var previous = MahjongButton.create(Component.literal("<"), ignored -> { page--; init(); })
            .bounds(contentLeft, navY, 30, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        var next = MahjongButton.create(Component.literal(">"), ignored -> { page++; init(); })
            .bounds(contentLeft + contentSpan - 30, navY, 30, 20).build();
        next.active = (page + 1) * rows < choices.size();
        addRenderableWidget(next);
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(contentLeft + contentSpan - Math.min(120, contentSpan), height - 30, Math.min(120, contentSpan), 20).build().primary());
    }

    private void choose(ResourceLocation id) {
        var settings = TableSettings.get();
        if (tab == 0) {
            settings.riichiStickPreset = id;
            RiichiStickPresets.sendChoice();
        } else {
            settings.voicePreset = id;
            settings.voiceSource = TableSettings.VoiceSource.SELECTED;
            VoicePresets.sendChoice();
            TableAudio.settingsChanged();
        }
        try { settings.save(TableSettings.configPath()); saveFailed = false; }
        catch (IOException failure) { saveFailed = true; }
        init();
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        layout.paint(graphics, font, width, Component.translatable("settings.mchjong.scope.personal").append(" › ").append(title),
            Component.translatable(tab == 0 ? "settings.mchjong.stick_preset" : "settings.mchjong.voice_preset").append(" / ").append(source.label()));
        for (int category = 0; category < 2; category++)
            graphics.fill(layout.left(), 38 + category * 78, layout.left() + layout.rail(), 108 + category * 78, MahjongUi.INPUT);
        var choices = choices();
        int rows = layout.rows();
        if (tab == 0) for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            graphics.fill(contentLeft, 60 + i * 22, contentLeft + previewWidth, 80 + i * 22, MahjongUi.INPUT);
            var id = choices.get(page * rows + i);
            if (BuiltinPresets.STICKS.contains(id)) {
                var item = switch (id.getPath()) {
                    case "bamboo" -> Items.BAMBOO;
                    case "lightning_rod" -> Items.LIGHTNING_ROD;
                    case "end_rod" -> Items.END_ROD;
                    default -> throw new IllegalStateException(id.toString());
                };
                graphics.renderItem(new ItemStack(item), contentLeft + (previewWidth - 16) / 2, 62 + i * 22);
            } else graphics.blit(RiichiStickPresets.texture(id),
                contentLeft + 4, 67 + i * 22, previewWidth - 8, 7, 0, 0, 384, 32, 384, 32);
        }
        if (choices.size() > rows) graphics.drawCenteredString(font,
            (page + 1) + " / " + ((choices.size() - 1) / rows + 1), contentLeft + contentSpan / 2, height - 51, MahjongUi.MUTED);
        if (saveFailed) graphics.drawCenteredString(font, Component.translatable("settings.mchjong.save_failed"),
            width / 2, height - 76, MahjongUi.NEGATIVE);
        super.render(graphics, x, y, partialTick);
    }
    private java.util.List<ResourceLocation> choices() {
        return (tab == 0 ? RiichiStickPresets.choices() : VoicePresets.choices()).stream()
            .filter(id -> source.includes(tab == 0 ? RiichiStickPresets.source(id) : VoicePresets.source(id))).toList();
    }
    @Override public void tick() { if (presetRevision != PresetSource.revision()) init(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
