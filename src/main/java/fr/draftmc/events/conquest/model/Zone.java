package fr.draftmc.events.conquest.model;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represente une zone de capture Conquest : un cuboide, sa couleur/nom,
 * son etat de capture courant (qui capture, depuis combien de temps),
 * et les points de chaque faction sur CETTE zone precise.
 *
 * Toute la logique de capture elle-meme vit dans CaptureManager ; cette
 * classe ne fait que porter l'etat (elle reste volontairement "anemique"
 * pour respecter la separation des responsabilites).
 */
public class Zone {

    private final String name;
    private ChatColor color;
    private String worldName;
    private double minX, minY, minZ;
    private double maxX, maxY, maxZ;

    // faction id -> points gagnes sur CETTE zone (0 a general.zone-max-points)
    private final Map<String, Integer> factionPoints = new LinkedHashMap<>();

    // joueurs actuellement physiquement presents dans la zone
    private final Set<UUID> playersInside = new HashSet<>();

    private UUID captorId;
    private int captureSeconds;
    private boolean locked;

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

    /**
     * Convertit une position exacte en coordonnees de BLOC entier.
     * Essentiel : si on gardait les decimales de la position du joueur au
     * moment du /conquest setpos1/setpos2, la limite de la zone tombait au
     * milieu d'un bloc plutot qu'a son bord. En marchant sur ce dernier
     * bloc, on traversait cette limite invisible sans meme changer de bloc,
     * ce qui declenchait une sortie de zone a tort.
     */
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

    /** Centre de la zone, utile pour l'affichage (hologramme, particules). */
    public Location getCenter(World world) {
        return new Location(world, (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    /**
     * Teste l'appartenance a la zone par BLOC entier (inclusif sur les
     * deux bornes), pas par coordonnee exacte : un joueur qui bouge a
     * l'interieur d'un meme bloc de bordure reste considere comme "dans
     * la zone" tant qu'il ne change pas reellement de bloc.
     */
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

    /** @return true si l'ajout a fait atteindre le plafond (la zone doit alors etre verrouillee par l'appelant). */
    public boolean addPoints(String factionId, int amount, int max) {
        int newTotal = Math.min(max, getPoints(factionId) + amount);
        factionPoints.put(factionId, newTotal);
        return newTotal >= max;
    }

    /**
     * Retire des points a une faction sur cette zone (pour la penalite de
     * mort), sans jamais descendre sous zero.
     * @return le nombre de points reellement retires (peut etre inferieur a amount si le solde etait insuffisant).
     */
    public int removePoints(String factionId, int amount) {
        int current = getPoints(factionId);
        int toRemove = Math.min(current, amount);
        factionPoints.put(factionId, current - toRemove);
        return toRemove;
    }

    // ================= Etat de capture =================

    public UUID getCaptorId() {
        return captorId;
    }

    public void setCaptorId(UUID captorId) {
        this.captorId = captorId;
    }

    public int getCaptureSeconds() {
        return captureSeconds;
    }

    public void setCaptureSeconds(int captureSeconds) {
        this.captureSeconds = captureSeconds;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /**
     * Remet la zone entierement a zero (points de toutes les factions,
     * capteur, timer, verrouillage) : utilisee a l'arret de l'evenement
     * (/conquest stop, ou automatiquement a la fin d'une victoire) pour
     * qu'un nouveau Conquest reparte toujours sur une base propre.
     */
    public void reset() {
        factionPoints.clear();
        playersInside.clear();
        captorId = null;
        captureSeconds = 0;
        locked = false;
    }
}
