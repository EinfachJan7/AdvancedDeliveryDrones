package de.cb.drones.configeditor;

import de.cb.drones.drone.GuiItem;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class ConfigEditorGuiSettings {
    private final String categoriesTitle;
    private final int categoriesSize;
    private final GuiItem categoriesFillItem;
    private final List<Integer> categoryContentSlots;
    private final String categoryNameFormat;
    private final List<String> categoryLore;

    private final String optionsTitle;
    private final int optionsSize;
    private final GuiItem optionsFillItem;
    private final List<Integer> optionContentSlots;
    private final String optionNameFormat;
    private final List<String> optionLore;

    private final GuiItem backItem;
    private final GuiItem previousPageItem;
    private final GuiItem nextPageItem;
    private final GuiItem pageInfoItem;

    public ConfigEditorGuiSettings(FileConfiguration guiConfig) {
        String section = "config-editor.";

        this.categoriesTitle = guiConfig.getString(section + "categories.title",
                "<!italic><gradient:#a855f7:#22d3ee>ᴄᴏɴꜰɪɢ ᴇᴅɪᴛᴏʀ</gradient>");
        this.categoriesSize = clampSize(guiConfig.getInt(section + "categories.size", 54));
        this.categoriesFillItem = parseFillItem(guiConfig, section + "categories.fill-item");
        this.categoryContentSlots = parseSlots(guiConfig, section + "categories.content-slots", defaultCategorySlots());
        this.categoryNameFormat = guiConfig.getString(section + "category-item.name-format",
                "<!italic><gradient:#a855f7:#ec4899><bold><name></bold></gradient>");
        this.categoryLore = nonEmptyLore(guiConfig.getStringList(section + "category-item.lore"), List.of(
                "<!italic><#9ca3af>  <count> ᴇɪɴsᴛᴇʟʟᴜɴɢ(en)",
                "<!italic><#a855f7>⬥ <italic>ᴋʟɪᴄᴋ ᴛᴏ ᴏᴘᴇɴ</italic>"
        ));

        this.optionsTitle = guiConfig.getString(section + "options.title",
                "<!italic><gradient:#22d3ee:#3b82f6><category></gradient>");
        this.optionsSize = clampSize(guiConfig.getInt(section + "options.size", 54));
        this.optionsFillItem = parseFillItem(guiConfig, section + "options.fill-item");
        this.optionContentSlots = parseSlots(guiConfig, section + "options.content-slots", defaultOptionSlots());
        this.optionNameFormat = guiConfig.getString(section + "option-item.name-format",
                "<!italic><gradient:#22d3ee:#3b82f6><bold><name></bold></gradient>");
        this.optionLore = nonEmptyLore(guiConfig.getStringList(section + "option-item.lore"), List.of(
                "<!italic><#9ca3af>  <description>",
                "<!italic><#4b5563>┌────────────────────┐",
                "<!italic><#9ca3af>  ᴡᴇʀᴛ: <#f3f4f6><value>",
                "<!italic><#4b5563>└────────────────────┘",
                "<!italic><#22d3ee>⬥ <italic><action></italic>"
        ));

        this.backItem = parseNavItem(guiConfig, section + "pagination.back", 45, Material.ARROW,
                "<!italic><gradient:#a855f7:#ec4899>⟵ ʙᴀᴄᴋ</gradient>",
                List.of("<!italic><#9ca3af>  ᴢᴜʀüᴄᴋ</#9ca3af>"));
        this.previousPageItem = parseNavItem(guiConfig, section + "pagination.previous", 48, Material.ARROW,
                "<!italic><gradient:#a855f7:#ec4899>◀ ᴘʀᴇᴠ</gradient>",
                List.of("<!italic><#9ca3af>  ᴠᴏʀʜᴇʀɪɢᴇ sᴇɪᴛᴇ</#9ca3af>"));
        this.nextPageItem = parseNavItem(guiConfig, section + "pagination.next", 50, Material.ARROW,
                "<!italic><gradient:#a855f7:#ec4899>ɴᴇxᴛ ▶</gradient>",
                List.of("<!italic><#9ca3af>  ɴäᴄʜsᴛᴇ sᴇɪᴛᴇ</#9ca3af>"));
        this.pageInfoItem = parseNavItem(guiConfig, section + "pagination.page-info", 49, Material.PAPER,
                "<!italic><#f3f4f6>sᴇɪᴛᴇ <page>/<pages>",
                List.of("<!italic><#9ca3af>  <count> ᴇɪɴᴛʀäɢᴇ</#9ca3af>"));
    }

    public String categoriesTitle() {
        return categoriesTitle;
    }

    public int categoriesSize() {
        return categoriesSize;
    }

    public GuiItem categoriesFillItem() {
        return categoriesFillItem;
    }

    public List<Integer> categoryContentSlots() {
        return categoryContentSlots;
    }

    public String categoryNameFormat() {
        return categoryNameFormat;
    }

    public List<String> categoryLore() {
        return categoryLore;
    }

    public String optionsTitle() {
        return optionsTitle;
    }

    public int optionsSize() {
        return optionsSize;
    }

    public GuiItem optionsFillItem() {
        return optionsFillItem;
    }

    public List<Integer> optionContentSlots() {
        return optionContentSlots;
    }

    public String optionNameFormat() {
        return optionNameFormat;
    }

    public List<String> optionLore() {
        return optionLore;
    }

    public GuiItem backItem() {
        return backItem;
    }

    public GuiItem previousPageItem() {
        return previousPageItem;
    }

    public GuiItem nextPageItem() {
        return nextPageItem;
    }

    public GuiItem pageInfoItem() {
        return pageInfoItem;
    }

    public int itemsPerPage(boolean categories) {
        return categories ? categoryContentSlots.size() : optionContentSlots.size();
    }

    private static int clampSize(int size) {
        return Math.max(9, Math.min(54, size));
    }

    private static GuiItem parseFillItem(FileConfiguration cfg, String section) {
        Material material = parseMaterial(cfg.getString(section + ".material", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        String name = cfg.getString(section + ".name", " ");
        return new GuiItem(-1, material, name, List.of());
    }

    private static GuiItem parseNavItem(FileConfiguration cfg, String section, int defaultPos, Material defaultMaterial, String defaultName, List<String> defaultLore) {
        int position = cfg.getInt(section + ".position", defaultPos);
        Material material = parseMaterial(cfg.getString(section + ".material", defaultMaterial.name()), defaultMaterial);
        String name = cfg.getString(section + ".name", defaultName);
        List<String> lore = nonEmptyLore(cfg.getStringList(section + ".lore"), defaultLore);
        return new GuiItem(position, material, name, lore);
    }

    private static List<Integer> parseSlots(FileConfiguration cfg, String path, List<Integer> defaults) {
        List<Integer> slots = cfg.getIntegerList(path);
        if (slots.isEmpty()) {
            return defaults;
        }
        List<Integer> valid = new ArrayList<>();
        for (Integer slot : slots) {
            if (slot != null && slot >= 0 && slot < 54) {
                valid.add(slot);
            }
        }
        return valid.isEmpty() ? defaults : valid;
    }

    private static List<Integer> defaultCategorySlots() {
        return List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
    }

    private static List<Integer> defaultOptionSlots() {
        return List.of(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        );
    }

    private static Material parseMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(value.trim(), true);
        return parsed == null ? fallback : parsed;
    }

    private static List<String> nonEmptyLore(List<String> lore, List<String> fallback) {
        return lore == null || lore.isEmpty() ? fallback : lore;
    }
}
