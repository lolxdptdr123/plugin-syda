package fr.draftmc.combat;

import fr.draftmc.Draftmc;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Multiplicateur de durabilité des armures.
 * Sharpness / Force / enchants restent inchangés.
 */
public class CombatDamageListener implements Listener {
    private final Draftmc plugin;
    private final Map<UUID, short[]> armorBefore = new HashMap<UUID, short[]>();

    public CombatDamageListener(Draftmc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStore(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        double mult = plugin.getConfig().getDouble("combat.armor-durability-multiplier", 1.0);
        if (mult >= 0.999) {
            return;
        }
        Player player = (Player) event.getEntity();
        armorBefore.put(player.getUniqueId(), readDurabilities(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorAdjust(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        short[] before = armorBefore.remove(player.getUniqueId());
        if (before == null) {
            return;
        }
        double mult = plugin.getConfig().getDouble("combat.armor-durability-multiplier", 1.0);
        if (mult < 0.0) {
            mult = 0.0;
        }
        if (mult >= 0.999) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length && i < before.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || piece.getType() == Material.AIR || piece.getType().getMaxDurability() <= 0) {
                continue;
            }
            short lost = (short) (piece.getDurability() - before[i]);
            if (lost <= 0) {
                continue;
            }
            short keep = (short) Math.max(0, Math.round(lost * mult));
            short next = (short) (before[i] + keep);
            if (next != piece.getDurability()) {
                piece.setDurability(next);
                armor[i] = piece;
                changed = true;
            }
        }
        if (changed) {
            inv.setArmorContents(armor);
            player.updateInventory();
        }
    }

    private short[] readDurabilities(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        short[] out = new short[4];
        for (int i = 0; i < 4; i++) {
            if (armor == null || i >= armor.length || armor[i] == null) {
                out[i] = 0;
            } else {
                out[i] = armor[i].getDurability();
            }
        }
        return out;
    }
}
