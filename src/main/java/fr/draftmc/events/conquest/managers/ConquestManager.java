package fr.draftmc.events.conquest.managers;
import fr.draftmc.util.NmsTitles;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cycle de vie global de l'evenement (start/stop/reload), calcul des
 * totaux de points par faction (somme sur toutes les zones), condition
 * de victoire, recompenses, et penalite de mort.
 */
public class ConquestManager {

    private final ConquestPlugin plugin;
    private final Random random = new Random();
    private ConquestState state = ConquestState.WAITING;
    private BukkitTask countdownTask;
    private int countdownSecondsLeft;

    public ConquestManager(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    public ConquestState getState() {
        return state;
    }

    /** @return les secondes restantes avant le lancement, ou -1 si aucun compte a rebours n'est en cours. */
    public int getCountdownSecondsLeft() {
        return state == ConquestState.STARTING ? countdownSecondsLeft : -1;
    }

    /** Demarre le compte a rebours (general.start-countdown-seconds, 60 par defaut) avant le vrai lancement de l'evenement. */
    public boolean start() {
        if (state != ConquestState.WAITING) return false;
        state = ConquestState.STARTING;

        countdownSecondsLeft = plugin.getConfig().getInt("general.start-countdown-seconds", 60);

        Map<String, String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(countdownSecondsLeft));
        plugin.getMessageManager().broadcast("countdown-start", ph);

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), this::tickCountdown, 20L, 20L);
        return true;
    }

    private void tickCountdown() {
        countdownSecondsLeft--;

        if (countdownSecondsLeft <= 0) {
            if (countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
            }
            launchNow();
            return;
        }

        // N'annonce qu'a intervalles reguliers pour ne pas spammer le chat :
        // toutes les 10 secondes, puis chaque seconde durant les 5 dernieres.
        boolean announce = countdownSecondsLeft % 10 == 0 || countdownSecondsLeft <= 5;
        if (!announce) return;

        Map<String, String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(countdownSecondsLeft));
        plugin.getMessageManager().broadcast("countdown-tick", ph);

        boolean soundsEnabled = plugin.getConfig().getBoolean("sounds.enabled", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            NmsTitles.send(p, ChatColor.translateAlternateColorCodes('&', "&e&l" + countdownSecondsLeft), "", 10, 40, 10);
            if (soundsEnabled) {
                try {
                    Sound sound = Sound.valueOf(plugin.getConfig().getString("sounds.on-capture-tick", "NOTE_PLING"));
                    p.playSound(p.getLocation(), sound, 1f, 1f);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /** Lancement effectif de l'evenement, une fois le compte a rebours termine. */
    private void launchNow() {
        state = ConquestState.RUNNING;

        plugin.getCaptureManager().start();
        plugin.getBossBarManager().start();
        plugin.getParticleManager().start();
        plugin.getHologramManager().start();
        plugin.getScoreboardManager().start();

        plugin.getMessageManager().broadcast("event-started");
        plugin.getStorageManager().saveAsync();
    }

    /**
     * Reprend directement l'evenement en RUNNING, SANS compte a rebours.
     * Utilisee uniquement par StorageManager au demarrage du plugin quand
     * l'evenement tournait deja avant un redemarrage serveur : on ne fait
     * pas revivre 60 secondes d'attente a une partie deja en cours.
     */
    public void resumeWithoutCountdown() {
        if (state != ConquestState.WAITING) return;
        launchNow();
    }

    /** Arrete l'evenement (ou annule le compte a rebours s'il etait encore en cours) et remet les zones a zero. */
    public boolean stop() {
        if (state == ConquestState.WAITING) return false;

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        boolean wasRunning = state == ConquestState.RUNNING;
        state = ConquestState.WAITING;

        if (wasRunning) {
            plugin.getCaptureManager().stop();
            plugin.getBossBarManager().stop();
            plugin.getParticleManager().stop();
            plugin.getHologramManager().stop();
            plugin.getScoreboardManager().stop();
        }

        // Un arret (manuel ou via victoire, qui appelle stop() en interne)
        // remet toutes les zones a zero : un nouveau Conquest repart
        // toujours sur une base propre, jamais avec les scores de la
        // manche precedente.
        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            zone.reset();
        }

        plugin.getMessageManager().broadcast("event-stopped");
        plugin.getStorageManager().saveAsync();
        return true;
    }

    public void reload() {
        plugin.reloadConfig();
        plugin.getZoneManager().loadZones();
    }

    /** Total de points d'une faction : somme de ses points sur toutes les zones. */
    public int getFactionTotal(String factionId) {
        if (factionId == null) return 0;
        int total = 0;
        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            total += zone.getPoints(factionId);
        }
        return total;
    }

    /** Classement complet : identifiant de faction -> total toutes zones confondues. */
    public Map<String, Integer> getAllTotals() {
        Map<String, Integer> totals = new HashMap<>();
        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            for (Map.Entry<String, Integer> entry : zone.getAllPoints().entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return totals;
    }

    public void checkVictory() {
        if (state != ConquestState.RUNNING) return;
        int pointsToWin = plugin.getConfig().getInt("general.points-to-win", 100);

        for (Map.Entry<String, Integer> entry : getAllTotals().entrySet()) {
            if (entry.getValue() >= pointsToWin) {
                declareVictory(entry.getKey(), entry.getValue());
                return;
            }
        }
    }

    private void declareVictory(String factionId, int points) {
        String factionName = plugin.getEventFactionHook().getFactionDisplayName(factionId);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("faction", factionName);
        placeholders.put("points", String.valueOf(points));
        plugin.getMessageManager().broadcast("victory", placeholders);

        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            String soundName = plugin.getConfig().getString("sounds.on-victory", "WITHER_SPAWN");
            try {
                Sound sound = Sound.valueOf(soundName);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), sound, 1f, 1f);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (plugin.getConfig().getBoolean("fireworks.enabled", true)) {
            launchFireworks();
        }

        for (String command : plugin.getConfig().getStringList("reward-commands")) {
            String parsed = command.replace("{faction}", factionName).replace("{points}", String.valueOf(points));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        if (plugin.getHost().events() != null) {
            plugin.getHost().events().awardTopPoints(fr.draftmc.events.EventType.CONQUEST, factionId);
            plugin.getHost().events().announceDiscordWinner(fr.draftmc.events.EventType.CONQUEST, "", factionName, points);
        }

        stop();
    }

    private void launchFireworks() {
        int amount = plugin.getConfig().getInt("fireworks.amount", 10);
        long interval = plugin.getConfig().getLong("fireworks.interval-ticks", 5);

        List<Location> spawnPoints = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            spawnPoints.add(p.getLocation());
        }
        if (spawnPoints.isEmpty()) return;

        for (int i = 0; i < amount; i++) {
            Bukkit.getScheduler().runTaskLater(plugin.getHost(), () -> {
                Location loc = spawnPoints.get(random.nextInt(spawnPoints.size()));
                if (loc.getWorld() == null) return;

                Firework firework = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                        .withColor(Color.YELLOW, Color.RED)
                        .with(FireworkEffect.Type.BURST)
                        .trail(true)
                        .build());
                meta.setPower(1);
                firework.setFireworkMeta(meta);
            }, i * interval);
        }
    }

    /** Penalite de mort : retire un pourcentage des points totaux de la faction du joueur, reparti aleatoirement. */
    public void applyDeathPenalty(Player player) {
        if (state != ConquestState.RUNNING) return;

        String factionId = plugin.getEventFactionHook().getFactionId(player);
        if (factionId == null) return;
        int total = getFactionTotal(factionId);
        if (total <= 0) return;

        int percent = plugin.getConfig().getInt("general.death-penalty-percent", 10);
        int toRemove = (total * percent) / 100;
        if (toRemove <= 0) return;

        List<Zone> eligibleZones = new ArrayList<>();
        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            if (zone.getPoints(factionId) > 0) {
                eligibleZones.add(zone);
            }
        }
        if (eligibleZones.isEmpty()) return;

        // Repartition aleatoire : les zones a zero ne sont jamais choisies
        // (retirees du pool des qu'elles atteignent zero), et on retire
        // toujours exactement le nombre de points demande (garanti car
        // toRemove <= total des zones eligibles, par construction).
        int remaining = toRemove;
        while (remaining > 0 && !eligibleZones.isEmpty()) {
            int index = random.nextInt(eligibleZones.size());
            Zone zone = eligibleZones.get(index);
            int removed = zone.removePoints(factionId, 1);
            remaining -= removed;
            if (zone.getPoints(factionId) <= 0) {
                eligibleZones.remove(index);
            }
        }

        String factionName = plugin.getEventFactionHook().getFactionDisplayName(factionId);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("faction", factionName);
        placeholders.put("amount", String.valueOf(toRemove));
        plugin.getMessageManager().broadcast("death-penalty", placeholders);

        plugin.getStorageManager().saveAsync();
    }
}
