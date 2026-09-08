package fr.draftmc.events.conquest.managers;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gere le cycle de vie des zones : creation/suppression/positions,
 * chargement et sauvegarde dans config.yml (section "zones" ou
 * "maps.<id>.zones" selon la map active).
 */
public class ZoneManager {

    private final ConquestPlugin plugin;
    private final Map<String, Zone> zones = new LinkedHashMap<String, Zone>();
    private String zonesPath = "zones";

    public ZoneManager(ConquestPlugin plugin) {
        this.plugin = plugin;
        loadZones();
    }

    public void loadForMap(String mapId, String worldOverride) {
        String path = "maps." + mapId + ".zones";
        if (plugin.getConfig().isConfigurationSection(path)) {
            zonesPath = path;
        } else {
            zonesPath = "zones";
        }
        loadZones(worldOverride);
    }

    public void loadZones() {
        loadZones(null);
    }

    private void loadZones(String worldOverride) {
        zones.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(zonesPath);
        if (section == null) {
            return;
        }

        for (String name : section.getKeys(false)) {
            ConfigurationSection zoneSection = section.getConfigurationSection(name);
            if (zoneSection == null) {
                continue;
            }

            ChatColor color;
            try {
                color = ChatColor.valueOf(zoneSection.getString("color", "WHITE").toUpperCase());
            } catch (IllegalArgumentException e) {
                color = ChatColor.WHITE;
            }
            String fallbackWorld = worldOverride != null ? worldOverride
                    : plugin.getConfig().getString("general.world", "world");
            String world = worldOverride != null ? worldOverride : zoneSection.getString("world", fallbackWorld);

            Zone zone = new Zone(name, color, world);
            double x1 = zoneSection.getDouble("pos1.x");
            double y1 = zoneSection.getDouble("pos1.y");
            double z1 = zoneSection.getDouble("pos1.z");
            double x2 = zoneSection.getDouble("pos2.x");
            double y2 = zoneSection.getDouble("pos2.y");
            double z2 = zoneSection.getDouble("pos2.z");
            zone.setBounds(
                    Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
            );
            zones.put(name.toLowerCase(), zone);
        }
    }

    public void saveZones() {
        plugin.getConfig().set(zonesPath, null);
        for (Zone zone : zones.values()) {
            String path = zonesPath + "." + zone.getName();
            plugin.getConfig().set(path + ".color", zone.getColor().name());
            plugin.getConfig().set(path + ".world", zone.getWorldName());
            plugin.getConfig().set(path + ".pos1.x", zone.getMinX());
            plugin.getConfig().set(path + ".pos1.y", zone.getMinY());
            plugin.getConfig().set(path + ".pos1.z", zone.getMinZ());
            plugin.getConfig().set(path + ".pos2.x", zone.getMaxX());
            plugin.getConfig().set(path + ".pos2.y", zone.getMaxY());
            plugin.getConfig().set(path + ".pos2.z", zone.getMaxZ());
        }
        plugin.saveConfig();
    }

    public boolean createZone(String name, ChatColor color) {
        String key = name.toLowerCase();
        if (zones.containsKey(key)) {
            return false;
        }
        String world = plugin.getConfig().getString("general.world", "world");
        zones.put(key, new Zone(name, color, world));
        saveZones();
        return true;
    }

    public boolean deleteZone(String name) {
        boolean removed = zones.remove(name.toLowerCase()) != null;
        if (removed) {
            saveZones();
        }
        return removed;
    }

    public Zone getZone(String name) {
        return zones.get(name.toLowerCase());
    }

    public Map<String, Zone> getZones() {
        return zones;
    }

    public Zone getZoneAt(Location location) {
        for (Zone zone : zones.values()) {
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }

    public boolean setPos1(String name, Location loc) {
        Zone zone = getZone(name);
        if (zone == null) {
            return false;
        }
        zone.setCorner1(loc);
        saveZones();
        return true;
    }

    public boolean setPos2(String name, Location loc) {
        Zone zone = getZone(name);
        if (zone == null) {
            return false;
        }
        zone.setCorner2(loc);
        saveZones();
        return true;
    }
}
