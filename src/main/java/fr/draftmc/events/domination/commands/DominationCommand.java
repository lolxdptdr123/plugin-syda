package fr.draftmc.events.domination.commands;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.Zone;
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

public class DominationCommand implements CommandExecutor, TabCompleter {

    private final DominationPlugin plugin;

    public DominationCommand(DominationPlugin plugin) {
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
        return sender.hasPermission(specific) || sender.hasPermission("domination.admin")
                || sender.hasPermission("draftmc.admin");
    }

    private boolean handleStart(CommandSender sender) {
        if (!hasAccess(sender, "domination.start")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (!plugin.getDominationManager().start()) {
            plugin.getMessageManager().send(sender, "already-running");
        }
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!hasAccess(sender, "domination.stop")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (!plugin.getDominationManager().stop()) {
            sender.sendMessage(ChatColor.RED + "Aucun Domination en cours (ni inscriptions, ni partie).");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasAccess(sender, "domination.reload")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        plugin.getDominationManager().reload();
        sender.sendMessage(ChatColor.GREEN + "Configuration Domination rechargee.");
        return true;
    }

    private boolean handleCreateZone(CommandSender sender, String[] args) {
        if (!sender.hasPermission("domination.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Utilisation : /domination createzone <nom>");
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
        if (!sender.hasPermission("domination.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Utilisation : /domination deletezone <nom>");
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
        if (!sender.hasPermission("domination.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Utilisation : /domination " + (isPos1 ? "setpos1" : "setpos2") + " <nom>");
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
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Domination ===");

        String etat;
        switch (plugin.getDominationManager().getState()) {
            case RUNNING:
                etat = "En cours";
                break;
            case STARTING:
                etat = "Compte a rebours (" + plugin.getDominationManager().getCountdownSecondsLeft() + "s)";
                break;
            default:
                etat = "Arrete";
        }
        sender.sendMessage(ChatColor.GRAY + "Etat : " + ChatColor.WHITE + etat);
        sender.sendMessage(ChatColor.GRAY + "Zones : " + ChatColor.WHITE + plugin.getZoneManager().getZones().size());
        sender.sendMessage(ChatColor.GRAY + "Objectif : " + ChatColor.WHITE
                + plugin.getConfig().getInt("general.points-to-win", 2000) + " points");

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            sender.sendMessage(zone.getDisplayName() + ChatColor.GRAY + " - "
                    + zone.getPlayersInside().size() + " joueur(s) present(s)");
        }
        return true;
    }

    private boolean handlePoints(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Classement Domination ===");
        int pointsToWin = plugin.getConfig().getInt("general.points-to-win", 2000);

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(plugin.getDominationManager().getAllTotals().entrySet());
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

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Diagnostic Domination ===");
        sender.sendMessage(ChatColor.GRAY + "Plugin Factions detecte : " + ChatColor.WHITE + plugin.getEventFactionHook().hasRealFactionPlugin());

        if (factionId == null) {
            sender.sendMessage(ChatColor.RED + "Tu n'as pas de faction (wilderness) : tu ne peux marquer aucun point tant que tu n'en rejoins pas une.");
            return true;
        }

        String factionName = plugin.getEventFactionHook().getFactionDisplayName(factionId);
        sender.sendMessage(ChatColor.GRAY + "Ton identifiant de faction (cle interne) : " + ChatColor.WHITE + factionId);
        sender.sendMessage(ChatColor.GRAY + "Nom affiche : " + ChatColor.WHITE + factionName);
        sender.sendMessage(ChatColor.GRAY + "Ton total de points Domination : " + ChatColor.WHITE
                + plugin.getDominationManager().getFactionTotal(factionId));
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("domination.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Debug Domination ===");
        sender.sendMessage(ChatColor.GRAY + "Plugin Factions detecte : " + ChatColor.WHITE + plugin.getEventFactionHook().hasRealFactionPlugin());
        sender.sendMessage(ChatColor.GRAY + "ActionBar NMS supportee : " + ChatColor.WHITE + plugin.getActionBarManager().isSupported());
        sender.sendMessage(ChatColor.GRAY + "Etat : " + ChatColor.WHITE + plugin.getDominationManager().getState());

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            sender.sendMessage(ChatColor.YELLOW + zone.getName() + ChatColor.GRAY
                    + " monde=" + zone.getWorldName()
                    + " presents=" + zone.getPlayersInside().size()
                    + " points=" + zone.getAllPoints());
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Domination ===");
        sender.sendMessage(ChatColor.YELLOW + "/domination start" + ChatColor.GRAY + " - Demarre l'evenement (compte a rebours)");
        sender.sendMessage(ChatColor.YELLOW + "/domination stop" + ChatColor.GRAY + " - Arrete l'evenement");
        sender.sendMessage(ChatColor.YELLOW + "/domination reload" + ChatColor.GRAY + " - Recharge la configuration");
        sender.sendMessage(ChatColor.YELLOW + "/domination createzone <nom>" + ChatColor.GRAY + " - Cree une zone");
        sender.sendMessage(ChatColor.YELLOW + "/domination deletezone <nom>" + ChatColor.GRAY + " - Supprime une zone");
        sender.sendMessage(ChatColor.YELLOW + "/domination setpos1 <nom>" + ChatColor.GRAY + " - Definit le 1er coin");
        sender.sendMessage(ChatColor.YELLOW + "/domination setpos2 <nom>" + ChatColor.GRAY + " - Definit le 2eme coin");
        sender.sendMessage(ChatColor.YELLOW + "/domination info" + ChatColor.GRAY + " - Etat de l'evenement et des zones");
        sender.sendMessage(ChatColor.YELLOW + "/domination points" + ChatColor.GRAY + " - Classement des factions");
        sender.sendMessage(ChatColor.YELLOW + "/domination debug" + ChatColor.GRAY + " - Informations techniques (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/domination whoami" + ChatColor.GRAY + " - Ta faction detectee et ton total de points");
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
