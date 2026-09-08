package fr.draftmc.deathban.manager;

import fr.draftmc.deathban.DeathBanPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BanManager {

    private final DeathBanPlugin plugin;
    private final File file;
    private final Map<UUID, DeathBanEntry> bans = new LinkedHashMap<UUID, DeathBanEntry>();

    public BanManager(DeathBanPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bans.yml");
    }

    public void load() {
        bans.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("bans");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String world = section.getString(key + ".world");
                long expireAt = section.getLong(key + ".expire-at", 0L);
                String name = section.getString(key + ".name", key);
                if (world != null) {
                    bans.put(uuid, new DeathBanEntry(uuid, name, world, expireAt));
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("UUID invalide dans bans.yml : " + key);
            }
        }
        purgeExpired();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (DeathBanEntry entry : bans.values()) {
            String path = "bans." + entry.getUuid().toString();
            yaml.set(path + ".name", entry.getName());
            yaml.set(path + ".world", entry.getWorld());
            yaml.set(path + ".expire-at", entry.getExpireAt());
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().severe("Impossible de créer le dossier du plugin.");
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'enregistrer bans.yml : " + e.getMessage());
        }
    }

    public void purgeExpired() {
        List<UUID> expired = new ArrayList<UUID>();
        for (Map.Entry<UUID, DeathBanEntry> entry : bans.entrySet()) {
            if (entry.getValue().isExpired()) {
                expired.add(entry.getKey());
            }
        }
        if (!expired.isEmpty()) {
            for (UUID uuid : expired) {
                bans.remove(uuid);
            }
            save();
        }
    }

    public DeathBanEntry ban(Player player, String worldName) {
        long duration = plugin.getConfigManager().getBanDurationSeconds();
        long expireAt = duration <= 0 ? 0L : System.currentTimeMillis() + duration * 1000L;
        DeathBanEntry entry = new DeathBanEntry(player.getUniqueId(), player.getName(), worldName, expireAt);
        bans.put(player.getUniqueId(), entry);
        save();
        return entry;
    }

    public DeathBanEntry ban(OfflinePlayer player, String worldName) {
        long duration = plugin.getConfigManager().getBanDurationSeconds();
        long expireAt = duration <= 0 ? 0L : System.currentTimeMillis() + duration * 1000L;
        String name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
        DeathBanEntry entry = new DeathBanEntry(player.getUniqueId(), name, worldName, expireAt);
        bans.put(player.getUniqueId(), entry);
        save();
        return entry;
    }

    public boolean unban(UUID uuid) {
        DeathBanEntry removed = bans.remove(uuid);
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public DeathBanEntry getBan(UUID uuid) {
        DeathBanEntry entry = bans.get(uuid);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            bans.remove(uuid);
            save();
            return null;
        }
        return entry;
    }

    public boolean isBannedFrom(UUID uuid, String worldName) {
        DeathBanEntry entry = getBan(uuid);
        return entry != null && entry.getWorld().equals(worldName);
    }

    public List<DeathBanEntry> getActiveBans() {
        purgeExpired();
        return new ArrayList<DeathBanEntry>(bans.values());
    }

    public void sendToSafeWorld(final Player player) {
        final String worldName = plugin.getConfigManager().getSafeWorld();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            plugin.getLogger().warning("Aucun monde sûr trouvé pour renvoyer " + player.getName());
            return;
        }
        final Location spawn = world.getSpawnLocation();
        Bukkit.getScheduler().runTask(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.teleport(spawn);
                }
            }
        });
    }

    public String formatDuration(DeathBanEntry entry) {
        if (entry == null || entry.isPermanent()) {
            return plugin.getConfigManager().raw("duration-permanent");
        }
        return plugin.getConfigManager().raw("duration-left")
                .replace("{time}", formatTime(entry.getRemainingMillis()));
    }

    public static String formatTime(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("j ");
        }
        if (hours > 0 || days > 0) {
            builder.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            builder.append(minutes).append("m ");
        }
        builder.append(seconds).append("s");
        return builder.toString().trim();
    }
}
