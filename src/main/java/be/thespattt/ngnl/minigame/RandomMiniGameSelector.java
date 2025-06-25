package be.thespattt.ngnl.minigame;

import be.thespattt.ngnl.NoGameNoLife;
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
    private final Map<UUID, UUID> playerSelections = new HashMap<>();
    private final Map<UUID, MiniGameType> selectedGames = new HashMap<>();
    private final Map<UUID, Boolean> canReroll = new HashMap<>();
    private final Map<UUID, Integer> animationTasks = new HashMap<>();
    private final Set<UUID> inTeleport = new HashSet<>();

    private static final int GUI_SIZE = 27;
    private static final int REROLL_SLOT = 0;
    private static final int START_SLOT = 8;
    private static final int CENTER_SLOT = 13;
    private static final int[] ROULETTE_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    public RandomMiniGameSelector(NoGameNoLife plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMiniGameSelectionGUI(Player killer, Player victim) {
        UUID killerUUID = killer.getUniqueId();
        playerSelections.put(killerUUID, victim.getUniqueId());
        canReroll.put(killerUUID, true);
        inTeleport.add(killerUUID);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (killer.isOnline()) {
                    Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.DARK_PURPLE + "Random Mini-Game Selector");
                    fillBackground(gui);
                    gui.setItem(REROLL_SLOT, createRerollButton(true));
                    gui.setItem(START_SLOT, createStartButton(false));

                    killer.openInventory(gui);
                    startRouletteAnimation(killer, gui);

                    // Remove from teleport tracking after GUI is opened
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            inTeleport.remove(killerUUID);
                        }
                    }.runTaskLater(plugin, 5);
                }
            }
        }.runTaskLater(plugin, 10); // Wait half a second after teleport
    }

    private void fillBackground(Inventory gui) {
        ItemStack backgroundItem = createGuiItem(Material.WHITE_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < GUI_SIZE; i++) {
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

    private ItemStack createStartButton(boolean enabled) {
        if (enabled) {
            ItemStack item = createGuiItem(
                    Material.EMERALD,
                    ChatColor.GREEN + "Start Mini-Game",
                    ChatColor.GRAY + "Click to start the selected mini-game"
            );

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

    private ItemStack createMiniGameItem(MiniGameType type, boolean selected) {
        Material material = getMaterialForMiniGameType(type);
        String description = type.getDescription();

        ItemStack item = createGuiItem(
                material,
                ChatColor.GOLD + type.getDisplayName(),
                ChatColor.GRAY + "Mini-game: " + ChatColor.DARK_PURPLE + type.name(),
                ChatColor.YELLOW + description,
                ChatColor.AQUA + "Challenge your opponent to this game!"
        );

        if (selected) {
            addGlowEffect(item);
        }

        return item;
    }

    private Material getMaterialForMiniGameType(MiniGameType type) {
        switch (type) {
            case SPLEEF: return Material.DIAMOND_SHOVEL;
            case TNT_RUN: return Material.TNT;
            case PARKOUR: return Material.FEATHER;
            case BLOC_PARTY: return Material.NOTE_BLOCK;
            case SUMO: return Material.SLIME_BLOCK;
            case SPLEGG: return Material.EGG;
            case FLOOR_IS_LAVA: return Material.LAVA_BUCKET;
            case DES_A_COUDRE: return Material.WATER_BUCKET;
            case ANVIL_RAIN: return Material.ANVIL;
            case WORD_CHAIN_BATTLE: return Material.BOOK;
            case LOGICAL_DEDUCTION: return Material.COMPASS;
            case MENTAL_CHESS: return Material.CHEST;
            case MEMORY_GAME: return Material.CLOCK;
            case ORACLE_CARD: return Material.ENCHANTED_BOOK;
            default: return Material.PAPER;
        }
    }

    private void addGlowEffect(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }

        return item;
    }

    private List<MiniGameType> getEnabledMiniGameTypes() {
        List<MiniGameType> enabledTypes = new ArrayList<>();

        for (MiniGameType type : MiniGameType.values()) {
            if (plugin.getConfigManager().getGameConfig().isMiniGameEnabled(type.name())) {
                enabledTypes.add(type);
            }
        }

        if (enabledTypes.isEmpty()) {
            return Arrays.asList(MiniGameType.values());
        }

        return enabledTypes;
    }

    private void startRouletteAnimation(Player player, Inventory gui) {
        UUID playerUUID = player.getUniqueId();
        List<MiniGameType> miniGameTypes = prepareRouletteGameList();

        final int[] speed = {2};
        final int[] iterations = {0};
        final int totalIterations = 30 + new Random().nextInt(10);

        cancelExistingAnimation(playerUUID);
        startNewAnimationTask(player, gui, miniGameTypes, speed, iterations, totalIterations, playerUUID);
    }

    private List<MiniGameType> prepareRouletteGameList() {
        List<MiniGameType> miniGameTypes = getEnabledMiniGameTypes();
        Collections.shuffle(miniGameTypes);

        while (miniGameTypes.size() < ROULETTE_SLOTS.length * 2) {
            miniGameTypes.addAll(new ArrayList<>(miniGameTypes));
        }

        return miniGameTypes;
    }

    private void cancelExistingAnimation(UUID playerUUID) {
        if (animationTasks.containsKey(playerUUID)) {
            Bukkit.getScheduler().cancelTask(animationTasks.get(playerUUID));
        }
    }

    private void startNewAnimationTask(Player player, Inventory gui, List<MiniGameType> miniGameTypes,
                                       final int[] speed, final int[] iterations, final int totalIterations, UUID playerUUID) {

        int taskId = new BukkitRunnable() {
            int currentIndex = 0;

            @Override
            public void run() {
                updateRouletteSlots(gui, miniGameTypes, currentIndex);
                playTickSound(player, iterations[0], totalIterations);

                currentIndex = (currentIndex + 1) % miniGameTypes.size();
                iterations[0]++;

                adjustAnimationSpeed(iterations[0], totalIterations, speed);

                if (iterations[0] >= totalIterations) {
                    finishAnimation(player, gui, miniGameTypes, currentIndex, playerUUID);
                } else {
                    scheduleNextAnimationTick(player, gui, miniGameTypes, speed, iterations, totalIterations, playerUUID, currentIndex);
                    this.cancel();
                }
            }
        }.runTaskLater(plugin, 2).getTaskId();

        animationTasks.put(playerUUID, taskId);
    }

    private void updateRouletteSlots(Inventory gui, List<MiniGameType> miniGameTypes, int currentIndex) {
        for (int i = 0; i < ROULETTE_SLOTS.length; i++) {
            int slot = ROULETTE_SLOTS[i];
            int gameIndex = (currentIndex + i) % miniGameTypes.size();
            boolean isSelected = (slot == CENTER_SLOT);
            gui.setItem(slot, createMiniGameItem(miniGameTypes.get(gameIndex), isSelected));
        }
    }

    private void playTickSound(Player player, int currentIteration, int totalIterations) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f + (currentIteration / (float)totalIterations));
    }

    private void adjustAnimationSpeed(int currentIteration, int totalIterations, int[] speed) {
        if (currentIteration > totalIterations / 2) {
            if (currentIteration % 3 == 0 && speed[0] < 10) {
                speed[0]++;
            }
        }
    }

    private void finishAnimation(Player player, Inventory gui, List<MiniGameType> miniGameTypes, int currentIndex, UUID playerUUID) {
        int centerGameIndex = (currentIndex + ROULETTE_SLOTS.length / 2 - 1) % miniGameTypes.size();
        MiniGameType selectedGame = miniGameTypes.get(centerGameIndex);

        selectedGames.put(playerUUID, selectedGame);
        gui.setItem(START_SLOT, createStartButton(true));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        MessageUtil.sendMessage(player, "&6Selected mini-game: &e" + selectedGame.getDisplayName());

        animationTasks.remove(playerUUID);
    }

    private void scheduleNextAnimationTick(Player player, Inventory gui, List<MiniGameType> miniGameTypes,
                                           final int[] speed, final int[] iterations, final int totalIterations,
                                           UUID playerUUID, int currentIndex) {

        int newTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    updateRouletteSlots(gui, miniGameTypes, currentIndex);
                    playTickSound(player, iterations[0], totalIterations);

                    int nextIndex = (currentIndex + 1) % miniGameTypes.size();
                    iterations[0]++;

                    adjustAnimationSpeed(iterations[0], totalIterations, speed);

                    if (iterations[0] >= totalIterations) {
                        finishAnimation(player, gui, miniGameTypes, nextIndex, playerUUID);
                        this.cancel();
                    } else {
                        scheduleNextAnimationTick(player, gui, miniGameTypes, speed, iterations, totalIterations, playerUUID, nextIndex);
                        this.cancel();
                    }
                } else {
                    this.cancel();
                    animationTasks.remove(playerUUID);
                }
            }
        }.runTaskLater(plugin, speed[0]).getTaskId();

        animationTasks.put(playerUUID, newTaskId);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        if (event.getView().getTitle().contains("Random Mini-Game Selector")) {
            event.setCancelled(true);

            if (event.getRawSlot() == REROLL_SLOT) {
                handleRerollClick(player, event.getClickedInventory());
            } else if (event.getRawSlot() == START_SLOT) {
                handleStartClick(player);
            }
        }
    }

    private void handleRerollClick(Player player, Inventory inventory) {
        UUID playerUUID = player.getUniqueId();

        if (canReroll.getOrDefault(playerUUID, false)) {
            canReroll.put(playerUUID, false);

            if (inventory != null) {
                inventory.setItem(REROLL_SLOT, createRerollButton(false));
                inventory.setItem(START_SLOT, createStartButton(false));
            }

            selectedGames.remove(playerUUID);
            startRouletteAnimation(player, inventory);

            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);
            MessageUtil.sendMessage(player, "&eRerolling for a new mini-game...");
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            MessageUtil.sendMessage(player, "&cYou've already used your reroll!");
        }
    }

    private void handleStartClick(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!selectedGames.containsKey(playerUUID)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

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

        MiniGameType selectedGame = selectedGames.get(playerUUID);
        cancelExistingAnimation(playerUUID);

        // Mark as intentional close to avoid the reopen logic
        inTeleport.add(playerUUID);
        player.closeInventory();

        boolean success = plugin.getMiniGameEngine().startGame(selectedGame, player, victim);

        if (success) {
            clearPlayerData(playerUUID);
            MessageUtil.sendMessage(player, "&aStarting mini-game: &e" + selectedGame.getDisplayName());
            MessageUtil.sendMessage(victim, "&aStarting mini-game: &e" + selectedGame.getDisplayName());
        } else {
            MessageUtil.sendMessage(player, "&cFailed to start the mini-game!");
            inTeleport.remove(playerUUID);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!event.getView().getTitle().contains("Random Mini-Game Selector") || inTeleport.contains(playerUUID)) {
            return;
        }

        if (animationTasks.containsKey(playerUUID)) {
            handleAnimationCancellation(player, playerUUID);
        } else if (selectedGames.containsKey(playerUUID)) {
            reopenSelectionGui(player, playerUUID);
        }
    }

    private void handleAnimationCancellation(Player player, UUID playerUUID) {
        cancelExistingAnimation(playerUUID);
        MessageUtil.sendMessage(player, "&cMini-game selection cancelled!");
        clearPlayerData(playerUUID);
    }

    private void reopenSelectionGui(Player player, UUID playerUUID) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                UUID victimUUID = playerSelections.get(playerUUID);
                if (victimUUID == null) return;

                Player victim = Bukkit.getPlayer(victimUUID);
                if (victim == null || !victim.isOnline()) return;

                Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.DARK_PURPLE + "Random Mini-Game Selector");
                fillBackground(gui);
                gui.setItem(REROLL_SLOT, createRerollButton(canReroll.getOrDefault(playerUUID, false)));
                gui.setItem(START_SLOT, createStartButton(true));

                MiniGameType selectedGame = selectedGames.get(playerUUID);
                for (int slot : ROULETTE_SLOTS) {
                    gui.setItem(slot, createMiniGameItem(selectedGame, slot == CENTER_SLOT));
                }

                player.openInventory(gui);
                MessageUtil.sendMessage(player, "&eYou must select a mini-game or cancel the selection!");
            }
        }.runTaskLater(plugin, 1);
    }

    private void clearPlayerData(UUID playerUUID) {
        playerSelections.remove(playerUUID);
        selectedGames.remove(playerUUID);
        canReroll.remove(playerUUID);
        inTeleport.remove(playerUUID);

        cancelExistingAnimation(playerUUID);
    }

    public void cleanup() {
        for (int taskId : animationTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        playerSelections.clear();
        selectedGames.clear();
        canReroll.clear();
        animationTasks.clear();
        inTeleport.clear();
    }
}