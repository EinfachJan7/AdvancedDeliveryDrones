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
    private final String socketItemNameFormat;
    private final String socketItemOwnerFormat;
    private final String socketItemClickHint;
    private final String socketItemEmptyLine;
    private final Material socketItemMaterial;
    private final String playerHeadNameFormat;
    private final List<String> playerHeadLore;
    
    public GuiConfiguration(FileConfiguration guiConfig) {
        this.sendMode = parseGuiSettings(guiConfig, "send-mode.", createDefaultSendModeItems());
        this.mainMenu = parseGuiSettings(guiConfig, "main-menu.", createDefaultMainMenuItems());
        this.playerSelection = parseGuiSettings(guiConfig, "player-selection.", createDefaultPlayerSelectionItems());
        this.targetSelection = parseGuiSettings(guiConfig, "target-selection.", createDefaultTargetSelectionItems());
        this.socketSelection = parseGuiSettings(guiConfig, "socket-selection.", createDefaultSocketSelectionItems());
        
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
    }
    
    public GuiSettings sendMode() { return sendMode; }
    public GuiSettings mainMenu() { return mainMenu; }
    public GuiSettings playerSelection() { return playerSelection; }
    public GuiSettings targetSelection() { return targetSelection; }
    public GuiSettings socketSelection() { return socketSelection; }
    public String socketItemNameFormat() { return socketItemNameFormat; }
    public String socketItemOwnerFormat() { return socketItemOwnerFormat; }
    public String socketItemClickHint() { return socketItemClickHint; }
    public String socketItemEmptyLine() { return socketItemEmptyLine; }
    public Material socketItemMaterial() { return socketItemMaterial; }
    public String playerHeadNameFormat() { return playerHeadNameFormat; }
    public List<String> playerHeadLore() { return playerHeadLore; }
    
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
        
        // Parse back item if it exists
        String backSection = section + "back-item";
        if (cfg.contains(backSection + ".position")) {
            int backPosition = cfg.getInt(backSection + ".position");
            Material backMaterial = parseMaterial(cfg.getString(backSection + ".material", "ARROW"), Material.ARROW);
            String backName = cfg.getString(backSection + ".name", "<yellow>Zurück");
            List<String> backLore = cfg.getStringList(backSection + ".lore");
            if (backLore.isEmpty()) {
                backLore = List.of("<gray>Zurück");
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
        return items;
    }
    
    private static Map<String, GuiItem> createDefaultPlayerSelectionItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("back", new GuiItem(45, Material.ARROW, "<yellow>Zurück", List.of("<gray>Zurück zum Hauptmenü")));
        return items;
    }
    
    private static Map<String, GuiItem> createDefaultTargetSelectionItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("back", new GuiItem(26, Material.ARROW, "<yellow>Zurück", List.of("<gray>Zurück zum Hauptmenü")));
        items.put("player", new GuiItem(11, Material.PLAYER_HEAD, "<green>Spieler auswählen", List.of("<gray>Wähle einen Spieler aus", "<gray>um ihm eine Drohne zu senden")));
        items.put("socket", new GuiItem(15, Material.BEACON, "<yellow>Socket auswählen", List.of("<gray>Wähle einen Socket aus", "<gray>um dort eine Drohne zu senden")));
        return items;
    }
    
    private static Map<String, GuiItem> createDefaultSocketSelectionItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("back", new GuiItem(45, Material.ARROW, "<yellow>Zurück", List.of("<gray>Zurück zur Zielauswahl")));
        return items;
    }
}
