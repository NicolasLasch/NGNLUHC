package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Chess minigame with a GUI interface - Modified for 6x8 board
 */
public class ChessMiniGame extends MiniGameBase implements Listener {

    // Game state
    private ChessGameState gameState = ChessGameState.SETUP;
    private UUID currentPlayerTurn;
    private int moveTimeRemaining = 30; // Time in seconds for a move
    private BukkitTask moveTimerTask;

    // Chess board data - now 6x8 (6 rows, 8 columns)
    private final ChessPiece[][] board = new ChessPiece[6][8];
    private Inventory player1Board; // White player's view
    private Inventory player2Board; // Black player's view

    // Selected piece data
    private int selectedRow = -1;
    private int selectedCol = -1;
    private final Set<String> possibleMoves = new HashSet<>();

    // Chess room coordinates in the mini-game world
    private final int chessRoomX = 0;
    private final int chessRoomY = 72;
    private final int chessRoomZ = 0;

    // Move history
    private final List<String> moveHistory = new ArrayList<>();

    // GUI constants
    private static final String WHITE_PLAYER_BOARD_TITLE = "Chess (White)";
    private static final String BLACK_PLAYER_BOARD_TITLE = "Chess (Black)";

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     * @param player1UUID UUID of player 1 (white)
     * @param player2UUID UUID of player 2 (black)
     * @param player1WonPvP True if player 1 won the PvP
     */
    public ChessMiniGame(NoGameNoLife plugin, UUID player1UUID, UUID player2UUID, boolean player1WonPvP) {
        super(plugin, player1UUID, player2UUID, MiniGameType.MENTAL_CHESS, player1WonPvP);
    }

