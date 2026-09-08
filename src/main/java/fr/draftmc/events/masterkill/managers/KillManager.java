package fr.draftmc.events.masterkill.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comptabilise les kills par EQUIPE (nom d'equipe manuel, pas une vraie
 * faction) sur TOUT le match (1 kill = 1 point), independamment du fait
 * que l'equipe soit encore en vie ou deja eliminee - le classement des
 * recompenses se fait sur le total de kills, pas sur la survie.
 * Comptabilise aussi les kills INDIVIDUELS de chaque joueur (affiches
 * dans le scoreboard, "Vos kills").
 */
public class KillManager {

    private final Map<String, Integer> teamKills = new LinkedHashMap<>();
    private final Map<UUID, Integer> personalKills = new HashMap<>();

    public void addKill(String teamName) {
        teamKills.merge(teamName, 1, Integer::sum);
    }

    public int getKills(String teamName) {
        Integer value = teamKills.get(teamName);
        return value == null ? 0 : value;
    }

    public Map<String, Integer> getAllKills() {
        return teamKills;
    }

    public void addPersonalKill(UUID player) {
        personalKills.merge(player, 1, Integer::sum);
    }

    public int getPersonalKills(UUID player) {
        Integer value = personalKills.get(player);
        return value == null ? 0 : value;
    }

    /** Classement descendant, limite a "limit" entrees. */
    public List<Map.Entry<String, Integer>> getTop(int limit) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(teamKills.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public void reset() {
        teamKills.clear();
        personalKills.clear();
    }
}
