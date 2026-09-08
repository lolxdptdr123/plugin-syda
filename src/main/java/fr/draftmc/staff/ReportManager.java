package fr.draftmc.staff;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import fr.draftmc.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /report <joueur> <raison> : signale un joueur au staff.
 * Seuls les joueurs avec draftmc.staff voient les alertes et la liste.
 *
 * /reports          : liste les derniers reports (staff)
 * /reports clear    : vide la liste (staff)
 *
 * Stockage dans reports.yml pour que le staff hors-ligne
 * puisse les consulter à la reconnexion.
 */
public class ReportManager implements CommandExecutor, Listener {
    private final Draftmc plugin;
    private final YamlFile file;
    private final Map<UUID, Long> lastReport = new HashMap<UUID, Long>();

    public ReportManager(Draftmc plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "reports.yml");
        pruneExpired();
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                pruneExpired();
            }
        }, 20L * 60L * 30L, 20L * 60L * 30L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if ("reports".equals(cmd)) {
            handleReports(sender, args);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player reporter = (Player) sender;
        if (args.length < 2) {
            plugin.msg(reporter, "&e/report <joueur> <raison>");
            return true;
        }

        long cooldown = plugin.getConfig().getInt("staff.report.cooldown-seconds", 60) * 1000L;
        Long last = lastReport.get(reporter.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldown && !reporter.hasPermission("draftmc.staff")) {
            long remaining = (cooldown - (now - last)) / 1000L + 1;
            plugin.msg(reporter, "&cAttends &e" + remaining + "s &cavant de refaire un report.");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        String targetName = target != null ? target.getName() : args[0];
        if (targetName.equalsIgnoreCase(reporter.getName())) {
            plugin.msg(reporter, "&cTu ne peux pas te report toi-même.");
            return true;
        }
        if (target == null) {
            plugin.msg(reporter, "&7Le joueur &e" + targetName + " &7est hors-ligne, le report est quand même transmis.");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        String reason = sb.toString();
        if (reason.length() > 120) {
            reason = reason.substring(0, 120);
        }

        lastReport.put(reporter.getUniqueId(), now);
        store(reporter.getName(), targetName, reason, now);
        alertStaff(reporter.getName(), targetName, reason);
        plugin.msg(reporter, "&aReport envoyé au staff. Merci !");
        return true;
    }

    private void handleReports(CommandSender sender, String[] args) {
        if (!sender.hasPermission("draftmc.staff")) {
            plugin.msg(sender, "&cPas la permission.");
            return;
        }
        if (args.length > 0 && "clear".equalsIgnoreCase(args[0])) {
            file.get().set("reports", null);
            file.save();
            plugin.msg(sender, "&aReports supprimés.");
            return;
        }
        pruneExpired();
        List<Map<?, ?>> reports = file.get().getMapList("reports");
        if (reports.isEmpty()) {
            plugin.msg(sender, "&7Aucun report.");
            return;
        }
        int page = 1;
        if (args.length > 0) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {
            }
        }
        int perPage = 10;
        int pages = (reports.size() + perPage - 1) / perPage;
        page = Math.min(page, pages);
        plugin.msg(sender, "&6Reports &7(page " + page + "/" + pages + ", " + reports.size() + " au total)");
        SimpleDateFormat format = new SimpleDateFormat("dd/MM HH:mm");
        // Les plus récents d'abord.
        int start = reports.size() - 1 - (page - 1) * perPage;
        int end = Math.max(0, start - perPage + 1);
        for (int i = start; i >= end; i--) {
            Map<?, ?> entry = reports.get(i);
            Object time = entry.get("time");
            long ts = time instanceof Number ? ((Number) time).longValue() : 0L;
            sender.sendMessage(CC.color("&8[&7" + format.format(new Date(ts)) + "&8] &e"
                    + entry.get("reporter") + " &7» &c" + entry.get("target")
                    + " &8: &f" + entry.get("reason")));
        }
        if (page < pages) {
            plugin.msg(sender, "&7Page suivante: &e/reports " + (page + 1));
        }
    }

    private long expireMillis() {
        int hours = Math.max(1, plugin.getConfig().getInt("staff.report.expire-hours", 24));
        return hours * 60L * 60L * 1000L;
    }

    private long entryTime(Map<?, ?> entry) {
        Object time = entry.get("time");
        return time instanceof Number ? ((Number) time).longValue() : 0L;
    }

    private java.util.List<Map<String, Object>> copyReports() {
        java.util.List<Map<String, Object>> updated = new java.util.ArrayList<Map<String, Object>>();
        long cutoff = System.currentTimeMillis() - expireMillis();
        for (Map<?, ?> entry : file.get().getMapList("reports")) {
            if (entryTime(entry) < cutoff) {
                continue;
            }
            Map<String, Object> copy = new HashMap<String, Object>();
            for (Map.Entry<?, ?> e : entry.entrySet()) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
            updated.add(copy);
        }
        return updated;
    }

    private void pruneExpired() {
        List<Map<?, ?>> reports = file.get().getMapList("reports");
        java.util.List<Map<String, Object>> kept = copyReports();
        if (kept.size() == reports.size()) {
            return;
        }
        file.get().set("reports", kept.isEmpty() ? null : kept);
        file.save();
    }

    private void store(String reporter, String target, String reason, long time) {
        java.util.List<Map<String, Object>> updated = copyReports();
        Map<String, Object> entry = new HashMap<String, Object>();
        entry.put("reporter", reporter);
        entry.put("target", target);
        entry.put("reason", reason);
        entry.put("time", time);
        updated.add(entry);
        int max = Math.max(10, plugin.getConfig().getInt("staff.report.max-stored", 100));
        while (updated.size() > max) {
            updated.remove(0);
        }
        file.get().set("reports", updated);
        file.save();
    }

    private void alertStaff(String reporter, String target, String reason) {
        String line = plugin.getConfig().getString("staff.report.alert",
                "&8[&cReport&8] &e%reporter% &7a signalé &c%target% &7» &f%reason%");
        line = CC.color(line.replace("%reporter%", reporter)
                .replace("%target%", target)
                .replace("%reason%", reason));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("draftmc.staff")) {
                p.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    /** Rappelle au staff qui se connecte qu'il y a des reports en attente. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("draftmc.staff")) {
            return;
        }
        pruneExpired();
        int count = file.get().getMapList("reports").size();
        if (count > 0) {
            plugin.msg(player, "&e" + count + " report(s) en attente. &7Consulte-les avec &e/reports&7.");
        }
    }
}
