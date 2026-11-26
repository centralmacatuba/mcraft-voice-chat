package io.centralmacatuba.mcraft.voicechat.command;

import io.centralmacatuba.mcraft.voicechat.McraftVoiceChatPlugin;
import io.centralmacatuba.mcraft.voicechat.service.ModerationRequestQueue;
import io.centralmacatuba.mcraft.voicechat.service.VoicePlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
            String state = !plugin.isVoiceChatEnabled() ? ChatColor.RED + "desativado"
                    : plugin.isVoiceChatConfigured() ? ChatColor.GREEN + "configurado"
                    : ChatColor.YELLOW + "indisponível (backend de tokens não configurado)";
            sender.sendMessage(ChatColor.GREEN + "Voice chat: " + state + ChatColor.WHITE
                    + " — " + plugin.getActiveVoiceSessionCount() + " jogador(es) com sessão ativa.");
            sender.sendMessage(ChatColor.GRAY + "Backend de tokens: "
                    + (plugin.isVoiceChatConfigured()
                    ? ChatColor.YELLOW + "conectividade não verificada (testada ao solicitar sessão)" : ChatColor.RED + "indisponível"));
            sender.sendMessage(ChatColor.GRAY + "Moderação no LiveKit: "
                    + (plugin.isServerModerationAvailable()
                    ? ChatColor.GREEN + "configurada; conectividade não verificada (testada ao moderar)" : ChatColor.RED + "não configurada"));
            return true;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("unmute"))) {
            if (!sender.hasPermission("mcraftvoice.moderate")) {
                sender.sendMessage(ChatColor.RED + "Você não tem permissão para moderar o voice chat.");
                return true;
            }
            if (!plugin.isModerated()) {
                sender.sendMessage(ChatColor.RED + "A moderação está desativada na configuração.");
                return true;
            }
            if (!plugin.isServerModerationAvailable() || playerManager == null) {
                sender.sendMessage(ChatColor.RED + "Backend de moderação não configurado; nenhuma alteração foi aplicada.");
                return true;
            }

            OfflinePlayer target = resolveTarget(args[1]);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage(ChatColor.RED
                        + "Jogador não encontrado no cache do servidor. O jogador precisa ter entrado ao menos uma vez.");
                return true;
            }
            UUID targetId = target.getUniqueId();
            String targetName = target.getName() == null ? args[1] : target.getName();
            boolean mute = args[0].equalsIgnoreCase("mute");
            sender.sendMessage(ChatColor.YELLOW + "Solicitando " + (mute ? "mute" : "unmute")
                    + " ao backend para " + targetName + "...");

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
                                    sender.sendMessage(ChatColor.RED
                                            + "O backend não confirmou a moderação. O estado local não foi alterado.");
                                    return;
                                }
                                playerManager.setForceMuted(targetId, mute);
                                sender.sendMessage(ChatColor.GREEN + targetName
                                        + (mute ? " foi silenciado no LiveKit." : " teve o mute removido no LiveKit."));
                            });
                        } catch (IllegalPluginAccessException ignoredException) {
                            // Plugin disabled before the completion could be scheduled.
                        }
                    });
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label + " <status|mute <jogador>|unmute <jogador>>");
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
