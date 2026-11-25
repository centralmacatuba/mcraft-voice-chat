package io.centralmacatuba.mcraft.voicechat.command;

import io.centralmacatuba.mcraft.voicechat.McraftVoiceChatPlugin;
import io.centralmacatuba.mcraft.voicechat.service.VoicePlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VoiceChatCommand implements CommandExecutor, TabCompleter {
    private final McraftVoiceChatPlugin plugin;
    private final VoicePlayerManager playerManager;

    public VoiceChatCommand(McraftVoiceChatPlugin plugin, VoicePlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GREEN + "Voice chat: "
                    + ChatColor.WHITE + playerManager.getActivePlayers().size()
                    + " jogador(es) com sessão ativa.");
            return true;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("mute")
                || args[0].equalsIgnoreCase("unmute"))) {
            if (!sender.hasPermission("mcraftvoice.moderate")) {
                sender.sendMessage(ChatColor.RED + "Você não tem permissão para moderar o voice chat.");
                return true;
            }
            if (!plugin.isModerated()) {
                sender.sendMessage(ChatColor.RED + "A moderação está desativada na configuração.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Jogador não encontrado ou offline.");
                return true;
            }

            boolean mute = args[0].equalsIgnoreCase("mute");
            playerManager.setForceMuted(target.getUniqueId(), mute);
            sender.sendMessage(ChatColor.GREEN + target.getName()
                    + (mute ? " foi colocado no mudo forçado." : " foi removido do mudo forçado."));
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label
                + " <status|mute <jogador>|unmute <jogador>>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> actions = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("mcraftvoice.moderate") && plugin.isModerated()) {
                actions.add("mute");
                actions.add("unmute");
            }
            return actions.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && sender.hasPermission("mcraftvoice.moderate")
                && (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("unmute"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return List.of();
    }
}
