package fr.draftmc.deathban.manager;

import fr.draftmc.deathban.DeathBanPlugin;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ConfigManager {

    private final DeathBanPlugin plugin;
    private Set<String> worlds;
    private Set<String> allowedCommands;
    private String safeWorld;
    private long banDurationSeconds;

    public ConfigManager(DeathBanPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.worlds = new HashSet<String>();
        List<String> worldList = config.getStringList("worlds");
        for (String world : worldList) {
            if (world != null && !world.trim().isEmpty()) {
                worlds.add(world.trim());
            }
        }

        this.allowedCommands = new HashSet<String>();
        for (String command : config.getStringList("allowed-commands")) {
            if (command != null && !command.trim().isEmpty()) {
                allowedCommands.add(normalizeCommand(command));
            }
        }

        this.safeWorld = config.getString("safe-world", "world");
        this.banDurationSeconds = config.getLong("ban-duration", 0L);
    }

    public boolean isDeathWorld(World world) {
        return world != null && worlds.contains(world.getName());
    }

    public boolean isDeathWorld(String worldName) {
        return worldName != null && worlds.contains(worldName);
    }

    public boolean isCommandAllowed(String command) {
        return allowedCommands.contains(normalizeCommand(command));
    }

    public String getSafeWorld() {
        return safeWorld;
    }

    public long getBanDurationSeconds() {
        return banDurationSeconds;
    }

    public Set<String> getWorlds() {
        return worlds;
    }

    public String message(String key) {
        String prefix = color(plugin.getConfig().getString("messages.prefix", ""));
        String body = plugin.getConfig().getString("messages." + key, key);
        return prefix + color(body);
    }

    public String raw(String key) {
        return color(plugin.getConfig().getString("messages." + key, key));
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public static String normalizeCommand(String command) {
        String value = command.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        int colon = value.indexOf(':');
        if (colon >= 0 && colon < value.length() - 1) {
            value = value.substring(colon + 1);
        }
        return value;
    }

    public List<String> getWorldNames() {
        return new ArrayList<String>(worlds);
    }
}
