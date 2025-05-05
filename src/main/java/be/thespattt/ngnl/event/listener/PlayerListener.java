package be.thespattt.ngnl.event.listener;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

/**
 * Event listener for player-related events
 */
public class PlayerListener implements Listener {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public PlayerListener(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if game is running
        if (plugin.getGameManager().isGameRunning()) {
            // Check if player was in the game
            if (plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
                // Player was in the game and is alive
                MessageUtil.sendMessage(player, "&aWelcome back to the game!");

                // Load player data
                NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
                if (ngnlPlayer != null) {
                    // Set max health based on their current max health
                    player.setMaxHealth(ngnlPlayer.getMaxHealth());
                }
            } else {
                // Player is not in the game or is dead
                player.setGameMode(GameMode.SPECTATOR);
                MessageUtil.sendMessage(player, "&cYou are spectating the current game.");
            }

            // Update player scoreboard
            plugin.getGameManager().getGame().getScoreboardManager().updateScoreboard(player);
        } else {
            // Game is not running
            // Set to default game mode (adventure for lobby)
            player.setGameMode(GameMode.ADVENTURE);
            player.setMaxHealth(20.0);
            player.setHealth(20.0);

            // Teleport to lobby
            if (plugin.getWorldManager().getSpawnLocation(be.thespattt.ngnl.game.world.WorldType.WAITING) != null) {
                player.teleport(plugin.getWorldManager().getSpawnLocation(be.thespattt.ngnl.game.world.WorldType.WAITING));
            }

            MessageUtil.sendMessage(player, "&aWelcome to No Game No Life UHC!");
            MessageUtil.sendMessage(player, "&7The game is not currently running.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // If the game is running and the player is alive, save their current state
        if (plugin.getGameManager().isGameRunning() && plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
            if (ngnlPlayer != null) {
                // Save current health
                ngnlPlayer.setLastHealth(player.getHealth());
                ngnlPlayer.setLastLocation(player.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Check if game is running
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        // Check if player was in the game
        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            return;
        }

        // Get killer (if any)
        Player killer = player.getKiller();
        UUID killerId = killer != null ? killer.getUniqueId() : null;

        // Handle death in the game
        plugin.getGameManager().handlePlayerElimination(player.getUniqueId(), killerId);

        // Set death message
        String deathMessage = "&c" + player.getName() + " has been eliminated!";
        if (killer != null) {
            deathMessage += " &7(Killed by " + killer.getName() + ")";
        }

        event.setDeathMessage(null); // Remove default death message
        MessageUtil.broadcast(deathMessage);

        // Only reveal the mini-game the player lost on
        if (plugin.getGameManager().getGame().getLastMiniGameLostBy(player.getUniqueId()) != null) {
            String miniGameName = plugin.getGameManager().getGame().getLastMiniGameLostBy(player.getUniqueId()).getDisplayName();
            MessageUtil.broadcast("&7They were defeated in: &f" + miniGameName);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Check if game is running
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        // Set player to spectator mode
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.setGameMode(GameMode.SPECTATOR);

            // Set respawn location based on game phase
            GameState gameState = plugin.getGameManager().getGameState();
            if (gameState == GameState.ARENA_PHASE) {
                // Respawn at arena center
                if (plugin.getWorldManager().getSpawnLocation(be.thespattt.ngnl.game.world.WorldType.ARENA) != null) {
                    player.teleport(plugin.getWorldManager().getSpawnLocation(be.thespattt.ngnl.game.world.WorldType.ARENA));
                }
            }

            MessageUtil.sendMessage(player, "&cYou have been eliminated from the game!");
            MessageUtil.sendMessage(player, "&7You are now in spectator mode.");
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        // Check if entity is a player
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Check if game is running
        if (!plugin.getGameManager().isGameRunning()) {
            // No damage outside of game
            event.setCancelled(true);
            return;
        }

        // Check if player is alive in the game
        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            // No damage to spectators
            event.setCancelled(true);
            return;
        }

        // Handle different damage causes based on game rules
        GameState gameState = plugin.getGameManager().getGameState();

        // Handle PvP separately
        if (event instanceof EntityDamageByEntityEvent && ((EntityDamageByEntityEvent) event).getDamager() instanceof Player) {
            handlePvPDamage((EntityDamageByEntityEvent) event, gameState);
            return;
        }

        // Handle damage based on phase
        switch (gameState) {
            case WAITING:
            case STARTING:
                // No damage during waiting or starting phase
                event.setCancelled(true);
                break;

            case MINING_PHASE:
                // Normal damage during mining phase except specific rules
                handleMiningPhaseDamage(event);
                break;

            case ARENA_PHASE:
                // Special rules for arena phase
                handleArenaPhaseDamage(event);
                break;

            case ENDED:
                // No damage after game has ended
                event.setCancelled(true);
                break;
        }
    }

    /**
     * Handle PvP damage
     *
     * @param event Entity damage by entity event
     * @param gameState Current game state
     */
    private void handlePvPDamage(EntityDamageByEntityEvent event, GameState gameState) {
        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        // Check if PvP is allowed
        boolean pvpEnabled = plugin.getConfigManager().getGameConfig().isForceEnablePvP();

        switch (gameState) {
            case WAITING:
            case STARTING:
                // No PvP during waiting or starting phase
                event.setCancelled(true);
                break;

            case MINING_PHASE:
                // PvP during mining phase depends on settings and time
                if (!pvpEnabled) {
                    // Check if PvP grace period has ended
                    int currentEpisode = plugin.getGameManager().getGame().getEpisodeManager().getCurrentEpisode();
                    int pvpEpisode = plugin.getConfigManager().getGameConfig().getPvpEnableEpisode();

                    if (currentEpisode < pvpEpisode) {
                        // PvP grace period still active
                        event.setCancelled(true);
                        MessageUtil.sendMessage(attacker, "&cPvP is not enabled until episode " + pvpEpisode + "!");
                        return;
                    }
                }

                // Check for alliances and pledges
                NGNLPlayer attackerNGNL = plugin.getPlayerManager().getNGNLPlayer(attacker.getUniqueId());
                NGNLPlayer victimNGNL = plugin.getPlayerManager().getNGNLPlayer(victim.getUniqueId());

                // Check duo partners
                if (attackerNGNL != null && victimNGNL != null) {
                    // Check if players are duo partners
                    if (attackerNGNL.getRole() != null && victimNGNL.getRole() != null) {
                        if (attackerNGNL.getRole().getRoleType().isDuo() &&
                                attackerNGNL.getRole().getPartnerUUID() != null &&
                                attackerNGNL.getRole().getPartnerUUID().equals(victim.getUniqueId())) {
                            // Players are duo partners, cancel damage
                            event.setCancelled(true);
                            MessageUtil.sendMessage(attacker, "&cYou cannot attack your duo partner!");
                            return;
                        }
                    }

                    // Check alliances
                    if (attackerNGNL.hasAlliancePartner() &&
                            attackerNGNL.getAlliancePartner().equals(victim.getUniqueId())) {
                        // Players are in an alliance
                        event.setCancelled(true);
                        MessageUtil.sendMessage(attacker, "&cYou cannot attack your alliance partner!");
                        return;
                    }

                    // Check pledges (if PledgeCommand is accessible)
                    if (plugin.getCommandManager().getPledgeCommand() != null &&
                            plugin.getCommandManager().getPledgeCommand().hasPledge(
                                    attacker.getUniqueId(), victim.getUniqueId())) {
                        // Players have a pledge, check terms for non-aggression
                        // This would require parsing pledge terms, which could be complex
                        // For now, we'll just notify the attacker about the pledge
                        MessageUtil.sendMessage(attacker, "&eRemember: You have a pledge with " + victim.getName() + "!");
                    }
                }
                break;

            case ARENA_PHASE:
                // PvP always allowed in arena phase, but still check for duo partners
                NGNLPlayer attackerNGNLArena = plugin.getPlayerManager().getNGNLPlayer(attacker.getUniqueId());
                NGNLPlayer victimNGNLArena = plugin.getPlayerManager().getNGNLPlayer(victim.getUniqueId());

                // Check if players are duo partners
                if (attackerNGNLArena != null && victimNGNLArena != null &&
                        attackerNGNLArena.getRole() != null && victimNGNLArena.getRole() != null) {
                    if (attackerNGNLArena.getRole().getRoleType().isDuo() &&
                            attackerNGNLArena.getRole().getPartnerUUID() != null &&
                            attackerNGNLArena.getRole().getPartnerUUID().equals(victim.getUniqueId())) {
                        // Players are duo partners, cancel damage
                        event.setCancelled(true);
                        MessageUtil.sendMessage(attacker, "&cYou cannot attack your duo partner!");
                        return;
                    }
                }
                break;

            case ENDED:
                // No PvP after game has ended
                event.setCancelled(true);
                break;
        }
    }

    /**
     * Handle damage during mining phase
     *
     * @param event Entity damage event
     */
    private void handleMiningPhaseDamage(EntityDamageEvent event) {
        // Specific rules for mining phase
        // For example, handle special faction benefits like Old Deus resistance to environmental damage

        Player player = (Player) event.getEntity();
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());

        if (ngnlPlayer != null && ngnlPlayer.getRole() != null) {
            // Check faction-specific damage modifiers
            switch (ngnlPlayer.getRole().getRoleType().getFaction()) {
                case OLD_DEUS:
                    // Old Deus have resistance to environmental damage
                    if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
                            event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
                        // Reduce environmental damage by 30%
                        event.setDamage(event.getDamage() * 0.7);
                    }
                    break;

                // Add other faction-specific rules as needed
            }

            // Role-specific damage handling
            // This would be better implemented in the role classes themselves
            // but we'll add a simple example here
            if (ngnlPlayer.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.OKEIN) {
                // Okein takes damage from water
                if (player.getLocation().getBlock().isLiquid() &&
                        event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
                    // Increase drowning damage
                    event.setDamage(event.getDamage() * 1.5);
                }
            }
        }
    }

    /**
     * Handle damage during arena phase
     *
     * @param event Entity damage event
     */
    private void handleArenaPhaseDamage(EntityDamageEvent event) {
        // Arena phase might have special damage rules
        // For now, we'll just let all damage through
    }
}