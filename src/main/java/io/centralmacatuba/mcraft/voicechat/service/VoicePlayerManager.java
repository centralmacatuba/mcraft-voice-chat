package io.centralmacatuba.mcraft.voicechat.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.centralmacatuba.mcraft.voicechat.McraftVoiceChatPlugin;
import io.centralmacatuba.mcraft.voicechat.protocol.VoiceMessage;
import io.centralmacatuba.mcraft.voicechat.protocol.VoiceMessageCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class VoicePlayerManager implements PluginMessageListener, Listener {
    private static final long FAILURE_COOLDOWN_MILLIS = 5_000L;
    private static final long REQUEST_COOLDOWN_MILLIS = 1_500L;
    private static final long MALFORMED_LOG_INTERVAL_MILLIS = 30_000L;
    private final McraftVoiceChatPlugin plugin;
    private final VoiceTokenProvider tokenProvider;
    private final Map<UUID, Player> activePlayers = new HashMap<>();
    private final Map<UUID, Long> pendingRequests = new HashMap<>();
    private final Map<UUID, Long> failedRequestCooldowns = new HashMap<>();
    private final Map<UUID, Long> requestCooldowns = new HashMap<>();
    private final Map<UUID, Long> malformedLogTimes = new HashMap<>();
    private final Map<UUID, String> lastNearbyPayloads = new HashMap<>();
    private final Set<UUID> forceMuted = new HashSet<>();
    private final AtomicLong requestSequence = new AtomicLong();

    public VoicePlayerManager(McraftVoiceChatPlugin plugin, VoiceTokenProvider tokenProvider) {
        this.plugin = plugin;
        this.tokenProvider = tokenProvider;
        for (String value : plugin.getConfig().getStringList("voice-chat.moderation.muted-players")) {
            try {
                forceMuted.add(UUID.fromString(value));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid UUID in voice-chat.moderation.muted-players.");
            }
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!VoiceMessageCodec.CHANNEL.equals(channel)) return;
        final VoiceMessage decoded;
        try {
            decoded = VoiceMessageCodec.decode(message);
        } catch (IOException exception) {
            long now = System.currentTimeMillis();
            long previous = malformedLogTimes.getOrDefault(player.getUniqueId(), 0L);
            if (now - previous >= MALFORMED_LOG_INTERVAL_MILLIS) {
                malformedLogTimes.put(player.getUniqueId(), now);
                plugin.getLogger().warning("Rejected malformed voice protocol message from " + player.getUniqueId()
                        + "; further malformed messages from this player are rate-limited.");
            }
            return;
        }
        switch (decoded.action()) {
            case "enable" -> enable(player);
            case "disable" -> disable(player.getUniqueId());
            default -> {
                String safeAction = decoded.action().replaceAll("[^A-Za-z0-9_.-]", "_");
                if (safeAction.length() > 32) safeAction = safeAction.substring(0, 32);
                plugin.getLogger().fine("Ignoring unsupported client voice action: " + safeAction);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> announceAvailable(event.getPlayer()));
    }

    @EventHandler
    public void onChannelRegistered(PlayerRegisterChannelEvent event) {
        if (VoiceMessageCodec.CHANNEL.equals(event.getChannel())) announceAvailable(event.getPlayer());
    }

    @EventHandler
    public void onChannelUnregistered(PlayerUnregisterChannelEvent event) {
        if (VoiceMessageCodec.CHANNEL.equals(event.getChannel())) disable(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        disable(playerId);
        failedRequestCooldowns.remove(playerId);
        requestCooldowns.remove(playerId);
        malformedLogTimes.remove(playerId);
        lastNearbyPayloads.remove(playerId);
        // Persisted moderation policy intentionally survives disconnects.
    }

    public void announceAvailable(Player player) {
        if (player.isOnline() && player.getListeningPluginChannels().contains(VoiceMessageCodec.CHANNEL)) {
            send(player, "available", new JsonObject());
        }
    }

    private void enable(Player player) {
        UUID playerId = player.getUniqueId();
        if (activePlayers.containsKey(playerId) || pendingRequests.containsKey(playerId)) return;
        if (!player.hasPermission("mcraftvoice.use")) return;
        long now = System.currentTimeMillis();
        Long cooldownUntil = failedRequestCooldowns.get(playerId);
        if (cooldownUntil != null && cooldownUntil > now) return;
        Long lastRequestAt = requestCooldowns.get(playerId);
        if (lastRequestAt != null && now - lastRequestAt < REQUEST_COOLDOWN_MILLIS) return;

        requestCooldowns.put(playerId, now);
        long requestId = requestSequence.incrementAndGet();
        String playerName = player.getName();
        pendingRequests.put(playerId, requestId);
        requestCredentials(playerId, playerName, requestId, forceMuted.contains(playerId));
    }

    private void requestCredentials(UUID playerId, String playerName, long requestId, boolean requestedForceMuted) {
        tokenProvider.createSession(playerId, playerName, plugin.getServerId(), plugin.getRoomName(),
                        requestedForceMuted)
                .whenComplete((credentials, error) -> {
                    if (!plugin.isEnabled()) return;
                    try {
                        Bukkit.getScheduler().runTask(plugin, () -> completeEnable(
                                playerId, requestId, playerName, requestedForceMuted, credentials, error));
                    } catch (IllegalPluginAccessException ignored) {
                        // Plugin disabled while the asynchronous request was completing.
                    }
                });
    }

    private void completeEnable(UUID playerId, long requestId, String playerName, boolean requestedForceMuted,
                                VoiceCredentials credentials, Throwable error) {
        if (!Long.valueOf(requestId).equals(pendingRequests.get(playerId))) return;
        boolean currentForceMuted = forceMuted.contains(playerId);
        if (currentForceMuted != requestedForceMuted) {
            requestCredentials(playerId, playerName, requestId, currentForceMuted);
            return;
        }
        pendingRequests.remove(playerId);
        if (error != null) {
            failedRequestCooldowns.put(playerId, System.currentTimeMillis() + FAILURE_COOLDOWN_MILLIS);
            plugin.getLogger().log(Level.WARNING, "Could not provision voice session for " + playerName, error);
            return;
        }
        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer == null || !onlinePlayer.isOnline()) return;
        // Permission may have been revoked while the asynchronous token request was pending.
        // Re-check on the server thread immediately before activating the session or sending its token.
        if (!onlinePlayer.hasPermission("mcraftvoice.use")) return;
        failedRequestCooldowns.remove(playerId);
        activePlayers.put(playerId, onlinePlayer);
        lastNearbyPayloads.remove(playerId);
        send(onlinePlayer, "config", createClientConfig(onlinePlayer, credentials));
    }

    private JsonObject createClientConfig(Player player, VoiceCredentials credentials) {
        JsonObject config = new JsonObject();
        config.addProperty("url", credentials.url());
        config.addProperty("room", credentials.room());
        config.addProperty("token", credentials.token());
        config.addProperty("identity", player.getUniqueId().toString());
        config.addProperty("hearingRange", plugin.getHearingRange());
        config.addProperty("moderated", plugin.isModerated());
        return config;
    }

    public void disable(UUID playerId) {
        pendingRequests.remove(playerId);
        activePlayers.remove(playerId);
        lastNearbyPayloads.remove(playerId);
    }

    /**
     * Re-checks permissions on the server thread. Permission plugins may revoke access
     * while a player is already connected and subscribed to the voice channel.
     */
    public void enforceCurrentPermissions() {
        for (Player player : new ArrayList<>(activePlayers.values())) {
            if (!player.isOnline() || !player.hasPermission("mcraftvoice.use")) {
                // The current upstream protocol has no server-to-client disable action.
                // Drop the server-side session and prevent stale token responses from restoring it.
                disable(player.getUniqueId());
            }
        }
        for (Map.Entry<UUID, Long> pending : new ArrayList<>(pendingRequests.entrySet())) {
            Player player = Bukkit.getPlayer(pending.getKey());
            if (player == null || !player.isOnline() || !player.hasPermission("mcraftvoice.use")) {
                pendingRequests.remove(pending.getKey(), pending.getValue());
            }
        }
    }

    public void sendNearby(Player player, JsonArray nearbyPlayers, JsonArray mutedIdentities) {
        if (!activePlayers.containsKey(player.getUniqueId())) return;
        JsonObject payload = new JsonObject();
        payload.add("players", nearbyPlayers);
        payload.add("forceMuted", mutedIdentities);
        String serialized = payload.toString();
        if (serialized.equals(lastNearbyPayloads.get(player.getUniqueId()))) return;
        lastNearbyPayloads.put(player.getUniqueId(), serialized);
        send(player, "nearby", payload);
    }

    public Collection<Player> getActivePlayers() {
        return new ArrayList<>(activePlayers.values());
    }

    public boolean isForceMuted(UUID playerId) {
        return forceMuted.contains(playerId);
    }

    /**
     * Allocates and durably stores the next mutation revision before the HTTP request is sent.
     * A skipped revision after a crash is safe; reusing a revision is not.
     */
    public long allocateModerationRevision(UUID playerId) {
        String path = "voice-chat.moderation.revisions." + playerId;
        long current = plugin.getConfig().getLong(path, 0L);
        if (current == Long.MAX_VALUE) {
            throw new IllegalStateException("Moderation revision counter is exhausted for this player.");
        }
        long next = current + 1L;
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        return next;
    }

    /** Persist the local mirror only after the trusted backend confirms enforcement. */
    public void setForceMuted(UUID playerId, boolean muted) {
        boolean changed = muted ? forceMuted.add(playerId) : forceMuted.remove(playerId);
        if (!changed) return;
        plugin.getConfig().set("voice-chat.moderation.muted-players",
                forceMuted.stream().map(UUID::toString).sorted().toList());
        plugin.saveConfig();
        lastNearbyPayloads.clear();
    }

    public void shutdown() {
        activePlayers.clear();
        pendingRequests.clear();
        failedRequestCooldowns.clear();
        requestCooldowns.clear();
        malformedLogTimes.clear();
        lastNearbyPayloads.clear();
        forceMuted.clear();
    }

    private void send(Player player, String action, JsonObject payload) {
        if (!player.isOnline() || !player.getListeningPluginChannels().contains(VoiceMessageCodec.CHANNEL)) return;
        try {
            player.sendPluginMessage(plugin, VoiceMessageCodec.CHANNEL, VoiceMessageCodec.encode(action, payload));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not encode voice action " + action, exception);
        }
    }
}
