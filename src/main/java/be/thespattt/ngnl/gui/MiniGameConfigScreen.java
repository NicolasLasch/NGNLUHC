package be.thespattt.ngnl.gui;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.config.GameConfig;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mini-game configuration screen
 */
public class MiniGameConfigScreen extends ConfigScreen {

    // Item slots
    private static final int WINNER_LOSS_SLOT = 11;
    private static final int LOSER_LOSS_SLOT = 13;
    private static final int REMATCH_CHANCE_SLOT = 15;
    private static final int RANDOM_START_SLOT = 29;
    private static final int REROLL_ALLOWED_SLOT = 31;
    private static final int BOOK_REWARD_SLOT = 33;

    // Config values
    private double winnerHealthLoss;
    private double loserHealthLoss;
    private double rematchChance;
    private boolean randomStart;
    private boolean rerollAllowed;
    private boolean bookReward;

    // Mini-game enabled states
    private Map<MiniGameType, Boolean> enabledMiniGames;
    private List<MiniGameType> allMiniGames;

    // Current page of mini-games
    private int currentPage = 0;
    private final int GAMES_PER_PAGE = 9;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param player Player viewing the screen
     */
    public MiniGameConfigScreen(NoGameNoLife plugin, Player player) {
        super(plugin, player, "§8Mini-Game Configuration", 54);
        // Initialize collections before calling super constructor
        this.allMiniGames = new ArrayList<>();
        this.enabledMiniGames = new HashMap<>();

        // Then call the parent constructor, which will trigger initialize()
    }

    @Override
    protected void initialize() {
        // Load current values
        GameConfig config = getConfig();
        winnerHealthLoss = config.getMiniGameWinnerHealthLoss();
        loserHealthLoss = config.getMiniGameLoserHealthLoss();
        rematchChance = config.getMiniGameRematchChance();
        randomStart = config.isMiniGameRandomStart();
        rerollAllowed = config.isMiniGameRerollAllowed();
        bookReward = config.isMiniGameBookReward();

        // Get all mini-game types
        for (MiniGameType type : MiniGameType.values()) {
            allMiniGames.add(type);
            enabledMiniGames.put(type, config.isMiniGameEnabled(type));
        }

        // Create and add items
        updateItems();

        // Add navigation buttons
        boolean hasNextPage = (currentPage + 1) * GAMES_PER_PAGE < allMiniGames.size();
        addNavigationButtons(true, true, hasNextPage);

        // Fill empty slots
        fillEmptySlots();
    }

