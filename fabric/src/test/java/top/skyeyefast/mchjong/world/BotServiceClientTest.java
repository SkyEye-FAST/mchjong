package top.skyeyefast.mchjong.world;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.BotPosition;
import static org.junit.jupiter.api.Assertions.*;

class BotServiceClientTest {
    @Test void wireNamesAndDecisionEchoProtectServerActions() {
        UUID table = UUID.randomUUID(), session = UUID.randomUUID();
        BotPosition position = new BotPosition(table, session, 1, 0, 4, 12,
            new BotPosition.Opening(0, 0, 0, 0, List.of(25000, 25000, 25000, 25000),
                List.of(0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48), 108),
            List.of(new BotPosition.Event("DRAW", 0, 52, false, false, null, null, List.of())),
            List.of(new Action(Action.Type.DISCARD, 52)), null, 52, List.of());
        var request = JsonParser.parseString(BotServiceClient.requestBody(position)).getAsJsonObject();
        assertEquals(4, request.get("player_count").getAsInt());
        assertEquals(1, request.getAsJsonArray("legal_actions").size());
        assertEquals(108, request.getAsJsonObject("opening").get("dora_marker").getAsInt());

        String valid = """
            {"table_id":"%s","session_id":"%s","hand_number":1,
             "seat":0,"decision":12,"action_index":0,"action_id":null}
            """.formatted(table, session);
        assertEquals(0, BotServiceClient.validatedIndex(position, valid));
        assertThrows(IllegalStateException.class, () -> BotServiceClient.validatedIndex(position,
            valid.replace("\"decision\":12", "\"decision\":11")));
        assertThrows(IllegalStateException.class, () -> BotServiceClient.validatedIndex(position,
            valid.replace("\"action_index\":0", "\"action_index\":1")));
    }
}
