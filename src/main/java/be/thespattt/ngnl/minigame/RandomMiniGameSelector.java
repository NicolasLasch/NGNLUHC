package be.thespattt.ngnl.minigame;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RandomMiniGameSelector implements Listener {
    private final NoGameNoLife plugin;
    private final Map<UUID, UUID> playerSelections = new HashMap<>(); // killer UUID -> victim UUID
    private final Map<UUID, MiniGameType> selectedGames = new HashMap<>(); // killer UUID -> selected game
    private final Map<UUID, Boolean> canReroll = new HashMap<>(); // killer UUID -> can reroll status
    private final Map<UUID, Integer> animationTasks = new HashMap<>(); // Task IDs for animation runnables

    // Constants for GUI positions
    private static final int GUI_SIZE = 27; // 3 rows of 9
    private static final int REROLL_SLOT = 0; // Left side
    private static final int START_SLOT = 8; // Right side
    private static final int CENTER_SLOT = 13; // Center of the GUI

    private static final int[] ROULETTE_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    public RandomMiniGameSelector(NoGameNoLife plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the minigame selection GUI for a player.
     */
    public void openMiniGameSelectionGUI(Player killer, Player victim) {
        UUID killerUUID = killer.getUniqueId();

        // Store the victim and initialize reroll state
        playerSelections.put(killerUUID, victim.getUniqueId());
        canReroll.put(killerUUID, true);

        // Create the GUI
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.DARK_PURPLE + "Random Mini-Game Selector");

        // Fill the GUI with background items
        fillBackground(gui);

        // Add the reroll button (green)
        gui.setItem(REROLL_SLOT, createRerollButton(true));

        // Add the start button (disabled initially)
        gui.setItem(START_SLOT, createStartButton(false));

        // Open the GUI for the player
        killer.openInventory(gui);

        // Start the animation
        startRouletteAnimation(killer, gui);
    }

    /**
     * Fills the background of the GUI with decorative items.
     */
    private void fillBackground(Inventory gui) {
        ItemStack backgroundItem = createGuiItem(Material.WHITE_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < GUI_SIZE; i++) {
            // Skip roulette slots, reroll slot, and start slot
            if (Arrays.binarySearch(ROULETTE_SLOTS, i) >= 0 || i == REROLL_SLOT || i == START_SLOT) {
                continue;
            }

            if (i == CENTER_SLOT - 9 || i == CENTER_SLOT + 9) {
                gui.setItem(i, createGuiItem(Material.PURPLE_STAINED_GLASS_PANE, " "));
            } else {
                gui.setItem(i, backgroundItem);
            }
        }
    }

    /**
     * Creates the reroll button with appropriate state.
     */
    private ItemStack createRerollButton(boolean enabled) {
        if (enabled) {
            return createGuiItem(
                    Material.LIME_DYE,
                    ChatColor.GREEN + "Reroll Mini-Game",
                    ChatColor.GRAY + "Click to get a different mini-game",
                    ChatColor.YELLOW + "You can reroll once!"
            );
        } else {
            return createGuiItem(
                    Material.RED_DYE,
                    ChatColor.RED + "Reroll Used",
                    ChatColor.GRAY + "You've already used your reroll"
            );
        }
    }

    /**
     * Creates the start button with appropriate state.
     */
    private ItemStack createStartButton(boolean enabled) {
        if (enabled) {
            ItemStack item = createGuiItem(
                    Material.EMERALD,
                    ChatColor.GREEN + "Start Mini-Game",
                    ChatColor.GRAY + "Click to start the selected mini-game"
            );

            // Add glow effect to make it stand out
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }

            return item;
        } else {
            return createGuiItem(
                    Material.BARRIER,
                    ChatColor.RED + "Selecting Mini-Game...",
                    ChatColor.GRAY + "Please wait for the selection to complete"
            );
        }
    }

    /**
     * Creates a minigame item for the roulette.
     */
    private ItemStack createMiniGameItem(MiniGameType type, boolean selected) {
        Material material;

        // Get the description to show in the lore
        String description = type.getDescription();

        // Assign a unique material for each mini-game type
        switch (type) {
            case SPEED_BEDWARS:
                material = Material.RED_BED;
                break;
            case SPLEEF:
                material = Material.DIAMOND_SHOVEL;
                break;
            case TNT_RUN:
                material = Material.TNT;
                break;
            case PARKOUR:
                material = Material.FEATHER;
                break;
            case BLOC_PARTY:
                material = Material.NOTE_BLOCK;
                break;
            case SUMO:
                material = Material.SLIME_BLOCK;
                break;
            case SPLEGG:
                material = Material.EGG;
                break;
            case FLOOR_IS_LAVA:
                material = Material.LAVA_BUCKET;
                break;
            case DES_A_COUDRE:
                material = Material.WATER_BUCKET;
                break;
            case ANVIL_RAIN:
                material = Material.ANVIL;
                break;
            case WORD_CHAIN_BATTLE:
                material = Material.BOOK;
                break;
            case LOGICAL_DEDUCTION:
                material = Material.COMPASS;
                break;
            case MENTAL_CHESS:
                material = Material.CHEST;
                break;
            case MEMORY_GAME:
                material = Material.CLOCK;
                break;
            case MATERIALIZATION_SHIRITORI:
                material = Material.ENCHANTED_BOOK;
                break;
            default:
                material = Material.PAPER;
                break;
        }

        ItemStack item = createGuiItem(
                material,
                ChatColor.GOLD + type.getDisplayName(),
                ChatColor.GRAY + "Mini-game: " + ChatColor.DARK_PURPLE + type.name(),
                ChatColor.YELLOW + description,
                ChatColor.AQUA + "Challenge your opponent to this game!"
        );

        // Add enchantment glow if selected
        if (selected) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }

        return item;
    }

    /**
     * Helper method to create GUI items with a name and lore.
     */
    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);

            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }

            // Hide attributes like attack damage
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Starts the roulette animation in the GUI.
     */
    private void startRouletteAnimation(Player player, Inventory gui) {
        UUID playerUUID = player.getUniqueId();

        // Get all available mini-game types
        List<MiniGameType> miniGameTypes = new ArrayList<>(Arrays.asList(MiniGameType.values()));
        Collections.shuffle(miniGameTypes); // Randomize order

        // Start with a fast speed and slow down
        final int[] speed = {2}; // Ticks between updates
        final int[] iterations = {0};
        final int totalIterations = 30 + new Random().nextInt(10); // Random ending point

        // Cancel any existing animation for this player
        if (animationTasks.containsKey(playerUUID)) {
            Bukkit.getScheduler().cancelTask(animationTasks.get(playerUUID));
        }

        // Store animation task ID
        int taskId = new BukkitRunnable() {
            int currentIndex = 0;

            @Override
            public void run() {
                // Update all roulette slots
                for (int i = 0; i < ROULETTE_SLOTS.length; i++) {
                    int slot = ROULETTE_SLOTS[i];
                    int gameIndex = (currentIndex + i) % miniGameTypes.size();
                    boolean isSelected = (slot == CENTER_SLOT);

                    gui.setItem(slot, createMiniGameItem(miniGameTypes.get(gameIndex), isSelected));
                }

                // Play sound effect
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f + (iterations[0] / (float)totalIterations));

                // Move to next item
                currentIndex = (currentIndex + 1) % miniGameTypes.size();
                iterations[0]++;

                // Slow down gradually
                if (iterations[0] > totalIterations / 2) {
                    if (iterations[0] % 3 == 0 && speed[0] < 10) {
                        speed[0]++;
                    }
                }

                // End animation when we've reached the end
                if (iterations[0] >= totalIterations) {
                    // Get the selected mini-game (the one in the center)
                    int centerGameIndex = (currentIndex + ROULETTE_SLOTS.length / 2 - 1) % miniGameTypes.size();
                    MiniGameType selectedGame = miniGameTypes.get(centerGameIndex);

                    // Store the selection
                    selectedGames.put(playerUUID, selectedGame);

                    // Update the GUI to show selection is complete
                    gui.setItem(START_SLOT, createStartButton(true));

                    // Play sound effect for completion
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    // Send message
                    MessageUtil.sendMessage(player, "&6Selected mini-game: &e" + selectedGame.getDisplayName());

                    // Cancel this task
                    this.cancel();
                    animationTasks.remove(playerUUID);
                } else {
                    // Schedule next update with increased delay
                    this.cancel();
                    int newTaskId = new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (player.isOnline()) {
                                run();
                            } else {
                                this.cancel();
                                animationTasks.remove(playerUUID);
                            }
                        }
                    }.runTaskLater(plugin, speed[0]).getTaskId();

                    animationTasks.put(playerUUID, newTaskId);
                }
            }
        }.runTaskLater(plugin, 2).getTaskId();

        animationTasks.put(playerUUID, taskId);
    }

    /**
     * Handles clicks in the mini-game selection GUI.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        // Check if this is our GUI
        if (event.getView().getTitle().contains("Random Mini-Game Selector")) {
            event.setCancelled(true); // Prevent taking items

            // Handle the clicks on specific slots
            if (event.getRawSlot() == REROLL_SLOT) {
                handleRerollClick(player, event.getClickedInventory());
            } else if (event.getRawSlot() == START_SLOT) {
                handleStartClick(player);
            }
        }
    }

    /**
     * Handles a click on the reroll button.
     */
    private void handleRerollClick(Player player, Inventory inventory) {
        UUID playerUUID = player.getUniqueId();

        // Check if player can reroll
        if (canReroll.getOrDefault(playerUUID, false)) {
            // Set reroll used
            canReroll.put(playerUUID, false);

            // Update the reroll button
            if (inventory != null) {
                inventory.setItem(REROLL_SLOT, createRerollButton(false));
                inventory.setItem(START_SLOT, createStartButton(false));
            }

            // Remove any stored selection
            selectedGames.remove(playerUUID);

            // Start a new animation
            startRouletteAnimation(player, inventory);

            // Play sound
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
            MessageUtil.sendMessage(player, "&eRerolling for a new mini-game...");
        } else {
            // Already used reroll
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            MessageUtil.sendMessage(player, "&cYou've already used your reroll!");
        }
    }

    /**
     * Handles a click on the start button.
     */
    private void handleStartClick(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Check if a game has been selected
        if (!selectedGames.containsKey(playerUUID)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if the victim is still online
        UUID victimUUID = playerSelections.get(playerUUID);
        if (victimUUID == null) {
            MessageUtil.sendMessage(player, "&cError: No opponent found!");
            player.closeInventory();
            return;
        }

        Player victim = Bukkit.getPlayer(victimUUID);
        if (victim == null || !victim.isOnline()) {
            MessageUtil.sendMessage(player, "&cYour opponent is no longer online!");
            player.closeInventory();
            return;
        }

        // Get the selected game
        MiniGameType selectedGame = selectedGames.get(playerUUID);

        // Cancel any animations
        if (animationTasks.containsKey(playerUUID)) {
            Bukkit.getScheduler().cancelTask(animationTasks.get(playerUUID));
            animationTasks.remove(playerUUID);
        }

        // Close the inventory
        player.closeInventory();

        // Start the game
        boolean success = plugin.getMiniGameEngine().startGame(selectedGame, player, victim);

        if (success) {
            // Clear stored data
            clearPlayerData(playerUUID);

            MessageUtil.sendMessage(player, "&aStarting mini-game: &e" + selectedGame.getDisplayName());
            MessageUtil.sendMessage(victim, "&aStarting mini-game: &e" + selectedGame.getDisplayName());
        } else {
            MessageUtil.sendMessage(player, "&cFailed to start the mini-game!");
        }
    }

    /**
     * Prevents players from closing the GUI except by selecting a game.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Check if this is our GUI
        if (event.getView().getTitle().contains("Random Mini-Game Selector")) {
            // If we have an active animation, the player is trying to escape selection
            if (animationTasks.containsKey(playerUUID)) {
                // Cancel the animation
                Bukkit.getScheduler().cancelTask(animationTasks.get(playerUUID));
                animationTasks.remove(playerUUID);

                // Send message
                MessageUtil.sendMessage(player, "&cMini-game selection cancelled!");

                // Clear data
                clearPlayerData(playerUUID);
            }
            // If we have a selection but no active animation, they closed after selection
            else if (selectedGames.containsKey(playerUUID)) {
                // Schedule a task to reopen the GUI (can't open in the same tick)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            // Check if the victim is still online
                            UUID victimUUID = playerSelections.get(playerUUID);
                            if (victimUUID != null) {
                                Player victim = Bukkit.getPlayer(victimUUID);
                                if (victim != null && victim.isOnline()) {
                                    // Reopen the GUI
                                    Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.DARK_PURPLE + "Random Mini-Game Selector");

                                    // Set up the GUI again
                                    fillBackground(gui);
                                    gui.setItem(REROLL_SLOT, createRerollButton(canReroll.getOrDefault(playerUUID, false)));
                                    gui.setItem(START_SLOT, createStartButton(true));

                                    // Place the selected game in all roulette slots, with the center highlighted
                                    MiniGameType selectedGame = selectedGames.get(playerUUID);
                                    for (int slot : ROULETTE_SLOTS) {
                                        gui.setItem(slot, createMiniGameItem(selectedGame, slot == CENTER_SLOT));
                                    }

                                    // Open for player
                                    player.openInventory(gui);

                                    // Message
                                    MessageUtil.sendMessage(player, "&eYou must select a mini-game or cancel the selection!");
                                }
                            }
                        }
                    }
                }.runTaskLater(plugin, 1);
            }
        }
    }

    /**
     * Clears all stored data for a player.
     */
    private void clearPlayerData(UUID playerUUID) {
        playerSelections.remove(playerUUID);
        selectedGames.remove(playerUUID);
        canReroll.remove(playerUUID);

        if (animationTasks.containsKey(playerUUID)) {
            Bukkit.getScheduler().cancelTask(animationTasks.get(playerUUID));
            animationTasks.remove(playerUUID);
        }
    }

    /**
     * Cancels all animations when the plugin is disabled.
     */
    public void cleanup() {
        for (int taskId : animationTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        playerSelections.clear();
        selectedGames.clear();
        canReroll.clear();
        animationTasks.clear();
    }
}