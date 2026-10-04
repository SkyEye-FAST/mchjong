package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.PlayerPresence;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.RoomSeating;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** One preparation layout for every rule: variant rail, roster, settings and stage action. */
final class RoomLobby {
    private final Screen parent;
    private final BlockPos pos;
    private final Runnable rebuild;
    private int tab;
    private int page;
    private long revision = -1;
    private boolean pending;
    private Layout layout;

    record Option(Component label, Component value, Boolean checked, boolean enabled, Runnable action) {
        Component message() {
            return value.getString().equals("›") ? label : Component.translatable("settings.mchjong.toggle", label,
                checked == null ? value : Component.translatable(checked ? "options.on" : "options.off"));
        }
        MahjongButton button(int x, int y, int width) {
            var button = RoomLobbyControls.button(message(), x, y, width, action).option(label, value);
            if (checked != null) button.checked(checked);
            button.active = enabled;
            return button;
        }
    }

    private record Layout(int left, int top, int span, int height, int rail) {
        static Layout of(int width, int height) {
            int span = Math.min(420, width - 48), panelHeight = Math.min(238, height - 48);
            return new Layout((width - span) / 2, (height - panelHeight) / 2, span, panelHeight,
                Math.min(88, Math.max(64, span / 5)));
        }
        int bodyLeft() { return left + rail + 12; }
        int bodyWidth() { return span - rail - 12; }
        int contentTop() { return top + 58; }
        int footer() { return top + height - 26; }
        int status() { return footer() - 14; }
    }

    RoomLobby(Screen parent, BlockPos pos, Runnable rebuild) {
        this.parent = parent;
        this.pos = pos;
        this.rebuild = rebuild;
    }

    void receivedView() { pending = false; }

    private MahjongTableBlockEntity table() {
        var client = Minecraft.getInstance();
        return client.level != null && client.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table ? table : null;
    }

    List<MahjongButton> build(TableRoomView room, int width, int height) {
        layout = Layout.of(width, height);
        if (revision != room.revision()) { revision = room.revision(); pending = false; }
        var buttons = new ArrayList<MahjongButton>();
        var client = Minecraft.getInstance();
        if (room.exitVote() != null) return TableExitControls.voteButtons(pos, room, width, height);
        int body = layout.bodyLeft(), span = layout.bodyWidth(), top = layout.top();
        int toolbarWidth = (span - 8) / 3;
        buttons.add(RoomLobbyControls.button(Component.translatable("settings.mchjong.scopes"), body, top + 6, toolbarWidth,
            () -> client.setScreen(new TableOptionsScreen(parent, pos))).shortCaption(Component.translatable("settings.mchjong.scopes.short")));
        int leave = RoomLobbyControls.find(room, RoomAction.Type.LEAVE_ROOM, List.of());
        var leaveButton = RoomLobbyControls.button(Component.translatable("action.mchjong.leave_room"), body + toolbarWidth + 4,
            top + 6, toolbarWidth, () -> send(room, leave)).shortCaption(Component.translatable("action.mchjong.leave_room.short"));
        leaveButton.active = leave >= 0 && !pending;
        buttons.add(leaveButton);
        var close = RoomLobbyControls.button(Component.translatable("room.mchjong.dissolve"), body + 2 * (toolbarWidth + 4), top + 6,
            span - 2 * (toolbarWidth + 4), () -> TableExitControls.send(pos, room,
                TableSessionControlPayload.Operation.REQUEST_EXIT, room.decision(), false)).shortCaption(Component.translatable("room.mchjong.dissolve.short"));
        close.active = host(room) && !pending;
        buttons.add(close);
        int variantRow = 0;
        for (var variant : MahjongVariant.values()) {
            var button = RoomLobbyControls.variantButton(pos, room, variant, layout.left(), top + 6 + variantRow++ * 24,
                layout.rail(), !room.manual(), () -> { pending = true; rebuild.run(); }).navigation();
            button.active &= !pending;
            buttons.add(button);
        }
        var replays = RoomLobbyControls.button(Component.translatable("replay.mchjong.title"), layout.left(), layout.footer(), layout.rail(),
            () -> ClientReplays.list(0, "", false)).navigation();
        replays.active = table() != null && table().clientWorldPolicy().replaysEnabled();
        if (room.variant() != MahjongVariant.TAIWAN) buttons.add(replays);
        var invite = RoomLobbyControls.button(Component.translatable("ui.mchjong.invite"), layout.left(), layout.footer() - 24, layout.rail(),
            () -> client.setScreen(new TableInviteScreen(parent, pos))).navigation().shortCaption(Component.translatable("ui.mchjong.invite.short"));
        invite.active = room.viewerSeat() >= 0 && room.lobby() && table().clientWorldPolicy().invitationsEnabled();
        buttons.add(invite);
        String[] tabs = {"room.mchjong.participants", "lobby.mchjong.match_settings"};
        for (int index = 0; index < tabs.length; index++) {
            int selected = index;
            buttons.add(RoomLobbyControls.button(Component.translatable(tabs[index]), body + index * (span + 4) / 2, top + 34,
                (span - 4) / 2, () -> { tab = selected; page = 0; rebuild.run(); }).navigation().selected(tab == index).shortCaption(Component.translatable(tabs[index] + ".short")));
        }
        if (tab == 0) buildRoster(buttons, room);
        else {
            var options = options(parent, pos, room);
            int rows = Math.max(1, (layout.status() - layout.contentTop() - 24) / 22);
            int pages = Math.max(1, (options.size() + rows - 1) / rows);
            page = Math.clamp(page, 0, pages - 1);
            for (int row = 0; row < rows && page * rows + row < options.size(); row++) {
                var button = options.get(page * rows + row).button(body, layout.contentTop() + row * 22, span);
                button.active &= !pending;
                buttons.add(button);
            }
            if (pages > 1) {
                var previous = RoomLobbyControls.button(Component.literal("‹"), body, layout.status() - 24, 24,
                    () -> { page--; rebuild.run(); });
                previous.active = page > 0; buttons.add(previous);
                var next = RoomLobbyControls.button(Component.literal("›"), body + span - 24, layout.status() - 24, 24,
                    () -> { page++; rebuild.run(); });
                next.active = page + 1 < pages; buttons.add(next);
            }
        }
        buildStage(buttons, room);
        return buttons;
    }

