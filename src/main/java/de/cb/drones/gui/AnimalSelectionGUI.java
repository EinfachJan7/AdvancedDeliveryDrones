package de.cb.drones.gui;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.command.DroneCommand.ComposeHubInventoryHolder;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.GuiItem;
import de.cb.drones.drone.GuiSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnimalSelectionGUI implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final Player sender;
    private final ComposeHubInventoryHolder composeHolder;

    private final NamespacedKey animalUuidKey;
    private final Map<UUID, Boolean> selectedAnimals = new HashMap<>();
    private final List<LivingEntity> availableAnimals = new ArrayList<>();
    private int currentPage = 0;
    
    private boolean isTransitioning = false; // Prevents triggering the close event when explicitly transitioning

    public AnimalSelectionGUI(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, Player sender, ComposeHubInventoryHolder composeHolder) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.sender = sender;
        this.composeHolder = composeHolder;
        this.animalUuidKey = new NamespacedKey(plugin, "animal_uuid");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        if (composeHolder.selectedAnimalIds() != null) {
            for (UUID id : composeHolder.selectedAnimalIds()) {
                selectedAnimals.put(id, true);
            }
        }
        
        loadAvailableAnimals();
    }

    private void loadAvailableAnimals() {
        double radius = droneManager.settings().animalSelectionRadius();
        boolean leashableOnly = droneManager.settings().animalSelectionLeashableOnly();
        
        for (Entity entity : sender.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity instanceof Player || entity.isDead()) {
                continue;
            }
            if (leashableOnly) {
                boolean isLeashable = living instanceof org.bukkit.entity.Animals ||
                                      living instanceof org.bukkit.entity.WaterMob ||
                                      living instanceof org.bukkit.entity.Golem ||
                                      living instanceof org.bukkit.entity.WanderingTrader;
                if (!isLeashable) {
                    continue;
                }
            }
            availableAnimals.add(living);
        }
    }

    public void openSelectionMenu() {
        GuiSettings settings = droneManager.settings().guiConfig().animalSelection();
        int size = settings.size();
        
        Inventory menu = Bukkit.createInventory(new AnimalSelectionHolder("selection"), size, MINI_MESSAGE.deserialize(settings.title()));
        
        // Fill item
        if (settings.fillItem() != null) {
            ItemStack filler = GuiItemStacks.create(settings.fillItem());
            for (int i = 0; i < size; i++) {
                menu.setItem(i, filler);
            }
        }

        List<Integer> availableSlots = getAvailableSlots(settings, size);
        int maxPerPage = availableSlots.size();
        if (maxPerPage <= 0) maxPerPage = 1;
        
        int totalPages = (int) Math.ceil((double) availableAnimals.size() / maxPerPage);
        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1;
        }

        int startIndex = currentPage * maxPerPage;
        for (int i = 0; i < maxPerPage; i++) {
            int dataIndex = startIndex + i;
            int slot = availableSlots.get(i);
            if (dataIndex < availableAnimals.size()) {
                LivingEntity animal = availableAnimals.get(dataIndex);
                boolean isSelected = selectedAnimals.getOrDefault(animal.getUniqueId(), false);
                menu.setItem(slot, createAnimalItem(animal, isSelected));
            } else {
                menu.setItem(slot, settings.fillItem() != null ? GuiItemStacks.create(settings.fillItem()) : new ItemStack(Material.AIR));
            }
        }

        GuiItem backItem = settings.items().get("back");
        if (backItem != null && backItem.position() >= 0 && backItem.position() < size) {
            menu.setItem(backItem.position(), GuiItemStacks.create(backItem));
        }

        GuiItem prevPage = settings.items().get("previous-page");
        if (currentPage > 0 && prevPage != null && prevPage.position() >= 0 && prevPage.position() < size) {
            menu.setItem(prevPage.position(), GuiItemStacks.create(prevPage));
        }

        GuiItem nextPage = settings.items().get("next-page");
        if (currentPage < totalPages - 1 && nextPage != null && nextPage.position() >= 0 && nextPage.position() < size) {
            menu.setItem(nextPage.position(), GuiItemStacks.create(nextPage));
        }

        isTransitioning = true;
        sender.openInventory(menu);
        isTransitioning = false;
    }
    


    private int getSelectedCount() {
        int count = 0;
        for (Boolean selected : selectedAnimals.values()) {
            if (selected) count++;
        }
        return count;
    }

    private ItemStack createAnimalItem(LivingEntity animal, boolean isSelected) {
        // Find best material representing the mob
        Material mat;
        try {
            mat = Material.valueOf(animal.getType().name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException e) {
            mat = Material.PORKCHOP;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String typeName = animal.getType().name().replace("_", " ");
            String nameFormat = isSelected 
                ? droneManager.settings().guiConfig().animalSelectionItemSelectedNameFormat() 
                : droneManager.settings().guiConfig().animalSelectionItemNameFormat();
            meta.displayName(MINI_MESSAGE.deserialize(nameFormat.replace("<type>", typeName)));

            List<String> loreFormat = isSelected
                ? droneManager.settings().guiConfig().animalSelectionItemSelectedLore()
                : droneManager.settings().guiConfig().animalSelectionItemLore();
            
            String customName = animal.customName() != null ? PlainTextComponentSerializer.plainText().serialize(animal.customName()) : null;
            String profession = animal instanceof org.bukkit.entity.Villager v ? v.getProfession().name() : null;
            String age = animal instanceof org.bukkit.entity.Ageable a ? (a.isAdult() ? "Adult" : "Baby") : null;
            String owner = animal instanceof org.bukkit.entity.Tameable t && t.isTamed() && t.getOwner() != null && t.getOwner().getName() != null ? t.getOwner().getName() : null;

            List<Component> lore = new ArrayList<>();
            for (String line : loreFormat) {
                if (line.contains("<custom-name>") && customName == null) continue;
                if (line.contains("<profession>") && profession == null) continue;
                if (line.contains("<age>") && age == null) continue;
                if (line.contains("<owner>") && owner == null) continue;

                String replaced = line.replace("<type>", typeName);
                if (customName != null) replaced = replaced.replace("<custom-name>", customName);
                if (profession != null) replaced = replaced.replace("<profession>", profession);
                if (age != null) replaced = replaced.replace("<age>", age);
                if (owner != null) replaced = replaced.replace("<owner>", owner);
                
                lore.add(MINI_MESSAGE.deserialize(replaced));
            }
            meta.lore(lore);

            if (isSelected) {
                try {
                    meta.setEnchantmentGlintOverride(true);
                } catch (NoSuchMethodError e) {
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    org.bukkit.enchantments.Enchantment ench = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
                    if (ench != null) {
                        meta.addEnchant(ench, 1, true);
                    }
                }
            }

            meta.getPersistentDataContainer().set(animalUuidKey, PersistentDataType.STRING, animal.getUniqueId().toString());
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Integer> getAvailableSlots(GuiSettings menuSettings, int size) {
        List<Integer> slots = menuSettings.contentSlots();
        if (slots != null && !slots.isEmpty()) {
            return slots;
        }
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            final int currentSlot = i;
            boolean isFixed = menuSettings.items().values().stream()
                    .anyMatch(item -> item.position() == currentSlot);
            if (!isFixed) {
                available.add(i);
            }
        }
        return available;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AnimalSelectionHolder holder)) {
            return;
        }
        if (!event.getWhoClicked().getUniqueId().equals(sender.getUniqueId())) {
            return;
        }
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        int slot = event.getSlot();

        if (holder.getMenuType().equals("selection")) {
            GuiSettings settings = droneManager.settings().guiConfig().animalSelection();
            GuiItem backItem = settings.items().get("back");
            if (backItem != null && slot == backItem.position()) {
                handleSelectionFinished();
                return;
            }

            GuiItem prevPage = settings.items().get("previous-page");
            if (prevPage != null && slot == prevPage.position()) {
                if (currentPage > 0) {
                    currentPage--;
                    openSelectionMenu();
                }
                return;
            }

            GuiItem nextPage = settings.items().get("next-page");
            if (nextPage != null && slot == nextPage.position()) {
                List<Integer> availableSlots = getAvailableSlots(settings, settings.size());
                int maxPerPage = availableSlots.size();
                int totalPages = (int) Math.ceil((double) availableAnimals.size() / maxPerPage);
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    openSelectionMenu();
                }
                return;
            }

            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(animalUuidKey, PersistentDataType.STRING)) {
                String uuidStr = meta.getPersistentDataContainer().get(animalUuidKey, PersistentDataType.STRING);
                if (uuidStr != null) {
                    UUID animalId = UUID.fromString(uuidStr);
                    boolean current = selectedAnimals.getOrDefault(animalId, false);
                    
                    if (!current) {
                        int maxAnimals = droneManager.maxLeashedAnimalsFor(sender);
                        if (maxAnimals > 0 && getSelectedCount() >= maxAnimals) {
                            droneManager.sendMessage(sender, "too-many-leashed-animals", "<max>", String.valueOf(maxAnimals));
                            return;
                        }
                    }
                    
                    selectedAnimals.put(animalId, !current);
                    
                    // Re-render item
                    LivingEntity target = null;
                    for (LivingEntity a : availableAnimals) {
                        if (a.getUniqueId().equals(animalId)) {
                            target = a;
                            break;
                        }
                    }
                    if (target != null) {
                        event.getInventory().setItem(slot, createAnimalItem(target, !current));
                    }
                }
            }
        }
    }
    
    private void handleSelectionFinished() {
        List<UUID> finalSelection = new ArrayList<>();
        for (Map.Entry<UUID, Boolean> entry : selectedAnimals.entrySet()) {
            if (entry.getValue()) {
                finalSelection.add(entry.getKey());
            }
        }
        
        InventoryDragEvent.getHandlerList().unregister(this);
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        
        isTransitioning = true;
        sender.closeInventory();
        isTransitioning = false;
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getDroneCommand().finishAnimalSelectionLaunch(sender, composeHolder, finalSelection);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AnimalSelectionHolder) {
            if (event.getWhoClicked().getUniqueId().equals(sender.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AnimalSelectionHolder holder) {
            if (!event.getPlayer().getUniqueId().equals(sender.getUniqueId())) return;
            if (isTransitioning) return;
            
            if (holder.getMenuType().equals("selection")) {
                handleSelectionFinished();
            }
        }
    }
    
    private void cleanupAndClose() {
        InventoryDragEvent.getHandlerList().unregister(this);
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        
        isTransitioning = true;
        sender.closeInventory();
        isTransitioning = false;
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getDroneCommand().reopenComposeHub(sender, composeHolder);
        });
    }

    public static class AnimalSelectionHolder implements InventoryHolder {
        private final String menuType;
        
        public AnimalSelectionHolder(String menuType) {
            this.menuType = menuType;
        }
        
        public String getMenuType() {
            return menuType;
        }

        @Override
        public Inventory getInventory() {
            return null; // Used for holder check
        }
    }
}
