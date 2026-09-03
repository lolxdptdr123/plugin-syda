package fr.sydaria.factions;

import org.bukkit.Material;

public enum FactionPerm {
    BUILD("Construire", Material.BRICK, true, true, false, false),
    BREAK("Casser", Material.DIAMOND_PICKAXE, true, true, false, false),
    CONTAINER("Conteneurs", Material.CHEST, true, true, false, false),
    DOOR("Portes", Material.WOOD_DOOR, true, true, true, false),
    BUTTON("Boutons", Material.STONE_BUTTON, true, true, true, false),
    LEVER("Leviers", Material.LEVER, true, true, false, false),
    HOME("Home faction", Material.BED, false, true, true, true),
    SETHOME("Définir le home", Material.COMPASS, false, false, true, true),
    INVITE("Inviter", Material.NAME_TAG, false, false, true, true),
    KICK("Expulser", Material.IRON_SWORD, false, false, false, true),
    PROMOTE("Promouvoir", Material.GOLD_SWORD, false, false, false, true),
    CLAIM("Claim", Material.GOLD_HOE, false, false, false, true),
    UNCLAIM("Unclaim", Material.WOOD_HOE, false, false, false, true),
    RELATIONS("Ennemis", Material.IRON_SWORD, false, false, false, true),
    UPGRADE("Upgrades", Material.ANVIL, false, false, false, true),
    CHEST("Coffre /f", Material.ENDER_CHEST, false, true, true, true),
    FLY("Fly", Material.FEATHER, false, false, true, true),
    DESC("Description", Material.BOOK, false, false, false, true),
    MOTD("MOTD", Material.PAPER, false, false, true, true),
    OPEN("Ouverture", Material.IRON_DOOR, false, false, false, true),
    PERM("Permissions", Material.REDSTONE_TORCH_ON, false, false, false, true),
    RALLY("Rally", Material.BEACON, false, false, true, true);

    private final String display;
    private final Material icon;
    private final boolean land;
    private final boolean recruit;
    private final boolean member;
    private final boolean officer;

    FactionPerm(String display, Material icon, boolean land, boolean recruit, boolean member, boolean officer) {
        this.display = display;
        this.icon = icon;
        this.land = land;
        this.recruit = recruit;
        this.member = member;
        this.officer = officer;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    public boolean land() {
        return land;
    }

    public boolean shownFor(String group) {
        if ("NEUTRAL".equals(group) || "ENEMY".equals(group)) {
            return land;
        }
        return true;
    }

    public boolean defaultValue(String group) {
        if ("LEADER".equals(group) || "COLEADER".equals(group)) {
            return true;
        }
        if ("OFFICER".equals(group)) {
            return officer || member || recruit;
        }
        if ("MEMBER".equals(group)) {
            return member || recruit;
        }
        if ("RECRUIT".equals(group)) {
            return recruit;
        }
        return false;
    }
}
