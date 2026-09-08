package fr.draftmc.events.domination.utils;

import org.bukkit.Bukkit;

/**
 * Detection dynamique de la revision CraftBukkit (v1_8_R1/R2/R3) et
 * helpers de reflexion NMS.
 *
 * IMPORTANT sur la portee de cette classe : elle n'est utilisee QUE pour
 * l'ActionBar (le seul morceau d'UI qui n'existe pas dans l'API Bukkit
 * 1.8 - sendActionBar() n'a ete ajoute qu'en 1.11). Ce n'est PAS une
 * violation de la regle "aucune dependance a un core precis" : tous les
 * forks Bukkit listes dans le cahier des charges (Spigot, PaperSpigot,
 * TacoSpigot, NachoSpigot, CarbonSpigot, WindSpigot, FoxSpigot,
 * PandaSpigot, ImanitySpigot, nSpigot) sont tous des forks de la MEME
 * revision CraftBukkit 1.8.8/1.8.9 (v1_8_R3 en general), et conservent
 * volontairement le meme nom de paquet NMS/OBC pour rester compatibles
 * avec les plugins existants - c'est exactement ce qui rend cette
 * reflexion fiable ici, contrairement a une reflexion inter-versions
 * (1.8 vers 1.12 par exemple), qui casserait.
 */
public final class NmsUtil {

    private static final String VERSION;

    static {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        VERSION = packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private NmsUtil() {
    }

    public static String getVersion() {
        return VERSION;
    }

    public static Class<?> getNmsClass(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + VERSION + "." + name);
    }

    public static Class<?> getCraftClass(String name) throws ClassNotFoundException {
        return Class.forName("org.bukkit.craftbukkit." + VERSION + "." + name);
    }
}
