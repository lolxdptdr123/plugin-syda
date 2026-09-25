package fr.draftmc.help;

import fr.draftmc.Draftmc;
import fr.draftmc.gui.GuiHolder;
import fr.draftmc.gui.Menus;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HelpCommand implements CommandExecutor, Listener {
    private final Draftmc plugin;

    public HelpCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        openMain((Player) sender);
        return true;
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder("help-main"), 54, CC.color("&8Aide Draftmc"));
        Menus.fill(inv);
        inv.setItem(4, new ItemBuilder(Material.BOOK)
                .name("&6&lAide")
                .lore("&7Choisis une catégorie", "&7pour voir les commandes.").build());
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        List<String> cats = categories();
        for (int i = 0; i < cats.size() && i < slots.length; i++) {
            String id = cats.get(i);
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("help-gui.categories." + id);
            if (sec == null) {
                continue;
            }
            Material mat = material(sec.getString("material", "PAPER"));
            inv.setItem(slots[i], new ItemBuilder(mat)
                    .name(sec.getString("name", "&e" + id))
                    .lore(loreOr(sec, "&7Clique pour voir les commandes."))
                    .build());
        }
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    public void openCategory(Player player, String category) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("help-gui.categories." + category);
        if (sec == null) {
            openMain(player);
            return;
        }
        if (isChatCategory(sec)) {
            player.closeInventory();
            sendChat(player, sec);
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("help-cat", category), 54,
                CC.color(sec.getString("title", sec.getString("name", "&8Aide"))));
        Menus.fill(inv);
        int slot = 10;
        List<?> items = sec.getList("items");
        if (items != null && !items.isEmpty()) {
            for (Object raw : items) {
                if (!(raw instanceof java.util.Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) raw;
                inv.setItem(slot, helpItem(map));
                slot = nextSlot(slot);
                if (slot >= 44) {
                    break;
                }
            }
        } else {
            for (String line : sec.getStringList("commands")) {
                String[] parts = line.split("\\|", 4);
                String cmd = parts[0].trim();
                String desc = parts.length > 1 ? parts[1].trim() : "";
                String run = parts.length > 2 ? parts[2].trim() : "";
                String icon = parts.length > 3 ? parts[3].trim() : "PAPER";
                List<String> lore = new ArrayList<String>();
                if (!desc.isEmpty()) {
                    lore.add("&7" + desc);
                }
                if (!run.isEmpty()) {
                    lore.add("");
                    lore.add("&eClique pour ouvrir.");
                }
                inv.setItem(slot, iconItem(icon, "&e" + cmd, lore));
                slot = nextSlot(slot);
                if (slot >= 44) {
                    break;
                }
            }
        }
        inv.setItem(45, Menus.back());
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder)) {
            return;
        }
        GuiHolder gui = (GuiHolder) holder;
        if (!gui.menu().startsWith("help-")) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if ("help-main".equals(gui.menu())) {
            List<String> cats = categories();
            int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < cats.size() && i < slots.length; i++) {
                if (slot == slots[i]) {
                    openCategory(player, cats.get(i));
                    return;
                }
            }
            return;
        }
        if (slot == 45) {
            openMain(player);
            return;
        }
        if (!current.hasItemMeta() || !current.getItemMeta().hasDisplayName()) {
            return;
        }
        String clicked = CC.strip(current.getItemMeta().getDisplayName());
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("help-gui.categories." + gui.extra());
        if (sec == null) {
            return;
        }
        List<?> items = sec.getList("items");
        if (items != null) {
            for (Object raw : items) {
                if (!(raw instanceof java.util.Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) raw;
                String name = String.valueOf(map.get("name") == null ? "" : map.get("name"));
                Object run = map.get("run");
                if (CC.strip(CC.color(name)).equalsIgnoreCase(clicked) && run != null && !String.valueOf(run).trim().isEmpty()) {
                    player.closeInventory();
                    player.performCommand(String.valueOf(run).trim());
                    return;
                }
            }
        }
        for (String line : sec.getStringList("commands")) {
            String[] parts = line.split("\\|", 4);
            if (parts[0].trim().equalsIgnoreCase(clicked) && parts.length > 2 && !parts[2].trim().isEmpty()) {
                player.closeInventory();
                player.performCommand(parts[2].trim());
                return;
            }
        }
    }

    private boolean isChatCategory(ConfigurationSection sec) {
        String action = sec.getString("action", "");
        return "chat".equalsIgnoreCase(action) || "link".equalsIgnoreCase(action);
    }

    private void sendChat(Player player, ConfigurationSection sec) {
        List<String> messages = sec.getStringList("messages");
        if (messages == null || messages.isEmpty()) {
            String one = sec.getString("message", "&e" + sec.getString("link", ""));
            messages = Arrays.asList(one);
        }
        String link = sec.getString("link", "");
        for (String line : messages) {
            player.sendMessage(CC.color(line.replace("{link}", link)));
        }
    }

    private ItemStack helpItem(java.util.Map<String, Object> map) {
        String icon = String.valueOf(map.get("material") == null ? "PAPER" : map.get("material"));
        String name = String.valueOf(map.get("name") == null ? "&eItem" : map.get("name"));
        List<String> lore = new ArrayList<String>();
        Object rawLore = map.get("lore");
        if (rawLore instanceof List) {
            for (Object line : (List<?>) rawLore) {
                lore.add(String.valueOf(line));
            }
        }
        Object run = map.get("run");
        if (run != null && !String.valueOf(run).trim().isEmpty()) {
            lore.add("");
            lore.add("&eClique pour ouvrir.");
        }
        return iconItem(icon, name, lore);
    }

    private ItemStack iconItem(String icon, String name, List<String> lore) {
        short data = 0;
        String matName = icon;
        int colon = icon.indexOf(':');
        if (colon > 0) {
            matName = icon.substring(0, colon);
            try {
                data = Short.parseShort(icon.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
                data = 0;
            }
        }
        return new ItemBuilder(material(matName), 1, data).name(name).lore(lore).build();
    }

    private int nextSlot(int slot) {
        slot++;
        if (slot % 9 == 8) {
            slot += 2;
        }
        return slot;
    }

    private List<String> categories() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("help-gui.categories");
        if (section == null) {
            return Arrays.asList("general", "faction", "economie", "grades", "teleport", "events");
        }
        return new ArrayList<String>(section.getKeys(false));
    }

    private List<String> loreOr(ConfigurationSection sec, String fallback) {
        List<String> lore = sec.getStringList("lore");
        if (lore == null || lore.isEmpty()) {
            return Arrays.asList(fallback);
        }
        return lore;
    }

    private Material material(String raw) {
        try {
            return Material.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            return Material.PAPER;
        }
    }
}
