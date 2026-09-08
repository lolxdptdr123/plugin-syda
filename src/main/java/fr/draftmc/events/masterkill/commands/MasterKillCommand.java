package fr.draftmc.events.masterkill.commands;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.managers.TeamManager;
import fr.draftmc.events.masterkill.model.MasterKillState;
import fr.draftmc.events.masterkill.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MasterKillCommand implements CommandExecutor, TabCompleter {

    private final MasterKillPlugin plugin;

    public MasterKillCommand(MasterKillPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "team":
                return handleTeam(sender, args);
            case "start":
                return handleStart(sender);
            case "launch":
                return handleLaunch(sender);
            case "stop":
                return handleStop(sender);
            case "reload":
                return handleReload(sender);
            case "setpos1":
                return handleSetPos(sender, true);
            case "setpos2":
                return handleSetPos(sender, false);
            case "setspawn":
                return handleSetSpawn(sender);
            case "addspawn":
                return handleAddSpawn(sender);
            case "clearspawns":
                return handleClearSpawns(sender);
            case "info":
                return handleInfo(sender);
            case "points":
                return handlePoints(sender);
            case "whoami":
                return handleWhoami(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean hasAccess(CommandSender sender, String specific) {
        return sender.hasPermission(specific) || sender.hasPermission("masterkill.admin")
                || sender.hasPermission("draftmc.admin");
    }

    // ============================== /masterkill team ... ==============================

    private boolean handleTeam(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande reservee aux joueurs.");
            return true;
        }
        if (args.length < 2) {
            sendTeamHelp(sender);
            return true;
        }
        Player player = (Player) sender;
        switch (args[1].toLowerCase()) {
            case "create":
                return teamCreate(player, args);
            case "invite":
                return teamInvite(player, args);
            case "accept":
                return teamAccept(player, args);
            case "deny":
                return teamDeny(player, args);
            case "list":
                return teamList(player);
            case "leave":
                return teamLeave(player);
            case "kick":
                return teamKick(player, args);
            case "disband":
                return teamDisband(player);
            default:
                sendTeamHelp(sender);
                return true;
        }
    }

    private boolean teamCreate(Player player, String[] args) {
        if (plugin.getMasterKillManager().getState() != MasterKillState.REGISTRATION) {
            player.sendMessage(ChatColor.RED + "Les inscriptions ne sont pas ouvertes. Un admin doit d'abord faire /masterkill start.");
            return true;
        }

        String factionId = plugin.getEventFactionHook().getFactionId(player);
        if (factionId == null) {
            player.sendMessage(ChatColor.RED + "Tu dois appartenir a une faction pour creer une equipe MasterKill.");
            return true;
        }
        String teamName = plugin.getEventFactionHook().getFactionDisplayName(factionId);

        TeamManager.CreateResult result = plugin.getTeamManager().createTeam(player, teamName);
        switch (result) {
            case OK:
                player.sendMessage(ChatColor.GREEN + "Equipe '" + teamName + "' creee (nom de ta faction) ! Tu en es le leader.");
                break;
            case ALREADY_IN_TEAM:
                player.sendMessage(ChatColor.RED + "Tu es deja dans une equipe.");
                break;
            case NAME_TAKEN:
                player.sendMessage(ChatColor.RED + "Une equipe existe deja pour ta faction (quelqu'un d'autre l'a deja creee - demande-lui de t'inviter).");
                break;
            case INVALID_NAME:
                player.sendMessage(ChatColor.RED + "Le nom de ta faction contient des caracteres non supportes (seuls lettres/chiffres/underscore sont acceptes).");
                break;
        }
        return true;
    }

    private boolean teamInvite(Player player, String[] args) {
        if (plugin.getMasterKillManager().getState() != MasterKillState.REGISTRATION) {
            player.sendMessage(ChatColor.RED + "Les inscriptions ne sont pas ouvertes actuellement.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Utilisation : /masterkill team invite <pseudo>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Joueur introuvable.");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "Tu ne peux pas t'inviter toi-meme.");
            return true;
        }

        TeamManager.InviteResult result = plugin.getTeamManager().invite(player, target);
        switch (result) {
            case OK:
                Team team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + target.getName() + " a ete invite dans " + team.getDisplayName() + ChatColor.GREEN + ".");
                target.sendMessage(ChatColor.YELLOW + player.getName() + " t'invite dans l'equipe " + team.getDisplayName()
                        + ChatColor.YELLOW + " ! Tape /masterkill team accept ou /masterkill team deny.");
                break;
            case NO_TEAM:
                player.sendMessage(ChatColor.RED + "Tu dois d'abord creer une equipe (/masterkill team create <nom>).");
                break;
            case NOT_LEADER:
                player.sendMessage(ChatColor.RED + "Seul le leader de l'equipe peut inviter des joueurs.");
                break;
            case TARGET_IN_TEAM:
                player.sendMessage(ChatColor.RED + "Ce joueur est deja dans une equipe.");
                break;
            case ALREADY_INVITED:
                player.sendMessage(ChatColor.RED + "Ce joueur a deja une invitation en attente pour ton equipe.");
                break;
        }
        return true;
    }

    private boolean teamAccept(Player player, String[] args) {
        String teamName = args.length >= 3 ? args[2] : null;
        TeamManager.JoinResult result = plugin.getTeamManager().accept(player, teamName);
        switch (result) {
            case OK:
                Team team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
                Bukkit.broadcastMessage(ChatColor.GREEN + player.getName() + " a rejoint l'equipe " + team.getDisplayName() + ChatColor.GREEN + " !");
                break;
            case NO_INVITE:
                player.sendMessage(ChatColor.RED + "Tu n'as pas d'invitation en attente pour cette equipe.");
                break;
            case AMBIGUOUS:
                Set<String> invites = plugin.getTeamManager().getInvites(player);
                player.sendMessage(ChatColor.RED + "Tu as plusieurs invitations, precise : /masterkill team accept <nom> (" + String.join(", ", invites) + ")");
                break;
            case ALREADY_IN_TEAM:
                player.sendMessage(ChatColor.RED + "Tu es deja dans une equipe.");
                break;
            case TEAM_GONE:
                player.sendMessage(ChatColor.RED + "Cette equipe n'existe plus.");
                break;
        }
        return true;
    }

    private boolean teamDeny(Player player, String[] args) {
        String teamName = args.length >= 3 ? args[2] : null;
        boolean ok = plugin.getTeamManager().deny(player, teamName);
        player.sendMessage(ok
                ? ChatColor.YELLOW + "Invitation refusee."
                : ChatColor.RED + "Tu n'as pas d'invitation en attente pour cette equipe.");
        return true;
    }

    private boolean teamList(Player player) {
        Team team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "Tu n'es dans aucune equipe.");
            return true;
        }
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== " + team.getDisplayName() + ChatColor.GOLD + ChatColor.BOLD + " ===");
        for (UUID uuid : team.getMembers()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            boolean isLeader = uuid.equals(team.getLeader());
            Player online = Bukkit.getPlayer(uuid);
            String status = online != null ? ChatColor.GREEN + "en ligne" : ChatColor.GRAY + "hors ligne";
            player.sendMessage((isLeader ? ChatColor.GOLD + "* " : ChatColor.WHITE + "- ") + name + ChatColor.GRAY + " (" + status + ChatColor.GRAY + ")");
        }
        return true;
    }

    private boolean teamLeave(Player player) {
        Team team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "Tu n'es dans aucune equipe.");
            return true;
        }
        boolean wasLeader = team.getLeader().equals(player.getUniqueId());
        plugin.getTeamManager().leave(player);
        player.sendMessage(wasLeader
                ? ChatColor.YELLOW + "Tu as quitte l'equipe, elle a ete dissoute (tu etais le leader)."
                : ChatColor.YELLOW + "Tu as quitte l'equipe " + team.getDisplayName() + ChatColor.YELLOW + ".");
        return true;
    }

    private boolean teamKick(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Utilisation : /masterkill team kick <pseudo>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Joueur introuvable.");
            return true;
        }
        boolean ok = plugin.getTeamManager().kick(player, target);
        player.sendMessage(ok
                ? ChatColor.GREEN + target.getName() + " a ete exclu de l'equipe."
                : ChatColor.RED + "Impossible (tu n'es pas leader, ou ce joueur n'est pas dans ton equipe).");
        return true;
    }

    private boolean teamDisband(Player player) {
        boolean ok = plugin.getTeamManager().disband(player);
        player.sendMessage(ok
                ? ChatColor.GREEN + "Ton equipe a ete dissoute."
                : ChatColor.RED + "Seul le leader peut dissoudre l'equipe.");
        return true;
    }

    private void sendTeamHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== MasterKill - Equipes ===");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill team create" + ChatColor.GRAY + " - Cree ton equipe (nom = celui de ta faction, automatique)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill team invite <pseudo>" + ChatColor.GRAY + " - Invite un joueur (leader)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill team accept [nom]" + ChatColor.GRAY + " - Accepte une invitation");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill team deny [nom]" + ChatColor.GRAY + " - Refuse une invitation");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill team list" + ChatColor.GRAY + " - Liste les membres de ton equipe");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill team leave" + ChatColor.GRAY + " - Quitte ton equipe");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill team kick <pseudo>" + ChatColor.GRAY + " - Exclut un membre (leader)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill team disband" + ChatColor.GRAY + " - Dissout ton equipe (leader)");
    }

    // ============================== reste des commandes ==============================

    private boolean handleStart(CommandSender sender) {
        if (!hasAccess(sender, "masterkill.start")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (!plugin.getMasterKillManager().start()) {
            plugin.getMessageManager().send(sender, "already-running");
        }
        return true;
    }

    private boolean handleLaunch(CommandSender sender) {
        if (!hasAccess(sender, "masterkill.start")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (!plugin.getMasterKillManager().isReadyToLaunch()) {
            plugin.getMessageManager().send(sender, "not-enough-teams");
            return true;
        }
        plugin.getMasterKillManager().launch();
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!hasAccess(sender, "masterkill.stop")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        if (!plugin.getMasterKillManager().stop()) {
            plugin.getMessageManager().send(sender, "no-event-running");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasAccess(sender, "masterkill.reload")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        plugin.getMasterKillManager().reload();
        sender.sendMessage(ChatColor.GREEN + "Configuration MasterKill rechargee.");
        return true;
    }

    private boolean handleSetPos(CommandSender sender, boolean isPos1) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande reservee aux joueurs.");
            return true;
        }
        if (!sender.hasPermission("masterkill.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        Player player = (Player) sender;
        plugin.getArenaManager().setCorner(isPos1 ? 1 : 2, player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Position " + (isPos1 ? "1" : "2") + " de l'arene MasterKill enregistree.");
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande reservee aux joueurs.");
            return true;
        }
        if (!sender.hasPermission("masterkill.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        Player player = (Player) sender;
        org.bukkit.Location loc = player.getLocation();
        plugin.getConfig().set("spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("spawn.x", loc.getX());
        plugin.getConfig().set("spawn.y", loc.getY());
        plugin.getConfig().set("spawn.z", loc.getZ());
        plugin.getConfig().set("spawn.yaw", loc.getYaw());
        plugin.getConfig().set("spawn.pitch", loc.getPitch());
        plugin.saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Point de spawn MasterKill enregistre a ta position (utilise a la fin d'un match).");
        return true;
    }

    private boolean handleAddSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande reservee aux joueurs.");
            return true;
        }
        if (!sender.hasPermission("masterkill.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        Player player = (Player) sender;
        int count = plugin.getArenaManager().addSpawnPoint(player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Point de spawn n\u00b0" + count + " ajoute a ta position (utilise pour teleporter les equipes au lancement du match, une equipe par point, dans l'ordre d'ajout).");
        return true;
    }

    private boolean handleClearSpawns(CommandSender sender) {
        if (!sender.hasPermission("masterkill.admin") && !sender.hasPermission("draftmc.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return true;
        }
        plugin.getArenaManager().clearSpawnPoints();
        sender.sendMessage(ChatColor.GREEN + "Tous les points de spawn manuels ont ete supprimes. "
                + "L'arene repartira les equipes automatiquement en cercle au prochain lancement.");
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== MasterKill ===");

        String etat;
        switch (plugin.getMasterKillManager().getState()) {
            case RUNNING:
                etat = "En cours (" + plugin.getTeamManager().countAliveTeams() + " equipe(s) en vie)";
                break;
            case REGISTRATION:
                etat = "Inscriptions ouvertes (" + plugin.getTeamManager().getTeams().size() + " equipe(s) formee(s))";
                break;
            default:
                etat = "Arrete";
        }
        sender.sendMessage(ChatColor.GRAY + "Etat : " + ChatColor.WHITE + etat);

        for (Team team : plugin.getTeamManager().getTeams()) {
            boolean alive = plugin.getTeamManager().isTeamAlive(team);
            sender.sendMessage(team.getDisplayName() + ChatColor.GRAY + " - "
                    + (plugin.getMasterKillManager().getState() == MasterKillState.RUNNING
                        ? (alive ? ChatColor.GREEN + "en vie" : ChatColor.RED + "eliminee")
                        : ChatColor.GRAY + (team.getMembers().size() + " membre(s)"))
                    + ChatColor.GRAY + " - " + plugin.getKillManager().getKills(team.getName()) + " kills");
        }
        return true;
    }

    private boolean handlePoints(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Classement MasterKill ===");
        List<Map.Entry<String, Integer>> top = plugin.getKillManager().getTop(10);

        int rank = 1;
        for (Map.Entry<String, Integer> entry : top) {
            sender.sendMessage(ChatColor.YELLOW + "Top " + rank + ChatColor.GRAY + " - " + ChatColor.WHITE
                    + entry.getKey() + ChatColor.GRAY + " " + entry.getValue() + " kills");
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

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Diagnostic MasterKill ===");
        sender.sendMessage(ChatColor.GRAY + "Plugin Factions detecte : " + ChatColor.WHITE + plugin.getEventFactionHook().hasRealFactionPlugin());

        if (factionId == null) {
            sender.sendMessage(ChatColor.RED + "Tu n'as pas de faction (wilderness) : tu ne peux pas creer d'equipe MasterKill.");
            return true;
        }

        String factionName = plugin.getEventFactionHook().getFactionDisplayName(factionId);
        sender.sendMessage(ChatColor.GRAY + "Ta faction detectee : " + ChatColor.WHITE + factionName);
        sender.sendMessage(ChatColor.GRAY + "C'est ce nom qui sera utilise pour /masterkill team create.");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== MasterKill ===");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill start" + ChatColor.GRAY + " - Ouvre les inscriptions (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill team ..." + ChatColor.GRAY + " - Gestion des equipes (create/invite/accept/deny/list/leave/kick/disband)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill launch" + ChatColor.GRAY + " - Teleporte et demarre le match (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill stop" + ChatColor.GRAY + " - Arrete/conclut le match (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill reload" + ChatColor.GRAY + " - Recharge la configuration (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill setpos1" + ChatColor.GRAY + " - Definit le 1er coin de l'arene (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill setpos2" + ChatColor.GRAY + " - Definit le 2eme coin de l'arene (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill setspawn" + ChatColor.GRAY + " - Ou renvoyer les joueurs A LA FIN d'un match (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill addspawn" + ChatColor.GRAY + " - Ajoute un point de spawn pour le LANCEMENT du match, un par equipe (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill clearspawns" + ChatColor.GRAY + " - Supprime tous les points de spawn de lancement (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill info" + ChatColor.GRAY + " - Etat du match et des equipes");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill points" + ChatColor.GRAY + " - Classement des equipes par kills");
        sender.sendMessage(ChatColor.YELLOW + "/masterkill whoami" + ChatColor.GRAY + " - Ta faction detectee (nom qui sera utilise pour ton equipe)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("team", "start", "launch", "stop", "reload", "setpos1", "setpos2", "setspawn", "addspawn", "clearspawns", "info", "points", "whoami");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("team")) {
            return Arrays.asList("create", "invite", "accept", "deny", "list", "leave", "kick", "disband");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("team")
                && (args[1].equalsIgnoreCase("invite") || args[1].equalsIgnoreCase("kick"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        }
        return Collections.emptyList();
    }
}
