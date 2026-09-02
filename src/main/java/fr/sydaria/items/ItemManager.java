package fr.sydaria.items;

import fr.sydaria.Sydaria;
import fr.sydaria.util.Cooldowns;
import fr.sydaria.util.ItemBuilder;
import fr.sydaria.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ItemManager implements Listener, CommandExecutor {

    private final Sydaria plugin;

    private static final String KEEP_ITEMS_KEY = "pending-keep-items";

    public ItemManager(Sydaria plugin) {
        this.plugin = plugin;

        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    tickHeld(player);
                }
            }
        }, 40L, 40L);
    }

    /*
     * Gestion des items à conserver à la mort.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {

        Player victim = event.getEntity();

        List<ItemStack> kept = new ArrayList<ItemStack>();

        Iterator<ItemStack> it = event.getDrops().iterator();

        while (it.hasNext()) {

            ItemStack stack = it.next();

            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            /*
             * Le SID est maintenant récupéré depuis le NBT.
             */
            String id = Cooldowns.sid(stack);

            if (id == null) {
                continue;
            }

            /*
             * Item configuré avec :
             *
             * keep-on-death: true
             */
            if (isKeepOnDeath(id)) {

                kept.add(stack.clone());

                /*
                 * On retire l'item des drops.
                 * Il sera rendu au respawn.
                 */
                it.remove();

            } else if (isRemoveOnDeath(id)) {

                /*
                 * Item qui doit disparaître à la mort.
                 */
                it.remove();
            }
        }

        /*
         * Sauvegarde des items à rendre au respawn.
         */
        if (!kept.isEmpty()) {

            List<String> serialized = new ArrayList<String>();

            for (ItemStack stack : kept) {
                serialized.add(Items.toBase64(stack));
            }

            plugin.data().setList(
                    victim.getUniqueId(),
                    KEEP_ITEMS_KEY,
                    serialized
            );
        }
    }

    /*
     * Rend les items après le respawn.
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {

        final Player player = event.getPlayer();

        final List<String> serialized =
                plugin.data().getList(
                        player.getUniqueId(),
                        KEEP_ITEMS_KEY
                );

        if (serialized.isEmpty()) {
            return;
        }

        /*
         * On vide la liste avant de rendre les items
         * pour éviter un double give.
         */
        plugin.data().setList(
                player.getUniqueId(),
                KEEP_ITEMS_KEY,
                new ArrayList<String>()
        );

        /*
         * Un tick plus tard pour être sûr que
         * l'inventaire est disponible.
         */
        Bukkit.getScheduler().runTask(plugin, new Runnable() {

            @Override
            public void run() {

                for (String data : serialized) {

                    ItemStack item = Items.fromBase64(data);

                    if (item == null) {
                        continue;
                    }

                    Map<Integer, ItemStack> overflow =
                            player.getInventory().addItem(item);

                    /*
                     * Si l'inventaire est plein,
                     * l'item tombe au sol.
                     */
                    for (ItemStack extra : overflow.values()) {
                        player.getWorld().dropItemNaturally(
                                player.getLocation(),
                                extra
                        );
                    }
                }
            }
        });
    }

    private boolean isKeepOnDeath(String id) {

        ConfigurationSection sec =
                plugin.getItemsConfig()
                        .getConfigurationSection("items." + id);

        return sec != null &&
                sec.getBoolean("keep-on-death", false);
    }

    private boolean isRemoveOnDeath(String id) {

        ConfigurationSection sec =
                plugin.getItemsConfig()
                        .getConfigurationSection("items." + id);

        return sec != null &&
                sec.getBoolean("remove-on-death", false);
    }

    /*
     * Empêche le drop volontaire (touche Q, ou clic en dehors de
     * l'inventaire avec l'item sur le curseur) des items configurés avec :
     *
     * no-drop: true
     *
     * Note : sur CraftBukkit 1.8.9, les deux actions passent par le même
     * chemin NMS (EntityHuman#drop) et déclenchent donc toutes les deux
     * PlayerDropItemEvent. Un seul handler suffit à couvrir les deux cas,
     * pas besoin de dupliquer la logique sur InventoryClickEvent.
     *
     * La mort du joueur est un chemin totalement différent (pas de
     * PlayerDropItemEvent) : elle reste gérée séparément par
     * keep-on-death / remove-on-death dans onDeath().
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {

        String id = Cooldowns.sid(
                event.getItemDrop().getItemStack()
        );

        if (id == null || !isNoDrop(id)) {
            return;
        }

        event.setCancelled(true);

        plugin.msg(
                event.getPlayer(),
                "&cCet item ne peut pas être jeté."
        );
    }

    private boolean isNoDrop(String id) {

        ConfigurationSection sec =
                plugin.getItemsConfig()
                        .getConfigurationSection("items." + id);

        return sec != null &&
                sec.getBoolean("no-drop", false);
    }

    /*
     * Effets des items.
     */
    private void tickHeld(Player player) {

        if (has(player, "SPEED_BOOTS")) {

            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.SPEED,
                            60,
                            1,
                            true,
                            false
                    ),
                    true
            );
        }

        if (has(player, "NV_HELMET")) {

            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.NIGHT_VISION,
                            400,
                            0,
                            true,
                            false
                    ),
                    true
            );
        }

        if (has(player, "REGEN_AMULET") ||
                has(player, "REGEN_ITEM")) {

            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.REGENERATION,
                            60,
                            0,
                            true,
                            false
                    ),
                    true
            );
        }
    }

    /*
     * Vérifie si le joueur possède un item
     * avec le SID demandé.
     */
    private boolean has(Player player, String sid) {

        for (ItemStack stack :
                player.getInventory().getContents()) {

            if (sid.equals(Cooldowns.sid(stack))) {
                return true;
            }
        }

        for (ItemStack stack :
                player.getInventory().getArmorContents()) {

            if (sid.equals(Cooldowns.sid(stack))) {
                return true;
            }
        }

        return false;
    }

    /*
     * Création des items custom.
     */
    public ItemStack create(String id) {

        ConfigurationSection sec =
                plugin.getItemsConfig()
                        .getConfigurationSection("items." + id);

        if (sec == null) {
            return null;
        }

        Material mat =
                Material.matchMaterial(
                        sec.getString("material", "STONE")
                );

        if (mat == null) {
            mat = Material.STONE;
        }

        short data =
                (short) sec.getInt("data", 0);

        ItemBuilder b =
                new ItemBuilder(mat, 1, data)
                        .name(sec.getString("name", id));

        List<String> lore =
                sec.getStringList("lore");

        if (lore != null) {
            b.lore(lore);
        }

        ItemStack item = b.build();
        ConfigurationSection enchants =
                sec.getConfigurationSection("enchants");

        if (enchants != null) {
            for (String enchantName : enchants.getKeys(false)) {

                try {
                    org.bukkit.enchantments.Enchantment enchant =
                            org.bukkit.enchantments.Enchantment.getByName(
                                    enchantName.toUpperCase()
                            );

                    if (enchant == null) {
                        plugin.getLogger().warning(
                                "Enchantement inconnu : " + enchantName
                        );
                        continue;
                    }

                    int level =
                            enchants.getInt(enchantName, 1);

                    item.addUnsafeEnchantment(enchant, level);

                } catch (Exception e) {
                    plugin.getLogger().warning(
                            "Impossible d'ajouter l'enchantement "
                                    + enchantName
                                    + " à "
                                    + id
                    );
                }
            }
        }
        /*
         * IMPORTANT :
         *
         * Le SID est toujours ajouté,
         * mais maintenant dans le NBT.
         *
         * Il ne sera donc plus visible dans le lore.
         */
        item = Cooldowns.tagSid(item, id);

        return item;
    }

    public void give(Player player, String id) {

        ItemStack item = create(id);

        if (item != null) {
            player.getInventory().addItem(item);
        }
    }

    public Set<String> ids() {

        ConfigurationSection sec =
                plugin.getItemsConfig()
                        .getConfigurationSection("items");

        return sec == null
                ? new java.util.HashSet<String>()
                : sec.getKeys(false);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (!sender.hasPermission("sydaria.items.give")) {
            return true;
        }

        if (args.length < 1) {

            plugin.msg(
                    sender,
                    "&e/itemsyd <id> [joueur]"
            );

            plugin.msg(
                    sender,
                    "&7" + ids()
            );

            return true;
        }

        Player target =
                args.length >= 2
                        ? Bukkit.getPlayer(args[1])
                        : sender instanceof Player
                          ? (Player) sender
                          : null;

        if (target == null) {

            plugin.msg(
                    sender,
                    "&cJoueur introuvable."
            );

            return true;
        }

        ItemStack item =
                create(args[0].toUpperCase());

        if (item == null) {

            plugin.msg(
                    sender,
                    "&cItem inconnu."
            );

            return true;
        }

        target.getInventory().addItem(item);

        plugin.msg(
                sender,
                "&aItem donné."
        );

        return true;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {

        Player player = event.getPlayer();

        String sid =
                Cooldowns.sid(
                        player.getItemInHand()
                );

        if (sid == null) {
            return;
        }

        Block block = event.getBlock();

        if (sid.equals("TRIPLE_PICK")) {

            breakRelative(
                    player,
                    block.getRelative(0, 1, 0)
            );

            breakRelative(
                    player,
                    block.getRelative(0, -1, 0)
            );

        } else if (
                sid.equals("SHOVEL_3X3") &&
                        isDirt(block.getType())
        ) {

            for (int x = -1; x <= 1; x++) {

                for (int z = -1; z <= 1; z++) {

                    if (x == 0 && z == 0) {
                        continue;
                    }

                    Block b =
                            block.getRelative(
                                    x,
                                    0,
                                    z
                            );

                    if (isDirt(b.getType())) {
                        breakRelative(player, b);
                    }
                }
            }
        }
    }

    private boolean isDirt(Material m) {

        return m == Material.DIRT ||
                m == Material.GRASS ||
                m == Material.SAND ||
                m == Material.GRAVEL ||
                m == Material.SOUL_SAND ||
                m == Material.CLAY;
    }

    private void breakRelative(
            Player player,
            Block block) {

        if (
                block.getType() == Material.AIR ||
                        block.getType() == Material.BEDROCK
        ) {
            return;
        }

        block.breakNaturally(
                player.getItemInHand()
        );
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {

        if (
                event.getAction() != Action.RIGHT_CLICK_AIR &&
                        event.getAction() != Action.RIGHT_CLICK_BLOCK
        ) {
            return;
        }

        Player player = event.getPlayer();

        String sid =
                Cooldowns.sid(event.getItem());

        if (sid == null) {
            return;
        }

        if (sid.equals("DASH_FEATHER")) {

            event.setCancelled(true);

            if (!Cooldowns.ready(player, "dash", 5)) {
                return;
            }

            player.setVelocity(
                    player.getLocation()
                            .getDirection()
                            .multiply(1.8)
                            .setY(0.3)
            );

        } else if (
                sid.equals("INFINITE_WATER") &&
                        event.getClickedBlock() != null
        ) {

            event.setCancelled(true);

            event.getClickedBlock()
                    .getRelative(event.getBlockFace())
                    .setType(Material.WATER);

        } else if (
                sid.equals("INFINITE_LAVA") &&
                        event.getClickedBlock() != null
        ) {

            event.setCancelled(true);

            event.getClickedBlock()
                    .getRelative(event.getBlockFace())
                    .setType(Material.LAVA);

        } else {

            /*
             * Dispatch générique par "ability" plutôt que par SID en dur :
             * ajouter une nouvelle orbe ne demande qu'une entrée dans
             * items.yml, aucun changement de code.
             */
            ConfigurationSection sec =
                    plugin.getItemsConfig()
                            .getConfigurationSection("items." + sid);

            if (sec != null &&
                    "ORB_EFFECT".equals(sec.getString("ability"))) {

                event.setCancelled(true);
                useOrb(player, sid, sec);
            }
        }
    }

    /*
     * Orbe d'effet : réutilisable, à cooldown, effet TEMPORAIRE (à la
     * différence des atouts qui sont permanents). L'objet n'est jamais
     * retiré de l'inventaire.
     */
    private void useOrb(
            Player player,
            String sid,
            ConfigurationSection sec) {

        String effectName = sec.getString("effect", "");

        PotionEffectType type =
                PotionEffectType.getByName(effectName);

        if (type == null) {

            plugin.msg(
                    player,
                    "&cOrbe mal configurée: effet \"" + effectName + "\" inconnu."
            );

            return;
        }

        /*
         * IMPORTANT : on valide la config AVANT de consommer le cooldown.
         * Sinon un item mal configuré grillerait le cooldown du joueur
         * sans lui donner l'effet en retour.
         */
        String cooldownKey = "orb:" + sid;
        int cooldownSeconds = sec.getInt("cooldown-seconds", 60);

        if (!Cooldowns.ready(player, cooldownKey, cooldownSeconds)) {

            plugin.msg(
                    player,
                    "&cOrbe en recharge, encore &e"
                            + Cooldowns.remaining(player, cooldownKey)
                            + "s&c."
            );

            return;
        }

        int amplifier = sec.getInt("amplifier", 0);
        int durationTicks = sec.getInt("duration-seconds", 20) * 20;

        /*
         * ambient=false, particles=true : contrairement aux atouts (buff
         * permanent et discret), une orbe est une action volontaire du
         * joueur, le retour visuel est donc voulu ici.
         */
        player.addPotionEffect(
                new PotionEffect(
                        type,
                        durationTicks,
                        amplifier,
                        false,
                        true
                ),
                true
        );

        plugin.msg(
                player,
                "&aOrbe utilisée !"
        );
    }
}