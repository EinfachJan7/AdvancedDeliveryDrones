package de.cb.drones.configeditor;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.drone.GuiItem;
import de.cb.drones.util.SkullTextureUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class ConfigEditorGUI {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final AdvancedDeliveryDronesPlugin plugin;
    private final ConfigEditorService service;
    private ConfigEditorGuiSettings guiSettings;
    private final NamespacedKey optionIdKey;
    private final NamespacedKey categoryIdKey;
    private final NamespacedKey navActionKey;

    public ConfigEditorGUI(AdvancedDeliveryDronesPlugin plugin, ConfigEditorService service, ConfigEditorGuiSettings guiSettings) {
        this.plugin = plugin;
        this.service = service;
        this.guiSettings = guiSettings;
        this.optionIdKey = new NamespacedKey(plugin, "config_option_id");
        this.categoryIdKey = new NamespacedKey(plugin, "config_category_id");
        this.navActionKey = new NamespacedKey(plugin, "config_nav_action");
    }

    public void reloadSettings(ConfigEditorGuiSettings settings) {
        this.guiSettings = settings;
    }

    public void openCategories(Player player) {
        openCategories(player, 0);
    }

    public void openCategories(Player player, int page) {
        List<ConfigCategory> categories = ConfigEditorRegistry.categories();
        int perPage = guiSettings.itemsPerPage(true);
        int totalPages = Math.max(1, (int) Math.ceil(categories.size() / (double) perPage));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        ConfigEditorHolder holder = new ConfigEditorHolder(ConfigEditorHolder.Type.CATEGORIES, null, safePage);
        Inventory inventory = Bukkit.createInventory(holder, guiSettings.categoriesSize(),
                MINI_MESSAGE.deserialize(guiSettings.categoriesTitle()));
        holder.setInventory(inventory);

        fill(inventory, guiSettings.categoriesSize(), guiSettings.categoriesFillItem());

        int start = safePage * perPage;
        List<Integer> slots = guiSettings.categoryContentSlots();
        for (int i = 0; i < perPage && start + i < categories.size(); i++) {
            if (i >= slots.size()) {
                break;
            }
            ConfigCategory category = categories.get(start + i);
            int optionCount = ConfigEditorRegistry.optionsForCategory(category.id()).size();
            inventory.setItem(slots.get(i), createCategoryItem(category, optionCount));
        }

        addPagination(inventory, safePage, totalPages, categories.size(), true);
        player.openInventory(inventory);
    }

    public void openOptions(Player player, String categoryId) {
        openOptions(player, categoryId, 0);
    }

    public void openOptions(Player player, String categoryId, int page) {
        List<ConfigOption> options = ConfigEditorRegistry.optionsForCategory(categoryId);
        int perPage = guiSettings.itemsPerPage(false);
        int totalPages = Math.max(1, (int) Math.ceil(options.size() / (double) perPage));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        String categoryName = localizedCategoryName(categoryId);
        String title = guiSettings.optionsTitle().replace("<category>", categoryName);

        ConfigEditorHolder holder = new ConfigEditorHolder(ConfigEditorHolder.Type.OPTIONS, categoryId, safePage);
        Inventory inventory = Bukkit.createInventory(holder, guiSettings.optionsSize(),
                MINI_MESSAGE.deserialize(title));
        holder.setInventory(inventory);

        fill(inventory, guiSettings.optionsSize(), guiSettings.optionsFillItem());

        int start = safePage * perPage;
        List<Integer> slots = guiSettings.optionContentSlots();
        for (int i = 0; i < perPage && start + i < options.size(); i++) {
            if (i >= slots.size()) {
                break;
            }
            inventory.setItem(slots.get(i), createOptionItem(options.get(start + i)));
        }

        addPagination(inventory, safePage, totalPages, options.size(), false);
        player.openInventory(inventory);
    }

    private void addPagination(Inventory inventory, int page, int totalPages, int totalEntries, boolean categories) {
        GuiItem back = guiSettings.backItem();
        if (back.position() >= 0 && back.position() < inventory.getSize()) {
            inventory.setItem(back.position(), createNavItem(back, "back", page, totalPages, totalEntries, categories));
        }

        if (totalPages <= 1) {
            return;
        }

        GuiItem previous = guiSettings.previousPageItem();
        if (page > 0 && previous.position() >= 0 && previous.position() < inventory.getSize()) {
            inventory.setItem(previous.position(), createNavItem(previous, "previous", page, totalPages, totalEntries, categories));
        }

        GuiItem pageInfo = guiSettings.pageInfoItem();
        if (pageInfo.position() >= 0 && pageInfo.position() < inventory.getSize()) {
            inventory.setItem(pageInfo.position(), createNavItem(pageInfo, "page-info", page, totalPages, totalEntries, categories));
        }

        GuiItem next = guiSettings.nextPageItem();
        if (page < totalPages - 1 && next.position() >= 0 && next.position() < inventory.getSize()) {
            inventory.setItem(next.position(), createNavItem(next, "next", page, totalPages, totalEntries, categories));
        }
    }

    private ItemStack createCategoryItem(ConfigCategory category, int optionCount) {
        String name = localizedCategoryName(category.id());
        String nameLine = guiSettings.categoryNameFormat().replace("<name>", name).replace("<count>", String.valueOf(optionCount));

        List<Component> lore = new ArrayList<>();
        for (String line : guiSettings.categoryLore()) {
            lore.add(MINI_MESSAGE.deserialize(line
                    .replace("<name>", name)
                    .replace("<count>", String.valueOf(optionCount))));
        }

        ItemStack item = new ItemStack(category.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI_MESSAGE.deserialize(nameLine));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(categoryIdKey, PersistentDataType.STRING, category.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createOptionItem(ConfigOption option) {
        String name = localizedOptionName(option);
        String description = localizedOptionDescription(option);
        String value = service.getDisplayValue(option);
        String action = localizedAction(option.type());

        String nameLine = guiSettings.optionNameFormat()
                .replace("<name>", name)
                .replace("<value>", value)
                .replace("<action>", action)
                .replace("<description>", description);

        List<Component> lore = new ArrayList<>();
        for (String line : guiSettings.optionLore()) {
            lore.add(MINI_MESSAGE.deserialize(line
                    .replace("<name>", name)
                    .replace("<value>", value)
                    .replace("<action>", action)
                    .replace("<description>", description)
                    .replace("<path>", option.configPath())));
        }

        ItemStack item = new ItemStack(option.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI_MESSAGE.deserialize(nameLine));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(optionIdKey, PersistentDataType.STRING, option.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavItem(GuiItem template, String action, int page, int totalPages, int totalEntries, boolean categories) {
        String name = template.name()
                .replace("<page>", String.valueOf(page + 1))
                .replace("<pages>", String.valueOf(totalPages))
                .replace("<count>", String.valueOf(totalEntries));

        List<Component> lore = new ArrayList<>();
        for (String line : template.lore()) {
            lore.add(MINI_MESSAGE.deserialize(line
                    .replace("<page>", String.valueOf(page + 1))
                    .replace("<pages>", String.valueOf(totalPages))
                    .replace("<count>", String.valueOf(totalEntries))));
        }

        ItemStack item = new ItemStack(template.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyHeadTexture(meta, template);
            meta.displayName(MINI_MESSAGE.deserialize(name));
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.getPersistentDataContainer().set(navActionKey, PersistentDataType.STRING, action);
            if ("back".equals(action) && !categories) {
                meta.getPersistentDataContainer().set(categoryIdKey, PersistentDataType.STRING, "options-back");
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fill(Inventory inventory, int size, GuiItem fillItem) {
        if (fillItem == null) {
            return;
        }
        ItemStack filler = new ItemStack(fillItem.material());
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            applyHeadTexture(meta, fillItem);
            meta.displayName(MINI_MESSAGE.deserialize(fillItem.name()));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }
    }

    public String localizedCategoryName(String categoryId) {
        return plugin.getLanguageManager().getString("config-editor-category-" + categoryId,
                ConfigEditorRegistry.category(categoryId).map(ConfigCategory::displayName).orElse(categoryId));
    }

    public String localizedOptionName(ConfigOption option) {
        return plugin.getLanguageManager().getString("config-editor-option-" + option.id() + "-name", option.name());
    }

    public String localizedOptionDescription(ConfigOption option) {
        return plugin.getLanguageManager().getString("config-editor-option-" + option.id() + "-desc", option.description());
    }

    private String localizedAction(ConfigOptionType type) {
        return switch (type) {
            case BOOLEAN -> plugin.getLanguageManager().getString("config-editor-action-toggle", "Click to toggle");
            case ENUM -> plugin.getLanguageManager().getString("config-editor-action-cycle", "Click to cycle");
            default -> plugin.getLanguageManager().getString("config-editor-action-chat", "Click for chat input");
        };
    }

    public NamespacedKey optionIdKey() {
        return optionIdKey;
    }

    public NamespacedKey categoryIdKey() {
        return categoryIdKey;
    }

    public NamespacedKey navActionKey() {
        return navActionKey;
    }

    public ConfigEditorGuiSettings guiSettings() {
        return guiSettings;
    }

    private static void applyHeadTexture(ItemMeta meta, GuiItem item) {
        if (item.headTexture() == null || item.material() != Material.PLAYER_HEAD || !(meta instanceof SkullMeta skullMeta)) {
            return;
        }
        SkullTextureUtils.applyTexture(skullMeta, item.headTexture());
    }
}
