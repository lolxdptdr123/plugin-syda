package fr.draftmc.staff;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import fr.draftmc.util.Cooldowns;
import fr.draftmc.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Items donnés en mode /staff : RTP random, freeze, CPS.
 */
public class StaffItems implements Listener {
    public static final String SID_RTP = "STAFF_RTP";
    public static final String SID_FREEZE = "STAFF_FREEZE";
    public static final String SID_CPS = "STAFF_CPS";

    private final Draftmc plugin;
    private final Random random = new Random();

    public StaffItems(Draftmc plugin) {
        this.plugin = plugin;
    }

    public void give(Player player) {
        if (!plugin.getConfig().getBoolean("staff.items.enabled", true)) {
            return;
        }
        player.getInventory().setItem(0, rtpItem());
        player.getInventory().setItem(1, freezeItem());
        player.getInventory().setItem(2, cpsItem());
        player.updateInventory();
    }

    private ItemStack rtpItem() {
        ItemStack item = new ItemBuilder(Material.COMPASS)
                .name("&b&lRandom TP")
                .lore("&7Clic : téléporte sur un joueur", "&7aléatoire en ligne.")
                .build();
        return Cooldowns.tagSid(item, SID_RTP);
    }

    private ItemStack freezeItem() {
        ItemStack item = new ItemBuilder(Material.STICK)
                .name("&b&lBâton Freeze")
                .lore("&7Clic sur un joueur :", "&7gèle / dégèle.")
                .build();
        return Cooldowns.tagSid(item, SID_FREEZE);
    }

    private ItemStack cpsItem() {
        ItemStack item = new ItemBuilder(Material.BLAZE_ROD)
                .name("&6&lCPS Checker")
                .lore("&7Clic sur un joueur :", "&7affiche ses CPS (5 dernières secondes).")
                .build();
        return Cooldowns.tagSid(item, SID_CPS);
    }

    private String sidOf(ItemStack item) {
        return Cooldowns.sid(item);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) {
            return;
        }
        Player staff = event.getPlayer();
        if (plugin.staff() == null || !plugin.staff().isStaff(staff)) {
            return;
        }
        ItemStack hand = staff.getItemInHand();
        String sid = sidOf(hand);
        if (sid == null) {
            return;
        }
        Player target = (Player) event.getRightClicked();
        if (SID_FREEZE.equals(sid)) {
            event.setCancelled(true);
            if (!staff.hasPermission("draftmc.staff.freeze")) {
                plugin.msg(staff, "&cPas la permission freeze.");
                return;
            }
            boolean frozen = plugin.freeze().toggleFreeze(target, staff.getName());
            plugin.msg(staff, frozen
                    ? "&c" + target.getName() + " &7gelé."
                    : "&a" + target.getName() + " &7dégelé.");
        } else if (SID_CPS.equals(sid)) {
            event.setCancelled(true);
            showCps(staff, target);
        } else if (SID_RTP.equals(sid)) {
            event.setCancelled(true);
            // Clic sur un joueur précis = TP dessus
            staff.teleport(target.getLocation());
            plugin.msg(staff, "&aTéléporté sur &e" + target.getName() + "&a.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) {
            return;
        }
        Player staff = (Player) event.getDamager();
        if (plugin.staff() == null || !plugin.staff().isStaff(staff)) {
            return;
        }
        String sid = sidOf(staff.getItemInHand());
        if (sid == null) {
            return;
        }
        Player target = (Player) event.getEntity();
        if (SID_FREEZE.equals(sid) || SID_CPS.equals(sid) || SID_RTP.equals(sid)) {
            event.setCancelled(true);
            if (SID_FREEZE.equals(sid)) {
                if (!staff.hasPermission("draftmc.staff.freeze")) {
                    plugin.msg(staff, "&cPas la permission freeze.");
                    return;
                }
                boolean frozen = plugin.freeze().toggleFreeze(target, staff.getName());
                plugin.msg(staff, frozen
                        ? "&c" + target.getName() + " &7gelé."
                        : "&a" + target.getName() + " &7dégelé.");
            } else if (SID_CPS.equals(sid)) {
                showCps(staff, target);
            } else {
                staff.teleport(target.getLocation());
                plugin.msg(staff, "&aTéléporté sur &e" + target.getName() + "&a.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player staff = event.getPlayer();
        if (plugin.staff() == null || !plugin.staff().isStaff(staff)) {
            return;
        }
        ItemStack hand = staff.getItemInHand();
        if (!SID_RTP.equals(sidOf(hand))) {
            return;
        }
        // Clic air/bloc = joueur aléatoire
        if (action == Action.RIGHT_CLICK_AIR || action == Action.LEFT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            randomTp(staff);
        }
    }

    private void randomTp(Player staff) {
        List<Player> candidates = new ArrayList<Player>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(staff.getUniqueId())) {
                continue;
            }
            if (plugin.staff().isStaff(online)) {
                continue;
            }
            candidates.add(online);
        }
        if (candidates.isEmpty()) {
            plugin.msg(staff, "&cAucun joueur disponible.");
            return;
        }
        Player target = candidates.get(random.nextInt(candidates.size()));
        staff.teleport(target.getLocation());
        plugin.msg(staff, "&aRandom TP sur &e" + target.getName() + "&a.");
    }

    private void showCps(Player staff, Player target) {
        int window = plugin.getConfig().getInt("staff.cps-window-seconds", 5);
        double cps = plugin.staff().cps(target.getUniqueId());
        plugin.msg(staff, "&eCPS de &6" + target.getName() + " &7» &6"
                + String.format("%.1f", cps) + " &7(moyenne " + window + "s)");
    }
}
