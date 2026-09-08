package fr.draftmc.events.teamfight;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class FightStats {
    private int hits;
    private int potions;
    private final Map<String, Integer> potionsByType = new LinkedHashMap<String, Integer>();
    private final Map<UUID, Integer> hitsPerEnemy = new LinkedHashMap<UUID, Integer>();

    public void addHit(UUID opponent) {
        hits++;
        if (opponent == null) {
            return;
        }
        Integer current = hitsPerEnemy.get(opponent);
        hitsPerEnemy.put(opponent, current == null ? 1 : current.intValue() + 1);
    }

    public void addPotion(String type) {
        potions++;
        String key = type == null || type.isEmpty() ? "Inconnue" : type;
        Integer current = potionsByType.get(key);
        potionsByType.put(key, current == null ? 1 : current.intValue() + 1);
    }

    public int getHits() {
        return hits;
    }

    public int getPotions() {
        return potions;
    }

    public Map<String, Integer> getPotionsByType() {
        return potionsByType;
    }

    public Map<UUID, Integer> getHitsPerEnemy() {
        return hitsPerEnemy;
    }
}
