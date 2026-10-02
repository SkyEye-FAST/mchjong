package top.skyeyefast.mchjong.engine;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SichuanRoomSettingsTest {
    private static UUID id(int seat) { return new UUID(73, seat); }
    private static SichuanSession lobby() {
        var session = new SichuanSession(UUID.randomUUID(), 12);
        for (int seat = 0; seat < 4; seat++) assertTrue(session.join(id(seat), "Player " + seat, seat));
        return session;
    }

    @Test void lobbyProjectsCompleteRulesWithoutInventingAMatchView() {
        var session = lobby();
        var original = session.roomSettings();
        assertNull(session.game());
        assertNull(session.view(id(0)));
        assertNull(session.view(null));
        assertEquals(SichuanPreset.SBR_2025.config(), original.rules());
        assertEquals(SichuanPreset.SBR_2025, original.preset());
        assertFalse(original.custom());
        assertTrue(original.rulesEditable());
        assertTrue(session.configureClock(id(0), new TimeControl(30, 15)));
        assertEquals(new TimeControl(30, 15), session.roomSettings().timeControl());
        assertEquals(TimeControl.DEFAULT, original.timeControl());
    }

    @Test void everyFieldDeterminesPresetIdentityAndHasStrictBounds() {
        var standard = SichuanPreset.SBR_2025.config();
        for (var option : SichuanRuleOption.values()) {
            int value = option.get(standard) == option.min() ? option.max() : option.min();
            var changed = option.with(standard, value);
            assertEquals(value, option.get(changed));
            var settings = new SichuanRoomSettings(changed, TimeControl.DEFAULT, true);
            assertNull(settings.preset(), option.name());
            assertTrue(settings.custom());
            assertEquals(SichuanPreset.SBR_2025, new SichuanRoomSettings(option.with(changed, option.get(standard)),
                TimeControl.DEFAULT, true).preset());
            for (var other : SichuanRuleOption.values()) if (other != option) assertEquals(other.get(standard), other.get(changed));
            assertThrows(IllegalArgumentException.class, () -> option.with(standard, option.min() - 1));
            assertThrows(IllegalArgumentException.class, () -> option.with(standard, option.max() + 1));
        }
        assertEquals(new SichuanRules(3, 1, 2, 2, 1, 24, false, true, 8, true, true, true, true), standard);
        assertEquals(SichuanPreset.TFMJ_2024, SichuanPreset.match(SichuanPreset.TFMJ_2024.config()));
        assertNotEquals(standard, SichuanPreset.TFMJ_2024.config());
    }

    @Test void hostChangesAreAtomicClearReadyAndSurviveLobbyAndMatchRestore() {
        var session = lobby();
        assertTrue(session.configureEquipment(false, Tile.sichuanSet()));
        session.participants[1].ready = session.participants[2].ready = session.participants[3].ready = true;
        long decision = session.decision();
        var custom = SichuanRuleOption.MATCH_HANDS.with(SichuanRuleOption.FAN_CAP.with(session.rules(), 4), 2);
        var before = session.save();
        assertFalse(session.configureRules(id(1), decision, custom));
        assertFalse(session.configureRules(id(4), decision, custom));
        assertFalse(session.configureRules(id(0), decision - 1, custom));
        assertEquals(before, session.save());
        assertTrue(session.configureRules(id(0), decision, custom));
        assertTrue(session.participants().stream().noneMatch(TableParticipant::ready));
        assertNotEquals(decision, session.decision());
        assertFalse(session.configureRules(id(0), decision, SichuanPreset.SBR_2025.config()));
        assertEquals(custom, session.roomSettings().rules());
        assertTrue(session.roomSettings().custom());
        var restored = SichuanCodec.restoreSession(SichuanCodec.saveSession(session));
        assertEquals(session.roomSettings(), restored.roomSettings());
        assertNull(restored.view(id(0)));
        assertNotEquals(session.incarnation(), restored.incarnation());
        assertFalse(restored.configureRules(id(0), session.decision(), SichuanPreset.SBR_2025.config()));
        restored.startMatch();
        var playing = restored.save();
        assertFalse(restored.configureRules(id(0), restored.decision(), SichuanPreset.SBR_2025.config()));
        assertEquals(playing, restored.save());
        assertEquals(custom, restored.view(null).game().rules());
        var matchRestore = SichuanCodec.restoreSession(SichuanCodec.saveSession(restored));
        assertEquals(custom, matchRestore.roomSettings().rules());
        assertEquals(custom, matchRestore.game().rules());
        assertEquals(custom, matchRestore.view(null).game().rules());
    }

    @Test void worldPolicyIsProjectedAndEnforcedWithoutMutatingRules() {
        var session = lobby();
        session.configureWorld(new WorldPolicy(true, false, true, 5000, true, true, true, false, null));
        assertFalse(session.roomSettings().rulesEditable());
        var before = session.save();
        assertFalse(session.configureRules(id(0), session.decision(), SichuanRuleOption.FAN_CAP.with(session.rules(), 8)));
        assertEquals(before, session.save());
    }
}
