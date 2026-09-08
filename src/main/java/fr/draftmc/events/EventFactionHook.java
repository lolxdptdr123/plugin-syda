package fr.draftmc.events;

import fr.draftmc.Draftmc;
import fr.draftmc.factions.FactionRank;
import org.bukkit.entity.Player;

/**
 * Hook factions Draftmc pour les events (remplace la detection par reflexion
 * des plugins Factions externes).
 */
public class EventFactionHook {
    private final Draftmc plugin;

    public EventFactionHook(Draftmc plugin) {
        this.plugin = plugin;
    }

    public EventFactionHook(java.util.logging.Logger ignored) {
        this.plugin = Draftmc.get();
    }

    public boolean hasRealFactionPlugin() {
        return true;
    }

    public boolean isAvailable() {
        return plugin != null && plugin.factions() != null;
    }

    public boolean hasFaction(Player player) {
        return getFactionId(player) != null;
    }

    public String getFactionId(Player player) {
        if (player == null || plugin.factions() == null) {
            return null;
        }
        String id = plugin.factions().factionOf(player);
        return id == null || id.isEmpty() ? null : id;
    }

    public String getFactionDisplayName(String factionId) {
        if (factionId == null || factionId.isEmpty()) {
            return "?";
        }
        if (plugin.factions() == null) {
            return factionId;
        }
        String name = plugin.factions().displayName(factionId);
        return name == null || name.isEmpty() ? factionId : name;
    }

    public boolean isFactionLeader(Player player) {
        if (player == null || plugin.factions() == null) {
            return false;
        }
        String fac = plugin.factions().factionOf(player);
        if (fac == null || fac.isEmpty()) {
            return false;
        }
        return plugin.factions().rankOf(player) == FactionRank.LEADER;
    }

    public String debugInfo(Player player) {
        if (player == null || plugin.factions() == null) {
            return "Factions Draftmc indisponibles.";
        }
        String fac = plugin.factions().factionOf(player);
        if (fac == null || fac.isEmpty()) {
            return "Aucune faction (Wilderness).";
        }
        return "Faction: " + plugin.factions().displayName(fac)
                + " (" + fac + ") rang=" + plugin.factions().rankOf(player).name()
                + " leader=" + isFactionLeader(player);
    }
}
