package de.cb.drones.gui;

import de.cb.drones.drone.GuiItem;
import de.cb.drones.drone.GuiSettings;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all GUI configurations from the gui.yml file
 */
public class GuiConfiguration {
    
    private final GuiSettings sendMode;
    private final GuiSettings mainMenu;
    private final GuiSettings playerSelection;
    private final GuiSettings targetSelection;
    private final GuiSettings socketSelection;
    private final GuiSettings socketManagement;
    private final GuiSettings socketEdit;
    private final String socketItemNameFormat;
    private final String socketItemOwnerFormat;
    private final String socketItemClickHint;
    private final String socketItemEmptyLine;
    private final Material socketItemMaterial;
    private final String playerHeadNameFormat;
    private final List<String> playerHeadLore;
    private final String socketManagementItemNameFormat;
    private final String socketManagementItemLocationFormat;
    private final String socketManagementItemDeleteHint;
    private final String socketManagementItemEmptyLine;
    private final Material socketManagementItemMaterial;
    private final String signRenameBorderLine;
    private final String signRenameTitleLine;
    
    public GuiConfiguration(FileConfiguration guiConfig) {
        this.sendMode = parseGuiSettings(guiConfig, "send-mode.", createDefaultSendModeItems());
        this.mainMenu = parseGuiSettings(guiConfig, "main-menu.", createDefaultMainMenuItems());
        this.playerSelection = parseGuiSettings(guiConfig, "player-selection.", createDefaultPlayerSelectionItems());
        this.targetSelection = parseGuiSettings(guiConfig, "target-selection.", createDefaultTargetSelectionItems());
        this.socketSelection = parseGuiSettings(guiConfig, "socket-selection.", createDefaultSocketSelectionItems());
        this.socketManagement = parseGuiSettings(guiConfig, "socket-management.", createDefaultSocketManagementItems());
        this.socketEdit = parseGuiSettings(guiConfig, "socket-edit.", createDefaultSocketEditItems());
        
        // Socket item format
        this.socketItemNameFormat = guiConfig.getString("socket-item-format.name-format", "<!italic><white><name></white>");
        this.socketItemOwnerFormat = guiConfig.getString("socket-item-format.owner-format", "<!italic><gray>Besitzer: <white><owner></white></gray>");
        this.socketItemClickHint = guiConfig.getString("socket-item-format.click-hint", "<!italic><green>Klicke um Drohne zu senden</green>");
        this.socketItemEmptyLine = guiConfig.getString("socket-item-format.empty-line", "<!italic><gray></gray>");
        this.socketItemMaterial = parseMaterial(guiConfig.getString("socket-item-format.material", "BEACON"), Material.BEACON);
        
        // Player head item format
        this.playerHeadNameFormat = guiConfig.getString("player-selection.player-head-item.name-format", "<!italic><white><player></white>");
        List<String> tempPlayerHeadLore = guiConfig.getStringList("player-selection.player-head-item.lore");
        if (tempPlayerHeadLore.isEmpty()) {
            tempPlayerHeadLore = List.of(
                "<!italic><gray>Klicke um eine Drohne</gray>",
                "<!italic><gray>an <white><player></white> zu senden</gray>"
            );
        }
        this.playerHeadLore = tempPlayerHeadLore;
        
        // Socket management item format
        this.socketManagementItemNameFormat = guiConfig.getString("socket-management.socket-management-item.name-format", "<!italic><white><name></white>");
        this.socketManagementItemLocationFormat = guiConfig.getString("socket-management.socket-management-item.location-format", "<!italic><gray>Ort: <white><world>, <x>, <y>, <z></white></gray>");
        this.socketManagementItemDeleteHint = guiConfig.getString("socket-management.socket-management-item.delete-hint", "<!italic><red>Rechtsklick zum Löschen</red>");
        this.socketManagementItemEmptyLine = guiConfig.getString("socket-management.socket-management-item.empty-line", "<!italic><gray></gray>");
        this.socketManagementItemMaterial = parseMaterial(guiConfig.getString("socket-management.socket-management-item.material", "BEACON"), Material.BEACON);
        
        // Sign rename configuration
        this.signRenameBorderLine = guiConfig.getString("sign-rename.border-line", "^^^^^^^^^^^^^^^");
        this.signRenameTitleLine = guiConfig.getString("sign-rename.title-line", "Neuer Name:");
    }
    
