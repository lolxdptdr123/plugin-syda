package fr.draftmc.events.masterkill.managers;
import fr.draftmc.util.NmsTitles;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.model.MasterKillState;
import fr.draftmc.events.masterkill.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cycle de vie global du match, en 2 etapes comme DraftRoyale :
 * /masterkill start ouvre les inscriptions, /masterkill launch teleporte
 * tout le monde et demarre reellement le match. Detecte l'elimination
 * (une equipe est eliminee quand tous ses membres sont morts/deconnectes)
 * et la victoire (une seule equipe restante, OU expiration du temps
 * limite - c'est alors l'equipe avec le plus de kills qui gagne). Seule
 * la faction TOP 1 par nombre de kills recoit les recompenses (le top 3
 * reste affiche dans le scoreboard a titre informatif).
 */
public class MasterKillManager {

    private final MasterKillPlugin plugin;
    private MasterKillState state = MasterKillState.WAITING;
    private final Set<String> announcedEliminated = new HashSet<>();
    private BukkitTask timerTask;
    private long matchEndMillis;

    public MasterKillManager(MasterKillPlugin plugin) {
        this.plugin = plugin;
    }

    public MasterKillState getState() {
        return state;
    }

    /** Ouvre les inscriptions : les equipes peuvent se creer, sans limite de temps fixe. */
    public boolean start() {
        if (state != MasterKillState.WAITING) return false;
        // Repart toujours d'une base propre (equipes ET compteur de
        // couleurs), meme si des tests precedents n'ont pas ete conclus
        // proprement par /masterkill stop.
        plugin.getTeamManager().reset();
        state = MasterKillState.REGISTRATION;
        plugin.getMessageManager().broadcast("registration-open");
        return true;
    }

    public boolean isReadyToLaunch() {
        return state == MasterKillState.REGISTRATION && plugin.getTeamManager().getTeams().size() >= 2;
    }

