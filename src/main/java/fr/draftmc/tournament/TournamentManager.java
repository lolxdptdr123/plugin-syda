package fr.draftmc.tournament;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import fr.draftmc.util.Chat;
import fr.draftmc.util.Locations;
import fr.draftmc.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TournamentManager implements Listener {
    private final Draftmc plugin;
    private final YamlFile file;
    private final TournamentKit kit;
    private final Map<String, TournamentDefinition> tournaments = new LinkedHashMap<String, TournamentDefinition>();
    private final Map<String, TournamentArena> arenas = new LinkedHashMap<String, TournamentArena>();
    private final Map<String, TournamentTeam> teams = new LinkedHashMap<String, TournamentTeam>();
    private final Map<UUID, String> playerTeam = new HashMap<UUID, String>();
    private final Map<UUID, TournamentSnapshot> snapshots = new HashMap<UUID, TournamentSnapshot>();
    private final java.util.Set<UUID> participants = new HashSet<UUID>();
    private final List<TournamentMatch> active = new ArrayList<TournamentMatch>();
    private TournamentDefinition running;
    private Phase phase = Phase.IDLE;
    private BukkitTask task;
    private final TournamentScoreboard scoreboard;
    private final Map<UUID, CreateDraft> createDrafts = new HashMap<UUID, CreateDraft>();
    private final Set<UUID> pendingLobby = new HashSet<UUID>();

    public TournamentManager(Draftmc plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "tournament.yml");
        this.kit = new TournamentKit(this);
        this.scoreboard = new TournamentScoreboard(this);
        load();
        Bukkit.getPluginManager().registerEvents(new TournamentListener(this), plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    public Draftmc plugin() {
        return plugin;
    }

    public org.bukkit.configuration.file.FileConfiguration config() {
        return file.get();
    }

    public TournamentKit kit() {
        return kit;
    }

    public boolean running() {
        return running != null;
    }

    public boolean signup() {
        return phase == Phase.SIGNUP;
    }

    public boolean fighting() {
        return phase == Phase.FIGHTING;
    }

    public TournamentDefinition runningDef() {
        return running;
    }

    public TournamentTeam teamOf(UUID uuid) {
        String id = playerTeam.get(uuid);
        return id == null ? null : teams.get(id);
    }

    public TournamentMatch matchOf(UUID uuid) {
        for (int i = 0; i < active.size(); i++) {
            TournamentMatch match = active.get(i);
            if (match.hasPlayer(uuid)) {
                return match;
            }
        }
        return null;
    }

    public boolean isParticipant(UUID uuid) {
        return running != null && participants.contains(uuid);
    }

    public boolean isFighter(UUID uuid) {
        TournamentMatch match = matchOf(uuid);
        return match != null && match.fighting() && !match.dead().contains(uuid);
    }

    public void disable() {
        if (running != null) {
            stop(Bukkit.getConsoleSender(), running.name(), true);
        }
        if (task != null) {
            task.cancel();
        }
        createDrafts.clear();
    }

    public String prefix() {
        return CC.color(file.get().getString("prefix", "&8[&6Tournoi&8] &7"));
    }

    public void msg(CommandSender sender, String key, String... pairs) {
        String raw = file.get().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        sender.sendMessage(prefix() + CC.color(raw));
    }

    public void broadcast(String key, String... pairs) {
        String raw = file.get().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        Bukkit.broadcastMessage(prefix() + CC.color(raw));
    }

    public void load() {
        tournaments.clear();
        arenas.clear();
        teams.clear();
        playerTeam.clear();
        ConfigurationSection defs = file.get().getConfigurationSection("tournaments");
        if (defs != null) {
            for (String id : defs.getKeys(false)) {
                TournamentDefinition def = new TournamentDefinition(id);
                def.setTeamSize(defs.getInt(id + ".team-size", file.get().getInt("defaults.team-size", 1)));
                def.setMaxTeams(defs.getInt(id + ".teams", file.get().getInt("defaults.teams", 8)));
                def.setLoserBracket(defs.getBoolean(id + ".loser-bracket",
                        file.get().getBoolean("defaults.loser-bracket", false)));
                tournaments.put(id.toLowerCase(Locale.ROOT), def);
            }
        }
        ConfigurationSection arenaSec = file.get().getConfigurationSection("arenas");
        if (arenaSec != null) {
            for (String id : arenaSec.getKeys(false)) {
                TournamentArena arena = new TournamentArena(id);
                arena.setSpawn1(Locations.deserialize(arenaSec.getString(id + ".spawn1")));
                arena.setSpawn2(Locations.deserialize(arenaSec.getString(id + ".spawn2")));
                arena.setSpectator(Locations.deserialize(arenaSec.getString(id + ".spectator")));
                arenas.put(id.toLowerCase(Locale.ROOT), arena);
            }
        }
        ConfigurationSection teamSec = file.get().getConfigurationSection("teams");
        if (teamSec != null) {
            for (String id : teamSec.getKeys(false)) {
                TournamentTeam team = new TournamentTeam(id);
                String leaderRaw = teamSec.getString(id + ".leader");
                if (leaderRaw != null && !leaderRaw.isEmpty()) {
                    try {
                        team.setLeader(UUID.fromString(leaderRaw));
                    } catch (Exception ignored) {
                    }
                }
                for (String raw : teamSec.getStringList(id + ".members")) {
                    try {
                        UUID uuid = UUID.fromString(raw);
                        team.add(uuid);
                        playerTeam.put(uuid, id.toLowerCase(Locale.ROOT));
                    } catch (Exception ignored) {
                    }
                }
                teams.put(id.toLowerCase(Locale.ROOT), team);
            }
        }
        kit.load();
    }

    public void save() {
        file.get().set("tournaments", null);
        for (TournamentDefinition def : tournaments.values()) {
            String path = "tournaments." + def.name();
            file.get().set(path + ".team-size", def.teamSize());
            file.get().set(path + ".teams", def.maxTeams());
            file.get().set(path + ".loser-bracket", def.loserBracket());
        }
        file.get().set("arenas", null);
        for (TournamentArena arena : arenas.values()) {
            String path = "arenas." + arena.name();
            file.get().set(path + ".spawn1", Locations.serialize(arena.spawn1()));
            file.get().set(path + ".spawn2", Locations.serialize(arena.spawn2()));
            file.get().set(path + ".spectator", Locations.serialize(arena.spectator()));
        }
        file.get().set("teams", null);
        for (TournamentTeam team : teams.values()) {
            List<String> members = new ArrayList<String>();
            for (UUID uuid : team.members()) {
                members.add(uuid.toString());
            }
            file.get().set("teams." + team.name() + ".members", members);
            if (team.leader() != null) {
                file.get().set("teams." + team.name() + ".leader", team.leader().toString());
            }
        }
        file.save();
    }

    public TournamentDefinition getTournament(String name) {
        return name == null ? null : tournaments.get(name.toLowerCase(Locale.ROOT));
    }

    public TournamentArena getArena(String name) {
        return name == null ? null : arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public TournamentTeam getTeam(String name) {
        return name == null ? null : teams.get(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, TournamentDefinition> tournaments() {
        return tournaments;
    }

    public Map<String, TournamentArena> arenas() {
        return arenas;
    }

    public Map<String, TournamentTeam> teams() {
        return teams;
    }

    public boolean createTournament(CommandSender sender, String name) {
        if (sender instanceof Player) {
            return beginCreate((Player) sender, name);
        }
        msg(sender, "create-usage");
        return true;
    }

    public boolean createTournament(CommandSender sender, String name, int teamSize, boolean loserBracket) {
        String id = sanitize(name);
        if (id.isEmpty()) {
            msg(sender, "already-exists");
            return true;
        }
        if (tournaments.containsKey(id)) {
            msg(sender, "already-exists");
            return true;
        }
        TournamentDefinition def = new TournamentDefinition(id);
        def.setTeamSize(teamSize);
        def.setMaxTeams(0);
        def.setLoserBracket(loserBracket);
        tournaments.put(id, def);
        save();
        if (sender instanceof Player) {
            createDrafts.remove(((Player) sender).getUniqueId());
        }
        msg(sender, "created",
                "name", def.name(),
                "size", String.valueOf(def.teamSize()),
                "lb", String.valueOf(def.loserBracket()));
        return true;
    }

    public boolean beginCreate(Player player, String name) {
        String id = sanitize(name);
        if (id.isEmpty() || tournaments.containsKey(id)) {
            msg(player, "already-exists");
            return true;
        }
        CreateDraft draft = new CreateDraft();
        draft.name = id;
        draft.step = 0;
        createDrafts.put(player.getUniqueId(), draft);
        msg(player, "create-ask-size");
        return true;
    }

    public boolean hasCreateDraft(UUID uuid) {
        return createDrafts.containsKey(uuid);
    }

    public boolean handleCreateChat(Player player, String raw) {
        CreateDraft draft = createDrafts.get(player.getUniqueId());
        if (draft == null) {
            return false;
        }
        String text = raw == null ? "" : raw.trim();
        if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("annuler")) {
            createDrafts.remove(player.getUniqueId());
            msg(player, "create-cancelled");
            return true;
        }
        if (draft.step == 0) {
            int size = parsePositive(text);
            if (size < 1) {
                msg(player, "create-invalid");
                msg(player, "create-ask-size");
                return true;
            }
            draft.teamSize = size;
            draft.step = 1;
            msg(player, "create-ask-lb");
            return true;
        }
        Boolean lb = parseYesNo(text);
        if (lb == null) {
            msg(player, "create-invalid");
            msg(player, "create-ask-lb");
            return true;
        }
        createTournament(player, draft.name, draft.teamSize, lb.booleanValue());
        return true;
    }

    public void cancelCreate(UUID uuid) {
        createDrafts.remove(uuid);
    }

    private int parsePositive(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return -1;
        }
    }

    private Boolean parseYesNo(String raw) {
        String v = raw.toLowerCase(Locale.ROOT);
        if (v.equals("oui") || v.equals("o") || v.equals("yes") || v.equals("y") || v.equals("true") || v.equals("1")) {
            return Boolean.TRUE;
        }
        if (v.equals("non") || v.equals("n") || v.equals("no") || v.equals("false") || v.equals("0")) {
            return Boolean.FALSE;
        }
        return null;
    }

    public boolean deleteTournament(CommandSender sender, String name) {
        TournamentDefinition def = getTournament(name);
        if (def == null) {
            msg(sender, "not-found");
            return true;
        }
        if (running == def) {
            stop(sender, def.name(), true);
        }
        tournaments.remove(def.name().toLowerCase(Locale.ROOT));
        save();
        msg(sender, "deleted", "name", def.name());
        return true;
    }

    public void info(CommandSender sender, String name) {
        TournamentDefinition def = getTournament(name);
        if (def == null) {
            msg(sender, "not-found");
            return;
        }
        sender.sendMessage(prefix() + CC.color("&6" + def.name()));
        sender.sendMessage(CC.color("&7Team size : &e" + def.teamSize()));
        sender.sendMessage(CC.color("&7Équipes : &eillimité"));
        sender.sendMessage(CC.color("&7Loser bracket : &e" + def.loserBracket()));
        sender.sendMessage(CC.color("&7État : " + phaseLabel(def)));
    }

    public boolean start(CommandSender sender, String name) {
        if (running != null) {
            msg(sender, phase == Phase.SIGNUP ? "already-signup" : "already-running");
            return true;
        }
        TournamentDefinition def = getTournament(name);
        if (def == null) {
            msg(sender, "not-found");
            return true;
        }
        running = def;
        phase = Phase.SIGNUP;
        participants.clear();
        for (TournamentTeam team : teams.values()) {
            participants.addAll(team.members());
        }
        broadcast("signup",
                "name", def.name(),
                "size", String.valueOf(def.teamSize()),
                "lb", String.valueOf(def.loserBracket()));
        scoreboard.start();
        return true;
    }

    public boolean launch(CommandSender sender) {
        if (running == null || phase != Phase.SIGNUP) {
            msg(sender, "not-signup");
            return true;
        }
        if (readyArenas().isEmpty()) {
            msg(sender, "not-enough-arenas");
            return true;
        }
        List<TournamentTeam> ready = readyTeams(running);
        if (ready.isEmpty()) {
            msg(sender, "no-teams", "size", String.valueOf(running.teamSize()));
            return true;
        }
        for (TournamentTeam team : teams.values()) {
            team.setBracket(TournamentTeam.Bracket.ELIMINATED);
            team.clearInvites();
        }
        for (TournamentTeam team : ready) {
            team.setBracket(TournamentTeam.Bracket.WINNERS);
        }
        participants.clear();
        for (TournamentTeam team : ready) {
            participants.addAll(team.members());
        }
        phase = Phase.FIGHTING;
        broadcast("launched", "name", running.name());
        fillRound();
        return true;
    }

    public boolean stop(CommandSender sender, String name, boolean silent) {
        if (running == null) {
            if (!silent) {
                msg(sender, "not-running");
            }
            return true;
        }
        if (name != null && !running.name().equalsIgnoreCase(name)) {
            msg(sender, "not-found");
            return true;
        }
        String id = running.name();
        List<TournamentMatch> copy = new ArrayList<TournamentMatch>(active);
        for (int i = 0; i < copy.size(); i++) {
            endMatch(copy.get(i), null, true);
        }
        scoreboard.stop();
        running = null;
        phase = Phase.IDLE;
        active.clear();
        snapshots.clear();
        participants.clear();
        clearAllTeams();
        if (!silent) {
            broadcast("stopped", "name", id);
        }
        return true;
    }

    public void sendStatus(CommandSender sender) {
        if (running == null) {
            msg(sender, "no-status");
            return;
        }
        sender.sendMessage(prefix() + CC.color(file.get().getString("messages.status-header", "&6Tournoi {name}")
                .replace("{name}", running.name())));
        if (phase == Phase.SIGNUP) {
            sender.sendMessage(CC.color("&eInscriptions ouvertes &7- teamsize &f" + running.teamSize()));
            if (teams.isEmpty()) {
                sender.sendMessage(CC.color("&7Aucune équipe. &e/tournament team create <nom>"));
                return;
            }
            for (TournamentTeam team : teams.values()) {
                sender.sendMessage(CC.color("&e" + team.name() + " &8- &f" + team.members().size()
                        + "&7/" + running.teamSize()));
            }
            return;
        }
        boolean any = false;
        for (TournamentTeam team : teams.values()) {
            if (team.eliminated()) {
                continue;
            }
            any = true;
            TournamentMatch match = matchOfTeam(team);
            if (match != null && match.fighting()) {
                msg(sender, "status-fight",
                        "team", team.name(),
                        "opponent", match.opponent(team).name(),
                        "arena", match.arena().name());
            } else if (match != null) {
                msg(sender, "status-fight",
                        "team", team.name(),
                        "opponent", match.opponent(team).name() + " (countdown)",
                        "arena", match.arena().name());
            } else {
                String extra = team.bracket() == TournamentTeam.Bracket.LOSERS ? " &8(LB)" : "";
                sender.sendMessage(prefix() + CC.color(file.get().getString("messages.status-waiting",
                        "&7En attente : &f{team}").replace("{team}", team.name() + extra)));
            }
        }
        if (!any) {
            msg(sender, "status-empty");
        }
    }

    public boolean createTeam(CommandSender sender, String name) {
        String id = sanitize(name);
        if (id.isEmpty() || teams.containsKey(id)) {
            msg(sender, "already-exists");
            return true;
        }
        if (fighting()) {
            msg(sender, "already-running");
            return true;
        }
        TournamentTeam team = new TournamentTeam(id);
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (teamOf(player.getUniqueId()) != null) {
                msg(player, "already-in-team");
                return true;
            }
            team.setLeader(player.getUniqueId());
            team.add(player.getUniqueId());
            playerTeam.put(player.getUniqueId(), id);
            if (signup()) {
                participants.add(player.getUniqueId());
            }
        }
        teams.put(id, team);
        save();
        msg(sender, "team-created", "team", id);
        return true;
    }

    public boolean addPlayer(CommandSender sender, String teamName, String playerName) {
        TournamentTeam team = getTeam(teamName);
        if (team == null) {
            msg(sender, "not-found");
            return true;
        }
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            plugin.msg(sender, "&cJoueur hors-ligne.");
            return true;
        }
        TournamentTeam existing = teamOf(target.getUniqueId());
        if (existing != null) {
            existing.remove(target.getUniqueId());
            playerTeam.remove(target.getUniqueId());
        }
        team.add(target.getUniqueId());
        playerTeam.put(target.getUniqueId(), team.name().toLowerCase(Locale.ROOT));
        save();
        msg(sender, "team-added", "player", target.getName(), "team", team.name());
        return true;
    }

    public boolean removePlayer(CommandSender sender, String teamName, String playerName) {
        TournamentTeam team = getTeam(teamName);
        if (team == null) {
            msg(sender, "not-found");
            return true;
        }
        Player target = Bukkit.getPlayer(playerName);
        UUID uuid = target != null ? target.getUniqueId() : plugin.data().findUuidByString("name", playerName);
        if (uuid == null) {
            plugin.msg(sender, "&cJoueur introuvable.");
            return true;
        }
        team.remove(uuid);
        playerTeam.remove(uuid);
        save();
        msg(sender, "team-removed", "player", plugin.data().nameOf(uuid), "team", team.name());
        return true;
    }

    public boolean invite(Player leader, Player target) {
        if (fighting()) {
            msg(leader, "already-running");
            return true;
        }
        if (target == null || !target.isOnline()) {
            plugin.msg(leader, "&cJoueur hors-ligne.");
            return true;
        }
        if (leader.getUniqueId().equals(target.getUniqueId())) {
            msg(leader, "invite-self");
            return true;
        }
        TournamentTeam team = teamOf(leader.getUniqueId());
        if (team == null) {
            msg(leader, "need-team");
            return true;
        }
        if (!team.isLeader(leader.getUniqueId())) {
            msg(leader, "not-leader");
            return true;
        }
        if (teamOf(target.getUniqueId()) != null) {
            msg(leader, "target-in-team", "player", target.getName());
            return true;
        }
        int size = currentTeamSize();
        if (team.members().size() >= size) {
            msg(leader, "team-full", "size", String.valueOf(size));
            return true;
        }
        team.invite(target.getUniqueId());
        Chat.sendClick(target,
                prefix() + CC.color(file.get().getString("messages.invite-received",
                        "&e{leader} &7t'invite dans &6{team}&7.")
                        .replace("{leader}", leader.getName())
                        .replace("{team}", team.name())),
                "&a[Accepter]",
                "&eClique pour rejoindre &6" + team.name(),
                "/tournament accept " + team.name());
        msg(leader, "invite-sent", "player", target.getName(), "team", team.name());
        return true;
    }

    public boolean accept(Player player, String teamName) {
        if (fighting()) {
            msg(player, "already-running");
            return true;
        }
        if (teamOf(player.getUniqueId()) != null) {
            msg(player, "already-in-team");
            return true;
        }
        TournamentTeam team = teamName == null || teamName.isEmpty()
                ? invitedTeamOf(player.getUniqueId())
                : getTeam(teamName);
        if (team == null || !team.hasInvite(player.getUniqueId())) {
            msg(player, "no-invite");
            return true;
        }
        int size = currentTeamSize();
        if (team.members().size() >= size) {
            team.clearInvite(player.getUniqueId());
            msg(player, "team-full", "size", String.valueOf(size));
            return true;
        }
        TournamentTeam existing = teamOf(player.getUniqueId());
        if (existing != null) {
            existing.remove(player.getUniqueId());
            playerTeam.remove(player.getUniqueId());
        }
        team.add(player.getUniqueId());
        playerTeam.put(player.getUniqueId(), team.name().toLowerCase(Locale.ROOT));
        if (signup()) {
            participants.add(player.getUniqueId());
        }
        save();
        msg(player, "invite-accepted", "team", team.name());
        Player lead = team.leader() == null ? null : Bukkit.getPlayer(team.leader());
        if (lead != null) {
            msg(lead, "member-joined", "player", player.getName(), "team", team.name());
        }
        return true;
    }

    public boolean deny(Player player) {
        boolean any = false;
        for (TournamentTeam team : teams.values()) {
            if (team.hasInvite(player.getUniqueId())) {
                team.clearInvite(player.getUniqueId());
                any = true;
            }
        }
        msg(player, any ? "invite-denied" : "no-invite");
        return true;
    }

    public boolean leave(Player player) {
        if (fighting() && isParticipant(player.getUniqueId())) {
            msg(player, "already-running");
            return true;
        }
        TournamentTeam team = teamOf(player.getUniqueId());
        if (team == null) {
            msg(player, "need-team");
            return true;
        }
        team.remove(player.getUniqueId());
        playerTeam.remove(player.getUniqueId());
        participants.remove(player.getUniqueId());
        if (team.members().isEmpty()) {
            teams.remove(team.name().toLowerCase(Locale.ROOT));
            msg(player, "team-disbanded", "team", team.name());
        } else {
            msg(player, "left-team", "team", team.name());
            Player lead = team.leader() == null ? null : Bukkit.getPlayer(team.leader());
            if (lead != null) {
                msg(lead, "member-left", "player", player.getName(), "team", team.name());
            }
        }
        save();
        return true;
    }

    public boolean kick(Player leader, String playerName) {
        if (fighting()) {
            msg(leader, "already-running");
            return true;
        }
        TournamentTeam team = teamOf(leader.getUniqueId());
        if (team == null) {
            msg(leader, "need-team");
            return true;
        }
        if (!team.isLeader(leader.getUniqueId())) {
            msg(leader, "not-leader");
            return true;
        }
        Player target = Bukkit.getPlayer(playerName);
        UUID uuid = target != null ? target.getUniqueId() : plugin.data().findUuidByString("name", playerName);
        if (uuid == null || !team.contains(uuid)) {
            plugin.msg(leader, "&cCe joueur n'est pas dans ton équipe.");
            return true;
        }
        if (uuid.equals(leader.getUniqueId())) {
            return leave(leader);
        }
        team.remove(uuid);
        playerTeam.remove(uuid);
        participants.remove(uuid);
        save();
        msg(leader, "team-removed", "player", plugin.data().nameOf(uuid), "team", team.name());
        if (target != null) {
            msg(target, "kicked", "team", team.name());
        }
        return true;
    }

    private TournamentTeam invitedTeamOf(UUID uuid) {
        TournamentTeam found = null;
        for (TournamentTeam team : teams.values()) {
            if (team.hasInvite(uuid)) {
                if (found != null) {
                    return null;
                }
                found = team;
            }
        }
        return found;
    }

    public boolean allowFactionFriendlyFire(Player a, Player b) {
        if (a == null || b == null || !fighting()) {
            return false;
        }
        TournamentMatch match = matchOf(a.getUniqueId());
        if (match == null || !match.fighting() || !match.hasPlayer(b.getUniqueId())) {
            return false;
        }
        TournamentTeam teamA = match.teamOf(a.getUniqueId());
        TournamentTeam teamB = match.teamOf(b.getUniqueId());
        return teamA != null && teamB != null && teamA != teamB;
    }

    public int currentTeamSize() {
        if (running != null) {
            return running.teamSize();
        }
        if (tournaments.size() == 1) {
            return tournaments.values().iterator().next().teamSize();
        }
        return Math.max(1, file.get().getInt("defaults.team-size", 1));
    }

    public boolean createArena(CommandSender sender, String name) {
        String id = sanitize(name);
        if (id.isEmpty() || arenas.containsKey(id)) {
            msg(sender, "already-exists");
            return true;
        }
        arenas.put(id, new TournamentArena(id));
        save();
        msg(sender, "arena-created", "arena", id);
        return true;
    }

    public boolean deleteArena(CommandSender sender, String name) {
        TournamentArena arena = getArena(name);
        if (arena == null) {
            msg(sender, "not-found");
            return true;
        }
        if (arena.match() != null) {
            plugin.msg(sender, "&cCette arène est occupée.");
            return true;
        }
        arenas.remove(arena.name().toLowerCase(Locale.ROOT));
        save();
        msg(sender, "arena-deleted", "arena", arena.name());
        return true;
    }

    public boolean setArenaSpawn(CommandSender sender, String name, String which) {
        if (!(sender instanceof Player)) {
            plugin.msg(sender, "&cJoueur uniquement.");
            return true;
        }
        TournamentArena arena = getArena(name);
        if (arena == null) {
            msg(sender, "not-found");
            return true;
        }
        Location loc = ((Player) sender).getLocation();
        if ("spawn1".equals(which)) {
            arena.setSpawn1(loc);
        } else if ("spawn2".equals(which)) {
            arena.setSpawn2(loc);
        } else {
            arena.setSpectator(loc);
        }
        save();
        msg(sender, "arena-spawn", "which", which, "arena", arena.name());
        return true;
    }

    public boolean setLobby(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.msg(sender, "&cJoueur uniquement.");
            return true;
        }
        Location loc = ((Player) sender).getLocation();
        file.get().set("lobby", Locations.serialize(loc));
        file.save();
        msg(sender, "lobby-set");
        return true;
    }

    public void onEliminated(Player player) {
        if (running == null) {
            return;
        }
        TournamentMatch match = matchOf(player.getUniqueId());
        if (match == null) {
            return;
        }
        match.dead().add(player.getUniqueId());
        pendingLobby.add(player.getUniqueId());
        if (!match.alive(match.teamOf(player.getUniqueId()))) {
            TournamentTeam loser = match.teamOf(player.getUniqueId());
            TournamentTeam winner = match.opponent(loser);
            endMatch(match, winner, false);
        }
    }

    public Location deathRespawnLocation(Player player) {
        UUID id = player.getUniqueId();
        TournamentMatch match = matchOf(id);
        if (!pendingLobby.contains(id) && (match == null || !match.dead().contains(id))) {
            return null;
        }
        return endFightLocation(match != null ? match.arena().spectator() : null);
    }

    public void afterDeathRespawn(final Player player) {
        UUID id = player.getUniqueId();
        TournamentMatch match = matchOf(id);
        if (!pendingLobby.remove(id) && (match == null || !match.dead().contains(id))) {
            return;
        }
        final Location dest = endFightLocation(match != null ? match.arena().spectator() : null);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    return;
                }
                kit.clear(player);
                player.setGameMode(GameMode.SURVIVAL);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setFireTicks(0);
                if (dest != null) {
                    player.teleport(dest);
                }
            }
        }, 2L);
    }

    private void tick() {
        if (running == null) {
            return;
        }
        tickEffects();
        for (int i = 0; i < active.size(); i++) {
            TournamentMatch match = active.get(i);
            if (match.fighting()) {
                continue;
            }
            match.tickCountdown();
            if (match.countdown() > 0) {
                announceCountdown(match);
            } else {
                match.setFighting(true);
                play(match, Sound.NOTE_PLING);
            }
        }
        if (active.isEmpty()) {
            fillRound();
        }
    }

    private void tickEffects() {
        if (!file.get().getBoolean("effects.enabled", true) || phase != Phase.FIGHTING) {
            return;
        }
        int speed = file.get().getInt("effects.speed", 1);
        int strength = file.get().getInt("effects.strength", 0);
        boolean fire = file.get().getBoolean("effects.fire-resistance", true);
        double radius = file.get().getDouble("effects.radius", 80);
        double r2 = radius * radius;
        for (int i = 0; i < active.size(); i++) {
            TournamentMatch match = active.get(i);
            Location center = match.arena().midpoint();
            for (Player player : matchPlayers(match)) {
                if (match.dead().contains(player.getUniqueId())) {
                    continue;
                }
                if (radius > 0 && center != null && center.getWorld() != null
                        && player.getWorld() == center.getWorld()
                        && player.getLocation().distanceSquared(center) > r2) {
                    continue;
                }
                applyEffect(player, PotionEffectType.SPEED, speed);
                applyEffect(player, PotionEffectType.INCREASE_DAMAGE, strength);
                if (fire) {
                    applyEffect(player, PotionEffectType.FIRE_RESISTANCE, 0);
                }
            }
        }
    }

    private void applyEffect(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, 8 * 20, amplifier, true, false), true);
    }

    private void announceCountdown(TournamentMatch match) {
        String text = CC.color("&e" + match.countdown());
        for (Player player : matchPlayers(match)) {
            player.sendMessage(prefix() + text);
        }
    }

    private void fillRound() {
        if (running == null || phase != Phase.FIGHTING) {
            return;
        }
        List<TournamentTeam> wb = bracketTeams(TournamentTeam.Bracket.WINNERS);
        List<TournamentTeam> lb = bracketTeams(TournamentTeam.Bracket.LOSERS);
        if (wb.size() == 1 && (lb.isEmpty() || !running.loserBracket())) {
            champion(wb.get(0));
            return;
        }
        if (running.loserBracket() && wb.size() == 1 && lb.size() == 1) {
            startMatch(wb.get(0), lb.get(0), TournamentMatch.Kind.GRAND_FINAL);
            return;
        }
        if (wb.size() >= 2) {
            pair(wb, TournamentMatch.Kind.WINNERS);
        }
        if (running.loserBracket() && lb.size() >= 2) {
            pair(lb, TournamentMatch.Kind.LOSERS);
        }
        if (active.isEmpty() && wb.size() == 1 && lb.size() > 1) {
            pair(lb, TournamentMatch.Kind.LOSERS);
        }
        if (active.isEmpty() && wb.size() <= 1 && lb.size() <= 1) {
            if (wb.size() == 1) {
                champion(wb.get(0));
            } else if (lb.size() == 1) {
                champion(lb.get(0));
            }
        }
    }

    private void pair(List<TournamentTeam> pool, TournamentMatch.Kind kind) {
        List<TournamentTeam> waiting = new ArrayList<TournamentTeam>();
        for (int i = 0; i < pool.size(); i++) {
            if (matchOfTeam(pool.get(i)) == null) {
                waiting.add(pool.get(i));
            }
        }
        for (int i = 0; i + 1 < waiting.size(); i += 2) {
            if (!startMatch(waiting.get(i), waiting.get(i + 1), kind)) {
                break;
            }
        }
    }

    private boolean startMatch(TournamentTeam a, TournamentTeam b, TournamentMatch.Kind kind) {
        TournamentArena arena = freeArena();
        if (arena == null) {
            return false;
        }
        int countdown = Math.max(0, file.get().getInt("countdown-seconds", 5));
        TournamentMatch match = new TournamentMatch(a, b, arena, kind, countdown);
        arena.setMatch(match);
        active.add(match);
        teleportTeam(a, arena.spawn1(), match);
        teleportTeam(b, arena.spawn2(), match);
        broadcast("match-start", "a", a.name(), "b", b.name(), "arena", arena.name());
        if (countdown <= 0) {
            match.setFighting(true);
        }
        return true;
    }

    private void teleportTeam(TournamentTeam team, Location spawn, TournamentMatch match) {
        for (UUID uuid : team.members()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                match.dead().add(uuid);
                continue;
            }
            if (!snapshots.containsKey(uuid)) {
                snapshots.put(uuid, new TournamentSnapshot(player));
            }
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.teleport(spawn);
            kit.give(player);
        }
    }

    private void endMatch(TournamentMatch match, TournamentTeam winner, boolean abort) {
        if (!active.remove(match)) {
            return;
        }
        match.arena().setMatch(null);
        cleanupArena(match.arena());
        if (!abort && winner != null) {
            TournamentTeam loser = match.opponent(winner);
            broadcast("match-win", "winner", winner.name(), "loser", loser.name());
            if (match.kind() == TournamentMatch.Kind.GRAND_FINAL) {
                loser.setBracket(TournamentTeam.Bracket.ELIMINATED);
                broadcast("eliminated", "team", loser.name());
                restoreMatchPlayers(match);
                champion(winner);
                return;
            }
            if (match.kind() == TournamentMatch.Kind.WINNERS && running != null && running.loserBracket()) {
                loser.setBracket(TournamentTeam.Bracket.LOSERS);
                broadcast("losers", "team", loser.name());
            } else {
                loser.setBracket(TournamentTeam.Bracket.ELIMINATED);
                broadcast("eliminated", "team", loser.name());
            }
        }
        restoreMatchPlayers(match);
        if (!abort && running != null) {
            fillRound();
        }
    }

    private void champion(TournamentTeam team) {
        if (running == null) {
            return;
        }
        long tokens = file.get().getLong("winner-tokens", 200);
        for (UUID uuid : team.members()) {
            plugin.tokens().add(uuid, tokens);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.msg(player, "&6+" + tokens + " tokens &7(victoire tournoi).");
            }
        }
        broadcast("champion", "team", team.name(), "tokens", String.valueOf(tokens));
        List<TournamentMatch> copy = new ArrayList<TournamentMatch>(active);
        for (int i = 0; i < copy.size(); i++) {
            restoreMatchPlayers(copy.get(i));
            copy.get(i).arena().setMatch(null);
        }
        active.clear();
        scoreboard.stop();
        running = null;
        phase = Phase.IDLE;
        snapshots.clear();
        participants.clear();
        clearAllTeams();
    }

    private void clearAllTeams() {
        teams.clear();
        playerTeam.clear();
        save();
    }

    private void restoreMatchPlayers(TournamentMatch match) {
        Set<UUID> seen = new HashSet<UUID>();
        Location spec = match.arena().spectator();
        restoreTeam(match.a(), seen, spec);
        restoreTeam(match.b(), seen, spec);
    }

    private void restoreTeam(TournamentTeam team, Set<UUID> seen, Location spectator) {
        boolean clearInv = file.get().getBoolean("restore.clear-inventory", true);
        boolean clearFx = file.get().getBoolean("restore.clear-effects", true);
        double health = file.get().getDouble("restore.health", 20);
        int food = file.get().getInt("restore.food", 20);
        float sat = (float) file.get().getDouble("restore.saturation", 20);
        GameMode gm = GameMode.SURVIVAL;
        try {
            gm = GameMode.valueOf(file.get().getString("restore.gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
        }
        for (UUID uuid : team.members()) {
            if (!seen.add(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            snapshots.remove(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (player.isDead()) {
                pendingLobby.add(uuid);
                continue;
            }
            if (clearInv) {
                kit.clear(player);
            } else if (clearFx) {
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
            }
            player.setGameMode(gm);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setFireTicks(0);
            player.setHealth(Math.min(Math.max(1D, health), player.getMaxHealth()));
            player.setFoodLevel(food);
            player.setSaturation(sat);
            Location dest = endFightLocation(spectator);
            if (dest != null) {
                player.teleport(dest);
            }
            player.updateInventory();
        }
    }

    private Location endFightLocation(Location spectator) {
        Location lobby = Locations.deserialize(file.get().getString("lobby"));
        if (lobby != null && lobby.getWorld() != null) {
            return lobby;
        }
        String warpName = file.get().getString("restore.warp", "tournois");
        if (plugin.warps() != null && warpName != null && !warpName.isEmpty()) {
            Location warp = plugin.warps().location(warpName);
            if (warp != null && warp.getWorld() != null) {
                return warp;
            }
        }
        return spectator;
    }

    public void cleanupArena(TournamentArena arena) {
        Location center = arena.midpoint();
        if (center == null || center.getWorld() == null) {
            return;
        }
        double radius = file.get().getDouble("cleanup-radius", 80);
        double r2 = radius * radius;
        World world = center.getWorld();
        for (Entity entity : new ArrayList<Entity>(world.getEntities())) {
            if (entity.getLocation().distanceSquared(center) > r2) {
                continue;
            }
            if (entity instanceof Player) {
                continue;
            }
            if (entity instanceof Projectile || entity instanceof Item || entity instanceof org.bukkit.entity.ExperienceOrb) {
                entity.remove();
                continue;
            }
            if (!(entity instanceof org.bukkit.entity.LivingEntity)) {
                entity.remove();
            }
        }
    }

    private List<TournamentTeam> readyTeams(TournamentDefinition def) {
        List<TournamentTeam> ready = new ArrayList<TournamentTeam>();
        for (TournamentTeam team : teams.values()) {
            int online = 0;
            for (UUID uuid : team.members()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    online++;
                }
            }
            if (online >= 1) {
                ready.add(team);
            }
        }
        return ready;
    }

    private List<TournamentArena> readyArenas() {
        List<TournamentArena> out = new ArrayList<TournamentArena>();
        for (TournamentArena arena : arenas.values()) {
            if (arena.ready()) {
                out.add(arena);
            }
        }
        return out;
    }

    private TournamentArena freeArena() {
        for (TournamentArena arena : arenas.values()) {
            if (arena.ready() && arena.free()) {
                return arena;
            }
        }
        return null;
    }

    private List<TournamentTeam> bracketTeams(TournamentTeam.Bracket bracket) {
        List<TournamentTeam> out = new ArrayList<TournamentTeam>();
        for (TournamentTeam team : teams.values()) {
            if (team.bracket() == bracket) {
                out.add(team);
            }
        }
        return out;
    }

    private TournamentMatch matchOfTeam(TournamentTeam team) {
        for (int i = 0; i < active.size(); i++) {
            TournamentMatch match = active.get(i);
            if (match.a() == team || match.b() == team) {
                return match;
            }
        }
        return null;
    }

    private List<Player> matchPlayers(TournamentMatch match) {
        List<Player> out = new ArrayList<Player>();
        addPlayers(match.a(), out);
        addPlayers(match.b(), out);
        return out;
    }

    private void addPlayers(TournamentTeam team, List<Player> out) {
        for (UUID uuid : team.members()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                out.add(player);
            }
        }
    }

    private void play(TournamentMatch match, Sound sound) {
        for (Player player : matchPlayers(match)) {
            player.playSound(player.getLocation(), sound, 1f, 1f);
        }
    }

    private String sanitize(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    private String phaseLabel(TournamentDefinition def) {
        if (running != def) {
            return "&7inactif";
        }
        if (phase == Phase.SIGNUP) {
            return "&einscriptions";
        }
        if (phase == Phase.FIGHTING) {
            return "&aen cours";
        }
        return "&7inactif";
    }

    public enum Phase {
        IDLE,
        SIGNUP,
        FIGHTING
    }

    private static class CreateDraft {
        private String name;
        private int step;
        private int teamSize;
    }
}