    private void buildRoster(List<MahjongButton> buttons, TableRoomView room) {
        int pitch = layout.height() < 200 ? 26 : 34;
        for (int seat = 0; seat < room.seats().size(); seat++) {
            int y = layout.contentTop() + seat * pitch;
            var participant = room.seats().get(seat).participant();
            int action = nextBot(room, seat);
            if (room.variant() != MahjongVariant.TAIWAN && (participant.id() == null || participant.bot() || room.seats().get(seat).presence() == PlayerPresence.DISCONNECTED)) {
                int target = action;
                var label = participant.bot() ? botName(room, seat).copy().append(" ›") : Component.translatable("room.mchjong.add_bot");
                var button = RoomLobbyControls.button(label, layout.bodyLeft() + layout.bodyWidth() - 78, y, 78, () -> send(room, target));
                button.active = action >= 0 && !pending;
                buttons.add(button);
            } else if (seat != room.host()) {
                int transfer = RoomLobbyControls.find(room, RoomAction.Type.TRANSFER_HOST, List.of(seat));
                var button = RoomLobbyControls.button(Component.translatable("room.mchjong.transfer"), layout.bodyLeft() + layout.bodyWidth() - 78,
                    y, 78, () -> send(room, transfer));
                button.setTooltip(Tooltip.create(Component.translatable("room.mchjong.transfer_host", participant.name())));
                button.active = transfer >= 0 && !pending;
                buttons.add(button);
            }
        }
    }

