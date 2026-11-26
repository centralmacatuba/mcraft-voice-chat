package io.centralmacatuba.mcraft.voicechat.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface VoiceTokenProvider {
    CompletableFuture<VoiceCredentials> createSession(UUID playerUuid, String username,
                                                        String serverId, String room, boolean forceMuted);
}
