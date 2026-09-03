package fr.sydaria.placeholders;

import fr.sydaria.Sydaria;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class SydariaPlaceholders extends PlaceholderExpansion {
    private final Sydaria plugin;

    public SydariaPlaceholders(Sydaria plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "sydaria";
    }

    @Override
    public String getAuthor() {
        return "Sydaria";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }
        if (params.equalsIgnoreCase("tokens")) {
            return String.valueOf(plugin.tokens().get(player.getUniqueId()));
        }
        if (params.equalsIgnoreCase("grade")) {
            String tag = plugin.tags().selectedName(player);
            return tag == null || tag.isEmpty() ? plugin.getConfig().getString("scoreboard.grade-default", "Joueur") : tag;
        }
        if (params.equalsIgnoreCase("power")) {
            int current = (int) Math.round(plugin.factions().currentPower(player.getUniqueId()));
            int max = (int) Math.round(plugin.getConfig().getDouble("factions.power.max", 10));
            return current + " / " + max;
        }
        if (params.equalsIgnoreCase("votes")) {
            if (plugin.voteParty() == null) {
                return "0/0";
            }
            return plugin.voteParty().current() + " / " + plugin.voteParty().goal();
        }
        if (params.equalsIgnoreCase("money")) {
            return plugin.economy().format(player);
        }
        if (params.equalsIgnoreCase("kills")) {
            return String.valueOf(plugin.data().getInt(player.getUniqueId(), "players_killed"));
        }
        if (params.equalsIgnoreCase("deaths")) {
            return String.valueOf(plugin.data().getInt(player.getUniqueId(), "deaths"));
        }
        if (params.equalsIgnoreCase("faction")) {
            return plugin.factions().displayOf(player);
        }
        if (params.equalsIgnoreCase("faction_rank")) {
            if (plugin.factions().factionOf(player).isEmpty()) {
                return "Aucun";
            }
            return plugin.factions().rankOf(player).display();
        }
        if (params.equalsIgnoreCase("tag")) {
            return plugin.tags().display(player);
        }
        return null;
    }
}
