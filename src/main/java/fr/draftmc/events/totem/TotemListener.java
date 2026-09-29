package fr.draftmc.events.totem;

import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerFishEvent;
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
        Block block = event.getBlock();
        if (!plugin.getEventFactionHook().hasFaction(player)) {
            denyBreak(event, totem, player, ChatColor.RED + "Tu dois etre dans une faction pour casser le totem.");
            return;
        }
        if (!holdingRequiredItem(player, totem.getItemInteract())) {
            denyBreak(event, totem, player, ChatColor.RED + "Casse le totem avec : " + totem.getItemInteract().name() + ".");
            return;
        }
        int wait = plugin.getTotemManager().breakCooldownRemaining(player);
        if (wait > 0) {
            denyBreak(event, totem, player, plugin.getConfig().getString("messages.break-too-fast",
                    "&cTu ne peux pas casser : tu as cassé le totem trop vite.")
                    .replace("{time}", String.valueOf(wait)));
            return;
        }
        totem.playerBreak(plugin, player, block);
    }

    private void denyBreak(BlockBreakEvent event, final Totem totem, Player player, String raw) {
        event.setCancelled(true);
        player.sendMessage(plugin.prefix() + CC.color(raw));
        final Block block = event.getBlock();
        final Material type = totem.getBlockMaterial() == null ? block.getType() : totem.getBlockMaterial();
        Bukkit.getScheduler().runTask(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                if (totem.getStatus() != TotemStatus.STARTED || !totem.contains(block)) {
                    return;
                }
                block.setType(type);
            }
        });
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
    public void onEnemyPunch(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("anti-punch.enabled", true)) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        if (!(event.getDamager() instanceof Projectile) || event.getDamager() instanceof ThrownPotion) {
            return;
        }
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!plugin.getTotemManager().inAntiPunchZone(victim)
                && !plugin.getTotemManager().inAntiPunchZone(attacker)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnemyRod(PlayerFishEvent event) {
        if (!plugin.getConfig().getBoolean("anti-punch.enabled", true)) {
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY
                || !(event.getCaught() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getCaught();
        Player attacker = event.getPlayer();
        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!plugin.getTotemManager().inAntiPunchZone(victim)
                && !plugin.getTotemManager().inAntiPunchZone(attacker)) {
            return;
        }
        event.setCancelled(true);
    }

    private Player attackerOf(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        }
        if (entity instanceof Projectile) {
            Object shooter = ((Projectile) entity).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
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
