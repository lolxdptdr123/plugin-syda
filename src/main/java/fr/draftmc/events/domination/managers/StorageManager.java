package fr.draftmc.events.domination.managers;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.DominationState;
import fr.draftmc.events.domination.model.Zone;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Persistance de l'etat vivant de l'evenement (points par zone, l'evenement
 * etait-il lance) dans data.yml, separe de config.yml qui ne contient que
 * les reglages statiques. Permet a l'evenement de reprendre exactement ou
 * il s'etait arrete apres un redemarrage du serveur.
 */
public class StorageManager {

    private final DominationPlugin plugin;
    private final File file;

    public StorageManager(DominationPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    /** Charge l'etat sauvegarde au demarrage du plugin, et reprend l'evenement (sans decompte) s'il tournait avant l'arret du serveur. */
    public void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            String path = "zones." + zone.getName();
            if (!data.contains(path)) continue;

            ConfigurationSection pointsSection = data.getConfigurationSection(path + ".points");
            if (pointsSection != null) {
                for (String factionId : pointsSection.getKeys(false)) {
                    zone.setPoints(factionId, pointsSection.getInt(factionId));
                }
            }
        }

        if (data.getBoolean("running", false)) {
            plugin.getDominationManager().resumeWithoutCountdown();
        }
    }

    /** Sauvegarde synchrone immediate (utilisee a l'extinction du plugin). */
    public void saveSync() {
        writeSnapshot(buildSnapshot());
    }

    /** Sauvegarde asynchrone (utilisee a chaud a chaque tick de score, pour ne jamais bloquer le thread principal). */
    public void saveAsync() {
        YamlConfiguration snapshot = buildSnapshot();
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin.getHost(), () -> writeSnapshot(snapshot));
        } else {
            writeSnapshot(snapshot);
        }
    }

    private YamlConfiguration buildSnapshot() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("running", plugin.getDominationManager().getState() == DominationState.RUNNING);

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            String path = "zones." + zone.getName();
            for (Map.Entry<String, Integer> entry : zone.getAllPoints().entrySet()) {
                data.set(path + ".points." + entry.getKey(), entry.getValue());
            }
        }
        return data;
    }

    private void writeSnapshot(YamlConfiguration data) {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[Domination] Impossible de sauvegarder data.yml : " + e.getMessage());
        }
    }
}
