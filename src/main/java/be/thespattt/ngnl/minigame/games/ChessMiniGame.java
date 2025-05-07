package be.thespattt.ngnl.minigame.games;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.world.WorldManager;
import be.thespattt.ngnl.minigame.MiniGameBase;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.*;
import org.bukkit.block.Block;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Chess minigame with a GUI interface - Modified for 6x8 board
 */
public class ChessMiniGame extends MiniGameBase implements Listener {

    // Game state
    private ChessGameState gameState = ChessGameState.SETUP;
    private UUID currentPlayerTurn;
    private int moveTimeRemaining = 15;
    private BukkitTask moveTimerTask;

    private final ChessPiece[][] board = new ChessPiece[6][8];
    private Inventory player1Board; // White player's view
    private Inventory player2Board; // Black player's view

    private int selectedRow = -1;
    private int selectedCol = -1;
    private final Set<String> possibleMoves = new HashSet<>();

    private int chessRoomX;
    private final int chessRoomY = 72;
    private int chessRoomZ;

    private final List<String> moveHistory = new ArrayList<>();

    private static final String WHITE_PLAYER_BOARD_TITLE = "&fMental Chess (White)";
    private static final String BLACK_PLAYER_BOARD_TITLE = "&0Mental Chess (Black)";
    private static final int ROOM_WIDTH = 12;
    private static final int  ROOM_HEIGHT = 5;
    private static final int  ROOM_DEPTH = 10;

