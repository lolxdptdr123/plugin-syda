package fr.draftmc.util;

import fr.draftmc.Draftmc;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ActionLogs {
    private static final Map<String, YamlFile> FILES = new HashMap<String, YamlFile>();
    private static final int MAX = 400;

    private ActionLogs() {
    }

    public static void append(Draftmc plugin, String fileName, String tag, String text) {
        String line = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(new Date())
                + " | " + text;
        plugin.getLogger().info("[" + tag + "] " + text);
        YamlFile file = file(plugin, fileName);
        List<String> logs = file.get().getStringList("logs");
        logs.add(line);
        if (logs.size() > MAX) {
            logs = new ArrayList<String>(logs.subList(logs.size() - MAX, logs.size()));
        }
        file.get().set("logs", logs);
        file.save();
    }

    public static void notifyStaff(Draftmc plugin, String message) {
        if (plugin.grades() == null) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.grades().isOwnerOrAdmin(online)) {
                plugin.msg(online, message);
            }
        }
    }

    public static void show(Draftmc plugin, CommandSender sender, String fileName, String title) {
        List<String> logs = file(plugin, fileName).get().getStringList("logs");
        plugin.msg(sender, "&6" + title);
        if (logs.isEmpty()) {
            plugin.msg(sender, "&7Aucun log.");
            return;
        }
        int from = Math.max(0, logs.size() - 15);
        for (int i = from; i < logs.size(); i++) {
            plugin.msg(sender, "&7" + logs.get(i));
        }
    }

    public static String itemLabel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "air";
        }
        String name = item.getType().name();
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                name = ChatColor.stripColor(meta.getDisplayName());
            }
        }
        return item.getAmount() + "x " + name;
    }

    private static YamlFile file(Draftmc plugin, String fileName) {
        YamlFile existing = FILES.get(fileName);
        if (existing == null) {
            existing = new YamlFile(plugin, fileName);
            FILES.put(fileName, existing);
        }
        return existing;
    }
}
