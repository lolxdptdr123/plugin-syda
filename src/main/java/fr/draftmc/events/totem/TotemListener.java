package fr.draftmc.events.totem;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.Iterator;

public class TotemListener implements Listener {
    private final TotemPlugin plugin;

    public TotemListener(TotemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Totem totem = plugin.getTotemManager().findByBlock(event.getBlock());
        if (totem == null || totem.getStatus() != TotemStatus.STARTED) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.getEventFactionHook().hasFaction(player)) {
            player.sendMessage(ChatColor.RED + "Tu dois etre dans une faction pour casser le totem.");
            return;
        }
        if (!holdingRequiredItem(player, totem.getItemInteract())) {
            player.sendMessage(ChatColor.RED + "Casse le totem avec : " + totem.getItemInteract().name() + ".");
            return;
        }
        totem.playerBreak(plugin, player, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getTotemManager().findByBlock(event.getBlock()) != null
                || plugin.getTotemManager().findByBlock(event.getBlockAgainst()) != null) {
            Totem totem = plugin.getTotemManager().findByBlock(event.getBlock());
            if (totem == null) {
                totem = plugin.getTotemManager().findByBlock(event.getBlockAgainst());
            }
            if (totem != null && totem.getStatus() == TotemStatus.STARTED) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Totem totem = plugin.getTotemManager().findByBlock(it.next());
            if (totem != null && totem.getStatus() == TotemStatus.STARTED) {
                it.remove();
            }
        }
    }

    private boolean holdingRequiredItem(Player player, Material required) {
        if (required == null) {
            return true;
        }
        ItemStack hand = player.getItemInHand();
        return hand != null && hand.getType() == required;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPoisonDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.POISON) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (!plugin.getTotemManager().inEffectZone(player)
                || !plugin.getConfig().getBoolean("effects.block-poison", true)) {
            return;
        }
        event.setCancelled(true);
        player.removePotionEffect(PotionEffectType.POISON);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPoisonSplash(PotionSplashEvent event) {
        if (!plugin.getConfig().getBoolean("effects.block-poison", true)
                || !plugin.getTotemManager().isLaunched()
                || !isPoisonPotion(event.getPotion().getItem())) {
            return;
        }
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player && plugin.getTotemManager().inEffectZone((Player) entity)) {
                event.setIntensity(entity, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPoisonDrink(PlayerItemConsumeEvent event) {
        if (!plugin.getConfig().getBoolean("effects.block-poison", true)
                || !plugin.getTotemManager().inEffectZone(event.getPlayer())
                || !isPoisonPotion(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.prefix() + ChatColor.RED + "Le poison est interdit sur le Totem.");
    }

    private boolean isPoisonPotion(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) {
            return false;
        }
        try {
            Potion potion = Potion.fromItemStack(item);
            if (potion != null && potion.getType() == PotionType.POISON) {
                return true;
            }
        } catch (Exception ignored) {
        }
        if (item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta) {
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta.hasCustomEffects()) {
                for (PotionEffect effect : meta.getCustomEffects()) {
                    if (effect.getType().equals(PotionEffectType.POISON)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