    private final int STARTX;
    private final int STARTY = chessRoomY;
    private final int STARTZ;

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
        this.chessRoomX = ThreadLocalRandom.current().nextInt(100, 1001);
        this.chessRoomZ = ThreadLocalRandom.current().nextInt(100, 1001);
        this.STARTX = chessRoomX - (ROOM_WIDTH / 2);
        this.STARTZ = chessRoomZ - (ROOM_DEPTH / 2);
    }

    @Override
    protected void onGameStart() {
        player1Board = Bukkit.createInventory(null, 6 * 9, WHITE_PLAYER_BOARD_TITLE);
        player2Board = Bukkit.createInventory(null, 6 * 9, BLACK_PLAYER_BOARD_TITLE);

        initializeBoard();

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
    public void preloadChunksAndThen(World world, Location center, int radius, Runnable onLoaded) {
        int chunkRadius = (int) Math.ceil(radius / 16.0);

        Set<Chunk> chunksToLoad = loadingChunks(center, world, chunkRadius);

        new BukkitRunnable() {
            @Override
            public void run() {
                if(chunksToLoad.stream().anyMatch(chunk -> !chunk.isLoaded())) return;
                cancel();
                onLoaded.run();
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }
    private Set<Chunk> loadingChunks(Location center, World world, int chunkRadius){
        Set<Chunk> chunksToLoad = new HashSet<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt(center.getBlockX() / 16 + dx, center.getBlockZ() / 16 + dz);
                chunksToLoad.add(chunk);
                chunk.load(true);
            }
        }
        return chunksToLoad;
    }
    @Override
    public void startGame() {
        super.startGame();

        Bukkit.getPluginManager().registerEvents(this, plugin);

        Location center = new Location(plugin.getWorldManager().getMinigameWorld(), chessRoomX, chessRoomY, chessRoomZ);
        preloadChunksAndThen(plugin.getWorldManager().getMinigameWorld(), center, 32, () -> {
            setupChessRoom();
            teleportPlayersToChessRoom();
        });
        setupChessRoom();

        teleportPlayersToChessRoom();
    }
    private void setupChessRoom() {
        // Create a 10x5x5 wooden room for the chess game
        World gameWorld = getOrCreateMinigameWorld();
        if (gameWorld == null) {
            return;
        }
        clearAreaWithAir();
        buildArenaFloor();
        buildArenaCeiling();
        buildArenaWalls();
        buildDecoration();
    }
    private void clearAreaWithAir(){
        for (int x = 0; x < ROOM_WIDTH + 2; x++) {
            for (int y = 0; y < ROOM_HEIGHT + 2; y++) {
                for (int z = 0; z < ROOM_DEPTH + 2; z++) {
                    Block block = gameWorld.getBlockAt(STARTX - 1 + x, STARTY - 1 + y, STARTZ - 1 + z);
                    block.setType(Material.AIR);
                }
            }
        }
    }
    private void buildArenaWalls(){
        for (int x = 0; x < ROOM_WIDTH; x++) {
            for (int y = 1; y < ROOM_HEIGHT - 1; y++) {
                // North wall
                Block northBlock = gameWorld.getBlockAt(STARTX + x, STARTY + y, STARTZ);
                northBlock.setType(Material.SPRUCE_PLANKS);

                // South wall
                Block southBlock = gameWorld.getBlockAt(STARTX + x, STARTY + y, STARTZ + ROOM_DEPTH - 1);
                southBlock.setType(Material.SPRUCE_PLANKS);
            }
        }
        for (int z = 0; z < ROOM_DEPTH; z++) {
            for (int y = 1; y < ROOM_HEIGHT - 1; y++) {
                // East wall
                Block eastBlock = gameWorld.getBlockAt(STARTX, STARTY + y, STARTZ + z);
                eastBlock.setType(Material.SPRUCE_PLANKS);

                // West wall
                Block westBlock = gameWorld.getBlockAt(STARTX + ROOM_WIDTH - 1, STARTX + y, STARTZ + z);
                westBlock.setType(Material.SPRUCE_PLANKS);
            }
        }
    }
    private void buildArenaCeiling(){
        // Build the ceiling
        for (int x = 0; x < ROOM_WIDTH; x++) {
            for (int z = 0; z < ROOM_DEPTH; z++) {
                Block block = gameWorld.getBlockAt(STARTX + x, STARTY + ROOM_HEIGHT - 1, STARTZ + z);
                block.setType(Material.DARK_OAK_PLANKS);
            }
        }
    }
    private void buildArenaFloor(){
        for (int x = 0; x < ROOM_WIDTH; x++) {
            for (int z = 0; z < ROOM_DEPTH; z++) {
                Block block = gameWorld.getBlockAt(STARTX + x, STARTY, STARTZ + z);
                block.setType(Material.DARK_OAK_PLANKS);
            }
        }
    }
    private void buildDecoration(){
        gameWorld.getBlockAt(STARTX + 2, STARTY + 3, STARTZ + 2).setType(Material.GLOWSTONE);
        gameWorld.getBlockAt(STARTX + ROOM_WIDTH - 3, STARTY + 3, STARTZ + 2).setType(Material.GLOWSTONE);
        gameWorld.getBlockAt(STARTX + 2, STARTY + 3, STARTZ + ROOM_DEPTH - 3).setType(Material.GLOWSTONE);
        gameWorld.getBlockAt(STARTX + ROOM_WIDTH - 3, STARTY + 3, STARTZ + ROOM_DEPTH - 3).setType(Material.GLOWSTONE);
        gameWorld.getBlockAt(STARTX + 2, STARTY + 1, STARTZ + 1).setType(Material.OAK_STAIRS);
        gameWorld.getBlockAt(STARTX + ROOM_WIDTH - 3, STARTY + 1, STARTZ + ROOM_DEPTH - 2).setType(Material.OAK_STAIRS);
        gameWorld.getBlockAt(chessRoomX, STARTY + 1, chessRoomZ).setType(Material.CRAFTING_TABLE);
    }
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
    private void initializeBoard() {
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 8; col++) {
                board[row][col] = null;
            }
        }
        for (int col = 0; col < 6; col++) {
            board[col][1] = new ChessPiece(ChessPieceType.PAWN, true);
            board[col][6] = new ChessPiece(ChessPieceType.PAWN, false);
        }

        List<Integer> positions = IntStream.range(0, 6).boxed().collect(Collectors.toList());
        Collections.shuffle(positions);

        ChessPieceType[] layout = new ChessPieceType[6];

        int rook1 = positions.get(0);
        int king  = positions.get(1);
        int rook2 = positions.get(2);

        List<Integer> sorted = Arrays.asList(rook1, king, rook2);
        Collections.sort(sorted);
        rook1 = sorted.get(0);
        king  = sorted.get(1);
        rook2 = sorted.get(2);

        layout[rook1] = ChessPieceType.ROOK;
        layout[king]  = ChessPieceType.KING;
        layout[rook2] = ChessPieceType.ROOK;

        List<ChessPieceType> others = new ArrayList<>(List.of(
                ChessPieceType.KNIGHT,
                ChessPieceType.BISHOP,
                ChessPieceType.BISHOP,
                ChessPieceType.KNIGHT,
                ChessPieceType.QUEEN
        ));
        Collections.shuffle(others);

        int otherIndex = 0;
        for (int i = 0; i < 6; i++) {
            if (layout[i] == null) {
                layout[i] = others.get(otherIndex++);
            }
        }
        for (int row = 0; row < 6; row++) {
            board[row][0] = new ChessPiece(layout[row], true);
            board[5 - row][7] = new ChessPiece(layout[row], false);
        }
        updateBoards();
    }
    private void updateBoards() {
        player1Board.clear();
        player2Board.clear();

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 8; col++) {
                int whiteSlot = row * 9 + col;
                int blackSlot = (5 - row) * 9 + (7 - col);

                ItemStack bgItem = createBackgroundItem(row, col);
                player1Board.setItem(whiteSlot, bgItem);
                player2Board.setItem(blackSlot, bgItem);

                if (board[row][col] != null) {
                    ItemStack pieceItem = createPieceItem(board[row][col], row, col);
                    player1Board.setItem(whiteSlot, pieceItem);
                    player2Board.setItem(blackSlot, pieceItem);
                }

                if (row == selectedRow && col == selectedCol) {
                    ItemStack selectedItem = board[row][col] != null ?
                            createSelectedPieceItem(board[row][col], row, col) :
                            createSelectedEmptyItem(row, col);

                    player1Board.setItem(whiteSlot, selectedItem);
                    player2Board.setItem(blackSlot, selectedItem);
                }

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
        addNavigationButtons();
    }
    private void addNavigationButtons() {
        for (int row = 0; row < 6; row++) {
            int slot = row * 9 + 8;

            ItemStack item;
            if (row == 0) {
                boolean isWhiteTurn = (currentPlayerTurn == null || currentPlayerTurn.equals(player1UUID));

                item = new ItemStack(isWhiteTurn ? Material.WHITE_WOOL : Material.BLACK_WOOL);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§6Current Turn: " + (isWhiteTurn ? "§fWhite" : "§0Black"));
                item.setItemMeta(meta);
            }
            else if (row == 5) {
                item = new ItemStack(Material.BARRIER);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§cForfeit Game");
                item.setItemMeta(meta);
            }
            else {
                item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(" ");
                item.setItemMeta(meta);
            }

            player1Board.setItem(slot, item);
            player2Board.setItem((5 - row) * 9 + 8, item); // Inverted for black player
        }
    }
    private ItemStack createBackgroundItem(int row, int col) {
        boolean isWhiteSquare = (row + col) % 2 == 0;
        Material material = isWhiteSquare ? Material.WHITE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(getPositionName(row, col));
        item.setItemMeta(meta);
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return item;
    }
    private ItemStack createPieceItem(ChessPiece piece, int row, int col) {
        Material material = getChessPieceMaterial(piece);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(piece.isWhite() ? "§fWhite " + piece.getType().name() : "§0Black " + piece.getType().name());
        List<String> lore = new ArrayList<>();
        lore.add("§7Position: " + getPositionName(row, col));
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack createSelectedPieceItem(ChessPiece piece, int row, int col) {
        ItemStack item = createPieceItem(piece, row, col);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        lore.add("§e✓ Selected");
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack createSelectedEmptyItem(int row, int col) {
        ItemStack item = createBackgroundItem(row, col);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("§e✓ Selected");
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack createPossibleMoveWithPieceItem(ChessPiece piece, int row, int col) {
        ItemStack item = createPieceItem(piece, row, col);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        lore.add("§a➢ Possible move");
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack createPossibleMoveEmptyItem(int row, int col) {
        boolean isWhiteSquare = (row + col) % 2 == 0;
        Material material = isWhiteSquare ? Material.LIME_WOOL : Material.GREEN_WOOL;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(getPositionName(row, col));
        List<String> lore = new ArrayList<>();
        lore.add("§a➢ Possible move");
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
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
    private String getPositionName(int row, int col) {
        char file = (char) ('a' + row);
        int rank = col + 1;
        return file + "" + rank;
    }
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
    private void startMoveTimer() {
        moveTimeRemaining = 15;

        if (moveTimerTask != null) {
            moveTimerTask.cancel();
        }

        moveTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            moveTimeRemaining--;

            if (moveTimeRemaining % 5 == 0 || moveTimeRemaining <= 5) {
                Player currentPlayer = currentPlayerTurn.equals(player1UUID) ? getPlayer1() : getPlayer2();
                if (currentPlayer != null) {
                    MessageUtil.sendMessage(currentPlayer, "&6Time remaining: &e" + moveTimeRemaining + " seconds");
                }
            }

            if (moveTimeRemaining <= 0) {
                handleTimeOut();
            }
        }, 20L, 20L);
    }
    private void handleTimeOut() {
        Player timeoutPlayer = currentPlayerTurn.equals(player1UUID) ? getPlayer1() : getPlayer2();
        if (timeoutPlayer != null) {
            MessageUtil.sendMessage(timeoutPlayer, "&cYou ran out of time! Forfeiting your turn.");
        }
        switchTurns();
    }
    private void switchTurns() {
        currentPlayerTurn = currentPlayerTurn.equals(player1UUID) ? player2UUID : player1UUID;
        selectedRow = -1;
        selectedCol = -1;
        possibleMoves.clear();

        updateBoards();

        startMoveTimer();

        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        if (player1 != null) {
            boolean isPlayerTurn = currentPlayerTurn.equals(player1UUID);
            MessageUtil.sendMessage(player1, isPlayerTurn ?
                    "&aIt's your turn (White)!" :
                    "&7Waiting for opponent's move (Black)...");

            player1.playSound(player1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, isPlayerTurn ? 1.5f : 1.0f);
        }

        if (player2 != null) {
            boolean isPlayerTurn = currentPlayerTurn.equals(player2UUID);
            MessageUtil.sendMessage(player2, isPlayerTurn ?
                    "&aIt's your turn (Black)!" :
                    "&7Waiting for opponent's move (White)...");

            player2.playSound(player2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, isPlayerTurn ? 1.5f : 1.0f);
        }

        checkGameEndConditions();
    }
    private void sendInstructions() {
        Player player1 = getPlayer1();
        Player player2 = getPlayer2();

        String instructions =
                "&6=== Modified Chess GUI Instructions ===\n" +
                        "&fThis is a 6x8 freestyle chess game with a horizontal board.\n" +
                        "&fEach side has 6 pawns, 1 king 2 rooks, and randomly max 2 knights, max 2 bishop and max 1 queen.\n" +
                        "&fWhite (Player 1) moves first.\n" +
                        "&fYou have 15 seconds to make each move.\n" +
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
    private void calculateLinearMoves(int row, int col, boolean isWhite, int rowOffset, int colOffset) {
        int newRow = row + rowOffset;
        int newCol = col + colOffset;

        while (newRow >= 0 && newRow < 6 && newCol >= 0 && newCol < 8) {
            if (board[newRow][newCol] == null) {
                possibleMoves.add(getPositionName(newRow, newCol));
            } else {
                if (board[newRow][newCol].isWhite() != isWhite) {
                    possibleMoves.add(getPositionName(newRow, newCol));
                }
                break;
            }
            newRow += rowOffset;
            newCol += colOffset;
        }
    }
    private void executeMove(int fromRow, int fromCol, int toRow, int toCol) {
        ChessPiece piece = board[fromRow][fromCol];
        boolean isCapture = board[toRow][toCol] != null;

        String pieceType = piece.getType().name();
        String fromPos = getPositionName(fromRow, fromCol);
        String toPos = getPositionName(toRow, toCol);
        moveHistory.add(pieceType + ": " + fromPos + "->" + toPos + (isCapture ? " (Capture)" : ""));

        board[fromRow][fromCol] = null;
        board[toRow][toCol] = piece;
        selectedRow = -1;
        selectedCol = -1;
        possibleMoves.clear();

        checkPawnPromotion(toRow, toCol, piece);
        updateBoards();
    }
    private void checkPawnPromotion(int row, int col, ChessPiece piece) {
        List<ChessPieceType> promotionPossibility = new ArrayList<>(List.of(
                ChessPieceType.BISHOP,
                ChessPieceType.KNIGHT,
                ChessPieceType.ROOK,
                ChessPieceType.QUEEN
        ));
        Collections.shuffle(promotionPossibility);

        if (piece.getType() != ChessPieceType.PAWN) {
            return;
        }

        if ((piece.isWhite() && col == 7) || (!piece.isWhite() && col == 0)) {
            board[row][col] = new ChessPiece(promotionPossibility.getFirst(), piece.isWhite());

            Player player = piece.isWhite() ? getPlayer1() : getPlayer2();
            if (player != null) {
                MessageUtil.sendMessage(player, "&6Your pawn has been promoted to a" + board[row][col].getType().name() + "!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
    }
    private boolean checkVictoryConditions(boolean isWhitePlayer) {
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
            UUID winnerId = isWhitePlayer ? player1UUID : player2UUID;

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
            endGame(winnerId);
            return true;
        }

        return false;
    }
    private void checkGameEndConditions() {
        // TODO, mais on veut que checkmate donc hassoul
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isActive) return;
        String title = event.getView().getTitle();
        if (!title.equals(WHITE_PLAYER_BOARD_TITLE) && !title.equals(BLACK_PLAYER_BOARD_TITLE)) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 6 * 9) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (slot % 9 == 8) {
            handleControlButtonClick(player, slot);
            return;
        }
        handleBoardClick(player, slot);
    }
    private void handleControlButtonClick(Player player, int slot) {
        int row = slot / 9;
        if (row == 5) {
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
    private void handleBoardClick(Player player, int slot) {
        if (!isPlayersTurn(player)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            MessageUtil.sendMessage(player, "&cIt's not your turn!");
            return;
        }

        boolean isWhitePlayer = player.getUniqueId().equals(player1UUID);
        int row, col;
        if (isWhitePlayer) {
            row = slot / 9;
            col = slot % 9;
        } else {
            row = 5 - (slot / 9);
            col = 7 - (slot % 9);
        }
        if (row < 0 || row >= 6 || col < 0 || col >= 8) {
            return;
        }
        if (selectedRow == -1 && selectedCol == -1) {
            if (board[row][col] != null) {
                boolean isWhitePiece = board[row][col].isWhite();
                if ((isWhitePlayer && isWhitePiece) || (!isWhitePlayer && !isWhitePiece)) {
                    selectedRow = row;
                    selectedCol = col;
                    calculatePossibleMoves(row, col, board[row][col]);
                    updateBoards();
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
            String targetPos = getPositionName(row, col);
            if (row == selectedRow && col == selectedCol) {
                selectedRow = -1;
                selectedCol = -1;
                possibleMoves.clear();
                updateBoards();
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_OFF, 1.0f, 1.0f);
                return;
            }
            if (board[row][col] != null) {
                boolean isWhitePiece = board[row][col].isWhite();
                if ((isWhitePlayer && isWhitePiece) || (!isWhitePlayer && !isWhitePiece)) {
                    selectedRow = row;
                    selectedCol = col;
                    possibleMoves.clear();
                    calculatePossibleMoves(row, col, board[row][col]);
                    updateBoards();
                    player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.2f);
                    MessageUtil.sendMessage(player, "&6Selected new piece at " + getPositionName(row, col) + ". Now choose a destination.");
                    return;
                }
            }
            if (possibleMoves.contains(targetPos)) {
                executeMove(selectedRow, selectedCol, row, col);
                boolean isCapture = board[row][col] != null;
                if (isCapture) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.0f);
                }
                if (checkVictoryConditions(isWhitePlayer)) {
                    return;
                }
                switchTurns();
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                MessageUtil.sendMessage(player, "&cInvalid move!");
            }
        }
    }
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isActive) return;

        String title = event.getView().getTitle();
        if (title.equals(WHITE_PLAYER_BOARD_TITLE) || title.equals(BLACK_PLAYER_BOARD_TITLE)) {
            event.setCancelled(true);
        }
    }

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
    private enum ChessGameState {
        SETUP,
        PLAYING,
        ENDED
    }
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
    private enum ChessPieceType {
        PAWN,
        ROOK,
        KNIGHT,
        BISHOP,
        QUEEN,
        KING
    }
    private void calculatePawnMoves(int row, int col, boolean isWhite) {
        int direction = isWhite ? 1 : -1;

        int newCol = col + direction;
        if (newCol >= 0 && newCol < 8 && board[row][newCol] == null) {
            possibleMoves.add(getPositionName(row, newCol));

            if ((isWhite && col == 1) || (!isWhite && col == 6)) {
                int doubleCol = col + (2 * direction);
                if (doubleCol >= 0 && doubleCol < 8 && board[row][doubleCol] == null) {
                    possibleMoves.add(getPositionName(row, doubleCol));
                }
            }
        }

        for (int rowOffset : new int[]{-1, 1}) {
            int newRow = row + rowOffset;
            if (newRow >= 0 && newRow < 6 && newCol >= 0 && newCol < 8) {
                if (board[newRow][newCol] != null && board[newRow][newCol].isWhite() != isWhite) {
                    possibleMoves.add(getPositionName(newRow, newCol));
                }
            }
        }
    }
    private void calculateRookMoves(int row, int col, boolean isWhite) {
        // Horizontal and vertical directions
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] dir : directions) {
            calculateLinearMoves(row, col, isWhite, dir[0], dir[1]);
        }
    }
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
    private void calculateBishopMoves(int row, int col, boolean isWhite) {
        // Diagonal directions
        int[][] directions = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        for (int[] dir : directions) {
            calculateLinearMoves(row, col, isWhite, dir[0], dir[1]);
        }
    }
    private void calculateQueenMoves(int row, int col, boolean isWhite) {
        // Combine rook and bishop moves
        calculateRookMoves(row, col, isWhite);
        calculateBishopMoves(row, col, isWhite);
    }
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