    public GuiSettings sendMode() { return sendMode; }
    public GuiSettings mainMenu() { return mainMenu; }
    public GuiSettings playerSelection() { return playerSelection; }
    public GuiSettings targetSelection() { return targetSelection; }
    public GuiSettings socketSelection() { return socketSelection; }
    public GuiSettings socketManagement() { return socketManagement; }
    public GuiSettings socketEdit() { return socketEdit; }
    public String socketItemNameFormat() { return socketItemNameFormat; }
    public String socketItemOwnerFormat() { return socketItemOwnerFormat; }
    public String socketItemClickHint() { return socketItemClickHint; }
    public String socketItemEmptyLine() { return socketItemEmptyLine; }
    public Material socketItemMaterial() { return socketItemMaterial; }
    public String playerHeadNameFormat() { return playerHeadNameFormat; }
    public List<String> playerHeadLore() { return playerHeadLore; }
    public String socketManagementItemNameFormat() { return socketManagementItemNameFormat; }
    public String socketManagementItemLocationFormat() { return socketManagementItemLocationFormat; }
    public String socketManagementItemDeleteHint() { return socketManagementItemDeleteHint; }
    public String socketManagementItemEmptyLine() { return socketManagementItemEmptyLine; }
    public Material socketManagementItemMaterial() { return socketManagementItemMaterial; }
    public String signRenameBorderLine() { return signRenameBorderLine; }
    public String signRenameTitleLine() { return signRenameTitleLine; }
    
    private static GuiSettings parseGuiSettings(FileConfiguration cfg, String section, Map<String, GuiItem> defaultItems) {
        String title = cfg.getString(section + "title", "GUI");
        int size = Math.max(9, Math.min(54, cfg.getInt(section + "size", 27)));
        
        Map<String, GuiItem> items = new HashMap<>();
        for (Map.Entry<String, GuiItem> entry : defaultItems.entrySet()) {
            String key = entry.getKey();
            GuiItem defaultItem = entry.getValue();
            
            String itemSection = section + "items." + key;
            int position = cfg.getInt(itemSection + ".position", defaultItem.position());
            Material material = parseMaterial(cfg.getString(itemSection + ".material", defaultItem.material().name()), defaultItem.material());
            String name = cfg.getString(itemSection + ".name", defaultItem.name());
            List<String> lore = cfg.getStringList(itemSection + ".lore");
            if (lore.isEmpty()) {
                lore = defaultItem.lore();
            }
            
            items.put(key, new GuiItem(position, material, name, lore));
        }
        
        // Parse back item if it exists in YAML
        String backSection = section + "back-item";
        GuiItem defaultBackItem = defaultItems.get("back");
        int defaultBackPosition = (defaultBackItem != null) ? defaultBackItem.position() : 18;
        
        // Check if back-item section exists in YAML
        boolean hasBackItemInYaml = cfg.contains(backSection);
        
        if (hasBackItemInYaml) {
            int backPosition = cfg.getInt(backSection + ".position", defaultBackPosition);
            Material backMaterial = parseMaterial(cfg.getString(backSection + ".material", defaultBackItem != null ? defaultBackItem.material().name() : "ARROW"), defaultBackItem != null ? defaultBackItem.material() : Material.ARROW);
            String backName = cfg.getString(backSection + ".name", defaultBackItem != null ? defaultBackItem.name() : "<yellow>Zurück");
            List<String> backLore = cfg.getStringList(backSection + ".lore");
            if (backLore.isEmpty()) {
                backLore = defaultBackItem != null ? defaultBackItem.lore() : List.of("<gray>Zurück");
            }
            items.put("back", new GuiItem(backPosition, backMaterial, backName, backLore));
        }
        
        // Parse fill item
        String fillSection = section + "fill-item";
        Material fillMaterial = parseMaterial(cfg.getString(fillSection + ".material", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        String fillName = cfg.getString(fillSection + ".name", " ");
        GuiItem fillItem = new GuiItem(-1, fillMaterial, fillName, List.of());
        
        return new GuiSettings(title, size, items, fillItem);
    }
    
    private static Material parseMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(value.trim(), true);
        return parsed == null ? fallback : parsed;
    }
    
    private static Map<String, GuiItem> createDefaultSendModeItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("animals", new GuiItem(11, Material.LEAD, "<gold>Tiere senden", List.of("<gray>Sendet nur angeleinte Tiere")));
        items.put("items", new GuiItem(15, Material.CHEST, "<gold>Items senden", List.of("<gray>Öffnet das Paket-Inventar")));
        return items;
    }
    
