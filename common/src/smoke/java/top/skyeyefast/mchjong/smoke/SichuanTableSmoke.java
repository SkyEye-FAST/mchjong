package top.skyeyefast.mchjong.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import top.skyeyefast.mchjong.client.MahjongButton;
import top.skyeyefast.mchjong.client.McrLobbyScreen;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.client.SichuanLobbyScreen;
import top.skyeyefast.mchjong.client.SichuanTableScreen;
import top.skyeyefast.mchjong.client.SichuanResultsScreen;
import top.skyeyefast.mchjong.client.ReplayScreen;
import top.skyeyefast.mchjong.client.ReplayBrowserScreen;
import top.skyeyefast.mchjong.client.ClientReplays;
import top.skyeyefast.mchjong.engine.SichuanAction;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.RoomSeating;
import top.skyeyefast.mchjong.engine.SichuanGame;
import top.skyeyefast.mchjong.engine.SichuanSession;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.SichuanActionPayload;
import top.skyeyefast.mchjong.network.SichuanNextHandPayload;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableRoomActionPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Real packets, recipient-safe scenes, exit voting and the eight-hand client lifecycle. */
final class SichuanTableSmoke {
    private static final List<MahjongVariant> CHOICES = List.of(MahjongVariant.SICHUAN, MahjongVariant.MCR,
        MahjongVariant.RIICHI, MahjongVariant.SICHUAN);
    private final List<ServerPlayer> guests = new ArrayList<>();
    private CompletableFuture<?> task;
    private int stage, choice, ticks, settled, captureTicks;
    private UUID incarnation;
    private UUID replayId;
    private boolean picked;
    private boolean hintsConfigured;
    private final ConvenienceHintsSmoke scoredHints = new ConvenienceHintsSmoke();
    private final WallSeatedSmoke seatedWall = new WallSeatedSmoke();
    private final MatchAutomationControlsSmoke automation = new MatchAutomationControlsSmoke();
    private int selectedTile;
    private int boundFirstDiscard = Tile.ABSENT;
    private long discardDecision;
    private top.skyeyefast.mchjong.network.SichuanViewPayload originalSettings;

