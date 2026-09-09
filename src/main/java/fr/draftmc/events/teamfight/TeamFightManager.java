package fr.draftmc.events.teamfight;

import fr.draftmc.events.EventHub;
import fr.draftmc.events.EventType;
import fr.draftmc.util.CC;
import fr.draftmc.util.NmsTitles;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeamFightManager {
    private final TeamFightPlugin plugin;
    private final Map<String, TfTeam> teams = new LinkedHashMap<String, TfTeam>();
    private final Map<UUID, String> playerTeam = new HashMap<UUID, String>();
    private final Map<UUID, FightStats> stats = new HashMap<UUID, FightStats>();
    private final Set<UUID> fighters = new HashSet<UUID>();
    private final Set<UUID> deadThisFight = new HashSet<UUID>();
    private TeamFightState state = TeamFightState.WAITING;
    private final List<TfMatch> queue = new ArrayList<TfMatch>();
    private final List<TfTeam> roundWinners = new ArrayList<TfTeam>();
    private TfMatch current;
    private TfTeam tournamentWinner;
    private BukkitTask task;
    private BukkitTask effectsTask;
    private int countdownLeft;
    private int betweenLeft;

    public TeamFightManager(TeamFightPlugin plugin) {
        this.plugin = plugin;
    }

    public TeamFightState getState() {
        return state;
    }

    public boolean isBusy() {
        return state != TeamFightState.WAITING && state != TeamFightState.ENDED;
    }

    public TfMatch getCurrentMatch() {
        return current;
    }

    public List<TfTeam> getTeams() {
        return new ArrayList<TfTeam>(teams.values());
    }

    public TfTeam getTeam(String name) {
        return name == null ? null : teams.get(name.toLowerCase(Locale.ROOT));
    }

    public TfTeam teamOf(UUID uuid) {
        String id = playerTeam.get(uuid);
        return id == null ? null : teams.get(id);
    }

    public FightStats statsOf(UUID uuid) {
        FightStats existing = stats.get(uuid);
        if (existing == null) {
            existing = new FightStats();
            stats.put(uuid, existing);
        }
        return existing;
    }

    public boolean isFighter(UUID uuid) {
        return fighters.contains(uuid);
    }

    public boolean isDeadThisFight(UUID uuid) {
        return deadThisFight.contains(uuid);
    }

    public int rosterSize() {
        return Math.max(1, plugin.getConfig().getInt("roster-size", 8));
    }

    public int minRoster() {
        int min = plugin.getConfig().getInt("min-roster", 3);
        if (min < 1) {
            min = 1;
        }
        return Math.min(min, rosterSize());
    }

    public boolean isTeamReady(TfTeam team) {
        return team != null && !team.isTournamentOut() && team.getMembers().size() >= minRoster();
    }

    public boolean openRegistrations() {
        if (state != TeamFightState.WAITING && state != TeamFightState.ENDED) {
            return false;
        }
        reset();
        state = TeamFightState.REGISTRATION;
        plugin.broadcast("registration-open", null, null, 0);
        plugin.broadcast("registration-hint", null, null, 0);
        return true;
    }

    public boolean isReadyToLaunch() {
        return state == TeamFightState.REGISTRATION && readyTeams().size() >= 2;
    }

    public List<TfTeam> readyTeams() {
        List<TfTeam> ready = new ArrayList<TfTeam>();
        for (TfTeam team : teams.values()) {
            if (isTeamReady(team)) {
                ready.add(team);
            }
        }
        return ready;
    }

    public boolean launch() {
        if (!isReadyToLaunch()) {
            return false;
        }
        if (readSpawn("spawns.a") == null || readSpawn("spawns.b") == null || readSpawn("spawns.wait") == null) {
            plugin.broadcast("no-spawn", null, null, 0);
            return false;
        }
        List<TfTeam> ready = readyTeams();
        int max = Math.min(plugin.getConfig().getInt("max-teams", 8), ready.size());
        if (max % 2 == 1) {
            max--;
        }
        if (max < 2) {
            return false;
        }
        Collections.shuffle(ready);
        if (ready.size() > max) {
            ready = ready.subList(0, max);
        }
        queue.clear();
        roundWinners.clear();
        queue.addAll(buildRound(ready, roundName(ready.size())));
        plugin.getScoreboard().start();
        startNextMatch();
        return true;
    }

    public boolean stop() {
        if (state == TeamFightState.WAITING) {
            return false;
        }
        cancelTask();
        cleanupFight(true);
        state = TeamFightState.WAITING;
        plugin.getScoreboard().stop();
        plugin.broadcast("stopped", null, null, 0);
        EventHub hub = plugin.getHost().events();
        if (hub != null) {
            hub.clearActive(EventType.TEAMFIGHT, null);
        }
        reset();
        return true;
    }

    public String createDenyReason(Player leader) {
        if (leader == null) {
            return "need-faction";
        }
        if (state != TeamFightState.REGISTRATION) {
            return "not-open";
        }
        if (teamOf(leader.getUniqueId()) != null) {
            return "already-in-team";
        }
        String factionId = plugin.getEventFactionHook().getFactionId(leader);
        if (factionId == null) {
            return "need-faction";
        }
        if (teamOfFaction(factionId) != null) {
            return "faction-has-team";
        }
        if (teams.size() >= plugin.getConfig().getInt("max-teams", 8)) {
            return "too-many-teams";
        }
        return null;
    }

    public TfTeam teamOfFaction(String factionId) {
        if (factionId == null || factionId.isEmpty()) {
            return null;
        }
        for (TfTeam team : teams.values()) {
            if (factionId.equals(team.getFactionId())) {
                return team;
            }
        }
        return null;
    }

    public boolean sameFaction(Player player, TfTeam team) {
        if (player == null || team == null || team.getFactionId() == null) {
            return false;
        }
        String factionId = plugin.getEventFactionHook().getFactionId(player);
        return team.getFactionId().equals(factionId);
    }

    public TfTeam createTeam(Player leader) {
        if (createDenyReason(leader) != null) {
            return null;
        }
        String factionId = plugin.getEventFactionHook().getFactionId(leader);
        String display = plugin.getEventFactionHook().getFactionDisplayName(factionId);
        if (display == null || display.isEmpty()) {
            display = factionId;
        }
        String id = factionId.toLowerCase(Locale.ROOT).replace(" ", "");
        TfTeam team = new TfTeam(id, display, leader.getUniqueId(), factionId);
        teams.put(id, team);
        playerTeam.put(leader.getUniqueId(), id);
        return team;
    }

    public boolean invite(Player leader, Player target) {
        TfTeam team = teamOf(leader.getUniqueId());
        if (team == null || !team.isLeader(leader.getUniqueId())) {
            return false;
        }
        if (team.isFull(rosterSize())) {
            return false;
        }
        if (teamOf(target.getUniqueId()) != null) {
            return false;
        }
        if (!sameFaction(target, team)) {
            return false;
        }
        team.invite(target.getUniqueId());
        return true;
    }

    public boolean accept(Player player, String teamName) {
        if (state != TeamFightState.REGISTRATION) {
            return false;
        }
        if (teamOf(player.getUniqueId()) != null) {
            return false;
        }
        TfTeam team = getTeam(teamName);
        if (team == null || !team.hasInvite(player.getUniqueId())) {
            return false;
        }
        if (!sameFaction(player, team)) {
            return false;
        }
        if (!team.addMember(player.getUniqueId(), rosterSize())) {
            return false;
        }
        playerTeam.put(player.getUniqueId(), team.getId());
        plugin.broadcast("team-joined", team, player, team.getMembers().size());
        if (team.getMembers().size() == minRoster()) {
            plugin.broadcast("team-ready", team, null, team.getMembers().size());
        }
        return true;
    }

    public void onHit(Player damager, Player victim) {
        if (state != TeamFightState.FIGHTING || current == null) {
            return;
        }
        if (!fighters.contains(damager.getUniqueId()) || !fighters.contains(victim.getUniqueId())) {
            return;
        }
        TfTeam a = teamOf(damager.getUniqueId());
        TfTeam b = teamOf(victim.getUniqueId());
        if (a == null || b == null || a == b) {
            return;
        }
        statsOf(damager.getUniqueId()).addHit(victim.getUniqueId());
    }

    public void onPotionUsed(Player player, String type) {
        if (state != TeamFightState.FIGHTING) {
            return;
        }
        if (!fighters.contains(player.getUniqueId())) {
            return;
        }
        statsOf(player.getUniqueId()).addPotion(type);
    }

    public void onEliminated(Player victim) {
        if (state != TeamFightState.FIGHTING || current == null || victim == null) {
            return;
        }
        if (!fighters.contains(victim.getUniqueId()) || deadThisFight.contains(victim.getUniqueId())) {
            return;
        }
        deadThisFight.add(victim.getUniqueId());
        TfTeam team = teamOf(victim.getUniqueId());
        int alive = aliveCount(team);
        plugin.broadcast("eliminated", team, victim, alive);
        checkMatchEnd();
    }

    public boolean sameTeam(Player a, Player b) {
        TfTeam ta = teamOf(a.getUniqueId());
        TfTeam tb = teamOf(b.getUniqueId());
        return ta != null && ta == tb;
    }

    public int aliveCount(TfTeam team) {
        if (team == null) {
            return 0;
        }
        int n = 0;
        for (UUID uuid : team.getMembers()) {
            if (fighters.contains(uuid) && !deadThisFight.contains(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    n++;
                }
            }
        }
        return n;
    }

    private void startNextMatch() {
        if (queue.isEmpty()) {
            finishTournament();
            return;
        }
        current = queue.remove(0);
        stats.clear();
        fighters.clear();
        deadThisFight.clear();
        plugin.broadcastRaw(plugin.msg("match-announce")
                .replace("{round}", current.getRound())
                .replace("{teamA}", current.getTeamA().getName())
                .replace("{teamB}", current.getTeamB().getName()));
        countdownLeft = Math.max(0, plugin.getConfig().getInt("countdown-seconds", 5));
        state = TeamFightState.COUNTDOWN;
        cancelTask();
        task = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                tickCountdown();
            }
        }, 0L, 20L);
    }

    private void tickCountdown() {
        if (countdownLeft <= 0) {
            cancelTask();
            beginFight();
            return;
        }
        plugin.broadcastRaw(plugin.msg("countdown").replace("{seconds}", String.valueOf(countdownLeft)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            NmsTitles.send(player, CC.color("&e&l" + countdownLeft), CC.color("&7" + current.getRound()), 5, 20, 5);
            player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1f);
        }
        countdownLeft--;
    }

    private void beginFight() {
        Location a = readSpawn("spawns.a");
        Location b = readSpawn("spawns.b");
        if (a == null || b == null) {
            plugin.broadcast("no-spawn", null, null, 0);
            stop();
            return;
        }
        teleportTeam(current.getTeamA(), a);
        teleportTeam(current.getTeamB(), b);
        if (aliveCount(current.getTeamA()) == 0) {
            endMatch(current.getTeamB());
            return;
        }
        if (aliveCount(current.getTeamB()) == 0) {
            endMatch(current.getTeamA());
            return;
        }
        state = TeamFightState.FIGHTING;
        startEffectsTask();
        plugin.broadcastRaw(plugin.msg("fight-start")
                .replace("{teamA}", current.getTeamA().getName())
                .replace("{teamB}", current.getTeamB().getName()));
    }

    private void teleportTeam(TfTeam team, Location loc) {
        List<Player> online = team.onlineMembers();
        for (int i = 0; i < online.size(); i++) {
            Player player = online.get(i);
            player.teleport(offset(loc, i));
            player.setGameMode(GameMode.SURVIVAL);
            plugin.getKit().give(player);
            fighters.add(player.getUniqueId());
            stats.put(player.getUniqueId(), new FightStats());
        }
    }

    private Location offset(Location base, int index) {
        Location loc = base.clone();
        loc.add((index % 4) * 1.5, 0, (index / 4) * 1.5);
        return loc;
    }

    private void checkMatchEnd() {
        if (current == null || state != TeamFightState.FIGHTING) {
            return;
        }
        int a = aliveCount(current.getTeamA());
        int b = aliveCount(current.getTeamB());
        if (a <= 0) {
            endMatch(current.getTeamB());
        } else if (b <= 0) {
            endMatch(current.getTeamA());
        }
    }

    private void endMatch(TfTeam winner) {
        if (current == null) {
            return;
        }
        current.setWinner(winner);
        TfTeam loser = current.opponent(winner);
        if (loser != null) {
            loser.setTournamentOut(true);
            plugin.broadcast("team-out", loser, null, 0);
        }
        plugin.broadcastRaw(plugin.msg("match-over").replace("{winner}", winner.getName()));
        broadcastRecap(current);
        cleanupFight(false);
        cancelTask();
        roundWinners.add(winner);
        if (!queue.isEmpty()) {
            startBetween();
            return;
        }
        if (roundWinners.size() <= 1) {
            tournamentWinner = winner;
            finishTournament();
            return;
        }
        List<TfTeam> nextRound = new ArrayList<TfTeam>(roundWinners);
        roundWinners.clear();
        queue.addAll(buildRound(nextRound, roundName(nextRound.size())));
        startBetween();
    }

    private void startBetween() {
        betweenLeft = Math.max(1, plugin.getConfig().getInt("between-match-seconds", 15));
        state = TeamFightState.BETWEEN;
        plugin.broadcastRaw(plugin.msg("next-match").replace("{seconds}", String.valueOf(betweenLeft)));
        task = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                betweenLeft--;
                if (betweenLeft <= 0) {
                    cancelTask();
                    startNextMatch();
                }
            }
        }, 20L, 20L);
    }

    private void finishTournament() {
        cancelTask();
        state = TeamFightState.ENDED;
        plugin.getScoreboard().stop();
        if (tournamentWinner != null) {
            plugin.broadcastRaw(plugin.msg("tournament-over").replace("{winner}", tournamentWinner.getName()));
            EventHub hub = plugin.getHost().events();
            if (hub != null) {
                Player leader = Bukkit.getPlayer(tournamentWinner.getLeader());
                String factionId = tournamentWinner.getFactionId();
                if (factionId == null && leader != null) {
                    factionId = plugin.getEventFactionHook().getFactionId(leader);
                }
                if (factionId != null) {
                    hub.awardTopPoints(EventType.TEAMFIGHT, factionId);
                    String facName = plugin.getEventFactionHook().getFactionDisplayName(factionId);
                    hub.announceDiscordWinner(EventType.TEAMFIGHT, tournamentWinner.getName(), facName, -1);
                }
                hub.clearActive(EventType.TEAMFIGHT, null);
            }
        }
        cleanupFight(true);
        state = TeamFightState.WAITING;
        reset();
    }

    private void broadcastRecap(TfMatch match) {
        Bukkit.broadcastMessage(CC.color("&8&m------------------------------"));
        Bukkit.broadcastMessage(CC.color("        &6TEAMFIGHT TERMINE"));
        Bukkit.broadcastMessage(CC.color("&8&m------------------------------"));
        Bukkit.broadcastMessage(CC.color("&6Vainqueur : &a" + match.getWinner().getName()));
        Bukkit.broadcastMessage("");
        recapTeam("&b", match.getTeamA());
        Bukkit.broadcastMessage("");
        recapTeam("&c", match.getTeamB());
        Bukkit.broadcastMessage(CC.color("&8&m------------------------------"));
    }

    private void recapTeam(String color, TfTeam team) {
        Bukkit.broadcastMessage(CC.color(color + team.getName().toUpperCase(Locale.ROOT)));
        for (UUID uuid : team.getMembers()) {
            if (!fighters.contains(uuid) && !stats.containsKey(uuid)) {
                continue;
            }
            FightStats st = statsOf(uuid);
            Player player = Bukkit.getPlayer(uuid);
            String name = player != null ? player.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            Bukkit.broadcastMessage(CC.color("&f" + name + "   &7→ &e" + st.getHits()
                    + " hits &8| &b" + st.getPotions() + " potions"));
            if (!st.getPotionsByType().isEmpty()) {
                StringBuilder types = new StringBuilder();
                for (java.util.Map.Entry<String, Integer> potion : st.getPotionsByType().entrySet()) {
                    if (types.length() > 0) {
                        types.append("&7, ");
                    }
                    types.append("&7").append(potion.getKey()).append(" &f").append(potion.getValue());
                }
                Bukkit.broadcastMessage(CC.color("  " + types.toString()));
            }
        }
    }

    private void cleanupFight(boolean everyone) {
        Location wait = readSpawn("spawns.wait");
        Set<UUID> involved = new HashSet<UUID>(fighters);
        involved.addAll(deadThisFight);
        if (everyone) {
            for (TfTeam team : teams.values()) {
                involved.addAll(team.getMembers());
            }
        }
        for (UUID uuid : involved) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            plugin.getKit().clear(player);
            player.setGameMode(GameMode.SURVIVAL);
            if (wait != null) {
                player.teleport(wait);
            }
        }
        clearArenaEntities();
        fighters.clear();
        deadThisFight.clear();
        cancelEffectsTask();
    }

    private void clearArenaEntities() {
        Location pos1 = readSpawn("arena.pos1");
        Location pos2 = readSpawn("arena.pos2");
        World world = pos1 != null ? pos1.getWorld() : (current != null ? readSpawn("spawns.a") != null
                ? readSpawn("spawns.a").getWorld() : null : null);
        if (world == null) {
            return;
        }
        double minX;
        double minY;
        double minZ;
        double maxX;
        double maxY;
        double maxZ;
        if (pos1 != null && pos2 != null && pos1.getWorld() == pos2.getWorld()) {
            minX = Math.min(pos1.getX(), pos2.getX());
            minY = Math.min(pos1.getY(), pos2.getY());
            minZ = Math.min(pos1.getZ(), pos2.getZ());
            maxX = Math.max(pos1.getX(), pos2.getX());
            maxY = Math.max(pos1.getY(), pos2.getY());
            maxZ = Math.max(pos1.getZ(), pos2.getZ());
        } else {
            Location a = readSpawn("spawns.a");
            Location b = readSpawn("spawns.b");
            if (a == null || b == null) {
                return;
            }
            minX = Math.min(a.getX(), b.getX()) - 40;
            minY = Math.min(a.getY(), b.getY()) - 10;
            minZ = Math.min(a.getZ(), b.getZ()) - 40;
            maxX = Math.max(a.getX(), b.getX()) + 40;
            maxY = Math.max(a.getY(), b.getY()) + 20;
            maxZ = Math.max(a.getZ(), b.getZ()) + 40;
            world = a.getWorld();
        }
        Iterator<Entity> it = world.getEntities().iterator();
        while (it.hasNext()) {
            Entity entity = it.next();
            if (entity instanceof Player) {
                continue;
            }
            Location loc = entity.getLocation();
            if (loc.getX() >= minX && loc.getX() <= maxX
                    && loc.getY() >= minY && loc.getY() <= maxY
                    && loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                entity.remove();
            }
        }
    }

    public boolean insideArena(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        Location pos1 = readSpawn("arena.pos1");
        Location pos2 = readSpawn("arena.pos2");
        if (pos1 == null || pos2 == null || pos1.getWorld() == null) {
            return true;
        }
        if (!sameWorld(pos1, loc)) {
            return true;
        }
        double minX = Math.min(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        Location spawnA = readSpawn("spawns.a");
        Location spawnB = readSpawn("spawns.b");
        if (spawnA != null && sameWorld(pos1, spawnA)) {
            minX = Math.min(minX, spawnA.getX());
            maxX = Math.max(maxX, spawnA.getX() + 8);
            minY = Math.min(minY, spawnA.getY());
            maxY = Math.max(maxY, spawnA.getY());
            minZ = Math.min(minZ, spawnA.getZ());
            maxZ = Math.max(maxZ, spawnA.getZ() + 8);
        }
        if (spawnB != null && sameWorld(pos1, spawnB)) {
            minX = Math.min(minX, spawnB.getX());
            maxX = Math.max(maxX, spawnB.getX() + 8);
            minY = Math.min(minY, spawnB.getY());
            maxY = Math.max(maxY, spawnB.getY());
            minZ = Math.min(minZ, spawnB.getZ());
            maxZ = Math.max(maxZ, spawnB.getZ() + 8);
        }
        minX -= 1;
        maxX += 1;
        minZ -= 1;
        maxZ += 1;
        if (maxY - minY < 8) {
            maxY = minY + 16;
        }
        minY -= 2;
        maxY += 8;
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getZ() >= minZ && loc.getZ() <= maxZ
                && loc.getY() >= minY && loc.getY() <= maxY;
    }

    private boolean sameWorld(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().getName().equalsIgnoreCase(b.getWorld().getName());
    }

    public void sendToWait(Player player) {
        Location wait = readSpawn("spawns.wait");
        plugin.getKit().clear(player);
        if (wait != null) {
            player.teleport(wait);
        }
    }

    public void saveSpawn(Player player, String slot) {
        Location loc = player.getLocation();
        String path = "spawns." + slot;
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x", loc.getX());
        plugin.getConfig().set(path + ".y", loc.getY());
        plugin.getConfig().set(path + ".z", loc.getZ());
        plugin.getConfig().set(path + ".yaw", loc.getYaw());
        plugin.getConfig().set(path + ".pitch", loc.getPitch());
        plugin.saveConfig();
    }

    public void saveArenaCorner(Player player, String corner) {
        Location loc = player.getLocation();
        String path = "arena." + corner;
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x", loc.getX());
        plugin.getConfig().set(path + ".y", loc.getY());
        plugin.getConfig().set(path + ".z", loc.getZ());
        plugin.saveConfig();
    }

    Location readSpawn(String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw", 0), (float) section.getDouble("pitch", 0));
    }

    private List<TfMatch> buildRound(List<TfTeam> roundTeams, String round) {
        List<TfMatch> matches = new ArrayList<TfMatch>();
        for (int i = 0; i + 1 < roundTeams.size(); i += 2) {
            matches.add(new TfMatch(roundTeams.get(i), roundTeams.get(i + 1), round));
        }
        return matches;
    }

    private String roundName(int teamCount) {
        if (teamCount >= 8) {
            return "Quart de finale";
        }
        if (teamCount >= 4) {
            return "Demi-finale";
        }
        return "Finale";
    }

    private void reset() {
        teams.clear();
        playerTeam.clear();
        stats.clear();
        fighters.clear();
        deadThisFight.clear();
        queue.clear();
        roundWinners.clear();
        current = null;
        tournamentWinner = null;
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void startEffectsTask() {
        cancelEffectsTask();
        effectsTask = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                if (state != TeamFightState.FIGHTING) {
                    return;
                }
                for (UUID uuid : fighters) {
                    if (deadThisFight.contains(uuid)) {
                        continue;
                    }
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        plugin.getKit().applyArenaEffects(player);
                    }
                }
            }
        }, 20L, 40L);
    }

    private void cancelEffectsTask() {
        if (effectsTask != null) {
            effectsTask.cancel();
            effectsTask = null;
        }
    }
}
