package fr.draftmc.events.battleroyal.managers;

import fr.draftmc.events.battleroyal.BattleRoyal;
import fr.draftmc.events.battleroyal.model.GameState;
import fr.draftmc.events.battleroyal.model.TeamBR;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Noyau de la Battle Royale : bordure qui retrecit par etapes,
 * teleportation des equipes, detection d'elimination et de victoire.
 * N'utilise que l'API Bukkit/Spigot standard (WorldBorder, Scheduler,
 * Scoreboard) pour rester compatible avec un maximum de cores.
 */
public class GameManager {

    private final BattleRoyal plugin;
    private final TeamManager teamManager;
    private final PointsManager pointsManager;
    private final ScoreboardManager scoreboardManager;
    private final KitManager kitManager;

    private GameState state = GameState.WAITING;
    private Location center;
    private BukkitTask scoreboardTask;
    private BukkitTask stageTask;
    private long gameStartMillis;
    private int stageIndex = 0;
    /**
     * Marqueur d'elimination PAR JOUEUR, independant du gamemode. Avant ce
     * fix, "encore en vie" etait deduit de `gameMode != SPECTATOR` : ca ne
     * fonctionne plus des lors qu'un joueur mort est renvoye en SURVIVAL
     * (spawn du monde) plutot qu'en spectateur - sans ce Set, un coequipier
     * deja mort aurait ete compte comme vivant indefiniment (equipe jamais
     * eliminee, victoire jamais declenchee).
     */
    private final Set<UUID> eliminatedPlayers = new HashSet<>();

    public GameManager(BattleRoyal plugin, TeamManager teamManager, PointsManager pointsManager,
                        ScoreboardManager scoreboardManager, KitManager kitManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.pointsManager = pointsManager;
        this.scoreboardManager = scoreboardManager;
        this.kitManager = kitManager;
    }

    public GameState getState() {
        return state;
    }

