package de.cb.drones.configeditor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ConfigEditorHolder implements InventoryHolder {
    public enum Type {
        CATEGORIES,
        OPTIONS
    }

    private final Type type;
    private final String categoryId;
    private final int page;
    private Inventory inventory;

    public ConfigEditorHolder(Type type, String categoryId, int page) {
        this.type = type;
        this.categoryId = categoryId;
        this.page = page;
    }

    public Type type() {
        return type;
    }

    public String categoryId() {
        return categoryId;
    }

    public int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
