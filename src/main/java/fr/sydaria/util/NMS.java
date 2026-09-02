package fr.sydaria.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

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

    private static String json(String text) {
        String colored = CC.color(text == null ? "" : text);
        return "{\"text\":\"" + colored.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
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