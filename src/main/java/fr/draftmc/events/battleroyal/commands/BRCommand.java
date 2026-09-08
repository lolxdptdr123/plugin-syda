package fr.draftmc.events.battleroyal.commands;

import fr.draftmc.events.battleroyal.BattleRoyal;
import fr.draftmc.events.EventFactionHook;
import fr.draftmc.events.battleroyal.managers.TeamManager;
import fr.draftmc.events.battleroyal.model.GameState;
import fr.draftmc.events.battleroyal.model.TeamBR;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BRCommand implements CommandExecutor, TabCompleter {

    private final BattleRoyal plugin;

    public BRCommand(BattleRoyal plugin) {
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
            case "list":
                return handleList(sender);
            case "start":
                return handleStart(sender);
            case "launch":
                return handleLaunch(sender);
            case "stop":
                return handleStop(sender);
            case "setbuffzone1":
                return handleSetBuffZone(sender, 1);
            case "setbuffzone2":
                return handleSetBuffZone(sender, 2);
            case "points":
                return handlePoints(sender, args);
            case "top":
                return handleTop(sender);
            case "whoami":
                return handleWhoami(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    // ============================== /br team ... ==============================

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
        String sub = args[1].toLowerCase();

        switch (sub) {
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
        if (plugin.getGameManager().getState() != GameState.REGISTRATION) {
            player.sendMessage(ChatColor.RED + "Les inscriptions ne sont pas ouvertes. Un admin doit d'abord faire /br start.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Utilisation : /br team create <nom>");
            return true;
        }
        TeamManager.CreateResult result = plugin.getTeamManager().createTeam(player, args[2]);
        switch (result) {
            case OK:
                player.sendMessage(ChatColor.GREEN + "Equipe '" + args[2] + "' creee ! Tu en es le leader.");
                break;
            case ALREADY_IN_TEAM:
                player.sendMessage(ChatColor.RED + "Tu es deja dans une equipe.");
                break;
            case NAME_TAKEN:
                player.sendMessage(ChatColor.RED + "Ce nom d'equipe est deja pris.");
                break;
            case INVALID_NAME:
                player.sendMessage(ChatColor.RED + "Nom invalide (2-16 caracteres, lettres/chiffres/underscore).");
                break;
            case NOT_IN_FACTION:
                player.sendMessage(ChatColor.RED + "Tu dois appartenir a une faction pour creer une equipe (wilderness non autorise).");
                break;
            case NOT_FACTION_LEADER:
                player.sendMessage(ChatColor.RED + "Seul le leader de ta faction peut creer une equipe au BR.");
                break;
            case FACTION_ALREADY_REGISTERED:
                player.sendMessage(ChatColor.RED + "Ta faction a deja une equipe inscrite au BR.");
                break;
            case FACTION_CHECK_UNAVAILABLE:
                player.sendMessage(ChatColor.RED + "Aucun plugin Factions compatible detecte sur le serveur, "
                        + "impossible de verifier ta faction. Contacte un admin.");
                break;
        }
        return true;
    }

    private boolean teamInvite(Player player, String[] args) {
        if (plugin.getGameManager().getState() != GameState.REGISTRATION) {
            player.sendMessage(ChatColor.RED + "Les inscriptions ne sont pas ouvertes actuellement.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Utilisation : /br team invite <pseudo>");
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
                TeamBR team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + target.getName() + " a ete invite dans " + team.getDisplayName() + ChatColor.GREEN + ".");
                target.sendMessage(ChatColor.YELLOW + player.getName() + " t'invite dans l'equipe " + team.getDisplayName()
                        + ChatColor.YELLOW + " ! Tape /br team accept ou /br team deny.");
                break;
            case NO_TEAM:
                player.sendMessage(ChatColor.RED + "Tu dois d'abord creer une equipe (/br team create <nom>).");
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
            case NOT_SAME_FACTION:
                player.sendMessage(ChatColor.RED + "Tu ne peux inviter que des joueurs de ta propre faction.");
                break;
            case FACTION_CHECK_UNAVAILABLE:
                player.sendMessage(ChatColor.RED + "Aucun plugin Factions compatible detecte sur le serveur, "
                        + "impossible de verifier l'appartenance a une faction. Contacte un admin.");
                break;
        }
        return true;
    }

    private boolean teamAccept(Player player, String[] args) {
        String teamName = args.length >= 3 ? args[2] : null;
        TeamManager.JoinResult result = plugin.getTeamManager().accept(player, teamName);
        switch (result) {
            case OK:
                TeamBR team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
                Bukkit.broadcastMessage(ChatColor.GREEN + player.getName() + " a rejoint l'equipe " + team.getDisplayName() + ChatColor.GREEN + " !");
                break;
            case NO_INVITE:
                player.sendMessage(ChatColor.RED + "Tu n'as pas d'invitation en attente pour cette equipe.");
                break;
            case AMBIGUOUS:
                Set<String> invites = plugin.getTeamManager().getInvites(player);
                player.sendMessage(ChatColor.RED + "Tu as plusieurs invitations, precise : /br team accept <nom> (" + String.join(", ", invites) + ")");
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
        TeamBR team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
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
            player.sendMessage((isLeader ? ChatColor.GOLD + "★ " : ChatColor.WHITE + "- ") + name + ChatColor.GRAY + " (" + status + ChatColor.GRAY + ")");
        }
        return true;
    }

    private boolean teamLeave(Player player) {
        TeamBR team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
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
            player.sendMessage(ChatColor.RED + "Utilisation : /br team kick <pseudo>");
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
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Battleroyal - Equipes ===");
        sender.sendMessage(ChatColor.YELLOW + "/br team create <nom>" + ChatColor.GRAY + " - Cree ton equipe (tu deviens leader)");
        sender.sendMessage(ChatColor.YELLOW + "/br team invite <pseudo>" + ChatColor.GRAY + " - Invite un joueur (leader uniquement)");
        sender.sendMessage(ChatColor.YELLOW + "/br team accept [nom]" + ChatColor.GRAY + " - Accepte une invitation");
        sender.sendMessage(ChatColor.YELLOW + "/br team deny [nom]" + ChatColor.GRAY + " - Refuse une invitation");
        sender.sendMessage(ChatColor.YELLOW + "/br team list" + ChatColor.GRAY + " - Liste les membres de ton equipe");
        sender.sendMessage(ChatColor.YELLOW + "/br team leave" + ChatColor.GRAY + " - Quitte ton equipe");
        sender.sendMessage(ChatColor.YELLOW + "/br team kick <pseudo>" + ChatColor.GRAY + " - Exclut un membre (leader uniquement)");
        sender.sendMessage(ChatColor.YELLOW + "/br team disband" + ChatColor.GRAY + " - Dissout ton equipe (leader uniquement)");
    }

    // ============================== reste des commandes ==============================

    private boolean handleList(CommandSender sender) {
        Collection<TeamBR> teams = plugin.getTeamManager().getTeams();
        if (teams.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Aucune equipe inscrite pour le moment.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Equipes inscrites (" + teams.size() + ") ===");
        for (TeamBR team : teams) {
            String leaderName = Bukkit.getOfflinePlayer(team.getLeader()).getName();
            sender.sendMessage(team.getDisplayName() + ChatColor.GRAY + " - " + team.getMembers().size()
                    + " membre(s) - leader: " + leaderName);
        }
        return true;
    }

    private boolean handleStart(CommandSender sender) {
        if (!sender.hasPermission("battleroyal.admin") && !sender.hasPermission("draftmc.admin")) {
            sender.sendMessage(ChatColor.RED + "Permission refusee.");
            return true;
        }
        boolean ok = plugin.getGameManager().openRegistrations();
        if (!ok) {
            sender.sendMessage(ChatColor.RED + "Impossible : une partie est deja en cours ou les inscriptions sont deja ouvertes.");
        }
        return true;
    }

    private boolean handleLaunch(CommandSender sender) {
        if (!sender.hasPermission("battleroyal.admin") && !sender.hasPermission("draftmc.admin")) {
            sender.sendMessage(ChatColor.RED + "Permission refusee.");
            return true;
        }
        if (!plugin.getGameManager().isReadyToLaunch()) {
            sender.sendMessage(ChatColor.RED + "Il faut que les inscriptions soient ouvertes (/br start) et au moins 2 equipes formees.");
            return true;
        }
        plugin.getGameManager().launchGame();
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!sender.hasPermission("battleroyal.admin") && !sender.hasPermission("draftmc.admin")) {
            sender.sendMessage(ChatColor.RED + "Permission refusee.");
            return true;
        }
        boolean stopped = plugin.getGameManager().stopGame();
        if (!stopped) {
            sender.sendMessage(ChatColor.RED + "Aucun BR en cours (ni inscriptions ouvertes, ni partie en jeu).");
        }
        return true;
    }

    private boolean handleSetBuffZone(CommandSender sender, int index) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande reservee aux joueurs.");
            return true;
        }
        if (!sender.hasPermission("battleroyal.admin") && !sender.hasPermission("draftmc.admin")) {
            sender.sendMessage(ChatColor.RED + "Permission refusee.");
            return true;
        }
        Player player = (Player) sender;
        plugin.getBuffZoneManager().setCorner(index, player.getLocation());
        player.sendMessage(ChatColor.GREEN + "Coin " + index + " de la zone de buff enregistre.");
        return true;
    }

    private boolean handlePoints(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Utilisation : /br points <joueur>");
            return true;
        }
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable.");
            return true;
        }
        int points = plugin.getPointsManager().getPoints(target.getUniqueId());
        sender.sendMessage(ChatColor.GOLD + target.getName() + " a " + points + " points.");
        return true;
    }

    private boolean handleWhoami(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande reservee aux joueurs.");
            return true;
        }
        Player player = (Player) sender;
        EventFactionHook hook = plugin.getEventFactionHook();

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Diagnostic Factions ===");
        if (hook == null) {
            sender.sendMessage(ChatColor.RED + "Hook Factions pas encore initialise (redemarre le serveur ou attends quelques secondes).");
            return true;
        }
        sender.sendMessage(ChatColor.GRAY + "Plugin Factions detecte : " + ChatColor.WHITE + hook.isAvailable());
        String factionId = hook.getFactionId(player);
        sender.sendMessage(ChatColor.GRAY + "Ta faction detectee : " + ChatColor.WHITE + (factionId != null ? factionId : "aucune (wilderness ?)"));
        sender.sendMessage(ChatColor.GRAY + "Detecte comme leader : " + ChatColor.WHITE + hook.isFactionLeader(player));
        sender.sendMessage(ChatColor.GOLD + "--- Methodes reelles detectees (pour diagnostic) ---");
        for (String line : hook.debugInfo(player).split("\n")) {
            sender.sendMessage(ChatColor.DARK_GRAY + line);
        }
        return true;
    }

    private boolean handleTop(CommandSender sender) {
        List<Map.Entry<UUID, Integer>> top = plugin.getPointsManager().getTop(10);
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Top 10 Battleroyal ===");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : top) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            sender.sendMessage(ChatColor.YELLOW + "#" + rank + " " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " - " + entry.getValue() + " pts");
            rank++;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== Battleroyal ===");
        sender.sendMessage(ChatColor.YELLOW + "/br team ..." + ChatColor.GRAY + " - Gestion des equipes (create/invite/accept/deny/list/leave/kick/disband)");
        sender.sendMessage(ChatColor.YELLOW + "/br list" + ChatColor.GRAY + " - Liste les equipes inscrites au BR");
        sender.sendMessage(ChatColor.YELLOW + "/br start" + ChatColor.GRAY + " - Ouvre les inscriptions (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/br launch" + ChatColor.GRAY + " - Demarre vraiment la Battle Royale (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/br stop" + ChatColor.GRAY + " - Arrete la partie (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/br setbuffzone1 | setbuffzone2" + ChatColor.GRAY + " - Definit la zone a effets permanents (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/br points [joueur]" + ChatColor.GRAY + " - Affiche les points");
        sender.sendMessage(ChatColor.YELLOW + "/br top" + ChatColor.GRAY + " - Classement general");
        sender.sendMessage(ChatColor.YELLOW + "/br whoami" + ChatColor.GRAY + " - Diagnostic : ta faction detectee et si tu es leader");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("team", "list", "start", "launch", "stop", "setbuffzone1", "setbuffzone2", "points", "top", "whoami");
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
        if (args.length == 2 && args[0].equalsIgnoreCase("points")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        }
        return Collections.emptyList();
    }
}