    private static Map<String, GuiItem> createDefaultMainMenuItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("send", new GuiItem(11, Material.PLAYER_HEAD, "<green>Drohne senden", List.of("<gray>Wähle einen Spieler aus", "<gray>um ihm eine Drohne zu senden")));
        items.put("toggle", new GuiItem(13, Material.REDSTONE, "<yellow>Drohnen-Empfang umschalten", List.of("<gray>Schalte ein/aus ob du", "<gray>Drohnen empfangen möchtest")));
        items.put("decline", new GuiItem(15, Material.BARRIER, "<red>Eingehende Drohnen ablehnen", List.of("<gray>Lehne alle eingehenden", "<gray>Drohnen für dich ab")));
        items.put("preview", new GuiItem(22, Material.ENDER_EYE, "<aqua>Drohne-Vorschau", List.of("<gray>Zeige eine Vorschau deiner", "<gray>aktiven Drohnen")));
        items.put("socket-manage", new GuiItem(24, Material.BEACON, "<yellow>Sockets Verwalten", List.of("<gray>Verwalte deine", "<gray>Lieferstationen")));
        return items;
    }
    
    private static Map<String, GuiItem> createDefaultPlayerSelectionItems() {
        Map<String, GuiItem> items = new HashMap<>();
        return items;
    }
    
    private static Map<String, GuiItem> createDefaultTargetSelectionItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("player", new GuiItem(11, Material.PLAYER_HEAD, "<green>Spieler auswählen", List.of("<gray>Wähle einen Spieler aus", "<gray>um ihm eine Drohne zu senden")));
        items.put("socket", new GuiItem(15, Material.BEACON, "<yellow>Socket auswählen", List.of("<gray>Wähle einen Socket aus", "<gray>um dort eine Drohne zu senden")));
        return items;
    }
    
    private static Map<String, GuiItem> createDefaultSocketSelectionItems() {
        Map<String, GuiItem> items = new HashMap<>();
        return items;
    }
    
    private static Map<String, GuiItem> createDefaultSocketManagementItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("no-sockets-item", new GuiItem(22, Material.BARRIER, "<red>Keine Sockets", List.of("<gray>Du hast keine Sockets")));
        return items;
    }

    private static Map<String, GuiItem> createDefaultSocketEditItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("rename", new GuiItem(11, Material.NAME_TAG, "<yellow>Rename", List.of("<gray>Click to rename the socket")));
        items.put("relocate", new GuiItem(13, Material.COMPASS, "<yellow>Relocate", List.of("<gray>Click to change the position")));
        items.put("delete", new GuiItem(15, Material.BARRIER, "<red>Delete", List.of("<gray>Click to delete the socket")));
        return items;
    }
}
