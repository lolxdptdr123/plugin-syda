package fr.draftmc.tournament;

import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TournamentCommand implements CommandExecutor, TabCompleter {
    private final TournamentManager manager;

    public TournamentCommand(TournamentManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("status".equalsIgnoreCase(command.getName())) {
            manager.sendStatus(sender);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("status")) {
            manager.sendStatus(sender);
            return true;
        }
        if (sub.equals("invite")) {
            return handleInvite(sender, args);
        }
        if (sub.equals("accept")) {
            return handleAccept(sender, args);
        }
        if (sub.equals("deny") || sub.equals("decline") || sub.equals("refuse")) {
            return handleDeny(sender);
        }
        if (sub.equals("leave") || sub.equals("quit")) {
            return handleLeave(sender);
        }
        if (sub.equals("kick")) {
            return handleKick(sender, args);
        }
        if (sub.equals("team")) {
            return team(sender, args);
        }
        if (sub.equals("list") && args.length == 1) {
            if (!admin(sender)) {
                return true;
            }
            listTournaments(sender);
            return true;
        }
        if (!admin(sender)) {
            return true;
        }
        if (sub.equals("create")) {
            if (args.length >= 4) {
                Boolean lb = parseYesNo(args[args.length >= 5 ? 4 : 3]);
                if (lb == null) {
                    manager.msg(sender, "create-invalid");
                    return true;
                }
                return manager.createTournament(sender, args[1], parse(args[2], 1), lb.booleanValue());
            }
            if (args.length == 2 && sender instanceof Player) {
                return manager.beginCreate((Player) sender, args[1]);
            }
            manager.msg(sender, "create-usage");
            return true;
        }
        if (sub.equals("delete") && args.length >= 2) {
            return manager.deleteTournament(sender, args[1]);
        }
        if (sub.equals("info") && args.length >= 2) {
            manager.info(sender, args[1]);
            return true;
        }
        if (sub.equals("start") && args.length >= 2) {
            return manager.start(sender, args[1]);
        }
        if (sub.equals("launch")) {
            return manager.launch(sender);
        }
        if (sub.equals("stop")) {
            return manager.stop(sender, args.length >= 2 ? args[1] : null, false);
        }
        if (sub.equals("setlobby") || sub.equals("setexit")) {
            return manager.setLobby(sender);
        }
        if (sub.equals("config") && args.length >= 4) {
            return config(sender, args[1], args[2], args[3]);
        }
        if (sub.equals("arena")) {
            return arena(sender, args);
        }
        help(sender);
        return true;
    }

    private boolean config(CommandSender sender, String name, String key, String value) {
        TournamentDefinition def = manager.getTournament(name);
        if (def == null) {
            manager.msg(sender, "not-found");
            return true;
        }
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("teamsize")) {
            def.setTeamSize(parse(value, def.teamSize()));
            manager.save();
            manager.msg(sender, "config-set", "key", "teamsize", "name", def.name(),
                    "value", String.valueOf(def.teamSize()));
            return true;
        }
        if (k.equals("loserbracket") || k.equals("losersbracket")) {
            def.setLoserBracket(value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("oui"));
            manager.save();
            manager.msg(sender, "config-set", "key", "loserbracket", "name", def.name(),
                    "value", String.valueOf(def.loserBracket()));
            return true;
        }
        manager.plugin().msg(sender, "&e/tournament config <nom> teamsize <n>");
        manager.plugin().msg(sender, "&e/tournament config <nom> loserbracket <true/false>");
        return true;
    }

    private boolean team(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("create") && args.length >= 3) {
            return manager.createTeam(sender, args[2]);
        }
        if (action.equals("list")) {
            sender.sendMessage(manager.prefix() + CC.color("&6Équipes"));
            if (manager.teams().isEmpty()) {
                sender.sendMessage(CC.color("&7Aucune équipe. &e/tournament team create <nom>"));
                return true;
            }
            for (TournamentTeam team : manager.teams().values()) {
                List<String> names = new ArrayList<String>();
                for (UUID uuid : team.members()) {
                    names.add(manager.plugin().data().nameOf(uuid));
                }
                sender.sendMessage(CC.color("&e" + team.name() + " &8- &f" + (names.isEmpty() ? "vide" : join(names))));
            }
            return true;
        }
        if (!isAdminPerm(sender)) {
            help(sender);
            return true;
        }
        if (action.equals("add") && args.length >= 4) {
            return manager.addPlayer(sender, args[2], args[3]);
        }
        if (action.equals("remove") && args.length >= 4) {
            return manager.removePlayer(sender, args[2], args[3]);
        }
        help(sender);
        return true;
    }

    private boolean arena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("create") && args.length >= 3) {
            return manager.createArena(sender, args[2]);
        }
        if (action.equals("delete") && args.length >= 3) {
            return manager.deleteArena(sender, args[2]);
        }
        if (action.equals("setspawn1") && args.length >= 3) {
            return manager.setArenaSpawn(sender, args[2], "spawn1");
        }
        if (action.equals("setspawn2") && args.length >= 3) {
            return manager.setArenaSpawn(sender, args[2], "spawn2");
        }
        if (action.equals("setspectator") && args.length >= 3) {
            return manager.setArenaSpawn(sender, args[2], "spectator");
        }
        if (action.equals("list")) {
            sender.sendMessage(manager.prefix() + CC.color("&6Arènes"));
            if (manager.arenas().isEmpty()) {
                sender.sendMessage(CC.color("&7Aucune arène."));
                return true;
            }
            for (TournamentArena arena : manager.arenas().values()) {
                String ready = arena.ready() ? "&aprête" : "&cspawn manquant";
                String busy = arena.free() ? "&7libre" : "&coccupée";
                sender.sendMessage(CC.color("&e" + arena.name() + " &8- " + ready + " &8- " + busy));
            }
            return true;
        }
        help(sender);
        return true;
    }

    private void listTournaments(CommandSender sender) {
        sender.sendMessage(manager.prefix() + CC.color("&6Tournois"));
        if (manager.tournaments().isEmpty()) {
            sender.sendMessage(CC.color("&7Aucun tournoi. &e/tournament create <nom>"));
            return;
        }
        for (TournamentDefinition def : manager.tournaments().values()) {
            String state = manager.runningDef() == def
                    ? (manager.signup() ? "&einscriptions" : "&aEN COURS")
                    : "&7inactif";
            sender.sendMessage(CC.color("&e" + def.name() + " &8- &7size &f" + def.teamSize()
                    + " &7LB &f" + def.loserBracket() + " &8- " + state));
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(CC.color("&8&m-----&r &6Tournoi &8&m-----"));
        sender.sendMessage(CC.color("&e/status &8- &7Qui est encore dans le tournoi"));
        sender.sendMessage(CC.color("&e/tournament team create <nom>"));
        sender.sendMessage(CC.color("&e/tournament invite <joueur>"));
        sender.sendMessage(CC.color("&e/tournament accept [equipe]"));
        sender.sendMessage(CC.color("&e/tournament deny"));
        sender.sendMessage(CC.color("&e/tournament leave"));
        sender.sendMessage(CC.color("&e/tournament kick <joueur>"));
        sender.sendMessage(CC.color("&e/tournament team list"));
        if (!isAdminPerm(sender)) {
            return;
        }
        sender.sendMessage(CC.color("&6Admin"));
        sender.sendMessage(CC.color("&e/tournament create <nom>"));
        sender.sendMessage(CC.color("&7  puis teamsize, puis loser bracket"));
        sender.sendMessage(CC.color("&e/tournament create <nom> <teamsize> <oui/non>"));
        sender.sendMessage(CC.color("&e/tournament delete <nom>"));
        sender.sendMessage(CC.color("&e/tournament info <nom>"));
        sender.sendMessage(CC.color("&e/tournament start <nom> &8- &7Ouvre les inscriptions"));
        sender.sendMessage(CC.color("&e/tournament launch &8- &7Lance les fights"));
        sender.sendMessage(CC.color("&e/tournament stop [nom]"));
        sender.sendMessage(CC.color("&e/tournament setlobby &8- &7TP des morts / fin de fight"));
        sender.sendMessage(CC.color("&e/tournament list"));
        sender.sendMessage(CC.color("&e/tournament config <nom> teamsize <n>"));
        sender.sendMessage(CC.color("&e/tournament config <nom> loserbracket <true/false>"));
        sender.sendMessage(CC.color("&e/tournament team add <team> <joueur>"));
        sender.sendMessage(CC.color("&e/tournament team remove <team> <joueur>"));
        sender.sendMessage(CC.color("&e/tournament arena create <nom>"));
        sender.sendMessage(CC.color("&e/tournament arena setspawn1 <nom>"));
        sender.sendMessage(CC.color("&e/tournament arena setspawn2 <nom>"));
        sender.sendMessage(CC.color("&e/tournament arena setSpectator <nom>"));
        sender.sendMessage(CC.color("&e/tournament arena delete <nom>"));
        sender.sendMessage(CC.color("&e/tournament arena list"));
    }

    private boolean handleInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        if (args.length < 2) {
            manager.plugin().msg(sender, "&e/tournament invite <joueur>");
            return true;
        }
        return manager.invite((Player) sender, Bukkit.getPlayer(args[1]));
    }

    private boolean handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        return manager.accept((Player) sender, args.length >= 2 ? args[1] : "");
    }

    private boolean handleDeny(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        return manager.deny((Player) sender);
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        return manager.leave((Player) sender);
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        if (args.length < 2) {
            manager.plugin().msg(sender, "&e/tournament kick <joueur>");
            return true;
        }
        return manager.kick((Player) sender, args[1]);
    }

    private boolean admin(CommandSender sender) {
        if (isAdminPerm(sender)) {
            return true;
        }
        sender.sendMessage(CC.color("&cPas la permission."));
        return false;
    }

    private boolean isAdminPerm(CommandSender sender) {
        return sender.hasPermission("draftmc.tournament.admin") || sender.hasPermission("draftmc.admin");
    }

    private Boolean parseYesNo(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.toLowerCase(Locale.ROOT);
        if (v.equals("oui") || v.equals("o") || v.equals("yes") || v.equals("y") || v.equals("true") || v.equals("1")) {
            return Boolean.TRUE;
        }
        if (v.equals("non") || v.equals("n") || v.equals("no") || v.equals("false") || v.equals("0")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private int parse(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return def;
        }
    }

    private String join(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append("&7, &f");
            }
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if ("status".equalsIgnoreCase(command.getName())) {
            return out;
        }
        if (args.length == 1) {
            return prefix(args[0], Arrays.asList("help", "status", "invite", "accept", "deny", "leave", "kick",
                    "create", "delete", "info", "start", "launch", "stop", "setlobby", "list", "config", "team", "arena"));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("invite") || sub.equals("kick"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(player.getName());
                }
            }
            return out;
        }
        if (args.length == 2 && sub.equals("accept")) {
            List<String> names = new ArrayList<String>();
            if (sender instanceof Player) {
                Player player = (Player) sender;
                for (TournamentTeam team : manager.teams().values()) {
                    if (team.hasInvite(player.getUniqueId())) {
                        names.add(team.name());
                    }
                }
            }
            return prefix(args[1], names);
        }
        if (args.length == 2 && sub.equals("team")) {
            return prefix(args[1], isAdminPerm(sender)
                    ? Arrays.asList("create", "add", "remove", "list")
                    : Arrays.asList("create", "list"));
        }
        if (!isAdminPerm(sender)) {
            return out;
        }
        if (args.length == 2) {
            if (Arrays.asList("delete", "info", "start", "stop", "config").contains(sub)) {
                return prefix(args[1], new ArrayList<String>(manager.tournaments().keySet()));
            }
            if (sub.equals("arena")) {
                return prefix(args[1], Arrays.asList("create", "setspawn1", "setspawn2", "setspectator", "delete", "list"));
            }
        }
        if (args.length == 3 && sub.equals("config")) {
            return prefix(args[2], Arrays.asList("teamsize", "loserbracket"));
        }
        if (args.length == 3 && sub.equals("team") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return prefix(args[2], new ArrayList<String>(manager.teams().keySet()));
        }
        if (args.length == 3 && sub.equals("arena") && !args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("list")) {
            return prefix(args[2], new ArrayList<String>(manager.arenas().keySet()));
        }
        if (args.length == 4 && sub.equals("create")) {
            return prefix(args[3], Arrays.asList("oui", "non", "true", "false"));
        }
        if (args.length == 4 && sub.equals("config") && args[2].equalsIgnoreCase("loserbracket")) {
            return prefix(args[3], Arrays.asList("true", "false", "oui", "non"));
        }
        if (args.length == 4 && sub.equals("team") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[3].toLowerCase(Locale.ROOT))) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }

    private List<String> prefix(String token, List<String> values) {
        List<String> out = new ArrayList<String>();
        String p = token.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(value);
            }
        }
        return out;
    }
}
