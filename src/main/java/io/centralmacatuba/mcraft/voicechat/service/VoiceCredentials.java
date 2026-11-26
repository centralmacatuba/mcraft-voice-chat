package io.centralmacatuba.mcraft.voicechat.service;

public record VoiceCredentials(String url, String room, String token) {
    @Override
    public String toString() {
        return "VoiceCredentials[url=" + url + ", room=" + room + ", token=[REDACTED]]";
    }
}