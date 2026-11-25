package io.centralmacatuba.mcraft.voicechat;

import io.centralmacatuba.mcraft.voicechat.command.VoiceChatCommand;
import io.centralmacatuba.mcraft.voicechat.protocol.VoiceMessageCodec;
import io.centralmacatuba.mcraft.voicechat.service.HttpVoiceModerationProvider;
import io.centralmacatuba.mcraft.voicechat.service.HttpVoiceTokenProvider;
import io.centralmacatuba.mcraft.voicechat.service.VoiceModerationProvider;
import io.centralmacatuba.mcraft.voicechat.service.VoicePlayerManager;
import io.centralmacatuba.mcraft.voicechat.service.VoiceProximityService;
import io.centralmacatuba.mcraft.voicechat.service.VoiceTokenProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.time.Duration;

public final class McraftVoiceChatPlugin extends JavaPlugin {
    private VoicePlayerManager playerManager;
    private VoiceProximityService proximityService;
    private VoiceTokenProvider tokenProvider;
    private VoiceModerationProvider moderationProvider;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String endpoint = configuredString("voice-chat.token-provider.endpoint", "");
        String tokenEnvironmentVariable = configuredString(
                "voice-chat.token-provider.bearer-token-env", "MCRAFT_VOICE_BACKEND_TOKEN");
        String backendToken = System.getenv(tokenEnvironmentVariable);
        int timeoutSeconds = boundedInt("voice-chat.token-provider.request-timeout-seconds", 10, 1, 60);
        long maxTokenLifetimeSeconds = boundedLong(
                "voice-chat.token-provider.max-token-lifetime-seconds", 600, 60, 600);

        if (!getConfig().getBoolean("voice-chat.enabled", true)) {
            getLogger().info("Voice chat is disabled by configuration.");
            return;
        }
        if (endpoint.isBlank() || backendToken == null || backendToken.isBlank()) {
            getLogger().warning("Voice chat is unavailable: configure token-provider.endpoint and the "
                    + tokenEnvironmentVariable + " environment variable.");
            return;
        }
        try {
            tokenProvider = new HttpVoiceTokenProvider(URI.create(endpoint), backendToken,
                    Duration.ofSeconds(timeoutSeconds), Duration.ofSeconds(maxTokenLifetimeSeconds));
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid voice token-provider configuration; verify the HTTPS endpoint and timeout settings.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String moderationEndpoint = configuredString("voice-chat.moderation.endpoint", "");
        if (!moderationEndpoint.isBlank()) {
            try {
                moderationProvider = new HttpVoiceModerationProvider(
                        URI.create(moderationEndpoint), backendToken, Duration.ofSeconds(timeoutSeconds));
            } catch (IllegalArgumentException exception) {
                getLogger().severe("Server-side voice moderation is unavailable; verify the HTTPS endpoint and timeout settings.");
            }
        } else if (isModerated()) {
            getLogger().warning("Moderation commands are disabled until voice-chat.moderation.endpoint is configured.");
        }

        playerManager = new VoicePlayerManager(this, tokenProvider);
        proximityService = new VoiceProximityService(this, playerManager);
        getServer().getMessenger().registerIncomingPluginChannel(this, VoiceMessageCodec.CHANNEL, playerManager);
        getServer().getMessenger().registerOutgoingPluginChannel(this, VoiceMessageCodec.CHANNEL);
        getServer().getPluginManager().registerEvents(playerManager, this);

        VoiceChatCommand voiceCommand = new VoiceChatCommand(this, playerManager);
        if (getCommand("mcraftvoice") != null) {
            getCommand("mcraftvoice").setExecutor(voiceCommand);
            getCommand("mcraftvoice").setTabCompleter(voiceCommand);
        } else {
            getLogger().severe("Command mcraftvoice is missing from plugin.yml.");
        }

        long interval = Math.max(1, getConfig().getLong("voice-chat.proximity.update-interval-ticks", 10));
        Bukkit.getScheduler().runTaskTimer(this, proximityService::tick, interval, interval);
        getLogger().info("Mcraft Voice Chat enabled on Paper; protocol channel: " + VoiceMessageCodec.CHANNEL);
    }

    @Override
    public void onDisable() {
        if (playerManager != null) playerManager.shutdown();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    private String configuredString(String path, String fallback) {
        String value = getConfig().getString(path, fallback);
        return value == null ? fallback : value.trim();
    }

    private int boundedInt(String path, int fallback, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, getConfig().getInt(path, fallback)));
    }

    private long boundedLong(String path, long fallback, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, getConfig().getLong(path, fallback)));
    }

    public String getRoomName() {
        String room = configuredString("voice-chat.session.room", "minecraft-survival");
        return room.isBlank() ? "minecraft-survival" : room;
    }

    public String getServerId() {
        String serverId = configuredString("voice-chat.session.server-id", "mcraft");
        return serverId.isBlank() ? "mcraft" : serverId;
    }

    public double getHearingRange() {
        double configured = getConfig().getDouble("voice-chat.session.hearing-range", 32.0);
        return Double.isFinite(configured) ? Math.min(512.0, Math.max(0.0, configured)) : 32.0;
    }

    public boolean isModerated() {
        return getConfig().getBoolean("voice-chat.session.moderated", true);
    }

    public VoiceModerationProvider getModerationProvider() {
        return moderationProvider;
    }

    public boolean isServerModerationAvailable() {
        return moderationProvider != null;
    }
}
