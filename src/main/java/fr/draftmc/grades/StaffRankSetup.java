package fr.draftmc.grades;

import fr.draftmc.Draftmc;
import org.bukkit.Bukkit;

/**
 * Crée les groupes LuckPerms helper / modo / admin / owner une seule fois.
 * Helper: /mute /sc. Modo: freeze mute ban unban sc. Admin: draftmc.admin. Owner: tab only (+ admin).
 */
public class StaffRankSetup {
    private final Draftmc plugin;

    public StaffRankSetup(Draftmc plugin) {
        this.plugin = plugin;
    }

    public void setupIfNeeded() {
        if (!plugin.getConfig().getBoolean("staff-ranks.auto-setup", true)) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        if (plugin.getConfig().getBoolean("staff-ranks.setup-done", false)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                apply();
            }
        }, 40L);
    }

    public void apply() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().warning("LuckPerms absent: groupes helper/modo/admin/owner non créés.");
            return;
        }
        console("lp creategroup helper");
        console("lp creategroup modo");
        console("lp creategroup admin");
        console("lp creategroup owner");

        console("lp group helper meta setprefix \"&aHelper \"");
        console("lp group helper meta setweight 80");
        console("lp group helper permission set draftmc.staff.chat true");
        console("lp group helper permission set draftmc.staff.mute true");

        console("lp group modo meta setprefix \"&1Modo \"");
        console("lp group modo meta setweight 90");
        console("lp group modo permission set draftmc.staff.chat true");
        console("lp group modo permission set draftmc.staff.mute true");
        console("lp group modo permission set draftmc.staff.freeze true");
        console("lp group modo permission set draftmc.staff.ban true");
        console("lp group modo permission set draftmc.staff.unban true");

        console("lp group admin meta setprefix \"&cAdmin \"");
        console("lp group admin meta setweight 100");
        console("lp group admin parent add modo");
        console("lp group admin permission set draftmc.admin true");
        console("lp group admin permission set draftmc.staff true");
        console("lp group admin permission set draftmc.staff.chat true");
        console("lp group admin permission set draftmc.staff.adminchat true");
        console("lp group admin permission set draftmc.faction.bypass true");
        console("lp group admin permission set draftmc.fly true");
        console("lp group admin permission set draftmc.gradecommands.bypass true");

        console("lp group owner meta setprefix \"&4Owner \"");
        console("lp group owner meta setweight 110");
        console("lp group owner parent add admin");

        plugin.getConfig().set("staff-ranks.setup-done", true);
        plugin.saveConfig();
        plugin.getLogger().info("Groupes LuckPerms helper/modo/admin/owner créés.");
    }

    private void console(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
