package fr.draftmc.events.totem;

import fr.draftmc.events.EventType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

public class TotemCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = Arrays.asList(
            "help", "set", "unset", "spawn", "start", "stop", "reset", "list", "info",
            "size", "block", "item", "reload");

    private final TotemPlugin plugin;

    public TotemCommand(TotemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasAdmin(sender)) {
            sender.sendMessage(ChatColor.RED + "Pas la permission.");
            return true;
        }
        boolean giant = isGiantCommand(command, label);
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, giant);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            return handleList(sender);
        }
        if (sub.equals("reload")) {
            plugin.getTotemManager().reload();
            sender.sendMessage(ChatColor.GREEN + "Totem recharge (totem.yml).");
            return true;
        }
        if (sub.equals("set")) {
            return handleSet(sender, args);
        }
        if (sub.equals("unset")) {
            return handleUnset(sender, args);
        }
        if (sub.equals("spawn") || sub.equals("start")) {
            return handleSpawn(sender, args, giant);
        }
        if (sub.equals("stop")) {
            return handleStop(sender, args);
        }
        if (sub.equals("reset")) {
            return handleReset(sender, args);
        }
        if (sub.equals("info")) {
            return handleInfo(sender, args);
        }
        if (sub.equals("size")) {
            return handleSize(sender, args);
        }
        if (sub.equals("block")) {
            return handleBlock(sender, args);
        }
        if (sub.equals("item")) {
            return handleItem(sender, args);
        }
        sendHelp(sender, giant);
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande joueur uniquement.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage : /totem set <nom>");
            return true;
        }
        plugin.getTotemManager().setAtPlayer((Player) sender, args[1]);
        return true;
    }

    private boolean handleUnset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage : /totem unset <nom>");
            return true;
        }
        if (!plugin.getTotemManager().unset(args[1])) {
            sender.sendMessage(ChatColor.RED + "Totem introuvable.");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Totem " + args[1].toLowerCase(Locale.ROOT) + " retire.");
        return true;
    }

    private boolean handleSpawn(CommandSender sender, String[] args, boolean giant) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage : /" + (giant ? "totemgeant" : "totem") + " spawn <nom>");
            return true;
        }
        if (!plugin.getTotemManager().startCountdown(args[1], giant)) {
            sender.sendMessage(ChatColor.RED + "Impossible de spawn ce totem (deja en cours ou position manquante).");
            return true;
        }
        if (plugin.getHost().events() != null) {
            plugin.getHost().events().markActive(
                    giant ? EventType.TOTEM_GEANT : EventType.TOTEM,
                    args[1].toLowerCase(Locale.ROOT));
        }
        sender.sendMessage(ChatColor.GREEN + (giant
                ? "Compte a rebours du Totem Geant lance (30 min apres spawn)."
                : "Compte a rebours du Totem lance (60s)."));
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage : /totem stop <nom>");
            return true;
        }
        if (!plugin.getTotemManager().stop(args[1])) {
            sender.sendMessage(ChatColor.RED + "Aucun totem en cours avec ce nom.");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Totem arrete.");
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage : /totem reset <nom>");
            return true;
        }
        if (!plugin.getTotemManager().reset(args[1])) {
            sender.sendMessage(ChatColor.RED + "Totem introuvable ou sans position.");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Totem reset.");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "---- [Totems] ----");
        if (plugin.getTotemManager().all().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Aucun totem. /totem set <nom>");
            return true;
        }
        for (Totem totem : plugin.getTotemManager().all()) {
            sender.sendMessage(ChatColor.GREEN + " - " + totem.getName() + ChatColor.GRAY + " : "
                    + statusLabel(totem.getStatus()));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage : /totem info <nom>");
            return true;
        }
        Totem totem = plugin.getTotemManager().get(args[1]);
        if (totem == null) {
            sender.sendMessage(ChatColor.RED + "Totem introuvable.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "---- [Totem " + totem.getName() + "] ----");
        sender.sendMessage(ChatColor.GREEN + " - Status : " + statusLabel(totem.getStatus()));
        if (totem.getLocation() != null && totem.getLocation().getWorld() != null) {
            sender.sendMessage(ChatColor.YELLOW + " - World : " + totem.getLocation().getWorld().getName());
            sender.sendMessage(ChatColor.YELLOW + " - Location : "
                    + totem.getLocation().getBlockX() + " "
                    + totem.getLocation().getBlockY() + " "
                    + totem.getLocation().getBlockZ());
        } else {
            sender.sendMessage(ChatColor.RED + " - Location : non definie (/totem set)");
        }
        sender.sendMessage(ChatColor.YELLOW + " - Size : " + totem.getSize()
                + " (restant : " + totem.getActualSize() + ")");
        sender.sendMessage(ChatColor.YELLOW + " - Bloc : " + totem.getBlockMaterial().name());
        sender.sendMessage(ChatColor.YELLOW + " - Item : " + totem.getItemInteract().name());
        if (totem.getCapturingFactionId() != null) {
            sender.sendMessage(ChatColor.YELLOW + " - Faction : "
                    + plugin.getEventFactionHook().getFactionDisplayName(totem.getCapturingFactionId()));
        }
        return true;
    }

    private boolean handleSize(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage : /totem size <nom> <taille>");
            return true;
        }
        Totem totem = plugin.getTotemManager().getOrCreate(args[1]);
        int size;
        try {
            size = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Taille invalide.");
            return true;
        }
        if (size < 1 || size > 64) {
            sender.sendMessage(ChatColor.RED + "Taille entre 1 et 64.");
            return true;
        }
        boolean running = totem.getStatus() == TotemStatus.STARTED;
        if (running) {
            totem.stop();
        }
        totem.setSize(size);
        totem.generate();
        plugin.getTotemManager().saveTotem(totem);
        if (running) {
            totem.spawn();
        }
        sender.sendMessage(ChatColor.GREEN + "Taille : " + size);
        return true;
    }

    private boolean handleBlock(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage : /totem block <nom> <material>");
            return true;
        }
        Totem totem = plugin.getTotemManager().get(args[1]);
        Material material = Material.getMaterial(args[2].toUpperCase(Locale.ROOT));
        if (totem == null || material == null || !material.isBlock()) {
            sender.sendMessage(ChatColor.RED + "Totem ou materiau invalide.");
            return true;
        }
        totem.setBlockMaterial(material);
        if (totem.getStatus() == TotemStatus.STARTED) {
            totem.reset();
        } else {
            totem.generate();
        }
        plugin.getTotemManager().saveTotem(totem);
        sender.sendMessage(ChatColor.GREEN + "Bloc : " + material.name());
        return true;
    }

    private boolean handleItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage : /totem item <nom> <material>");
            return true;
        }
        Totem totem = plugin.getTotemManager().get(args[1]);
        Material material = Material.getMaterial(args[2].toUpperCase(Locale.ROOT));
        if (totem == null || material == null) {
            sender.sendMessage(ChatColor.RED + "Totem ou materiau invalide.");
            return true;
        }
        totem.setItemInteract(material);
        plugin.getTotemManager().saveTotem(totem);
        sender.sendMessage(ChatColor.GREEN + "Item : " + material.name());
        return true;
    }

    private void sendHelp(CommandSender sender, boolean giant) {
        if (giant) {
            sender.sendMessage(new String[] {
                    ChatColor.GREEN + "----- [Totem Geant] -----",
                    ChatColor.GOLD + "/totemgeant spawn <nom> " + ChatColor.WHITE + "- Lance le totem geant (classement 30 min)",
                    ChatColor.GOLD + "/totemgeant stop <nom> " + ChatColor.WHITE + "- Arrete et affiche le top 3",
                    ChatColor.GOLD + "/totem set <nom> " + ChatColor.WHITE + "- Pose la tour (partage avec /totem)",
                    ChatColor.GOLD + "/event start totemgeant <map> " + ChatColor.WHITE + "- Start via /event"
            });
            return;
        }
        sender.sendMessage(new String[] {
                ChatColor.GREEN + "----- [Totem] -----",
                ChatColor.GOLD + "/totem set <nom> " + ChatColor.WHITE + "- Pose le totem a tes pieds",
                ChatColor.GOLD + "/totem unset <nom> " + ChatColor.WHITE + "- Supprime le totem",
                ChatColor.GOLD + "/totem spawn <nom> " + ChatColor.WHITE + "- Spawn le totem",
                ChatColor.GOLD + "/totem stop <nom> " + ChatColor.WHITE + "- Arrete le totem",
                ChatColor.GOLD + "/totem reset <nom> " + ChatColor.WHITE + "- Reset la tour",
                ChatColor.GOLD + "/totem list " + ChatColor.WHITE + "- Liste",
                ChatColor.GOLD + "/totem info <nom> " + ChatColor.WHITE + "- Infos",
                ChatColor.GOLD + "/totem size <nom> <n> " + ChatColor.WHITE + "- Hauteur",
                ChatColor.GOLD + "/totem block <nom> <mat> " + ChatColor.WHITE + "- Bloc",
                ChatColor.GOLD + "/totem item <nom> <mat> " + ChatColor.WHITE + "- Item pour casser",
                ChatColor.GOLD + "/event start totem <map> " + ChatColor.WHITE + "- Start via /event",
                ChatColor.GOLD + "/event start totemgeant <map> " + ChatColor.WHITE + "- Totem Geant (top 3, 30 min)"
        });
    }

    private boolean isGiantCommand(Command command, String label) {
        String name = command == null ? "" : command.getName();
        String used = label == null ? name : label;
        String n = used.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return n.equals("totemgeant") || n.equals("gianttotem") || n.equals("totemg");
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("totem.admin") || sender.hasPermission("draftmc.admin");
    }

    private String statusLabel(TotemStatus status) {
        if (status == TotemStatus.STARTED) {
            return "En cours";
        }
        if (status == TotemStatus.STARTING) {
            return "Compte a rebours";
        }
        if (status == TotemStatus.FINISHED) {
            return "Termine";
        }
        return "En attente";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAdmin(sender)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("unset") || sub.equals("spawn") || sub.equals("start")
                    || sub.equals("stop") || sub.equals("reset") || sub.equals("info")
                    || sub.equals("size") || sub.equals("block") || sub.equals("item")) {
                return filter(totemNames(), args[1]);
            }
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("block") || args[0].equalsIgnoreCase("item"))) {
            List<String> mats = new ArrayList<String>();
            for (Material material : Material.values()) {
                if (args[0].equalsIgnoreCase("block") && !material.isBlock()) {
                    continue;
                }
                mats.add(material.name());
            }
            return filter(mats, args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> totemNames() {
        List<String> names = new ArrayList<String>();
        for (Totem totem : plugin.getTotemManager().all()) {
            names.add(totem.getName());
        }
        return names;
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
