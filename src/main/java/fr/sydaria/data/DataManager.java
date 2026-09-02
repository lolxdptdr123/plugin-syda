package fr.sydaria.data;

import fr.sydaria.Sydaria;
import fr.sydaria.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataManager {
    private final YamlFile file;

    public DataManager(Sydaria plugin) {
        this.file = new YamlFile(plugin, "players.yml");
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                save();
            }
        }, 20L * 60, 20L * 60);
    }

    public void save() {
        file.save();
    }

    public void ensure(Player player) {
        String path = path(player.getUniqueId());
        if (!file.get().contains(path + ".name")) {
            file.get().set(path + ".name", player.getName());
            file.get().set(path + ".tokens", Sydaria.get().getConfig().getInt("tokens.starting", 0));
            file.get().set(path + ".money", Sydaria.get().getConfig().getDouble("economy.starting", 0));
            file.get().set(path + ".faction", "");
            file.get().set(path + ".tag", "");
            file.get().set(path + ".atouts", new ArrayList<String>());
            file.get().set(path + ".atouts_owned", new ArrayList<String>());
        } else {
            file.get().set(path + ".name", player.getName());
            if (!file.get().contains(path + ".atouts")) file.get().set(path + ".atouts", new ArrayList<String>());
            if (!file.get().contains(path + ".atouts_owned")) {
                file.get().set(path + ".atouts_owned", file.get().getStringList(path + ".atouts"));
            }
            if (!file.get().contains(path + ".money")) {
                file.get().set(path + ".money", Sydaria.get().getConfig().getDouble("economy.starting", 0));
            }
        }
    }

    public String path(UUID uuid) {
        return "players." + uuid.toString();
    }

    public int getInt(UUID uuid, String key) {
        return file.get().getInt(path(uuid) + "." + key, 0);
    }

    public void addInt(UUID uuid, String key, int amount) {
        file.get().set(path(uuid) + "." + key, getInt(uuid, key) + amount);
    }

    public void setInt(UUID uuid, String key, int value) {
        file.get().set(path(uuid) + "." + key, value);
    }

    public String getString(UUID uuid, String key) {
        return file.get().getString(path(uuid) + "." + key, "");
    }

    public void setString(UUID uuid, String key, String value) {
        file.get().set(path(uuid) + "." + key, value);
    }

    public List<String> getList(UUID uuid, String key) {
        List<String> list = file.get().getStringList(path(uuid) + "." + key);
        return list == null ? new ArrayList<String>() : new ArrayList<String>(list);
    }

    public void setList(UUID uuid, String key, List<String> list) {
        file.get().set(path(uuid) + "." + key, list);
    }

    public long getLong(UUID uuid, String key) {
        return file.get().getLong(path(uuid) + "." + key, 0L);
    }

    public void setLong(UUID uuid, String key, long value) {
        file.get().set(path(uuid) + "." + key, value);
    }

    public long getTokens(UUID uuid) {
        return file.get().getLong(path(uuid) + ".tokens", 0L);
    }

    public void setTokens(UUID uuid, long amount) {
        file.get().set(path(uuid) + ".tokens", amount);
    }

    public double getMoney(UUID uuid) {
        return file.get().getDouble(path(uuid) + ".money", 0D);
    }

    public void setMoney(UUID uuid, double amount) {
        file.get().set(path(uuid) + ".money", Math.max(0D, amount));
    }

    public ConfigurationSection section() {
        ConfigurationSection section = file.get().getConfigurationSection("players");
        return section;
    }

    public String nameOf(UUID uuid) {
        String name = getString(uuid, "name");
        if (name == null || name.isEmpty()) {
            Player p = Bukkit.getPlayer(uuid);
            return p != null ? p.getName() : uuid.toString().substring(0, 8);
        }
        return name;
    }
}
