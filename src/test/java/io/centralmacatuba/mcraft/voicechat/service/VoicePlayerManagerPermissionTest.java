package io.centralmacatuba.mcraft.voicechat.service;

import com.google.gson.JsonObject;
import io.centralmacatuba.mcraft.voicechat.McraftVoiceChatPlugin;
import io.centralmacatuba.mcraft.voicechat.protocol.VoiceMessageCodec;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class VoicePlayerManagerPermissionTest {
    private ServerMock server;
    private McraftVoiceChatPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(McraftVoiceChatPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void removesAnActiveSessionWhenUsePermissionIsRevoked() throws Exception {
        Player player = server.addPlayer();
        VoicePlayerManager manager = new VoicePlayerManager(plugin,
                (uuid, username, serverId, room, forceMuted) ->
                        CompletableFuture.completedFuture(credentials()));

        manager.onPluginMessageReceived(VoiceMessageCodec.CHANNEL, player,
                VoiceMessageCodec.encode("enable", new JsonObject()));
        server.getScheduler().performOneTick();
        assertEquals(1, manager.getActivePlayers().size());

        player.addAttachment(plugin).setPermission("mcraftvoice.use", false);
        manager.enforceCurrentPermissions();

        assertTrue(manager.getActivePlayers().isEmpty());
    }

    @Test
    void discardsCredentialsThatCompleteAfterPendingPermissionIsRevoked() throws Exception {
        Player player = server.addPlayer();
        CompletableFuture<VoiceCredentials> pending = new CompletableFuture<>();
        VoicePlayerManager manager = new VoicePlayerManager(plugin,
                (uuid, username, serverId, room, forceMuted) -> pending);

        manager.onPluginMessageReceived(VoiceMessageCodec.CHANNEL, player,
                VoiceMessageCodec.encode("enable", new JsonObject()));
        player.addAttachment(plugin).setPermission("mcraftvoice.use", false);
        manager.enforceCurrentPermissions();

        pending.complete(credentials());
        server.getScheduler().performOneTick();

        assertTrue(manager.getActivePlayers().isEmpty());
    }

    private static VoiceCredentials credentials() {
        return new VoiceCredentials("wss://livekit.example.net", "minecraft-survival", "test.jwt.signature");
    }
}
