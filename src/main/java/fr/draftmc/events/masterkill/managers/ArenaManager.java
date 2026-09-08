package fr.draftmc.events.masterkill.managers;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prepare l'arene a partir d'un cuboide (deux coins : /masterkill setpos1
 * et setpos2, comme les zones de Conquest) et teleporte les equipes au
 * lancement du match.
 *
 * Deux modes de placement des equipes, dans cet ordre de priorite :
 *  1) Points de spawn MANUELS (/masterkill addspawn), un par equipe, dans
 *     l'ordre ou ils ont ete ajoutes - utile pour une arene avec des bases
 *     deja construites a des endroits precis.
 *  2) A defaut (aucun point manuel defini), repartition AUTOMATIQUE en
 *     cercle autour du centre du cuboide - meme principe que DraftRoyale.
 */
public class ArenaManager {

    private final MasterKillPlugin plugin;
    private Location center;
    private double sizeX;
    private double sizeZ;

    public ArenaManager(MasterKillPlugin plugin) {
        this.plugin = plugin;
    }

    /** Definit un coin de l'arene (index 1 ou 2) et sauvegarde dans config.yml. */
    public void setCorner(int index, Location loc) {
        String prefix = "arena.pos" + index;
        plugin.getConfig().set(prefix + ".world", loc.getWorld().getName());
        plugin.getConfig().set(prefix + ".x", loc.getBlockX());
        plugin.getConfig().set(prefix + ".y", loc.getBlockY());
        plugin.getConfig().set(prefix + ".z", loc.getBlockZ());
        plugin.saveConfig();
    }

    // ================= Points de spawn manuels (un par equipe) =================

    /** Ajoute un point de spawn a la position du joueur, a la suite de la liste existante. */
    public int addSpawnPoint(Location loc) {
        List<Map<String, Object>> spawns = readRawSpawnList();

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("world", loc.getWorld().getName());
        entry.put("x", loc.getX());
        entry.put("y", loc.getY());
        entry.put("z", loc.getZ());
        entry.put("yaw", (double) loc.getYaw());
        entry.put("pitch", (double) loc.getPitch());
        spawns.add(entry);

        plugin.getConfig().set("arena.spawns", spawns);
        plugin.saveConfig();
        return spawns.size();
    }

    /** Supprime tous les points de spawn manuels : l'arene retombe sur la repartition automatique en cercle. */
    public void clearSpawnPoints() {
        plugin.getConfig().set("arena.spawns", new ArrayList<>());
        plugin.saveConfig();
    }

    public int getSpawnPointCount() {
        return plugin.getConfig().getMapList("arena.spawns").size();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readRawSpawnList() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("arena.spawns")) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
            result.add(copy);
        }
        return result;
    }

    private List<Location> readSpawnPoints() {
        List<Location> result = new ArrayList<>();
        for (Map<String, Object> m : readRawSpawnList()) {
            String worldName = String.valueOf(m.get("world"));
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            double x = toDouble(m.get("x"));
            double y = toDouble(m.get("y"));
            double z = toDouble(m.get("z"));
            float yaw = (float) toDouble(m.get("yaw"));
            float pitch = (float) toDouble(m.get("pitch"));
            result.add(new Location(world, x, y, z, yaw, pitch));
        }
        return result;
    }

    private double toDouble(Object o) {
        return o == null ? 0 : Double.parseDouble(String.valueOf(o));
    }

    // ================= Preparation / teleportation =================

    /** @return true si l'arene a pu etre preparee (les deux coins sont definis et valides). */
    public boolean prepareArena() {
        Location pos1 = readCorner(1);
        Location pos2 = readCorner(2);

        if (pos1 == null || pos2 == null) {
            plugin.getLogger().warning("[MasterKill] L'arene n'est pas definie : utilise /masterkill setpos1 et /masterkill setpos2 en jeu.");
            return false;
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            plugin.getLogger().warning("[MasterKill] setpos1 et setpos2 doivent etre dans le meme monde.");
            return false;
        }

        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());

        sizeX = maxX - minX;
        sizeZ = maxZ - minZ;
        center = new Location(pos1.getWorld(), (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);

        if (sizeX <= 0 && sizeZ <= 0) {
            plugin.getLogger().warning("[MasterKill] L'arene semble non configuree (pos1 et pos2 identiques). "
                    + "Utilise /masterkill setpos1 et /masterkill setpos2 a deux endroits differents.");
            return false;
        }

        if (plugin.getConfig().getBoolean("arena.use-border", true)) {
            // La bordure de monde est carree (une seule taille pour X et Z) :
            // on prend la plus grande des deux dimensions pour etre sur que
            // le cuboide entier reste bien a l'interieur.
            double borderSize = Math.max(sizeX, sizeZ);
            WorldBorder border = center.getWorld().getWorldBorder();
            border.setCenter(center);
            border.setSize(borderSize);
        }
        return true;
    }

    private Location readCorner(int index) {
        String prefix = "arena.pos" + index;
        String worldName = plugin.getConfig().getString(prefix + ".world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        return new Location(world,
                plugin.getConfig().getInt(prefix + ".x"),
                plugin.getConfig().getInt(prefix + ".y"),
                plugin.getConfig().getInt(prefix + ".z"));
    }

    public Location getCenter() {
        return center;
    }

    /**
     * Teleporte chaque equipe au lancement du match. Utilise en priorite
     * les points de spawn manuels (/masterkill addspawn), un par equipe
     * dans l'ordre d'ajout (avec bouclage s'il y a plus d'equipes que de
     * points) ; a defaut, repartition automatique en cercle autour du
     * centre du cuboide setpos1/setpos2.
     */
    public void teleportTeams(Collection<Team> teams) {
        List<Team> teamList = new ArrayList<>(teams);
        boolean kitEnabled = plugin.getConfig().getBoolean("kit.enabled", false);

        List<Location> manualSpawns = readSpawnPoints();
        if (!manualSpawns.isEmpty()) {
            for (int i = 0; i < teamList.size(); i++) {
                Location spawn = manualSpawns.get(i % manualSpawns.size());
                teleportTeamMembers(teamList.get(i), spawn, kitEnabled);
            }
            return;
        }

        if (center == null) return;

        // Rayon base sur la plus PETITE dimension pour rester bien a
        // l'interieur du cuboide defini par setpos1/setpos2.
        double radius = Math.min(sizeX, sizeZ) / 2.5;
        int n = teamList.size();

        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI / n) * i;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location spawn = new Location(center.getWorld(), x, center.getY(), z);
            spawn.setY(center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1);

            teleportTeamMembers(teamList.get(i), spawn, kitEnabled);
        }
    }

    private void teleportTeamMembers(Team team, Location spawn, boolean kitEnabled) {
        for (UUID uuid : team.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            p.teleport(spawn);
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.setFireTicks(0);
            for (PotionEffect effect : new ArrayList<>(p.getActivePotionEffects())) {
                p.removePotionEffect(effect.getType());
            }

            if (kitEnabled) {
                plugin.getKitManager().giveKit(p);
            }
        }
    }
}
