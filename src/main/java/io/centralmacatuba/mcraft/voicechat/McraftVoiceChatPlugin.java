package io.centralmacatuba.mcraft.voicechat;

import io.centralmacatuba.mcraft.voicechat.protocol.VoiceMessageCodec;
import io.centralmacatuba.mcraft.voicechat.service.HttpVoiceTokenProvider;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String endpoint = getConfig().getString("voice-chat.token-provider.endpoint", "").trim();
        String tokenEnvironmentVariable = getConfig().getString(
                "voice-chat.token-provider.bearer-token-env", "MCRAFT_VOICE_BACKEND_TOKEN");
        String backendToken = System.getenv(tokenEnvironmentVariable);
        int timeoutSeconds = Math.max(1, getConfig().getInt(
                "voice-chat.token-provider.request-timeout-seconds", 10));

        if (!getConfig().getBoolean("voice-chat.enabled", true)) {
            getLogger().info("Voice chat is disabled by configuration.");
            return;
        }

        if (endpoint.isBlank() || backendToken == null || backendToken.isBlank()) {
            getLogger().warning("Voice chat is not available: configure token-provider.endpoint and the "
                    + tokenEnvironmentVariable + " environment variable.");
            return;
        }

        try {
            tokenProvider = new HttpVoiceTokenProvider(URI.create(endpoint), backendToken,
                    Duration.ofSeconds(timeoutSeconds));
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid voice token-provider configuration: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerManager = new VoicePlayerManager(this, tokenProvider);
        proximityService = new VoiceProximityService(this, playerManager);

        getServer().getMessenger().registerIncomingPluginChannel(
                this, VoiceMessageCodec.CHANNEL, playerManager);
        getServer().getMessenger().registerOutgoingPluginChannel(this, VoiceMessageCodec.CHANNEL);
        getServer().getPluginManager().registerEvents(playerManager, this);

        long interval = Math.max(1, getConfig().getLong(
                "voice-chat.proximity.update-interval-ticks", 10));
        Bukkit.getScheduler().runTaskTimer(this, proximityService::tick, interval, interval);

        getLogger().info("Mcraft Voice Chat enabled on Paper; protocol channel: " + VoiceMessageCodec.CHANNEL);
    }

    @Override
    public void onDisable() {
        if (playerManager != null) {
            playerManager.shutdown();
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public String getRoomName() {
        return getConfig().getString("voice-chat.session.room", "minecraft-survival");
    }

    public String getServerId() {
        return getConfig().getString("voice-chat.session.server-id", "mcraft");
    }

    public double getHearingRange() {
        return Math.max(0.0, getConfig().getDouble("voice-chat.session.hearing-range", 32.0));
    }

    public boolean isModerated() {
        return getConfig().getBoolean("voice-chat.session.moderated", true);
    }
}
