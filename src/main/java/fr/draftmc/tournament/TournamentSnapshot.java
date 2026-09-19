package fr.draftmc.tournament;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public class TournamentSnapshot {
    private final Location location;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack hand;
    private final double health;
    private final int food;
    private final float saturation;
    private final GameMode gameMode;
    private final List<PotionEffect> effects = new ArrayList<PotionEffect>();
    private final boolean allowFlight;
    private final boolean flying;
    private final int fireTicks;
    private final int level;
    private final float exp;

    public TournamentSnapshot(Player player) {
        this.location = player.getLocation().clone();
        this.contents = clone(player.getInventory().getContents());
        this.armor = clone(player.getInventory().getArmorContents());
        this.hand = player.getItemInHand() == null ? null : player.getItemInHand().clone();
        this.health = player.getHealth();
        this.food = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.gameMode = player.getGameMode();
        this.effects.addAll(player.getActivePotionEffects());
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
        this.fireTicks = player.getFireTicks();
        this.level = player.getLevel();
        this.exp = player.getExp();
    }

    public void restoreFull(Player player) {
        player.getInventory().setContents(clone(contents));
        player.getInventory().setArmorContents(clone(armor));
        if (hand != null) {
            player.setItemInHand(hand.clone());
        }
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying && allowFlight);
        player.setFireTicks(fireTicks);
        player.setLevel(level);
        player.setExp(exp);
        player.setHealth(Math.min(health, player.getMaxHealth()));
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect, true);
        }
        player.teleport(location);
        player.updateInventory();
    }

    private ItemStack[] clone(ItemStack[] src) {
        if (src == null) {
            return new ItemStack[0];
        }
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] == null ? null : src[i].clone();
        }
        return out;
    }
}