    /** Ouvre les inscriptions : les equipes peuvent alors se creer. Admin uniquement. */
    public boolean openRegistrations() {
        if (state != GameState.WAITING) return false;
        state = GameState.REGISTRATION;
        broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "Les inscriptions au Battle Royale sont ouvertes ! "
                + ChatColor.RESET + ChatColor.GOLD + "Utilisez /br team create <nom> pour former votre equipe.");
        return true;
    }

    /** Il faut que les inscriptions soient ouvertes et au moins 2 equipes formees. */
    public boolean isReadyToLaunch() {
        return state == GameState.REGISTRATION && teamManager.getTeams().size() >= 2;
    }

    /** Lance vraiment la partie : bordure, teleportation, distribution des kits, scoreboard. */
    public void launchGame() {
        if (state != GameState.REGISTRATION) return;

        String worldName = plugin.getConfig().getString("arena.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Le monde '" + worldName + "' est introuvable ! Verifie arena.world dans config.yml");
            return;
        }

        double cx = plugin.getConfig().getDouble("arena.center-x", 0);
        double cy = plugin.getConfig().getDouble("arena.center-y", 100);
        double cz = plugin.getConfig().getDouble("arena.center-z", 0);
        center = new Location(world, cx, cy, cz);

        double initialSize = plugin.getConfig().getDouble("arena.initial-size", 500);
        WorldBorder border = world.getWorldBorder();
        border.setCenter(center);
        border.setSize(initialSize);
        border.setDamageAmount(plugin.getConfig().getDouble("border.damage-amount", 1.0));
        border.setDamageBuffer(plugin.getConfig().getDouble("border.damage-buffer", 2.0));
        border.setWarningDistance(15);

        teleportTeamsAroundCenter(initialSize);

        for (TeamBR team : teamManager.getTeams()) {
            team.setEliminated(false);
        }
        eliminatedPlayers.clear();

        state = GameState.INGAME;
        stageIndex = 0;
        gameStartMillis = System.currentTimeMillis();
        broadcast(ChatColor.GREEN + "La partie commence ! " + teamManager.getTeams().size() + " equipes s'affrontent.");

        scheduleNextStage();
        startScoreboardLoop();
    }

    private void teleportTeamsAroundCenter(double size) {
        double radius = Math.min(size / 2.5, 200);
        List<TeamBR> teams = new ArrayList<>(teamManager.getTeams());
        int n = teams.size();

        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI / n) * i;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location spawn = new Location(center.getWorld(), x, center.getY(), z);
            spawn.setY(center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1);

            TeamBR team = teams.get(i);
            for (UUID uuid : team.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.teleport(spawn);
                    p.setGameMode(GameMode.SURVIVAL);
                    p.setHealth(20);
                    p.setFoodLevel(20);
                    kitManager.giveKit(p);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void scheduleNextStage() {
        List<?> rawStages = plugin.getConfig().getList("border.stages");
        if (rawStages == null || stageIndex >= rawStages.size()) {
            broadcast(ChatColor.RED + "La bordure a atteint sa taille minimale !");
            return;
        }

        Map<String, Object> stage = (Map<String, Object>) rawStages.get(stageIndex);
        int wait = toInt(stage.get("wait"));
        int duration = toInt(stage.get("shrink-duration"));
        double size = toDouble(stage.get("size"));

        broadcast(ChatColor.YELLOW + "La bordure va se resserrer dans " + wait + "s (taille cible : " + (int) size + ").");

        stageTask = Bukkit.getScheduler().runTaskLater(plugin.getHost(), () -> {
            if (state != GameState.INGAME) return;
            center.getWorld().getWorldBorder().setSize(size, duration);
            broadcast(ChatColor.GOLD + "La bordure se resserre maintenant !");
            stageIndex++;
            stageTask = Bukkit.getScheduler().runTaskLater(plugin.getHost(), this::scheduleNextStage, duration * 20L);
        }, wait * 20L);
    }

    private int toInt(Object o) {
        return Integer.parseInt(String.valueOf(o));
    }

    private double toDouble(Object o) {
        return Double.parseDouble(String.valueOf(o));
    }

    private void startScoreboardLoop() {
        scoreboardTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.INGAME) {
                    cancel();
                    return;
                }
                updateScoreboards();
            }
        }.runTaskTimer(plugin.getHost(), 0L, 20L);
    }

    private void updateScoreboards() {
        List<TeamBR> aliveTeams = new ArrayList<>();
        for (TeamBR t : teamManager.getTeams()) {
            if (!t.isEliminated()) aliveTeams.add(t);
        }
        // Les equipes avec le plus de membres survivants en premier.
        aliveTeams.sort((a, b) -> countAliveMembers(b) - countAliveMembers(a));

        List<String> teamLines = new ArrayList<>();
        for (TeamBR team : aliveTeams) {
            teamLines.add(team.getDisplayName() + ChatColor.GRAY + "(" + countAliveMembers(team) + ")");
        }

        long elapsed = (System.currentTimeMillis() - gameStartMillis) / 1000;

        for (Player player : Bukkit.getOnlinePlayers()) {
            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "Temps: " + formatTime(elapsed));
            lines.add(ChatColor.GRAY + "Equipes: " + aliveTeams.size());
            lines.add(" ");
            lines.addAll(teamLines);
            lines.add("  ");
            lines.add(ChatColor.GOLD + "Points: " + pointsManager.getPoints(player.getUniqueId()));

            scoreboardManager.update(player, ChatColor.BOLD + "DRAFT ROYALE", lines);
        }
    }

    /** Nombre de membres d'une equipe actuellement en ligne et non-elimines (donc encore en vie). */
    private int countAliveMembers(TeamBR team) {
        int count = 0;
        for (UUID uuid : team.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && !eliminatedPlayers.contains(uuid)) count++;
        }
        return count;
    }

    private String formatTime(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Stoppe reellement tout ce qui est en cours : annule les taches
     * planifiees (bordure, scoreboard) et remet la bordure a sa taille de
     * base (arena.initial-size) au lieu de la laisser figee la ou elle en
     * etait (sans ca, le WorldBorder continue de bouger tout seul jusqu'a
     * la fin de la duree prevue, meme si le plugin ne fait plus rien).
     */
    private void haltAllTasks() {
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            scoreboardTask = null;
        }
        if (stageTask != null) {
            stageTask.cancel();
            stageTask = null;
        }
        if (center != null && center.getWorld() != null) {
            double initialSize = plugin.getConfig().getDouble("arena.initial-size", 500);
            center.getWorld().getWorldBorder().setSize(initialSize);
        }
    }

    /** @return true si le joueur est actuellement dans la zone du BR (partie en cours). Utilise par l'anti-commande. */
    public boolean isInsideArena(Player player) {
        if (state != GameState.INGAME || center == null) return false;
        if (!player.getWorld().equals(center.getWorld())) return false;

        WorldBorder border = center.getWorld().getWorldBorder();
        double half = border.getSize() / 2.0;
        Location loc = player.getLocation();
        return Math.abs(loc.getX() - center.getX()) <= half && Math.abs(loc.getZ() - center.getZ()) <= half;
    }

    /**
     * Teleporte tous les joueurs inscrits au BR vers le spawn et les
     * "nettoie" (inventaire + effets de potion). Appele a la fin d'une
     * partie (victoire) et lors d'un arret force.
     */
    private void teleportAllToSpawnAndClear() {
        Location spawn = resolveSpawnLocation();
        for (TeamBR team : teamManager.getTeams()) {
            for (UUID uuid : team.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    resetPlayer(p, spawn);
                }
            }
        }
    }

    private void resetPlayer(Player player, Location spawn) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        if (spawn != null) {
            player.teleport(spawn);
        }
    }

    /** Lit spawn.world/x/y/z/yaw/pitch dans la config, ou se replie sur le spawn du premier monde du serveur. */
    public Location resolveSpawnLocation() {
        String worldName = plugin.getConfig().getString("spawn.world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;

        if (world == null) {
            if (Bukkit.getWorlds().isEmpty()) return null;
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        double x = plugin.getConfig().getDouble("spawn.x", world.getSpawnLocation().getX());
        double y = plugin.getConfig().getDouble("spawn.y", world.getSpawnLocation().getY());
        double z = plugin.getConfig().getDouble("spawn.z", world.getSpawnLocation().getZ());
        float yaw = (float) plugin.getConfig().getDouble("spawn.yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("spawn.pitch", 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    /** Appele par le listener quand un joueur meurt (ou se deconnecte en jeu). */
    public void onPlayerEliminated(Player victim, Player killer) {
        if (state != GameState.INGAME) return;

        eliminatedPlayers.add(victim.getUniqueId());

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            pointsManager.addPoints(killer.getUniqueId(), plugin.getConfig().getInt("points.kill", 10));
        }

        TeamBR victimTeam = teamManager.getTeamOf(victim.getUniqueId());
        if (victimTeam == null) return;

        boolean teamStillAlive = false;
        for (UUID id : victimTeam.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !eliminatedPlayers.contains(id)) {
                teamStillAlive = true;
                break;
            }
        }

        if (!teamStillAlive) {
            victimTeam.setEliminated(true);
            broadcast(ChatColor.RED + "L'equipe " + victimTeam.getDisplayName() + ChatColor.RED + " est eliminee !");
        }

        checkWinCondition();
    }

    private void checkWinCondition() {
        List<TeamBR> alive = new ArrayList<>();
        for (TeamBR team : teamManager.getTeams()) {
            if (!team.isEliminated()) alive.add(team);
        }
        if (alive.size() <= 1) {
            endGame(alive.isEmpty() ? null : alive.get(0));
        }
    }

    private void endGame(TeamBR winner) {
        state = GameState.ENDING;
        haltAllTasks();

        if (winner != null) {
            broadcast(ChatColor.GREEN + "" + ChatColor.BOLD + "Victoire de l'equipe " + winner.getDisplayName()
                    + ChatColor.GREEN + ChatColor.BOLD + " !");
            int winPoints = plugin.getConfig().getInt("points.win", 100);
            for (UUID uuid : winner.getMembers()) {
                pointsManager.addPoints(uuid, winPoints);
            }
            if (winner.getFactionId() != null && plugin.getHost().events() != null) {
                plugin.getHost().events().awardTopPoints(fr.draftmc.events.EventType.BATTLEROYAL, winner.getFactionId());
            }
        } else {
            broadcast(ChatColor.RED + "Partie terminee, aucune equipe survivante.");
        }

        long elapsedMinutes = (System.currentTimeMillis() - gameStartMillis) / 60000;
        int perMinute = plugin.getConfig().getInt("points.survival-per-minute", 2);
        for (TeamBR team : teamManager.getTeams()) {
            for (UUID uuid : team.getMembers()) {
                pointsManager.addPoints(uuid, (int) (elapsedMinutes * perMinute));
            }
        }

        // Une victoire termine la partie exactement comme /br stop : memes
        // equipes reinitialisees, memes joueurs renvoyes au spawn "propres".
        resetEverything();
    }

    /**
     * Arret force de la partie (admin), utilisable aussi bien pour annuler
     * des inscriptions en cours que pour interrompre un match en jeu.
     * @return false si aucun BR n'etait en cours (rien a arreter).
     */
    public boolean stopGame() {
        if (state == GameState.WAITING) {
            return false;
        }
        haltAllTasks();
        resetEverything();
        broadcast(ChatColor.RED + "La partie a ete arretee par un administrateur.");
        return true;
    }

    /**
     * Reinitialisation complete partagee entre une victoire et un /br stop.
     * IMPORTANT sur l'ordre : teleportAllToSpawnAndClear() doit s'executer
     * AVANT teamManager.reset(), car elle a besoin de connaitre les membres
     * de chaque equipe pour savoir qui teleporter — un reset() premature
     * viderait cette liste et personne ne serait teleporte.
     */
    private void resetEverything() {
        scoreboardManager.clearAll();
        teleportAllToSpawnAndClear();
        teamManager.reset();
        clearArenaEntities();
        state = GameState.WAITING;
    }

    /**
     * Supprime toutes les entites (items droppes, fleches, mobs...) du
     * monde de l'arene UNIQUEMENT, une fois tous les joueurs teleportes
     * hors de ce monde. Les joueurs sont explicitement exclus (au cas ou
     * un spectateur/admin serait reste sur place a observer).
     */
    private void clearArenaEntities() {
        if (center == null || center.getWorld() == null) return;

        for (Entity entity : center.getWorld().getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "[Battleroyal] " + ChatColor.RESET + message);
    }
}
