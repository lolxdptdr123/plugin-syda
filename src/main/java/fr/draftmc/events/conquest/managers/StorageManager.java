package fr.draftmc.events.conquest.managers;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Persistance de l'etat vivant de l'evenement (points par zone, capteur en
 * cours, verrouillage, l'evenement etait-il lance) dans data.yml, separe
 * de config.yml qui ne contient que les reglages statiques. Permet a
 * l'evenement de reprendre exactement ou il s'etait arrete apres un
 * redemarrage du serveur.
 */
public class StorageManager {

    private final ConquestPlugin plugin;
    private final File file;

    public StorageManager(ConquestPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    /** Charge l'etat sauvegarde au demarrage du plugin, et relance l'evenement s'il tournait avant l'arret du serveur. */
    public void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            String path = "zones." + zone.getName();
            if (!data.contains(path)) continue;

            zone.setLocked(data.getBoolean(path + ".locked", false));
            zone.setCaptureSeconds(data.getInt(path + ".capture-seconds", 0));

            String captorRaw = data.getString(path + ".captor");
            if (captorRaw != null) {
                try {
                    zone.setCaptorId(UUID.fromString(captorRaw));
                } catch (IllegalArgumentException ignored) {
                }
            }

            ConfigurationSection pointsSection = data.getConfigurationSection(path + ".points");
            if (pointsSection != null) {
                for (String factionId : pointsSection.getKeys(false)) {
                    zone.setPoints(factionId, pointsSection.getInt(factionId));
                }
            }
        }

        if (data.getBoolean("running", false)) {
            plugin.getConquestManager().resumeWithoutCountdown();
        }
    }

    /** Sauvegarde synchrone immediate (utilisee a l'extinction du plugin, ou le scheduler async n'est plus disponible). */
    public void saveSync() {
        writeSnapshot(buildSnapshot());
    }

    /** Sauvegarde asynchrone (utilisee a chaud a chaque changement de score/capteur, pour ne jamais bloquer le thread principal). */
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
        data.set("running", plugin.getConquestManager().getState() == ConquestState.RUNNING);

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            String path = "zones." + zone.getName();
            data.set(path + ".locked", zone.isLocked());
            data.set(path + ".capture-seconds", zone.getCaptureSeconds());
            data.set(path + ".captor", zone.getCaptorId() != null ? zone.getCaptorId().toString() : null);
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
            plugin.getLogger().warning("[Conquest] Impossible de sauvegarder data.yml : " + e.getMessage());
        }
    }
}