    /**
     * Called after the game room is created and players are teleported
     * Implementation of the abstract method from MiniGameBase
     */
    @Override
    protected void onGameStart() {
        // Create inventories for both players - 6 rows of 8 columns is 48 slots
        // This fits within the 54 slot limit (6 rows of 9)
        player1Board = Bukkit.createInventory(null, 6 * 9, WHITE_PLAYER_BOARD_TITLE);
        player2Board = Bukkit.createInventory(null, 6 * 9, BLACK_PLAYER_BOARD_TITLE);

        // Initialize chess board
        initializeBoard();

        // Set the initial player turn (white moves first)
        currentPlayerTurn = player1UUID;
        gameState = ChessGameState.PLAYING;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            openBoardForPlayers();      // ⬅️ Ouvre après 5 ticks
            startMoveTimer();           // ⬅️ Lance le timer
            sendInstructions();         // ⬅️ Envoie les messages
        }, 5L);
    }

    public void reopenBoard(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !isActive) return;

        if (playerUUID.equals(player1UUID)) {
            player.openInventory(player1Board);
        } else if (playerUUID.equals(player2UUID)) {
            player.openInventory(player2Board);
        }
    }


    @Override
    public void startGame() {
        super.startGame();

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Setup chess room
        setupChessRoom();

        // Teleport players to chess room
        teleportPlayersToChessRoom();

        // The rest of the initialization is handled in onGameStart()
    }

    /**
     * Set up the chess room
     */
    private void setupChessRoom() {
        // Create a 10x5x5 wooden room for the chess game
        World gameWorld = getOrCreateMinigameWorld();
        if (gameWorld == null) {
            return;
        }

        // Define the room dimensions and position
        int roomWidth = 12;
        int roomHeight = 5;
        int roomDepth = 10;

        int startX = chessRoomX - (roomWidth / 2);
        int startY = chessRoomY;
        int startZ = chessRoomZ - (roomDepth / 2);

        // Clear the area first
        for (int x = 0; x < roomWidth + 2; x++) {
            for (int y = 0; y < roomHeight + 2; y++) {
                for (int z = 0; z < roomDepth + 2; z++) {
                    Block block = gameWorld.getBlockAt(startX - 1 + x, startY - 1 + y, startZ - 1 + z);
                    block.setType(Material.AIR);
                }
            }
        }

        // Build the floor
        for (int x = 0; x < roomWidth; x++) {
            for (int z = 0; z < roomDepth; z++) {
                Block block = gameWorld.getBlockAt(startX + x, startY, startZ + z);
                block.setType(Material.DARK_OAK_PLANKS);
            }
        }

        // Build the ceiling
        for (int x = 0; x < roomWidth; x++) {
            for (int z = 0; z < roomDepth; z++) {
                Block block = gameWorld.getBlockAt(startX + x, startY + roomHeight - 1, startZ + z);
                block.setType(Material.DARK_OAK_PLANKS);
            }
        }

        // Build the walls
        // North and south walls
        for (int x = 0; x < roomWidth; x++) {
            for (int y = 1; y < roomHeight - 1; y++) {
                // North wall
                Block northBlock = gameWorld.getBlockAt(startX + x, startY + y, startZ);
                northBlock.setType(Material.SPRUCE_PLANKS);

                // South wall
                Block southBlock = gameWorld.getBlockAt(startX + x, startY + y, startZ + roomDepth - 1);
                southBlock.setType(Material.SPRUCE_PLANKS);
            }
        }

        // East and west walls
        for (int z = 0; z < roomDepth; z++) {
            for (int y = 1; y < roomHeight - 1; y++) {
                // East wall
                Block eastBlock = gameWorld.getBlockAt(startX, startY + y, startZ + z);
                eastBlock.setType(Material.SPRUCE_PLANKS);

                // West wall
                Block westBlock = gameWorld.getBlockAt(startX + roomWidth - 1, startY + y, startZ + z);
                westBlock.setType(Material.SPRUCE_PLANKS);
            }
        }

        // Add some light sources
        gameWorld.getBlockAt(startX + 2, startY + 3, startZ + 2).setType(Material.GLOWSTONE);
        gameWorld.getBlockAt(startX + roomWidth - 3, startY + 3, startZ + 2).setType(Material.GLOWSTONE);
        gameWorld.getBlockAt(startX + 2, startY + 3, startZ + roomDepth - 3).setType(Material.GLOWSTONE);
        gameWorld.getBlockAt(startX + roomWidth - 3, startY + 3, startZ + roomDepth - 3).setType(Material.GLOWSTONE);

        // Add some decorations - seats for players
        gameWorld.getBlockAt(startX + 2, startY + 1, startZ + 1).setType(Material.OAK_STAIRS);
        gameWorld.getBlockAt(startX + roomWidth - 3, startY + 1, startZ + roomDepth - 2).setType(Material.OAK_STAIRS);

        // Add a small table in the center
        gameWorld.getBlockAt(chessRoomX, startY + 1, chessRoomZ).setType(Material.CRAFTING_TABLE);
    }

    /**
     * Teleport players to the chess room
     */
    private void teleportPlayersToChessRoom() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        World miniGameWorld = getOrCreateMinigameWorld();
        if (miniGameWorld == null) {
            // Fallback to the default world if mini-game world doesn't exist
            miniGameWorld = Bukkit.getWorlds().get(0);
        }

        if (player1 != null) {
            Location whiteLocation = new Location(miniGameWorld,
                    chessRoomX + 2, chessRoomY + 1, chessRoomZ + 2, 45, 0); // White position
            player1.teleport(whiteLocation);
        }

        if (player2 != null) {
            Location blackLocation = new Location(miniGameWorld,
                    chessRoomX - 2, chessRoomY + 1, chessRoomZ - 2, 225, 0); // Black position
            player2.teleport(blackLocation);
        }
    }

    /**
     * Initialize the chess board for a 6x8 setup
     */
    private void initializeBoard() {
        // Clear the board
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 8; col++) {
                board[row][col] = null;
            }
        }

        // Place pawns (6 pawns for each side)
        for (int col = 0; col < 6; col++) {
            board[col][1] = new ChessPiece(ChessPieceType.PAWN, true);  // White pawns
            board[col][6] = new ChessPiece(ChessPieceType.PAWN, false); // Black pawns
        }

        // Place rooks
        board[0][0] = new ChessPiece(ChessPieceType.ROOK, true);
        board[5][0] = new ChessPiece(ChessPieceType.ROOK, true);

        board[0][7] = new ChessPiece(ChessPieceType.ROOK, false);
        board[5][7] = new ChessPiece(ChessPieceType.ROOK, false);

        // Place knights (only one per side)
        board[1][0] = new ChessPiece(ChessPieceType.KNIGHT, true);
        board[4][7] = new ChessPiece(ChessPieceType.KNIGHT, false);

        // Place bishops (only one per side)
        board[4][0] = new ChessPiece(ChessPieceType.BISHOP, true);
        board[1][7] = new ChessPiece(ChessPieceType.BISHOP, false);

        // Place queens
        board[2][0] = new ChessPiece(ChessPieceType.QUEEN, true);
        board[3][7] = new ChessPiece(ChessPieceType.QUEEN, false);

        // Place kings
        board[3][0] = new ChessPiece(ChessPieceType.KING, true);
        board[2][7] = new ChessPiece(ChessPieceType.KING, false);

        // Update the visual boards
        updateBoards();
    }

    /**
     * Update the visual representation of the boards for both players
     */
    private void updateBoards() {
        // Clear both boards
        player1Board.clear();
        player2Board.clear();

        // Populate the boards
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 8; col++) {
                // Calculate slot position - horizontal layout (left to right, top to bottom)
                int whiteSlot = row * 9 + col;
                // Calculate slot position - black's view (inverted)
                int blackSlot = (5 - row) * 9 + (7 - col);

                // Set background color for slot
                ItemStack bgItem = createBackgroundItem(row, col);
                player1Board.setItem(whiteSlot, bgItem);
                player2Board.setItem(blackSlot, bgItem);

                // If there's a piece at this position, add it
                if (board[row][col] != null) {
                    ItemStack pieceItem = createPieceItem(board[row][col], row, col);
                    player1Board.setItem(whiteSlot, pieceItem);
                    player2Board.setItem(blackSlot, pieceItem);
                }

                // Highlight selected position
                if (row == selectedRow && col == selectedCol) {
                    ItemStack selectedItem = board[row][col] != null ?
                            createSelectedPieceItem(board[row][col], row, col) :
                            createSelectedEmptyItem(row, col);

                    player1Board.setItem(whiteSlot, selectedItem);
                    player2Board.setItem(blackSlot, selectedItem);
                }

                // Highlight possible moves
                String pos = getPositionName(row, col);
                if (possibleMoves.contains(pos)) {
                    ItemStack possibleMoveItem = board[row][col] != null ?
                            createPossibleMoveWithPieceItem(board[row][col], row, col) :
                            createPossibleMoveEmptyItem(row, col);

                    player1Board.setItem(whiteSlot, possibleMoveItem);
                    player2Board.setItem(blackSlot, possibleMoveItem);
                }
            }
        }

        // Add navigation buttons in the last column
        addNavigationButtons();
    }

    /**
     * Add navigation buttons to the inventory
     */
    private void addNavigationButtons() {
        // Add turn indicator in last column
        for (int row = 0; row < 6; row++) {
            int slot = row * 9 + 8; // Last column

            ItemStack item;
            if (row == 0) {
                // Turn indicator
                boolean isWhiteTurn = (currentPlayerTurn == null || currentPlayerTurn.equals(player1UUID));

                item = new ItemStack(isWhiteTurn ? Material.WHITE_WOOL : Material.BLACK_WOOL);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§6Current Turn: " + (isWhiteTurn ? "§fWhite" : "§0Black"));
                item.setItemMeta(meta);
            }
            else if (row == 5) {
                // Forfeit button
                item = new ItemStack(Material.BARRIER);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§cForfeit Game");
                item.setItemMeta(meta);
            }
            else {
                // Just a separator
                item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(" ");
                item.setItemMeta(meta);
            }

            player1Board.setItem(slot, item);
            player2Board.setItem((5 - row) * 9 + 8, item); // Inverted for black player
        }
    }

    /**
     * Create the background item for a chess square
     */
    private ItemStack createBackgroundItem(int row, int col) {
        boolean isWhiteSquare = (row + col) % 2 == 0;
        Material material = isWhiteSquare ? Material.WHITE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(getPositionName(row, col));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create an item representing a chess piece
     */
    private ItemStack createPieceItem(ChessPiece piece, int row, int col) {
        Material material = getChessPieceMaterial(piece);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(piece.isWhite() ? "§fWhite " + piece.getType().name() : "§0Black " + piece.getType().name());
        List<String> lore = new ArrayList<>();
        lore.add("§7Position: " + getPositionName(row, col));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create an item for a selected chess piece
     */
    private ItemStack createSelectedPieceItem(ChessPiece piece, int row, int col) {
        ItemStack item = createPieceItem(piece, row, col);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        lore.add("§e✓ Selected");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create an item for a selected empty square
     */
    private ItemStack createSelectedEmptyItem(int row, int col) {
        ItemStack item = createBackgroundItem(row, col);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("§e✓ Selected");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create an item for a possible move square with a piece
     */
    private ItemStack createPossibleMoveWithPieceItem(ChessPiece piece, int row, int col) {
        ItemStack item = createPieceItem(piece, row, col);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        lore.add("§a➢ Possible move");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create an item for a possible move empty square
     */
    private ItemStack createPossibleMoveEmptyItem(int row, int col) {
        boolean isWhiteSquare = (row + col) % 2 == 0;
        Material material = isWhiteSquare ? Material.LIME_WOOL : Material.GREEN_WOOL;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(getPositionName(row, col));
        List<String> lore = new ArrayList<>();
        lore.add("§a➢ Possible move");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Map a chess piece to a material for display
     */
    private Material getChessPieceMaterial(ChessPiece piece) {
        if (piece.isWhite()) {
            switch (piece.getType()) {
                case PAWN: return Material.WHITE_CANDLE;
                case ROOK: return Material.END_ROD;
                case KNIGHT: return Material.HORSE_SPAWN_EGG;
                case BISHOP: return Material.WHITE_BANNER;
                case QUEEN: return Material.DIAMOND;
                case KING: return Material.GOLDEN_APPLE;
                default: return Material.WHITE_WOOL;
            }
        } else {
            switch (piece.getType()) {
                case PAWN: return Material.BLACK_CANDLE;
                case ROOK: return Material.DARK_OAK_LOG;
                case KNIGHT: return Material.DONKEY_SPAWN_EGG;
                case BISHOP: return Material.BLACK_BANNER;
                case QUEEN: return Material.NETHERITE_INGOT;
                case KING: return Material.ENCHANTED_GOLDEN_APPLE;
                default: return Material.BLACK_WOOL;
            }
        }
    }

    /**
     * Convert a row and column to chess notation (e.g., "e4")
     * Adapted for 6x8 board
     */
    private String getPositionName(int row, int col) {
        char file = (char) ('a' + col);
        int rank = row + 1;
        return file + "" + rank;
    }

    /**
     * Parse a position name to get row and column
     * Adapted for 6x8 board
     */
    private int[] getPositionRowCol(String position) {
        char file = position.charAt(0);
        int rank = Character.getNumericValue(position.charAt(1));
        int col = file - 'a';
        int row = rank - 1;
        return new int[] {row, col};
    }

    /**
     * Open the board for both players
     */
    private void openBoardForPlayers() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            player1.openInventory(player1Board);
        }

        if (player2 != null) {
            player2.openInventory(player2Board);
        }
    }

    /**
     * Start the move timer
     */
    private void startMoveTimer() {
        moveTimeRemaining = 30; // Reset to 30 seconds

        // Cancel any existing timer
        if (moveTimerTask != null) {
            moveTimerTask.cancel();
        }

        // Start a new timer
        moveTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            moveTimeRemaining--;

            // Update timer display every 5 seconds or when <= 5 seconds
            if (moveTimeRemaining % 5 == 0 || moveTimeRemaining <= 5) {
                Player currentPlayer = currentPlayerTurn.equals(player1UUID) ? getPlayer1() : getPlayer2();
                if (currentPlayer != null) {
                    MessageUtil.sendMessage(currentPlayer, "&6Time remaining: &e" + moveTimeRemaining + " seconds");
                }
            }

            // Time's up - forfeit turn
            if (moveTimeRemaining <= 0) {
                handleTimeOut();
            }
        }, 20L, 20L); // 1 second interval
    }

    /**
     * Handle timeout when a player doesn't move in time
     */
    private void handleTimeOut() {
        Player timeoutPlayer = currentPlayerTurn.equals(player1UUID) ? getPlayer1() : getPlayer2();
        if (timeoutPlayer != null) {
            MessageUtil.sendMessage(timeoutPlayer, "&cYou ran out of time! Forfeiting your turn.");
        }

        // Switch turns
        switchTurns();
    }

    /**
     * Switch turns between players
     */
    private void switchTurns() {
        // Switch the current player
        currentPlayerTurn = currentPlayerTurn.equals(player1UUID) ? player2UUID : player1UUID;

        // Reset the selected position and possible moves
        selectedRow = -1;
        selectedCol = -1;
        possibleMoves.clear();

        // Update the boards
        updateBoards();

        // Restart the move timer
        startMoveTimer();

        // Notify players
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            boolean isPlayerTurn = currentPlayerTurn.equals(player1UUID);
            MessageUtil.sendMessage(player1, isPlayerTurn ?
                    "&aIt's your turn (White)!" :
                    "&7Waiting for opponent's move (Black)...");

            // Play sound for turn notification
            player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, isPlayerTurn ? 1.5f : 1.0f);
        }

        if (player2 != null) {
            boolean isPlayerTurn = currentPlayerTurn.equals(player2UUID);
            MessageUtil.sendMessage(player2, isPlayerTurn ?
                    "&aIt's your turn (Black)!" :
                    "&7Waiting for opponent's move (White)...");

            // Play sound for turn notification
            player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, isPlayerTurn ? 1.5f : 1.0f);
        }

        // Check for game-ending conditions (checkmate, stalemate, etc.)
        checkGameEndConditions();
    }

    /**
     * Send game instructions to players
     */
    private void sendInstructions() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        String instructions =
                "&6=== Modified Chess GUI Instructions ===\n" +
                        "&fThis is a 6x8 chess game with a horizontal board.\n" +
                        "&fEach side has 6 pawns, 2 rooks, 1 knight, 1 bishop, 1 queen, and 1 king.\n" +
                        "&fWhite (Player 1) moves first.\n" +
                        "&fYou have 30 seconds to make each move.\n" +
                        "&f- First click on a piece to select it\n" +
                        "&f- Then click on a highlighted square to move\n" +
                        "&fPossible moves will be highlighted in green.\n" +
                        "&fThe selected piece will be highlighted in yellow.\n";

        if (player1 != null) {
            MessageUtil.sendMessage(player1, instructions);
            MessageUtil.sendMessage(player1, "&fYou are playing as &fWhite&f.");
        }

        if (player2 != null) {
            MessageUtil.sendMessage(player2, instructions);
            MessageUtil.sendMessage(player2, "&fYou are playing as &0Black&f.");
        }
    }

    /**
     * Calculate linear moves in a given direction
     */
    private void calculateLinearMoves(int row, int col, boolean isWhite, int rowOffset, int colOffset) {
        int newRow = row + rowOffset;
        int newCol = col + colOffset;

        while (newRow >= 0 && newRow < 6 && newCol >= 0 && newCol < 8) {
            if (board[newRow][newCol] == null) {
                // Empty square, valid move
                possibleMoves.add(getPositionName(newRow, newCol));
            } else {
                // Occupied square
                if (board[newRow][newCol].isWhite() != isWhite) {
                    // Enemy piece, can capture
                    possibleMoves.add(getPositionName(newRow, newCol));
                }
                // Stop in either case - can't move past pieces
                break;
            }

            // Move to next position in the same direction
            newRow += rowOffset;
            newCol += colOffset;
        }
    }

    /**
     * Execute a chess move
     */
    private void executeMove(int fromRow, int fromCol, int toRow, int toCol) {
        // Get the piece to move
        ChessPiece piece = board[fromRow][fromCol];

        // Check if this is a capture move
        boolean isCapture = board[toRow][toCol] != null;

        // Add to move history
        String pieceType = piece.getType().name();
        String fromPos = getPositionName(fromRow, fromCol);
        String toPos = getPositionName(toRow, toCol);
        moveHistory.add(pieceType + ": " + fromPos + "->" + toPos + (isCapture ? " (Capture)" : ""));

        // Remove the piece from the starting position
        board[fromRow][fromCol] = null;

        // Place the piece at the destination
        board[toRow][toCol] = piece;

        // Reset selection and possible moves
        selectedRow = -1;
        selectedCol = -1;
        possibleMoves.clear();

        // Check for pawn promotion
        checkPawnPromotion(toRow, toCol, piece);

        // Update the board display
        updateBoards();
    }

    /**
     * Check for pawn promotion - adapted for 6x8 board
     */
    private void checkPawnPromotion(int row, int col, ChessPiece piece) {
        // Check if it's a pawn
        if (piece.getType() != ChessPieceType.PAWN) {
            return;
        }

        // Check if the pawn reached the opposite end of the board
        if ((piece.isWhite() && row == 5) || (!piece.isWhite() && row == 0)) {
            // Promote to queen (simplified for this implementation)
            board[row][col] = new ChessPiece(ChessPieceType.QUEEN, piece.isWhite());

            // Notify players
            Player player = piece.isWhite() ? getPlayer1() : getPlayer2();
            if (player != null) {
                MessageUtil.sendMessage(player, "&6Your pawn has been promoted to a Queen!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Check victory conditions
     */
    private boolean checkVictoryConditions(boolean isWhitePlayer) {
        // Check if the opponent's king is captured
        boolean kingFound = false;

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 8; col++) {
                if (board[row][col] != null &&
                        board[row][col].getType() == ChessPieceType.KING &&
                        board[row][col].isWhite() != isWhitePlayer) {
                    kingFound = true;
                    break;
                }
            }
            if (kingFound) break;
        }

        if (!kingFound) {
            // King is captured, player wins
            UUID winnerId = isWhitePlayer ? player1UUID : player2UUID;

            // Notify players
            Player winner = isWhitePlayer ? getPlayer1() : getPlayer2();
            Player loser = isWhitePlayer ? getPlayer2() : getPlayer1();

            if (winner != null) {
                MessageUtil.sendMessage(winner, "&aYou have captured the opponent's king! You win!");
                winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }

            if (loser != null) {
                MessageUtil.sendMessage(loser, "&cYour king has been captured! You lose!");
                loser.playSound(loser.getLocation(), Sound.ENTITY_BLAZE_DEATH, 1.0f, 0.5f);
            }

            // End the game
            endGame(winnerId);
            return true;
        }

        return false;
    }

    /**
     * Check for game-ending conditions
     */
    private void checkGameEndConditions() {
        // In a real implementation, this would check for:
        // - Checkmate
        // - Stalemate
        // - Draw by insufficient material
        // - Draw by repetition
        // - Draw by 50-move rule
        // - Draw by agreement (if implemented)

        // For this simplified version, we only check if a king is captured in checkVictoryConditions
    }

    /**
     * Handle inventory clicks (chess moves) - adapted for 6x8 horizontal board
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isActive) return;

        // Check if the inventory is one of our chess boards
        String title = event.getView().getTitle();
        if (!title.equals(WHITE_PLAYER_BOARD_TITLE) && !title.equals(BLACK_PLAYER_BOARD_TITLE)) {
            return;
        }

        // Always cancel the event to prevent item movement
        event.setCancelled(true);

        // Check if the click is in a valid slot
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 6 * 9) {
            return;
        }

        // Get the player
        Player player = (Player) event.getWhoClicked();

        // Check if clicked in the control column (last column)
        if (slot % 9 == 8) {
            handleControlButtonClick(player, slot);
            return;
        }

        // Handle the click on the actual chess board
        handleBoardClick(player, slot);
    }

    /**
     * Handle clicks on control buttons
     */
    private void handleControlButtonClick(Player player, int slot) {
        int row = slot / 9;

        // Forfeit button
        if (row == 5) {
            // Forfeit the game
            UUID playerUUID = player.getUniqueId();
            UUID winnerUUID = playerUUID.equals(player1UUID) ? player2UUID : player1UUID;

            MessageUtil.sendMessage(player, "&cYou have forfeited the game!");
            Player opponent = playerUUID.equals(player1UUID) ? getPlayer2() : getPlayer1();
            if (opponent != null) {
                MessageUtil.sendMessage(opponent, "&aYour opponent has forfeited the game! You win!");
            }

            endGame(winnerUUID);
        }
    }

    /**
     * Handle a click on the chess board - adapted for 6x8 horizontal board
     */
    private void handleBoardClick(Player player, int slot) {
        if (!isPlayersTurn(player)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            MessageUtil.sendMessage(player, "&cIt's not your turn!");
            return;
        }

        boolean isWhitePlayer = player.getUniqueId().equals(player1UUID);

        // Convert inventory slot to row and column - horizontal layout
        int row, col;
        if (isWhitePlayer) {
            // White player's board is not inverted
            row = slot / 9;
            col = slot % 9;
        } else {
            // Black player's board is inverted
            row = 5 - (slot / 9);
            col = 7 - (slot % 9);
        }

        // Validate row and column (just in case)
        if (row < 0 || row >= 6 || col < 0 || col >= 8) {
            return;
        }

        // If no piece is selected yet
        if (selectedRow == -1 && selectedCol == -1) {
            // Try to select a piece
            if (board[row][col] != null) {
                // Check if the piece belongs to the current player
                boolean isWhitePiece = board[row][col].isWhite();
                if ((isWhitePlayer && isWhitePiece) || (!isWhitePlayer && !isWhitePiece)) {
                    // Select the piece
                    selectedRow = row;
                    selectedCol = col;

                    // Calculate possible moves
                    calculatePossibleMoves(row, col, board[row][col]);

                    // Update the boards
                    updateBoards();

                    // Play selection sound
                    player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
                    MessageUtil.sendMessage(player, "&6Selected piece at " + getPositionName(row, col) + ". Now choose a destination.");
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    MessageUtil.sendMessage(player, "&cYou can only move your own pieces!");
                }
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
        } else {
            // A piece is already selected
            String targetPos = getPositionName(row, col);

            // Check if clicking on the same piece (deselect)
            if (row == selectedRow && col == selectedCol) {
                selectedRow = -1;
                selectedCol = -1;
                possibleMoves.clear();
                updateBoards();
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_OFF, 1.0f, 1.0f);
                return;
            }

            // Check if clicking on another own piece (change selection)
            if (board[row][col] != null) {
                boolean isWhitePiece = board[row][col].isWhite();
                if ((isWhitePlayer && isWhitePiece) || (!isWhitePlayer && !isWhitePiece)) {
                    // Change selection to the new piece
                    selectedRow = row;
                    selectedCol = col;

                    // Recalculate possible moves
                    possibleMoves.clear();
                    calculatePossibleMoves(row, col, board[row][col]);

                    // Update the boards
                    updateBoards();

                    // Play selection sound
                    player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.2f);
                    MessageUtil.sendMessage(player, "&6Selected new piece at " + getPositionName(row, col) + ". Now choose a destination.");
                    return;
                }
            }

            // Check if the position is a valid move
            if (possibleMoves.contains(targetPos)) {
                // Execute the move
                executeMove(selectedRow, selectedCol, row, col);

                // Play move sound
                boolean isCapture = board[row][col] != null;
                if (isCapture) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.0f);
                }

                // Check for victory conditions
                if (checkVictoryConditions(isWhitePlayer)) {
                    return;
                }

                // Switch turns
                switchTurns();
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                MessageUtil.sendMessage(player, "&cInvalid move!");
            }
        }
    }

    /**
     * Handle inventory drag events (prevent)
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isActive) return;

        // Check if the inventory is one of our chess boards
        String title = event.getView().getTitle();
        if (title.equals(WHITE_PLAYER_BOARD_TITLE) || title.equals(BLACK_PLAYER_BOARD_TITLE)) {
            // Cancel all drag events in the chess board
            event.setCancelled(true);
        }
    }

    /**
     * Handle inventory close events
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!isActive) return;

        // Check if the inventory is one of our chess boards
        String title = event.getView().getTitle();
        if (!title.equals(WHITE_PLAYER_BOARD_TITLE) && !title.equals(BLACK_PLAYER_BOARD_TITLE)) {
            return;
        }

        // Get the player
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        // If it's their turn, reopen the board after a short delay
        if (playerId.equals(currentPlayerTurn)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Only reopen if the game is still active and it's still their turn
                if (isActive && playerId.equals(currentPlayerTurn)) {
                    if (playerId.equals(player1UUID)) {
                        player.openInventory(player1Board);
                    } else {
                        player.openInventory(player2Board);
                    }
                    MessageUtil.sendMessage(player, "&cYou must make a move before closing the board!");
                }
            }, 5L); // 0.25 second delay
        }
    }

    /**
     * Handle player movement (restrict during game)
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isActive) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Only restrict movement if the player is in the game
        if (playerId.equals(player1UUID) || playerId.equals(player2UUID)) {
            // Allow minimal head movement but prevent actual movement
            Location from = event.getFrom();
            Location to = event.getTo();

            if (to != null && (from.getBlockX() != to.getBlockX() ||
                    from.getBlockY() != to.getBlockY() ||
                    from.getBlockZ() != to.getBlockZ())) {
                // Teleport back to prevent movement
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handle player quit during game
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!isActive) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // If a player quits, the other player wins
        if (playerId.equals(player1UUID) || playerId.equals(player2UUID)) {
            UUID winnerId = playerId.equals(player1UUID) ? player2UUID : player1UUID;

            // Notify the remaining player
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner != null) {
                MessageUtil.sendMessage(winner, "&aYour opponent has left the game! You win!");
            }

            // End the game
            endGame(winnerId);
        }
    }

    private boolean isPlayersTurn(Player player) {
        UUID playerUUID = player.getUniqueId();
        return playerUUID.equals(player1UUID) && currentPlayerTurn.equals(player1UUID) ||
                playerUUID.equals(player2UUID) && currentPlayerTurn.equals(player2UUID);
    }

    /**
     * Calculate possible moves for a king
     */
    private void calculateKingMoves(int row, int col, boolean isWhite) {
        // All adjacent squares (8 directions)
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int colOffset = -1; colOffset <= 1; colOffset++) {
                // Skip the current position
                if (rowOffset == 0 && colOffset == 0) {
                    continue;
                }

                int newRow = row + rowOffset;
                int newCol = col + colOffset;

                // Check if the position is on the board
                if (newRow >= 0 && newRow < 6 && newCol >= 0 && newCol < 8) {
                    // If the position is empty or has an enemy piece, it's a valid move
                    if (board[newRow][newCol] == null || board[newRow][newCol].isWhite() != isWhite) {
                        possibleMoves.add(getPositionName(newRow, newCol));
                    }
                }
            }
        }

        // TODO: Add castling logic if implementing advanced rules
    }

    @Override
    public void endGame(UUID winnerUUID) {
        if (!isActive) {
            return;
        }

        // Clean up resources
        if (moveTimerTask != null) {
            moveTimerTask.cancel();
            moveTimerTask = null;
        }

        // Unregister all event handlers
        HandlerList.unregisterAll(this);

        // Close inventories
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            player1.closeInventory();
        }

        if (player2 != null) {
            player2.closeInventory();
        }

        // End the game in the parent class
        super.endGame(winnerUUID);
    }

    @Override
    public void timeoutGame() {
        if (!isActive) {
            return;
        }

        // Notify players
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            MessageUtil.sendMessage(player1, "&cThe game has timed out!");
            player1.closeInventory();
        }

        if (player2 != null) {
            MessageUtil.sendMessage(player2, "&cThe game has timed out!");
            player2.closeInventory();
        }

        // The player who won the PvP wins the mini-game by default
        UUID winnerUUID = player1WonPvP ? player1UUID : player2UUID;

        // End the game
        super.timeoutGame();
    }

    /**
     * Enum for chess game states
     */
    private enum ChessGameState {
        SETUP,
        PLAYING,
        ENDED
    }

    /**
     * Class representing a chess piece
     */
    private static class ChessPiece {
        private final ChessPieceType type;
        private final boolean white;

        /**
         * Constructor
         */
        public ChessPiece(ChessPieceType type, boolean white) {
            this.type = type;
            this.white = white;
        }

        /**
         * Get the piece type
         */
        public ChessPieceType getType() {
            return type;
        }

        /**
         * Check if the piece is white
         */
        public boolean isWhite() {
            return white;
        }

        /**
         * Get the symbol for the piece
         */
        public String getSymbol() {
            switch (type) {
                case PAWN: return "P";
                case ROOK: return "R";
                case KNIGHT: return "N";
                case BISHOP: return "B";
                case QUEEN: return "Q";
                case KING: return "K";
                default: return "?";
            }
        }
    }

    /**
     * Enum for chess piece types
     */
    private enum ChessPieceType {
        PAWN,
        ROOK,
        KNIGHT,
        BISHOP,
        QUEEN,
        KING
    }

    /**
     * Calculate possible moves for a pawn - adapted for 6x8 board
     */
    private void calculatePawnMoves(int row, int col, boolean isWhite) {
        // Déplacement horizontal (blanc → droite, noir → gauche)
        int direction = isWhite ? 1 : -1;

        // Avancer tout droit
        int newCol = col + direction;
        if (newCol >= 0 && newCol < 8 && board[row][newCol] == null) {
            possibleMoves.add(getPositionName(row, newCol));

            // Double pas depuis la position initiale
            if ((isWhite && col == 1) || (!isWhite && col == 6)) {
                int doubleCol = col + (2 * direction);
                if (doubleCol >= 0 && doubleCol < 8 && board[row][doubleCol] == null) {
                    possibleMoves.add(getPositionName(row, doubleCol));
                }
            }
        }

        // Captures diagonales (en haut et en bas sur l'axe Y)
        for (int rowOffset : new int[]{-1, 1}) {
            int newRow = row + rowOffset;
            if (newRow >= 0 && newRow < 6 && newCol >= 0 && newCol < 8) {
                if (board[newRow][newCol] != null && board[newRow][newCol].isWhite() != isWhite) {
                    possibleMoves.add(getPositionName(newRow, newCol));
                }
            }
        }
    }

    /**
     * Calculate possible moves for a rook
     */
    private void calculateRookMoves(int row, int col, boolean isWhite) {
        // Horizontal and vertical directions
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] dir : directions) {
            calculateLinearMoves(row, col, isWhite, dir[0], dir[1]);
        }
    }

    /**
     * Calculate possible moves for a knight
     */
    private void calculateKnightMoves(int row, int col, boolean isWhite) {
        // All possible knight moves
        int[][] offsets = {
                {1, 2}, {2, 1}, {2, -1}, {1, -2},
                {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
        };

        for (int[] offset : offsets) {
            int newRow = row + offset[0];
            int newCol = col + offset[1];

            // Check if the position is on the board
            if (newRow >= 0 && newRow < 6 && newCol >= 0 && newCol < 8) {
                // If the position is empty or has an enemy piece, it's a valid move
                if (board[newRow][newCol] == null || board[newRow][newCol].isWhite() != isWhite) {
                    possibleMoves.add(getPositionName(newRow, newCol));
                }
            }
        }
    }

    /**
     * Calculate possible moves for a bishop
     */
    private void calculateBishopMoves(int row, int col, boolean isWhite) {
        // Diagonal directions
        int[][] directions = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        for (int[] dir : directions) {
            calculateLinearMoves(row, col, isWhite, dir[0], dir[1]);
        }
    }

    /**
     * Calculate possible moves for a queen
     */
    private void calculateQueenMoves(int row, int col, boolean isWhite) {
        // Combine rook and bishop moves
        calculateRookMoves(row, col, isWhite);
        calculateBishopMoves(row, col, isWhite);
    }

    /**
     * Calculate possible moves for a piece
     */
    private void calculatePossibleMoves(int row, int col, ChessPiece piece) {
        possibleMoves.clear();

        // Calculate moves based on piece type
        switch (piece.getType()) {
            case PAWN:
                calculatePawnMoves(row, col, piece.isWhite());
                break;

            case ROOK:
                calculateRookMoves(row, col, piece.isWhite());
                break;

            case KNIGHT:
                calculateKnightMoves(row, col, piece.isWhite());
                break;

            case BISHOP:
                calculateBishopMoves(row, col, piece.isWhite());
                break;

            case QUEEN:
                calculateQueenMoves(row, col, piece.isWhite());
                break;

            case KING:
                calculateKingMoves(row, col, piece.isWhite());
                break;
        }


    }
}