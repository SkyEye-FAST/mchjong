package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.network.*;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** A bounded rule draft. Only a server reply closes an applied proposal. */
public final class TaiwanRulesScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final BlockPos pos;
    private TaiwanGameState.Rules draft;
    private boolean pending;
    private int page;
    private MahjongEditBox base, perTai, dealer, repeat;
    private MahjongButton apply;
    private int left, top;
    public TaiwanRulesScreen(Screen parent, BlockPos pos) {
        super(Component.translatable("taiwan.mchjong.rules.title"));
        this.parent = parent; this.pos = pos.immutable();
        draft = table().clientTaiwanSettings().rules();
    }
    public BlockPos tablePos() { return pos; }
    @Override public Screen parent() { return parent; }
    private MahjongTableBlockEntity table() {
        var client = net.minecraft.client.Minecraft.getInstance();
        return (MahjongTableBlockEntity) client.level.getBlockEntity(pos);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float partial) {}
    public void receivedView(boolean reply) {
        if (pending && reply) {
            pending = false;
            if (draft.equals(table().clientTaiwanSettings().rules())) { onClose(); return; }
        }
        rebuild();
    }
    @Override protected void init() { rebuild(); }
    private boolean editable() {
        var room = table().clientTableRoom();
        return RoomLobby.host(room) && room.lobby() && !pending;
    }
    private void rebuild() {
        clearWidgets(); left = (width - 280) / 2; top = (height - 194) / 2;
        var room = table().clientTableRoom();
        for (int i = 0; i < 2; i++) {
            int index = i;
            addRenderableWidget(new MahjongButton(left + 8 + i * 134, top + 26, 130, 20,
                Component.translatable(i == 0 ? "taiwan.mchjong.presets" : "taiwan.mchjong.payment_rules"), ignored -> {
                    if (page == 1 && readPayment() == null) return;
                    page = index; rebuild();
                }).navigation().selected(page == i));
        }
        if (page == 0) {
            int row = 0;
            for (var preset : TaiwanPreset.values()) {
                var rules = TaiwanGameState.Rules.of(preset.rules());
                var button = addRenderableWidget(new MahjongButton(left + 8, top + 56 + row++ * 24, 264, 20,
                    Component.translatable("taiwan.mchjong.preset." + preset.name().toLowerCase(java.util.Locale.ROOT)), ignored -> { draft = rules; rebuild(); }).selected(draft.equals(rules)));
                button.active = editable();
            }
            var reserve = addRenderableWidget(new MahjongButton(left + 8, top + 110, 264, 20,
                Component.translatable("taiwan.mchjong.reserve." + draft.reserve().name().toLowerCase(java.util.Locale.ROOT)), ignored -> {
                    draft = new TaiwanGameState.Rules(draft.name(), draft.values(), draft.exclusions(), draft.sources(), draft.flowers(), draft.flowerSets(), draft.pinfu(), draft.replacements(), draft.taiLimit(),
                        draft.reserve() == TaiwanRules.Reserve.FIXED_SIXTEEN ? TaiwanRules.Reserve.SIXTEEN_PLUS_KONGS : TaiwanRules.Reserve.FIXED_SIXTEEN, draft.payment());
                    rebuild();
                }));
            reserve.active = editable() && table().clientTaiwanSettings().rulesEditable();
        } else {
            base = field(left + 8, top + 70, draft.payment().base());
            perTai = field(left + 144, top + 70, draft.payment().perTai());
            dealer = field(left + 8, top + 118, draft.payment().dealerTai());
            repeat = field(left + 144, top + 118, draft.payment().repeatTai());
        }
        apply = addRenderableWidget(new MahjongButton(left + 8, top + 166, 130, 20, Component.translatable("gui.done"), ignored -> {
            if (page == 1 && readPayment() == null) return;
            var current = table().clientTableRoom();
            if (!editable() || minecraft.getConnection() == null) return;
            pending = true;
            minecraft.getConnection().send(PayloadPackets.serverbound(new TaiwanRulesPayload(pos, current.tableId(), current.incarnation(), current.decision(), draft)));
            rebuild();
        }).primary());
        addRenderableWidget(new MahjongButton(left + 144, top + 166, 128, 20, Component.translatable("gui.cancel"), ignored -> onClose()));
        apply.active = editable();
    }
    private MahjongEditBox field(int x, int y, long value) {
        var box = new MahjongEditBox(font, x, y, 128, 20, title);
        box.setMaxLength(7); box.setFilter(text -> text.matches("[0-9]{0,7}")); box.setValue(Long.toString(value));
        box.setEditable(editable() && table().clientTaiwanSettings().rulesEditable());
        return addRenderableWidget(box);
    }
    private TaiwanRules.Payment readPayment() {
        try {
            var value = new TaiwanRules.Payment(Long.parseLong(base.getValue()), Long.parseLong(perTai.getValue()), Integer.parseInt(dealer.getValue()), Integer.parseInt(repeat.getValue()));
            draft = new TaiwanGameState.Rules(draft.name(), draft.values(), draft.exclusions(), draft.sources(), draft.flowers(), draft.flowerSets(), draft.pinfu(), draft.replacements(), draft.taiLimit(), draft.reserve(), value);
            return value;
        } catch (IllegalArgumentException invalid) { return null; }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float partial) {
        MahjongUi.panel(g, left, top, 280, 194);
        MahjongUi.text(g, font, pending ? Component.translatable("rules.mchjong.pending") : title, left + 8, top + 8, 264, MahjongUi.TEXT, false);
        if (page == 1) {
            String[] keys = {"base", "per_tai", "dealer", "repeat"};
            for (int i = 0; i < 4; i++) MahjongUi.text(g, font, Component.translatable("taiwan.mchjong.payment." + keys[i]), left + 8 + i % 2 * 136, top + 56 + i / 2 * 48, 128, MahjongUi.MUTED, false);
        } else MahjongUi.text(g, font, Component.translatable("taiwan.mchjong.stock", draft.flowers() == TaiwanRules.Flowers.NONE ? 136 : 144), left + 8, top + 144, 264, MahjongUi.MUTED, false);
        super.extractRenderState(g, x, y, partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
