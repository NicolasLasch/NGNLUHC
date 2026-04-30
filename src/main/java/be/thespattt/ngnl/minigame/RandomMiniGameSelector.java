package be.thespattt.ngnl.minigame;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.role.duo.StephanieRole;
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
    private final Map<UUID, Set<UUID>> guiViewers = new HashMap<>(); // controller -> set of viewers
    private final Set<UUID> mustStayInGui = new HashSet<>(); // players who cannot leave GUI

    private static final int GUI_SIZE = 27;
    private static final String RANDOM_SELECTOR_TITLE = ChatColor.DARK_PURPLE + "Random Mini-Game Selector";
    private static final String STEPHANIE_MODE_TITLE = ChatColor.LIGHT_PURPLE + "Stephanie - Mini-Game";
    private static final String STEPHANIE_CHOOSE_TITLE = ChatColor.LIGHT_PURPLE + "Stephanie - Choose Game";
    private static final String SORA_MODE_TITLE = ChatColor.AQUA + "Sora - Substitution";
    private static final String SHIRO_ACCEPT_TITLE = ChatColor.AQUA + "Shiro - Accept";
    private static final int REROLL_SLOT = 0;
    private static final int START_SLOT = 8;
    private static final int CENTER_SLOT = 13;
    private static final int[] ROULETTE_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    public RandomMiniGameSelector(NoGameNoLife plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMiniGameSelectionGUI(Player killer, Player victim) {
        if (shouldOpenStephanieMode(killer)) {
            openStephanieModeGUI(killer, victim);
            return;
        }
        if (shouldOpenSoraMode(killer)) {
            openSoraModeGUI(killer, victim);
            return;
        }

        openRandomMiniGameSelectionGUI(killer, victim);
    }

    private void openRandomMiniGameSelectionGUI(Player killer, Player victim) {
        UUID killerUUID = killer.getUniqueId();
        playerSelections.put(killerUUID, victim.getUniqueId());
        canReroll.put(killerUUID, true);
        inTeleport.add(killerUUID);

        // NEW: Track both players in GUI
        Set<UUID> viewers = new HashSet<>();
        viewers.add(killer.getUniqueId());
        viewers.add(victim.getUniqueId());
        guiViewers.put(killerUUID, viewers);
        mustStayInGui.addAll(viewers);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (killer.isOnline() && victim.isOnline()) {
                    Inventory gui = Bukkit.createInventory(null, GUI_SIZE, RANDOM_SELECTOR_TITLE);
                    fillBackground(gui);
                    gui.setItem(REROLL_SLOT, createRerollButton(true));
                    gui.setItem(START_SLOT, createStartButton(false));

                    // Open GUI for both players
                    killer.openInventory(gui);
                    victim.openInventory(gui);

                    startRouletteAnimation(killer, gui);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            inTeleport.remove(killerUUID);
                        }
                    }.runTaskLater(plugin, 5);
                }
            }
        }.runTaskLater(plugin, 10);
    }

    private boolean shouldOpenStephanieMode(Player player) {
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        return ngnlPlayer != null
                && ngnlPlayer.getRole() instanceof StephanieRole stephanieRole
                && !stephanieRole.hasUsedMiniGameChoice()
                && plugin.getMiniGameSelectionManager().hasActiveSelection(player.getUniqueId());
    }

    private boolean shouldOpenSoraMode(Player player) {
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        return ngnlPlayer != null
                && ngnlPlayer.getRole() != null
                && ngnlPlayer.getRole().getRoleType() == RoleType.SORA
                && plugin.getMiniGameSelectionManager().isSoraSubstitutionSelection(player.getUniqueId());
    }

    private void openStephanieModeGUI(Player stephanie, Player opponent) {
        UUID stephanieUUID = stephanie.getUniqueId();
        playerSelections.put(stephanieUUID, opponent.getUniqueId());
        inTeleport.add(stephanieUUID);

        Set<UUID> viewers = new HashSet<>();
        viewers.add(stephanie.getUniqueId());
        guiViewers.put(stephanieUUID, viewers);
        mustStayInGui.addAll(viewers);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!stephanie.isOnline() || !opponent.isOnline()) {
                    return;
                }

                Inventory gui = Bukkit.createInventory(null, GUI_SIZE, STEPHANIE_MODE_TITLE);
                fillBackground(gui);
                gui.setItem(11, createGuiItem(
                        Material.DISPENSER,
                        ChatColor.GREEN + "Random",
                        ChatColor.GRAY + "Launch the normal roulette."
                ));
                gui.setItem(15, createGuiItem(
                        Material.NETHER_STAR,
                        ChatColor.LIGHT_PURPLE + "Choose the game",
                        ChatColor.GRAY + "Remaining uses: " + ChatColor.YELLOW + "1",
                        ChatColor.GRAY + "Opens all enabled mini-games."
                ));

                stephanie.openInventory(gui);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        inTeleport.remove(stephanieUUID);
                    }
                }.runTaskLater(plugin, 5);
            }
        }.runTaskLater(plugin, 10);
    }

    private void openStephanieGameChoiceGUI(Player stephanie) {
        Inventory gui = Bukkit.createInventory(null, 54, STEPHANIE_CHOOSE_TITLE);
        List<MiniGameType> types = getEnabledMiniGameTypes();
        for (int i = 0; i < Math.min(types.size(), 45); i++) {
            gui.setItem(i, createMiniGameItem(types.get(i), false));
        }
        gui.setItem(53, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Back"));
        stephanie.openInventory(gui);
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

        // NEW: Update for all viewers
        UUID controllerUUID = findControllerForGui();
        if (controllerUUID != null) {
            updateGuiForAllViewers(controllerUUID, gui);
        }
    }

    private UUID findControllerForGui() {
        // Find the controller based on current animation tasks
        for (UUID uuid : animationTasks.keySet()) {
            return uuid;
        }
        return null;
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

        if (event.getView().getTitle().contains("Stephanie - Mini-Game")) {
            event.setCancelled(true);

            if (!playerSelections.containsKey(playerUUID)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }

            if (event.getRawSlot() == 11) {
                UUID victimUUID = playerSelections.get(playerUUID);
                Player victim = victimUUID != null ? Bukkit.getPlayer(victimUUID) : null;
                markIntentionalClose(playerUUID);
                clearPlayerData(playerUUID);
                inTeleport.add(playerUUID);
                player.closeInventory();
                if (victim != null) {
                    openRandomMiniGameSelectionGUI(player, victim);
                }
            } else if (event.getRawSlot() == 15) {
                markTemporaryTransition(playerUUID);
                openStephanieGameChoiceGUI(player);
            }
            return;
        }

        if (event.getView().getTitle().contains("Stephanie - Choose Game")) {
            event.setCancelled(true);

            if (!playerSelections.containsKey(playerUUID)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }

            if (event.getRawSlot() == 53) {
                UUID victimUUID = playerSelections.get(playerUUID);
                Player victim = victimUUID != null ? Bukkit.getPlayer(victimUUID) : null;
                if (victim != null) {
                    markTemporaryTransition(playerUUID);
                    openStephanieModeGUI(player, victim);
                }
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            MiniGameType selected = getMiniGameTypeFromItem(clicked);
            if (selected != null) {
                markIntentionalClose(playerUUID);
                player.closeInventory();
                clearPlayerData(playerUUID);
                if (!plugin.getMiniGameSelectionManager().processStephanieChoice(player, selected)) {
                    MessageUtil.sendMessage(player, "&cThis mini-game choice is no longer available.");
                    inTeleport.remove(playerUUID);
                }
            }
            return;
        }

        if (event.getView().getTitle().contains("Sora - Substitution")) {
            event.setCancelled(true);

            if (!playerSelections.containsKey(playerUUID)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }

            UUID victimUUID = playerSelections.get(playerUUID);
            Player victim = victimUUID != null ? Bukkit.getPlayer(victimUUID) : null;
            if (victim == null) {
                clearPlayerData(playerUUID);
                return;
            }

            if (event.getRawSlot() == 11) {
                Player shiro = plugin.getMiniGameSelectionManager().processSoraSubstitutionFromGui(player);
                if (shiro != null) {
                    markIntentionalClose(playerUUID);
                    clearPlayerData(playerUUID);
                    player.closeInventory();
                    openShiroAcceptGUI(player, shiro, victim);
                }
                return;
            }

            if (event.getRawSlot() == 15) {
                markIntentionalClose(playerUUID);
                clearPlayerData(playerUUID);
                player.closeInventory();
                plugin.getMiniGameSelectionManager().processSoraDecline(player);
            }
            return;
        }

        if (event.getView().getTitle().contains("Shiro - Accept")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 11) {
                UUID soraUUID = plugin.getMiniGameSelectionManager().getSoraControllerForShiro(playerUUID);
                if (soraUUID != null) {
                    markIntentionalClose(soraUUID);
                }
                markIntentionalClose(playerUUID);
                player.closeInventory();
                plugin.getMiniGameSelectionManager().processShiroAccept(player);
            } else if (event.getRawSlot() == 15) {
                UUID soraUUID = plugin.getMiniGameSelectionManager().getSoraControllerForShiro(playerUUID);
                if (soraUUID != null) {
                    markTemporaryTransition(soraUUID);
                }
                markIntentionalClose(playerUUID);
                player.closeInventory();
                plugin.getMiniGameSelectionManager().processShiroDecline(player);
            }
            return;
        }

        if (event.getView().getTitle().contains("Random Mini-Game Selector")) {
            event.setCancelled(true);

            boolean canControl = playerSelections.containsKey(playerUUID);

            if (!canControl) {
                // Player can only watch, not interact
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }

            if (event.getRawSlot() == REROLL_SLOT) {
                handleRerollClick(player, event.getClickedInventory());
            } else if (event.getRawSlot() == START_SLOT) {
                handleStartClick(player);
            }
        }
    }

    private MiniGameType getMiniGameTypeFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null || item.getItemMeta().getLore() == null) {
            return null;
        }
        for (String line : item.getItemMeta().getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped != null && stripped.startsWith("Mini-game: ")) {
                return MiniGameType.getByName(stripped.substring("Mini-game: ".length()));
            }
        }
        return null;
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

        markIntentionalClose(playerUUID);
        player.closeInventory();
        victim.closeInventory();
        clearPlayerData(playerUUID);
        plugin.getMiniGameSessionManager().clearPending(playerUUID);

        boolean success = plugin.getMiniGameEngine().startGame(selectedGame, player, victim);

        if (success) {
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

        if (!event.getView().getTitle().contains("Random Mini-Game Selector")
                && !event.getView().getTitle().contains("Stephanie - Mini-Game")
                && !event.getView().getTitle().contains("Stephanie - Choose Game")
                && !event.getView().getTitle().contains("Sora - Substitution")
                && !event.getView().getTitle().contains("Shiro - Accept")) {
            return;
        }

        if (mustStayInGui.contains(playerUUID) && !inTeleport.contains(playerUUID)) {
            // Force reopen GUI after 1 tick
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;

                    // Check if they still need to stay in GUI
                    if (mustStayInGui.contains(playerUUID)) {
                        reopenSelectionGui(player, playerUUID);
                        MessageUtil.sendMessage(player, "&eYou must wait for the mini-game selection to complete!");
                    }
                }
            }.runTaskLater(plugin, 1);
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

                if (shouldOpenStephanieMode(player)) {
                    openStephanieModeGUI(player, victim);
                    return;
                }
                if (shouldOpenSoraMode(player)) {
                    openSoraModeGUI(player, victim);
                    return;
                }

                Inventory gui = Bukkit.createInventory(null, GUI_SIZE, RANDOM_SELECTOR_TITLE);
                fillBackground(gui);
                gui.setItem(REROLL_SLOT, createRerollButton(canReroll.getOrDefault(playerUUID, false)));
                gui.setItem(START_SLOT, createStartButton(true));

                MiniGameType selectedGame = selectedGames.get(playerUUID);
                if (selectedGame != null) {
                    for (int slot : ROULETTE_SLOTS) {
                        gui.setItem(slot, createMiniGameItem(selectedGame, slot == CENTER_SLOT));
                    }
                } else {
                    for (int slot : ROULETTE_SLOTS) {
                        gui.setItem(slot, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
                    }
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

        Set<UUID> viewers = guiViewers.remove(playerUUID);
        if (viewers != null) {
            mustStayInGui.removeAll(viewers);
        }

        cancelExistingAnimation(playerUUID);
    }

    public void clearSelectionForController(UUID playerUUID) {
        clearPlayerData(playerUUID);
    }

    private void markIntentionalClose(UUID controllerUUID) {
        inTeleport.add(controllerUUID);
        Set<UUID> viewers = guiViewers.get(controllerUUID);
        if (viewers != null) {
            inTeleport.addAll(viewers);
        }
    }

    private void markTemporaryTransition(UUID controllerUUID) {
        markIntentionalClose(controllerUUID);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            inTeleport.remove(controllerUUID);
            Set<UUID> viewers = guiViewers.get(controllerUUID);
            if (viewers != null) {
                viewers.forEach(inTeleport::remove);
            }
        }, 3L);
    }

    private void openSoraModeGUI(Player sora, Player opponent) {
        UUID soraUUID = sora.getUniqueId();
        playerSelections.put(soraUUID, opponent.getUniqueId());
        inTeleport.add(soraUUID);

        Set<UUID> viewers = new HashSet<>();
        viewers.add(sora.getUniqueId());
        guiViewers.put(soraUUID, viewers);
        mustStayInGui.addAll(viewers);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!sora.isOnline() || !opponent.isOnline()) {
                    return;
                }
                Inventory gui = Bukkit.createInventory(null, GUI_SIZE, SORA_MODE_TITLE);
                fillBackground(gui);
                gui.setItem(11, createGuiItem(
                        Material.ENDER_PEARL,
                        ChatColor.AQUA + "Ask Shiro",
                        ChatColor.GRAY + "Request Shiro to play this mini-game."
                ));
                gui.setItem(15, createGuiItem(
                        Material.EMERALD,
                        ChatColor.GREEN + "Play yourself",
                        ChatColor.GRAY + "Continue to the normal roulette."
                ));
                sora.openInventory(gui);
                Bukkit.getScheduler().runTaskLater(plugin, () -> inTeleport.remove(soraUUID), 5L);
            }
        }.runTaskLater(plugin, 10L);
    }

    private void openShiroAcceptGUI(Player sora, Player shiro, Player opponent) {
        markTemporaryTransition(sora.getUniqueId());
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, SHIRO_ACCEPT_TITLE);
        fillBackground(gui);
        gui.setItem(11, createGuiItem(
                Material.LIME_DYE,
                ChatColor.GREEN + "Accept",
                ChatColor.GRAY + "Play the mini-game against " + opponent.getName() + "."
        ));
        gui.setItem(15, createGuiItem(
                Material.RED_DYE,
                ChatColor.RED + "Decline",
                ChatColor.GRAY + "Sora will continue normally."
        ));
        shiro.openInventory(gui);
        MessageUtil.sendMessage(shiro, "&6" + sora.getName() + " wants you to substitute.");
        MessageUtil.sendMessage(shiro, "&eYou can also use &a/acceptsub &eor &a/duo acceptsub&e.");
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

    private void updateGuiForAllViewers(UUID controllerUUID, Inventory gui) {
        Set<UUID> viewers = guiViewers.get(controllerUUID);
        if (viewers == null) return;

        for (UUID viewerUUID : viewers) {
            Player viewer = Bukkit.getPlayer(viewerUUID);
            if (viewer != null && viewer.isOnline()) {
                // Update the viewer's inventory view
                if (viewer.getOpenInventory().getTitle().contains("Random Mini-Game Selector")) {
                    // Copy the controller's GUI to the viewer
                    for (int i = 0; i < GUI_SIZE; i++) {
                        viewer.getOpenInventory().getTopInventory().setItem(i, gui.getItem(i));
                    }
                }
            }
        }
    }
}
