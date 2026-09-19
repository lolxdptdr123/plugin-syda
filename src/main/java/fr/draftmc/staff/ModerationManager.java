package fr.draftmc.staff;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import fr.draftmc.util.YamlFile;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanctions staff persistantes :
 * /mute [-s] &lt;joueur&gt; &lt;durée&gt; [raison]
 * /unmute [-s] &lt;joueur&gt;
 * /ban [-s] &lt;joueur&gt; [raison]
 * /tempban [-s] &lt;joueur&gt; &lt;durée&gt; [raison]
 * /unban [-s] &lt;joueur&gt;
 * -s : message visible uniquement par les admins.
 *
 * Les mutes sont écrits dans mutes.yml pour survivre à un redémarrage.
 */
public class ModerationManager implements CommandExecutor, TabCompleter, Listener {
    private static final Pattern DURATION = Pattern.compile(
            "(\\d+)\\s*(w|semaines?|weeks?|d|j|jours?|days?|h|heures?|hours?|m|mins?|minutes?|s|secs?|secondes?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IP = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");

    private final Draftmc plugin;
    private final YamlFile file;

    public ModerationManager(Draftmc plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "mutes.yml");
        pruneExpired();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if ("unban".equals(cmd)) {
            return handleUnban(sender, args);
        }
        if ("tempban".equals(cmd)) {
            return handleTempban(sender, args);
        }
        if ("ban".equals(cmd)) {
            return handleBan(sender, args);
        }
        if ("unmute".equals(cmd)) {
            return handleUnmute(sender, args);
        }
        return handleMute(sender, args);
    }

    private boolean handleMute(CommandSender sender, String[] raw) {
        ParsedArgs parsed = parseSilent(raw);
        String[] args = parsed.args;
        if (!sender.hasPermission("draftmc.staff.mute")) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (args.length < 2) {
            plugin.msg(sender, "&e/mute [-s] <joueur> <durée> [raison]");
            plugin.msg(sender, "&7Exemples: &f30m &7| &f2h &7| &f1d &7| &f1h30m &7| &fperm");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            plugin.msg(sender, "&cJoueur introuvable.");
            return true;
        }
        if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
            plugin.msg(sender, "&cTu ne peux pas te mute toi-même.");
            return true;
        }
        Player online = target.getPlayer();
        if (online != null && (online.hasPermission("draftmc.mute.bypass")
                || online.hasPermission("draftmc.admin"))) {
            plugin.msg(sender, "&cTu ne peux pas mute ce joueur.");
            return true;
        }
        long duration = parseDurationMillis(args[1]);
        if (duration == 0L) {
            plugin.msg(sender, "&cDurée invalide. Exemples: &e30s 10m 2h 1d 1w perm");
            return true;
        }
        String reason = join(args, 2);
        if (reason.isEmpty()) {
            reason = plugin.getConfig().getString("staff.mute.default-reason", "Aucune raison");
        }
        long until = duration < 0L ? -1L : System.currentTimeMillis() + duration;
        String name = displayName(target, args[0]);
        saveMute(target.getUniqueId(), name, until, reason, sender.getName());

        String time = formatRemaining(until);
        if (online != null && online.isOnline()) {
            plugin.msg(online, "&cTu es mute &e" + time + "&c. Raison: &f" + reason);
        }
        announce(parsed.silent, sender, plugin.getConfig().getString("staff.announce.mute",
                "&c{player} &7a été mute &e{time} &7par &e{staff}&7. &8({reason})")
                .replace("{player}", name).replace("{time}", time)
                .replace("{staff}", sender.getName()).replace("{reason}", reason));
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] raw) {
        ParsedArgs parsed = parseSilent(raw);
        String[] args = parsed.args;
        if (!sender.hasPermission("draftmc.staff.mute")) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (args.length < 1) {
            plugin.msg(sender, "&e/unmute [-s] <joueur>");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null) {
            plugin.msg(sender, "&cJoueur introuvable.");
            return true;
        }
        if (!isMuted(target.getUniqueId())) {
            plugin.msg(sender, "&e" + displayName(target, args[0]) + " &7n'est pas mute.");
            return true;
        }
        clearMute(target.getUniqueId());
        String name = displayName(target, args[0]);
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            plugin.msg(online, "&aTu n'es plus mute.");
        }
        announce(parsed.silent, sender, plugin.getConfig().getString("staff.announce.unmute",
                "&a{player} &7n'est plus mute &7(&e{staff}&7).")
                .replace("{player}", name).replace("{staff}", sender.getName()));
        return true;
    }

    private boolean handleTempban(CommandSender sender, String[] raw) {
        ParsedArgs parsed = parseSilent(raw);
        String[] args = parsed.args;
        if (!sender.hasPermission("draftmc.staff.ban")) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (args.length < 2) {
            plugin.msg(sender, "&e/tempban [-s] <joueur> <durée> [raison]");
            plugin.msg(sender, "&7Exemples: &f30m &7| &f2h &7| &f1d &7| &f7d");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            plugin.msg(sender, "&cJoueur introuvable.");
            return true;
        }
        if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
            plugin.msg(sender, "&cTu ne peux pas te ban toi-même.");
            return true;
        }
        Player online = target.getPlayer();
        if (online != null && (online.hasPermission("draftmc.ban.bypass")
                || online.hasPermission("draftmc.admin"))) {
            plugin.msg(sender, "&cTu ne peux pas ban ce joueur.");
            return true;
        }
        long duration = parseDurationMillis(args[1]);
        if (duration <= 0L) {
            plugin.msg(sender, "&cDurée invalide. Exemples: &e30m 2h 1d 7d");
            return true;
        }
        String reason = join(args, 2);
        if (reason.isEmpty()) {
            reason = plugin.getConfig().getString("staff.ban.default-reason", "Aucune raison");
        }
        long until = System.currentTimeMillis() + duration;
        String name = displayName(target, args[0]);
        Date expires = new Date(until);
        Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, expires, sender.getName());

        String time = formatRemaining(until);
        if (online != null && online.isOnline()) {
            online.kickPlayer(kickMessage(time, reason));
        }
        announce(parsed.silent, sender, plugin.getConfig().getString("staff.announce.tempban",
                "&c{player} &7a été banni &e{time} &7par &e{staff}&7. &8({reason})")
                .replace("{player}", name).replace("{time}", time)
                .replace("{staff}", sender.getName()).replace("{reason}", reason));
        return true;
    }

    private boolean handleBan(CommandSender sender, String[] raw) {
        ParsedArgs parsed = parseSilent(raw);
        String[] args = parsed.args;
        if (!sender.hasPermission("draftmc.staff.ban")) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (args.length < 1) {
            plugin.msg(sender, "&e/ban [-s] <joueur> [raison]");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            plugin.msg(sender, "&cJoueur introuvable.");
            return true;
        }
        if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
            plugin.msg(sender, "&cTu ne peux pas te ban toi-même.");
            return true;
        }
        Player online = target.getPlayer();
        if (online != null && (online.hasPermission("draftmc.ban.bypass")
                || online.hasPermission("draftmc.admin"))) {
            plugin.msg(sender, "&cTu ne peux pas ban ce joueur.");
            return true;
        }
        String reason = join(args, 1);
        if (reason.isEmpty()) {
            reason = plugin.getConfig().getString("staff.ban.default-reason", "Aucune raison");
        }
        String name = displayName(target, args[0]);
        Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, null, sender.getName());
        if (online != null && online.isOnline()) {
            online.kickPlayer(kickMessage("permanent", reason));
        }
        announce(parsed.silent, sender, plugin.getConfig().getString("staff.announce.ban",
                "&c{player} &7a été banni définitivement par &e{staff}&7. &8({reason})")
                .replace("{player}", name).replace("{staff}", sender.getName()).replace("{reason}", reason));
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] raw) {
        ParsedArgs parsed = parseSilent(raw);
        String[] args = parsed.args;
        if (!sender.hasPermission("draftmc.staff.unban")) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (args.length < 1) {
            plugin.msg(sender, "&e/unban [-s] <joueur|ip>");
            return true;
        }
        String input = args[0];
        BanList names = Bukkit.getBanList(BanList.Type.NAME);
        BanList ips = Bukkit.getBanList(BanList.Type.IP);
        OfflinePlayer off = Bukkit.getOfflinePlayer(input);
        String name = off.getName() != null ? off.getName() : input;

        boolean banned = names.isBanned(input) || names.isBanned(name) || off.isBanned()
                || (IP.matcher(input).matches() && ips.isBanned(input));
        if (!banned) {
            plugin.msg(sender, "&e" + input + " &7n'est pas banni.");
            return true;
        }
        names.pardon(input);
        names.pardon(name);
        if (IP.matcher(input).matches()) {
            ips.pardon(input);
        }
        announce(parsed.silent, sender, plugin.getConfig().getString("staff.announce.unban",
                "&a{player} &7n'est plus banni &7(&e{staff}&7).")
                .replace("{player}", name).replace("{staff}", sender.getName()));
        return true;
    }

    public boolean isMuted(UUID uuid) {
        Mute mute = loadMute(uuid);
        return mute != null;
    }

    private Mute loadMute(UUID uuid) {
        ConfigurationSection sec = file.get().getConfigurationSection("players." + uuid.toString());
        if (sec == null) {
            return null;
        }
        long until = sec.getLong("until", 0L);
        if (until > 0L && until <= System.currentTimeMillis()) {
            clearMute(uuid);
            return null;
        }
        return new Mute(until, sec.getString("reason", "Aucune raison"));
    }

    private void saveMute(UUID uuid, String name, long until, String reason, String by) {
        String path = "players." + uuid.toString();
        file.get().set(path + ".name", name);
        file.get().set(path + ".until", until);
        file.get().set(path + ".reason", reason);
        file.get().set(path + ".by", by);
        file.save();
    }

    private void clearMute(UUID uuid) {
        file.get().set("players." + uuid.toString(), null);
        file.save();
    }

    private void pruneExpired() {
        ConfigurationSection players = file.get().getConfigurationSection("players");
        if (players == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String key : new HashSet<String>(players.getKeys(false))) {
            long until = players.getLong(key + ".until", 0L);
            if (until > 0L && until <= now) {
                file.get().set("players." + key, null);
                changed = true;
            }
        }
        if (changed) {
            file.save();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("draftmc.mute.bypass")) {
            return;
        }
        Mute mute = loadMute(player.getUniqueId());
        if (mute == null) {
            return;
        }
        event.setCancelled(true);
        notifyMuted(player, mute);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("draftmc.mute.bypass")) {
            return;
        }
        Mute mute = loadMute(player.getUniqueId());
        if (mute == null) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        if (!blockedCommands().contains(label)) {
            return;
        }
        event.setCancelled(true);
        notifyMuted(player, mute);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.KICK_BANNED) {
            return;
        }
        BanEntry entry = Bukkit.getBanList(BanList.Type.NAME).getBanEntry(event.getPlayer().getName());
        if (entry == null) {
            return;
        }
        Date expires = entry.getExpiration();
        String time = expires == null ? "permanent" : formatRemaining(expires.getTime());
        String reason = entry.getReason() != null ? entry.getReason() : "Aucune raison";
        event.setKickMessage(kickMessage(time, reason));
    }

    private String kickMessage(String time, String reason) {
        return CC.color(plugin.getConfig().getString("staff.ban.kick-message",
                "&cTu es banni.\n&7Durée restante: &e{time}\n&7Raison: &f{reason}")
                .replace("{time}", time)
                .replace("{reason}", reason));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Mute mute = loadMute(player.getUniqueId());
        if (mute == null) {
            return;
        }
        notifyMuted(player, mute);
    }

    private void notifyMuted(final Player player, Mute mute) {
        final String time = formatRemaining(mute.until);
        final String reason = mute.reason;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                plugin.msg(player, "&cTu es mute &e" + time + "&c. Raison: &f" + reason);
            }
        });
    }

    private Set<String> blockedCommands() {
        List<String> list = plugin.getConfig().getStringList("staff.mute.blocked-commands");
        Set<String> set = new HashSet<String>();
        if (list.isEmpty()) {
            set.add("msg");
            set.add("tell");
            set.add("whisper");
            set.add("w");
            set.add("m");
            set.add("r");
            set.add("reply");
            set.add("message");
            set.add("t");
            set.add("pm");
            set.add("emsg");
            set.add("etell");
            set.add("ewhisper");
            return set;
        }
        for (String s : list) {
            set.add(s.toLowerCase(Locale.ROOT));
        }
        return set;
    }

    private void announce(boolean silent, CommandSender sender, String message) {
        String line = CC.color(plugin.prefix() + message);
        if (!silent) {
            Bukkit.broadcastMessage(line);
            return;
        }
        String silentLine = line + CC.color(" &8[-s]");
        Bukkit.getConsoleSender().sendMessage(silentLine);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("draftmc.admin")) {
                player.sendMessage(silentLine);
            }
        }
        if (sender instanceof Player && !((Player) sender).hasPermission("draftmc.admin")) {
            sender.sendMessage(silentLine);
        }
    }

    private ParsedArgs parseSilent(String[] args) {
        if (args.length > 0 && ("-s".equalsIgnoreCase(args[0]) || "-silent".equalsIgnoreCase(args[0]))) {
            String[] rest = new String[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
            return new ParsedArgs(true, rest);
        }
        return new ParsedArgs(false, args);
    }

    private void alertStaff(String message) {
        String line = CC.color(plugin.prefix() + message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("draftmc.staff")) {
                p.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    static long parseDurationMillis(String input) {
        String raw = input.toLowerCase(Locale.ROOT).trim();
        if (raw.equals("perm") || raw.equals("permanent") || raw.equals("infini")
                || raw.equals("forever") || raw.equals("-1")) {
            return -1L;
        }
        Matcher matcher = DURATION.matcher(raw);
        long total = 0L;
        boolean any = false;
        while (matcher.find()) {
            any = true;
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            long mul;
            if (unit.startsWith("w") || unit.startsWith("sem")) {
                mul = 7L * 24L * 60L * 60L * 1000L;
            } else if (unit.startsWith("d") || unit.startsWith("j") || unit.startsWith("day") || unit.startsWith("jour")) {
                mul = 24L * 60L * 60L * 1000L;
            } else if (unit.startsWith("h")) {
                mul = 60L * 60L * 1000L;
            } else if (unit.equals("m") || unit.startsWith("min")) {
                mul = 60L * 1000L;
            } else {
                mul = 1000L;
            }
            total += amount * mul;
        }
        return any ? total : 0L;
    }

    static String formatRemaining(long until) {
        if (until < 0L) {
            return "permanent";
        }
        long sec = Math.max(0L, (until - System.currentTimeMillis()) / 1000L);
        long days = sec / 86400L;
        sec %= 86400L;
        long hours = sec / 3600L;
        sec %= 3600L;
        long minutes = sec / 60L;
        sec %= 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0L) {
            sb.append(days).append("j ");
        }
        if (hours > 0L) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0L) {
            sb.append(minutes).append("m ");
        }
        if (sb.length() == 0 || (days == 0L && hours == 0L && minutes == 0L)) {
            sb.append(sec).append("s");
        }
        return sb.toString().trim();
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off;
    }

    private String displayName(OfflinePlayer player, String fallback) {
        if (player.getName() != null && !player.getName().isEmpty()) {
            return player.getName();
        }
        return fallback;
    }

    private String join(String[] args, int from) {
        if (args.length <= from) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if ("mute".equals(cmd) && !sender.hasPermission("draftmc.staff.mute")) {
            return Collections.emptyList();
        }
        if ("unmute".equals(cmd) && !sender.hasPermission("draftmc.staff.mute")) {
            return Collections.emptyList();
        }
        if ("unban".equals(cmd) && !sender.hasPermission("draftmc.staff.unban")) {
            return Collections.emptyList();
        }
        if ("tempban".equals(cmd) && !sender.hasPermission("draftmc.staff.ban")) {
            return Collections.emptyList();
        }
        if ("ban".equals(cmd) && !sender.hasPermission("draftmc.staff.ban")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<String>();
            if ("-s".startsWith(prefix) || "-silent".startsWith(prefix)) {
                names.add("-s");
            }
            if ("unban".equals(cmd)) {
                for (BanEntry entry : Bukkit.getBanList(BanList.Type.NAME).getBanEntries()) {
                    if (entry.getTarget() != null && entry.getTarget().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        names.add(entry.getTarget());
                    }
                }
                return names;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(online.getName());
                }
            }
            return names;
        }
        int offset = 0;
        if (args.length >= 1 && ("-s".equalsIgnoreCase(args[0]) || "-silent".equalsIgnoreCase(args[0]))) {
            offset = 1;
        }
        if (offset == 1 && args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<String>();
            if ("unban".equals(cmd)) {
                for (BanEntry entry : Bukkit.getBanList(BanList.Type.NAME).getBanEntries()) {
                    if (entry.getTarget() != null && entry.getTarget().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        names.add(entry.getTarget());
                    }
                }
                return names;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(online.getName());
                }
            }
            return names;
        }
        if (("mute".equals(cmd) || "tempban".equals(cmd)) && args.length == 2 + offset) {
            List<String> times = new ArrayList<String>();
            String[] opts = "tempban".equals(cmd)
                    ? new String[]{"30m", "1h", "2h", "6h", "1d", "3d", "7d", "30d"}
                    : new String[]{"30s", "10m", "30m", "1h", "2h", "1d", "7d", "perm"};
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (int i = 0; i < opts.length; i++) {
                if (opts[i].startsWith(prefix)) {
                    times.add(opts[i]);
                }
            }
            return times;
        }
        return Collections.emptyList();
    }

    private static final class ParsedArgs {
        private final boolean silent;
        private final String[] args;

        private ParsedArgs(boolean silent, String[] args) {
            this.silent = silent;
            this.args = args;
        }
    }

    private static class Mute {
        private final long until;
        private final String reason;

        private Mute(long until, String reason) {
            this.until = until;
            this.reason = reason;
        }
    }
}
