package be.thespattt.ngnl.event.listener;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.duo.IzunaRole;
import be.thespattt.ngnl.role.duo.ShiroRole;
import be.thespattt.ngnl.role.duo.SoraRole;
import be.thespattt.ngnl.role.solo.GhostRole;
import be.thespattt.ngnl.role.solo.KainasRole;
import be.thespattt.ngnl.role.solo.OkeinRole;
import be.thespattt.ngnl.role.solo.TetoRole;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
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
                if (!isMiniGameWorld(player.getWorld())) {
                    ngnlPlayer.setLastLocation(player.getLocation());
                }
            }
        }
    }

    private boolean isMiniGameWorld(World world) {
        World miniGameWorld = plugin.getWorldManager().getMinigameWorld();
        return world != null && miniGameWorld != null && world.getUID().equals(miniGameWorld.getUID());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerShouldDie(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.isCancelled()) return;
        if (isMiniGameWorld(victim.getWorld()) || plugin.getMiniGameEngine().isPlayerInMiniGame(victim.getUniqueId())) {
            return;
        }

        double finalHealth = victim.getHealth() - event.getFinalDamage();
        if (finalHealth <= 0) {
            UUID killerId = plugin.getCombatTracker().getLastDamager(victim.getUniqueId());
            GameState gameState = plugin.getGameManager().getGameState();
            if (killerId != null && gameState == GameState.MINING_PHASE) {
                Player killer = Bukkit.getPlayer(killerId);
                event.setCancelled(true);
                victim.setHealth(victim.getMaxHealth());
                if (killer != null){
                    killer.setHealth(killer.getMaxHealth());
                }
                plugin.getMiniGameManager().startMiniGameDuel(killer, victim);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (isMiniGameWorld(player.getWorld()) || plugin.getMiniGameEngine().isPlayerInMiniGame(player.getUniqueId())) {
            return;
        }

        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            return;
        }

        Player killer = player.getKiller();
        UUID killerId = killer != null ? killer.getUniqueId() : null;

        plugin.getGameManager().handlePlayerElimination(player.getUniqueId(), killerId);

        String deathMessage = "&c" + player.getName() + " has been eliminated!";
        if (killer != null) {
            deathMessage += " &7(Killed by " + killer.getName() + ")";
        }

        event.setDeathMessage(null);
        MessageUtil.broadcast(deathMessage);

        if (killer == null && plugin.getGameManager().getGame().getLastMiniGameLostBy(player.getUniqueId()) != null) {
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

        NGNLPlayer damagedNGNL = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (damagedNGNL != null && damagedNGNL.getRole() instanceof KainasRole) {
            switch (event.getCause()) {
                case FIRE:
                case FIRE_TICK:
                case LAVA:
                    event.setDamage(event.getDamage() * 2.0);
                    break;
                default:
                    break;
            }
        }

        if (event.getFinalDamage() > 0) {
            for (var role : plugin.getRoleManager().getAllRoles()) {
                if (role instanceof OkeinRole okeinRole) {
                    okeinRole.registerFirstDamagedPlayer(player.getUniqueId());
                }
            }
        }

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

        switch (gameState) {
            case WAITING:
            case STARTING:
                // No damage during waiting or starting phase
                event.setCancelled(true);
                break;

            case MINING_PHASE:
                handleMiningPhaseDamage(event);
                break;

            case ARENA_PHASE:
                handleArenaPhaseDamage(event);
                break;

            case ENDED:
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

        NGNLPlayer preCheckAttacker = plugin.getPlayerManager().getNGNLPlayer(attacker.getUniqueId());
        if (preCheckAttacker != null && preCheckAttacker.getRole() instanceof IzunaRole izunaRole && izunaRole.isProtectedByShield()) {
            event.setDamage(0);
            MessageUtil.sendMessage(attacker, "&cYou cannot attack while protected by the Hatsuse Shield.");
            return;
        }
        if (preCheckAttacker != null && preCheckAttacker.getRole() instanceof GhostRole ghostRole && ghostRole.isHidden()) {
            event.setDamage(0);
            MessageUtil.sendMessage(attacker, "&cYou cannot attack while hidden as 179 Ghost.");
            return;
        }
        if (preCheckAttacker != null && preCheckAttacker.getRole() instanceof TetoRole tetoRole && tetoRole.isAttackLocked()) {
            event.setDamage(0);
            MessageUtil.sendMessage(attacker, "&cThe King's Piece prevents you from attacking right now.");
            return;
        }
        if (preCheckAttacker != null && preCheckAttacker.getRole() instanceof SoraRole soraRole && soraRole.isDistancePenaltyActive()) {
            event.setDamage(event.getDamage() * 0.75);
        }
        if (preCheckAttacker != null && preCheckAttacker.getRole() instanceof ShiroRole shiroRole && shiroRole.isDistancePenaltyActive()) {
            event.setDamage(event.getDamage() * 0.75);
        }

        // Check if PvP is allowed
        boolean pvpEnabled = plugin.getConfigManager().getGameConfig().isForceEnablePvP();

        switch (gameState) {
            case WAITING:
            case STARTING:
                // No PvP during waiting or starting phase
                event.setCancelled(true);
                break;

            case MINING_PHASE:
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
                            event.setDamage(0);
                            MessageUtil.sendMessage(attacker, "&cYou cannot attack your duo partner!");
                            return;
                        }
                    }

                    // Check alliances
                    if (attackerNGNL.hasAlliancePartner() &&
                            attackerNGNL.getAlliancePartner().equals(victim.getUniqueId())) {
                        // Players are in an alliance
                        event.setDamage(0);
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
                startWaterDamageTask(ngnlPlayer.getPlayer());
            }
        }
    }

    private void startWaterDamageTask(Player player) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Block block = player.getLocation().getBlock();
            Block blockAbove = player.getLocation().add(0, 1, 0).getBlock();

            if (block.getType() == Material.WATER || blockAbove.getType() == Material.WATER) {
                player.damage(1.5);
            }

            else if (player.getWorld().hasStorm() &&
                    player.getLocation().getBlock().getLightFromSky() == 15) {
                player.damage(1.5);
            }
        }, 0L, 20L);
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
