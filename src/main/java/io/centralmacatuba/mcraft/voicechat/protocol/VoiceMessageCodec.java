package io.centralmacatuba.mcraft.voicechat.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Codec for the client's container: pstring(action, i16) + pstring(json, i16). */
public final class VoiceMessageCodec {
    public static final String CHANNEL = "minecraft-web-client:voice-chat";
    private static final int MAX_STRING_BYTES = Short.MAX_VALUE;

    private VoiceMessageCodec() {
    }

    public static VoiceMessage decode(byte[] packet) throws IOException {
        if (packet == null || packet.length < 4) {
            throw new IOException("Voice packet is too short.");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(packet))) {
            String action = readPString(input);
            String json = readPString(input);
            if (input.available() != 0) {
                throw new IOException("Unexpected trailing bytes in voice packet.");
            }
            if (action.isBlank()) {
                throw new IOException("Voice action cannot be empty.");
            }

            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(json.isBlank() ? "{}" : json);
            } catch (RuntimeException exception) {
                throw new IOException("Voice payload contains invalid JSON.", exception);
            }
            if (!parsed.isJsonObject()) {
                throw new IOException("Voice payload must be a JSON object.");
            }
            return new VoiceMessage(action, parsed.getAsJsonObject());
        }
    }

    public static byte[] encode(String action, JsonObject payload) throws IOException {
        if (action == null || action.isBlank()) {
            throw new IOException("Voice action cannot be empty.");
        }
        String json = payload == null ? "{}" : payload.toString();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            writePString(output, action);
            writePString(output, json);
        }
        return bytes.toByteArray();
    }

    private static String readPString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length > MAX_STRING_BYTES) {
            throw new IOException("pstring length exceeds the signed i16 limit.");
        }
        if (length > input.available()) {
            throw new IOException("Truncated pstring in voice packet.");
        }
        byte[] value = input.readNBytes(length);
        return new String(value, StandardCharsets.UTF_8);
    }

    private static void writePString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_STRING_BYTES) {
            throw new IOException("pstring exceeds the signed i16 limit.");
        }
        output.writeShort(encoded.length);
        output.write(encoded);
    }
}
