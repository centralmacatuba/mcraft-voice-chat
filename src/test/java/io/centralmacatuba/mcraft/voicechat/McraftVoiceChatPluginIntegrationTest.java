package io.centralmacatuba.mcraft.voicechat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises plugin loading and command registration against a simulated Paper server.
 * External LiveKit and client interoperability remains covered by the manual E2E plan.
 */
class McraftVoiceChatPluginIntegrationTest {
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
    void enablesWithoutBackendAndKeepsStatusCommandAvailable() {
        assertTrue(plugin.isEnabled(), "Missing optional backend configuration must not disable the plugin");
        assertFalse(plugin.isVoiceChatOperational(), "Voice chat must report unavailable without a token backend");
        assertFalse(plugin.isServerModerationAvailable(), "Moderation must remain unavailable without its backend");
        assertEquals(0, plugin.getActiveVoiceSessionCount());

        assertNotNull(plugin.getCommand("mcraftvoice"));
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "mcraftvoice status"));
    }

    @Test
    void registersTheShortCommandAlias() {
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "mcvoice status"));
    }

}
