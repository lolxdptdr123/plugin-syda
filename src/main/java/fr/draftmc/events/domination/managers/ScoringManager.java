package fr.draftmc.events.domination.managers;
import fr.draftmc.util.NmsTitles;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.DominationState;
import fr.draftmc.events.domination.model.Zone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Coeur de la logique Domination : chaque seconde, pour chaque zone, TOUTES
 * les factions distinctes ayant au moins un membre present gagnent 1 point
 * simultanement - contrairement a Conquest, il n'y a pas de capteur unique
 * ni de contestation, et le nombre de membres presents n'influence rien
 * (une faction avec 1 ou 20 joueurs presents gagne exactement pareil).
 *
 * PERFORMANCE : le tick periodique ne parcourt QUE les zones (un tres petit
 * nombre) et, pour chacune, uniquement les joueurs deja connus comme etant
 * PRESENTS dans cette zone (maintenu reactivement par les listeners de
 * mouvement) - jamais la liste complete des joueurs en ligne du serveur.
 */
public class ScoringManager {

    private final DominationPlugin plugin;
    private BukkitTask tickTask;
    private final Set<UUID> playersShownZoneStatusThisTick = new HashSet<>();

    public ScoringManager(DominationPlugin plugin) {
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
        playersShownZoneStatusThisTick.clear();
    }

    private void tick() {
        if (plugin.getDominationManager().getState() != DominationState.RUNNING) return;
        playersShownZoneStatusThisTick.clear();

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            if (zone.getPlayersInside().isEmpty()) continue;

            // Factions DISTINCTES presentes dans cette zone : peu importe le
            // nombre de membres, chaque faction ne compte qu'une seule fois.
            // Les joueurs sans faction (wilderness) ne comptent pour rien.
            Set<String> factionsPresent = new HashSet<>();
            for (UUID uuid : zone.getPlayersInside()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    String factionId = plugin.getEventFactionHook().getFactionId(p);
                    if (factionId != null) {
                        factionsPresent.add(factionId);
                    }
                }
            }

            for (String factionId : factionsPresent) {
                boolean firstPoint = zone.getPoints(factionId) == 0;
                zone.addPoints(factionId, 1);

                if (firstPoint) {
                    announceFirstControl(zone, factionId);
                }
            }

            sendZoneActionBars(zone);
        }

        plugin.getDominationManager().checkVictory();
        plugin.getStorageManager().saveAsync();
    }

    /**
     * @return true si ce joueur a deja recu une ligne d'action bar de zone
     * cette seconde (utilise par BossBarManager pour ne pas ecraser cet
     * affichage avec sa propre ligne de total).
     */
    public boolean wasShownZoneStatusThisTick(Player player) {
        return playersShownZoneStatusThisTick.contains(player.getUniqueId());
    }

    /** Petite mise en avant (son + titre) la toute premiere fois qu'une faction marque sur une zone. */
    private void announceFirstControl(Zone zone, String factionId) {
        String factionName = plugin.getEventFactionHook().getFactionDisplayName(factionId);

        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            try {
                Sound sound = Sound.valueOf(plugin.getConfig().getString("sounds.on-zone-point", "NOTE_PLING"));
                for (UUID uuid : zone.getPlayersInside()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.playSound(p.getLocation(), sound, 1f, 1f);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (plugin.getConfig().getBoolean("titles.enabled", true)) {
            String title = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("titles.zone-first-point.title", "&6{zone}")
                            .replace("{zone}", zone.getDisplayName() + ChatColor.RESET));
            String subtitle = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("titles.zone-first-point.subtitle", "&7controlee par &f{faction}")
                            .replace("{faction}", factionName));

            for (UUID uuid : zone.getPlayersInside()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) NmsTitles.send(p, title, subtitle, 10, 40, 10);
            }
        }
    }

    /** N'envoie la ligne de statut qu'aux joueurs PHYSIQUEMENT PRESENTS dans la zone, chacun voyant UNIQUEMENT sa propre faction. */
    private void sendZoneActionBars(Zone zone) {
        if (!plugin.getConfig().getBoolean("actionbar.enabled", true)) return;
        String format = plugin.getConfig().getString("actionbar.format",
                "&7{zone} &7: &ata faction controle &7(&a+1&7/s, total &e{total}&7)");
        String noFactionFormat = plugin.getConfig().getString("actionbar.no-faction-format",
                "&7{zone} &7: &cTu n'as pas de faction, tu ne marques aucun point ici.");

        for (UUID uuid : zone.getPlayersInside()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            String factionId = plugin.getEventFactionHook().getFactionId(p);
            String line;
            if (factionId == null) {
                line = noFactionFormat.replace("{zone}", zone.getDisplayName() + ChatColor.RESET);
            } else {
                // {points} = points marques sur CETTE zone uniquement, {total} = somme
                // sur toutes les zones (meme valeur que le scoreboard/bossbar).
                line = format
                        .replace("{zone}", zone.getDisplayName() + ChatColor.RESET)
                        .replace("{points}", String.valueOf(zone.getPoints(factionId)))
                        .replace("{total}", String.valueOf(plugin.getDominationManager().getFactionTotal(factionId)));
            }

            plugin.getActionBarManager().send(p, line);
            playersShownZoneStatusThisTick.add(uuid);
        }
    }

    // ================= Entree/sortie de zone (appele par les listeners) =================

    public void onPlayerEnterZone(Player player, Zone zone) {
        if (plugin.getDominationManager().getState() != DominationState.RUNNING) return;
        zone.getPlayersInside().add(player.getUniqueId());
    }

    public void onPlayerLeaveZone(Player player, Zone zone) {
        zone.getPlayersInside().remove(player.getUniqueId());
    }

    /** A appeler sur mort/deconnexion/teleport : retire le joueur de TOUTES les zones ou il se trouvait. */
    public void onPlayerRemoved(Player player) {
        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            zone.getPlayersInside().remove(player.getUniqueId());
        }
    }
}