package fr.draftmc.events.conquest.managers;
import fr.draftmc.util.NmsTitles;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Coeur de la logique de capture : progression du timer, attribution des
 * points, verrouillage de zone, reassignation aleatoire du capteur.
 *
 * PERFORMANCE : le tick periodique ne parcourt QUE les zones (un tres
 * petit nombre, quelques unites a quelques dizaines), jamais la liste des
 * joueurs en ligne - c'est ce qui garantit qu'il n'y a aucun cout
 * supplementaire lie au nombre de joueurs connectes (200+).
 */
public class CaptureManager {

    private final ConquestPlugin plugin;
    private final Random random = new Random();
    private BukkitTask tickTask;
    private final Set<UUID> playersShownCaptureThisTick = new HashSet<>();

    public CaptureManager(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), this::tick, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        playersShownCaptureThisTick.clear();
    }

    private void tick() {
        if (plugin.getConquestManager().getState() != ConquestState.RUNNING) return;

        int duration = plugin.getConfig().getInt("general.capture-duration-seconds", 10);
        int maxPoints = plugin.getConfig().getInt("general.zone-max-points", 25);
        playersShownCaptureThisTick.clear();

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            if (zone.getCaptorId() == null) continue;

            Player captor = Bukkit.getPlayer(zone.getCaptorId());
            if (captor == null || !captor.isOnline()) {
                // Etat incoherent (le listener aurait du liberer la zone) : on se protege quand meme.
                releaseCaptor(zone, null);
                continue;
            }

            int seconds = zone.getCaptureSeconds() + 1;
            zone.setCaptureSeconds(seconds);

            playSound(captor, plugin.getConfig().getString("sounds.on-capture-tick", "NOTE_PLING"));

            String factionId = plugin.getEventFactionHook().getFactionId(captor);
            if (factionId == null) {
                // Securite : un capteur sans faction (wilderness) ne devrait
                // jamais exister (deja filtre a l'entree de zone), mais on
                // protege quand meme en transferant la capture immediatement.
                releaseCaptor(zone, captor);
                continue;
            }

            String line = buildCaptureLine(zone, captor, factionId, seconds, duration, maxPoints);
            sendCaptureActionBarToZone(zone, line);

            if (seconds >= duration) {
                zone.setCaptureSeconds(0);
                // Plafond de 25 points PAR FACTION PAR ZONE (pas un verrou
                // global) : une faction qui a deja atteint le plafond sur
                // cette zone n'y gagne plus rien, mais la zone reste
                // totalement ouverte pour toutes les autres factions.
                if (isFactionMaxed(zone, factionId)) {
                    // Securite : ne devrait normalement jamais arriver
                    // (l'entree de zone filtre deja les factions au plafond),
                    // mais on protege quand meme en transferant la capture.
                    releaseCaptor(zone, captor);
                } else {
                    boolean justReachedMax = awardPoint(zone, captor, factionId);
                    if (justReachedMax) {
                        // Cette faction vient d'atteindre SON plafond sur
                        // cette zone : son capteur est immediatement retire
                        // et remplace au hasard par un autre joueur present
                        // d'une faction pas encore plafonnee, si possible.
                        releaseCaptor(zone, captor);
                    }
                }
            }
        }
    }

    /** Un joueur sans faction (factionId null) est toujours considere "plafonne" (donc jamais eligible). */
    private boolean isFactionMaxed(Zone zone, String factionId) {
        if (factionId == null) return true;
        int maxPoints = plugin.getConfig().getInt("general.zone-max-points", 25);
        return zone.getPoints(factionId) >= maxPoints;
    }

    /**
     * @return true si ce joueur a deja recu une ligne d'action bar de
     * capture cette seconde (utilise par BossBarManager pour ne pas
     * ecraser cet affichage avec sa propre ligne de total).
     */
    public boolean wasShownCaptureThisTick(Player player) {
        return playersShownCaptureThisTick.contains(player.getUniqueId());
    }

    private String buildCaptureLine(Zone zone, Player captor, String factionId, int seconds, int duration, int maxPoints) {
        String format = plugin.getConfig().getString("actionbar.format",
                "&7Zone {zone}&7 : &fCapture &e{seconds}s&7/&e{duration} &7par &f{player} &e{points}&7/&e{max}");

        return format
                .replace("{zone}", zone.getDisplayName() + ChatColor.RESET)
                .replace("{seconds}", String.valueOf(seconds))
                .replace("{duration}", String.valueOf(duration))
                .replace("{player}", captor.getName())
                .replace("{points}", String.valueOf(zone.getPoints(factionId)))
                .replace("{max}", String.valueOf(maxPoints));
    }

    /** N'envoie la ligne de capture qu'aux joueurs PHYSIQUEMENT PRESENTS dans cette zone, jamais a tout le serveur. */
    private void sendCaptureActionBarToZone(Zone zone, String line) {
        if (!plugin.getConfig().getBoolean("actionbar.enabled", true)) return;

        for (UUID uuid : zone.getPlayersInside()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                plugin.getActionBarManager().send(p, line);
                playersShownCaptureThisTick.add(uuid);
            }
        }
    }

    private void playSound(Player player, String soundName) {
        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) return;
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean awardPoint(Zone zone, Player captor, String factionId) {
        if (factionId == null) {
            releaseCaptor(zone, captor);
            return false;
        }
        int maxPoints = plugin.getConfig().getInt("general.zone-max-points", 25);
        boolean justReachedMax = zone.addPoints(factionId, 1, maxPoints);
        String factionName = plugin.getEventFactionHook().getFactionDisplayName(factionId);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("faction", factionName);
        placeholders.put("zone", zone.getDisplayName() + ChatColor.RESET);
        placeholders.put("points", String.valueOf(zone.getPoints(factionId)));
        placeholders.put("max", String.valueOf(maxPoints));
        plugin.getMessageManager().broadcast("capture-point", placeholders);

        playSound(captor, plugin.getConfig().getString("sounds.on-zone-point", "LEVEL_UP"));
        sendZoneCapturedTitle(zone, factionName);

        if (justReachedMax) {
            // Cette faction precise a atteint SON plafond sur CETTE zone :
            // elle n'y gagnera plus rien, mais la zone reste ouverte pour
            // toutes les autres factions (pas de verrou global).
            playSound(captor, plugin.getConfig().getString("sounds.on-zone-locked", "ANVIL_LAND"));

            Map<String, String> maxedPh = new HashMap<>();
            maxedPh.put("zone", zone.getDisplayName() + ChatColor.RESET);
            plugin.getMessageManager().send(captor, "faction-zone-maxed", maxedPh);
        }

        plugin.getConquestManager().checkVictory();
        plugin.getStorageManager().saveAsync();
        return justReachedMax;
    }

    private void sendZoneCapturedTitle(Zone zone, String factionName) {
        if (!plugin.getConfig().getBoolean("titles.enabled", true)) return;

        String title = plugin.getConfig().getString("titles.zone-captured.title", "&6{zone}")
                .replace("{zone}", zone.getDisplayName() + ChatColor.RESET);
        String subtitle = plugin.getConfig().getString("titles.zone-captured.subtitle", "&7capturee par &f{faction}")
                .replace("{faction}", factionName);
        title = ChatColor.translateAlternateColorCodes('&', title);
        subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);

        for (Player p : Bukkit.getOnlinePlayers()) {
            NmsTitles.send(p, title, subtitle, 10, 40, 10);
        }
    }

    // ================= Entree/sortie de zone (appele par les listeners) =================

    public void onPlayerEnterZone(Player player, Zone zone) {
        if (plugin.getConquestManager().getState() != ConquestState.RUNNING) return;

        zone.getPlayersInside().add(player.getUniqueId());

        String factionId = plugin.getEventFactionHook().getFactionId(player);
        if (factionId == null) {
            plugin.getMessageManager().send(player, "need-faction");
            return;
        }

        if (zone.getCaptorId() == null && !isFactionMaxed(zone, factionId)) {
            assignCaptor(zone, player);
        }
    }

    public void onPlayerLeaveZone(Player player, Zone zone) {
        zone.getPlayersInside().remove(player.getUniqueId());
        if (player.getUniqueId().equals(zone.getCaptorId())) {
            releaseCaptor(zone, player);
        }
    }

    /** A appeler sur mort/deconnexion/teleport : retire le joueur de TOUTES les zones ou il se trouvait. */
    public void onPlayerRemoved(Player player) {
        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            if (zone.getPlayersInside().contains(player.getUniqueId())) {
                onPlayerLeaveZone(player, zone);
            }
        }
    }

    private void assignCaptor(Zone zone, Player player) {
        zone.setCaptorId(player.getUniqueId());
        zone.setCaptureSeconds(0);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("zone", zone.getDisplayName() + ChatColor.RESET);
        plugin.getMessageManager().send(player, "capture-started", placeholders);
        plugin.getStorageManager().saveAsync();
    }

    /** Libere le capteur actuel d'une zone et en choisit un nouveau au hasard parmi les joueurs presents dont la faction n'est pas deja plafonnee sur cette zone. */
    private void releaseCaptor(Zone zone, Player leavingPlayer) {
        zone.setCaptorId(null);
        zone.setCaptureSeconds(0);

        Set<UUID> candidates = new HashSet<>(zone.getPlayersInside());
        if (leavingPlayer != null) candidates.remove(leavingPlayer.getUniqueId());
        candidates.removeIf(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) return true;
            String factionId = plugin.getEventFactionHook().getFactionId(p);
            // Exclut les joueurs sans faction (wilderness) ET ceux dont la
            // faction est deja plafonnee sur cette zone.
            return factionId == null || isFactionMaxed(zone, factionId);
        });

        if (!candidates.isEmpty()) {
            UUID[] array = candidates.toArray(new UUID[0]);
            UUID chosen = array[random.nextInt(array.length)];
            Player newCaptor = Bukkit.getPlayer(chosen);
            if (newCaptor != null) {
                assignCaptor(zone, newCaptor);
                return;
            }
        }
        plugin.getStorageManager().saveAsync();
    }

    public boolean isCurrentlyCapturing(Player player) {
        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            if (player.getUniqueId().equals(zone.getCaptorId())) return true;
        }
        return false;
    }
}
