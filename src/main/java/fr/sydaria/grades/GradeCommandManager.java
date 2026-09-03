package fr.sydaria.grades;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.Cooldowns;
import fr.sydaria.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Commandes débloquées selon le grade du joueur (LuckPerms).
 * Configurable dans grade-commands.commands.<id>.
 */
public class GradeCommandManager implements CommandExecutor, Listener {
    private final Sydaria plugin;

    public GradeCommandManager(Sydaria plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("grade-commands.enabled", true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        String key = command.getName().toLowerCase(Locale.ROOT);

        if ("grades".equals(key) || "gradecmds".equals(key)) {
            sendHelp(player);
            return true;
        }

        if (!isEnabled()) {
            plugin.msg(player, "&cLes commandes de grade sont desactivees.");
            return true;
        }

        ConfigurationSection section = commandSection(key);
        if (section == null) {
            plugin.msg(player, "&cCommande inconnue.");
            return true;
        }

        if (!checkAccess(player, key, section)) {
            return true;
        }

        if ("feed".equals(key)) {
            feed(player);
        } else if ("pv".equals(key)) {
            openVault(player, section, args);
        } else if ("ec".equals(key)) {
            player.openInventory(player.getEnderChest());
        } else if ("refill".equals(key)) {
            refill(player, section);
        } else if ("craft".equals(key)) {
            player.openWorkbench(player.getLocation(), true);
        } else if ("near".equals(key)) {
            near(player, section);
        } else {
            plugin.msg(player, "&cCette commande n'est pas encore implementee.");
        }
        return true;
    }

    private void sendHelp(Player player) {
        plugin.msg(player, "&6&lCommandes par grade");
        plugin.msg(player, "&7Ton grade: &e" + plugin.grades().displayName(player));
        ConfigurationSection commands = plugin.getConfig().getConfigurationSection("grade-commands.commands");
        if (commands == null) {
            plugin.msg(player, "&cSection grade-commands.commands manquante dans config.yml");
            return;
        }
        for (String id : commands.getKeys(false)) {
            ConfigurationSection sec = commands.getConfigurationSection(id);
            if (sec == null || !sec.getBoolean("enabled", true)) {
                continue;
            }
            String minGrade = sec.getString("min-grade");
            if (minGrade == null || minGrade.isEmpty()) {
                continue;
            }
            boolean unlocked = plugin.grades().hasMinGrade(player, minGrade);
            String status = unlocked ? "&a[OK]" : "&c[X]";
            String usage = "pv".equals(id) ? "/pv [numero]" : "/" + id;
            plugin.msg(player, status + " &e" + usage + " &7- grade &e"
                    + plugin.grades().displayNameForGroup(minGrade) + " &7min.");
        }
    }

    private ConfigurationSection commandSection(String key) {
        return plugin.getConfig().getConfigurationSection("grade-commands.commands." + key);
    }

    private boolean checkAccess(Player player, String key, ConfigurationSection section) {
        if (!section.getBoolean("enabled", true)) {
            plugin.msg(player, "&cCette commande est desactivee.");
            return false;
        }
        String minGrade = section.getString("min-grade");
        if (minGrade == null || minGrade.isEmpty()) {
            plugin.msg(player, "&cCommande mal configuree (&emin-grade&c manquant).");
            return false;
        }
        if (!plugin.grades().hasMinGrade(player, minGrade)) {
            String msg = plugin.getConfig().getString("grade-commands.no-permission",
                    "&cTu dois etre au grade &e%grade% &cminimum pour utiliser &e/%cmd%&c.");
            msg = msg.replace("%grade%", plugin.grades().displayNameForGroup(minGrade))
                    .replace("%cmd%", key);
            plugin.msg(player, msg);
            return false;
        }
        int cooldown = section.getInt("cooldown-seconds", 0);
        if (cooldown > 0) {
            int remaining = Cooldowns.remaining(player, "gradecmd:" + key);
            if (remaining > 0) {
                String msg = plugin.getConfig().getString("grade-commands.cooldown-message",
                        "&cCommande en cooldown. &7Attends &e%time%s&7.");
                plugin.msg(player, msg.replace("%time%", String.valueOf(remaining)));
                return false;
            }
            Cooldowns.ready(player, "gradecmd:" + key, cooldown);
        }
        return true;
    }

    private void feed(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20f);
        plugin.msg(player, plugin.getConfig().getString("grade-commands.messages.feed", "&aTu as ete nourri."));
    }