    boolean tick(Minecraft client, BlockPos pos, Path output) {
        require(++ticks < 1400, "Sichuan smoke timed out at " + stage);
        if (task != null && !task.isDone()) return false;
        if (task != null) { task.join(); task = null; }
        var table = (MahjongTableBlockEntity) client.level.getBlockEntity(pos);
        var room = table.clientTableRoom();
        var server = client.getSingleplayerServer();
        UUID mainId = client.player.getUUID();
        switch (stage) {
            case 0 -> {
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    target.sit(main, 0); target.open(main);
                });
                stage++;
            }
            case 1 -> {
                if (room == null || room.viewerSeat() < 0 || client.screen == null) break;
                var buttons = client.screen.children().stream().filter(MahjongButton.class::isInstance)
                    .map(MahjongButton.class::cast).toList();
                for (var variant : MahjongVariant.values()) {
                    var label = Component.translatable("variant.mchjong." + variant.name().toLowerCase(java.util.Locale.ROOT)).getString();
                    var button = buttons.stream().filter(candidate -> candidate.getMessage().getString().equals(label)).findFirst().orElseThrow();
                    require(button.getX() >= 0 && button.getX() + button.getWidth() <= client.screen.width, "Variant button outside screen");
                    if (variant == CHOICES.get(choice)) button.onPress();
                }
                stage++;
            }
            case 2 -> {
                if (room == null || room.variant() != CHOICES.get(choice)) break;
                require(switch (room.variant()) {
                    case RIICHI -> client.screen instanceof RiichiTableScreen;
                    case MCR -> client.screen instanceof McrLobbyScreen;
                    case SICHUAN -> client.screen instanceof SichuanLobbyScreen;
                }, "Variant did not open its own screen");
                client.getWindow().setWindowed(960, 720);
                client.options.guiScale().set(3); client.resizeDisplay();
                captureTicks = 0; stage = 60;
            }
            case 60 -> {
                if (++captureTicks < 12) break;
                require(client.screen.width == 320 && client.screen.height == 240, "Lobby viewport was not 320x240");
                AutomationControlsSmoke.checkBounds(client);
                SmokeScreenshots.grab(output.toFile(), "lobby-" + room.variant().name().toLowerCase(java.util.Locale.ROOT) + "-320x240.png", client.getMainRenderTarget(), ignored -> {});
                var lobbyScreen = client.screen;
                LobbySmoke.find(client, Component.translatable("settings.mchjong.scopes").getString()).onPress();
                var optionsScreen = client.screen;
                LobbySmoke.find(client, Component.translatable("ui.mchjong.clock_settings").getString()).onPress();
                require(client.screen instanceof top.skyeyefast.mchjong.client.TableClockScreen, "Variant clock settings did not open");
                AutomationControlsSmoke.checkBounds(client);
                client.screen.onClose();
                require(client.screen == optionsScreen, "Clock did not return to its settings scope");
                LobbySmoke.find(client, Component.translatable("settings.mchjong.scope.world").getString()).onPress();
                String policy = Component.translatable("settings.mchjong.toggle", Component.translatable("settings.mchjong.invitations_enabled"),
                    Component.translatable(table.clientWorldPolicy().invitationsEnabled() ? "options.on" : "options.off")).getString();
                require(LobbySmoke.find(client, policy) != null, "Variant world scope did not display synchronized policy");
                client.screen.onClose();
                require(client.screen == lobbyScreen, "Settings did not return to the variant lobby");
                client.options.guiScale().set(2); client.getWindow().setWindowed(1280, 800); client.resizeDisplay();
                if (++choice < CHOICES.size()) { stage = 1; break; }
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    require(target.participantRoom(main) instanceof SichuanSession, "Wrong runtime for Sichuan");
                    require(target.equipment().sichuanStock().tiles().equals(Tile.sichuanSet()), "Wrong physical Sichuan stock");
                    for (int seat = 1; seat < 4; seat++) {
                        var guest = new Guest(main, seat); guests.add(guest);
                        guest.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                        main.serverLevel().addNewPlayer(guest); server.getPlayerList().getPlayers().add(guest);
                        target.sit(guest, seat);
                    }
                    target.open(main);
                });
                stage = 3;
            }
            case 3 -> {
                if (room.seats().stream().anyMatch(seat -> seat.participant().id() == null)) break;
                require(table.clientSichuanView() == null && table.clientSichuanSettings() != null
                    && table.clientSichuanSettings().preset() == top.skyeyefast.mchjong.engine.SichuanPreset.SBR_2025,
                    "Lobby did not project authoritative Sichuan settings");
                originalSettings = new top.skyeyefast.mchjong.network.SichuanViewPayload(pos, "", room, table.clientSichuanDeck(),
                    table.clientSichuanCloth(), false, false, false, table.clientSichuanSettings(), table.clientWorldPolicy());
                LobbySmoke.settings(client);
                var label = Component.translatable("sichuan.mchjong.rules.title").getString();
                client.screen.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
                    .filter(button -> button.getMessage().getString().startsWith(label)).findFirst().orElseThrow().onPress();
                stage = 22;
            }
            case 22 -> {
                if (!(client.screen instanceof top.skyeyefast.mchjong.client.SichuanRulesScreen)) break;
                verifyRuleLayout(client);
                var label = Component.translatable("sichuan.mchjong.rules.fan_cap").getString();
                var field = client.screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).filter(box -> box.getMessage().getString().equals(label))
                    .findFirst().orElseThrow();
                field.setValue("9");
                var applyLabel = Component.translatable("rules.mchjong.apply").getString();
                require(client.screen.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
                    .filter(button -> button.getMessage().getString().equals(applyLabel)).noneMatch(button -> button.active),
                    "Out-of-range rule draft enabled Apply");
                field.setValue("4");
                require(table.clientSichuanSettings().rules().fanCap() == 3, "Local draft mutated authoritative settings");
                press(client, "rules.mchjong.apply");
                stage = 23;
            }
            case 23 -> {
                if (table.clientSichuanSettings().rules().fanCap() != 4) break;
                require(table.clientSichuanSettings().custom() && client.screen instanceof top.skyeyefast.mchjong.client.SichuanRulesScreen,
                    "Rules acknowledgement lost the editor or Custom state");
                top.skyeyefast.mchjong.client.ClientSichuanNetworking.receive(originalSettings);
                require(table.clientSichuanSettings().rules().fanCap() == 4, "Old snapshot rolled back confirmed rules");
                SmokeScreenshots.grab(output.toFile(), "sichuan-rules.png", client.getMainRenderTarget(), ignored -> {});
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var session = (SichuanSession) target.participantRoom(main);
                    var before = session.save();
                    var standard = top.skyeyefast.mchjong.engine.SichuanPreset.SBR_2025.config();
                    TableNetworking.receive(guests.get(0), new top.skyeyefast.mchjong.network.SichuanRulesPayload(pos,
                        session.tableId(), session.incarnation(), session.decision(), standard));
                    TableNetworking.receive(main, new top.skyeyefast.mchjong.network.SichuanRulesPayload(pos,
                        session.tableId(), session.incarnation(), originalSettings.room().decision(), standard));
                    TableNetworking.receive(main, new top.skyeyefast.mchjong.network.SichuanRulesPayload(pos,
                        session.tableId(), UUID.randomUUID(), session.decision(), standard));
                    require(before.equals(session.save()), "Unauthorized or stale rules payload changed the room");
                });
                stage = 24;
            }
            case 24 -> {
                selectPreset(client, "sichuan.mchjong.rules.preset.sbr_2025");
                press(client, "rules.mchjong.apply");
                stage = 25;
            }
            case 25 -> {
                if (table.clientSichuanSettings().preset() != top.skyeyefast.mchjong.engine.SichuanPreset.SBR_2025) break;
                selectPreset(client, "sichuan.mchjong.rules.preset.tfmj_2024");
                press(client, "rules.mchjong.apply");
                stage = 26;
            }
            case 26 -> {
                if (table.clientSichuanSettings().preset() != top.skyeyefast.mchjong.engine.SichuanPreset.TFMJ_2024) break;
                require(table.clientSichuanSettings().rules().transferKongOnShoot()
                    && !table.clientSichuanSettings().rules().selectFirstDiscard(), "TFMJ preset did not select its behavior");
                selectPreset(client, "sichuan.mchjong.rules.preset.sbr_2025");
                press(client, "rules.mchjong.apply");
                stage = 27;
            }
            case 27 -> {
                if (table.clientSichuanSettings().preset() != top.skyeyefast.mchjong.engine.SichuanPreset.SBR_2025) break;
                if (client.screen instanceof top.skyeyefast.mchjong.client.SichuanRulesScreen) client.screen.onClose();
                require(client.screen instanceof SichuanLobbyScreen, "Rule editor did not return to its lobby");
                if (!hintsConfigured) {
                    client.screen.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
                        .filter(button -> button.getMessage().getString().startsWith(Component.translatable(
                            "settings.mchjong.convenience_hints").getString())).findFirst().orElseThrow().onPress();
                    hintsConfigured = true;
                    break;
                }
                if (!room.convenienceHints()) break;
                int index = room.actions().indexOf(new RoomAction(RoomAction.Type.BEGIN_SEATING));
                require(index >= 0, "Sichuan room has no seating action");
                client.getConnection().send(PayloadPackets.serverbound(new TableRoomActionPayload(pos, room.tableId(),
                    room.incarnation(), room.decision(), index)));
                stage = 4;
            }
            case 4 -> {
                if (room.seating() != RoomSeating.Stage.POSITIONING) break;
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var everyone = new ArrayList<>(guests); everyone.add(main);
                    var assigned = target.roomView(main).seats();
                    for (var player : everyone) if (player.getVehicle() instanceof SeatEntity mount) { player.stopRiding(); mount.discard(); }
                    for (var player : everyone) for (int seat = 0; seat < 4; seat++)
                        if (player.getUUID().equals(assigned.get(seat).participant().id())) target.sit(player, seat);
                    for (var guest : guests) {
                        var offered = target.roomView(guest);
                        int index = offered.actions().indexOf(new RoomAction(RoomAction.Type.READY));
                        require(index >= 0, "Sichuan guest has no ready action");
                        TableNetworking.receive(guest, new TableRoomActionPayload(pos, offered.tableId(), offered.incarnation(), offered.decision(), index));
                    }
                    target.open(main);
                });
                stage++;
            }
            case 5 -> {
                if (room.seats().stream().filter(seat -> seat.participant().ready()).count() != 3) break;
                int index = room.actions().indexOf(new RoomAction(RoomAction.Type.READY));
                require(index >= 0, "Sichuan host has no ready action");
                client.getConnection().send(PayloadPackets.serverbound(new TableRoomActionPayload(pos, room.tableId(),
                    room.incarnation(), room.decision(), index)));
                stage++;
            }
            case 6 -> {
                var view = table.clientSichuanView();
                if (view == null) break;
                require(client.screen instanceof top.skyeyefast.mchjong.client.SichuanTableScreen && view.game().phase() == SichuanGame.Phase.VOIDING, "Sichuan did not enter declaration phase");
                require(view.game().rules().equals(table.clientSichuanSettings().rules()), "Table disagrees with confirmed room rules");
                require(view.game().wall().remaining() == 55 && view.game().seats().stream().filter(seat -> seat.hand().stream()
                    .anyMatch(tile -> tile != Tile.HIDDEN)).count() == 1, "Sichuan private deal leaked");
                incarnation = view.incarnation();
                if (++settled < 10) break;
                var action = view.game().actions().get(0);
                require(!action.tiles().isEmpty(), "Sichuan smoke opening needs a bound first discard");
                boundFirstDiscard = action.tiles().get(0);
                var screen = (SichuanTableScreen) client.screen;
                var piece = top.skyeyefast.mchjong.client.SichuanTableScene.build(view.game()).stream()
                    .filter(candidate -> candidate.area() == top.skyeyefast.mchjong.client.SichuanTableScene.Area.HAND
                        && candidate.seat() == view.game().viewerSeat() && candidate.tile() == boundFirstDiscard).findFirst().orElseThrow();
                var pointer = project(client, pos, piece.position());
                require(screen.mouseClicked(pointer.x, pointer.y, 0) && screen.selected(piece), "Secret first-discard picking failed");
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                stage++;
            }
            case 7 -> {
                var view = table.clientSichuanView();
                if (!view.game().actions().isEmpty()) break;
                require(view.game().seats().stream().filter(seat -> seat.voidSuit() >= 0).count() == 1, "Private void choice leaked");
                require(view.game().seats().get(view.game().viewerSeat()).firstDiscard() == boundFirstDiscard, "First discard was not submitted");
                for (int seat = 0; seat < 4; seat++) if (seat != view.game().viewerSeat())
                    require(view.game().seats().get(seat).firstDiscard() == Tile.ABSENT, "Private first-discard choice leaked");
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var saved = target.saveWithoutMetadata();
                    target.load(saved);
                    for (var guest : guests) {
                        var session = (SichuanSession) target.participantRoom(guest);
                        require(session != null, "Restored Sichuan seat lost authorization");
                        var offered = session.view(guest.getUUID());
                        TableNetworking.receive(guest, new SichuanActionPayload(pos, offered.tableId(), offered.incarnation(), offered.game().decision(), 0));
                    }
                    target.open(main);
                });
                stage++;
            }
            case 8 -> {
                var view = table.clientSichuanView();
                if (view == null || view.game().phase() != SichuanGame.Phase.TURN) break;
                if (!automation.tick(client, table, output, "sichuan")) break;
                require(!incarnation.equals(view.incarnation()), "Restored Sichuan incarnation was reused");
                require(view.game().seats().stream().allMatch(seat -> seat.voidSuit() == 0), "Sichuan declarations did not complete");
                require(view.game().seats().get(view.game().viewerSeat()).firstDiscard() == boundFirstDiscard, "NBT restore lost the first-discard binding");
                press(client, "ui.mchjong.exit");
                stage++;
            }
            case 9 -> {
                if (room.exitVote() == null) break;
                require(table.clientSichuanView().paused(), "Exit vote did not pause Sichuan");
                require(client.screen.children().stream().filter(MahjongButton.class::isInstance).count() >= 2, "Exit vote has no controls");
                task = server.submit(() -> {
                    var guest = guests.get(0);
                    var target = (MahjongTableBlockEntity) guest.serverLevel().getBlockEntity(pos);
                    var offered = target.roomView(guest);
                    TableNetworking.receive(guest, new TableSessionControlPayload(pos, offered.tableId(),
                        TableSessionControlPayload.Operation.ANSWER_EXIT, offered.exitVote().id(), false));
                });
                stage++;
            }
            case 10 -> {
                if (room.exitVote() != null || table.clientSichuanView().paused()) break;
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var session = (SichuanSession) target.participantRoom(main);
                    for (int move = 0; move < 40; move++) {
                        if (session.view(mainId).game().actions().stream().anyMatch(action -> action.type() == SichuanAction.Type.DISCARD)) {
                            target.open(main); return;
                        }
                        advanceOne(session);
                    }
                    throw new IllegalStateException("Sichuan client discard turn not reached");
                });
                settled = 0; stage++;
            }
            case 11 -> {
                if (!seatedWall.finished()) { seatedWall.tick(client, table, output, false); break; }
                if (!scoredHints.finished()) { scoredHints.tick(client, table, output, false); break; }
                var view = table.clientSichuanView();
                if (view.game().actions().stream().noneMatch(action -> action.type() == SichuanAction.Type.DISCARD)) break;
                if (++settled < 10) break;
                var screen = (SichuanTableScreen) client.screen;
                if (!picked) {
                    top.skyeyefast.mchjong.client.TableSettings.get().discardMode = top.skyeyefast.mchjong.client.TableSettings.DiscardMode.CONFIRM;
                    selectedTile = view.game().actions().stream().filter(action -> action.type() == SichuanAction.Type.DISCARD)
                        .findFirst().orElseThrow().tiles().get(0);
                    require(selectedTile == boundFirstDiscard, "First discard did not use the secret selection");
                    var piece = top.skyeyefast.mchjong.client.SichuanTableScene.build(view.game()).stream()
                        .filter(candidate -> candidate.area() == top.skyeyefast.mchjong.client.SichuanTableScene.Area.HAND
                            && candidate.seat() == view.game().viewerSeat() && candidate.tile() == selectedTile).findFirst().orElseThrow();
                    var pointer = project(client, pos, piece.position());
                    require(screen.mouseClicked(pointer.x, pointer.y, 0) && screen.selected(piece), "Seated Sichuan picking failed");
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                    require(screen.immersive(), "Sichuan cannot enter immersive view");
                    var player = view.game().seats().get(view.game().viewerSeat());
                    var tiles = new ArrayList<>(player.hand());
                    if (player.drawn() >= 0 && tiles.remove(Integer.valueOf(player.drawn()))) tiles.add(player.drawn());
                    double scale = Math.min(screen.width / 1280.0, screen.height / 800.0);
                    double horizontal = (screen.width - 1280 * scale) / 2
                        + (248 + tiles.indexOf(selectedTile) * 58 + (selectedTile == player.drawn() ? 29 : 0)) * scale;
                    double vertical = (screen.height - 800 * scale) / 2 + 690 * scale;
                    require(screen.mouseClicked(horizontal, vertical, 0), "Immersive Sichuan picking failed");
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                    require(screen.selected(piece), "Immersive Sichuan picking selected a different tile");
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                    picked = true; settled = 0; break;
                }
                var hint = screen.children().stream().filter(child -> child instanceof MahjongButton
                    && child.getClass().getSimpleName().equals("TableHints")).map(child -> (MahjongButton) child).findFirst().orElseThrow();
                require(hint.visible && hint.active, "Sichuan convenience preview missing after restored room and discard selection");
                if (settled == 10) { screen.setFocused(hint); break; }
                require(hint.isFocused(), "Sichuan hint cannot retain native keyboard focus");
                SmokeScreenshots.grab(output.toFile(), "sichuan-hints.png", client.getMainRenderTarget(), ignored -> {});
                SmokeScreenshots.grab(output.toFile(), "sichuan-table.png", client.getMainRenderTarget(), ignored -> {});
                screen.setFocused(null);
                discardDecision = view.game().decision();
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                stage = 17;
            }
            case 17 -> {
                var view = table.clientSichuanView();
                if (view.game().decision() == discardDecision) break;
                require(view.game().seats().get(view.game().viewerSeat()).river().stream().anyMatch(discard -> discard.tile() == selectedTile),
                    "Sichuan selection did not discard the physical tile");
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    finishHand((SichuanSession) target.participantRoom(main));
                    target.open(main);
                });
                stage = 12;
            }
            case 12 -> {
                if (!(client.screen instanceof SichuanResultsScreen results)) break;
                var view = table.clientSichuanView();
                require(view.game().phase() == SichuanGame.Phase.HAND_END && view.game().result() != null, "No hand ledger in results");
                require(view.game().rules().equals(table.clientSichuanSettings().rules()), "Results disagree with confirmed room rules");
                require(results.immersive(), "Results lost the selected table view");
                if (++settled < 20) break;
                SmokeScreenshots.grab(output.toFile(), "sichuan-results.png", client.getMainRenderTarget(), ignored -> {});
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    for (var guest : guests) {
                        var session = (SichuanSession) target.participantRoom(guest);
                        var offered = session.view(guest.getUUID());
                        var payload = new SichuanNextHandPayload(pos, offered.tableId(), offered.incarnation(), offered.game().decision());
                        TableNetworking.receive(guest, payload);
                        int confirmed = session.view(guest.getUUID()).confirmedCount();
                        TableNetworking.receive(guest, payload);
                        require(confirmed == session.view(guest.getUUID()).confirmedCount(), "Duplicate next-hand confirmation counted twice");
                    }
                    target.open(main);
                });
                stage++;
            }
            case 13 -> {
                if (table.clientSichuanView().confirmedCount() != 3) break;
                press(client, "sichuan.mchjong.next_hand");
                stage++;
            }
            case 14 -> {
                var view = table.clientSichuanView();
                if (view.game().handNumber() != 2 || view.game().phase() != SichuanGame.Phase.VOIDING) break;
                require(client.screen instanceof SichuanTableScreen screen && screen.immersive(), "Next hand did not return to table");
                require(view.confirmedCount() == 0 && view.game().result() == null, "Next hand retained settlement state");
                for (int seat = 0; seat < 4; seat++) if (seat != view.game().viewerSeat()) {
                    require(view.game().seats().get(seat).voidSuit() == -1, "Next hand leaked a void choice");
                    require(view.game().seats().get(seat).firstDiscard() == Tile.ABSENT, "Next hand leaked a first-discard choice");
                    require(view.game().seats().get(seat).hand().stream().allMatch(tile -> tile == Tile.HIDDEN), "Next hand leaked an opponent hand");
                }
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var session = (SichuanSession) target.participantRoom(main);
                    while (session.game().phase() != SichuanGame.Phase.MATCH_END) {
                        finishHand(session);
                        if (session.game().phase() == SichuanGame.Phase.MATCH_END) break;
                        var end = session.view(mainId);
                        for (var seat : session.roomView(mainId).seats()) require(session.confirmNextHand(seat.participant().id(),
                            end.tableId(), end.incarnation(), end.game().decision()), "Eight-hand progression rejected confirmation");
                    }
                    require(session.save().replay().handCount() == 8, "Sichuan replay lost completed hands");
                    replayId = session.save().replay().id();
                    try { top.skyeyefast.mchjong.replay.ReplayServer.flush(server, session); }
                    catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                    require(session.pendingReplays().isEmpty(), "Sichuan archive was not acknowledged");
                    target.open(main);
                });
                stage++;
            }
            case 15 -> {
                var view = table.clientSichuanView();
                if (!(client.screen instanceof SichuanResultsScreen) || view.game().phase() != SichuanGame.Phase.MATCH_END) break;
                require(view.game().handNumber() == 8 && !view.canConfirmNextHand(), "Final results allow a ninth hand");
                require(client.screen.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
                    .noneMatch(button -> button.getMessage().getString().equals(Component.translatable("sichuan.mchjong.next_hand").getString())),
                    "Match results contain a next-hand control");
                press(client, "action.mchjong.return_to_lobby");
                stage++;
            }
            case 16 -> {
                if (!room.lobby() || !(client.screen instanceof SichuanLobbyScreen)) break;
                require(table.clientSichuanView() == null, "Lobby retained finished Sichuan decisions");
                ClientReplays.list(0, replayId.toString(), false);
                settled = 0;
                stage = 18;
            }
            case 18 -> {
                if (!(client.screen instanceof ReplayBrowserScreen)) break;
                if (++settled < 12) break;
                press(client, "replay.mchjong.open");
                stage++;
            }
            case 19 -> {
                if (!(client.screen instanceof ReplayScreen replay)) break;
                require(replay.match().id().equals(replayId) && replay.match().complete() && replay.match().handCount() == 8,
                    "Browser did not fetch the completed Sichuan replay");
                require(replay.cursor() == 0 && replay.handIndex() == 0, "Sichuan replay did not open at the initial deal");
                replay.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
                require(replay.cursor() == 1, "Sichuan replay did not step one event");
                replay.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                stage++;
            }
            case 20 -> {
                if (!(client.screen instanceof ReplayScreen replay)) break;
                SmokeScreenshots.grab(output.toFile(), "sichuan-replay.png", client.getMainRenderTarget(), ignored -> {});
                for (int hand = 1; hand < 8; hand++) replay.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0, 0);
                require(replay.handIndex() == 7 && replay.cursor() == 0, "Sichuan replay hand navigation failed");
                replay.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0);
                stage++;
            }
            case 21 -> {
                if (!(client.screen instanceof ReplayScreen replay)) break;
                require(replay.cursor() > 1 && replay.match().header().finalScores().size() == 4,
                    "Sichuan replay did not seek to final standings");
                SmokeScreenshots.grab(output.toFile(), "sichuan-replay-settlement.png", client.getMainRenderTarget(), ignored -> {});
                replay.onClose();
                require(client.screen instanceof ReplayBrowserScreen, "Sichuan replay did not return to the shared browser");
                client.screen.onClose();
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    var session = (SichuanSession) target.participantRoom(main);
                    for (var guest : guests) {
                        if (guest.getVehicle() instanceof SeatEntity mount) { guest.stopRiding(); mount.discard(); }
                        session.unseat(guest.getUUID());
                    }
                    target.open(main);
                });
                stage = 28;
            }
            case 28 -> {
                if (room.seats().stream().filter(seat -> seat.participant().id() != null).count() != 1) break;
                sendRoom(client, pos, room, RoomAction.Type.SET_BOT); stage++;
            }
            case 29 -> {
                if (room.seats().stream().noneMatch(seat -> seat.participant().bot())) break;
                sendRoom(client, pos, room, RoomAction.Type.REMOVE_BOT); stage++;
            }
            case 30 -> {
                if (room.seats().stream().anyMatch(seat -> seat.participant().bot())) break;
                sendRoom(client, pos, room, RoomAction.Type.FILL_BOTS); stage++;
            }
            case 31 -> {
                if (room.seats().stream().filter(seat -> seat.participant().bot() && seat.participant().ready()).count() != 3) break;
                client.getConnection().send(PayloadPackets.serverbound(new top.skyeyefast.mchjong.network.SichuanRulesPayload(pos,
                    room.tableId(), room.incarnation(), room.decision(), top.skyeyefast.mchjong.engine.SichuanPreset.TFMJ_2024.config())));
                stage++;
            }
            case 32 -> {
                if (table.clientSichuanSettings().preset() != top.skyeyefast.mchjong.engine.SichuanPreset.TFMJ_2024) break;
                sendRoom(client, pos, room, RoomAction.Type.BEGIN_SEATING); stage++;
            }
            case 33 -> {
                if (room.seating() != RoomSeating.Stage.POSITIONING) break;
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    if (main.getVehicle() instanceof SeatEntity mount) { main.stopRiding(); mount.discard(); }
                    target.sit(main, target.participantRoom(main).seatOf(mainId));
                    target.open(main);
                });
                stage++;
            }
            case 34 -> {
                if (room.actions().stream().noneMatch(action -> action.type() == RoomAction.Type.READY)) break;
                sendRoom(client, pos, room, RoomAction.Type.READY); stage++;
            }
            case 35 -> {
                if (table.clientSichuanView() == null) break;
                task = server.submit(() -> {
                    var main = server.getPlayerList().getPlayer(mainId);
                    var target = (MahjongTableBlockEntity) main.serverLevel().getBlockEntity(pos);
                    target.load(target.saveWithoutMetadata());
                    var session = (SichuanSession) target.participantRoom(main);
                    session.synchronizeSeats(java.util.Map.of(mainId, session.seatOf(mainId)));
                    require(session.participants().stream().filter(top.skyeyefast.mchjong.engine.TableParticipant::bot).count() == 3,
                        "NBT restore lost Sichuan Bots");
                    for (int tick = 0; tick < 30_000 && session.game().phase() != SichuanGame.Phase.MATCH_END; tick++) {
                        var offered = session.view(mainId);
                        if (offered.canConfirmNextHand()) {
                            require(offered.confirmedCount() == 3, "Sichuan Bots did not confirm the hand");
                            TableNetworking.receive(main, new SichuanNextHandPayload(pos, offered.tableId(), offered.incarnation(), offered.game().decision()));
                        } else for (int index = 0; index < offered.game().actions().size(); index++) {
                            var type = offered.game().actions().get(index).type();
                            if (type == SichuanAction.Type.VOID_SUIT || type == SichuanAction.Type.DISCARD
                                || type == SichuanAction.Type.PASS || type == SichuanAction.Type.WIN) {
                                TableNetworking.receive(main, new SichuanActionPayload(pos, offered.tableId(), offered.incarnation(), offered.game().decision(), index));
                                break;
                            }
                        }
                        session.tick();
                    }
                    require(session.game().phase() == SichuanGame.Phase.MATCH_END, "One human and three Sichuan Bots stalled");
                    var replay = session.pendingReplays().get(0);
                    require(replay.complete() && replay.handCount() == 8 && replay.participants().stream()
                        .filter(top.skyeyefast.mchjong.engine.ReplayMatch.Participant::bot).count() == 3, "Bot replay roster or hands lost");
                    top.skyeyefast.mchjong.engine.ReplayCodec.validate(replay);
                    require(replay.header().finalRanks().size() == 4, "Bot match lost final standings");
                    target.open(main);
                });
                stage++;
            }
            case 36 -> {
                if (!(client.screen instanceof SichuanResultsScreen) || table.clientSichuanView().game().phase() != SichuanGame.Phase.MATCH_END) break;
                require(table.clientSichuanView().game().handNumber() == 8, "Bot client lost match completion");
                press(client, "action.mchjong.return_to_lobby"); stage++;
            }
            case 37 -> {
                if (!room.lobby() || !(client.screen instanceof SichuanLobbyScreen)) break;
                require(room.seats().stream().filter(seat -> seat.participant().bot() && seat.participant().ready()).count() == 3,
                    "Returning to the lobby lost ready Bots");
                return true;
            }
            default -> throw new IllegalStateException("Unknown Sichuan smoke stage");
        }
        return false;
    }
    private static void sendRoom(Minecraft client, BlockPos pos, top.skyeyefast.mchjong.engine.TableRoomView room, RoomAction.Type type) {
        int index = java.util.stream.IntStream.range(0, room.actions().size()).filter(i -> room.actions().get(i).type() == type).findFirst().orElseThrow();
        client.getConnection().send(PayloadPackets.serverbound(new TableRoomActionPayload(pos, room.tableId(), room.incarnation(), room.decision(), index)));
    }
    private static void press(Minecraft client, String key) {
        var label = Component.translatable(key).getString();
        var button = client.screen.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
            .filter(candidate -> candidate.getMessage().getString().equals(label)).findFirst().orElseThrow();
        require(button.active, "Disabled Sichuan control: " + key);
        button.onPress();
    }
    private static void selectPreset(Minecraft client, String key) {
        var editor = client.screen;
        String prefix = Component.translatable("rules.mchjong.preset", "").getString();
        client.screen.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
            .filter(button -> button.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow().onPress();
        press(client, key);
        require(client.screen == editor, "Preset selection did not return to its rule editor");
    }
    private static void verifyRuleLayout(Minecraft client) {
        var screen = client.screen;
        int originalWidth = screen.width, originalHeight = screen.height;
        for (var size : List.of(new int[]{320, 240}, new int[]{640, 400})) {
            screen.init(client, size[0], size[1]);
            for (var group : top.skyeyefast.mchjong.engine.SichuanRuleOption.Group.values()) {
                press(client, group.translationKey());
                for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget)
                    require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= size[0]
                        && widget.getY() + widget.getHeight() <= size[1],
                        "Sichuan rules widget outside logical viewport");
            }
        }
        screen.init(client, originalWidth, originalHeight);
        press(client, top.skyeyefast.mchjong.engine.SichuanRuleOption.Group.BASIC.translationKey());
    }
    private static void finishHand(SichuanSession session) {
        for (int move = 0; move < 2000 && !session.game().ended(); move++) advanceOne(session);
        require(session.game().ended(), "Sichuan hand exceeded action bound");
    }
    private static void advanceOne(SichuanSession session) {
        for (var seat : session.roomView(null).seats()) {
            var actor = seat.participant().id();
            var offered = session.view(actor);
            if (offered.game().actions().isEmpty()) continue;
            int index = 0;
            for (int action = 0; action < offered.game().actions().size(); action++) {
                var type = offered.game().actions().get(action).type();
                if (type == SichuanAction.Type.WIN || type == SichuanAction.Type.PASS) { index = action; break; }
            }
            require(session.act(actor, offered.tableId(), offered.incarnation(), offered.game().decision(), index), "Sichuan issued action rejected");
            return;
        }
        throw new IllegalStateException("Sichuan hand stalled");
    }
    private static net.minecraft.world.phys.Vec3 project(Minecraft client, BlockPos pos, net.minecraft.world.phys.Vec3 point) {
        var camera = client.gameRenderer.getMainCamera();
        double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
        var forward = new net.minecraft.world.phys.Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        var right = new net.minecraft.world.phys.Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        var delta = top.skyeyefast.mchjong.world.TableGeometry.world(pos, point).subtract(camera.getPosition());
        double fov = ((top.skyeyefast.mchjong.mixin.GameRendererAccessor) client.gameRenderer).mchjong$getFov(camera, 1, true);
        double focal = client.screen.height / (2 * Math.tan(Math.toRadians(fov) / 2));
        return new net.minecraft.world.phys.Vec3(client.screen.width / 2.0 + delta.dot(right) * focal / delta.dot(forward),
            client.screen.height / 2.0 - delta.dot(right.cross(forward)) * focal / delta.dot(forward), 0);
    }
    private static final class Guest extends ServerPlayer {
        Guest(ServerPlayer main, int number) {
            super(main.server, main.serverLevel(), new GameProfile(UUID.randomUUID(), "SichuanGuest" + number));
            connection = new ServerGamePacketListenerImpl(main.server, new Connection(PacketFlow.SERVERBOUND), this) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            };
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
