package top.skyeyefast.mchjong.client;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.junit.jupiter.api.Test;

class ReplayCommandTest {
    @Test void searchArgumentsRoundTripIncludingEmptyUnicodeAndEscapedQuotes() throws Exception {
        for (String value : new String[]{"", " ", "player", "name with spaces", "玩家", "\"quoted\"", "C:\\replays", "\"".repeat(80)}) {
            String encoded = ClientReplays.searchArgument(value);
            assertFalse(encoded.isEmpty(), "An empty search must still occupy one command argument");
            var reader = new StringReader(encoded);
            assertEquals(value, StringArgumentType.string().parse(reader));
            assertFalse(reader.canRead());
            assertTrue(("mchjong replay " + java.util.UUID.randomUUID() + " delete false " + encoded).length() <= 256);
        }
    }
}
