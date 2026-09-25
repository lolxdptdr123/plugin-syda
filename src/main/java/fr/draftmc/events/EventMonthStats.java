package fr.draftmc.events;

import fr.draftmc.Draftmc;
import fr.draftmc.classement.ClassementManager;
import org.bukkit.entity.Player;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Compteurs mensuels par joueur (players.yml) pour la commande Discord /stats.
 */
public class EventMonthStats {
    public static final String TOTEM_GIANT_POINTS = "totem_giant_points";
    public static final String TOTEM_BREAKS = "totem_breaks";
    public static final String KOTH_KILLS = "koth_kills";
    public static final String KOTH_DEATHS = "koth_deaths";
    public static final String TF_HITS = "tf_hits";
    public static final String CONQUEST_CAPS = "conquest_caps";
    public static final String DOM_CAPS = "dom_caps";
    public static final String DOM_DEATHS = "dom_deaths";

    private static final String[] MONTHS_FR = {
            "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    private final Draftmc plugin;

    public EventMonthStats(Draftmc plugin) {
        this.plugin = plugin;
    }

    public static void add(Player player, String stat, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        Draftmc host = Draftmc.get();
        if (host == null || host.events() == null || host.events().monthStats() == null) {
            return;
        }
        host.events().monthStats().add(player.getUniqueId(), player.getName(), stat, amount);
    }

    public void add(UUID uuid, String name, String stat, int amount) {
        if (uuid == null || stat == null || amount <= 0) {
            return;
        }
        plugin.data().addInt(uuid, key(stat), amount);
        if (name != null && !name.isEmpty()) {
            plugin.data().setString(uuid, "name", name);
        }
    }

    public String monthId() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) + "-" + String.format(Locale.ROOT, "%02d", cal.get(Calendar.MONTH) + 1);
    }

    public String monthLabel() {
        Calendar cal = Calendar.getInstance();
        return MONTHS_FR[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.YEAR);
    }

    public String key(String stat) {
        return "ev_" + monthId().replace('-', '_') + "_" + stat;
    }

    public List<ClassementManager.Entry> top(String stat, int limit) {
        return plugin.classement().top(key(stat), limit);
    }

    public String toJson(int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":true,\"month\":\"").append(escape(monthId()))
                .append("\",\"label\":\"").append(escape(monthLabel()))
                .append("\",\"boards\":{");
        appendBoard(sb, "totemgeant", "Totem géant — points", TOTEM_GIANT_POINTS, "pts", limit, false);
        sb.append(',');
        appendBoard(sb, "totem", "Totem — blocs cassés", TOTEM_BREAKS, "cassés", limit, false);
        sb.append(',');
        appendBoard(sb, "koth_kills", "KOTH géant — kills", KOTH_KILLS, "kills", limit, false);
        sb.append(',');
        appendBoard(sb, "koth_deaths", "KOTH géant — morts", KOTH_DEATHS, "morts", limit, false);
        sb.append(',');
        appendBoard(sb, "teamfight", "TeamFight — hits", TF_HITS, "hits", limit, false);
        sb.append(',');
        appendBoard(sb, "conquest", "Conquest — points cap", CONQUEST_CAPS, "pts", limit, false);
        sb.append(',');
        appendBoard(sb, "domination_caps", "Domination — points cap", DOM_CAPS, "pts", limit, false);
        sb.append(',');
        appendBoard(sb, "domination_deaths", "Domination — morts", DOM_DEATHS, "morts", limit, true);
        sb.append("}}");
        return sb.toString();
    }

    private void appendBoard(StringBuilder sb, String id, String title, String stat, String unit,
                             int limit, boolean last) {
        sb.append('"').append(id).append("\":{\"title\":\"").append(escape(title))
                .append("\",\"unit\":\"").append(escape(unit)).append("\",\"entries\":[");
        List<ClassementManager.Entry> top = top(stat, limit);
        for (int i = 0; i < top.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            ClassementManager.Entry e = top.get(i);
            sb.append("{\"name\":\"").append(escape(e.name)).append("\",\"value\":").append(e.value).append('}');
        }
        sb.append("]}");
        if (last) {
            // last flag unused besides caller commas
        }
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", " ");
    }
}
