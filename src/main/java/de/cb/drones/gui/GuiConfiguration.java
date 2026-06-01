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
    private final GuiSettings socketTrustMenu;
    private final GuiSettings socketBlacklistMenu;
    private final GuiSettings trustPlayerSelection;
    private final GuiSettings untrustPlayerSelection;
    private final GuiSettings blacklistManagement;
    private final GuiSettings blacklistPlayerAddSelection;
    private final GuiSettings blacklistPlayerRemoveSelection;
    private final GuiSettings blacklistSocketAddSelection;
    private final GuiSettings blacklistSocketRemoveSelection;
    private final String socketItemNameFormat;
    private final String socketItemOwnerFormat;
    private final String socketItemClickHint;
    private final String socketItemEmptyLine;
    private final Material socketItemMaterial;
    private final String playerHeadNameFormat;
    private final List<String> playerHeadLore;
    private final String trustPlayerHeadNameFormat;
    private final List<String> trustPlayerHeadLore;
    private final String untrustPlayerHeadNameFormat;
    private final List<String> untrustPlayerHeadLore;
    private final String blacklistAddPlayerHeadNameFormat;
    private final List<String> blacklistAddPlayerHeadLore;
    private final String blacklistRemovePlayerHeadNameFormat;
    private final List<String> blacklistRemovePlayerHeadLore;
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
        this.socketTrustMenu = parseGuiSettings(guiConfig, "socket-trust-menu.", createDefaultSocketTrustMenuItems());
        this.socketBlacklistMenu = parseGuiSettings(guiConfig, "socket-blacklist-menu.", createDefaultSocketBlacklistMenuItems());
        this.trustPlayerSelection = parseGuiSettings(guiConfig, "trust-player-selection.", createDefaultPlayerSelectionItems());
        this.untrustPlayerSelection = parseGuiSettings(guiConfig, "untrust-player-selection.", createDefaultPlayerSelectionItems());
        this.blacklistManagement = parseGuiSettings(guiConfig, "blacklist-management.", createDefaultBlacklistManagementItems());
        this.blacklistPlayerAddSelection = parseGuiSettings(guiConfig, "blacklist-player-add-selection.", createDefaultPlayerSelectionItems());
        this.blacklistPlayerRemoveSelection = parseGuiSettings(guiConfig, "blacklist-player-remove-selection.", createDefaultPlayerSelectionItems());
        this.blacklistSocketAddSelection = parseGuiSettings(guiConfig, "blacklist-socket-add-selection.", createDefaultPlayerSelectionItems());
        this.blacklistSocketRemoveSelection = parseGuiSettings(guiConfig, "blacklist-socket-remove-selection.", createDefaultPlayerSelectionItems());

        // Socket item format
        this.socketItemNameFormat = guiConfig.getString("socket-item-format.name-format", "<!italic><white><name></white>");
        this.socketItemOwnerFormat = guiConfig.getString("socket-item-format.owner-format", "<!italic><gray>Besitzer: <white><owner></white></gray>");
        this.socketItemClickHint = guiConfig.getString("socket-item-format.click-hint", "<!italic><green>Klicke um Drohne zu senden</green>");
        this.socketItemEmptyLine = guiConfig.getString("socket-item-format.empty-line", "<!italic><gray></gray>");
        this.socketItemMaterial = parseMaterial(guiConfig.getString("socket-item-format.material", "BEACON"), Material.BEACON);

        // Player head item format (regular)
        this.playerHeadNameFormat = guiConfig.getString("player-selection.player-head-item.name-format", "<!italic><white><player></white>");
        List<String> tempPlayerHeadLore = guiConfig.getStringList("player-selection.player-head-item.lore");
        if (tempPlayerHeadLore.isEmpty()) {
            tempPlayerHeadLore = List.of(
                "<!italic><gray>Klicke um eine Drohne</gray>",
                "<!italic><gray>an <white><player></white> zu senden</gray>"
            );
        }
        this.playerHeadLore = tempPlayerHeadLore;

        // Player head item format (trust)
        this.trustPlayerHeadNameFormat = guiConfig.getString("trust-player-selection.player-head-item.name-format", "<!italic><white><player></white>");
        List<String> tempTrustPlayerHeadLore = guiConfig.getStringList("trust-player-selection.player-head-item.lore");
        if (tempTrustPlayerHeadLore.isEmpty()) {
            tempTrustPlayerHeadLore = List.of(
                "<!italic><gray>Klicke um den Spieler</gray>",
                "<!italic><gray>zu <#4ade80>vertrauen</gray>"
            );
        }
        this.trustPlayerHeadLore = tempTrustPlayerHeadLore;

        // Player head item format (untrust)
        this.untrustPlayerHeadNameFormat = guiConfig.getString("untrust-player-selection.player-head-item.name-format", "<!italic><white><player></white>");
        List<String> tempUntrustPlayerHeadLore = guiConfig.getStringList("untrust-player-selection.player-head-item.lore");
        if (tempUntrustPlayerHeadLore.isEmpty()) {
            tempUntrustPlayerHeadLore = List.of(
                "<!italic><gray>Klicke um das Vertrauen</gray>",
                "<!italic><gray>zu <#f87171>entfernen</gray>"
            );
        }
        this.untrustPlayerHeadLore = tempUntrustPlayerHeadLore;

        this.blacklistAddPlayerHeadNameFormat = guiConfig.getString(
                "blacklist-player-add-selection.player-head-item.name-format",
                "<!italic><gradient:#f87171:#ef4444><bold><player></bold></gradient>");
        List<String> tempBlacklistAddLore = guiConfig.getStringList("blacklist-player-add-selection.player-head-item.lore");
        if (tempBlacklistAddLore.isEmpty()) {
            tempBlacklistAddLore = List.of(
                    "<!italic><gray>Klicke um den Spieler zu sperren</gray>"
            );
        }
        this.blacklistAddPlayerHeadLore = tempBlacklistAddLore;

        this.blacklistRemovePlayerHeadNameFormat = guiConfig.getString(
                "blacklist-player-remove-selection.player-head-item.name-format",
                "<!italic><gradient:#4ade80:#22c55e><bold><player></bold></gradient>");
        List<String> tempBlacklistRemoveLore = guiConfig.getStringList("blacklist-player-remove-selection.player-head-item.lore");
        if (tempBlacklistRemoveLore.isEmpty()) {
            tempBlacklistRemoveLore = List.of(
                    "<!italic><gray>Klicke um die Sperre aufzuheben</gray>"
            );
        }
        this.blacklistRemovePlayerHeadLore = tempBlacklistRemoveLore;
        
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
    public GuiSettings socketTrustMenu() { return socketTrustMenu; }
    public GuiSettings socketBlacklistMenu() { return socketBlacklistMenu; }
    public GuiSettings trustPlayerSelection() { return trustPlayerSelection; }
    public GuiSettings untrustPlayerSelection() { return untrustPlayerSelection; }
    public GuiSettings blacklistManagement() { return blacklistManagement; }
    public GuiSettings blacklistPlayerAddSelection() { return blacklistPlayerAddSelection; }
    public GuiSettings blacklistPlayerRemoveSelection() { return blacklistPlayerRemoveSelection; }
    public GuiSettings blacklistSocketAddSelection() { return blacklistSocketAddSelection; }
    public GuiSettings blacklistSocketRemoveSelection() { return blacklistSocketRemoveSelection; }
    public String socketItemNameFormat() { return socketItemNameFormat; }
    public String socketItemOwnerFormat() { return socketItemOwnerFormat; }
    public String socketItemClickHint() { return socketItemClickHint; }
    public String socketItemEmptyLine() { return socketItemEmptyLine; }
    public Material socketItemMaterial() { return socketItemMaterial; }
    public String playerHeadNameFormat() { return playerHeadNameFormat; }
    public List<String> playerHeadLore() { return playerHeadLore; }
    public String trustPlayerHeadNameFormat() { return trustPlayerHeadNameFormat; }
    public List<String> trustPlayerHeadLore() { return trustPlayerHeadLore; }
    public String untrustPlayerHeadNameFormat() { return untrustPlayerHeadNameFormat; }
    public List<String> untrustPlayerHeadLore() { return untrustPlayerHeadLore; }
    public String blacklistAddPlayerHeadNameFormat() { return blacklistAddPlayerHeadNameFormat; }
    public List<String> blacklistAddPlayerHeadLore() { return blacklistAddPlayerHeadLore; }
    public String blacklistRemovePlayerHeadNameFormat() { return blacklistRemovePlayerHeadNameFormat; }
    public List<String> blacklistRemovePlayerHeadLore() { return blacklistRemovePlayerHeadLore; }
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

            String headTexture = null;
            if (material == Material.PLAYER_HEAD) {
                headTexture = firstNonBlank(
                        cfg.getString(itemSection + ".value"),
                        cfg.getString(itemSection + ".head-texture"),
                        cfg.getString(itemSection + ".texture")
                );
            }

            items.put(key, new GuiItem(position, material, name, lore, headTexture));
        }
        
        // Parse back item
        String backSection = section + "back-item";
        boolean hasLocalBackItem = cfg.contains(backSection);
        boolean isExplicitlyDisabled = hasLocalBackItem && cfg.isBoolean(backSection) && !cfg.getBoolean(backSection);
        boolean hasGlobalBackItem = cfg.contains("global.back-item");
        boolean isRootMenu = section.equals("main-menu.") || section.equals("send-mode.");
        
        boolean shouldHaveBackItem = (hasLocalBackItem && !isExplicitlyDisabled) 
                || (hasGlobalBackItem && !isRootMenu && !isExplicitlyDisabled);
        
        if (shouldHaveBackItem) {
            GuiItem defaultBackItem = defaultItems.get("back");
            
            // 1. Position
            int backPosition;
            if (hasLocalBackItem && cfg.isInt(backSection + ".position")) {
                backPosition = cfg.getInt(backSection + ".position");
            } else if (hasGlobalBackItem && cfg.isInt("global.back-item.position")) {
                backPosition = cfg.getInt("global.back-item.position");
            } else if (defaultBackItem != null) {
                backPosition = defaultBackItem.position();
            } else {
                backPosition = size - 9;
            }
            
            // 2. Material
            Material backMaterial = null;
            if (hasLocalBackItem && cfg.contains(backSection + ".material")) {
                backMaterial = parseMaterial(cfg.getString(backSection + ".material"), null);
            }
            if (backMaterial == null && hasGlobalBackItem && cfg.contains("global.back-item.material")) {
                backMaterial = parseMaterial(cfg.getString("global.back-item.material"), null);
            }
            if (backMaterial == null && defaultBackItem != null) {
                backMaterial = defaultBackItem.material();
            }
            if (backMaterial == null) {
                backMaterial = Material.ARROW;
            }
            
            // 3. Name
            String backName = null;
            if (hasLocalBackItem && cfg.contains(backSection + ".name")) {
                backName = cfg.getString(backSection + ".name");
            }
            if (backName == null && hasGlobalBackItem && cfg.contains("global.back-item.name")) {
                backName = cfg.getString("global.back-item.name");
            }
            if (backName == null && defaultBackItem != null) {
                backName = defaultBackItem.name();
            }
            if (backName == null) {
                backName = "<yellow>Zurück";
            }
            
            // 4. Lore
            List<String> backLore = null;
            if (hasLocalBackItem && cfg.contains(backSection + ".lore")) {
                backLore = cfg.getStringList(backSection + ".lore");
            }
            if ((backLore == null || backLore.isEmpty()) && hasGlobalBackItem && cfg.contains("global.back-item.lore")) {
                backLore = cfg.getStringList("global.back-item.lore");
            }
            if ((backLore == null || backLore.isEmpty()) && defaultBackItem != null) {
                backLore = defaultBackItem.lore();
            }
            if (backLore == null || backLore.isEmpty()) {
                backLore = List.of("<gray>Zurück");
            }
            
            items.put("back", new GuiItem(backPosition, backMaterial, backName, backLore, null));
        }
        
        // Parse fill item
        String fillSection = section + "fill-item";
        boolean hasLocalFillItem = cfg.contains(fillSection);
        boolean hasGlobalFillItem = cfg.contains("global.fill-item");
        
        Material fillMaterial = Material.GRAY_STAINED_GLASS_PANE;
        String fillName = " ";
        
        // 1. Try local fill-item first
        if (hasLocalFillItem && cfg.contains(fillSection + ".material")) {
            fillMaterial = parseMaterial(cfg.getString(fillSection + ".material"), fillMaterial);
        }
        // 2. If no local, try global fill-item
        else if (hasGlobalFillItem && cfg.contains("global.fill-item.material")) {
            fillMaterial = parseMaterial(cfg.getString("global.fill-item.material"), fillMaterial);
        }
        
        // 1. Try local fill-item name first
        if (hasLocalFillItem && cfg.contains(fillSection + ".name")) {
            fillName = cfg.getString(fillSection + ".name");
        }
        // 2. If no local, try global fill-item name
        else if (hasGlobalFillItem && cfg.contains("global.fill-item.name")) {
            fillName = cfg.getString("global.fill-item.name");
        }
        
        GuiItem fillItem = new GuiItem(-1, fillMaterial, fillName, List.of());
        
        return new GuiSettings(title, size, items, fillItem);
    }
    
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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
        items.put("blacklist", new GuiItem(34, Material.IRON_BARS, "<red>Blacklist", List.of("<gray>Spieler sperren")));
        return items;
    }

    private static Map<String, GuiItem> createDefaultBlacklistManagementItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("player-add", new GuiItem(11, Material.PLAYER_HEAD, "<red>Spieler sperren", List.of("<gray>Drohnen von Spielern blockieren")));
        items.put("player-remove", new GuiItem(15, Material.BARRIER, "<green>Sperre aufheben", List.of("<gray>Spieler entsperren")));
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
        items.put("rename", new GuiItem(10, Material.NAME_TAG, "<yellow>Umbenennen", List.of("<gray>Klicke um den Namen zu ändern")));
        items.put("relocate", new GuiItem(12, Material.COMPASS, "<yellow>Neu setzen", List.of("<gray>Klicke um die Position zu aktualisieren")));
        items.put("trust-management", new GuiItem(14, Material.EMERALD, "<green>Vertrauen", List.of("<gray>Spieler vertrauen oder entfernen")));
        items.put("blacklist-management", new GuiItem(16, Material.IRON_BARS, "<red>Sperre", List.of("<gray>Spieler sperren oder entsperren")));
        items.put("delete", new GuiItem(22, Material.BARRIER, "<red>Löschen", List.of("<gray>Klicke um diesen Socket zu entfernen")));
        items.put("back", new GuiItem(18, Material.ARROW, "<purple>Zurück", List.of("<gray>Zurück zur Verwaltung")));
        return items;
    }

    private static Map<String, GuiItem> createDefaultSocketTrustMenuItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("trust", new GuiItem(11, Material.EMERALD, "<green>Spieler vertrauen", List.of("<gray>Spieler zu diesem Socket hinzufügen")));
        items.put("untrust", new GuiItem(15, Material.REDSTONE, "<red>Vertrauen entfernen", List.of("<gray>Vertrauen eines Spielers entfernen")));
        items.put("back", new GuiItem(18, Material.ARROW, "<purple>Zurück", List.of("<gray>Zurück zum Socket-Editor")));
        return items;
    }

    private static Map<String, GuiItem> createDefaultSocketBlacklistMenuItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("blacklist-add", new GuiItem(11, Material.IRON_BARS, "<red>Spieler sperren", List.of("<gray>Spieler für diesen Socket sperren")));
        items.put("blacklist-remove", new GuiItem(15, Material.BARRIER, "<green>Sperre aufheben", List.of("<gray>Socket-Sperre entfernen")));
        items.put("back", new GuiItem(18, Material.ARROW, "<purple>Zurück", List.of("<gray>Zurück zum Socket-Editor")));
        return items;
    }
}
