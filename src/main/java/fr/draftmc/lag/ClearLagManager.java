package fr.draftmc.lag;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Nettoie les drops / projectiles / orbes toutes les 5 minutes (configurable)
 * pour limiter le lag. Les items nommés (clés, stuff custom) sont conservés.
 */
public class ClearLagManager implements CommandExecutor {
    private final Draftmc plugin;
    private BukkitTask task;
    private int secondsLeft;

    public ClearLagManager(Draftmc plugin) {
        this.plugin = plugin;
        start();
    }

    public void start() {
        shutdown();
        if (!plugin.getConfig().getBoolean("clear-lag.enabled", true)) {
            return;
        }
        secondsLeft = interval();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("draftmc.admin")) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("now")) {
            int removed = clear();
            plugin.msg(sender, "&aClearLag forcé : &e" + removed + " &7entités.");
            return true;
        }
        plugin.msg(sender, "&e/clearlag now &7- nettoie immédiatement");
        plugin.msg(sender, "&7Prochain auto: &e" + secondsLeft + "s");
        return true;
    }

    private void tick() {
        secondsLeft--;
        if (secondsLeft <= 0) {
            clear();
            secondsLeft = interval();
            return;
        }
        if (warnSeconds().contains(secondsLeft)) {
            Bukkit.broadcastMessage(CC.color(plugin.getConfig().getString("clear-lag.warn-message",
                    "&8[&cClearLag&8] &7Nettoyage dans &e{time}&7.")
                    .replace("{time}", formatTime(secondsLeft))));
        }
    }

    private int clear() {
        Set<EntityType> types = types();
        boolean keepNamed = plugin.getConfig().getBoolean("clear-lag.keep-named-items", true);
        List<String> worlds = plugin.getConfig().getStringList("clear-lag.worlds");
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!worlds.isEmpty() && !containsIgnoreCase(worlds, world.getName())) {
                continue;
            }
            List<Entity> entities = new ArrayList<Entity>(world.getEntities());
            for (Entity entity : entities) {
                if (!types.contains(entity.getType())) {
                    continue;
                }
                if (entity.hasMetadata("draftmc-protect")) {
                    continue;
                }
                if (keepNamed && entity instanceof Item) {
                    ItemStack stack = ((Item) entity).getItemStack();
                    if (stack != null && stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                        continue;
                    }
                }
                entity.remove();
                removed++;
            }
        }
        Bukkit.broadcastMessage(CC.color(plugin.getConfig().getString("clear-lag.clear-message",
                "&8[&cClearLag&8] &e{count} &7entités supprimées.")
                .replace("{count}", String.valueOf(removed))));
        return removed;
    }

    private int interval() {
        return Math.max(30, plugin.getConfig().getInt("clear-lag.interval-seconds", 300));
    }

    private Set<Integer> warnSeconds() {
        Set<Integer> set = new HashSet<Integer>();
        for (int value : plugin.getConfig().getIntegerList("clear-lag.warn-seconds")) {
            if (value > 0) {
                set.add(value);
            }
        }
        if (set.isEmpty()) {
            set.add(60);
            set.add(30);
            set.add(10);
            set.add(5);
        }
        return set;
    }

    private Set<EntityType> types() {
        Set<EntityType> set = new HashSet<EntityType>();
        List<String> list = plugin.getConfig().getStringList("clear-lag.types");
        if (list.isEmpty()) {
            set.add(EntityType.DROPPED_ITEM);
            set.add(EntityType.ARROW);
            set.add(EntityType.EXPERIENCE_ORB);
            return set;
        }
        for (String raw : list) {
            try {
                set.add(EntityType.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return set;
    }

    private String formatTime(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String entry : list) {
            if (entry != null && entry.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
