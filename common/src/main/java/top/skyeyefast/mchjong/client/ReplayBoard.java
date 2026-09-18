package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.engine.ReplayHand;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.ReplayPlayback;
import top.skyeyefast.mchjong.engine.Tile;

/** Responsive, scrollable replay table, independent of the live 3D block renderer. */
public final class ReplayBoard extends AbstractWidget {
    private final Font font;
    private final ReplayMatch match;
    private int handIndex, scroll, contentHeight;
    private ReplayPlayback.Frame frame;
    public ReplayBoard(Font font, ReplayMatch match, int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("replay.mchjong.title"));
        this.font = font; this.match = match;
    }
    public void show(int handIndex, int cursor) {
        if (this.handIndex != handIndex) scroll = 0;
        this.handIndex = handIndex;
        frame = ReplayPlayback.at(match, handIndex, cursor);
    }
    private Component eventName() {
        if (frame.settled()) return Component.translatable("result.mchjong." + match.hands().get(handIndex).result());
        if (frame.event() == null) return Component.translatable("replay.mchjong.initial");
        var event = frame.event();
        String key = switch (event.kind()) {
            case DRAW -> "replay.mchjong.draw";
            case DISCARD -> event.riichi() ? "action.mchjong.riichi" : "action.mchjong.discard";
            case NUKI -> "action.mchjong.nuki";
            case MELD -> "action.mchjong." + event.meld().type().name().toLowerCase(Locale.ROOT);
            case RIICHI -> "replay.mchjong.deposit";
            case DORA -> "ui.mchjong.result_indicators";
        };
        return event.seat() < 0 ? Component.translatable(key) : Component.literal(match.participants().get(event.seat()).name())
            .append(" · ").append(Component.translatable(key));
    }
    @Override protected void renderWidget(GuiGraphics graphics, int x, int y, float partialTick) {
        if (frame == null) return;
        MahjongUi.panel(graphics, getX(), getY(), width, height);
        contentHeight = contents(null, 0);
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - height + 8));
        graphics.enableScissor(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3);
        contents(graphics, getY() + 6 - scroll);
        graphics.disableScissor();
        if (contentHeight > height) {
            int thumb = Math.max(10, height * height / contentHeight);
            int top = getY() + (height - thumb) * scroll / Math.max(1, contentHeight - height + 8);
            graphics.fill(getX() + width - 4, top, getX() + width - 2, top + thumb, MahjongUi.ACCENT);
        }
    }
    private int contents(GuiGraphics graphics, int origin) {
        ReplayHand hand = match.hands().get(handIndex);
        int y = paragraph(graphics, eventName(), origin, MahjongUi.ACCENT);
        y = tiles(graphics, frame.dora(), y + 3, 14, -1);
        if (frame.settled() && !hand.ura().isEmpty()) {
            y = paragraph(graphics, Component.translatable("ui.mchjong.ura_indicators"), y, MahjongUi.MUTED);
            y = tiles(graphics, hand.ura(), y + 3, 14, -1);
        }
        int tileWidth = Math.clamp((width - 32) / 14 - 2, 8, 21);
        for (int seat = 0; seat < frame.seats().size(); seat++) {
            var player = frame.seats().get(seat);
            y += 10;
            var heading = Component.literal(player.name()).append(" · ").append(Component.translatable("ui.mchjong.points", player.points()));
            if (player.riichi()) heading.append(" · ").append(Component.translatable("action.mchjong.riichi"));
            if (frame.settled()) {
                heading.append("  " + String.format(Locale.ROOT, "%+d", hand.deltas().get(seat)));
                if (!hand.finalRanks().isEmpty()) heading.append(" · ").append(Component.translatable("ui.mchjong.rank", hand.finalRanks().get(seat)));
            }
            y = paragraph(graphics, heading, y, MahjongUi.TEXT);
            var concealed = new ArrayList<>(player.hand());
            int winning = Tile.ABSENT;
            if (frame.settled()) for (var win : hand.wins()) if (win.seat() == seat && win.tile() >= 0) {
                concealed.remove(Integer.valueOf(win.tile())); concealed.add(win.tile()); winning = win.tile();
            }
            y = tiles(graphics, concealed, y + 3, tileWidth, winning >= 0 ? winning : player.drawn());
            for (var meld : player.melds()) {
                if (graphics != null) TileGui.meld(graphics, meld, seat, getX() + 24, y + tileWidth / 2 + 4, tileWidth, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI);
                y += tileWidth * 2 + 8;
            }
            if (!player.norths().isEmpty()) y = tiles(graphics, player.norths(), y, tileWidth, -1);
            int riverWidth = Math.max(7, Math.min(16, (width - 28) / 10));
            for (int i = 0; i < player.river().size(); i++) {
                var discard = player.river().get(i);
                int x = getX() + 12 + (i % 6) * (riverWidth * 3 / 2 + 3);
                int ty = y + i / 6 * (riverWidth * 3 / 2 + 4);
                if (graphics != null) {
                    TileGui.tile(graphics, discard.tile(), x, ty, riverWidth, false, discard.riichi(), !discard.tsumogiri(), top.skyeyefast.mchjong.item.TileFacePreset.KANSAI);
                    if (discard.called()) graphics.fill(x, ty, x + riverWidth, ty + riverWidth * 3 / 2, 0x9920373a);
                }
            }
            y += (player.river().size() + 5) / 6 * (riverWidth * 3 / 2 + 4);
            if (frame.settled()) {
                if (hand.result().equals("exhaustive")) y = paragraph(graphics,
                    Component.translatable(player.exposed() ? "ui.mchjong.tenpai" : "ui.mchjong.noten"), y + 4, MahjongUi.MUTED);
                for (var win : hand.wins()) if (win.seat() == seat) {
                    y = paragraph(graphics, win.score().yakuman() > 0
                        ? Component.translatable("ui.mchjong.yakuman", win.score().yakuman())
                        : Component.translatable("ui.mchjong.han_fu", win.score().han(), win.score().fu()), y + 4, MahjongUi.ACCENT);
                    for (String yaku : win.score().yaku()) y = paragraph(graphics,
                        Component.translatable("yaku.mchjong." + yaku.toLowerCase(Locale.ROOT)), y, MahjongUi.MUTED);
                    if (win.score().dora() > 0) y = paragraph(graphics, Component.translatable("ui.mchjong.dora", win.score().dora()), y, MahjongUi.ACCENT);
                }
                if (!hand.finalScores().isEmpty()) y = paragraph(graphics, Component.translatable("ui.mchjong.final_score",
                    String.format(Locale.ROOT, "%+.1f", hand.finalScores().get(seat))), y + 3, MahjongUi.ACCENT);
            }
        }
        return y - origin + 6;
    }
    private int tiles(GuiGraphics graphics, List<Integer> tiles, int y, int size, int marked) {
        int columns = Math.max(1, (width - 24) / (size + 2));
        for (int i = 0; i < tiles.size(); i++) if (graphics != null)
            TileGui.tile(graphics, tiles.get(i), getX() + 12 + i % columns * (size + 2),
                y + i / columns * (size * 3 / 2 + 4), size, false, false, tiles.get(i) == marked, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI);
        return y + (tiles.size() + columns - 1) / columns * (size * 3 / 2 + 4) + 3;
    }
    private int paragraph(GuiGraphics graphics, Component text, int y, int color) {
        for (var line : font.split(text, Math.max(20, width - 28))) {
            if (graphics != null) graphics.drawString(font, line, getX() + 12, y, color, false);
            y += 12;
        }
        return y;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x, y)) return false;
        scroll = Math.clamp(scroll - (int) Math.round(vertical * 32), 0, Math.max(0, contentHeight - height + 8));
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!isFocused()) return false;
        int amount = switch (key) { case GLFW.GLFW_KEY_UP -> -24; case GLFW.GLFW_KEY_DOWN -> 24;
            case GLFW.GLFW_KEY_PAGE_UP -> -height; case GLFW.GLFW_KEY_PAGE_DOWN -> height; default -> 0; };
        if (amount == 0) return super.keyPressed(key, scan, modifiers);
        scroll = Math.clamp(scroll + amount, 0, Math.max(0, contentHeight - height + 8));
        return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        if (frame != null) output.add(NarratedElementType.TITLE, eventName());
        output.add(NarratedElementType.USAGE, Component.translatable("replay.mchjong.controls"));
    }
}