    private void buildStage(List<MahjongButton> buttons, TableRoomView room) {
        int x = layout.bodyLeft(), y = layout.footer(), span = layout.bodyWidth();
        if (room.seating() == RoomSeating.Stage.DRAWING) {
            int cell = (span - (room.seats().size() - 1) * 4) / room.seats().size();
            for (int slot = 0; slot < room.seats().size(); slot++) {
                int action = RoomLobbyControls.find(room, RoomAction.Type.DRAW_WIND, List.of(slot));
                var button = RoomLobbyControls.button(Component.translatable("room.mchjong.wind_tile", slot + 1), x + slot * (cell + 4), y, cell,
                    () -> send(room, action)).primary();
                button.active = action >= 0 && !pending; buttons.add(button);
            }
            return;
        }
        int action = RoomLobbyControls.find(room, room.seating() == RoomSeating.Stage.GATHERING ? RoomAction.Type.BEGIN_SEATING : RoomAction.Type.READY, List.of());
        String key;
        if (room.seating() == RoomSeating.Stage.GATHERING) {
            int fill = RoomLobbyControls.find(room, RoomAction.Type.FILL_BOTS, List.of());
            if (fill >= 0) {
                var button = RoomLobbyControls.button(Component.translatable("action.mchjong.fill_bots"), x, y, (span - 4) / 2, () -> send(room, fill));
                button.active = !pending; buttons.add(button);
                x += (span + 4) / 2; span = (span - 4) / 2;
            }
            key = action >= 0 ? room.manual() ? "room.mchjong.start_manual" : "room.mchjong.start_auto"
                : !room.equipped() && host(room) ? room.manual() ? "ui.mchjong.manual_equipment_needed" : "ui.mchjong.equipment_needed"
                : host(room) ? "lobby.mchjong.wait_players" : "room.mchjong.wait_host";
        } else {
            boolean ready = room.viewerSeat() >= 0 && room.seats().get(room.viewerSeat()).participant().ready();
            key = action >= 0 ? ready ? "action.mchjong.unready" : "action.mchjong.ready"
                : !room.equipped() ? room.manual() ? "ui.mchjong.manual_equipment_needed" : "ui.mchjong.equipment_needed" : "room.mchjong.take_seats";
        }
        int index = action;
        var primary = RoomLobbyControls.button(Component.translatable(key), x, y, span, () -> send(room, index)).primary();
        primary.active = action >= 0 && !pending;
        buttons.add(primary);
    }

    private void send(TableRoomView room, int action) {
        if (pending || action < 0) return;
        pending = true;
        RoomLobbyControls.send(pos, room, action);
        rebuild.run();
    }

    private int nextBot(TableRoomView room, int seat) {
        var participant = room.seats().get(seat).participant();
        int current = participant.bot() ? participant.difficulty().ordinal() : -1;
        var bots = table().clientRiichiSettings() == null ? List.<top.skyeyefast.mchjong.engine.ExternalBot>of() : table().clientRiichiSettings().externalBots();
        if (room.variant() == MahjongVariant.RIICHI && participant.externalBotId() != null) {
            for (int index = 0; index < bots.size(); index++) if (bots.get(index).id().equals(participant.externalBotId())) current = index + 2;
        }
        for (var action : room.actions()) if (action.type() == RoomAction.Type.SET_BOT && action.arguments().getFirst() == seat
            && action.arguments().get(1) > current) return room.actions().indexOf(action);
        return RoomLobbyControls.find(room, RoomAction.Type.REMOVE_BOT, List.of(seat));
    }

    private Component botName(TableRoomView room, int seat) {
        var settings = table().clientRiichiSettings();
        return TableParticipantsScreen.botName(room, settings == null ? List.of() : settings.externalBots(), seat);
    }

    static boolean host(TableRoomView room) { return room.viewerSeat() >= 0 && room.viewerSeat() == room.host() && room.exitVote() == null; }

