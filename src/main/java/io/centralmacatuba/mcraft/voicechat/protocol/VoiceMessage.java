package io.centralmacatuba.mcraft.voicechat.protocol;

import com.google.gson.JsonObject;

public record VoiceMessage(String action, JsonObject payload) {
}
