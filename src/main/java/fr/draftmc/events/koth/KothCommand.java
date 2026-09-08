package fr.draftmc.events.koth;

import fr.draftmc.util.CC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KothCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = Arrays.asList(
            "help", "pos1", "pos2", "create", "delete", "forcestart", "forcestop", "schedule", "list", "show", "reload");
    private static final List<String> DAYS = Arrays.asList(
            "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche");

    private final KothPlugin plugin;

    public KothCommand(KothPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("koth.admin") && !sender.hasPermission("draftmc.admin")) {
            sender.sendMessage(CC.color("&cPas la permission."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("pos1") || sub.equals("pos2")) {
            return handlePos(sender, sub.equals("pos1") ? 1 : 2);
        }
        if (sub.equals("create")) {
            return handleCreate(sender, args);
        }
        if (sub.equals("delete")) {
            return handleDelete(sender, args);
        }
        if (sub.equals("forcestart") || sub.equals("start")) {
            return handleStart(sender, args);
        }
        if (sub.equals("forcestop") || sub.equals("stop")) {
            return handleStop(sender);
        }
        if (sub.equals("list")) {
            return handleList(sender);
        }
        if (sub.equals("show")) {
            return handleShow(sender, args);
        }
        if (sub.equals("reload")) {
            plugin.getKothManager().reload();
            msg(sender, plugin.format("reloaded", null, null, 0, 0));
            return true;
        }
        if (sub.equals("schedule")) {
            return handleSchedule(sender, args);
        }
        sendHelp(sender);
        return true;
    }

    private boolean handlePos(CommandSender sender, int which) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        Player player = (Player) sender;
        plugin.getKothManager().setCorner(player, which);
        org.bukkit.Location loc = player.getLocation().getBlock().getLocation();
        sender.sendMessage(plugin.prefix() + CC.color("&aPos" + which + " : &e"
                + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ()
                + " &7(" + loc.getWorld().getName() + ")"));
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(CC.color("&e/koth pos1 &7puis &e/koth pos2"));
            sender.sendMessage(CC.color("&e/koth create <nom> [giant]"));
            sender.sendMessage(CC.color("&e/koth create <nom> <x1;y1;z1> <x2;y2;z2> [giant]"));
            sender.sendMessage(CC.color("&e/koth create <nom> <x1> <y1> <z1> <x2> <y2> <z2> [giant]"));
            return true;
        }
        Player player = (Player) sender;
        org.bukkit.World world = player.getWorld();
        List<String> rest = new ArrayList<String>();
        for (int i = 2; i < args.length; i++) {
            rest.add(args[i]);
        }
        KothType type = KothType.GIANT;
        if (!rest.isEmpty()) {
            String last = rest.get(rest.size() - 1);
            if (isTypeToken(last)) {
                type = KothType.from(last);
                rest.remove(rest.size() - 1);
            }
        }
        int[] a = null;
        int[] b = null;
        if (rest.isEmpty()) {
            org.bukkit.Location p1 = plugin.getKothManager().getCorner(player, 1);
            org.bukkit.Location p2 = plugin.getKothManager().getCorner(player, 2);
            if (p1 == null || p2 == null) {
                sender.sendMessage(CC.color("&cPlace d'abord &e/koth pos1 &cet &e/koth pos2 &caux coins du cuboide."));
                return true;
            }
            if (p1.getWorld() == null || p2.getWorld() == null
                    || !p1.getWorld().getName().equalsIgnoreCase(p2.getWorld().getName())) {
                sender.sendMessage(CC.color("&cPos1 et pos2 doivent etre dans le meme monde."));
                return true;
            }
            a = new int[] {p1.getBlockX(), p1.getBlockY(), p1.getBlockZ()};
            b = new int[] {p2.getBlockX(), p2.getBlockY(), p2.getBlockZ()};
            world = p1.getWorld();
        } else if (rest.size() == 2) {
            a = parseCoord(rest.get(0));
            b = parseCoord(rest.get(1));
        } else if (rest.size() == 6) {
            a = parseTriple(rest.get(0), rest.get(1), rest.get(2));
            b = parseTriple(rest.get(3), rest.get(4), rest.get(5));
        }
        if (a == null || b == null) {
            sender.sendMessage(CC.color("&cCoordonnees invalides."));
            sender.sendMessage(CC.color("&7Ex: &e/koth pos1 &7/ &e/koth pos2 &7puis &e/koth create geant"));
            sender.sendMessage(CC.color("&7Ex: &e/koth create geant 100;60;100 150;80;150"));
            return true;
        }
        KothZone zone = plugin.getKothManager().create(args[1], world,
                a[0], a[1], a[2], b[0], b[1], b[2], type);
        msg(sender, plugin.format("created", zone, null, 0, 0));
        sender.sendMessage(CC.color("&7Cuboide : &f" + zone.getMinX() + ";" + zone.getMinY() + ";" + zone.getMinZ()
                + " &7-> &f" + zone.getMaxX() + ";" + zone.getMaxY() + ";" + zone.getMaxZ()));
        return true;
    }

    private boolean isTypeToken(String raw) {
        if (raw == null) {
            return false;
        }
        String n = raw.toLowerCase(Locale.ROOT);
        return n.equals("giant") || n.equals("geant") || n.equals("normal")
                || n.equals("classic") || n.equals("koth");
    }

    private int[] parseTriple(String x, String y, String z) {
        try {
            return new int[] {
                    (int) Math.floor(Double.parseDouble(x.trim())),
                    (int) Math.floor(Double.parseDouble(y.trim())),
                    (int) Math.floor(Double.parseDouble(z.trim()))
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(CC.color("&e/koth delete <nom>"));
            return true;
        }
        KothZone zone = plugin.getKothManager().get(args[1]);
        if (!plugin.getKothManager().delete(args[1])) {
            msg(sender, plugin.format("unknown-zone", null, null, 0, 0));
            return true;
        }
        msg(sender, plugin.format("deleted", zone, null, 0, 0));
        return true;
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (plugin.getKothManager().isRunning()) {
            msg(sender, plugin.format("already-running", plugin.getKothManager().getActive(), null, 0, 0));
            return true;
        }
        String name;
        if (args.length >= 2) {
            name = args[1];
        } else if (plugin.getKothManager().ids().size() == 1) {
            name = plugin.getKothManager().ids().get(0);
        } else {
            sender.sendMessage(CC.color("&e/koth forcestart <zone>"));
            return true;
        }
        KothZone zone = plugin.getKothManager().get(name);
        if (zone == null) {
            msg(sender, plugin.format("unknown-zone", null, null, 0, 0));
            return true;
        }
        if (!plugin.getKothManager().start(zone.getId())) {
            msg(sender, plugin.format("no-world", zone, null, 0, 0));
            return true;
        }
        if (plugin.getHost().events() != null) {
            plugin.getHost().events().markActive(fr.draftmc.events.EventType.KOTH, zone.getId());
        }
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!plugin.getKothManager().stop(true)) {
            msg(sender, plugin.format("not-running", null, null, 0, 0));
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<KothZone> all = plugin.getKothManager().all();
        if (all.isEmpty()) {
            sender.sendMessage(plugin.prefix() + CC.color("&7Aucune zone. &e/koth pos1 &7/ &epos2 &7puis &e/koth create <nom>"));
            return true;
        }
        sender.sendMessage(plugin.prefix() + CC.color("&6Zones KOTH Geant :"));
        KothZone active = plugin.getKothManager().getActive();
        for (KothZone zone : all) {
            boolean on = plugin.getKothManager().isRunning() && active != null && active.getId().equals(zone.getId());
            sender.sendMessage(CC.color((on ? "&a▶ " : "&8- ") + "&e" + zone.getId()
                    + " &7(" + zone.getType().display() + ", " + zone.getPointsToWin() + " pts)"
                    + (on ? " &a[en cours]" : "")));
        }
        return true;
    }

    private boolean handleShow(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(CC.color("&e/koth show <nom>"));
            return true;
        }
        KothZone zone = plugin.getKothManager().get(args[1]);
        if (zone == null) {
            msg(sender, plugin.format("unknown-zone", null, null, 0, 0));
            return true;
        }
        sender.sendMessage(plugin.prefix() + CC.color("&6Zone &e" + zone.getDisplay()));
        sender.sendMessage(CC.color("&7Type: &f" + zone.getType().display()
                + " &8| &7Objectif: &f" + zone.getPointsToWin()));
        sender.sendMessage(CC.color("&7Monde: &f" + zone.getWorldName()));
        sender.sendMessage(CC.color("&7Min: &f" + zone.getMinX() + ";" + zone.getMinY() + ";" + zone.getMinZ()));
        sender.sendMessage(CC.color("&7Max: &f" + zone.getMaxX() + ";" + zone.getMaxY() + ";" + zone.getMaxZ()));
        if (plugin.getKothManager().isRunning() && plugin.getKothManager().getActive() != null
                && plugin.getKothManager().getActive().getId().equals(zone.getId())) {
            sender.sendMessage(CC.color("&6Scores :"));
            List<Map.Entry<String, Integer>> top = plugin.getKothManager().top(10);
            int place = 1;
            for (Map.Entry<String, Integer> entry : top) {
                sender.sendMessage(CC.color("&e" + place + ". &f"
                        + plugin.getEventFactionHook().getFactionDisplayName(entry.getKey())
                        + " &7" + entry.getValue()));
                place++;
            }
        }
        return true;
    }

    private boolean handleSchedule(CommandSender sender, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("list")) {
            List<KothScheduler.KothScheduleEntry> entries = plugin.getScheduler().loadEntries();
            sender.sendMessage(plugin.prefix() + CC.color("&6Programmation KOTH Geant :"));
            if (entries.isEmpty()) {
                sender.sendMessage(CC.color("&7Aucune entree. &e/koth schedule add <jour> <HH:mm> <zone>"));
                return true;
            }
            for (int i = 0; i < entries.size(); i++) {
                KothScheduler.KothScheduleEntry entry = entries.get(i);
                sender.sendMessage(CC.color("&e" + (i + 1) + ". &f" + entry.dayLabel
                        + " &6" + entry.timeLabel + " &7-> &e" + entry.zoneId));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("add")) {
            if (args.length < 5) {
                sender.sendMessage(CC.color("&e/koth schedule add <jour> <HH:mm> <zone>"));
                return true;
            }
            if (plugin.getScheduler().add(args[2], args[3], args[4])) {
                sender.sendMessage(plugin.prefix() + CC.color("&aHoraire ajoute : &e"
                        + args[2] + " " + args[3] + " &7-> &e" + args[4].toLowerCase(Locale.ROOT)));
            } else {
                sender.sendMessage(CC.color("&cHoraire ou zone invalide."));
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            if (args.length < 3) {
                sender.sendMessage(CC.color("&e/koth schedule remove <numero>"));
                return true;
            }
            try {
                int index = Integer.parseInt(args[2]) - 1;
                if (plugin.getScheduler().remove(index)) {
                    sender.sendMessage(plugin.prefix() + CC.color("&cHoraire retire."));
                } else {
                    sender.sendMessage(CC.color("&cNumero invalide."));
                }
            } catch (NumberFormatException ignored) {
                sender.sendMessage(CC.color("&cNumero invalide."));
            }
            return true;
        }
        sender.sendMessage(CC.color("&e/koth schedule list|add|remove"));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.prefix() + CC.color("&6Commandes KOTH Geant"));
        sender.sendMessage(CC.color("&e/koth pos1 &7/ &e/koth pos2 &7- coins du cuboide"));
        sender.sendMessage(CC.color("&e/koth create <nom> [giant] &7- utilise pos1/pos2"));
        sender.sendMessage(CC.color("&e/koth create <nom> <x1;y1;z1> <x2;y2;z2> [giant]"));
        sender.sendMessage(CC.color("&e/koth delete <nom>"));
        sender.sendMessage(CC.color("&e/koth forcestart <zone>"));
        sender.sendMessage(CC.color("&e/koth forcestop"));
        sender.sendMessage(CC.color("&e/koth list &7/ &e/koth show <nom>"));
        sender.sendMessage(CC.color("&e/koth schedule list|add|remove"));
        sender.sendMessage(CC.color("&e/koth reload"));
        sender.sendMessage(CC.color("&7Ex: &e/koth pos1 &7puis &e/koth pos2 &7puis &e/koth create geant"));
    }

    private void msg(CommandSender sender, String text) {
        if (text != null && !text.isEmpty()) {
            sender.sendMessage(plugin.prefix() + text);
        }
    }

    private int[] parseCoord(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.replace(",", ";").replace(":", ";").split(";");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[] {
                    (int) Math.floor(Double.parseDouble(parts[0].trim())),
                    (int) Math.floor(Double.parseDouble(parts[1].trim())),
                    (int) Math.floor(Double.parseDouble(parts[2].trim()))
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("koth.admin") && !sender.hasPermission("draftmc.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("delete") || sub.equals("forcestart") || sub.equals("start") || sub.equals("show")) {
                return filter(plugin.getKothManager().ids(), args[1]);
            }
            if (sub.equals("schedule")) {
                return filter(Arrays.asList("list", "add", "remove"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("schedule") && args[1].equalsIgnoreCase("add")) {
            return filter(DAYS, args[2]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("create")) {
            return filter(Arrays.asList("giant", "geant", "normal"), args[4]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("schedule") && args[1].equalsIgnoreCase("add")) {
            return filter(plugin.getKothManager().ids(), args[4]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String token) {
        String prefix = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
