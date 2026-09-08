package fr.draftmc.events.masterkill.managers;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Centralise tous les messages textuels du plugin (config.yml, section
 * "messages"), avec remplacement de placeholders simple ("{cle}").
 */
public class MessageManager {

    private final MasterKillPlugin plugin;

    public MessageManager(MasterKillPlugin plugin) {
        this.plugin = plugin;
    }

    public String get(String key, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, "");
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String full = prefix + raw;

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                full = full.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return ChatColor.translateAlternateColorCodes('&', full);
    }

    public String get(String key) {
        return get(key, null);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, null);
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        Bukkit.broadcastMessage(get(key, placeholders));
    }

    public void broadcast(String key) {
        broadcast(key, null);
    }
}