    private void near(Player player, ConfigurationSection section) {
        int radius = section.getInt("radius", 100);
        List<String> found = new ArrayList<String>();
        Location origin = player.getLocation();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || !other.getWorld().equals(origin.getWorld())) {
                continue;
            }
            if (other.getLocation().distance(origin) <= radius) {
                found.add(other.getName());
            }
        }
        if (found.isEmpty()) {
            plugin.msg(player, "&7Aucun joueur dans un rayon de &e" + radius + " &7blocs.");
            return;
        }
        plugin.msg(player, "&7Joueurs proches (&e" + found.size() + "&7) : &f" + join(found));
    }

    private void openVault(Player player, ConfigurationSection section, String[] args) {
        int maxVaults = Math.max(1, section.getInt("max-vaults", 5));
        int vaultId = 1;
        if (args.length > 0) {
            try {
                vaultId = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                plugin.msg(player, "&cUsage: /pv [1-" + maxVaults + "]");
                return;
            }
        }
        if (vaultId < 1 || vaultId > maxVaults) {
            plugin.msg(player, "&cNumero invalide. Utilise &e/pv 1 &cà &e/pv " + maxVaults + "&c.");
            return;
        }
        int size = Math.max(9, Math.min(54, section.getInt("size", 54)));
        size = (size / 9) * 9;
        String titleTemplate = section.getString("title", "&8Coffre prive #%number%");
        String title = CC.color(titleTemplate.replace("%number%", String.valueOf(vaultId)));
        VaultHolder holder = new VaultHolder(player.getUniqueId(), vaultId);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.inventory = inv;
        loadVault(player.getUniqueId(), vaultId, inv);
        player.openInventory(inv);
    }

    private void refill(Player player, ConfigurationSection section) {
        List<ItemStack> templates = buildRefillPotions(section);
        if (templates.isEmpty()) {
            plugin.msg(player, "&cRefill mal configure dans config.yml.");
            return;
        }
        int filled = 0;
        int templateIndex = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack slot = contents[i];
            if (slot != null && slot.getType() != Material.AIR) {
                continue;
            }
            ItemStack potion = templates.get(templateIndex % templates.size()).clone();
            player.getInventory().setItem(i, potion);
            filled++;
            templateIndex++;
        }
        String msg = plugin.getConfig().getString("grade-commands.messages.refill",
                "&a%count% slot(s) rempli(s) avec des potions.");
        plugin.msg(player, msg.replace("%count%", String.valueOf(filled)));
    }

    private List<ItemStack> buildRefillPotions(ConfigurationSection section) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        List<?> entries = section.getList("potions");
        if (entries != null) {
            for (Object entry : entries) {
                ItemStack stack = potionFromConfig(entry);
                if (stack != null) {
                    out.add(stack);
                }
            }
        }
        if (out.isEmpty()) {
            ItemStack fallback = createPotion(
                    section.getString("potion-type", "INSTANT_HEAL"),
                    section.getInt("potion-level", 2),
                    section.getBoolean("splash", false)
            );
            if (fallback != null) {
                out.add(fallback);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private ItemStack potionFromConfig(Object entry) {
        if (entry instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) entry;
            Object typeObj = map.get("type");
            if (typeObj == null) {
                return null;
            }
            int level = 1;
            Object levelObj = map.get("level");
            if (levelObj instanceof Number) {
                level = ((Number) levelObj).intValue();
            }
            boolean splash = Boolean.TRUE.equals(map.get("splash"));
            return createPotion(String.valueOf(typeObj), level, splash);
        }
        return null;
    }

    private ItemStack createPotion(String typeName, int level, boolean splash) {
        try {
            PotionType type = PotionType.valueOf(typeName.toUpperCase(Locale.ROOT));
            int amplifier = Math.max(1, level) - 1;
            Potion potion = new Potion(type, amplifier);
            potion.setSplash(splash);
            return potion.toItemStack(1);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @EventHandler
    public void onVaultClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof VaultHolder)) {
            return;
        }
        VaultHolder holder = (VaultHolder) event.getInventory().getHolder();
        saveVault(holder.owner, holder.vaultId, event.getInventory());
    }

    private void loadVault(UUID uuid, int vaultId, Inventory inv) {
        String key = vaultKey(vaultId);
        List<String> stored = plugin.data().getList(uuid, key);
        if (stored.isEmpty() && vaultId == 1) {
            List<String> legacy = plugin.data().getList(uuid, "pv_vault");
            if (!legacy.isEmpty()) {
                stored = legacy;
                plugin.data().setList(uuid, key, legacy);
            }
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        for (int i = 0; i < stored.size() && i < inv.getSize(); i++) {
            String raw = stored.get(i);
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            ItemStack item = Items.fromBase64(raw);
            if (item != null) {
                inv.setItem(i, item);
            }
        }
    }

    private void saveVault(UUID uuid, int vaultId, Inventory inv) {
        List<String> stored = new ArrayList<String>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null) {
                stored.add(Items.toBase64(stack));
            } else {
                stored.add("");
            }
        }
        plugin.data().setList(uuid, vaultKey(vaultId), stored);
    }

    private String vaultKey(int vaultId) {
        return "pv_vault_" + vaultId;
    }

    private String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static class VaultHolder implements InventoryHolder {
        private final UUID owner;
        private final int vaultId;
        private Inventory inventory;

        private VaultHolder(UUID owner, int vaultId) {
            this.owner = owner;
            this.vaultId = vaultId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
