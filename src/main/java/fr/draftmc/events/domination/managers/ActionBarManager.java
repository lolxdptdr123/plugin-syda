package fr.draftmc.events.domination.managers;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.utils.NmsUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Envoie du texte dans l'action bar (juste au-dessus de la hotbar).
 * L'API Bukkit officielle Player#sendActionBar() n'existe qu'a partir de
 * la 1.11 : sur 1.8, il faut envoyer manuellement un PacketPlayOutChat
 * avec le type "2" (action bar). Ce n'est possible qu'en NMS - voir
 * NmsUtil pour l'explication de pourquoi cette reflexion reste fiable
 * sur tous les forks 1.8.8/1.8.9 cibles par ce plugin.
 * Si la reflexion echoue pour une raison quelconque (core non standard),
 * on se replie silencieusement sur un message de chat classique plutot
 * que de faire planter le plugin.
 */
public class ActionBarManager {

    private final DominationPlugin plugin;
    private boolean supported = true;

    private Constructor<?> chatComponentTextConstructor;
    private Constructor<?> packetConstructor;
    private Method getHandleMethod;
    private Field playerConnectionField;
    private Method sendPacketMethod;

    public ActionBarManager(DominationPlugin plugin) {
        this.plugin = plugin;
        try {
            Class<?> iChatBaseComponentClass = NmsUtil.getNmsClass("IChatBaseComponent");
            Class<?> chatComponentTextClass = NmsUtil.getNmsClass("ChatComponentText");
            chatComponentTextConstructor = chatComponentTextClass.getConstructor(String.class);

            Class<?> packetClass = NmsUtil.getNmsClass("PacketPlayOutChat");
            packetConstructor = packetClass.getConstructor(iChatBaseComponentClass, byte.class);

            Class<?> craftPlayerClass = NmsUtil.getCraftClass("entity.CraftPlayer");
            getHandleMethod = craftPlayerClass.getMethod("getHandle");

            Class<?> entityPlayerClass = NmsUtil.getNmsClass("EntityPlayer");
            playerConnectionField = entityPlayerClass.getField("playerConnection");

            Class<?> playerConnectionClass = NmsUtil.getNmsClass("PlayerConnection");
            Class<?> packetSuperClass = NmsUtil.getNmsClass("Packet");
            sendPacketMethod = playerConnectionClass.getMethod("sendPacket", packetSuperClass);
        } catch (Exception e) {
            supported = false;
            plugin.getLogger().warning("[Domination] Action bar non disponible via NMS sur ce core "
                    + "(repli sur un message de chat classique). Detail : " + e);
        }
    }

    /** Envoie une ligne d'action bar (avec codes couleur '&') a un joueur, si active dans la config. */
    public void send(Player player, String rawMessage) {
        String colored = ChatColor.translateAlternateColorCodes('&', rawMessage);

        if (!supported) {
            player.sendMessage(colored);
            return;
        }
        try {
            Object component = chatComponentTextConstructor.newInstance(colored);
            Object packet = packetConstructor.newInstance(component, (byte) 2);
            Object entityPlayer = getHandleMethod.invoke(player);
            Object connection = playerConnectionField.get(entityPlayer);
            sendPacketMethod.invoke(connection, packet);
        } catch (Exception e) {
            // Ne jamais faire planter l'evenement pour un simple probleme d'affichage.
            player.sendMessage(colored);
        }
    }

    public boolean isSupported() {
        return supported;
    }
}
