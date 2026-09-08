package fr.draftmc.events.battleroyal.managers;

import fr.draftmc.events.battleroyal.BattleRoyal;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Zone rectangulaire (definie par 2 coins) ou des effets sont appliques en
 * continu a tout joueur physiquement present a l'interieur (ex. zone de
 * spawn buffee : Force, Vitesse II, Resistance au feu, Satiete...).
 * Les coins sont persistes dans config.yml, la verification tourne toutes
 * les secondes independamment de l'etat de la partie (zone toujours active).
 */
public class BuffZoneManager {

    private final BattleRoyal plugin;
    private final List<PotionEffect> effectsTemplate = new ArrayList<>();

    public BuffZoneManager(BattleRoyal plugin) {
        this.plugin = plugin;
        loadEffects();
        Bukkit.getScheduler().runTaskTimer(plugin.getHost(), this::tick, 20L, 20L);
    }

    @SuppressWarnings("unchecked")
    public void loadEffects() {
        effectsTemplate.clear();
        List<?> raw = plugin.getConfig().getList("buff-zone.effects");
        if (raw == null) return;

        for (Object obj : raw) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> map = (Map<String, Object>) obj;
            try {
                PotionEffectType type = PotionEffectType.getByName(String.valueOf(map.get("type")).toUpperCase());
                if (type == null) {
                    plugin.getLogger().warning("Effet de buff-zone invalide ignore : " + map.get("type"));
                    continue;
                }
                int amplifier = map.get("amplifier") != null ? Integer.parseInt(String.valueOf(map.get("amplifier"))) : 0;
                // Duree courte (3s), reappliquee chaque seconde tant que le
                // joueur reste dans la zone : ca donne l'effet "permanent"
                // sans avoir a detecter precisement l'entree/sortie de zone.
                effectsTemplate.add(new PotionEffect(type, 3 * 20, amplifier, true, false));
            } catch (Exception e) {
                plugin.getLogger().warning("Effet de buff-zone invalide ignore : " + map.get("type"));
            }
        }
    }

    /** Definit un coin de la zone (index 1 ou 2) et sauvegarde dans config.yml. */
    public void setCorner(int index, Location loc) {
        String prefix = "buff-zone.pos" + index;
        plugin.getConfig().set(prefix + ".world", loc.getWorld().getName());
        plugin.getConfig().set(prefix + ".x", loc.getBlockX());
        plugin.getConfig().set(prefix + ".y", loc.getBlockY());
        plugin.getConfig().set(prefix + ".z", loc.getBlockZ());
        plugin.saveConfig();
    }

    private Location getCorner(int index) {
        String prefix = "buff-zone.pos" + index;
        String worldName = plugin.getConfig().getString(prefix + ".world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        return new Location(world,
                plugin.getConfig().getInt(prefix + ".x"),
                plugin.getConfig().getInt(prefix + ".y"),
                plugin.getConfig().getInt(prefix + ".z"));
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("buff-zone.enabled", true)) return;
        if (effectsTemplate.isEmpty()) return;

        Location pos1 = getCorner(1);
        Location pos2 = getCorner(2);
        if (pos1 == null || pos2 == null) return;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(pos1.getWorld())) continue;

            Location loc = player.getLocation();
            boolean inside = loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                    && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
                    && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;

            if (inside) {
                for (PotionEffect template : effectsTemplate) {
                    player.addPotionEffect(new PotionEffect(
                            template.getType(), template.getDuration(), template.getAmplifier(), true, false), true);
                }
            }
        }
    }
}
