package fr.draftmc.social;

import fr.draftmc.Draftmc;
import fr.draftmc.gui.GuiHolder;
import fr.draftmc.gui.Menus;
import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class YtCommand implements CommandExecutor, Listener {
    private final Draftmc plugin;

    public YtCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        open((Player) sender);
        return true;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder("yt"), 27, CC.color("&8Créateurs"));
        Menus.fill(inv);
        inv.setItem(11, head("streamer"));
        inv.setItem(13, head("youtubeur"));
        inv.setItem(15, head("partenaire"));
        inv.setItem(22, Menus.close());
        player.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack head(String id) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("yt-menu." + id);
        String name = sec != null ? sec.getString("name", "&e" + id) : "&e" + id;
        String owner = sec != null ? sec.getString("skull", "MHF_Question") : "MHF_Question";
        List<String> lore = sec != null ? sec.getStringList("lore") : java.util.Arrays.asList("&7Bientôt.");
        return Menus.skull(owner, name, lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder) || !"yt".equals(((GuiHolder) holder).menu())) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() == 22) {
            event.getWhoClicked().closeInventory();
        }
    }
}
