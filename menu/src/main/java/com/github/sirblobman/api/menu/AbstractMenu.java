package com.github.sirblobman.api.menu;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import com.github.sirblobman.api.item.ItemBuilder;
import com.github.sirblobman.api.item.SkullBuilder;
import com.github.sirblobman.api.menu.button.AbstractButton;
import com.github.sirblobman.api.nms.HeadHandler;
import com.github.sirblobman.api.utility.MessageUtility;
import com.github.sirblobman.api.utility.Validate;

import com.cryptomorin.xseries.XMaterial;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractMenu implements InventoryHolder, Listener {
    private final JavaPlugin plugin;
    private final Player player;
    private final Map<Integer, AbstractButton> buttonMap;

    public AbstractMenu(JavaPlugin plugin, Player player) {
        this.plugin = Validate.notNull(plugin, "plugin must not be null!");
        this.player = Validate.notNull(player, "player must not be null!");
        if (!this.player.isOnline()) {
            throw new IllegalArgumentException("player must be online!");
        }

        this.buttonMap = new ConcurrentHashMap<>();
    }

    /**
     * Override this to create a custom inventory.
     * Buttons and slots may not be set properly when using custom methods.
     *
     * @return A filled {@link Inventory} instance with this menu instance as the holder.
     */
    @Override
    public Inventory getInventory() {
        int size = getSize();
        String title = getTitle();
        Inventory inventory = getInventory(size, title);

        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = getItem(slot);
            contents[slot] = item;

            AbstractButton button = getButton(slot);
            setButton(slot, button);
        }

        inventory.setContents(contents);
        return inventory;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public final void onClick(InventoryClickEvent e) {
        Inventory inventory = e.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (!this.equals(holder)) {
            return;
        }

        Inventory clickedInventory = e.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        ClickType clickType = e.getClick();
        InventoryType clickedInventoryType = clickedInventory.getType();
        if (clickedInventoryType == InventoryType.PLAYER && clickType.isShiftClick()
                && shouldCancelShiftClickFromPlayerInventory()) {
            e.setCancelled(true);
            return;
        }

        int slot = e.getSlot();
        if (shouldPreventClick(slot)) {
            e.setCancelled(true);
        }

        int rawSlot = e.getRawSlot();
        AbstractButton button = internalGetButton(rawSlot);
        if (button != null) {
            button.onClick(e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onClose(InventoryCloseEvent e) {
        Inventory inventory = e.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (!this.equals(holder)) {
            return;
        }

        onCustomClose(e);
        HandlerList.unregisterAll(this);
    }

    /**
     * @param size  The size of the inventory. Must be five for a hopper menu or a non-zero multiple of
     *              nine for a chest menu.
     * @param title The title of the GUI.
     *              (legacy color codes with the '&amp;' symbol will be translated automatically)
     * @return An empty {@link Inventory} instance with this menu instance as its holder.
     */
    public final Inventory getInventory(int size, String title) {
        String realTitle = MessageUtility.color(title);

        if (size == 5) {
            return Bukkit.createInventory(this, InventoryType.HOPPER, realTitle);
        }

        if (size < 9) {
            throw new IllegalArgumentException("size must be equal to 5 or at least 9");
        }

        if (size > 54) {
            throw new IllegalArgumentException("size cannot be more than 54");
        }

        if (size % 9 != 0) {
            throw new IllegalArgumentException("size must be equal to 5 or divisible by 9");
        }

        return Bukkit.createInventory(this, size, realTitle);
    }

    /**
     * @return The plugin linked to the listeners and tasks for this menu.
     */
    public final JavaPlugin getPlugin() {
        return this.plugin;
    }

    /**
     * @return The player that will open this menu.
     */
    public final Player getPlayer() {
        return this.player;
    }

    /**
     * Use this method to open the menu for the current player.
     */
    public void open() {
        JavaPlugin plugin = getPlugin();
        Player player = getPlayer();
        player.closeInventory();

        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.scheduleSyncDelayedTask(plugin, this::internalOpen);
    }

    /**
     * @return The head handler for the current Bukkit version.
     */
    public HeadHandler getHeadHandler() {
        return null;
    }

    /**
     * @return {@code true} if the event should be cancelled, otherwise {@code false}.
     */
    public boolean shouldCancelShiftClickFromPlayerInventory() {
        return true;
    }

    /**
     * Set a button for this menu.
     *
     * @param slot   The slot to contain the button.
     * @param button The button instance. Use {@code null} to remove the current button.
     */
    protected final void setButton(int slot, AbstractButton button) {
        if (button == null) {
            this.buttonMap.remove(slot);
            return;
        }

        this.buttonMap.put(slot, button);
    }

    /**
     * Load an ItemStack from a configuration file or section.
     *
     * @param config The configuration or section to load the item from.
     * @param path   The path in the configuration or section.
     * @return An {@link ItemStack} read from the section, or {@code null} if one could not be read.
     */
    @Nullable
    protected final ItemStack loadItemStack(ConfigurationSection config, String path) {
        if (config.isItemStack(path)) {
            return config.getItemStack(path);
        }

        ConfigurationSection section = config.getConfigurationSection(path);
        return loadItemStack(section);
    }

    private ItemStack loadItemStack(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String materialName = section.getString("material");
        Optional<XMaterial> xMaterial = XMaterial.matchXMaterial(materialName);
        if (!xMaterial.isPresent()) {
            Logger logger = this.plugin.getLogger();
            logger.warning("Unknown material name '" + materialName + "'.");
            return null;
        }

        XMaterial realMaterial = xMaterial.get();
        ItemBuilder builder = new ItemBuilder(realMaterial);
        builder = checkSkull(builder, realMaterial, section);

        int amount = section.getInt("quantity", 1);
        builder.withAmount(amount);

        int damage = section.getInt("damage", 0);
        builder.withDamage(damage);

        Integer model = section.isSet("model") ? section.getInt("model") : null;
        builder.withModel(model);

        String displayName = section.getString("display-name");
        if (displayName != null) {
            String displayNameColored = MessageUtility.color(displayName);
            builder.withName(displayNameColored);
        }

        List<String> loreList = section.getStringList("lore");
        if (!loreList.isEmpty()) {
            List<String> loreListColored = MessageUtility.colorList(loreList);
            builder.withLore(loreListColored);
        }

        if (section.getBoolean("glowing")) builder.withGlowing();
        return builder.build();
    }

    private ItemBuilder checkSkull(ItemBuilder builder, XMaterial xMaterial, ConfigurationSection section) {
        HeadHandler headHandler = getHeadHandler();
        if (xMaterial != XMaterial.PLAYER_HEAD || headHandler == null) {
            return builder;
        }

        String texture = section.getString("texture");
        if (texture != null) {
            return SkullBuilder.withTexture(headHandler, texture);
        }

        String username = section.getString("skull-owner");
        if (username != null) {
            return SkullBuilder.withOwner(headHandler, username);
        }

        return builder;
    }

    private void internalOpen() {
        this.buttonMap.clear();
        JavaPlugin plugin = getPlugin();
        Inventory inventory = getInventory();

        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(this, plugin);

        Player player = getPlayer();
        player.openInventory(inventory);
    }

    private AbstractButton internalGetButton(int slot) {
        return this.buttonMap.getOrDefault(slot, null);
    }

    /**
     * @return The size for the GUI (usually 54)
     */
    public abstract int getSize();

    /**
     * @return The title for the menu (legacy color codes with the '&amp;' symbol will be translated automatically)
     */
    public abstract String getTitle();

    /**
     * Get an ItemStack for a specific slot.
     *
     * @param slot The slot that will contain the item stack.
     * @return An {@link ItemStack} or {@code null}
     */
    @Nullable
    public abstract ItemStack getItem(int slot);

    /**
     * Get a button for a specific slot.
     *
     * @param slot The slot that will contain the button.
     * @return A custom button or action that will be triggered when the slot is clicked.
     * You can also return null if you do not want an action.
     */
    @Nullable
    public abstract AbstractButton getButton(int slot);

    /**
     * Override this method to change whether a click is prevented or not.
     *
     * @param slot The slot that was clicked.
     * @return {@code true} to cancel the click, {@code false} to allow the click.
     */
    public abstract boolean shouldPreventClick(int slot);

    /**
     * Override this method to use a custom close action.
     *
     * @param e the InventoryCloseEvent of the action.
     */
    public void onCustomClose(InventoryCloseEvent e) {
        // Do Nothing
    }
}
