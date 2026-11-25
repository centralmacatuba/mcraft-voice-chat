package io.centralmacatuba.mcraft.voicechat.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.centralmacatuba.mcraft.voicechat.McraftVoiceChatPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

public final class VoiceProximityService {
    private final McraftVoiceChatPlugin plugin;
    private final VoicePlayerManager playerManager;

    public VoiceProximityService(McraftVoiceChatPlugin plugin, VoicePlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
    }

    /** Runs on the server thread and publishes only changed proximity payloads. */
    public void tick() {
        Collection<Player> activePlayers = playerManager.getActivePlayers();
        double hearingRange = plugin.getHearingRange();
        double hearingRangeSquared = hearingRange * hearingRange;

        for (Player listener : activePlayers) {
            if (!listener.isOnline()) {
                continue;
            }
            JsonArray nearby = new JsonArray();
            JsonArray muted = new JsonArray();
            Location listenerLocation = listener.getLocation();

            for (Player speaker : activePlayers) {
                if (speaker.getUniqueId().equals(listener.getUniqueId()) || !speaker.isOnline()) {
                    continue;
                }
                if (!listener.getWorld().equals(speaker.getWorld())) {
                    continue;
                }
                if (listenerLocation.distanceSquared(speaker.getLocation()) > hearingRangeSquared) {
                    continue;
                }

                UUID speakerId = speaker.getUniqueId();
                JsonObject entry = new JsonObject();
                entry.addProperty("identity", speakerId.toString());
                entry.addProperty("username", speaker.getName());
                nearby.add(entry);
                if (playerManager.isForceMuted(speakerId)) {
                    muted.add(speakerId.toString());
                }
            }
            playerManager.sendNearby(listener, nearby, muted);
        }
    }
}
