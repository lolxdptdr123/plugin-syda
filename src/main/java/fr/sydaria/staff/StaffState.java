package fr.sydaria.staff;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/**
 * Photo de l'état d'un joueur au moment où il active le mode staff.
 * Tout ce qui est restauré à la désactivation vit ici, en un seul endroit,
 * pour qu'il soit impossible d'oublier un champ entre la sauvegarde et la
 * restauration (les deux passent par le même objet).
 *
 * Volontairement immuable : une fois pris, un snapshot ne doit plus bouger.
 */
public class StaffState {
    private final ItemStack[] inventory;
    private final ItemStack[] armor;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exp;
    private final int level;
    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean flying;
    private final float walkSpeed;
    private final float flySpeed;
    private final Location location;

    public StaffState(ItemStack[] inventory, ItemStack[] armor, double health, int foodLevel, float saturation,
                       float exp, int level, GameMode gameMode, boolean allowFlight, boolean flying,
                       float walkSpeed, float flySpeed, Location location) {
        this.inventory = inventory;
        this.armor = armor;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exp = exp;
        this.level = level;
        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.walkSpeed = walkSpeed;
        this.flySpeed = flySpeed;
        this.location = location;
    }

    public ItemStack[] inventory() { return inventory; }
    public ItemStack[] armor() { return armor; }
    public double health() { return health; }
    public int foodLevel() { return foodLevel; }
    public float saturation() { return saturation; }
    public float exp() { return exp; }
    public int level() { return level; }
    public GameMode gameMode() { return gameMode; }
    public boolean allowFlight() { return allowFlight; }
    public boolean flying() { return flying; }
    public float walkSpeed() { return walkSpeed; }
    public float flySpeed() { return flySpeed; }
    public Location location() { return location; }
}
