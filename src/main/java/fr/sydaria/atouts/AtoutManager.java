package fr.sydaria.atouts;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AtoutManager implements Listener, CommandExecutor {
    public static final String TITLE = CC.color("&8Atouts");

    /**
     * Durée "permanente" : Minecraft (client vanilla, y compris 1.8.9) affiche l'icône
     * d'effet avec le symbole infini (∞) quand la durée restante vaut Integer.MAX_VALUE,
     * au lieu d'un décompte. On l'applique UNE SEULE FOIS par (ré)activation, on ne la
     * réapplique jamais en boucle : c'est ce qui provoquait le décompte qui repartait
     * sans cesse à 4s toutes les 2s.
     */
    private static final int PERMANENT_DURATION_TICKS = Integer.MAX_VALUE;

    /** Filet de sécurité peu fréquent : couvre les cas où le client vanilla purge tous
     *  les effets sans notifier explicitement le plugin (ex: seau de lait). Le respawn
     *  et la connexion sont eux gérés par événement dédié, donc ce filet n'a pas besoin
     *  d'être agressif. */
    private static final long SAFETY_NET_PERIOD_TICKS = 20L * 60 * 5; // 5 minutes

    /** NO_DEBUFF doit neutraliser des debuffs appliqués par des sources externes
     *  (mobs, sorts d'autres joueurs) à tout moment : un polling court reste justifié
     *  ici, mais il ne doit pas être couplé à la logique des buffs permanents. */
    private static final long DEBUFF_WATCH_PERIOD_TICKS = 20L; // 1 seconde

    /** Association atout -> (type d'effet, amplificateur par défaut en config). */
    private final Map<String, PotionEffectType> managedEffects = new LinkedHashMap<String, PotionEffectType>();

    private final Sydaria plugin;

    public AtoutManager(Sydaria plugin) {
        this.plugin = plugin;
        managedEffects.put("SPEED", PotionEffectType.SPEED);
        managedEffects.put("INCREASE_DAMAGE", PotionEffectType.INCREASE_DAMAGE);
        managedEffects.put("FIRE_RESISTANCE", PotionEffectType.FIRE_RESISTANCE);
        managedEffects.put("FAST_DIGGING", PotionEffectType.FAST_DIGGING);

        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { safetyNet(); }
        }, SAFETY_NET_PERIOD_TICKS, SAFETY_NET_PERIOD_TICKS);

        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { watchDebuffs(); }
        }, DEBUFF_WATCH_PERIOD_TICKS, DEBUFF_WATCH_PERIOD_TICKS);
    }

    /** Filet de sécurité : ne réapplique QUE ce qui manque réellement, donc ne provoque
     *  jamais de reset visuel pour un effet déjà actif. */
    private void safetyNet() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPermanentEffects(player, plugin.data().getList(player.getUniqueId(), "atouts"));
        }
    }

    private void watchDebuffs() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.data().getList(player.getUniqueId(), "atouts").contains("NO_DEBUFF")) continue;
            for (PotionEffect effect : new ArrayList<PotionEffect>(player.getActivePotionEffects())) {
                if (isDebuff(effect.getType())) player.removePotionEffect(effect.getType());
            }
        }
    }

    private boolean isDebuff(PotionEffectType type) {
        return type.equals(PotionEffectType.SLOW) || type.equals(PotionEffectType.WEAKNESS)
                || type.equals(PotionEffectType.POISON) || type.equals(PotionEffectType.WITHER)
                || type.equals(PotionEffectType.BLINDNESS) || type.equals(PotionEffectType.CONFUSION)
                || type.equals(PotionEffectType.HUNGER) || type.equals(PotionEffectType.SLOW_DIGGING);
    }

    /**
     * Aligne les effets de potion réellement actifs sur la liste d'atouts activés :
     * applique en durée permanente ceux qui manquent (ou dont l'amplificateur a changé),
     * retire ceux qui ne devraient plus être actifs. Idempotent : appeler cette méthode
     * plusieurs fois de suite sans changement de state n'a aucun effet visible.
     */
    private void syncPermanentEffects(Player player, List<String> atouts) {
        for (Map.Entry<String, PotionEffectType> entry : managedEffects.entrySet()) {
            String atoutId = entry.getKey();
            PotionEffectType type = entry.getValue();
            boolean shouldBeActive = atouts.contains(atoutId);
            PotionEffect current = findActiveEffect(player, type);

            if (!shouldBeActive) {
                if (current != null) player.removePotionEffect(type);
                continue;
            }

            int amplifier = "FIRE_RESISTANCE".equals(atoutId) ? 0
                    : plugin.getConfig().getInt("atouts.effects." + atoutId + ".amplifier", 0);

            // Ne réapplique QUE si absent ou si le niveau configuré a changé : c'est ce
            // qui évite le reset du décompte visuel observé avec l'ancienne version.
            if (current == null || current.getAmplifier() != amplifier) {
                player.addPotionEffect(new PotionEffect(type, PERMANENT_DURATION_TICKS, amplifier, true, false), true);
            }
        }
    }

    /**
     * LivingEntity#getPotionEffect(PotionEffectType) n'existe pas dans l'API Spigot
     * 1.8.9 (ajouté seulement dans des versions ultérieures) : on ne dispose que de
     * getActivePotionEffects(), qu'il faut donc parcourir manuellement.
     */
    private PotionEffect findActiveEffect(Player player, PotionEffectType type) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(type)) return effect;
        }
        return null;
    }

    /** /atouts ne fait plus aucun achat : il sert uniquement à activer/désactiver les atouts déjà possédés. */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        String[] ids = {"SPEED","INCREASE_DAMAGE","FIRE_RESISTANCE","FAST_DIGGING","ANTI_CHUTE","NO_HUNGER","NO_DEBUFF","KEEP_XP"};
        int[] slots = {10,11,12,13,14,15,16,22};
        org.bukkit.Material[] mats = {
                org.bukkit.Material.SUGAR, org.bukkit.Material.BLAZE_POWDER, org.bukkit.Material.MAGMA_CREAM,
                org.bukkit.Material.GOLD_PICKAXE, org.bukkit.Material.FEATHER, org.bukkit.Material.COOKED_BEEF,
                org.bukkit.Material.MILK_BUCKET, org.bukkit.Material.EXP_BOTTLE
        };
        for (int i = 0; i < ids.length; i++) inv.setItem(slots[i], displayAtout(player, ids[i], mats[i]));
        player.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack displayAtout(Player player, String id, org.bukkit.Material material) {
        boolean owned = plugin.data().getList(player.getUniqueId(), "atouts_owned").contains(id);
        String display = plugin.getConfig().getString("atouts.effects." + id + ".display",
                plugin.getConfig().getString("atouts.flags." + id, id));
        java.util.List<String> lore = new ArrayList<String>();
        boolean active = plugin.data().getList(player.getUniqueId(), "atouts").contains(id);
        lore.add("&7Statut: " + (!owned ? "&cNON POSSEDE" : (active ? "&aACTIF" : "&eDESACTIVE")));
        if (owned) lore.add("&eClique pour " + (active ? "désactiver" : "activer"));
        else lore.add("&8Achète cet atout dans &e/boutique");
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(CC.color(display));
        meta.setLore(CC.color(lore));
        item.setItemMeta(meta);
        return item;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) open((Player) sender);
        return true;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();
        String[] ids = {"SPEED","INCREASE_DAMAGE","FIRE_RESISTANCE","FAST_DIGGING","ANTI_CHUTE","NO_HUNGER","NO_DEBUFF","KEEP_XP"};
        int[] slots = {10,11,12,13,14,15,16,22};
        String id = null;
        for (int i=0;i<slots.length;i++) if (slot == slots[i]) id = ids[i];
        if (id == null) return;
        Player player=(Player)event.getWhoClicked();
        List<String> list=plugin.data().getList(player.getUniqueId(),"atouts");
        if (!plugin.data().getList(player.getUniqueId(), "atouts_owned").contains(id)) {
            plugin.msg(player,"&cTu ne possèdes pas cet atout. &7Achète-le dans &e/boutique&7.");
            return;
        }
        if (list.contains(id)) {
            list.remove(id);
            plugin.msg(player,"&cAtout désactivé.");
        } else {
            list.add(id);
            plugin.msg(player,"&aAtout activé.");
        }
        plugin.data().setList(player.getUniqueId(),"atouts",list);
        syncPermanentEffects(player, list);
        open(player);
    }

    /** Le respawn vanilla purge tous les effets de potion : sans ce handler, un joueur
     *  qui meurt perdrait Speed/Force/Haste/FireRes jusqu'au prochain passage du filet
     *  de sécurité (jusqu'à 5 minutes). */
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                syncPermanentEffects(player, plugin.data().getList(player.getUniqueId(), "atouts"));
            }
        });
    }

    /** Restaure les atouts permanents à la connexion (les effets de potion ne
     *  persistent pas entre deux sessions côté client/serveur). */
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                syncPermanentEffects(player, plugin.data().getList(player.getUniqueId(), "atouts"));
            }
        });
    }

    @EventHandler public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getCause()!=EntityDamageEvent.DamageCause.FALL) return;
        if (plugin.data().getList(((Player)event.getEntity()).getUniqueId(),"atouts").contains("ANTI_CHUTE")) event.setCancelled(true);
    }
    @EventHandler public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player=(Player)event.getEntity();
        if (plugin.data().getList(player.getUniqueId(),"atouts").contains("NO_HUNGER")) {
            event.setCancelled(true); player.setFoodLevel(20);
        }
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player player=event.getEntity();
        if (plugin.data().getList(player.getUniqueId(),"atouts").contains("KEEP_XP")) {
            event.setKeepLevel(true); event.setDroppedExp(0);
        }
    }
}