package fr.draftmc.core;

import fr.draftmc.Draftmc;
import fr.draftmc.util.ActionLogs;
import fr.draftmc.util.CC;
import fr.draftmc.util.Cooldowns;
import fr.draftmc.util.ItemBuilder;
import fr.draftmc.util.Items;
import fr.draftmc.util.Locations;
import fr.draftmc.util.NMS;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CoreCommands implements CommandExecutor, Listener {
    public static final String FURNACE_TITLE = CC.color("&8Four portable");
    public static final String TRASH_TITLE = CC.color("&8Poubelle");
    private final Draftmc plugin;
    private final Map<String, Integer> blockHp = new HashMap<String, Integer>();

    public CoreCommands(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("title")) {
            return title(sender, args);
        }
        if (name.equals("actionbar")) {
            return actionbar(sender, args);
        }
        if (!(sender instanceof Player)) {
            plugin.msg(sender, "&cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (name.equals("enclume")) {
            openAnvil(player);
            return true;
        }
        if (name.equals("enchantement")) {
            player.openEnchanting(player.getLocation(), true);
            return true;
        }
        if (name.equals("bottlexp")) {
            bottle(player);
            return true;
        }
        if (name.equals("furnace")) {
            player.openInventory(Bukkit.createInventory(player, 9, FURNACE_TITLE));
            plugin.msg(player, "&7Place tes items puis referme pour les cuire.");
            return true;
        }
        if (name.equals("randomkey")) {
            if (!player.hasPermission("draftmc.randomkey")) {
                plugin.msg(player, "&cPas la permission.");
                return true;
            }
            giveRandomKey(player);
            return true;
        }
        if (name.equals("repair")) {
            repair(player);
            return true;
        }
        if (name.equals("poubelle")) {
            if (args.length >= 1 && (args[0].equalsIgnoreCase("logs") || args[0].equalsIgnoreCase("log"))) {
                if (plugin.grades() == null || !plugin.grades().isOwnerOrAdmin(player)) {
                    plugin.msg(player, "&cSeuls les Owner et les Admin peuvent voir les logs poubelle.");
                    return true;
                }
                ActionLogs.show(plugin, player, "bin-logs.yml", "Logs poubelle");
                return true;
            }
            player.openInventory(Bukkit.createInventory(player, 36, TRASH_TITLE));
            return true;
        }
        if (name.equals("vision")) {
            if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                plugin.msg(player, "&cVision désactivée.");
            } else {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
                plugin.msg(player, "&aVision activée.");
            }
            return true;
        }
        if (name.equals("b")) {
            welcome(player, args);
            return true;
        }
        return true;
    }

    private boolean title(CommandSender sender, String[] args) {
        if (!sender.hasPermission("draftmc.admin")) {
            return true;
        }
        if (args.length < 2) {
            plugin.msg(sender, "&e/title <joueur|all> <message>");
            return true;
        }
        String msg = join(args, 1);
        if (args[0].equalsIgnoreCase("all")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                NMS.title(p, msg, "", 10, 40, 10);
            }
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.msg(sender, "&cJoueur introuvable.");
            return true;
        }
        NMS.title(target, msg, "", 10, 40, 10);
        return true;
    }

    private boolean actionbar(CommandSender sender, String[] args) {
        if (!sender.hasPermission("draftmc.admin")) {
            return true;
        }
        if (args.length < 2) {
            plugin.msg(sender, "&e/actionbar <joueur|all> <message>");
            return true;
        }
        String msg = join(args, 1);
        if (args[0].equalsIgnoreCase("all")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                NMS.actionBar(p, msg);
            }
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            return true;
        }
        NMS.actionBar(target, msg);
        return true;
    }

    private void openAnvil(Player player) {
        NMS.openAnvil(player);
        plugin.msg(player, "&7Enclume portable ouverte.");
    }

    private void bottle(Player player) {
        int per = plugin.getConfig().getInt("core.bottle-xp.xp-per-bottle", 20);
        int total = player.getTotalExperience();
        if (total < per) {
            plugin.msg(player, "&cPas assez d'XP. &7(" + per + " requis)");
            return;
        }
        int bottles = total / per;
        if (bottles > 64) {
            bottles = 64;
        }
        int take = bottles * per;
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0f);
        int remain = total - take;
        player.giveExp(remain);
        ItemStack item = new ItemBuilder(Material.EXP_BOTTLE, bottles)
                .name("&aBouteille d'XP")
                .lore("&7Contient &e" + per + " XP &7chacune.")
                .build();
        player.getInventory().addItem(item);
        plugin.msg(player, "&a+" + bottles + " bouteilles d'XP.");
    }

    private void giveRandomKey(Player player) {
        List<String> keys = plugin.getConfig().getStringList("core.randomkey-keys");
        if (keys.isEmpty()) {
            return;
        }
        String key = keys.get(new java.util.Random().nextInt(keys.size()));
        ItemStack item = plugin.items().create("KEY_" + key);
        if (item == null) {
            item = new ItemBuilder(Material.TRIPWIRE_HOOK).name("&eClé " + key).build();
            Cooldowns.tagSid(item, "KEY_" + key);
        }
        player.getInventory().addItem(item);
        plugin.msg(player, "&aTu reçois une clé &e" + key);
    }

    public void giveRandomKeyPublic(Player player) {
        giveRandomKey(player);
    }

    private void repair(Player player) {
        if (!player.hasPermission("draftmc.repair")) {
            plugin.msg(player, "&cPas la permission.");
            return;
        }
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() == Material.AIR || !Locations.isTool(item)) {
            plugin.msg(player, "&cPrends un item réparable en main.");
            return;
        }
        int cd = plugin.getConfig().getInt("core.repair.cooldown-seconds", 120);
        if (!player.hasPermission("draftmc.repair.bypass") && !Cooldowns.ready(player, "repair", cd)) {
            plugin.msg(player, "&cCooldown &e" + Cooldowns.remaining(player, "repair") + "s");
            return;
        }
        int max = plugin.getConfig().getInt("core.repair.max-per-item", 3);
        int used = Cooldowns.getRepairs(item);
        if (used >= max && !player.hasPermission("draftmc.repair.bypass")) {
            plugin.msg(player, "&cCet item a atteint la limite de réparations (&e" + max + "&c).");
            return;
        }
        item.setDurability((short) 0);
        Cooldowns.setRepairs(item, used + 1, max);
        player.setItemInHand(item);
        plugin.msg(player, "&aItem réparé. &7(" + (used + 1) + "/" + max + ")");
    }

    private void welcome(Player player, String[] args) {
        if (!player.hasPermission("draftmc.welcome")) {
            return;
        }
        if (args.length < 1) {
            plugin.msg(player, "&e/b <joueur>");
            return;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || target.equals(player)) {
            plugin.msg(player, "&cJoueur invalide.");
            return;
        }
        int cd = plugin.getConfig().getInt("core.welcome.cooldown-seconds", 60);
        if (!Cooldowns.ready(player, "welcome-" + target.getUniqueId(), cd)) {
            plugin.msg(player, "&cTu as déjà souhaité la bienvenue.");
            return;
        }
        String broadcast = plugin.getConfig().getString("core.welcome.broadcast",
                "&e%player% &7souhaite la bienvenue à &a%target%&7 !");
        if (broadcast != null && !broadcast.trim().isEmpty()) {
            Bukkit.broadcastMessage(CC.color(plugin.prefix() + broadcast
                    .replace("%player%", player.getName())
                    .replace("%target%", target.getName())));
        }
        double reward = plugin.getConfig().getDouble("core.welcome.reward", 50);
        if (plugin.getConfig().getBoolean("core.welcome.use-vault", true)) {
            plugin.economy().deposit(player, reward);
            plugin.msg(player, "&a+" + plugin.economy().format(reward) + " &7(argent shop) pour l'accueil.");
        } else {
            plugin.tokens().add(player.getUniqueId(), (long) reward);
            plugin.msg(player, "&a+" + (long) reward + " tokens boutique pour l'accueil.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("join-quit.hide-join-message", true)) {
            event.setJoinMessage(null);
        }
        Items.resyncClient(plugin, event.getPlayer());
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return;
        }
        java.util.List<String> lines = plugin.getConfig().getStringList("join-quit.welcome");
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) {
                continue;
            }
            player.sendMessage(CC.color(plugin.prefix() + line.replace("%player%", player.getName())));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuitMessage(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("join-quit.hide-quit-message", false)) {
            event.setQuitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        final Player player = event.getPlayer();
        final GameMode mode = player.getGameMode();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || skipGameModeRestore(player)) {
                    return;
                }
                if (plugin.staff() != null && plugin.staff().isStaff(player)) {
                    player.setGameMode(GameMode.CREATIVE);
                    player.setAllowFlight(true);
                    player.updateInventory();
                    return;
                }
                if (player.getGameMode() != mode) {
                    player.setGameMode(mode);
                }
                player.updateInventory();
            }
        });
    }

    private boolean skipGameModeRestore(Player player) {
        if (plugin.tournament() != null && plugin.tournament().manager() != null
                && plugin.tournament().manager().isParticipant(player.getUniqueId())) {
            return true;
        }
        if (plugin.events() != null && plugin.events().teamfight() != null
                && plugin.events().teamfight().getManager().isFighter(player.getUniqueId())) {
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPreviewClick(InventoryClickEvent event) {
        if (TITLE_CLASSEMENT(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    private boolean TITLE_CLASSEMENT(String title) {
        return title != null && title.contains("Classements");
    }

    @EventHandler
    public void onTrashClose(InventoryCloseEvent event) {
        if (!TRASH_TITLE.equals(event.getView().getTitle())) {
            return;
        }
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            StringBuilder parts = new StringBuilder();
            for (ItemStack stack : event.getInventory().getContents()) {
                if (stack == null || stack.getType() == Material.AIR) {
                    continue;
                }
                if (parts.length() > 0) {
                    parts.append(", ");
                }
                parts.append(ActionLogs.itemLabel(stack));
            }
            if (parts.length() > 0) {
                ActionLogs.append(plugin, "bin-logs.yml", "bin", player.getName() + " a jeté " + parts);
                ActionLogs.notifyStaff(plugin, "&7[Bin] &e" + player.getName() + " &7a jeté &f" + parts);
            }
        }
        event.getInventory().clear();
    }

    @EventHandler
    public void onFurnaceClose(InventoryCloseEvent event) {
        if (!FURNACE_TITLE.equals(event.getView().getTitle())) {
            return;
        }
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        for (ItemStack stack : event.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            ItemStack result = smelt(stack);
            if (result != null) {
                player.getInventory().addItem(result);
            } else {
                player.getInventory().addItem(stack);
            }
        }
        event.getInventory().clear();
        plugin.msg(player, "&aCuisson terminée.");
    }

    private ItemStack smelt(ItemStack in) {
        Material t = in.getType();
        Material out = null;
        short data = 0;
        if (t == Material.IRON_ORE) out = Material.IRON_INGOT;
        else if (t == Material.GOLD_ORE) out = Material.GOLD_INGOT;
        else if (t == Material.COBBLESTONE) out = Material.STONE;
        else if (t == Material.SAND) out = Material.GLASS;
        else if (t == Material.PORK) out = Material.GRILLED_PORK;
        else if (t == Material.RAW_BEEF) out = Material.COOKED_BEEF;
        else if (t == Material.RAW_CHICKEN) out = Material.COOKED_CHICKEN;
        else if (t == Material.RAW_FISH) out = Material.COOKED_FISH;
        else if (t == Material.POTATO_ITEM) out = Material.BAKED_POTATO;
        else if (t == Material.LOG || t == Material.LOG_2) {
            out = Material.COAL;
            data = 1;
        } else if (t == Material.CACTUS) {
            out = Material.INK_SACK;
            data = 2;
        } else if (t == Material.NETHERRACK) out = Material.NETHER_BRICK_ITEM;
        else if (t == Material.CLAY_BALL) out = Material.CLAY_BRICK;
        else if (t == Material.DIAMOND_ORE) out = Material.DIAMOND;
        else if (t == Material.EMERALD_ORE) out = Material.EMERALD;
        else if (t == Material.COAL_ORE) out = Material.COAL;
        else if (t == Material.REDSTONE_ORE || t == Material.GLOWING_REDSTONE_ORE) out = Material.REDSTONE;
        else if (t == Material.LAPIS_ORE) {
            out = Material.INK_SACK;
            data = 4;
        } else if (t == Material.QUARTZ_ORE) out = Material.QUARTZ;
        if (out == null) {
            return null;
        }
        return new ItemStack(out, in.getAmount(), data);
    }

    @EventHandler
    public void onXp(PlayerExpChangeEvent event) {
        if (event.getPlayer().hasPermission("draftmc.doublexp") && event.getAmount() > 0) {
            double m = plugin.getConfig().getDouble("core.double-xp-multiplier", 2.0);
            event.setAmount((int) Math.round(event.getAmount() * m));
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("core.explosion-durability.enabled", true)) {
            return;
        }
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            int need = plugin.getConfig().getInt("core.explosion-durability.blocks." + block.getType().name(),
                    plugin.getConfig().getInt("core.explosion-durability.default-hits", 1));
            if (need <= 1) {
                continue;
            }
            String key = locKey(block);
            Integer hp = blockHp.get(key);
            if (hp == null) {
                hp = need;
            }
            hp = hp - 1;
            if (hp > 0) {
                blockHp.put(key, hp);
                it.remove();
            } else {
                blockHp.remove(key);
            }
        }
    }

    private String locKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    @EventHandler
    public void onSplash(PotionSplashEvent event) {
        List<String> noheal = plugin.getConfig().getStringList("core.noheal-blocks");
        Block hit = event.getPotion().getLocation().getBlock();
        boolean onNoheal = false;
        for (String n : noheal) {
            if (hit.getType().name().equalsIgnoreCase(n) || hit.getRelative(0, -1, 0).getType().name().equalsIgnoreCase(n)) {
                onNoheal = true;
                break;
            }
        }
        event.setCancelled(false);
        if (onNoheal) {
            return;
        }
    }

    @EventHandler
    public void onBottleDrink(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.EXP_BOTTLE) {
            return;
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains("Bouteille d'XP")) {
            event.setCancelled(true);
            int per = plugin.getConfig().getInt("core.bottle-xp.xp-per-bottle", 20);
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                event.getPlayer().setItemInHand(null);
            }
            event.getPlayer().giveExp(per);
            plugin.msg(event.getPlayer(), "&a+" + per + " XP");
        }
    }

    private String join(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
