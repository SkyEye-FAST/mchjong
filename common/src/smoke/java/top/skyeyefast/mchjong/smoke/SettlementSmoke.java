package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.client.TableResults;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.client.RiichiAudio;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.client.VoicePresets;
import top.skyeyefast.mchjong.engine.ScoreAnnouncements;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.HandScore;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Visual fixtures only; never replace the authoritative game or send fabricated actions. */
final class SettlementSmoke {
    private RiichiView fixture;
    private int ticks;
    private boolean animations;
    private boolean sequenceComplete, heardRecording;
    private int sequenceTicks, captureStage, previousRows;
    private net.minecraft.resources.Identifier voicePreset;
    private TableSettings.VoiceSource voiceSource;
    private double voiceVolume;
    private static final String[] LANGUAGES = {"ja_jp", "zh_cn", "zh_tw", "en_us"};
    private int locale, localeTicks;
    private java.util.concurrent.CompletableFuture<Void> languageReload;
    private java.util.concurrent.CompletableFuture<com.mojang.blaze3d.audio.SoundBuffer> voiceDecode;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        if (fixture == null) {
            animations = top.skyeyefast.mchjong.client.TableSettings.get().animations;
            top.skyeyefast.mchjong.client.TableSettings.get().animations = true;
            var settings = TableSettings.get();
            voicePreset = settings.voicePreset;
            voiceSource = settings.voiceSource;
            voiceVolume = settings.voiceVolume;
            var preset = net.minecraft.resources.Identifier.parse("smoke:readout");
            // Reuse an original project effect as a bounded smoke-only voice recording.
            try (var sound = client.getResourceManager().open(net.minecraft.resources.Identifier.parse("mchjong:sounds/table/score_reveal.ogg"))) {
                byte[] recording = sound.readAllBytes();
                var recordings = new java.util.HashMap<String, byte[]>();
                ScoreAnnouncements.SUBTITLES.keySet().forEach(event -> recordings.put(event, recording));
                VoicePresets.installLocal(java.util.Map.of(preset,
                    new top.skyeyefast.mchjong.config.PresetArchives.Voice("Readout smoke", recordings)));
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            settings.voicePreset = preset;
            settings.voiceSource = TableSettings.VoiceSource.SELECTED;
            settings.voiceVolume = .2;
            // Match the native engine's sound-only provider; it cannot supply sounds.json.
            voiceDecode = new net.minecraft.client.sounds.SoundBufferLibrary(location -> location.getPath().endsWith(".ogg")
                    ? client.getResourceManager().getResource(location) : java.util.Optional.empty())
                .getCompleteBuffer(VoicePresets.audioPath(preset, "yaku.riichi"));
            fixture = fixture(table.clientView());
            client.setScreen(null);
            acceptFixture(table, fixture);
            client.setScreen(new RiichiTableScreen(table.getBlockPos()));
        }
        acceptFixture(table, fixture);
        if (ticks >= 110) {
            if (languageReload == null) {
                client.getLanguageManager().setSelected(LANGUAGES[locale]);
                languageReload = client.reloadResourcePacks();
                return false;
            }
            if (!languageReload.isDone() || client.getOverlay() != null) return false;
            languageReload.join();
            if (localeTicks++ == 0) {
                client.getWindow().setWindowed(960, 720);
                client.options.guiScale().set(3);
                client.resizeGui();
                client.setScreen(new RiichiTableScreen(table.getBlockPos()));
            }
            if (localeTicks < 8) return false;
            if (localeTicks == 8) {
                if (client.screen.width != 320 || client.screen.height != 240)
                    throw new IllegalStateException("Settlement locale viewport was not 320x240");
                checkBounds(client);
                capture(client, output, "18-settlement-" + LANGUAGES[locale] + ".png");
                clickAward(client);
                if (!(client.screen instanceof top.skyeyefast.mchjong.client.TableHelpScreen)) throw new IllegalStateException("Mouse result explanation did not open");
                checkBounds(client);
                return false;
            }
            if (localeTicks < 12) return false;
            if (localeTicks == 12) {
                capture(client, output, "20-result-explanation-" + LANGUAGES[locale] + ".png");
                client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0));
                if (!(client.screen instanceof RiichiTableScreen)) throw new IllegalStateException("Explanation did not return to settlement");
                return false;
            }
            if (localeTicks == 13) {
                click(client, net.minecraft.network.chat.Component.translatable("ui.mchjong.result_page.2").getString());
                return false;
            }
            checkBounds(client);
            capture(client, output, "19-ranking-" + LANGUAGES[locale] + ".png");
            if (++locale == LANGUAGES.length) return true;
            localeTicks = 0;
            languageReload = null;
            return false;
        }
        if (!sequenceComplete) {
            if (++sequenceTicks > 900) throw new IllegalStateException("Settlement readout stalled");
            if (voiceDecode.isDone()) voiceDecode.join();
            var readout = RiichiAudio.result(fixture);
            if (readout == null) throw new IllegalStateException("Missing settlement readout");
            heardRecording |= VoicePresets.playing();
            int visible = readout.visibleRows(0);
            if (captureStage == 0 && visible == 1 && previousRows == 1) {
                capture(client, output, "06-readout-first-yaku.png");
                captureStage++;
            } else if (captureStage == 1 && visible >= 3 && previousRows >= 3) {
                capture(client, output, "06-readout-partial.png");
                captureStage++;
            } else if (captureStage == 2 && readout.scoredAt(0) >= 0
                    && net.minecraft.util.Util.getMillis() - readout.scoredAt(0) >= 50) {
                if (readout.limitVisible(0)) throw new IllegalStateException("Grade appeared in the points beat");
                capture(client, output, "06-readout-points.png");
                captureStage++;
            } else if (captureStage == 3 && readout.limitVisible(0)) {
                if (net.minecraft.util.Util.getMillis() - readout.scoredAt(0) < 750)
                    throw new IllegalStateException("Grade did not leave a 750ms points beat");
                capture(client, output, "06-readout-grade.png");
                captureStage++;
            }
            previousRows = visible;
            if (!readout.complete() || net.minecraft.util.Util.getMillis() - readout.pointsAt() < 1300) return false;
            if (!voiceDecode.isDone()) throw new IllegalStateException("Voice buffer never decoded");
            voiceDecode.join();
            if (!heardRecording || captureStage != 4) throw new IllegalStateException("Readout did not visit every recorded stage");
            checkSettledPoints(client);
            TableResults panel = panel(client);
            client.screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(panel.getX() + 15, panel.getY() + 25, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
            sequenceComplete = true;
        }
        ticks++;
        if (ticks == 2) {
            InputSmoke.switchView(client.screen);
        } else if (ticks == 6) {
            capture(client, output, "07-readout-complete-immersive.png");
            InputSmoke.switchView(client.screen);
        } else if (ticks == 10) {
            checkBounds(client);
            capture(client, output, "08-settlement.png");
            TableResults panel = panel(client);
            int span = panel.getWidth() - 20 - (panel.getWidth() >= 500 ? 156 : 0);
            client.screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(panel.getX() + 10 + span * 3 / 4, panel.getY() + 25, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
        } else if (ticks == 20) {
            if (panel(client).selectedWinner() != 1) throw new IllegalStateException("Second winner was not selectable");
            capture(client, output, "09-settlement-details.png");
            client.options.guiScale().set(3);
            client.resizeGui();
        } else if (ticks == 30) {
            checkBounds(client);
            capture(client, output, "10-settlement-small.png");
            click(client, net.minecraft.network.chat.Component.translatable("ui.mchjong.view_table").getString());
        } else if (ticks == 35) {
            if (client.screen.children().stream().anyMatch(TableResults.class::isInstance))
                throw new IllegalStateException("Settlement could not be collapsed");
            click(client, net.minecraft.network.chat.Component.translatable("ui.mchjong.view_results").getString());
        } else if (ticks == 40) {
            TableResults panel = panel(client);
            int span = panel.getWidth() - 20 - (panel.getWidth() >= 500 ? 156 : 0);
            client.screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(panel.getX() + 10 + span * 3 / 4, panel.getY() + 25, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
        } else if (ticks == 45) {
            if (panel(client).selectedWinner() != 1) throw new IllegalStateException("Winner selection failed");
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeGui();
        } else if (ticks == 50) {
            if (client.screen.width != 320 || client.screen.height != 240)
                throw new IllegalStateException("Settlement viewport was not 320x240");
            checkBounds(client);
            capture(client, output, "15-settlement-smallest.png");
            click(client, net.minecraft.network.chat.Component.translatable("ui.mchjong.result_page.1").getString());
            checkSettledPoints(client);
            click(client, net.minecraft.network.chat.Component.translatable("ui.mchjong.result_page.1").getString());
            checkSettledPoints(client);
            ((RiichiTableScreen) client.screen).receivedView();
        } else if (ticks == 55) {
            checkBounds(client);
            checkSettledPoints(client);
            capture(client, output, "16-settlement-smallest-points.png");
            click(client, net.minecraft.network.chat.Component.translatable("ui.mchjong.result_page.2").getString());
        } else if (ticks == 60) {
            checkBounds(client);
            capture(client, output, "17-settlement-smallest-ranking.png");
            client.getWindow().setWindowed(1280, 800);
            client.options.guiScale().set(2);
            client.resizeGui();
            click(client, net.minecraft.network.chat.Component.translatable("ui.mchjong.result_page.1").getString());
            checkSettledPoints(client);
        } else if (ticks == 70) {
            checkBounds(client);
            capture(client, output, "12-settlement-points.png");
            click(client, net.minecraft.network.chat.Component.translatable("ui.mchjong.result_page.2").getString());
        } else if (ticks == 80) {
            capture(client, output, "13-settlement-ranking.png");
            var win = new RiichiView.Win(0, 2, 126, new HandScore(78, 0, 6, 288000, 0, 0,
                List.of("Daisushi", "SuankoTanki", "Tsuiso", "Tenhou"), 0));
            var changes = List.of(288000, 0, -288000, 0);
            var seats = new ArrayList<RiichiView.Seat>();
            for (int seat = 0; seat < fixture.seats().size(); seat++) {
                var player = fixture.seats().get(seat);
                seats.add(new RiichiView.Seat(player.entityBot(), player.name(), player.occupied(), player.bot(),
                    player.ready(), 25000 + changes.get(seat), player.hand(), player.drawn(), player.melds(),
                    player.river(), player.norths(), player.riichi(), player.exposed(), player.doubleRiichi()));
            }
            fixture = new RiichiView(fixture.tableId(), fixture.revision() + 1, fixture.decision() + 1,
                fixture.handNumber() + 1, fixture.rules(), RiichiView.Phase.HAND_END, fixture.viewerSeat(), fixture.dealer(),
                fixture.round(), fixture.honba(), fixture.riichiSticks(), fixture.turn(), fixture.remaining(),
                fixture.wallBreak(), fixture.wall(), null, seats, List.of(new RiichiAction(RiichiAction.Type.SKIP_SETTLEMENT)),
                List.of(win), "ron", changes, List.of(), List.of(), fixture.timeControl(), fixture.clocks(), List.of(),
                fixture.playerHandVisibility(), fixture.openHands(), null, null, fixture.autoPlay(), false, 1, java.util.Map.of(), List.of(), ScoreAnnouncements.maximumTicks(List.of(win)), 0);
            acceptFixture(table, fixture);
            RiichiAudio.finishResult();
            client.setScreen(new RiichiTableScreen(table.getBlockPos()));
        } else if (ticks == 85) {
            capture(client, output, "13-yakuman.png");
            client.options.guiScale().set(4);
            client.resizeGui();
        } else if (ticks == 90) {
            checkBounds(client);
            capture(client, output, "13-yakuman-smallest.png");
        } else if (ticks == 95) {
            checkBounds(client);
            fixture = new RiichiView(fixture.tableId(), fixture.revision() + 1, fixture.decision() + 1,
                fixture.handNumber(), fixture.rules(), RiichiView.Phase.HAND_END, fixture.viewerSeat(), fixture.dealer(),
                fixture.round(), fixture.honba(), fixture.riichiSticks(), fixture.turn(), fixture.remaining(),
                fixture.wallBreak(), fixture.wall(), null, fixture.seats(), List.of(new RiichiAction(RiichiAction.Type.SKIP_SETTLEMENT)),
                List.of(), "exhaustive", List.of(1500,1500,-1500,-1500), List.of(), List.of(),
                fixture.timeControl(), fixture.clocks(), List.of(), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false, null, null, fixture.autoPlay(), false, 1, java.util.Map.of(), List.of(), 0, 0);
            acceptFixture(table, fixture);
            client.setScreen(new RiichiTableScreen(table.getBlockPos()));
        } else if (ticks == 100) {
            InputSmoke.switchView(client.screen);
            if (!((RiichiTableScreen) client.screen).immersive()) throw new IllegalStateException("Settlement did not enter immersive view");
        } else if (ticks == 105) {
            capture(client, output, "14-settlement-draw-immersive.png");
            InputSmoke.switchView(client.screen);
        } else if (ticks == 110) {
            checkBounds(client);
            capture(client, output, "14-settlement-draw.png");
            top.skyeyefast.mchjong.client.TableSettings.get().animations = animations;
            TableSettings.get().voicePreset = voicePreset;
            TableSettings.get().voiceSource = voiceSource;
            TableSettings.get().voiceVolume = voiceVolume;
            VoicePresets.stop();
            fixture = fixture(fixture);
            acceptFixture(table, fixture);
            RiichiAudio.finishResult();
        }
        return false;
    }

    private static TableResults panel(Minecraft client) {
        return client.screen.children().stream().filter(TableResults.class::isInstance).map(TableResults.class::cast)
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing settlement panel"));
    }
    private static void clickAward(Minecraft client) {
        try {
            var panel = panel(client);
            var field = TableResults.class.getDeclaredField("awardHits"); field.setAccessible(true);
            var hit = ((List<?>) field.get(panel)).getFirst();
            var x = hit.getClass().getDeclaredMethod("x"); x.setAccessible(true);
            var y = hit.getClass().getDeclaredMethod("y"); y.setAccessible(true);
            client.screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(panel.getX() + (int) x.invoke(hit) + 1,
                panel.getY() + (int) y.invoke(hit) + 1, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private void checkSettledPoints(Minecraft client) {
        for (int seat = 0; seat < fixture.seats().size(); seat++)
            if (panel(client).displayedPoints(seat) != fixture.seats().get(seat).points())
                throw new IllegalStateException("Settlement score animation restarted after navigation or refresh");
    }

    private static void checkBounds(Minecraft client) {
        var help = net.minecraft.network.chat.Component.translatable("ui.mchjong.result_help");
        if (client.font.width(help) > client.screen.width - 20)
            throw new IllegalStateException("Settlement operations do not fit the footer: " + help.getString());
        for (int suit = 0; suit < 3; suit++) {
            var label = net.minecraft.network.chat.Component.translatable("sichuan.mchjong.suit." + suit + ".short");
            if (client.font.width(label) > 40)
                throw new IllegalStateException("Sichuan void label is clipped: " + label.getString());
        }
        for (var child : client.screen.children()) if (child instanceof AbstractWidget widget)
            if (widget.getX() < 0 || widget.getY() < 0 || widget.getRight() > client.screen.width
                    || widget.getBottom() > client.screen.height)
                throw new IllegalStateException("Widget outside resized settlement: " + widget.getMessage().getString());
    }

    private static void click(Minecraft client, String label) {
        var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
        client.screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(button.getX() + 5, button.getY() + 5, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
    }

    private static void capture(Minecraft client, Path output, String name) {
        SmokeScreenshots.grab(output.toFile(), name, client.getMainRenderTarget(), 1, ignored -> {});
    }

    static void acceptFixture(MahjongTableBlockEntity table, RiichiView view) {
        table.acceptView(view);
        if (table.clientView() == view) RiichiAudio.accept(table, view);
    }

    static RiichiView fixture(RiichiView base) {
        var seats = new ArrayList<RiichiView.Seat>();
        int[] points = {49000, 33000, -7000, 25000};
        for (int seat = 0; seat < 4; seat++) {
            var original = base.seats().get(seat);
            List<Integer> hand = seat < 2 ? List.of(0, 4, 8, 36, 40, 44, 72, 76, 80, 108, 109, 124, 125)
                : java.util.Collections.nCopies(13, Tile.HIDDEN);
            List<Meld> melds = switch (seat) {
                case 0 -> List.of(new Meld(Meld.Type.CONCEALED_QUAD, List.of(16, 17, 18, 19), 0, Tile.ABSENT));
                case 1 -> List.of(new Meld(Meld.Type.OPEN_QUAD, List.of(52, 53, 54, 55), 2, 52),
                    new Meld(Meld.Type.CONCEALED_QUAD, List.of(56, 57, 58, 59), 1, 56));
                case 2 -> List.of(new Meld(Meld.Type.SEQUENCE, List.of(88, 92, 96), 1, 88));
                default -> List.of();
            };
            seats.add(new RiichiView.Seat(false, seat == 0 ? "A player with a long display name" : "Player " + (seat + 1),
                true, false, false, points[seat], hand, Tile.ABSENT, melds, original.river(), List.of(), seat == 0, seat < 2, false));
        }
        var wins = List.of(new RiichiView.Win(0, 2, 126, new HandScore(10, 40, 0, 24000, 8000, 8000,
                List.of("Richi", "Ippatsu", "Chanta", "Sanshoku", "Haku", "SelfWind", "RoundWind"), 1)),
            new RiichiView.Win(1, 2, 126, new HandScore(5, 40, 0, 8000, 4000, 2000, List.of("Honitsu", "Chanta", "Haku"), 1)));
        return new RiichiView(base.tableId(), base.revision() + 10000, base.decision() + 10000, base.handNumber(), base.rules(), RiichiView.Phase.MATCH_END,
            0, 0, 7, 0, 0, 2, base.remaining(), base.wallBreak(), base.wall(), null, seats,
            List.of(new RiichiAction(RiichiAction.Type.SKIP_SETTLEMENT)), wins, "ron",
            List.of(24000, 8000, -32000, 0), List.of(69.0, 13.0, -57.0, -25.0), List.of(15.0, 5.0, -15.0, -5.0), base.timeControl(), base.clocks(), List.of(1, 2, 4, 3), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false, null, null, base.autoPlay(), false, 1, java.util.Map.of(), List.of(), ScoreAnnouncements.maximumTicks(wins) + RiichiGame.SETTLEMENT_TICKS, 0);
    }
}