    static List<Option> options(Screen parent, BlockPos pos, TableRoomView room) {
        var client = Minecraft.getInstance();
        var options = new ArrayList<Option>();
        if (client.level == null || !(client.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table)) return options;
        var root = TableChildScreen.root(parent);
        if (root instanceof RiichiTableScreen riichi) {
            var settings = riichi.roomSettings();
            for (int count : new int[]{4, 3}) {
                var preset = settings.rules().preset().tenhou() ? count == 4 ? top.skyeyefast.mchjong.engine.RiichiPreset.TENHOU_4 : top.skyeyefast.mchjong.engine.RiichiPreset.TENHOU_3
                    : count == 4 ? top.skyeyefast.mchjong.engine.RiichiPreset.MAHJONG_SOUL_4 : top.skyeyefast.mchjong.engine.RiichiPreset.MAHJONG_SOUL_3;
                var rules = settings.rules().withPreset(preset);
                options.add(new Option(Component.translatable("ui.mchjong.players." + count), Component.empty(), settings.rules().players() == count,
                    host(room) && room.lobby() && room.seating() == RoomSeating.Stage.GATHERING && count != settings.rules().players() && riichi.canSupplyReds(rules.sanma(), rules.redFives())
                        && (count == 4 || room.seats().size() < 4 || room.seats().get(3).participant().id() == null), () -> riichi.configureRules(room, rules)));
            }
            options.add(new Option(Component.translatable("rules.mchjong.preset", Component.translatable(settings.rules().preset().presetKey())), Component.literal("›"), null, true,
                () -> client.setScreen(new RiichiRulesScreen(parent, room, settings, true))));
            options.add(new Option(Component.translatable("rules.mchjong.title"), Component.literal("›"), null, true,
                () -> client.setScreen(new RiichiRulesScreen(parent, room, settings))));
            var visibility = settings.playerHandVisibility();
            options.add(new Option(Component.translatable("settings.mchjong.hand_visibility.label"), Component.translatable("settings.mchjong.hand_visibility." + visibility.name().toLowerCase(java.util.Locale.ROOT)), null,
                host(room) && room.lobby(), () -> {
                    var values = top.skyeyefast.mchjong.engine.PlayerHandVisibility.values();
                    riichi.configureVisibility(values[Math.floorMod(visibility.ordinal() + (Screen.hasShiftDown() ? -1 : 1), values.length)]);
                }));
            options.add(new Option(Component.translatable("settings.mchjong.open_hands"), Component.empty(), settings.openHands(), host(room) && room.lobby(),
                () -> riichi.control(room, top.skyeyefast.mchjong.network.RiichiControlPayload.Operation.OPEN_HANDS, room.decision(), !settings.openHands())));
        } else if (root instanceof SichuanLobbyScreen sichuan) {
            var settings = sichuan.roomSettings();
            options.add(new Option(Component.translatable("sichuan.mchjong.rules.title").append(": ").append(Component.translatable(settings.presetKey())), Component.literal("›"), null, true,
                () -> client.setScreen(new SichuanRulesScreen(parent, room, settings))));
        }
        if (root instanceof TaiwanLobbyScreen) {
            var settings = table.clientTaiwanSettings();
            options.add(new Option(Component.translatable("taiwan.mchjong.rules.title").append(": ").append(Component.translatable(settings.presetKey())), Component.literal("›"), null, true,
                () -> client.setScreen(new TaiwanRulesScreen(parent, pos))));
        }
        options.add(new Option(Component.translatable("room.mchjong.participants"), Component.literal("›"), null, true,
            () -> client.setScreen(new TableParticipantsScreen(parent, pos))));
        var time = room.variant() == MahjongVariant.RIICHI ? table.clientRiichiSettings().timeControl()
            : room.variant() == MahjongVariant.MCR ? table.clientMcrTimeControl()
            : room.variant() == MahjongVariant.TAIWAN ? table.clientTaiwanSettings().timeControl() : table.clientSichuanSettings().timeControl();
        options.add(new Option(Component.translatable("ui.mchjong.clock_settings"), Component.translatable("lobby.mchjong.clock_summary", time.moveSeconds(), time.reserveSeconds()), null, host(room) && room.lobby(),
            () -> client.setScreen(new TableClockScreen(parent, time))));
        if (room.variant() != MahjongVariant.TAIWAN) options.add(new Option(Component.translatable("settings.mchjong.convenience_hints"), Component.empty(), room.convenienceHints(), host(room) && room.lobby() && room.allowConvenienceHints(),
            () -> TableExitControls.send(pos, room, TableSessionControlPayload.Operation.CONVENIENCE_HINTS, room.decision(), !room.convenienceHints())));
        options.add(new Option(Component.translatable("ui.mchjong.invite"), Component.literal("›"), null, room.viewerSeat() >= 0 && room.lobby() && table.clientWorldPolicy().invitationsEnabled(),
            () -> client.setScreen(new TableInviteScreen(parent, pos))));
        return options;
    }

