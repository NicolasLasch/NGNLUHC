package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.minigame.games.addons.EffectType;
import be.thespattt.ngnl.minigame.games.addons.Element;
import be.thespattt.ngnl.minigame.games.addons.CardType;
import be.thespattt.ngnl.minigame.games.addons.OracleCard;
import be.thespattt.ngnl.minigame.games.addons.CombinationResult;
import be.thespattt.ngnl.minigame.games.addons.ScrollCombination;
import be.thespattt.ngnl.minigame.games.addons.ScrollCombinations;
import be.thespattt.ngnl.minigame.games.addons.StatusEffect;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

// Working
// TODO : Better rule explanation and card combination + Ressource pack
public class OracleCardMiniGame extends MiniGameBase implements Listener {

    private enum GamePhase {
        SETUP,
        CARD_SELECTION,
        RESOLUTION,
        ENDED
    }

    private GamePhase currentPhase = GamePhase.SETUP;
    private UUID currentPlayerTurn;
    private int currentRound = 0;
    private final int MAX_ROUNDS = 8;
    private int turnTimeRemaining = 20;
    private BukkitTask turnTimerTask;

    private final Map<UUID, Integer> playerHealth = new HashMap<>();
    private final Map<UUID, List<OracleCard>> playerHands = new HashMap<>();
    private final Map<UUID, List<OracleCard>> playerSelectedCards = new HashMap<>();
    private final Map<UUID, Integer> playerTotalDamage = new HashMap<>();
    private final Map<UUID, StatusEffect> playerStatusEffects = new HashMap<>();

    private Inventory player1Inventory;
    private Inventory player2Inventory;

    private Location arenaCenter;
    private boolean arenaReady = false;

    private static final String PLAYER1_TITLE = "&6Oracle Cards - Your Hand";
    private static final String PLAYER2_TITLE = "&6Oracle Cards - Your Hand";
    private static final int STARTING_HEALTH = 40;

