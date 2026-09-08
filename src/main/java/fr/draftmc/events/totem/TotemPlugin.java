package fr.draftmc.events.totem;

import fr.draftmc.Draftmc;
import fr.draftmc.events.EventFactionHook;
import fr.draftmc.events.EventHub;
import fr.draftmc.events.EventModule;
import fr.draftmc.events.EventType;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TotemPlugin extends EventModule {
    private EventFactionHook factionHook;
    private TotemManager totemManager;
    private TotemScoreboard scoreboard;

    public TotemPlugin(Draftmc host) {
        super(host, "totem", "totem.yml");
        enable();
    }

    private void enable() {
        factionHook = new EventFactionHook(getHost());
        totemManager = new TotemManager(this);
        scoreboard = new TotemScoreboard(this);
        getServer().getPluginManager().registerEvents(new TotemListener(this), getHost());
        TotemCommand command = new TotemCommand(this);
        if (getCommand("totem") != null) {
            getCommand("totem").setExecutor(command);
            getCommand("totem").setTabCompleter(command);
        } else {
            getLogger().warning("[Totem] Commande /totem absente de plugin.yml");
        }
        if (getCommand("totemgeant") != null) {
            getCommand("totemgeant").setExecutor(command);
            getCommand("totemgeant").setTabCompleter(command);
        }
        getLogger().info("[Totem] Event charge (totem.yml).");
    }

    @Override
    public void disable() {
        if (totemManager != null) {
            totemManager.stopAll();
        }
    }

    public EventFactionHook getEventFactionHook() {
        return factionHook;
    }

    public TotemManager getTotemManager() {
        return totemManager;
    }

    public TotemScoreboard getScoreboard() {
        return scoreboard;
    }

    public boolean isHasteBlocked() {
        return totemManager != null && totemManager.isBusy();
    }

    public void onFactionDisband(String factionId) {
        if (totemManager != null) {
            totemManager.onFactionDisband(factionId);
        }
    }

    public void onTotemStarting() {
        if (scoreboard != null) {
            scoreboard.start();
        }
        syncHaste();
    }

    public void onTotemEnded() {
        if (scoreboard != null) {
            scoreboard.stop();
        }
        syncHaste();
    }

    private void syncHaste() {
        if (getHost().atouts() == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            getHost().atouts().resync(player);
        }
    }

    public String prefix() {
        if (totemManager != null && totemManager.isGiantMode()) {
            return CC.color(getConfig().getString("giant.prefix", "&8[&6Totem Geant&8] &7"));
        }
        return CC.color(getConfig().getString("prefix", "&8[&6Totem&8] &7"));
    }

    public int oneshotBonus() {
        return Math.max(0, getConfig().getInt("giant.oneshot-bonus", 10));
    }

    public int rankingPoints(int place) {
        return getConfig().getInt("giant.ranking-points." + place,
                place == 1 ? 15 : (place == 2 ? 10 : (place == 3 ? 5 : 0)));
    }

    public int blockPointsFor(Totem totem, int blockIndex) {
        List<Integer> points = getConfig().getIntegerList("giant.block-points");
        if (points == null || points.isEmpty()) {
            points = java.util.Arrays.asList(1, 2, 4, 6, 8);
        }
        if (totem == null) {
            return 0;
        }
        int fromTop = totem.getSize() - 1 - blockIndex;
        if (fromTop < 0) {
            return 0;
        }
        if (fromTop < points.size()) {
            return points.get(fromTop).intValue();
        }
        return points.get(points.size() - 1).intValue();
    }

    public String format(String key, Totem totem, Player player) {
        String raw = getConfig().getString("messages." + key, "");
        if (raw == null) {
            raw = "";
        }
        String faction = "";
        if (player != null && factionHook.getFactionId(player) != null) {
            faction = factionHook.getFactionDisplayName(factionHook.getFactionId(player));
        } else if (totem != null && totem.getCapturingFactionId() != null) {
            faction = factionHook.getFactionDisplayName(totem.getCapturingFactionId());
        }
        String blocked = "";
        if (totem != null && totem.getCapturingFactionId() != null) {
            blocked = factionHook.getFactionDisplayName(totem.getCapturingFactionId());
        }
        int seconds = totemManager == null ? 0 : Math.max(0, totemManager.getCountdownSecondsLeft());
        int points = getConfig().getInt("pvp-points", 15);
        int score = 0;
        if (player != null && factionHook.getFactionId(player) != null && totemManager != null) {
            score = totemManager.getScore(factionHook.getFactionId(player));
        } else if (totem != null && totem.getCapturingFactionId() != null && totemManager != null) {
            score = totemManager.getScore(totem.getCapturingFactionId());
        }
        return CC.color(raw
                .replace("{faction}", faction)
                .replace("{blocked}", blocked)
                .replace("{player}", player != null ? player.getName() : "")
                .replace("{blocks}", totem != null ? String.valueOf(totem.getActualSize()) : "0")
                .replace("{block}", ordinal(totem == null ? 0 : totem.getLastBlockNumber()))
                .replace("{totem}", totem != null ? totem.getName() : "")
                .replace("{seconds}", String.valueOf(seconds))
                .replace("{points}", String.valueOf(points))
                .replace("{gained}", totem != null ? String.valueOf(totem.getLastGained()) : "0")
                .replace("{score}", String.valueOf(score))
                .replace("{bonus}", totem != null ? String.valueOf(totem.getLastBonus()) : "0")
                .replace("%FACTION_NAME%", faction)
                .replace("%PLAYER_NAME%", player != null ? player.getName() : "")
                .replace("%BLOCK_NUMBER%", totem != null ? String.valueOf(totem.getActualSize()) : "0")
                .replace("%TOTEM_NAME%", totem != null ? totem.getName() : ""));
    }

    private String ordinal(int n) {
        if (n <= 0) {
            return "";
        }
        if (n == 1) {
            return "1er";
        }
        return n + "ème";
    }

    public void send(CommandSender sender, String key, Totem totem, Player player) {
        String text = format(key, totem, player);
        if (!text.isEmpty()) {
            sender.sendMessage(prefix() + text);
        }
    }

    public void broadcast(String key, Totem totem, Player player) {
        String text = format(key, totem, player);
        if (!text.isEmpty()) {
            Bukkit.broadcastMessage(prefix() + text);
        }
    }

    public void victory(Totem totem, Player player) {
        broadcast("win", totem, player);
        if (totem.getLocation() != null && totem.getLocation().getWorld() != null) {
            totem.getLocation().getWorld().playEffect(totem.getLocation(), Effect.FIREWORKS_SPARK, 1);
        }
        String factionId = totem.getCapturingFactionId();
        String faction = factionId == null ? "?" : factionHook.getFactionDisplayName(factionId);
        EventHub hub = getHost().events();
        int points = hub != null ? hub.topPointsFor(EventType.TOTEM) : getConfig().getInt("pvp-points", 15);
        if (hub != null && factionId != null) {
            hub.awardTopPoints(EventType.TOTEM, factionId);
        }
        List<String> rewards = getConfig().getStringList("reward-commands");
        for (String command : rewards) {
            if (command == null || command.isEmpty()) {
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("{faction}", faction)
                    .replace("{player}", player != null ? player.getName() : "")
                    .replace("{totem}", totem.getName())
                    .replace("{points}", String.valueOf(points)));
        }
        totem.finishWithBedrock();
        onTotemEnded();
        if (hub != null) {
            hub.clearActive(EventType.TOTEM, totem.getName());
        }
    }

    public void completeGiant(Totem totem, List<java.util.Map.Entry<String, Integer>> ranking) {
        if (ranking == null) {
            ranking = java.util.Collections.emptyList();
        }
        broadcast("end-giant", totem, null);
        broadcastRanking(ranking);
        EventHub hub = getHost().events();
        int place = 1;
        for (java.util.Map.Entry<String, Integer> entry : ranking) {
            if (place > 3) {
                break;
            }
            int reward = rankingPoints(place);
            if (hub != null && reward > 0) {
                hub.awardTopPoints(EventType.TOTEM_GEANT, entry.getKey(), reward);
            }
            place++;
        }
        if (totem != null && ranking != null && !ranking.isEmpty()) {
            String winnerId = ranking.get(0).getKey();
            String faction = winnerId == null ? "?" : factionHook.getFactionDisplayName(winnerId);
            List<String> rewards = getConfig().getStringList("giant.reward-commands");
            if (rewards == null || rewards.isEmpty()) {
                rewards = getConfig().getStringList("reward-commands");
            }
            for (String command : rewards) {
                if (command == null || command.isEmpty()) {
                    continue;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                        .replace("{faction}", faction)
                        .replace("{totem}", totem.getName())
                        .replace("{points}", String.valueOf(ranking.get(0).getValue())));
            }
        }
        onTotemEnded();
        if (hub != null) {
            hub.clearActive(EventType.TOTEM_GEANT, totem == null ? null : totem.getName());
        }
    }

    public void broadcastRanking(List<java.util.Map.Entry<String, Integer>> ranking) {
        String header = getConfig().getString("messages.ranking-header", "&6Classement:");
        Bukkit.broadcastMessage(prefix() + CC.color(header));
        String line = getConfig().getString("messages.ranking-line",
                "&e{place}: &f{faction} &7({score} pts)");
        String[] places = new String[] {"1ere", "2eme", "3eme"};
        int max = Math.min(3, ranking == null ? 0 : ranking.size());
        if (max == 0) {
            Bukkit.broadcastMessage(prefix() + CC.color("&7Aucune faction n'a marque de points."));
            return;
        }
        for (int i = 0; i < max; i++) {
            java.util.Map.Entry<String, Integer> entry = ranking.get(i);
            String faction = factionHook.getFactionDisplayName(entry.getKey());
            int reward = rankingPoints(i + 1);
            Bukkit.broadcastMessage(prefix() + CC.color(line
                    .replace("{place}", places[i])
                    .replace("{faction}", faction)
                    .replace("{score}", String.valueOf(entry.getValue()))
                    .replace("{points}", String.valueOf(reward))));
        }
    }
}
