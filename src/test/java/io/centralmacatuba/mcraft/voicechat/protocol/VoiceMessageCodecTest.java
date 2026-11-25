package io.centralmacatuba.mcraft.voicechat.protocol;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceMessageCodecTest {
    @Test
    void roundTripsClientEnableMessage() throws IOException {
        JsonObject payload = new JsonObject();
        VoiceMessage decoded = VoiceMessageCodec.decode(VoiceMessageCodec.encode("enable", payload));

        assertEquals("enable", decoded.action());
        assertEquals("{}", decoded.payload().toString());
    }

    @Test
    void roundTripsUnicodeAndNestedPayload() throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "Jogador-á");
        JsonObject decoded = VoiceMessageCodec.decode(VoiceMessageCodec.encode("nearby", payload));

        assertEquals("nearby", decoded.action());
        assertEquals("Jogador-á", decoded.payload().get("username").getAsString());
    }

    @Test
    void rejectsTruncatedPacket() {
        assertThrows(IOException.class, () -> VoiceMessageCodec.decode(new byte[]{0, 5, 'e'}));
    }

    @Test
    void rejectsNonObjectJsonPayload() throws IOException {
        byte[] packet = new byte[]{0, 6, 'e', 'n', 'a', 'b', 'l', 'e', 0, 2, '[', ']'};
        assertThrows(IOException.class, () -> VoiceMessageCodec.decode(packet));
    }

    @Test
    void rejectsTrailingBytes() throws IOException {
        byte[] valid = VoiceMessageCodec.encode("disable", new JsonObject());
        byte[] withTrailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IOException.class, () -> VoiceMessageCodec.decode(withTrailing));
    }
}