    /** Lancement effectif : teleporte toutes les equipes formees et demarre le match, avec sa limite de temps. */
    public void launch() {
        if (!isReadyToLaunch()) return;

        if (!plugin.getArenaManager().prepareArena()) {
            return;
        }

        plugin.getKillManager().reset();
        announcedEliminated.clear();
        plugin.getArenaManager().teleportTeams(plugin.getTeamManager().getTeams());

        state = MasterKillState.RUNNING;
        plugin.getScoreboardManager().start();
        plugin.getMessageManager().broadcast("event-started");

        int durationMinutes = plugin.getConfig().getInt("general.match-duration-minutes", 30);
        matchEndMillis = System.currentTimeMillis() + durationMinutes * 60_000L;
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), this::tickTimer, 20L, 20L);
    }

    private void tickTimer() {
        if (state != MasterKillState.RUNNING) return;
        if (getRemainingSeconds() <= 0) {
            forceEndByTime();
        }
    }

    /** @return le nombre de secondes restantes avant la fin forcee du match, ou 0 si le match ne tourne pas. */
    public long getRemainingSeconds() {
        if (state != MasterKillState.RUNNING) return 0;
        return Math.max(0, (matchEndMillis - System.currentTimeMillis()) / 1000);
    }

    /** Le temps limite est ecoule : meme s'il reste plusieurs equipes, c'est celle avec le plus de kills qui gagne. */
    private void forceEndByTime() {
        List<Map.Entry<String, Integer>> top = plugin.getKillManager().getTop(1);
        Team winner = top.isEmpty() ? null : plugin.getTeamManager().getTeamByName(top.get(0).getKey());
        endMatch(winner);
    }

    /** Appele par le listener de mort/deconnexion. */
    public void onPlayerEliminated(Player victim, Player killer) {
        if (state != MasterKillState.RUNNING) return;

        // IMPORTANT : on marque l'elimination IMMEDIATEMENT ici, pas en
        // attendant PlayerRespawnEvent (qui ne se declenche qu'au clic sur
        // "Respawn", potentiellement bien plus tard) - sinon le decompte
        // d'equipes vivantes restait fausse et le match ne se terminait jamais.
        plugin.getTeamManager().markEliminated(victim.getUniqueId());

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            Team killerTeam = plugin.getTeamManager().getTeamOf(killer);
            Team victimTeam = plugin.getTeamManager().getTeamOf(victim);

            // Un kill ne compte que s'il vient d'une equipe differente de
            // celle de la victime (pas de points pour du "friendly fire").
            if (killerTeam != null && killerTeam != victimTeam) {
                plugin.getKillManager().addKill(killerTeam.getName());
                plugin.getKillManager().addPersonalKill(killer.getUniqueId());
                announceKill(killer, victim, killerTeam, victimTeam);
            }
        }

        checkTeamEliminations();
        checkForWinner();
    }

    private void announceKill(Player killer, Player victim, Team killerTeam, Team victimTeam) {
        Map<String, String> ph = new HashMap<>();
        ph.put("killer", killer.getName());
        ph.put("victim", victim.getName());
        ph.put("victimTeam", victimTeam != null ? victimTeam.getName() : "?");
        ph.put("killerTeam", killerTeam.getName());
        ph.put("points", String.valueOf(plugin.getKillManager().getKills(killerTeam.getName())));
        plugin.getMessageManager().broadcast("kill-broadcast", ph);

        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            try {
                Sound sound = Sound.valueOf(plugin.getConfig().getString("sounds.on-kill", "LEVEL_UP"));
                killer.playSound(killer.getLocation(), sound, 1f, 1f);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (plugin.getConfig().getBoolean("titles.enabled", true)) {
            String title = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("titles.on-kill.title", "&a+1 KILL"));
            String subtitle = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("titles.on-kill.subtitle", "&7{victim} elimine !")
                            .replace("{victim}", victim.getName()));
            NmsTitles.send(killer, title, subtitle, 10, 40, 10);
        }
    }

    /** Annonce (une seule fois) toute equipe qui vient d'etre completement eliminee. */
    private void checkTeamEliminations() {
        for (Team team : plugin.getTeamManager().getTeams()) {
            if (!plugin.getTeamManager().isTeamAlive(team) && !announcedEliminated.contains(team.getName())) {
                announcedEliminated.add(team.getName());
                Map<String, String> ph = new HashMap<>();
                ph.put("team", team.getName());
                plugin.getMessageManager().broadcast("team-eliminated", ph);
            }
        }
    }

    private void checkForWinner() {
        int alive = plugin.getTeamManager().countAliveTeams();
        if (alive <= 1) {
            Team winner = plugin.getTeamManager().getSoleAliveTeam();
            endMatch(winner);
        }
    }

    private String factionIdOf(Team team) {
        if (team == null || plugin.getEventFactionHook() == null) {
            return null;
        }
        Player leader = team.getLeader() == null ? null : Bukkit.getPlayer(team.getLeader());
        if (leader != null) {
            String id = plugin.getEventFactionHook().getFactionId(leader);
            if (id != null) {
                return id;
            }
        }
        for (UUID uuid : team.getMembers()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) {
                String id = plugin.getEventFactionHook().getFactionId(member);
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    private void endMatch(Team winner) {
        if (winner != null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("team", winner.getName());
            plugin.getMessageManager().broadcast("winner", ph);

            if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
                try {
                    Sound sound = Sound.valueOf(plugin.getConfig().getString("sounds.on-victory", "WITHER_SPAWN"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), sound, 1f, 1f);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            String factionId = factionIdOf(winner);
            if (plugin.getHost().events() != null) {
                if (factionId != null) {
                    plugin.getHost().events().awardTopPoints(fr.draftmc.events.EventType.MASTERKILL, factionId);
                }
                String factionName = factionId == null
                        ? winner.getName()
                        : plugin.getEventFactionHook().getFactionDisplayName(factionId);
                plugin.getHost().events().announceDiscordWinner(fr.draftmc.events.EventType.MASTERKILL, "", factionName, -1);
            }
        }

        distributeRewards();
        resetEverything();
    }

    /** Seule la faction TOP 1 par kills recoit les recompenses (le top 3 reste affiche dans le scoreboard). */
    private void distributeRewards() {
        List<Map.Entry<String, Integer>> top = plugin.getKillManager().getTop(1);
        if (top.isEmpty()) return;

        Map.Entry<String, Integer> entry = top.get(0);
        String teamName = entry.getKey();

        List<String> commands = plugin.getConfig().getStringList("rewards.rank-1");
        for (String command : commands) {
            String parsed = command
                    .replace("{team}", teamName)
                    .replace("{kills}", String.valueOf(entry.getValue()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        // N'annonce "reward-announce" que si rewards.announce n'est pas
        // desactive explicitement (par defaut : true).
        if (!plugin.getConfig().getBoolean("rewards.announce", true)) return;

        Map<String, String> ph = new HashMap<>();
        ph.put("team", teamName);
        ph.put("kills", String.valueOf(entry.getValue()));
        plugin.getMessageManager().broadcast("reward-announce", ph);
    }

    /** Arret manuel (admin) : annule les inscriptions, ou conclut le match en cours (recompense sur les kills actuels). */
    public boolean stop() {
        if (state == MasterKillState.WAITING) return false;

        resetEverything();
        plugin.getMessageManager().broadcast("event-stopped");
        return true;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    private void resetEverything() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        teleportAllToSpawnAndReset();
        plugin.getScoreboardManager().stop();
        plugin.getTeamManager().reset();
        plugin.getKillManager().reset();
        announcedEliminated.clear();
        state = MasterKillState.WAITING;
    }

    private void teleportAllToSpawnAndReset() {
        Location spawn = resolveSpawnLocation();
        for (Team team : plugin.getTeamManager().getTeams()) {
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
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setHealth(20);
        player.setGameMode(GameMode.SURVIVAL);
        if (spawn != null) {
            player.teleport(spawn);
        }
    }

    /** Expose pour RespawnListener - meme spawn que celui utilise en fin de match. */
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
}
