package be.thespattt.ngnl.game;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.arena.ArenaBorderShrinkTask;
import be.thespattt.ngnl.arena.ArenaCombatManager;
import be.thespattt.ngnl.arena.ArenaWorldHandler;
import be.thespattt.ngnl.event.custom.PhaseChangeEvent;
import be.thespattt.ngnl.game.episode.EpisodeManager;
import be.thespattt.ngnl.game.scoreboard.NGNLScoreboardManager;
import be.thespattt.ngnl.game.world.WorldManager;
import be.thespattt.ngnl.game.world.WorldType;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.solo.HolouRole;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Core game class that handles the game state and flow
 */
public class NGNLGame {

    private final NoGameNoLife plugin;

    // Game state
    private GameState gameState;
    private int remainingPlayersForArena;
    private boolean pledgesActive;

    // Managers
    private final EpisodeManager episodeManager;
    private final NGNLScoreboardManager scoreboardManager;

    // Player tracking
    private final List<UUID> alivePlayers;
    private final List<UUID> eliminatedPlayers;

    private final Map<UUID, MiniGameType> lastMiniGameLostBy = new HashMap<>();
    private final Map<UUID, UUID> scheduledMiniGames = new HashMap<>();
    private final Map<String, MiniGameType> scheduledMiniGameTypes = new HashMap<>();

    private ArenaCombatManager arenaCombatManager;
    private ArenaBorderShrinkTask borderShrinkTask;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public NGNLGame(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.gameState = GameState.WAITING;
        this.alivePlayers = new ArrayList<>();
        this.eliminatedPlayers = new ArrayList<>();
        this.pledgesActive = true;

        // Initialize managers
        this.episodeManager = new EpisodeManager(plugin);
        this.scoreboardManager = new NGNLScoreboardManager(plugin);

        this.arenaCombatManager = new ArenaCombatManager(plugin);

        // Load config values
        loadConfigValues();
    }

    /**
     * Load configuration values
     */
    private void loadConfigValues() {
        int threshold = plugin.getConfigManager().getGameConfig().getArenaPlayerThreshold();
        this.remainingPlayersForArena = threshold > 0 ? threshold : 2;
    }

    /**
     * Start the game
     */
    public void startGame() {
        if (gameState != GameState.WAITING) {
            MessageUtil.broadcast("&cThe game is already running!");
            return;
        }
        gameState = GameState.STARTING;
        loadConfigValues();
        resetGameData();
        initializePlayers();

        plugin.getWorldManager().ensureMiningWorld();
        plugin.getWorldManager().clearMiniGameWorldMobs();
        teleportPlayersToMiningWorld();
        plugin.getSpecialItemManager().prepareGame();
        plugin.getImanityShop().rollPrices();
        sendResourcePackToPlayers();

        episodeManager.startEpisodeTimer();
        gameState = GameState.MINING_PHASE;

        MessageUtil.broadcast("&fNo Game No Life UHC has begun!");
        MessageUtil.broadcast("&fGood luck and remember: In this world, &5games &fdecide everything!");
    }

    /**
     * Reset everything that belongs to a single game.
     */
    private void resetGameData() {
        pledgesActive = true;
        lastMiniGameLostBy.clear();
        scheduledMiniGames.clear();
        scheduledMiniGameTypes.clear();
        plugin.getMiniGameStatsTracker().clearStats();
    }

