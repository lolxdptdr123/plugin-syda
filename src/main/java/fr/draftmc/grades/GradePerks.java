package fr.draftmc.grades;

import fr.draftmc.Draftmc;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Plafonds et accès liés au grade (homes, HDV, vaults, cooldowns repair).
 */
public class GradePerks {
    private final Draftmc plugin;
    private final GradeManager grades;

    public GradePerks(Draftmc plugin, GradeManager grades) {
        this.plugin = plugin;
        this.grades = grades;
    }

    public int maxHomes(Player player) {
        return value(player, "homes", 1);
    }

    public int maxHdv(Player player) {
        return value(player, "hdv", 3);
    }

    public int maxVaults(Player player) {
        return value(player, "vaults", 0);
    }

    public int repairCooldown(Player player) {
        return value(player, "repair-seconds", 7200);
    }

    public int repairAllCooldown(Player player) {
        return value(player, "repair-all-seconds", 21600);
    }

    public int value(Player player, String key, int fallback) {
        List<String> ladder = grades.ladder();
        int index = grades.playerGradeIndex(player);
        for (int i = index; i >= 0; i--) {
            String group = ladder.get(i);
            String path = "grade-perks." + group + "." + key;
            if (plugin.getConfig().contains(path)) {
                return plugin.getConfig().getInt(path);
            }
        }
        return plugin.getConfig().getInt("grade-perks.default." + key, fallback);
    }
}
