package fr.sydaria.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Cooldowns {

    private static final Map<String, Long> MAP =
            new ConcurrentHashMap<String, Long>();

    /*
     * Nom du tag NBT contenant le SID.
     *
     * Exemple :
     * SydariaSID = EXCALIBUR
     *
     * Il n'est PAS affiché dans le lore.
     */
    private static final String SID_KEY = "SydariaSID";

    private Cooldowns() {
    }

    public static boolean ready(Player player, String key, int seconds) {
        String id = player.getUniqueId() + ":" + key;

        Long until = MAP.get(id);

        long now = System.currentTimeMillis();

        if (until != null && until > now) {
            return false;
        }

        MAP.put(id, now + seconds * 1000L);

        return true;
    }

    public static int remaining(Player player, String key) {
        Long until =
                MAP.get(player.getUniqueId() + ":" + key);

        if (until == null) {
            return 0;
        }

        long left =
                until - System.currentTimeMillis();

        return left <= 0
                ? 0
                : (int) Math.ceil(left / 1000.0);
    }

    /**
     * Récupère le SID depuis le NBT.
     *
     * Aucun SID n'est affiché dans le lore.
     */
    public static String sid(ItemStack item) {

        if (item == null) {
            return null;
        }

        try {

            Class<?> craftItemStackClass =
                    Class.forName(
                            "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
                    );

            Method asNMSCopy =
                    craftItemStackClass.getMethod(
                            "asNMSCopy",
                            ItemStack.class
                    );

            Object nmsItem =
                    asNMSCopy.invoke(null, item);

            if (nmsItem == null) {
                return null;
            }

            Method getTag =
                    nmsItem.getClass().getMethod("getTag");

            Object nbt =
                    getTag.invoke(nmsItem);

            if (nbt == null) {
                return null;
            }

            Method hasKey =
                    nbt.getClass().getMethod(
                            "hasKey",
                            String.class
                    );

            boolean exists =
                    (Boolean) hasKey.invoke(
                            nbt,
                            SID_KEY
                    );

            if (!exists) {
                return null;
            }

            Method getString =
                    nbt.getClass().getMethod(
                            "getString",
                            String.class
                    );

            return (String) getString.invoke(
                    nbt,
                    SID_KEY
            );

        } catch (Exception e) {

            /*
             * Si l'item n'a pas de SID ou si le serveur
             * n'est pas en v1_8_R3, on retourne null.
             */
            return null;
        }
    }

    /**
     * Ajoute le SID dans le NBT.
     *
     * IMPORTANT :
     * Cette méthode retourne le nouvel ItemStack.
     */
    public static ItemStack tagSid(
            ItemStack item,
            String sid
    ) {

        if (item == null || sid == null || sid.isEmpty()) {
            return item;
        }

        try {

            Class<?> craftItemStackClass =
                    Class.forName(
                            "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
                    );

            /*
             * Bukkit -> NMS
             */
            Method asNMSCopy =
                    craftItemStackClass.getMethod(
                            "asNMSCopy",
                            ItemStack.class
                    );

            Object nmsItem =
                    asNMSCopy.invoke(
                            null,
                            item
                    );

            if (nmsItem == null) {
                return item;
            }

            /*
             * Récupération du NBTTagCompound
             */
            Method getTag =
                    nmsItem.getClass().getMethod(
                            "getTag"
                    );

            Object nbt =
                    getTag.invoke(nmsItem);

            /*
             * Création du NBTTagCompound s'il n'existe pas.
             */
            if (nbt == null) {

                Class<?> nbtClass =
                        Class.forName(
                                "net.minecraft.server.v1_8_R3.NBTTagCompound"
                        );

                nbt =
                        nbtClass.newInstance();

                Method setTag =
                        nmsItem.getClass().getMethod(
                                "setTag",
                                nbtClass
                        );

                setTag.invoke(
                        nmsItem,
                        nbt
                );
            }

            /*
             * NBT :
             *
             * SydariaSID = EXCALIBUR
             */
            Method setString =
                    nbt.getClass().getMethod(
                            "setString",
                            String.class,
                            String.class
                    );

            setString.invoke(
                    nbt,
                    SID_KEY,
                    sid
            );

            /*
             * NMS -> Bukkit
             */
            Method asBukkitCopy =
                    craftItemStackClass.getMethod(
                            "asBukkitCopy",
                            nmsItem.getClass()
                    );

            return (ItemStack) asBukkitCopy.invoke(
                    null,
                    nmsItem
            );

        } catch (Exception e) {

            e.printStackTrace();

            /*
             * En cas d'erreur, on retourne l'item original.
             */
            return item;
        }
    }

    public static int getRepairs(ItemStack item) {

        if (item == null ||
                !item.hasItemMeta() ||
                !item.getItemMeta().hasLore()) {

            return 0;
        }

        for (String line :
                item.getItemMeta().getLore()) {

            String s =
                    CC.strip(line);

            if (s.startsWith("Réparations:")) {

                try {

                    String[] parts =
                            s.replace(
                                            "Réparations:",
                                            ""
                                    )
                                    .trim()
                                    .split("/");

                    return Integer.parseInt(
                            parts[0].trim()
                    );

                } catch (Exception ignored) {
                    return 0;
                }
            }
        }

        return 0;
    }

    public static void setRepairs(
            ItemStack item,
            int used,
            int max
    ) {

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return;
        }

        List<String> lore =
                meta.hasLore()
                        ? new ArrayList<String>(
                        meta.getLore()
                )
                        : new ArrayList<String>();

        /*
         * Supprime l'ancien compteur.
         */
        for (int i = lore.size() - 1; i >= 0; i--) {

            if (CC.strip(lore.get(i))
                    .startsWith("Réparations:")) {

                lore.remove(i);
            }
        }

        lore.add(
                CC.color(
                        "&8Réparations: "
                                + used
                                + "/"
                                + max
                )
        );

        meta.setLore(lore);

        item.setItemMeta(meta);
    }

    public static UUID uuid(String raw) {

        try {
            return UUID.fromString(raw);

        } catch (Exception e) {
            return null;
        }
    }
}