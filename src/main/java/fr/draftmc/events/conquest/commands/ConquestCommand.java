package fr.draftmc.events.conquest.commands;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConquestCommand implements CommandExecutor, TabCompleter {

    private final ConquestPlugin plugin;

    public ConquestCommand(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                return handleStart(sender);
            case "stop":
                return handleStop(sender);
            case "reload":
                return handleReload(sender);
            case "createzone":
                return handleCreateZone(sender, args);
            case "deletezone":
                return handleDeleteZone(sender, args);
            case "setpos1":
                return handleSetPos(sender, args, true);
            case "setpos2":
                return handleSetPos(sender, args, false);
            case "info":
                return handleInfo(sender);
            case "points":
                return handlePoints(sender);
            case "debug":
                return handleDebug(sender);
            case "whoami":
                return handleWhoami(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean hasAccess(CommandSender sender, String specific) {
        return sender.hasPermission(specific) || sender.hasPermission("conquest.admin")
                || sender.hasPermission("draftmc.admin");
    }

    private boolean handleStart(CommandSender sender) {
        if (!hasAccess(sender, "conquest.start")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (!plugin.getConquestManager().start()) {
            plugin.getMessageManager().send(sender, "already-running");
        }
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!hasAccess(sender, "conquest.stop")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (!plugin.getConquestManager().stop()) {
            plugin.getMessageManager().send(sender, "no-event-running");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasAccess(sender, "conquest.reload")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        plugin.getConquestManager().reload();
        sender.sendMessage(ChatColor.GREEN + "Configuration Conquest rechargee.");
        return true;
    }

    private boolean handleCreateZone(CommandSender sender, String[] args) {
        if (!sender.hasPermission("conquest.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Utilisation : /conquest createzone <nom>");
            return true;
        }
        boolean created = plugin.getZoneManager().createZone(args[1], ChatColor.WHITE);
        Map<String, String> ph = new HashMap<>();
        ph.put("zone", args[1]);
        if (created) {
            plugin.getMessageManager().send(sender, "zone-created", ph);
        } else {
            sender.sendMessage(ChatColor.RED + "Une zone porte deja ce nom.");
        }
        return true;
    }

    private boolean handleDeleteZone(CommandSender sender, String[] args) {
        if (!sender.hasPermission("conquest.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Utilisation : /conquest deletezone <nom>");
            return true;
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("zone", args[1]);
        if (plugin.getZoneManager().deleteZone(args[1])) {
            plugin.getMessageManager().send(sender, "zone-deleted", ph);
        } else {
            plugin.getMessageManager().send(sender, "zone-unknown", ph);
        }
        return true;
    }

    private boolean handleSetPos(CommandSender sender, String[] args, boolean isPos1) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande reservee aux joueurs.");
            return true;
        }
        if (!sender.hasPermission("conquest.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Utilisation : /conquest " + (isPos1 ? "setpos1" : "setpos2") + " <nom>");
            return true;
        }

        Player player = (Player) sender;
        boolean ok = isPos1
                ? plugin.getZoneManager().setPos1(args[1], player.getLocation())
                : plugin.getZoneManager().setPos2(args[1], player.getLocation());

        Map<String, String> ph = new HashMap<>();
        ph.put("zone", args[1]);
        ph.put("index", isPos1 ? "1" : "2");
        if (ok) {
            plugin.getMessageManager().send(sender, "pos-set", ph);
        } else {
            plugin.getMessageManager().send(sender, "zone-unknown", ph);
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Conquest ===");

        String etat;
        switch (plugin.getConquestManager().getState()) {
            case RUNNING:
                etat = "En cours";
                break;
            case STARTING:
                etat = "Compte a rebours (" + plugin.getConquestManager().getCountdownSecondsLeft() + "s)";
                break;
            default:
                etat = "Arrete";
        }
        sender.sendMessage(ChatColor.GRAY + "Etat : " + ChatColor.WHITE + etat);
        sender.sendMessage(ChatColor.GRAY + "Zones : " + ChatColor.WHITE + plugin.getZoneManager().getZones().size());
        sender.sendMessage(ChatColor.GRAY + "Objectif : " + ChatColor.WHITE
                + plugin.getConfig().getInt("general.points-to-win", 100) + " points");

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            String status = zone.isLocked() ? "verrouillee" : (zone.getCaptorId() != null ? "capturee" : "libre");
            sender.sendMessage(zone.getDisplayName() + ChatColor.GRAY + " - " + status);
        }
        return true;
    }

    private boolean handlePoints(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Classement Conquest ===");
        int pointsToWin = plugin.getConfig().getInt("general.points-to-win", 100);

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(plugin.getConquestManager().getAllTotals().entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        int rank = 1;
        for (Map.Entry<String, Integer> entry : sorted) {
            String name = plugin.getEventFactionHook().getFactionDisplayName(entry.getKey());
            sender.sendMessage(ChatColor.YELLOW + "Top " + rank + ChatColor.GRAY + " - " + ChatColor.WHITE
                    + "\"" + name + "\"" + ChatColor.GRAY + " " + entry.getValue() + "/" + pointsToWin);
            rank++;
        }
        return true;
    }

    private boolean handleWhoami(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande reservee aux joueurs.");
            return true;
        }
        Player player = (Player) sender;
        String factionId = plugin.getEventFactionHook().getFactionId(player);
        String factionName = plugin.getEventFactionHook().getFactionDisplayName(factionId);

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Diagnostic Conquest ===");
        sender.sendMessage(ChatColor.GRAY + "Plugin Factions detecte : " + ChatColor.WHITE + plugin.getEventFactionHook().hasRealFactionPlugin());
        sender.sendMessage(ChatColor.GRAY + "Ton identifiant de faction (cle interne) : " + ChatColor.WHITE
                + (factionId == null ? "aucune (wilderness)" : factionId));
        sender.sendMessage(ChatColor.GRAY + "Nom affiche : " + ChatColor.WHITE
                + (factionId == null ? "aucune faction" : factionName));
        sender.sendMessage(ChatColor.GRAY + "Ton total de points Conquest : " + ChatColor.WHITE
                + plugin.getConquestManager().getFactionTotal(factionId));

        if (!plugin.getEventFactionHook().hasRealFactionPlugin()) {
            sender.sendMessage(ChatColor.YELLOW + "Aucun plugin Factions detecte : chaque joueur compte "
                    + "comme sa propre faction (mode solo). Si tu veux des factions partagees entre plusieurs "
                    + "joueurs, installe un plugin Factions compatible.");
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("conquest.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Debug Conquest ===");
        sender.sendMessage(ChatColor.GRAY + "Plugin Factions detecte : " + ChatColor.WHITE + plugin.getEventFactionHook().hasRealFactionPlugin());
        sender.sendMessage(ChatColor.GRAY + "ActionBar NMS supportee : " + ChatColor.WHITE + plugin.getActionBarManager().isSupported());
        sender.sendMessage(ChatColor.GRAY + "Etat : " + ChatColor.WHITE + plugin.getConquestManager().getState());

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            sender.sendMessage(ChatColor.YELLOW + zone.getName() + ChatColor.GRAY
                    + " monde=" + zone.getWorldName()
                    + " locked=" + zone.isLocked()
                    + " captor=" + zone.getCaptorId()
                    + " secondes=" + zone.getCaptureSeconds()
                    + " presents=" + zone.getPlayersInside().size()
                    + " points=" + zone.getAllPoints());
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Conquest ===");
        sender.sendMessage(ChatColor.YELLOW + "/conquest start" + ChatColor.GRAY + " - Demarre l'evenement");
        sender.sendMessage(ChatColor.YELLOW + "/conquest stop" + ChatColor.GRAY + " - Arrete l'evenement");
        sender.sendMessage(ChatColor.YELLOW + "/conquest reload" + ChatColor.GRAY + " - Recharge la configuration");
        sender.sendMessage(ChatColor.YELLOW + "/conquest createzone <nom>" + ChatColor.GRAY + " - Cree une zone");
        sender.sendMessage(ChatColor.YELLOW + "/conquest deletezone <nom>" + ChatColor.GRAY + " - Supprime une zone");
        sender.sendMessage(ChatColor.YELLOW + "/conquest setpos1 <nom>" + ChatColor.GRAY + " - Definit le 1er coin");
        sender.sendMessage(ChatColor.YELLOW + "/conquest setpos2 <nom>" + ChatColor.GRAY + " - Definit le 2eme coin");
        sender.sendMessage(ChatColor.YELLOW + "/conquest info" + ChatColor.GRAY + " - Etat de l'evenement et des zones");
        sender.sendMessage(ChatColor.YELLOW + "/conquest points" + ChatColor.GRAY + " - Classement des factions");
        sender.sendMessage(ChatColor.YELLOW + "/conquest debug" + ChatColor.GRAY + " - Informations techniques (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/conquest whoami" + ChatColor.GRAY + " - Ta faction detectee et ton total de points");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "reload", "createzone", "deletezone",
                    "setpos1", "setpos2", "info", "points", "debug", "whoami");
        }
        if (args.length == 2 && Arrays.asList("deletezone", "setpos1", "setpos2").contains(args[0].toLowerCase())) {
            return new ArrayList<>(plugin.getZoneManager().getZones().keySet());
        }
        return Collections.emptyList();
    }
}
