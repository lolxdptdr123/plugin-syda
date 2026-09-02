package fr.sydaria.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Titles 1.8.8 via NMS (PacketPlayOutTitle). L'actionbar passe par Spigot API.
 */
public final class NmsTitles {
    private NmsTitles() {}

    public static void send(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = handle.getClass().getField("playerConnection").get(handle);
            Class<?> packetClass = nms("PacketPlayOutTitle");
            Class<?> enumAction = nms("PacketPlayOutTitle$EnumTitleAction");
            Class<?> iChat = nms("IChatBaseComponent");
            Class<?> serializer = nms("IChatBaseComponent$ChatSerializer");
            Method a = serializer.getMethod("a", String.class);
            Object titleComp = a.invoke(null, "{\"text\":\"" + jsonEscape(title) + "\"}");
            Object subComp = a.invoke(null, "{\"text\":\"" + jsonEscape(subtitle) + "\"}");
            Constructor<?> times = packetClass.getConstructor(int.class, int.class, int.class);
            Object timesPacket = times.newInstance(fadeIn, stay, fadeOut);
            Constructor<?> titlePacket = packetClass.getConstructor(enumAction, iChat);
            Object tPacket = titlePacket.newInstance(enumValue(enumAction, "TITLE"), titleComp);
            Object sPacket = titlePacket.newInstance(enumValue(enumAction, "SUBTITLE"), subComp);
            Method send = connection.getClass().getMethod("sendPacket", nms("Packet"));
            send.invoke(connection, timesPacket);
            send.invoke(connection, tPacket);
            send.invoke(connection, sPacket);
        } catch (Exception ignored) {
            player.sendMessage(title + (subtitle.isEmpty() ? "" : " " + subtitle));
        }
    }

    private static Object enumValue(Class<?> type, String name) {
        for (Object c : type.getEnumConstants()) {
            if (c.toString().equals(name)) return c;
        }
        return type.getEnumConstants()[0];
    }

    private static Class<?> nms(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server.v1_8_R3." + name);
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\").replace("\n", " ");
    }
}
