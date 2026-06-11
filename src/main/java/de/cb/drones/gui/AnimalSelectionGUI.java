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
        List<String> blacklist = droneManager.settings().mobSendingBlacklist();
        
        for (Entity entity : sender.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity instanceof Player || entity.isDead()) {
                continue;
            }
            // Skip blacklisted mob types
            if (blacklist.contains(entity.getType().name().toUpperCase())) {
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
            String health = String.format("%.1f", animal.getHealth());
            String maxHealth = "";
            try {
                maxHealth = String.format("%.1f", animal.getAttribute(org.bukkit.attribute.Attribute.valueOf("GENERIC_MAX_HEALTH")).getValue());
            } catch (Exception e) {
                try {
                    maxHealth = String.format("%.1f", animal.getAttribute(org.bukkit.attribute.Attribute.valueOf("MAX_HEALTH")).getValue());
                } catch (Exception e2) {
                    maxHealth = String.format("%.1f", 20.0);
                }
            }
            String color = null;
            if (animal instanceof org.bukkit.entity.Sheep s && s.getColor() != null) color = s.getColor().name();
            else if (animal instanceof org.bukkit.entity.Wolf w && w.getCollarColor() != null) color = w.getCollarColor().name();
            else if (animal instanceof org.bukkit.entity.Cat c && c.getCollarColor() != null) color = c.getCollarColor().name();

            String variant = null;
            if (animal instanceof org.bukkit.entity.Axolotl ax) variant = ax.getVariant().name();
            else if (animal instanceof org.bukkit.entity.Fox f) variant = f.getFoxType().name();
            else if (animal instanceof org.bukkit.entity.Frog fr) variant = fr.getVariant().name();
            else if (animal instanceof org.bukkit.entity.Llama l) variant = l.getColor().name();
            else if (animal instanceof org.bukkit.entity.MushroomCow mc) variant = mc.getVariant().name();
            else if (animal instanceof org.bukkit.entity.Parrot p) variant = p.getVariant().name();
            else if (animal instanceof org.bukkit.entity.Rabbit r) variant = r.getRabbitType().name();
            else if (animal instanceof org.bukkit.entity.Horse h) variant = h.getColor().name() + " " + h.getStyle().name();

            String speed = "";
            try {
                speed = String.format("%.2f", animal.getAttribute(org.bukkit.attribute.Attribute.valueOf("GENERIC_MOVEMENT_SPEED")).getValue());
            } catch (Exception e) {
                try {
                    speed = String.format("%.2f", animal.getAttribute(org.bukkit.attribute.Attribute.valueOf("MOVEMENT_SPEED")).getValue());
                } catch (Exception e2) {
                    speed = "0.2";
                }
            }

            String jump = null;
            if (animal instanceof org.bukkit.entity.AbstractHorse) {
                try {
                    jump = String.format("%.2f", animal.getAttribute(org.bukkit.attribute.Attribute.valueOf("GENERIC_JUMP_STRENGTH")).getValue());
                } catch (Exception e) {
                    try {
                        jump = String.format("%.2f", animal.getAttribute(org.bukkit.attribute.Attribute.valueOf("HORSE_JUMP_STRENGTH")).getValue());
                    } catch (Exception e2) {}
                }
            }
            
            String size = animal instanceof org.bukkit.entity.Slime s ? String.valueOf(s.getSize()) : null;
            String sheared = animal instanceof org.bukkit.entity.Sheep s && s.isSheared() ? "Yes" : null;
            String sitting = animal instanceof org.bukkit.entity.Sittable s && s.isSitting() ? "Yes" : null;
            String anger = animal instanceof org.bukkit.entity.Bee b && b.getAnger() > 0 ? "Angry" : (animal instanceof org.bukkit.entity.Wolf w && w.isAngry() ? "Angry" : null);

            String saddled = null;
            if (animal instanceof org.bukkit.entity.Pig p) saddled = p.hasSaddle() ? "Yes" : null;
            else if (animal instanceof org.bukkit.entity.AbstractHorse h) saddled = h.getInventory().getSaddle() != null ? "Yes" : null;
            else if (animal instanceof org.bukkit.entity.Strider st) saddled = st.hasSaddle() ? "Yes" : null;

            String pandaMainGene = animal instanceof org.bukkit.entity.Panda p ? p.getMainGene().name() : null;
            String pandaHiddenGene = animal instanceof org.bukkit.entity.Panda p ? p.getHiddenGene().name() : null;
            String hasEgg = animal instanceof org.bukkit.entity.Turtle t && t.hasEgg() ? "Yes" : null;
            String carriedBlock = animal instanceof org.bukkit.entity.Enderman e && e.getCarriedBlock() != null ? e.getCarriedBlock().getMaterial().name() : null;
            String playerCreated = animal instanceof org.bukkit.entity.IronGolem ig && ig.isPlayerCreated() ? "Yes" : null;
            String derp = animal instanceof org.bukkit.entity.Snowman sm && sm.isDerp() ? "Yes" : null;
            String leftHorn = animal instanceof org.bukkit.entity.Goat g && g.hasLeftHorn() ? "Yes" : null;
            String rightHorn = animal instanceof org.bukkit.entity.Goat g && g.hasRightHorn() ? "Yes" : null;
            String tropicalPattern = animal instanceof org.bukkit.entity.TropicalFish tf ? tf.getPattern().name() : null;
            String tropicalBodyColor = animal instanceof org.bukkit.entity.TropicalFish tf ? tf.getBodyColor().name() : null;
            String tropicalPatternColor = animal instanceof org.bukkit.entity.TropicalFish tf ? tf.getPatternColor().name() : null;
            String powered = animal instanceof org.bukkit.entity.Creeper c && c.isPowered() ? "Yes" : null;
            String awake = animal instanceof org.bukkit.entity.Bat b && b.isAwake() ? "Yes" : null;
            String trusting = animal instanceof org.bukkit.entity.Ocelot o && o.isTrusting() ? "Yes" : null;
            String puffState = animal instanceof org.bukkit.entity.PufferFish pf ? String.valueOf(pf.getPuffState()) : null;
            String zombieBaby = animal instanceof org.bukkit.entity.Zombie z && z.isBaby() ? "Yes" : null;
            String villagerType = animal instanceof org.bukkit.entity.Villager v ? v.getVillagerType().name() : (animal instanceof org.bukkit.entity.ZombieVillager zv ? zv.getVillagerType().name() : null);
            String canBreed = animal instanceof org.bukkit.entity.Breedable br && br.canBreed() ? "Yes" : null;
            String crouching = animal instanceof org.bukkit.entity.Fox f && f.isCrouching() ? "Yes" : null;
            String sleeping = animal instanceof org.bukkit.entity.Fox f && f.isSleeping() ? "Yes" : null;
            String domestication = animal instanceof org.bukkit.entity.AbstractHorse ah ? String.valueOf(ah.getDomestication()) : null;
            String maxDomestication = animal instanceof org.bukkit.entity.AbstractHorse ah ? String.valueOf(ah.getMaxDomestication()) : null;
            String angerLevel = null;
            try {
                if (animal instanceof org.bukkit.entity.Warden w) angerLevel = w.getAngerLevel().name();
            } catch (NoClassDefFoundError | NoSuchMethodError e) {
                // Ignore for older versions
            }
            String immuneToZombification = animal instanceof org.bukkit.entity.PiglinAbstract p && p.isImmuneToZombification() ? "Yes" : null;
            String patrolLeader = animal instanceof org.bukkit.entity.Raider r && r.isPatrolLeader() ? "Yes" : null;
            String canDuplicate = null;
            try { if (animal instanceof org.bukkit.entity.Allay a && a.canDuplicate()) canDuplicate = "Yes"; } catch (NoClassDefFoundError | NoSuchMethodError e) {}
            String snifferState = null;
            try { if (animal instanceof org.bukkit.entity.Sniffer s) snifferState = s.getState().name(); } catch (NoClassDefFoundError | NoSuchMethodError e) {}
            String shivering = animal instanceof org.bukkit.entity.Strider s && s.isShivering() ? "Yes" : null;
            String drinkingPotion = animal instanceof org.bukkit.entity.Witch w && w.isDrinkingPotion() ? "Yes" : null;
            String charged = animal instanceof org.bukkit.entity.Wither w && w.isCharged() ? "Yes" : null;
            String dragonPhase = animal instanceof org.bukkit.entity.EnderDragon ed ? ed.getPhase().name() : null;
            String ignited = animal instanceof org.bukkit.entity.Creeper c && c.isIgnited() ? "Yes" : null;
            String villagerLevel = animal instanceof org.bukkit.entity.Villager v ? String.valueOf(v.getVillagerLevel()) : null;
            String villagerExperience = animal instanceof org.bukkit.entity.Villager v ? String.valueOf(v.getVillagerExperience()) : null;

            List<Component> lore = new ArrayList<>();
            for (String line : loreFormat) {
                if (line.contains("<custom-name>") && customName == null) continue;
                if (line.contains("<profession>") && profession == null) continue;
                if (line.contains("<age>") && age == null) continue;
                if (line.contains("<owner>") && owner == null) continue;
                if (line.contains("<color>") && color == null) continue;
                if (line.contains("<variant>") && variant == null) continue;
                if (line.contains("<speed>") && speed == null) continue;
                if (line.contains("<jump>") && jump == null) continue;
                if (line.contains("<size>") && size == null) continue;
                if (line.contains("<sheared>") && sheared == null) continue;
                if (line.contains("<sitting>") && sitting == null) continue;
                if (line.contains("<anger>") && anger == null) continue;
                if (line.contains("<saddled>") && saddled == null) continue;
                if (line.contains("<panda-main-gene>") && pandaMainGene == null) continue;
                if (line.contains("<panda-hidden-gene>") && pandaHiddenGene == null) continue;
                if (line.contains("<has-egg>") && hasEgg == null) continue;
                if (line.contains("<carried-block>") && carriedBlock == null) continue;
                if (line.contains("<player-created>") && playerCreated == null) continue;
                if (line.contains("<derp>") && derp == null) continue;
                if (line.contains("<left-horn>") && leftHorn == null) continue;
                if (line.contains("<right-horn>") && rightHorn == null) continue;
                if (line.contains("<tropical-pattern>") && tropicalPattern == null) continue;
                if (line.contains("<tropical-body-color>") && tropicalBodyColor == null) continue;
                if (line.contains("<tropical-pattern-color>") && tropicalPatternColor == null) continue;
                if (line.contains("<powered>") && powered == null) continue;
                if (line.contains("<awake>") && awake == null) continue;
                if (line.contains("<trusting>") && trusting == null) continue;
                if (line.contains("<puff-state>") && puffState == null) continue;
                if (line.contains("<zombie-baby>") && zombieBaby == null) continue;
                if (line.contains("<villager-type>") && villagerType == null) continue;
                if (line.contains("<can-breed>") && canBreed == null) continue;
                if (line.contains("<crouching>") && crouching == null) continue;
                if (line.contains("<sleeping>") && sleeping == null) continue;
                if (line.contains("<domestication>") && domestication == null) continue;
                if (line.contains("<max-domestication>") && maxDomestication == null) continue;
                if (line.contains("<anger-level>") && angerLevel == null) continue;
                if (line.contains("<immune-to-zombification>") && immuneToZombification == null) continue;
                if (line.contains("<patrol-leader>") && patrolLeader == null) continue;
                if (line.contains("<can-duplicate>") && canDuplicate == null) continue;
                if (line.contains("<sniffer-state>") && snifferState == null) continue;
                if (line.contains("<shivering>") && shivering == null) continue;
                if (line.contains("<drinking-potion>") && drinkingPotion == null) continue;
                if (line.contains("<charged>") && charged == null) continue;
                if (line.contains("<dragon-phase>") && dragonPhase == null) continue;
                if (line.contains("<ignited>") && ignited == null) continue;
                if (line.contains("<villager-level>") && villagerLevel == null) continue;
                if (line.contains("<villager-experience>") && villagerExperience == null) continue;

                String replaced = line.replace("<type>", typeName);
                if (customName != null) replaced = replaced.replace("<custom-name>", customName);
                if (profession != null) replaced = replaced.replace("<profession>", profession);
                if (age != null) replaced = replaced.replace("<age>", age);
                if (owner != null) replaced = replaced.replace("<owner>", owner);
                if (health != null) replaced = replaced.replace("<health>", health);
                if (maxHealth != null) replaced = replaced.replace("<max-health>", maxHealth);
                if (color != null) replaced = replaced.replace("<color>", color);
                if (variant != null) replaced = replaced.replace("<variant>", variant);
                if (speed != null) replaced = replaced.replace("<speed>", speed);
                if (jump != null) replaced = replaced.replace("<jump>", jump);
                if (size != null) replaced = replaced.replace("<size>", size);
                if (sheared != null) replaced = replaced.replace("<sheared>", sheared);
                if (sitting != null) replaced = replaced.replace("<sitting>", sitting);
                if (anger != null) replaced = replaced.replace("<anger>", anger);
                if (saddled != null) replaced = replaced.replace("<saddled>", saddled);
                if (pandaMainGene != null) replaced = replaced.replace("<panda-main-gene>", pandaMainGene);
                if (pandaHiddenGene != null) replaced = replaced.replace("<panda-hidden-gene>", pandaHiddenGene);
                if (hasEgg != null) replaced = replaced.replace("<has-egg>", hasEgg);
                if (carriedBlock != null) replaced = replaced.replace("<carried-block>", carriedBlock);
                if (playerCreated != null) replaced = replaced.replace("<player-created>", playerCreated);
                if (derp != null) replaced = replaced.replace("<derp>", derp);
                if (leftHorn != null) replaced = replaced.replace("<left-horn>", leftHorn);
                if (rightHorn != null) replaced = replaced.replace("<right-horn>", rightHorn);
                if (tropicalPattern != null) replaced = replaced.replace("<tropical-pattern>", tropicalPattern);
                if (tropicalBodyColor != null) replaced = replaced.replace("<tropical-body-color>", tropicalBodyColor);
                if (tropicalPatternColor != null) replaced = replaced.replace("<tropical-pattern-color>", tropicalPatternColor);
                if (powered != null) replaced = replaced.replace("<powered>", powered);
                if (awake != null) replaced = replaced.replace("<awake>", awake);
                if (trusting != null) replaced = replaced.replace("<trusting>", trusting);
                if (puffState != null) replaced = replaced.replace("<puff-state>", puffState);
                if (zombieBaby != null) replaced = replaced.replace("<zombie-baby>", zombieBaby);
                if (villagerType != null) replaced = replaced.replace("<villager-type>", villagerType);
                if (canBreed != null) replaced = replaced.replace("<can-breed>", canBreed);
                if (crouching != null) replaced = replaced.replace("<crouching>", crouching);
                if (sleeping != null) replaced = replaced.replace("<sleeping>", sleeping);
                if (domestication != null) replaced = replaced.replace("<domestication>", domestication);
                if (maxDomestication != null) replaced = replaced.replace("<max-domestication>", maxDomestication);
                if (angerLevel != null) replaced = replaced.replace("<anger-level>", angerLevel);
                if (immuneToZombification != null) replaced = replaced.replace("<immune-to-zombification>", immuneToZombification);
                if (patrolLeader != null) replaced = replaced.replace("<patrol-leader>", patrolLeader);
                if (canDuplicate != null) replaced = replaced.replace("<can-duplicate>", canDuplicate);
                if (snifferState != null) replaced = replaced.replace("<sniffer-state>", snifferState);
                if (shivering != null) replaced = replaced.replace("<shivering>", shivering);
                if (drinkingPotion != null) replaced = replaced.replace("<drinking-potion>", drinkingPotion);
                if (charged != null) replaced = replaced.replace("<charged>", charged);
                if (dragonPhase != null) replaced = replaced.replace("<dragon-phase>", dragonPhase);
                if (ignited != null) replaced = replaced.replace("<ignited>", ignited);
                if (villagerLevel != null) replaced = replaced.replace("<villager-level>", villagerLevel);
                if (villagerExperience != null) replaced = replaced.replace("<villager-experience>", villagerExperience);
                
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