    public OracleCardMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.ORACLE_CARD, player1WonPvP);

        playerHealth.put(player1UUID, STARTING_HEALTH);
        playerHealth.put(player2UUID, STARTING_HEALTH);
        playerTotalDamage.put(player1UUID, 0);
        playerTotalDamage.put(player2UUID, 0);
        playerSelectedCards.put(player1UUID, new ArrayList<>());
        playerSelectedCards.put(player2UUID, new ArrayList<>());
    }

    @Override
    protected void onGameStart() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        setupArenaAndStart();
    }

    private void setupArenaAndStart() {
        World world = getOrCreateMinigameWorld();
        if (world == null) {
            MessageUtil.sendMessage(getPlayer1(), "&cError: Could not create minigame world!");
            MessageUtil.sendMessage(getPlayer2(), "&cError: Could not create minigame world!");
            return;
        }

        generateArenaLocation(world);
        loadChunksSync(world, arenaCenter, 20);
        completeArenaSetupAndStart();
    }

    private void generateArenaLocation(World world) {
        int x = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(100, 1001) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int y = 100;
        arenaCenter = new Location(world, x, y, z);
    }

    private void loadChunksSync(World world, Location center, int radius) {
        int chunkRadius = (int) Math.ceil(radius / 16.0);

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt(center.getBlockX() / 16 + dx, center.getBlockZ() / 16 + dz);
                chunk.load(true);
            }
        }
    }

    private void completeArenaSetupAndStart() {
        buildArena();
        teleportPlayers();
        initializeGame();
        arenaReady = true;

        MessageUtil.sendMessage(getPlayer1(), "&aArena ready! Starting Oracle Card Game...");
        MessageUtil.sendMessage(getPlayer2(), "&aArena ready! Starting Oracle Card Game...");

        beginGame();
    }

    private void buildArena() {
        World world = getOrCreateMinigameWorld();
        if (world == null) return;

        clearArenaArea(world);
        buildMysticalArena(world);
    }

    private void clearArenaArea(World world) {
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                for (int y = -5; y <= 10; y++) {
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.AIR);
                }
            }
        }
    }

    private void buildMysticalArena(World world) {
        // Build circular mystical floor
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= 10) {
                    // Mystical pattern floor
                    Material floorMaterial;
                    if (distance <= 3) {
                        floorMaterial = Material.GOLD_BLOCK;
                    } else if ((x + z) % 2 == 0) {
                        floorMaterial = Material.PURPLE_CONCRETE;
                    } else {
                        floorMaterial = Material.BLACK_CONCRETE;
                    }

                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY(), arenaCenter.getBlockZ() + z).setType(floorMaterial);

                    // Foundation
                    for (int y = -5; y <= -1; y++) {
                        world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.BEDROCK);
                    }
                }
            }
        }

        // Add mystical decorations
        buildMysticalDecorations(world);
    }

    private void buildMysticalDecorations(World world) {
        // Add glowstone pillars at cardinal directions
        int[] positions = {-8, 8};
        for (int x : positions) {
            for (int z : positions) {
                for (int y = 1; y <= 3; y++) {
                    world.getBlockAt(arenaCenter.getBlockX() + x, arenaCenter.getBlockY() + y, arenaCenter.getBlockZ() + z).setType(Material.GLOWSTONE);
                }
            }
        }

        // Central altar
        world.getBlockAt(arenaCenter.getBlockX(), arenaCenter.getBlockY() + 1, arenaCenter.getBlockZ()).setType(Material.ENCHANTING_TABLE);

        // Mystical barriers
        for (int angle = 0; angle < 360; angle += 30) {
            double radians = Math.toRadians(angle);
            int x = (int) (arenaCenter.getX() + 12 * Math.cos(radians));
            int z = (int) (arenaCenter.getZ() + 12 * Math.sin(radians));

            for (int y = 0; y <= 5; y++) {
                world.getBlockAt(x, arenaCenter.getBlockY() + y, z).setType(Material.BARRIER);
            }
        }
    }

    private void teleportPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            Location spawn1 = arenaCenter.clone().add(-6, 1, 0);
            spawn1.setYaw(90);
            player1.teleport(spawn1);
        }

        if (player2 != null) {
            Location spawn2 = arenaCenter.clone().add(6, 1, 0);
            spawn2.setYaw(270);
            player2.teleport(spawn2);
        }
    }

    private void initializeGame() {
        // Create and shuffle deck
        List<OracleCard> deck = createFullDeck();
        Collections.shuffle(deck);

        // Deal 22 cards to each player (full deck split)
        List<OracleCard> player1Cards = new ArrayList<>();
        List<OracleCard> player2Cards = new ArrayList<>();

        for (int i = 0; i < deck.size(); i++) {
            if (i % 2 == 0) {
                player1Cards.add(deck.get(i));
            } else {
                player2Cards.add(deck.get(i));
            }
        }

        playerHands.put(player1UUID, player1Cards);
        playerHands.put(player2UUID, player2Cards);

        // Create inventories
        player1Inventory = Bukkit.createInventory(null, 54, PLAYER1_TITLE);
        player2Inventory = Bukkit.createInventory(null, 54, PLAYER2_TITLE);
    }

    private List<OracleCard> createFullDeck() {
        List<OracleCard> deck = new ArrayList<>();

        // Create two copies of each card (44 total)
        for (int copy = 0; copy < 2; copy++) {
            deck.add(new OracleCard("The Fool", Element.AIR, CardType.CHAOS, 1, "Unpredictable"));
            deck.add(new OracleCard("The Magician", Element.FIRE, CardType.MAGIC, 7, "Amplify"));
            deck.add(new OracleCard("High Priestess", Element.WATER, CardType.MYSTERY, 6, "Reflect"));
            deck.add(new OracleCard("The Empress", Element.EARTH, CardType.NATURE, 5, "Heal"));
            deck.add(new OracleCard("The Emperor", Element.FIRE, CardType.AUTHORITY, 8, "Command"));
            deck.add(new OracleCard("The Hierophant", Element.EARTH, CardType.WISDOM, 4, "Shield"));
            deck.add(new OracleCard("The Lovers", Element.AIR, CardType.EMOTION, 3, "Charm"));
            deck.add(new OracleCard("The Chariot", Element.FIRE, CardType.WAR, 7, "Charge"));
            deck.add(new OracleCard("Strength", Element.EARTH, CardType.PHYSICAL, 6, "Endure"));
            deck.add(new OracleCard("Hermit", Element.EARTH, CardType.WISDOM, 2, "Hide"));
            deck.add(new OracleCard("Wheel of Fortune", Element.AIR, CardType.CHAOS, 5, "Random"));
            deck.add(new OracleCard("Justice", Element.AIR, CardType.BALANCE, 6, "Counter"));
            deck.add(new OracleCard("Hanged Man", Element.WATER, CardType.SACRIFICE, 3, "Stall"));
            deck.add(new OracleCard("Death", Element.WATER, CardType.CHANGE, 9, "Transform"));
            deck.add(new OracleCard("Temperance", Element.WATER, CardType.BALANCE, 4, "Stabilize"));
            deck.add(new OracleCard("The Devil", Element.FIRE, CardType.CORRUPTION, 8, "Bind"));
            deck.add(new OracleCard("The Tower", Element.FIRE, CardType.DESTRUCTION, 9, "Shatter"));
            deck.add(new OracleCard("The Star", Element.WATER, CardType.HOPE, 5, "Restore"));
            deck.add(new OracleCard("The Moon", Element.WATER, CardType.ILLUSION, 4, "Confuse"));
            deck.add(new OracleCard("The Sun", Element.FIRE, CardType.ENERGY, 8, "Energize"));
            deck.add(new OracleCard("Judgement", Element.AIR, CardType.DIVINE, 7, "Purify"));
            deck.add(new OracleCard("The World", Element.EARTH, CardType.COMPLETION, 6, "Finalize"));
        }

        return deck;
    }

    private void beginGame() {
        currentPhase = GamePhase.CARD_SELECTION;
        currentRound = 1;
        currentPlayerTurn = player1UUID; // White goes first

        sendGameInstructions();
        startNewRound();
    }

    private void sendGameInstructions() {
        String instructions =
                "&6=== Oracle Card Game Instructions ===\n" +
                        "&fYou each start with 40 HP and 22 cards.\n" +
                        "&fEach round, select 2 cards to create a scroll combination.\n" +
                        "&fMatching elements create powerful scroll effects!\n" +
                        "&fIf no valid combo: damage = (card1 + card2) ÷ 2\n" +
                        "&fWin by reducing opponent to 0 HP or having more HP after 8 rounds.\n" +
                        "&fYou have 20 seconds to select your cards each round.";

        MessageUtil.sendMessage(getPlayer1(), instructions);
        MessageUtil.sendMessage(getPlayer2(), instructions);

        MessageUtil.sendMessage(getPlayer1(), "&eYour current HP: " + playerHealth.get(player1UUID));
        MessageUtil.sendMessage(getPlayer2(), "&eYour current HP: " + playerHealth.get(player2UUID));
    }

    private void startNewRound() {
        MessageUtil.sendMessage(getPlayer1(), "&6=== Round " + currentRound + "/" + MAX_ROUNDS + " ===");
        MessageUtil.sendMessage(getPlayer2(), "&6=== Round " + currentRound + "/" + MAX_ROUNDS + " ===");

        playerSelectedCards.get(player1UUID).clear();
        playerSelectedCards.get(player2UUID).clear();

        updatePlayerInventory(player1UUID);
        updatePlayerInventory(player2UUID);

        openInventoryForPlayers();
        startTurnTimer();
    }

    private void updatePlayerInventory(UUID playerUUID) {
        List<OracleCard> hand = playerHands.get(playerUUID);
        List<OracleCard> selected = playerSelectedCards.get(playerUUID);
        Inventory inv = playerUUID.equals(player1UUID) ? player1Inventory : player2Inventory;

        inv.clear();

        for (int i = 0; i < hand.size() && i < 45; i++) {
            OracleCard card = hand.get(i);
            boolean isSelected = selected.contains(card);
            ItemStack cardItem = createCardItem(card, isSelected);
            inv.setItem(i, cardItem);
        }

        // Add status panel
        addStatusPanel(inv, playerUUID);
    }

    private void addStatusPanel(Inventory inv, UUID playerUUID) {
        // Health indicator
        ItemStack healthItem = new ItemStack(Material.RED_DYE);
        ItemMeta healthMeta = healthItem.getItemMeta();
        healthMeta.setDisplayName("&cHealth: " + playerHealth.get(playerUUID) + "/" + STARTING_HEALTH);
        healthItem.setItemMeta(healthMeta);
        inv.setItem(45, healthItem);

        // Selected cards count
        ItemStack selectedItem = new ItemStack(Material.PAPER);
        ItemMeta selectedMeta = selectedItem.getItemMeta();
        int selectedCount = playerSelectedCards.get(playerUUID).size();
        selectedMeta.setDisplayName("&eSelected: " + selectedCount + "/2 cards");
        selectedItem.setItemMeta(selectedMeta);
        inv.setItem(46, selectedItem);

        // Round indicator
        ItemStack roundItem = new ItemStack(Material.CLOCK);
        ItemMeta roundMeta = roundItem.getItemMeta();
        roundMeta.setDisplayName("&6Round: " + currentRound + "/" + MAX_ROUNDS);
        roundItem.setItemMeta(roundMeta);
        inv.setItem(47, roundItem);

        // Ready/Confirm button
        List<OracleCard> selected = playerSelectedCards.get(playerUUID);
        if (selected.size() == 2) {
            ItemStack confirmItem = new ItemStack(Material.EMERALD);
            ItemMeta confirmMeta = confirmItem.getItemMeta();
            confirmMeta.setDisplayName("&aConfirm Selection");
            List<String> lore = new ArrayList<>();
            lore.add("&7Selected cards:");
            lore.add("&f1. " + selected.get(0).getName());
            lore.add("&f2. " + selected.get(1).getName());
            confirmMeta.setLore(lore);
            confirmItem.setItemMeta(confirmMeta);
            inv.setItem(53, confirmItem);
        } else {
            ItemStack waitItem = new ItemStack(Material.GRAY_DYE);
            ItemMeta waitMeta = waitItem.getItemMeta();
            waitMeta.setDisplayName("&7Select 2 cards");
            waitItem.setItemMeta(waitMeta);
            inv.setItem(53, waitItem);
        }
    }

    private ItemStack createCardItem(OracleCard card, boolean selected) {
        Material material = getCardMaterial(card);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = selected ? "&e✓ " + card.getName() : "&f" + card.getName();
        meta.setDisplayName(name);

        List<String> lore = new ArrayList<>();
        lore.add("&7Element: &" + getElementColor(card.getElement()) + card.getElement().name());
        lore.add("&7Type: &9" + card.getType().name());
        lore.add("&7Power: &c" + card.getPower());
        lore.add("&7Effect: &d" + card.getEffect());

        if (selected) {
            lore.add("");
            lore.add("&e✓ Selected");
        } else {
            lore.add("");
            lore.add("&7Click to select");
        }

        meta.setLore(lore);

        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);

        return item;
    }

    private Material getCardMaterial(OracleCard card) {
        switch (card.getElement()) {
            case FIRE:
                return Material.RED_STAINED_GLASS;
            case WATER:
                return Material.BLUE_STAINED_GLASS;
            case EARTH:
                return Material.GREEN_STAINED_GLASS;
            case AIR:
                return Material.YELLOW_STAINED_GLASS;
            default:
                return Material.WHITE_STAINED_GLASS;
        }
    }

    private char getElementColor(Element element) {
        switch (element) {
            case FIRE:
                return 'c';
            case WATER:
                return 'b';
            case EARTH:
                return 'a';
            case AIR:
                return 'e';
            default:
                return 'f';
        }
    }

    private void openInventoryForPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            player1.openInventory(player1Inventory);
        }
        if (player2 != null) {
            player2.openInventory(player2Inventory);
        }
    }

    private void startTurnTimer() {
        turnTimeRemaining = 20;

        if (turnTimerTask != null) {
            turnTimerTask.cancel();
        }

        turnTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                turnTimeRemaining--;

                if (turnTimeRemaining <= 0) {
                    handleTimeUp();
                    this.cancel();
                } else if (turnTimeRemaining <= 5) {
                    MessageUtil.sendMessage(getPlayer1(), "&cTime remaining: " + turnTimeRemaining + " seconds");
                    MessageUtil.sendMessage(getPlayer2(), "&cTime remaining: " + turnTimeRemaining + " seconds");
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void handleTimeUp() {
        // Auto-select cards for players who haven't selected 2 cards
        autoSelectCardsForPlayer(player1UUID);
        autoSelectCardsForPlayer(player2UUID);

        resolveRound();
    }

    private void autoSelectCardsForPlayer(UUID playerUUID) {
        List<OracleCard> selected = playerSelectedCards.get(playerUUID);
        List<OracleCard> hand = playerHands.get(playerUUID);

        while (selected.size() < 2 && !hand.isEmpty()) {
            OracleCard card = hand.get(0);
            if (!selected.contains(card)) {
                selected.add(card);
            }
            if (selected.size() < 2 && hand.size() > 1) {
                OracleCard card2 = hand.get(1);
                if (!selected.contains(card2)) {
                    selected.add(card2);
                }
            }
        }

        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            MessageUtil.sendMessage(player, "&cTime's up! Auto-selected cards.");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isActive) return;

        String title = event.getView().getTitle();
        if (!title.equals(PLAYER1_TITLE) && !title.equals(PLAYER2_TITLE)) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        if (!playerUUID.equals(player1UUID) && !playerUUID.equals(player2UUID)) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 53) { // Confirm button
            handleConfirmClick(playerUUID);
        } else if (slot < 45) { // Card slots
            handleCardClick(playerUUID, slot);
        }
    }

    private void handleCardClick(UUID playerUUID, int slot) {
        List<OracleCard> hand = playerHands.get(playerUUID);
        List<OracleCard> selected = playerSelectedCards.get(playerUUID);

        if (slot >= hand.size()) return;

        OracleCard clickedCard = hand.get(slot);

        if (selected.contains(clickedCard)) {
            // Deselect card
            selected.remove(clickedCard);
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_OFF, 1.0f, 1.0f);
            }
        } else if (selected.size() < 2) {
            // Select card
            selected.add(clickedCard);
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.2f);
            }
        } else {
            // Replace first selected card
            selected.set(0, selected.get(1));
            selected.set(1, clickedCard);
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
            }
        }

        updatePlayerInventory(playerUUID);
    }

    private void handleConfirmClick(UUID playerUUID) {
        List<OracleCard> selected = playerSelectedCards.get(playerUUID);

        if (selected.size() != 2) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                MessageUtil.sendMessage(player, "&cYou must select exactly 2 cards!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return;
        }

        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            MessageUtil.sendMessage(player, "&aCards confirmed! Waiting for opponent...");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            player.closeInventory();
        }

        checkBothPlayersReady();
    }

    private void checkBothPlayersReady() {
        boolean player1Ready = playerSelectedCards.get(player1UUID).size() == 2;
        boolean player2Ready = playerSelectedCards.get(player2UUID).size() == 2;

        if (player1Ready && player2Ready) {
            if (turnTimerTask != null) {
                turnTimerTask.cancel();
            }
            resolveRound();
        }
    }

    private void resolveRound() {
        currentPhase = GamePhase.RESOLUTION;

        MessageUtil.sendMessage(getPlayer1(), "&6Resolving round " + currentRound + "...");
        MessageUtil.sendMessage(getPlayer2(), "&6Resolving round " + currentRound + "...");

        // Get selected cards
        List<OracleCard> player1Cards = new ArrayList<>(playerSelectedCards.get(player1UUID));
        List<OracleCard> player2Cards = new ArrayList<>(playerSelectedCards.get(player2UUID));

        // Remove used cards from hands
        playerHands.get(player1UUID).removeAll(player1Cards);
        playerHands.get(player2UUID).removeAll(player2Cards);

        // Resolve combinations
        CombinationResult player1Result = resolveCardCombination(player1Cards);
        CombinationResult player2Result = resolveCardCombination(player2Cards);

        applyRoundEffects(player1Result, player2Result);

        if (checkGameEnd()) {
            return;
        }

        currentRound++;
        if (currentRound <= MAX_ROUNDS) {
            Bukkit.getScheduler().runTaskLater(plugin, this::startNewRound, 60L);
        } else {
            endGameByRounds();
        }
    }

    private CombinationResult resolveCardCombination(List<OracleCard> cards) {
        if (cards.size() != 2) {
            return new CombinationResult("No Combo", 0, EffectType.NONE, "");
        }

        OracleCard card1 = cards.get(0);
        OracleCard card2 = cards.get(1);

        ScrollCombination combo = findScrollCombination(card1, card2);
        if (combo != null) {
            return new CombinationResult(combo.name, combo.damage, combo.effectType, combo.description);
        }

        // No combo fallback
        int fallbackDamage = (card1.getPower() + card2.getPower()) / 2;
        return new CombinationResult("Basic Attack", fallbackDamage, EffectType.DAMAGE,
                "No scroll combination. Basic damage: " + fallbackDamage);
    }

    private ScrollCombination findScrollCombination(OracleCard card1, OracleCard card2) {
        String combo = getComboKey(card1, card2);
        return ScrollCombinations.getCombination(combo);
    }

    private String getComboKey(OracleCard card1, OracleCard card2) {
        // Sort cards alphabetically for consistent lookup
        if (card1.getName().compareTo(card2.getName()) <= 0) {
            return card1.getName() + "+" + card2.getName();
        } else {
            return card2.getName() + "+" + card1.getName();
        }
    }

    private void applyRoundEffects(CombinationResult player1Result, CombinationResult player2Result) {
        // Show what each player played
        MessageUtil.sendMessage(getPlayer1(), "&eYour combo: &6" + player1Result.name +
                " &7(" + player1Result.description + ")");
        MessageUtil.sendMessage(getPlayer2(), "&eYour combo: &6" + player2Result.name +
                " &7(" + player2Result.description + ")");

        MessageUtil.sendMessage(getPlayer1(), "&eOpponent's combo: &6" + player2Result.name);
        MessageUtil.sendMessage(getPlayer2(), "&eOpponent's combo: &6" + player1Result.name);

        // Apply effects in priority order
        int player1FinalDamage = player1Result.damage;
        int player2FinalDamage = player2Result.damage;

        // 1. Apply defensive effects first
        if (player1Result.effectType == EffectType.SHIELD) {
            player2FinalDamage = Math.max(0, player2FinalDamage - 10);
            MessageUtil.sendMessage(getPlayer1(), "&aYour shield blocked 10 damage!");
        }
        if (player2Result.effectType == EffectType.SHIELD) {
            player1FinalDamage = Math.max(0, player1FinalDamage - 10);
            MessageUtil.sendMessage(getPlayer2(), "&aYour shield blocked 10 damage!");
        }

        // 2. Apply mirror effects
        if (player1Result.effectType == EffectType.REFLECT) {
            int reflected = player2FinalDamage / 2;
            player2FinalDamage = reflected;
            MessageUtil.sendMessage(getPlayer1(), "&dYou reflected " + reflected + " damage back!");
        }
        if (player2Result.effectType == EffectType.REFLECT) {
            int reflected = player1FinalDamage / 2;
            player1FinalDamage = reflected;
            MessageUtil.sendMessage(getPlayer2(), "&dYou reflected " + reflected + " damage back!");
        }

        // 3. Apply damage
        if (player1FinalDamage > 0) {
            int newHealth = playerHealth.get(player2UUID) - player1FinalDamage;
            playerHealth.put(player2UUID, Math.max(0, newHealth));
            playerTotalDamage.put(player1UUID, playerTotalDamage.get(player1UUID) + player1FinalDamage);

            MessageUtil.sendMessage(getPlayer1(), "&cYou dealt " + player1FinalDamage + " damage!");
            MessageUtil.sendMessage(getPlayer2(), "&cYou took " + player1FinalDamage + " damage!");
        }

        if (player2FinalDamage > 0) {
            int newHealth = playerHealth.get(player1UUID) - player2FinalDamage;
            playerHealth.put(player1UUID, Math.max(0, newHealth));
            playerTotalDamage.put(player2UUID, playerTotalDamage.get(player2UUID) + player2FinalDamage);

            MessageUtil.sendMessage(getPlayer2(), "&cYou dealt " + player2FinalDamage + " damage!");
            MessageUtil.sendMessage(getPlayer1(), "&cYou took " + player2FinalDamage + " damage!");
        }

        // 4. Apply healing effects
        if (player1Result.effectType == EffectType.HEAL) {
            int healAmount = Math.min(10, STARTING_HEALTH - playerHealth.get(player1UUID));
            playerHealth.put(player1UUID, playerHealth.get(player1UUID) + healAmount);
            MessageUtil.sendMessage(getPlayer1(), "&aYou healed " + healAmount + " HP!");
        }
        if (player2Result.effectType == EffectType.HEAL) {
            int healAmount = Math.min(10, STARTING_HEALTH - playerHealth.get(player2UUID));
            playerHealth.put(player2UUID, playerHealth.get(player2UUID) + healAmount);
            MessageUtil.sendMessage(getPlayer2(), "&aYou healed " + healAmount + " HP!");
        }

        // Show current health
        MessageUtil.sendMessage(getPlayer1(), "&eYour HP: &c" + playerHealth.get(player1UUID) +
                "&7/&c" + STARTING_HEALTH);
        MessageUtil.sendMessage(getPlayer2(), "&eYour HP: &c" + playerHealth.get(player2UUID) +
                "&7/&c" + STARTING_HEALTH);
    }

    private boolean checkGameEnd() {
        int player1HP = playerHealth.get(player1UUID);
        int player2HP = playerHealth.get(player2UUID);

        if (player1HP <= 0 && player2HP <= 0) {
            // Both died - determine by total damage dealt
            int player1Damage = playerTotalDamage.get(player1UUID);
            int player2Damage = playerTotalDamage.get(player2UUID);

            UUID winner = player1Damage >= player2Damage ? player1UUID : player2UUID;
            MessageUtil.sendMessage(getPlayer1(), "§6Both players eliminated! Winner by damage dealt!");
            MessageUtil.sendMessage(getPlayer2(), "§6Both players eliminated! Winner by damage dealt!");
            endGame(winner);
            return true;
        } else if (player1HP <= 0) {
            MessageUtil.sendMessage(getPlayer1(), "§cYou have been defeated!");
            MessageUtil.sendMessage(getPlayer2(), "§aYou win! Your opponent has been defeated!");
            endGame(player2UUID);
            return true;
        } else if (player2HP <= 0) {
            MessageUtil.sendMessage(getPlayer1(), "§aYou win! Your opponent has been defeated!");
            MessageUtil.sendMessage(getPlayer2(), "§cYou have been defeated!");
            endGame(player1UUID);
            return true;
        }

        return false;
    }

    private void endGameByRounds() {
        int player1HP = playerHealth.get(player1UUID);
        int player2HP = playerHealth.get(player2UUID);

        UUID winner;
        if (player1HP > player2HP) {
            winner = player1UUID;
        } else if (player2HP > player1HP) {
            winner = player2UUID;
        } else {
            // Tie on HP - determine by total damage dealt
            int player1Damage = playerTotalDamage.get(player1UUID);
            int player2Damage = playerTotalDamage.get(player2UUID);
            winner = player1Damage >= player2Damage ? player1UUID : player2UUID;
        }

        MessageUtil.sendMessage(getPlayer1(), "§6All rounds completed!");
        MessageUtil.sendMessage(getPlayer2(), "§6All rounds completed!");
        MessageUtil.sendMessage(getPlayer1(), "§eFinal HP - You: " + player1HP + " | Opponent: " + player2HP);
        MessageUtil.sendMessage(getPlayer2(), "§eFinal HP - You: " + player2HP + " | Opponent: " + player1HP);

        endGame(winner);
    }
}