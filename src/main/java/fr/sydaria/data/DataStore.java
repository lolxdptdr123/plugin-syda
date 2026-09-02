package fr.sydaria.data;

import fr.sydaria.Sydaria;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DataStore {

    private final Sydaria plugin;
    private final File file;
    private YamlConfiguration yaml;

    public DataStore(Sydaria plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauver data.yml: " + e.getMessage());
        }
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public String path(UUID uuid) {
        return "players." + uuid.toString();
    }

    public long getLong(UUID uuid, String key, long def) {
        return yaml.getLong(path(uuid) + "." + key, def);
    }

    public void set(UUID uuid, String key, Object value) {
        yaml.set(path(uuid) + "." + key, value);
    }

    public void add(UUID uuid, String key, long amount) {
        set(uuid, key, getLong(uuid, key, 0) + amount);
    }

    public Set<String> stringSet(UUID uuid, String key) {
        List<String> list = yaml.getStringList(path(uuid) + "." + key);
        return new HashSet<String>(list);
    }

    public void setStringSet(UUID uuid, String key, Set<String> values) {
        yaml.set(path(uuid) + "." + key, new ArrayList<String>(values));
    }

    public ConfigurationSection playerSection(Player player) {
        String p = path(player.getUniqueId());
        if (yaml.getConfigurationSection(p) == null) {
            yaml.createSection(p);
        }
        return yaml.getConfigurationSection(p);
    }
}
