package fr.draftmc.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GuiHolder implements InventoryHolder {
    private final String menu;
    private final String extra;
    private final int page;

    public GuiHolder(String menu) {
        this(menu, "", 0);
    }

    public GuiHolder(String menu, String extra) {
        this(menu, extra, 0);
    }

    public GuiHolder(String menu, String extra, int page) {
        this.menu = menu;
        this.extra = extra == null ? "" : extra;
        this.page = page;
    }

    public String menu() {
        return menu;
    }

    public String extra() {
        return extra;
    }

    public int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
