package fr.draftmc.events.domination.model;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represente une zone de controle Domination : un cuboide, sa couleur/nom,
 * les joueurs actuellement presents, et les points de chaque faction sur
 * CETTE zone precise.
 *
 * Contrairement a Conquest, il n'y a PAS de notion de "capteur unique" :
 * n'importe quelle faction ayant au moins un membre present marque des
 * points, simultanement avec les autres factions presentes. Toute la
 * logique de score vit dans ScoringManager ; cette classe ne fait que
 * porter l'etat.
 */
public class Zone {

    private final String name;
    private ChatColor color;
    private String worldName;
    private double minX, minY, minZ;
    private double maxX, maxY, maxZ;

    // faction id -> points gagnes sur CETTE zone
    private final Map<String, Integer> factionPoints = new LinkedHashMap<>();

    // joueurs actuellement physiquement presents dans la zone
    private final Set<UUID> playersInside = new HashSet<>();

    public Zone(String name, ChatColor color, String worldName) {
        this.name = name;
        this.color = color;
        this.worldName = worldName;
    }

    // ================= Identite =================

    public String getName() {
        return name;
    }

    public ChatColor getColor() {
        return color;
    }

    public void setColor(ChatColor color) {
        this.color = color;
    }

    public String getDisplayName() {
        return color + name;
    }

    // ================= Positions =================

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public void setCorner1(Location loc) {
        this.worldName = loc.getWorld().getName();
        recomputeBounds(blockLocation(loc), new Location(loc.getWorld(), maxX, maxY, maxZ));
    }

    public void setCorner2(Location loc) {
        this.worldName = loc.getWorld().getName();
        recomputeBounds(new Location(loc.getWorld(), minX, minY, minZ), blockLocation(loc));
    }

    /** Convertit une position exacte en coordonnees de BLOC entier (evite les limites de zone au milieu d'un bloc). */
    private Location blockLocation(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private void recomputeBounds(Location a, Location b) {
        this.minX = Math.min(a.getX(), b.getX());
        this.minY = Math.min(a.getY(), b.getY());
        this.minZ = Math.min(a.getZ(), b.getZ());
        this.maxX = Math.max(a.getX(), b.getX());
        this.maxY = Math.max(a.getY(), b.getY());
        this.maxZ = Math.max(a.getZ(), b.getZ());
    }

    public void setBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getMaxZ() { return maxZ; }

    public Location getCenter(World world) {
        return new Location(world, (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    /** Teste l'appartenance a la zone par BLOC entier (inclusif sur les deux bornes). */
    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    // ================= Joueurs presents =================

    public Set<UUID> getPlayersInside() {
        return playersInside;
    }

    // ================= Points =================

    public int getPoints(String factionId) {
        Integer value = factionPoints.get(factionId);
        return value == null ? 0 : value;
    }

    public Map<String, Integer> getAllPoints() {
        return factionPoints;
    }

    public void setPoints(String factionId, int amount) {
        factionPoints.put(factionId, amount);
    }

    public void addPoints(String factionId, int amount) {
        factionPoints.put(factionId, getPoints(factionId) + amount);
    }

    /**
     * Retire des points a une faction sur cette zone (penalite de mort),
     * sans jamais descendre sous zero.
     * @return le nombre de points reellement retires.
     */
    public int removePoints(String factionId, int amount) {
        int current = getPoints(factionId);
        int toRemove = Math.min(current, amount);
        factionPoints.put(factionId, current - toRemove);
        return toRemove;
    }

    /** Remet la zone entierement a zero (points de toutes les factions) : utilise a l'arret de l'evenement. */
    public void reset() {
        factionPoints.clear();
        playersInside.clear();
    }
}
