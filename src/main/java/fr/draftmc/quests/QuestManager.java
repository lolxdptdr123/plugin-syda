package fr.draftmc.quests;

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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class QuestManager implements CommandExecutor, Listener {
    private final Draftmc plugin;

    public QuestManager(Draftmc plugin) {
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
        Inventory inv = Bukkit.createInventory(new GuiHolder("quete"), 54, CC.color("&8Quêtes"));
        Menus.fill(inv);
        ConfigurationSection quests = plugin.getConfig().getConfigurationSection("quests.list");
        int slot = 10;
        if (quests != null) {
            for (String id : quests.getKeys(false)) {
                ConfigurationSection quest = quests.getConfigurationSection(id);
                if (quest == null) {
                    continue;
                }
                int need = quest.getInt("amount", 10);
                int progress = plugin.data().getInt(player.getUniqueId(), "quest_" + id);
                boolean claimed = plugin.data().getList(player.getUniqueId(), "quests_claimed").contains(id);
                List<String> lore = new ArrayList<String>();
                lore.add("&7" + quest.getString("description", ""));
                lore.add("&7Progression : &e" + Math.min(progress, need) + "&7/&e" + need);
                lore.add("&7Récompense : &a" + plugin.economy().format(quest.getDouble("money", 0))
                        + (quest.getLong("tokens", 0) > 0 ? " &7+ &6" + quest.getLong("tokens") + " tokens" : ""));
                for (String line : quest.getStringList("reward-lore")) {
                    lore.add(line);
                }
                lore.add("");
                if (claimed) {
                    lore.add("&aRécompense récupérée");
                } else if (progress >= need) {
                    lore.add("&eClique pour récupérer !");
                } else {
                    lore.add("&7Termine la quête pour réclamer.");
                }
                inv.setItem(slot, new ItemBuilder(material(quest.getString("material", "BOOK")))
                        .name(quest.getString("name", "&e" + id))
                        .lore(lore)
                        .build());
                slot++;
                if (slot % 9 == 8) {
                    slot += 2;
                }
                if (slot >= 44) {
                    break;
                }
            }
        }
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder) || !"quete".equals(((GuiHolder) holder).menu())) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR || !current.hasItemMeta()) {
            return;
        }
        String clicked = CC.strip(current.getItemMeta().getDisplayName());
        ConfigurationSection quests = plugin.getConfig().getConfigurationSection("quests.list");
        if (quests == null) {
            return;
        }
        for (String id : quests.getKeys(false)) {
            ConfigurationSection quest = quests.getConfigurationSection(id);
            if (quest == null) {
                continue;
            }
            if (!CC.strip(CC.color(quest.getString("name", id))).equalsIgnoreCase(clicked)) {
                continue;
            }
            claim(player, id, quest);
            open(player);
            return;
        }
    }

    private void claim(Player player, String id, ConfigurationSection quest) {
        UUID uuid = player.getUniqueId();
        List<String> claimed = plugin.data().getList(uuid, "quests_claimed");
        if (claimed.contains(id)) {
            plugin.msg(player, "&cQuête déjà récupérée.");
            return;
        }
        int need = quest.getInt("amount", 10);
        if (plugin.data().getInt(uuid, "quest_" + id) < need) {
            plugin.msg(player, "&cQuête non terminée.");
            return;
        }
        claimed.add(id);
        plugin.data().setList(uuid, "quests_claimed", claimed);
        plugin.economy().deposit(player, quest.getDouble("money", 0));
        plugin.tokens().add(uuid, quest.getLong("tokens", 0));
        for (java.util.Map<?, ?> map : quest.getMapList("items")) {
            Object matName = map.get("material");
            if (matName == null) {
                continue;
            }
            int amount = map.get("amount") instanceof Number ? ((Number) map.get("amount")).intValue() : 1;
            ItemBuilder builder = new ItemBuilder(material(String.valueOf(matName)), Math.max(1, amount));
            if (map.get("name") != null) {
                builder.name(String.valueOf(map.get("name")));
            }
            player.getInventory().addItem(builder.build());
        }
        plugin.classement().addQuest(player);
        plugin.msg(player, "&aQuête terminée : " + quest.getString("name", id));
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            if (plugin.factions() != null && event.getEntity().getKiller() != null) {
                plugin.factions().addPvpPoints(event.getEntity().getKiller(), 1);
            }
            progress(event.getEntity().getKiller(), "PVP", "PLAYER");
            return;
        }
        Player killer = event.getEntity().getKiller();
        EntityType type = event.getEntityType();
        progress(killer, "KILL", type.name());
        if (plugin.factions() != null) {
            plugin.factions().addFarmPoints(killer, 1);
            plugin.factions().addMissionProgress(killer, "kill_mobs", 1);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        progress(event.getPlayer(), "BREAK", event.getBlock().getType().name());
        if (plugin.factions() != null) {
            plugin.factions().addFarmPoints(event.getPlayer(), 1);
            plugin.factions().addMissionProgress(event.getPlayer(), "mine_blocks", 1);
        }
    }

    private void progress(Player player, String type, String target) {
        ConfigurationSection quests = plugin.getConfig().getConfigurationSection("quests.list");
        if (quests == null) {
            return;
        }
        for (String id : quests.getKeys(false)) {
            ConfigurationSection quest = quests.getConfigurationSection(id);
            if (quest == null) {
                continue;
            }
            if (!type.equalsIgnoreCase(quest.getString("type", "KILL"))) {
                continue;
            }
            if (!target.equalsIgnoreCase(quest.getString("target", ""))) {
                continue;
            }
            UUID uuid = player.getUniqueId();
            if (plugin.data().getList(uuid, "quests_claimed").contains(id)) {
                continue;
            }
            int need = quest.getInt("amount", 10);
            int now = plugin.data().getInt(uuid, "quest_" + id);
            if (now >= need) {
                continue;
            }
            plugin.data().addInt(uuid, "quest_" + id, 1);
            if (now + 1 >= need) {
                plugin.msg(player, "&aQuête terminée ! &e/quete &7pour récupérer.");
            }
        }
    }

    private Material material(String raw) {
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return Material.BOOK;
        }
    }
}
