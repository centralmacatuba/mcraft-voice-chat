package io.centralmacatuba.mcraft.voicechat.protocol;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceMessageCodecTest {
    @Test
    void roundTripsClientEnableMessage() throws IOException {
        VoiceMessage decoded = VoiceMessageCodec.decode(VoiceMessageCodec.encode("enable", new JsonObject()));
        assertEquals("enable", decoded.action());
        assertEquals("{}", decoded.payload().toString());
    }

    @Test
    void roundTripsUnicodeAndNestedPayload() throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "Jogador-á");
        VoiceMessage decoded = VoiceMessageCodec.decode(VoiceMessageCodec.encode("nearby", payload));
        assertEquals("nearby", decoded.action());
        assertEquals("Jogador-á", decoded.payload().get("username").getAsString());
    }

    @Test
    void rejectsTruncatedPacket() {
        assertThrows(IOException.class, () -> VoiceMessageCodec.decode(new byte[]{0, 5, 'e'}));
    }

    @Test
    void rejectsNonObjectJsonPayload() {
        byte[] packet = new byte[]{0, 6, 'e', 'n', 'a', 'b', 'l', 'e', 0, 2, '[', ']'};
        assertThrows(IOException.class, () -> VoiceMessageCodec.decode(packet));
    }

    @Test
    void rejectsTrailingBytes() throws IOException {
        byte[] valid = VoiceMessageCodec.encode("disable", new JsonObject());
        assertThrows(IOException.class, () -> VoiceMessageCodec.decode(Arrays.copyOf(valid, valid.length + 1)));
    }

    @Test
    void rejectsInvalidUtf8() {
        byte[] packet = new byte[]{0, 1, (byte) 0xFF, 0, 2, '{', '}'};
        assertThrows(IOException.class, () -> VoiceMessageCodec.decode(packet));
    }

    @Test
    void rejectsNegativeSignedLengthEncodedAsUnsignedI16() {
        byte[] packet = new byte[]{(byte) 0x80, 0, 0, 2, '{', '}'};
        assertThrows(IOException.class, () -> VoiceMessageCodec.decode(packet));
    }

    @Test
    void rejectsEmptyAction() {
        byte[] packet = new byte[]{0, 0, 0, 2, '{', '}'};
        assertThrows(IOException.class, () -> VoiceMessageCodec.decode(packet));
    }
}
