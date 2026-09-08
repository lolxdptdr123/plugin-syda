package fr.draftmc.events.battleroyal.managers;

import fr.draftmc.events.battleroyal.BattleRoyal;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Systeme de points competitif, persiste dans points.yml.
 * Volontairement en YAML (aucune dependance externe type MySQL) pour
 * rester compatible sur n'importe quel core sans configuration DB.
 */
public class PointsManager {

    private final BattleRoyal plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Integer> cache = new HashMap<>();

    public PointsManager(BattleRoyal plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "points.yml");

        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Impossible de creer points.yml : " + e.getMessage());
            }
        }

        this.data = YamlConfiguration.loadConfiguration(file);
        for (String key : data.getKeys(false)) {
            try {
                cache.put(UUID.fromString(key), data.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // cle invalide, on l'ignore
            }
        }
    }

    public int getPoints(UUID uuid) {
        Integer value = cache.get(uuid);
        return value == null ? 0 : value;
    }

    public void addPoints(UUID uuid, int amount) {
        int newTotal = getPoints(uuid) + amount;
        cache.put(uuid, newTotal);
        data.set(uuid.toString(), newTotal);
        save();
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder points.yml : " + e.getMessage());
        }
    }

    public List<Map.Entry<UUID, Integer>> getTop(int count) {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(cache.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        return list.subList(0, Math.min(count, list.size()));
    }
}
