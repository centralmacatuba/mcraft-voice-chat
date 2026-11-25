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

    private final McraftVoiceChatPlugin plugin;
    private final VoiceTokenProvider tokenProvider;
    private final Map<UUID, Player> activePlayers = new HashMap<>();
    private final Map<UUID, Long> pendingRequests = new HashMap<>();
    private final Map<UUID, Long> failedRequestCooldowns = new HashMap<>();
    private final Map<UUID, Long> requestCooldowns = new HashMap<>();
    private final Map<UUID, String> lastNearbyPayloads = new HashMap<>();
    private final Set<UUID> forceMuted = new HashSet<>();
    private final AtomicLong requestSequence = new AtomicLong();

    public VoicePlayerManager(McraftVoiceChatPlugin plugin, VoiceTokenProvider tokenProvider) {
        this.plugin = plugin;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!VoiceMessageCodec.CHANNEL.equals(channel)) {
            return;
        }
        final VoiceMessage decoded;
        try {
            decoded = VoiceMessageCodec.decode(message);
        } catch (IOException exception) {
            plugin.getLogger().warning("Rejected malformed voice message from " + player.getName()
                    + ": " + exception.getMessage());
            return;
        }

        switch (decoded.action()) {
            case "enable" -> enable(player);
            case "disable" -> disable(player.getUniqueId());
            default -> plugin.getLogger().fine("Ignoring unsupported client voice action: " + decoded.action());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> announceAvailable(event.getPlayer()));
    }

    @EventHandler
    public void onChannelRegistered(PlayerRegisterChannelEvent event) {
        if (VoiceMessageCodec.CHANNEL.equals(event.getChannel())) {
            announceAvailable(event.getPlayer());
        }
    }

    @EventHandler
    public void onChannelUnregistered(PlayerUnregisterChannelEvent event) {
        if (VoiceMessageCodec.CHANNEL.equals(event.getChannel())) {
            disable(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        disable(playerId);
        failedRequestCooldowns.remove(playerId);
        requestCooldowns.remove(playerId);
        forceMuted.remove(playerId);
        lastNearbyPayloads.remove(playerId);
    }

    public void announceAvailable(Player player) {
        if (!player.isOnline() || !player.getListeningPluginChannels().contains(VoiceMessageCodec.CHANNEL)) {
            return;
        }
        send(player, "available", new JsonObject());
    }

    private void enable(Player player) {
        UUID playerId = player.getUniqueId();
        if (activePlayers.containsKey(playerId) || pendingRequests.containsKey(playerId)) {
            return;
        }
        if (!player.hasPermission("mcraftvoice.use")) {
            plugin.getLogger().fine("Player " + player.getName() + " does not have mcraftvoice.use.");
            return;
        }

        long now = System.currentTimeMillis();
        Long cooldownUntil = failedRequestCooldowns.get(playerId);
        if (cooldownUntil != null && cooldownUntil > now) {
            return;
        }
        Long lastRequestAt = requestCooldowns.get(playerId);
        if (lastRequestAt != null && now - lastRequestAt < REQUEST_COOLDOWN_MILLIS) {
            return;
        }

        requestCooldowns.put(playerId, now);
        long requestId = requestSequence.incrementAndGet();
        String playerName = player.getName();
        pendingRequests.put(playerId, requestId);
        tokenProvider.createSession(playerId, playerName, plugin.getServerId(), plugin.getRoomName())
                .whenComplete((credentials, error) -> {
                    if (!plugin.isEnabled()) {
                        return;
                    }
                    try {
                        Bukkit.getScheduler().runTask(plugin, () -> completeEnable(
                                playerId, requestId, playerName, credentials, error));
                    } catch (IllegalPluginAccessException ignored) {
                        // The plugin was disabled between the isEnabled check and task scheduling.
                    }
                });
    }

    private void completeEnable(UUID playerId, long requestId, String playerName,
                                VoiceCredentials credentials, Throwable error) {
        if (!Long.valueOf(requestId).equals(pendingRequests.get(playerId))) {
            return;
        }
        pendingRequests.remove(playerId);

        if (error != null) {
            failedRequestCooldowns.put(playerId, System.currentTimeMillis() + FAILURE_COOLDOWN_MILLIS);
            plugin.getLogger().log(Level.WARNING,
                    "Could not provision voice session for " + playerName, error);
            return;
        }

        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer == null || !onlinePlayer.isOnline()) {
            return;
        }

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

    public void sendNearby(Player player, JsonArray nearbyPlayers, JsonArray mutedIdentities) {
        if (!activePlayers.containsKey(player.getUniqueId())) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.add("players", nearbyPlayers);
        payload.add("forceMuted", mutedIdentities);
        String serialized = payload.toString();
        if (serialized.equals(lastNearbyPayloads.get(player.getUniqueId()))) {
            return;
        }
        lastNearbyPayloads.put(player.getUniqueId(), serialized);
        send(player, "nearby", payload);
    }

    public Collection<Player> getActivePlayers() {
        return new ArrayList<>(activePlayers.values());
    }

    public boolean isForceMuted(UUID playerId) {
        return forceMuted.contains(playerId);
    }

    /** Controls the force-muted identities advertised to nearby compatible clients. */
    public void setForceMuted(UUID playerId, boolean muted) {
        if (muted) {
            forceMuted.add(playerId);
        } else {
            forceMuted.remove(playerId);
        }
        lastNearbyPayloads.clear();
    }

    public void shutdown() {
        activePlayers.clear();
        pendingRequests.clear();
        failedRequestCooldowns.clear();
        requestCooldowns.clear();
        lastNearbyPayloads.clear();
        forceMuted.clear();
    }

    private void send(Player player, String action, JsonObject payload) {
        if (!player.isOnline() || !player.getListeningPluginChannels().contains(VoiceMessageCodec.CHANNEL)) {
            return;
        }
        try {
            player.sendPluginMessage(plugin, VoiceMessageCodec.CHANNEL,
                    VoiceMessageCodec.encode(action, payload));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not encode voice action " + action, exception);
        }
    }
}
