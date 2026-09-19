package fr.draftmc.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class NMS {
    private static final String VERSION = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

    private NMS() {}

    public static void actionBar(Player player, String message) {
        try {
            Class<?> chatClass = nms("IChatBaseComponent");
            Class<?> serializer = nms("IChatBaseComponent$ChatSerializer");
            Class<?> packetClass = nms("PacketPlayOutChat");

            Method a = serializer.getMethod("a", String.class);
            Object component = a.invoke(null, json(message));

            // byte 2 = action bar (PacketPlayOutChat en v1_8_R3)
            Constructor<?> ctor = packetClass.getConstructor(chatClass, byte.class);
            Object packet = ctor.newInstance(component, (byte) 2);

            sendPacket(player, packet);
        } catch (Throwable t) {
            player.sendMessage(CC.color(message));
        }
    }

    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Class<?> packetClass = nms("PacketPlayOutTitle");
            Class<?> enumClass = nms("PacketPlayOutTitle$EnumTitleAction");
            Class<?> chatClass = nms("IChatBaseComponent");
            Class<?> serializer = nms("IChatBaseComponent$ChatSerializer");
            Method a = serializer.getMethod("a", String.class);
            Object titleComp = a.invoke(null, json(title));
            Object subComp = a.invoke(null, json(subtitle));

            Constructor<?> timesCtor = packetClass.getConstructor(int.class, int.class, int.class);
            Constructor<?> textCtor = packetClass.getConstructor(enumClass, chatClass);

            Object times = timesCtor.newInstance(fadeIn, stay, fadeOut);
            Object titlePacket = textCtor.newInstance(enumValue(enumClass, "TITLE"), titleComp);
            Object subPacket = textCtor.newInstance(enumValue(enumClass, "SUBTITLE"), subComp);

            sendPacket(player, times);
            sendPacket(player, titlePacket);
            sendPacket(player, subPacket);
        } catch (Throwable t) {
            player.sendMessage(CC.color(title + " " + subtitle));
        }
    }

    private static Object enumValue(Class<?> enumClass, String name) {
        for (Object constant : enumClass.getEnumConstants()) {
            if (constant.toString().equals(name)) {
                return constant;
            }
        }
        return enumClass.getEnumConstants()[0];
    }

    public static void tabHeaderFooter(Player player, String header, String footer) {
        try {
            Class<?> chatClass = nms("IChatBaseComponent");
            Class<?> serializer = nms("IChatBaseComponent$ChatSerializer");
            Method a = serializer.getMethod("a", String.class);
            Object headerComp = a.invoke(null, json(header));
            Object footerComp = a.invoke(null, json(footer));
            Class<?> packetClass = nms("PacketPlayOutPlayerListHeaderFooter");
            Object packet = packetClass.getConstructor().newInstance();
            java.lang.reflect.Field headerField = field(packetClass, "a", "header");
            java.lang.reflect.Field footerField = field(packetClass, "b", "footer");
            headerField.setAccessible(true);
            footerField.setAccessible(true);
            headerField.set(packet, headerComp);
            footerField.set(packet, footerComp);
            sendPacket(player, packet);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Nom affiche dans le tab (1.8) : si un display name est envoye, le client
     * ne concatene plus prefixe/suffixe d'equipe dans la liste. Le nametag
     * au-dessus de la tete continue d'utiliser l'equipe.
     */
    public static void setTabDisplayName(Player player, String name) {
        if (player == null) {
            return;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Class<?> packetClass = nms("PacketPlayOutPlayerInfo");
            Class<?> actionClass = inner(packetClass, "EnumPlayerInfoAction");
            Object action = enumValue(actionClass, "UPDATE_DISPLAY_NAME");
            Object packet = packetClass.getConstructor().newInstance();
            Field actionField = field(packetClass, "a", "action");
            actionField.setAccessible(true);
            actionField.set(packet, action);

            Class<?> serializer = nms("IChatBaseComponent$ChatSerializer");
            Object component = serializer.getMethod("a", String.class).invoke(null, json(name));
            Object profile = handle.getClass().getMethod("getProfile").invoke(handle);
            int ping = handle.getClass().getField("ping").getInt(handle);
            Object gamemode = tabGameMode(handle);

            Class<?> dataClass = inner(packetClass, "PlayerInfoData");
            Object data = newPlayerInfoData(dataClass, packet, profile, ping, gamemode, component);
            List<Object> entries = new ArrayList<Object>();
            entries.add(data);
            Field listField = field(packetClass, "b", "b");
            listField.setAccessible(true);
            listField.set(packet, entries);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                sendPacket(viewer, packet);
            }
        } catch (Throwable ignored) {
            String plain = org.bukkit.ChatColor.stripColor(CC.color(name == null ? "" : name));
            if (plain.length() > 16) {
                plain = plain.substring(0, 16);
            }
            try {
                player.setPlayerListName(plain);
            } catch (Throwable ignored2) {
            }
        }
    }

    private static Object newPlayerInfoData(Class<?> dataClass, Object packet, Object profile,
            int ping, Object gamemode, Object component) throws Exception {
        Constructor<?>[] ctors = dataClass.getDeclaredConstructors();
        for (int i = 0; i < ctors.length; i++) {
            Constructor<?> ctor = ctors[i];
            Class<?>[] types = ctor.getParameterTypes();
            ctor.setAccessible(true);
            if (types.length == 5) {
                try {
                    return ctor.newInstance(packet, profile, Integer.valueOf(ping), gamemode, component);
                } catch (Throwable ignored) {
                }
                try {
                    return ctor.newInstance(profile, Integer.valueOf(ping), gamemode, component, packet);
                } catch (Throwable ignored) {
                }
            }
        }
        throw new IllegalStateException("PlayerInfoData");
    }

    private static Object tabGameMode(Object handle) throws Exception {
        try {
            Object manager = handle.getClass().getField("playerInteractManager").get(handle);
            return manager.getClass().getMethod("getGameMode").invoke(manager);
        } catch (Throwable ignored) {
        }
        Class<?> enumClass;
        try {
            enumClass = nms("WorldSettings$EnumGamemode");
        } catch (ClassNotFoundException e) {
            enumClass = nms("EnumGamemode");
        }
        return enumValue(enumClass, "SURVIVAL");
    }

    private static Class<?> inner(Class<?> outer, String simple) throws ClassNotFoundException {
        Class<?>[] nested = outer.getDeclaredClasses();
        for (int i = 0; i < nested.length; i++) {
            if (nested[i].getSimpleName().equals(simple)) {
                return nested[i];
            }
        }
        return Class.forName(outer.getName() + "$" + simple);
    }

    private static java.lang.reflect.Field field(Class<?> type, String primary, String fallback) throws NoSuchFieldException {
        try {
            return type.getDeclaredField(primary);
        } catch (NoSuchFieldException e) {
            return type.getDeclaredField(fallback);
        }
    }

    public static void openAnvil(Player player) {
        if (player == null) {
            return;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object world = handle.getClass().getField("world").get(handle);
            Object inventory = handle.getClass().getField("inventory").get(handle);
            org.bukkit.Location loc = player.getLocation();
            Class<?> blockPosClass = nms("BlockPosition");
            Object pos = blockPosClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            Class<?> containerClass = nms("ContainerAnvil");
            Object container = containerClass.getConstructor(
                    nms("PlayerInventory"), nms("World"), blockPosClass, nms("EntityHuman"))
                    .newInstance(inventory, world, pos, handle);
            Field reachable = field(nms("Container"), "checkReachable", "checkReachable");
            reachable.setAccessible(true);
            reachable.setBoolean(container, false);

            int id = ((Integer) handle.getClass().getMethod("nextContainerCounter").invoke(handle)).intValue();
            Field windowId = field(nms("Container"), "windowId", "windowId");
            windowId.setAccessible(true);
            windowId.setInt(container, id);

            Class<?> chatClass = nms("IChatBaseComponent");
            Class<?> serializer = nms("IChatBaseComponent$ChatSerializer");
            Object title = serializer.getMethod("a", String.class).invoke(null, json("&8Enclume"));
            Class<?> packetClass = nms("PacketPlayOutOpenWindow");
            Object packet;
            try {
                packet = packetClass.getConstructor(int.class, String.class, chatClass, int.class)
                        .newInstance(Integer.valueOf(id), "minecraft:anvil", title, Integer.valueOf(0));
            } catch (NoSuchMethodException e) {
                packet = packetClass.getConstructor(int.class, String.class, chatClass)
                        .newInstance(Integer.valueOf(id), "minecraft:anvil", title);
            }
            sendPacket(player, packet);
            handle.getClass().getField("activeContainer").set(handle, container);
            containerClass.getMethod("addSlotListener", nms("ICrafting")).invoke(container, handle);
        } catch (Throwable ignored) {
            player.openInventory(Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.ANVIL,
                    CC.color("&8Enclume")));
        }
    }

    /**
     * 1.8 EntityItem.owner : seul ce pseudo peut ramasser l'item
     * (tant que l'item n'est pas en fin de despawn).
     */
    public static void setDroppedItemOwner(org.bukkit.entity.Item item, String playerName) {
        if (item == null || playerName == null || playerName.isEmpty()) {
            return;
        }
        try {
            Object handle = item.getClass().getMethod("getHandle").invoke(item);
            if (setStringField(handle, "owner", playerName)) {
                return;
            }
            Class<?> type = handle.getClass();
            while (type != null && type != Object.class) {
                Field[] fields = type.getDeclaredFields();
                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    if (field.getType() != String.class) {
                        continue;
                    }
                    String name = field.getName();
                    if ("thrower".equals(name)) {
                        continue;
                    }
                    if ("c".equals(name) || "f".equals(name)) {
                        field.setAccessible(true);
                        field.set(handle, playerName);
                    }
                }
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean setStringField(Object handle, String fieldName, String value) {
        Class<?> type = handle.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                if (field.getType() == String.class) {
                    field.setAccessible(true);
                    field.set(handle, value);
                    return true;
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
                return false;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static String json(String text) {
        String colored = CC.color(text == null ? "" : text);
        return "{\"text\":\"" + colored.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
    }

    private static void sendPacket(Player player, Object packet) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object connection = handle.getClass().getField("playerConnection").get(handle);
        Class<?> packetClass = nms("Packet");
        connection.getClass().getMethod("sendPacket", packetClass).invoke(connection, packet);
    }

    private static Class<?> nms(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + VERSION + "." + name);
    }
}