    /**
     * Update the items in the inventory based on current values
     */
    private void updateItems() {
        // Clear current items (except navigation)
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, null);
        }

        // Add configuration items
        ItemStack winnerLossItem = new ItemBuilder(Material.RED_DYE)
                .name("&c&lWinner Health Loss")
                .lore(
                        "&7Current: &f" + winnerHealthLoss + " hearts",
                        "&7The amount of health a player loses",
                        "&7when they win a PvP but lose the mini-game.",
                        "",
                        "&eLeft-click &7to increase by 0.5 (max 10.0)",
                        "&eRight-click &7to decrease by 0.5 (min 1.0)"
                )
                .build();
        inventory.setItem(WINNER_LOSS_SLOT, winnerLossItem);

        ItemStack loserLossItem = new ItemBuilder(Material.REDSTONE)
                .name("&c&lLoser Health Loss")
                .lore(
                        "&7Current: &f" + loserHealthLoss + " hearts",
                        "&7The amount of health a player loses",
                        "&7when they lose both the PvP and the mini-game.",
                        "",
                        "&eLeft-click &7to increase by 0.5 (max 10.0)",
                        "&eRight-click &7to decrease by 0.5 (min 1.0)"
                )
                .build();
        inventory.setItem(LOSER_LOSS_SLOT, loserLossItem);

        ItemStack rematchItem = new ItemBuilder(Material.CLOCK)
                .name("&e&lRematch Chance")
                .lore(
                        "&7Current: &f" + (rematchChance * 100) + "%",
                        "&7The chance that a player will get a",
                        "&7rematch after losing a mini-game.",
                        "",
                        "&eLeft-click &7to increase by 5% (max 100%)",
                        "&eRight-click &7to decrease by 5% (min 0%)"
                )
                .build();
        inventory.setItem(REMATCH_CHANCE_SLOT, rematchItem);

        ItemStack randomStartItem = new ItemBuilder(Material.COMPASS)
                .name("&e&lRandom Mini-Game Start")
                .lore(
                        "&7Current: " + (randomStart ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, mini-games will start with",
                        "&7a random mini-game type. Otherwise,",
                        "&7they will use a weighted selection.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(RANDOM_START_SLOT, getToggleItem(randomStart));

        ItemStack rerollItem = new ItemBuilder(Material.EMERALD)
                .name("&e&lReroll Allowed")
                .lore(
                        "&7Current: " + (rerollAllowed ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, players who win the PvP",
                        "&7can reroll the mini-game type once.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(REROLL_ALLOWED_SLOT, getToggleItem(rerollAllowed));

        ItemStack bookRewardItem = new ItemBuilder(Material.ENCHANTED_BOOK)
                .name("&e&lBook Reward")
                .lore(
                        "&7Current: " + (bookReward ? "&aEnabled" : "&cDisabled"),
                        "&7If enabled, players who win a mini-game",
                        "&7will receive a random enchanted book.",
                        "",
                        "&eClick &7to toggle"
                )
                .build();
        inventory.setItem(BOOK_REWARD_SLOT, getToggleItem(bookReward));

        // Display mini-games for current page
        int startIndex = currentPage * GAMES_PER_PAGE;
        int endIndex = Math.min(startIndex + GAMES_PER_PAGE, allMiniGames.size());

        int row = 3;
        int col = 0;
        for (int i = startIndex; i < endIndex; i++) {
            MiniGameType gameType = allMiniGames.get(i);
            boolean enabled = enabledMiniGames.get(gameType);

            Material material;
            switch (gameType) {
                case SPLEEF:
                    material = Material.SNOW_BLOCK;
                    break;
                case TNT_RUN:
                    material = Material.TNT;
                    break;
                case PARKOUR:
                    material = Material.LADDER;
                    break;
                case BLOC_PARTY:
                    material = Material.GRASS_BLOCK;
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
                    material = Material.BRAIN_CORAL;
                    break;
                case MENTAL_CHESS:
                    material = Material.BONE;
                    break;
                case MEMORY_GAME:
                    material = Material.MAP;
                    break;
                case MATERIALIZATION_SHIRITORI:
                    material = Material.NETHER_STAR;
                    break;
                default:
                    material = Material.NETHER_STAR;
                    break;
            }

            ItemStack gameItem = new ItemBuilder(material)
                    .name("&a&l" + formatGameName(gameType.name()))
                    .lore(
                            "&7Status: " + (enabled ? "&aEnabled" : "&cDisabled"),
                            "&7This mini-game will " + (enabled ? "" : "not ") + "be available",
                            "&7during the qualification phase.",
                            "",
                            "&eClick &7to toggle enabled/disabled"
                    )
                    .build();

            inventory.setItem(row * 9 + col + 1, gameItem);

            col++;
            if (col >= 7) {
                col = 0;
                row++;
            }
        }

        // Add page indicator
        int totalPages = (int) Math.ceil((double) allMiniGames.size() / GAMES_PER_PAGE);
        ItemStack pageItem = new ItemBuilder(Material.PAPER)
                .name("&e&lPage " + (currentPage + 1) + "/" + totalPages)
                .lore(
                        "&7Showing mini-games " + (startIndex + 1) + "-" + endIndex,
                        "&7out of " + allMiniGames.size() + " total mini-games"
                )
                .build();
        inventory.setItem(4, pageItem);
    }

    /**
     * Format a game name for display
     *
     * @param name The game name in enum form
     * @return Formatted name
     */
    private String formatGameName(String name) {
        String[] words = name.replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(word.substring(0, 1).toUpperCase());
                result.append(word.substring(1).toLowerCase());
                result.append(" ");
            }
        }

        return result.toString().trim();
    }

    @Override
    public void handleClick(int slot, boolean isLeftClick, boolean isRightClick, boolean isShiftClick) {
        boolean valueChanged = true;

        switch (slot) {
            case WINNER_LOSS_SLOT:
                if (isLeftClick && winnerHealthLoss < 10.0) {
                    winnerHealthLoss = Math.min(10.0, winnerHealthLoss + 0.5);
                } else if (isRightClick && winnerHealthLoss > 1.0) {
                    winnerHealthLoss = Math.max(1.0, winnerHealthLoss - 0.5);
                } else {
                    valueChanged = false;
                }
                break;

            case LOSER_LOSS_SLOT:
                if (isLeftClick && loserHealthLoss < 10.0) {
                    loserHealthLoss = Math.min(10.0, loserHealthLoss + 0.5);
                } else if (isRightClick && loserHealthLoss > 1.0) {
                    loserHealthLoss = Math.max(1.0, loserHealthLoss - 0.5);
                } else {
                    valueChanged = false;
                }
                break;

            case REMATCH_CHANCE_SLOT:
                if (isLeftClick && rematchChance < 1.0) {
                    rematchChance = Math.min(1.0, rematchChance + 0.05);
                    rematchChance = Math.round(rematchChance * 100) / 100.0; // Round to 2 decimal places
                } else if (isRightClick && rematchChance > 0.0) {
                    rematchChance = Math.max(0.0, rematchChance - 0.05);
                    rematchChance = Math.round(rematchChance * 100) / 100.0; // Round to 2 decimal places
                } else {
                    valueChanged = false;
                }
                break;

            case RANDOM_START_SLOT:
                randomStart = !randomStart;
                break;

            case REROLL_ALLOWED_SLOT:
                rerollAllowed = !rerollAllowed;
                break;

            case BOOK_REWARD_SLOT:
                bookReward = !bookReward;
                break;

            case BACK_SLOT:
                openScreen(ConfigGUIManager.ScreenType.MAIN);
                return;

            case SAVE_SLOT:
                saveChanges();
                player.closeInventory();
                return;

            case NEXT_SLOT:
                int totalPages = (int) Math.ceil((double) allMiniGames.size() / GAMES_PER_PAGE);
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    updateItems();
                }
                return;

            default:
                // Check if this is a mini-game slot
                int row = slot / 9;
                int col = slot % 9 - 1;

                if (row >= 3 && row <= 4 && col >= 0 && col < 7) {
                    int index = currentPage * GAMES_PER_PAGE + (row - 3) * 7 + col;

                    if (index >= 0 && index < allMiniGames.size()) {
                        MiniGameType gameType = allMiniGames.get(index);
                        enabledMiniGames.put(gameType, !enabledMiniGames.get(gameType));
                    } else {
                        valueChanged = false;
                    }
                } else {
                    valueChanged = false;
                }
                break;
        }

        if (valueChanged) {
            markChanged();
            updateItems();
        }
    }

    @Override
    public void saveChanges() {
        if (!needsSaving()) {
            return;
        }

        GameConfig config = getConfig();
        config.setMiniGameWinnerHealthLoss(winnerHealthLoss);
        config.setMiniGameLoserHealthLoss(loserHealthLoss);
        config.setMiniGameRematchChance(rematchChance);
        config.setMiniGameRandomStart(randomStart);
        config.setMiniGameRerollAllowed(rerollAllowed);
        config.setMiniGameBookReward(bookReward);

        // Save mini-game enabled states
        for (Map.Entry<MiniGameType, Boolean> entry : enabledMiniGames.entrySet()) {
            config.setMiniGameEnabled(entry.getKey(), entry.getValue());
        }

        plugin.getConfigManager().saveConfig();
    }
}