    void paint(GuiGraphics graphics, TableRoomView room, int width, int height, int mouseX, int mouseY) {
        if (layout == null) return;
        var font = Minecraft.getInstance().font;
        var l = layout;
        MahjongUi.panel(graphics, l.left() - 6, l.top(), l.span() + 12, l.height());
        if (tab == 0) {
            int pitch = l.height() < 200 ? 26 : 34;
            for (int seat = 0; seat < room.seats().size(); seat++) {
                var state = room.seats().get(seat); var player = state.participant();
                int y = l.contentTop() + seat * pitch;
                boolean control = player.id() == null || player.bot() || state.presence() == PlayerPresence.DISCONNECTED || seat != room.host();
                int textWidth = l.bodyWidth() - (control ? 88 : 8);
                graphics.fill(l.bodyLeft(), y, l.bodyLeft() + l.bodyWidth(), y + pitch - 2, MahjongUi.INPUT);
                int portrait = PlayerPortrait.draw(graphics, player, l.bodyLeft() + 4, y + 2, 10);
                var name = player.id() == null ? Component.translatable("room.mchjong.empty") : Component.literal(player.name());
                int color = seat == room.viewerSeat() ? MahjongUi.ACCENT : state.presence() == PlayerPresence.DISCONNECTED ? MahjongUi.NEGATIVE : MahjongUi.TEXT;
                MahjongUi.text(graphics, font, name, l.bodyLeft() + 4 + portrait, y + 2, textWidth - portrait, color, false);
                var status = (player.id() == null ? Component.empty() : player.ready() ? Component.translatable("ui.mchjong.ready")
                    : player.bot() ? botName(room, seat) : TableParticipantsScreen.presence(state.presence())).copy();
                if (state.wind() >= 0) status = Component.translatable("room.mchjong.member", TableParticipantsScreen.wind(state.wind()), status);
                if (seat == room.host()) status = Component.translatable("ui.mchjong.annotation", status, Component.translatable("room.mchjong.host.short"));
                MahjongUi.text(graphics, font, status, l.bodyLeft() + 4, y + 15, textWidth, MahjongUi.MUTED, false);
                var hover = name.copy().append("\n").append(status);
                if (state.wind() >= 0) {
                    var position = top.skyeyefast.mchjong.world.TableGeometry.stool(pos, state.wind());
                    hover.append("\n").append(Component.translatable("room.mchjong.position", TableParticipantsScreen.wind(state.wind()), position.getX(), position.getY(), position.getZ()));
                }
                var service = table() == null ? null : table().clientBotService();
                String error = service == null || seat >= service.seatErrors().size() ? null : service.seatErrors().get(seat);
                if (error != null) {
                    MahjongUi.text(graphics, font, Component.literal("!"), l.bodyLeft() + l.bodyWidth() - 88, y + 3, 8, MahjongUi.NEGATIVE, false);
                    hover.append("\n").append(Component.translatable("bot.mchjong.service." + error));
                }
                if (mouseX >= l.bodyLeft() && mouseX < l.bodyLeft() + l.bodyWidth() - 82 && mouseY >= y && mouseY < y + pitch - 2)
                    parent.setTooltipForNextRenderPass(hover);
            }
        } else {
            var options = options(parent, pos, room);
            int rows = Math.max(1, (l.status() - l.contentTop() - 24) / 22), pages = Math.max(1, (options.size() + rows - 1) / rows);
            if (pages > 1) graphics.drawCenteredString(font, (page + 1) + " / " + pages, l.bodyLeft() + l.bodyWidth() / 2, l.status() - 18, MahjongUi.MUTED);
        }
        if (pending) MahjongUi.text(graphics, font, Component.translatable("rules.mchjong.pending"),
            l.bodyLeft(), l.status(), l.bodyWidth(), MahjongUi.ACCENT, false);
        TableExitControls.renderVote(graphics, font, room, width, height);
    }
}
