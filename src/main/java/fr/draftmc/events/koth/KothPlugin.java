package fr.draftmc.events.koth;

import fr.draftmc.Draftmc;
import fr.draftmc.events.EventFactionHook;
import fr.draftmc.events.EventModule;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class KothPlugin extends EventModule {
    private EventFactionHook factionHook;
    private KothManager kothManager;
    private KothScoreboard scoreboard;
    private KothScheduler scheduler;

    public KothPlugin(Draftmc host) {
        super(host, "koth", "koth.yml");
        enable();
    }

    private void enable() {
        factionHook = new EventFactionHook(getHost());
        kothManager = new KothManager(this);
        scoreboard = new KothScoreboard(this);
        scheduler = new KothScheduler(this);
        getServer().getPluginManager().registerEvents(new KothListener(this), getHost());
        KothCommand command = new KothCommand(this);
        if (getCommand("koth") != null) {
            getCommand("koth").setExecutor(command);
            getCommand("koth").setTabCompleter(command);
        } else {
            getLogger().warning("[KOTH] Commande /koth absente de plugin.yml");
        }
        getLogger().info("[KOTH] Event charge (koth.yml / zones.yml).");
    }

    @Override
    public void disable() {
        if (scheduler != null) {
            scheduler.stop();
        }
        if (kothManager != null) {
            kothManager.stopAll();
        }
    }

    public EventFactionHook getEventFactionHook() {
        return factionHook;
    }

    public KothManager getKothManager() {
        return kothManager;
    }

    public KothScoreboard getScoreboard() {
        return scoreboard;
    }

    public KothScheduler getScheduler() {
        return scheduler;
    }

    public String prefix() {
        return CC.color(getConfig().getString("prefix", "&8[&6KOTH GEANT&8] &7"));
    }

    public int rankingPoints(int place) {
        return getConfig().getInt("ranking-points." + place, place == 1 ? 15 : (place == 2 ? 10 : (place == 3 ? 5 : 0)));
    }

    public void broadcast(String key, KothZone zone, Player player, int lost, int percent) {
        String text = format(key, zone, player, lost, percent);
        if (!text.isEmpty()) {
            Bukkit.broadcastMessage(prefix() + text);
        }
    }

    public void broadcastScheduled(KothZone zone, String day, String time) {
        String raw = getConfig().getString("messages.scheduled", "&aKOTH Geant &e{zone} &alance automatiquement &7({day} {time}).");
        Bukkit.broadcastMessage(prefix() + CC.color(raw
                .replace("{zone}", zone.getDisplay())
                .replace("{day}", day)
                .replace("{time}", time)));
    }

    public void broadcastRanking(List<Map.Entry<String, Integer>> ranking) {
        String header = getConfig().getString("messages.ranking-header", "&6Classement:");
        Bukkit.broadcastMessage(prefix() + CC.color(header));
        String line = getConfig().getString("messages.ranking-line",
                "&e{place} place: &f{faction} &7({points}points)");
        String[] places = new String[] {"1ere", "2eme", "3eme"};
        int max = Math.min(3, ranking.size());
        for (int i = 0; i < max; i++) {
            Map.Entry<String, Integer> entry = ranking.get(i);
            String faction = factionHook.getFactionDisplayName(entry.getKey());
            int reward = rankingPoints(i + 1);
            Bukkit.broadcastMessage(prefix() + CC.color(line
                    .replace("{place}", places[i])
                    .replace("{faction}", faction)
                    .replace("{points}", String.valueOf(reward))));
        }
    }

    public String format(String key, KothZone zone, Player player, int lost, int percent) {
        String raw = getConfig().getString("messages." + key, "");
        if (raw == null) {
            raw = "";
        }
        String faction = "";
        if (player != null && factionHook.getFactionId(player) != null) {
            faction = factionHook.getFactionDisplayName(factionHook.getFactionId(player));
        }
        return CC.color(raw
                .replace("{zone}", zone == null ? "" : zone.getDisplay())
                .replace("{player}", player == null ? "" : player.getName())
                .replace("{faction}", faction)
                .replace("{lost}", String.valueOf(lost))
                .replace("{percent}", String.valueOf(percent))
                .replace("{type}", zone == null ? "" : zone.getType().display()));
    }
}
