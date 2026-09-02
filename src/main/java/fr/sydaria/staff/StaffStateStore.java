package fr.sydaria.staff;

import fr.sydaria.Sydaria;
import fr.sydaria.util.Locations;
import fr.sydaria.util.YamlFile;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Sauvegarde sur disque du snapshot pris par StaffManager à l'activation du
 * mode staff, et supprimée à la désactivation propre.
 *
 * Pourquoi ce filet en plus du restore-on-quit de StaffManager : un /staff
 * vide l'inventaire de survie du joueur pendant qu'il patrouille en créatif.
 * Si le serveur crashe (kill -9, OOM, panne) pendant ce laps de temps, le
 * fichier player .dat de Minecraft sera sauvegardé avec un inventaire vide —
 * sans ce fichier, l'objet StaffState (qui ne vivait qu'en mémoire) est perdu
 * avec lui, et l'inventaire de survie du staff (armes/items custom, parfois
 * uniques) disparaît définitivement. Écrire ce snapshot sur disque au moment
 * de l'activation coûte une écriture YAML ponctuelle et élimine ce risque :
 * au prochain démarrage, Sydaria#onEnable log les entrées orphelines restées
 * dans ce fichier, et StaffManager les restaure automatiquement à la
 * reconnexion du joueur concerné.
 */
public class StaffStateStore {
    private final YamlFile file;

    public StaffStateStore(Sydaria plugin) {
        this.file = new YamlFile(plugin, "staffmode.yml");
    }

    public void save(UUID uuid, String playerName, StaffState state) {
        String path = uuid.toString();
        ConfigurationSection section = file.get().createSection(path);
        section.set("name", playerName);
        try {
            section.set("inventory", toBase64(state.inventory()));
            section.set("armor", toBase64(state.armor()));
        } catch (IOException e) {
            // On ne bloque jamais l'activation du mode staff pour un souci
            // d'écriture disque : le restore-on-quit en mémoire reste la
            // protection principale, ceci n'est qu'un filet secondaire.
            Sydaria.get().getLogger().warning("Impossible d'écrire le snapshot staff de " + playerName + " sur disque: " + e.getMessage());
        }
        section.set("health", state.health());
        section.set("food-level", state.foodLevel());
        section.set("saturation", (double) state.saturation());
        section.set("exp", (double) state.exp());
        section.set("level", state.level());
        section.set("game-mode", state.gameMode().name());
        section.set("allow-flight", state.allowFlight());
        section.set("flying", state.flying());
        section.set("walk-speed", (double) state.walkSpeed());
        section.set("fly-speed", (double) state.flySpeed());
        section.set("location", Locations.serialize(state.location()));
        file.save();
    }

    public void remove(UUID uuid) {
        if (file.get().contains(uuid.toString())) {
            file.get().set(uuid.toString(), null);
            file.save();
        }
    }

    public boolean has(UUID uuid) {
        return file.get().contains(uuid.toString());
    }

    /** UUIDs restés dans le fichier après un arrêt non propre (crash). */
    public List<UUID> pending() {
        List<UUID> result = new ArrayList<UUID>();
        for (String key : file.get().getKeys(false)) {
            try {
                result.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // clé invalide, on l'ignore plutôt que de faire échouer le chargement
            }
        }
        return result;
    }

    public StaffState load(UUID uuid) {
        ConfigurationSection section = file.get().getConfigurationSection(uuid.toString());
        if (section == null) {
            return null;
        }
        ItemStack[] inventory;
        ItemStack[] armor;
        try {
            inventory = fromBase64(section.getString("inventory"));
            armor = fromBase64(section.getString("armor"));
        } catch (Exception e) {
            Sydaria.get().getLogger().warning("Snapshot staff illisible pour " + uuid + ": " + e.getMessage());
            return null;
        }
        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(section.getString("game-mode", "SURVIVAL"));
        } catch (IllegalArgumentException e) {
            gameMode = GameMode.SURVIVAL;
        }
        Location location = Locations.deserialize(section.getString("location", ""));
        return new StaffState(
                inventory,
                armor,
                section.getDouble("health", 20.0),
                section.getInt("food-level", 20),
                (float) section.getDouble("saturation", 20.0),
                (float) section.getDouble("exp", 0.0),
                section.getInt("level", 0),
                gameMode,
                section.getBoolean("allow-flight", false),
                section.getBoolean("flying", false),
                (float) section.getDouble("walk-speed", 0.2),
                (float) section.getDouble("fly-speed", 0.1),
                location
        );
    }

    private static String toBase64(ItemStack[] items) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BukkitObjectOutputStream data = new BukkitObjectOutputStream(out);
        data.writeInt(items.length);
        for (ItemStack item : items) {
            data.writeObject(item);
        }
        data.close();
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static ItemStack[] fromBase64(String base64) throws IOException, ClassNotFoundException {
        if (base64 == null || base64.isEmpty()) {
            return new ItemStack[0];
        }
        ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
        BukkitObjectInputStream data = new BukkitObjectInputStream(in);
        int length = data.readInt();
        ItemStack[] items = new ItemStack[length];
        for (int i = 0; i < length; i++) {
            items[i] = (ItemStack) data.readObject();
        }
        data.close();
        return items;
    }
}