    /**
     * Send the role card resource pack to every player so it is loaded when the roles are revealed.
     */
    private void sendResourcePackToPlayers() {
        for (UUID playerId : alivePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.getRoleCardManager().sendPack(player);
            }
        }
    }

    /**
     * End the game
     *
     * @param force Force end even if players remain
     */
    public void endGame(boolean force) {
        if (gameState == GameState.WAITING || gameState == GameState.ENDED) return;

        episodeManager.stopEpisodeTimer();
        MessageUtil.broadcast("&5&m                    ");

        List<UUID> allPlayers = plugin.getPlayerManager().getAllNGNLPlayers().stream()
                .map(NGNLPlayer::getPlayerId)
                .toList();

        announceWinners(force);

        // Afficher TOUS les joueurs avec leur rôle, en mettant les gagnants en vert
        MessageUtil.broadcast("&fPlayers and Roles:");
        for (UUID playerId : allPlayers) {
            NGNLPlayer ngnl = plugin.getPlayerManager().getNGNLPlayer(playerId);
            Player player = Bukkit.getPlayer(playerId);
            String name = player != null ? player.getName() : "Unknown";
            String role = ngnl.getRole() != null ? ngnl.getRole().getDisplayName() : "No Role";

            if (alivePlayers.contains(playerId)) {
                MessageUtil.broadcast("&2✔ &f" + name + " &5(" + role + ")");
            } else {
                MessageUtil.broadcast("&4✘ &f" + name + " &5(" + role + ")");
            }
        }

        MessageUtil.broadcast("&5&m                    ");

        gameState = GameState.ENDED;
        resetPlayers();
        cleanup();
        gameState = GameState.WAITING;
    }

    /**
     * Announce the winner(s): the last player, or every member of the last allied team.
     *
     * @param force True if an administrator ended the game
     */
    private void announceWinners(boolean force) {
        if (force) {
            MessageUtil.broadcast("&fThe game has been forcefully ended by an &3administrator.");
            return;
        }
        if (alivePlayers.isEmpty() || !areAllAliveAllied()) {
            MessageUtil.broadcast("&fThe game has ended in a &edraw!");
            return;
        }

        List<String> names = new ArrayList<>();
        List<String> roles = new ArrayList<>();
        for (UUID winnerId : alivePlayers) {
            Player winner = Bukkit.getPlayer(winnerId);
            NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(winnerId);
            names.add(winner != null ? winner.getName() : "Unknown");
            roles.add(data != null && data.getRole() != null ? data.getRole().getDisplayName() : "No Role");
        }
        MessageUtil.broadcast((names.size() == 1 ? "&fWinner: &e" : "&fWinners: &e") + String.join("&f, &e", names));
        MessageUtil.broadcast("&5   (" + String.join(" / ", roles) + ")");
    }

    /**
     * Check whether every alive player belongs to the same team (duo partners and alliances
     * are chained together).
     *
     * @return True if all alive players are linked to each other
     */
    private boolean areAllAliveAllied() {
        if (alivePlayers.size() <= 1) {
            return true;
        }
        Set<UUID> reached = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(alivePlayers.get(0));
        reached.add(alivePlayers.get(0));
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (UUID other : alivePlayers) {
                if (!reached.contains(other) && areAllies(current, other)) {
                    reached.add(other);
                    queue.add(other);
                }
            }
        }
        return reached.size() == alivePlayers.size();
    }

    /**
     * Check whether two players are duo partners or alliance partners.
     *
     * @param first  First player
     * @param second Second player
     * @return True if they are on the same side
     */
    private boolean areAllies(UUID first, UUID second) {
        NGNLPlayer a = plugin.getPlayerManager().getNGNLPlayer(first);
        NGNLPlayer b = plugin.getPlayerManager().getNGNLPlayer(second);
        if (a == null || b == null) {
            return false;
        }
        boolean duo = a.getRole() != null && a.getRole().isDuo() && second.equals(a.getRole().getPartnerUUID());
        boolean allied = (a.hasAlliancePartner() && second.equals(a.getAlliancePartner()))
                || (b.hasAlliancePartner() && first.equals(b.getAlliancePartner()));
        return duo || allied;
    }

    /**
     * Start the arena phase: teleport everybody to the arena, hand out the arena equipment,
     * activate the finale abilities and start the shrinking border.
     */
    public void startArenaPhase() {
        if (gameState != GameState.MINING_PHASE) {
            MessageUtil.logWarning("Cannot start arena phase from state: " + gameState);
            return;
        }
        PhaseChangeEvent event = new PhaseChangeEvent(GameState.MINING_PHASE, GameState.ARENA_PHASE);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            MessageUtil.logWarning("Arena phase start was cancelled by event");
            return;
        }

        ArenaWorldHandler arenaHandler = plugin.getWorldManager().getArenaWorldHandler();
        if (arenaHandler.getArenaWorld() == null) {
            arenaHandler.initializeArenaWorld();
        }
        if (arenaHandler.getArenaWorld() == null) {
            MessageUtil.logError("The arena world could not be loaded: staying in the mining phase.");
            return;
        }
        arenaHandler.prepareForGame();

        gameState = GameState.ARENA_PHASE;
        pledgesActive = false;
        arenaCombatManager.activateArenaCombat();
        announceArenaPhase();

        List<Player> fighters = collectOnlinePlayers(alivePlayers);
        arenaHandler.teleportPlayersToArena(fighters);
        teleportSpectatorsToArena(arenaHandler);

        // Finale abilities and equipment once everybody has arrived
        plugin.getRoleManager().activateArenaPhaseAbilities();
        Bukkit.getScheduler().runTaskLater(plugin, () -> giveArenaEquipment(fighters), 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> startBorderShrink(arenaHandler), 100L);

        scoreboardManager.updateScoreboardsForAllPlayers();
        MessageUtil.logInfo("Arena phase started successfully!");
    }

    /**
     * Broadcast the start of the final phase.
     */
    private void announceArenaPhase() {
        MessageUtil.broadcast("&5&m═══════════════════════════════════════════════");
        MessageUtil.broadcast("&5&l            LOVE FIGHT COMMENCÉ");
        MessageUtil.broadcast("&f Les qualifications sont terminées !");
        MessageUtil.broadcast("&f Bienvenue dans l'arène finale !");
        MessageUtil.broadcast("&c Les armes traditionnelles sont désactivées !");
        MessageUtil.broadcast("&6 Utilisez votre Love Gun pour combattre !");
        MessageUtil.broadcast("&5&m═══════════════════════════════════════════════");
    }

    /**
     * Resolve UUIDs to the online players.
     *
     * @param ids UUIDs to resolve
     * @return Online players among them
     */
    private List<Player> collectOnlinePlayers(Collection<UUID> ids) {
        List<Player> players = new ArrayList<>();
        for (UUID id : ids) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    /**
     * Send the eliminated players (spectators) to the arena center so they can watch.
     *
     * @param arenaHandler Arena world handler
     */
    private void teleportSpectatorsToArena(ArenaWorldHandler arenaHandler) {
        if (arenaHandler.getCenterLocation() == null) {
            return;
        }
        for (Player spectator : collectOnlinePlayers(eliminatedPlayers)) {
            spectator.teleport(arenaHandler.getCenterLocation().clone().add(0, 15, 0));
        }
    }

    /**
     * Give the arena weapon (Love Gun) to every fighter who is still online.
     *
     * @param fighters Players in the arena
     */
    private void giveArenaEquipment(List<Player> fighters) {
        for (Player player : fighters) {
            if (player.isOnline() && isPlayerAlive(player.getUniqueId())) {
                arenaCombatManager.giveArenaEquipment(player);
            }
        }
    }

    /**
     * Start the task that shrinks the arena border.
     *
     * @param arenaHandler Arena world handler
     */
    private void startBorderShrink(ArenaWorldHandler arenaHandler) {
        if (gameState != GameState.ARENA_PHASE) {
            return;
        }
        if (borderShrinkTask != null) {
            borderShrinkTask.cancel();
        }
        borderShrinkTask = new ArenaBorderShrinkTask(plugin, arenaHandler.getArenaWorld());
        borderShrinkTask.runTaskTimer(plugin, 20L, 20L);
        MessageUtil.logInfo("Started border shrink task");
    }

    /**
     * Initialize players for the game: full health, empty inventory, no effects.
     */
    private void initializePlayers() {
        alivePlayers.clear();
        eliminatedPlayers.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            alivePlayers.add(player.getUniqueId());
            plugin.getPlayerManager().registerNGNLPlayer(new NGNLPlayer(player.getUniqueId()));
            resetPlayerState(player);
        }
    }

    /**
     * Put a player in the starting state of a game.
     *
     * @param player Player to reset
     */
    private void resetPlayerState(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setMaxHealth(20.0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setLevel(0);
        player.setExp(0);
        player.setAllowFlight(false);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
    }

    /**
     * Send everybody back to the lobby in a clean state after the game.
     */
    private void resetPlayers() {
        Location lobby = plugin.getWorldManager().getSpawnLocation(WorldType.WAITING);
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetPlayerState(player);
            player.setGameMode(GameMode.ADVENTURE);
            if (lobby != null) {
                player.teleport(lobby);
            }
        }
        plugin.getPlayerManager().clearAllPlayers();
    }

    /**
     * Teleport every player to his own spawn point of the mining world.
     */
    private void teleportPlayersToMiningWorld() {
        List<Player> players = collectOnlinePlayers(alivePlayers);
        List<Location> spawns = plugin.getWorldManager().getMiningSpawns(players.size());
        for (int i = 0; i < players.size(); i++) {
            players.get(i).teleport(spawns.get(i));
        }
    }

    /**
     * Eliminate a player from the game
     *
     * @param playerId UUID of the player to eliminate
     * @param killer UUID of the killer (can be null)
     */
    public void eliminatePlayer(UUID playerId, UUID killer) {
        if (!alivePlayers.contains(playerId)) {
            return;
        }

        alivePlayers.remove(playerId);
        eliminatedPlayers.add(playerId);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.setGameMode(GameMode.SPECTATOR);
        }
        handleEliminatedRole(playerId, killer, player);

        for (Role role : plugin.getRoleManager().getAllRoles()) {
            role.onAnyPlayerEliminated(playerId, killer);
        }

        MessageUtil.logInfo("Player eliminated. Alive players: " + alivePlayers.size() + "/" + remainingPlayersForArena);
        if (!checkGameEnd()) {
            checkArenaPhase();
        }
    }

    /**
     * Announce the elimination and notify the role of the eliminated player and his partner.
     *
     * @param playerId UUID of the eliminated player
     * @param killer   UUID of the killer (can be null)
     * @param player   The eliminated player if online
     */
    private void handleEliminatedRole(UUID playerId, UUID killer, Player player) {
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
        if (ngnlPlayer == null || ngnlPlayer.getRole() == null) {
            return;
        }
        Role role = ngnlPlayer.getRole();
        announceElimination(role, player != null ? player.getName() : "Unknown");
        role.onDeath(killer);

        if (role.isDuo() && role.getPartnerUUID() != null) {
            NGNLPlayer partner = plugin.getPlayerManager().getNGNLPlayer(role.getPartnerUUID());
            if (partner != null && partner.getRole() != null) {
                partner.getRole().onPartnerDeath(playerId, killer);
            }
        }
        role.cleanup();
    }

    /**
     * Announce a death: only the ROLE of the dead player is revealed, never his name.
     * If Holou hides the dead, only Holou sees who died and everybody else just learns that
     * somebody was eliminated.
     *
     * @param role Role of the eliminated player
     * @param name Name of the eliminated player
     */
    private void announceElimination(Role role, String name) {
        HolouRole hider = findDeathHider();
        if (hider == null) {
            MessageUtil.broadcast("&c☠ &7Le rôle &f" + role.getDisplayName() + " &7a été éliminé !");
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(hider.getPlayerId())) {
                MessageUtil.sendMessage(online, "&c☠ &f" + name + " &7(" + role.getDisplayName() + "&7) a été éliminé ! &8[Voile des morts]");
            } else {
                MessageUtil.sendMessage(online, "&c☠ &7Un joueur a été éliminé !");
            }
        }
    }

    /**
     * Find an alive Holou who chose to hide the dead.
     *
     * @return His role, or null if nobody hides the deaths
     */
    private HolouRole findDeathHider() {
        for (Role role : plugin.getRoleManager().getAllRoles()) {
            if (role instanceof HolouRole holou && holou.hidesDeaths() && isPlayerAlive(holou.getPlayerId())) {
                return holou;
            }
        }
        return null;
    }

    /**
     * Start the arena phase when few enough players remain.
     */
    private void checkArenaPhase() {
        if (gameState == GameState.MINING_PHASE && alivePlayers.size() <= remainingPlayersForArena) {
            MessageUtil.logInfo("Arena phase triggered!");
            startArenaPhase();
        }
    }

    /**
     * End the game when one player (or one allied team) remains.
     *
     * @return True if the game ended
     */
    private boolean checkGameEnd() {
        if (alivePlayers.size() <= 1 || (alivePlayers.size() > 1 && areAllAliveAllied())) {
            endGame(false);
            return true;
        }
        return false;
    }

    /**
     * Clean up all the resources of the finished game.
     */
    private void cleanup() {
        episodeManager.stopEpisodeTimer();

        if (borderShrinkTask != null) {
            borderShrinkTask.cancel();
            borderShrinkTask = null;
        }
        if (arenaCombatManager != null) {
            arenaCombatManager.deactivateArenaCombat();
        }

        plugin.getRoleManager().clearRoles();
        plugin.getCloneManager().cleanup();
        plugin.getDisguiseService().restoreAll();
        plugin.getSpecialItemManager().cleanup();
        plugin.getMiniGameStatsTracker().clearStats();
        plugin.getWorldManager().cleanup();
        alivePlayers.clear();
        eliminatedPlayers.clear();
    }

    public void forceArenaPhaseForTesting() {
        MessageUtil.logInfo("FORCING ARENA PHASE FOR TESTING");
        startArenaPhase();
    }

    /**
     * Get the current game state
     *
     * @return Current GameState
     */
    public GameState getGameState() {
        return gameState;
    }

    /**
     * Check if player is alive
     *
     * @param playerId UUID of player to check
     * @return True if player is alive
     */
    public boolean isPlayerAlive(UUID playerId) {
        return alivePlayers.contains(playerId);
    }

    /**
     * Get the list of alive players
     *
     * @return List of alive player UUIDs
     */
    public List<UUID> getAlivePlayers() {
        return new ArrayList<>(alivePlayers);
    }

    /**
     * Get the list of eliminated players
     *
     * @return List of eliminated player UUIDs
     */
    public List<UUID> getEliminatedPlayers() {
        return new ArrayList<>(eliminatedPlayers);
    }

    public boolean revivePlayer(UUID playerId, Location location, double health) {
        if (alivePlayers.contains(playerId)) {
            return false;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return false;
        }

        eliminatedPlayers.remove(playerId);
        alivePlayers.add(playerId);
        player.setGameMode(GameMode.SURVIVAL);

        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(playerId);
        if (ngnlPlayer != null) {
            player.setMaxHealth(ngnlPlayer.getMaxHealth());
            player.setHealth(Math.min(Math.max(1.0, health), player.getMaxHealth()));
        }

        if (location != null) {
            player.teleport(location);
        }

        reactivateRoleAfterRevive(playerId);
        MessageUtil.broadcast("&a" + player.getName() + " has been revived!");
        return true;
    }

    /**
     * A revived player lost the tasks of his role when he died: restart the finale abilities.
     *
     * @param playerId UUID of the revived player
     */
    private void reactivateRoleAfterRevive(UUID playerId) {
        NGNLPlayer data = plugin.getPlayerManager().getNGNLPlayer(playerId);
        if (gameState == GameState.ARENA_PHASE && data != null && data.getRole() != null) {
            data.getRole().onArenaPhaseStart();
        }
    }

    /**
     * Check if pledges are active
     *
     * @return True if pledges are active
     */
    public boolean arePledgesActive() {
        return pledgesActive;
    }

    /**
     * Get the episode manager
     *
     * @return EpisodeManager
     */
    public EpisodeManager getEpisodeManager() {
        return episodeManager;
    }

    /**
     * Get the scoreboard manager
     *
     * @return ScoreboardManager
     */
    public NGNLScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    /**
     * Get the last mini-game a player lost on
     *
     * @param playerId UUID of the player
     * @return MiniGameType or null if not found
     */
    public MiniGameType getLastMiniGameLostBy(UUID playerId) {
        return lastMiniGameLostBy.get(playerId);
    }

    /**
     * Set the last mini-game a player lost on
     *
     * @param playerId UUID of the player
     * @param miniGameType Mini-game type
     */
    public void setLastMiniGameLostBy(UUID playerId, MiniGameType miniGameType) {
        lastMiniGameLostBy.put(playerId, miniGameType);
    }

    /**
     * Get the scheduled mini-game opponent for a player
     *
     * @param playerId UUID of the player
     * @return UUID of opponent or null if not found
     */
    public UUID getScheduledMiniGameOpponent(UUID playerId) {
        return scheduledMiniGames.get(playerId);
    }

    /**
     * Schedule a mini-game between two players
     *
     * @param player1Id UUID of player 1
     * @param player2Id UUID of player 2
     */
    public void scheduleMinigame(UUID player1Id, UUID player2Id) {
        scheduledMiniGames.put(player1Id, player2Id);
        scheduledMiniGames.put(player2Id, player1Id);
    }

    /**
     * Set the mini-game type for a scheduled mini-game
     *
     * @param player1Id UUID of player 1
     * @param player2Id UUID of player 2
     * @param miniGameType Mini-game type
     */
    public void setScheduledMiniGameType(UUID player1Id, UUID player2Id, MiniGameType miniGameType) {
        String gameId = player1Id.toString() + "-" + player2Id.toString();
        scheduledMiniGameTypes.put(gameId, miniGameType);
    }

    /**
     * Get the mini-game type for a scheduled mini-game
     *
     * @param player1Id UUID of player 1
     * @param player2Id UUID of player 2
     * @return MiniGameType or null if not found
     */
    public MiniGameType getScheduledMiniGameType(UUID player1Id, UUID player2Id) {
        String gameId = player1Id.toString() + "-" + player2Id.toString();
        return scheduledMiniGameTypes.get(gameId);
    }

    /**
     * Clear a scheduled mini-game
     *
     * @param player1Id UUID of player 1
     * @param player2Id UUID of player 2
     */
    public void clearScheduledMiniGame(UUID player1Id, UUID player2Id) {
        scheduledMiniGames.remove(player1Id);
        scheduledMiniGames.remove(player2Id);

        String gameId = player1Id.toString() + "-" + player2Id.toString();
        String reverseGameId = player2Id.toString() + "-" + player1Id.toString();

        scheduledMiniGameTypes.remove(gameId);
        scheduledMiniGameTypes.remove(reverseGameId);
    }
}
