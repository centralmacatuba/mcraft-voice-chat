package io.centralmacatuba.mcraft.voicechat.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Applies an authoritative mute policy through the trusted server-side backend. */
public interface VoiceModerationProvider {
    CompletableFuture<Void> setMuted(UUID playerUuid, String serverId, String room, boolean muted);
}
