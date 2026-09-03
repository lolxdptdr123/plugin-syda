package fr.sydaria.core;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sauvegarde le stuff du tueur au moment du kill et affiche un lien cliquable
 * [InventoryView] dans le chat pour le consulter plus tard.
 * Utilise /tellraw (JSON) — compatible Spigot 1.8.8 sans dépendance BungeeCord.
 */
public class DeathInventoryManager implements Listener, CommandExecutor {
    private static final String PREVIEW_PREFIX = CC.color("&8Stuff de ");

    private final Sydaria plugin;
    private final Map<Integer, DeathSnapshot> snapshots = new HashMap<Integer, DeathSnapshot>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public DeathInventoryManager(Sydaria plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                cleanupExpired();
            }
        }, 20L * 60L, 20L * 60L);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("core.death-inventory-preview", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!isEnabled()) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }

        int id = nextId.getAndIncrement();
        long expireAt = System.currentTimeMillis() + getExpireMillis();
        DeathSnapshot snapshot = new DeathSnapshot(id, killer.getName(), CapturedInventory.from(killer), expireAt);
        snapshots.put(id, snapshot);

        if (plugin.getConfig().getBoolean("core.death-inventory.replace-vanilla-message", true)) {
            event.setDeathMessage(null);
        }

        sendDeathMessage(killer, victim, snapshot);
    }

    private void sendDeathMessage(Player killer, Player victim, DeathSnapshot snapshot) {
        try {
            String weaponSuffix = weaponSuffix(killer);
            String line = plugin.getConfig().getString("core.death-inventory.message",
                    "&e%victim% &7a ete tue par &c%killer%%weapon%&7.");
            line = line.replace("%victim%", victim.getName())
                    .replace("%killer%", killer.getName())
                    .replace("%weapon%", weaponSuffix);

            String linkText = plugin.getConfig().getString("core.death-inventory.link-text", "&7[&fInventoryView&7]");
            String hover = plugin.getConfig().getString("core.death-inventory.hover",
                    "&eClique pour voir le stuff de &c%killer%&e.");
            hover = hover.replace("%killer%", killer.getName())
                    .replace("%victim%", victim.getName());

            String json = buildTellraw(CC.color(line), CC.color(linkText), CC.color(hover),
                    "/deathinv " + snapshot.id);

            for (Player online : Bukkit.getOnlinePlayers()) {
                sendTellraw(online, json);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("DeathInventoryManager: impossible d'envoyer le message cliquable: " + t.getMessage());
            Bukkit.broadcastMessage(CC.color(plugin.prefix() + "&e" + victim.getName() + " &7a ete tue par &c"
                    + killer.getName() + "&7. &7Utilise &e/deathinv " + snapshot.id + " &7pour voir le stuff du tueur."));
        }
    }

    private String buildTellraw(String message, String linkText, String hover, String command) {
        return "{\"text\":\"" + escapeJson(message) + " \",\"extra\":[{\"text\":\""
                + escapeJson(linkText) + "\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\""
                + escapeJson(command) + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\""
                + escapeJson(hover) + "\"}}]}";
    }

    private void sendTellraw(Player player, String json) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String weaponSuffix(Player killer) {
        if (!plugin.getConfig().getBoolean("core.death-inventory.show-weapon", true)) {
            return "";
        }
        ItemStack weapon = killer.getItemInHand();
        if (weapon == null || weapon.getType() == Material.AIR) {
            return "";
        }
        String name;
        if (weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName()) {
            name = ChatColor.stripColor(weapon.getItemMeta().getDisplayName());
        } else {
            name = formatMaterial(weapon.getType().name());
        }
        return " &7(" + name + ")";
    }

    private String formatMaterial(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (lower.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        if (!isEnabled()) {
            plugin.msg((Player) sender, "&cLa consultation d'inventaire est desactivee.");
            return true;
        }
        if (args.length < 1) {
            return true;
        }

        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            plugin.msg((Player) sender, "&cInventaire introuvable.");
            return true;
        }

        openSnapshot((Player) sender, id);
        return true;
    }

    public void openSnapshot(Player viewer, int id) {
        DeathSnapshot snapshot = snapshots.get(id);
        if (snapshot == null || snapshot.isExpired()) {
            snapshots.remove(id);
            plugin.msg(viewer, "&cCet inventaire n'est plus disponible.");
            return;
        }
        viewer.openInventory(snapshot.buildInventory());
    }

    @EventHandler
    public void onPreviewClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title != null && title.startsWith(PREVIEW_PREFIX)) {
            event.setCancelled(true);
        }
    }

    private long getExpireMillis() {
        int minutes = plugin.getConfig().getInt("core.death-inventory.expire-minutes", 10);
        return Math.max(1, minutes) * 60L * 1000L;
    }

    private void cleanupExpired() {
        Iterator<Map.Entry<Integer, DeathSnapshot>> it = snapshots.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
            }
        }
    }

    private static final class CapturedInventory {
        private final ItemStack[] contents;
        private final ItemStack[] armor;

        private CapturedInventory(ItemStack[] contents, ItemStack[] armor) {
            this.contents = contents;
            this.armor = armor;
        }

        static CapturedInventory from(Player player) {
            return new CapturedInventory(cloneArray(player.getInventory().getContents(), 36),
                    cloneArray(player.getInventory().getArmorContents(), 4));
        }

        private static ItemStack[] cloneArray(ItemStack[] src, int max) {
            ItemStack[] out = new ItemStack[max];
            for (int i = 0; i < max && i < src.length; i++) {
                out[i] = src[i] == null ? null : src[i].clone();
            }
            return out;
        }
    }

    private static final class DeathSnapshot {
        private final int id;
        private final String ownerName;
        private final CapturedInventory inventory;
        private final long expireAt;

        private DeathSnapshot(int id, String ownerName, CapturedInventory inventory, long expireAt) {
            this.id = id;
            this.ownerName = ownerName;
            this.inventory = inventory;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }

        Inventory buildInventory() {
            Inventory preview = Bukkit.createInventory(null, 54, PREVIEW_PREFIX + ownerName);
            preview.setContents(inventory.contents);
            for (int i = 0; i < inventory.armor.length; i++) {
                preview.setItem(45 + i, inventory.armor[i]);
            }
            return preview;
        }
    }
}
