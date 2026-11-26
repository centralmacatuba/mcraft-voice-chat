package io.centralmacatuba.mcraft.voicechat.command;

import io.centralmacatuba.mcraft.voicechat.McraftVoiceChatPlugin;
import io.centralmacatuba.mcraft.voicechat.service.ModerationRequestQueue;
import io.centralmacatuba.mcraft.voicechat.service.VoicePlayerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public final class VoiceChatCommand implements CommandExecutor, TabCompleter {
    private final McraftVoiceChatPlugin plugin;
    private VoicePlayerManager playerManager;
    private final ModerationRequestQueue moderationQueue = new ModerationRequestQueue();

    public VoiceChatCommand(McraftVoiceChatPlugin plugin, VoicePlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
    }

    public void setPlayerManager(VoicePlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            Component state = !plugin.isVoiceChatEnabled()
                    ? Component.text("desativado", NamedTextColor.RED)
                    : plugin.isVoiceChatConfigured()
                    ? Component.text("configurado", NamedTextColor.GREEN)
                    : Component.text("indisponível (backend de tokens não configurado)", NamedTextColor.YELLOW);

            sender.sendMessage(Component.text("Voice chat: ", NamedTextColor.GREEN)
                    .append(state)
                    .append(Component.text(" — " + plugin.getActiveVoiceSessionCount()
                            + " jogador(es) com sessão ativa.", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("Backend de tokens: ", NamedTextColor.GRAY)
                    .append(Component.text(plugin.isVoiceChatConfigured()
                                    ? "conectividade não verificada (testada ao solicitar sessão)" : "indisponível",
                            plugin.isVoiceChatConfigured() ? NamedTextColor.YELLOW : NamedTextColor.RED)));
            sender.sendMessage(Component.text("Moderação no LiveKit: ", NamedTextColor.GRAY)
                    .append(Component.text(plugin.isServerModerationAvailable()
                                    ? "configurada; conectividade não verificada (testada ao moderar)" : "não configurada",
                            plugin.isServerModerationAvailable() ? NamedTextColor.GREEN : NamedTextColor.RED)));
            return true;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("unmute"))) {
            if (!sender.hasPermission("mcraftvoice.moderate")) {
                sender.sendMessage(Component.text("Você não tem permissão para moderar o voice chat.", NamedTextColor.RED));
                return true;
            }
            if (!plugin.isModerated()) {
                sender.sendMessage(Component.text("A moderação está desativada na configuração.", NamedTextColor.RED));
                return true;
            }
            if (!plugin.isServerModerationAvailable() || playerManager == null) {
                sender.sendMessage(Component.text(
                        "Backend de moderação não configurado; nenhuma alteração foi aplicada.", NamedTextColor.RED));
                return true;
            }

            OfflinePlayer target = resolveTarget(args[1]);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage(Component.text(
                        "Jogador não encontrado no cache do servidor. O jogador precisa ter entrado ao menos uma vez.",
                        NamedTextColor.RED));
                return true;
            }
            UUID targetId = target.getUniqueId();
            String targetName = target.getName() == null ? args[1] : target.getName();
            boolean mute = args[0].equalsIgnoreCase("mute");
            sender.sendMessage(Component.text("Solicitando " + (mute ? "mute" : "unmute")
                    + " ao backend para " + targetName + "...", NamedTextColor.YELLOW));

            moderationQueue.submit(targetId, () -> plugin.getModerationProvider().setMuted(
                            targetId, plugin.getServerId(), plugin.getRoomName(), mute))
                    .whenComplete((ignored, error) -> {
                        if (!plugin.isEnabled()) return;
                        try {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (error != null) {
                                    Throwable cause = error instanceof CompletionException && error.getCause() != null
                                            ? error.getCause() : error;
                                    plugin.getLogger().log(Level.WARNING,
                                            "Could not apply server-side voice moderation for " + targetId, cause);
                                    sender.sendMessage(Component.text(
                                            "O backend não confirmou a moderação. O estado local não foi alterado.",
                                            NamedTextColor.RED));
                                    return;
                                }
                                playerManager.setForceMuted(targetId, mute);
                                sender.sendMessage(Component.text(targetName
                                        + (mute ? " foi silenciado no LiveKit." : " teve o mute removido no LiveKit."),
                                        NamedTextColor.GREEN));
                            });
                        } catch (IllegalPluginAccessException ignoredException) {
                            // Plugin disabled before the completion could be scheduled.
                        }
                    });
            return true;
        }
        sender.sendMessage(Component.text(
                "Uso: /" + label + " <status|mute <jogador>|unmute <jogador>>", NamedTextColor.YELLOW));
        return true;
    }

    private OfflinePlayer resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> actions = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("mcraftvoice.moderate") && plugin.isModerated()
                    && plugin.isServerModerationAvailable()) {
                actions.add("mute");
                actions.add("unmute");
            }
            return actions.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && sender.hasPermission("mcraftvoice.moderate")
                && plugin.isServerModerationAvailable()
                && (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("unmute"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        return List.of();
    }
}
