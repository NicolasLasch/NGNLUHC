package be.thespattt.ngnl.event.listener;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.event.custom.MiniGameEndEvent;
import be.thespattt.ngnl.event.custom.MiniGameStartEvent;
import be.thespattt.ngnl.minigame.MiniGameSessionManager;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Event listener for mini-game related events
 */
public class MiniGameListener implements Listener {

    private final NoGameNoLife plugin;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public MiniGameListener(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle mini-game start event
     *
     * @param event Mini-game start event
     */
    @EventHandler
    public void onMiniGameStart(MiniGameStartEvent event) {
        // Get players
        Player player1 = Bukkit.getPlayer(event.getPlayer1Id());
        Player player2 = Bukkit.getPlayer(event.getPlayer2Id());

        if (player1 == null || player2 == null) {
            return;
        }

        // Get player data
        NGNLPlayer ngnlPlayer1 = plugin.getPlayerManager().getNGNLPlayer(player1.getUniqueId());
        NGNLPlayer ngnlPlayer2 = plugin.getPlayerManager().getNGNLPlayer(player2.getUniqueId());

        clearBaseWorldEffectsForMiniGame(player1, ngnlPlayer1);
        clearBaseWorldEffectsForMiniGame(player2, ngnlPlayer2);

        // Notify roles about mini-game start
        if (ngnlPlayer1 != null && ngnlPlayer1.getRole() != null) {
            ngnlPlayer1.getRole().onMiniGameStart(player2.getUniqueId(), event.getMiniGameType(), event.isPlayer1WonPvP());
        }

        if (ngnlPlayer2 != null && ngnlPlayer2.getRole() != null) {
            ngnlPlayer2.getRole().onMiniGameStart(player1.getUniqueId(), event.getMiniGameType(), !event.isPlayer1WonPvP());
        }

        // Broadcast mini-game start
        MessageUtil.broadcast("&6A mini-game has started: &e" + event.getMiniGameType().getDisplayName());
        MessageUtil.broadcast("&6" + player1.getName() + " vs " + player2.getName());

        // Give players mini-game specific items or abilities
        giveMiniGameItems(player1, player2, event.getMiniGameType());
    }

    /**
     * Handle mini-game end event
     *
     * @param event Mini-game end event
     */
    @EventHandler
    public void onMiniGameEnd(MiniGameEndEvent event) {
        // Get players
        Player player1 = Bukkit.getPlayer(event.getPlayer1Id());
        Player player2 = Bukkit.getPlayer(event.getPlayer2Id());

        if (player1 == null || player2 == null) {
            return;
        }

        // Get player data
        NGNLPlayer ngnlPlayer1 = plugin.getPlayerManager().getNGNLPlayer(player1.getUniqueId());
        NGNLPlayer ngnlPlayer2 = plugin.getPlayerManager().getNGNLPlayer(player2.getUniqueId());

        // Notify roles about mini-game end
        if (ngnlPlayer1 != null && ngnlPlayer1.getRole() != null) {
            ngnlPlayer1.getRole().onMiniGameEnd(player2.getUniqueId(), event.getMiniGameType(), event.isPlayer1Winner());
        }

        if (ngnlPlayer2 != null && ngnlPlayer2.getRole() != null) {
            ngnlPlayer2.getRole().onMiniGameEnd(player1.getUniqueId(), event.getMiniGameType(), !event.isPlayer1Winner());
        }

        Player winner = event.isPlayer1Winner() ? player1 : player2;
        if (winner != null) {
            applyWinnerRoleBonuses(winner);
        }

        clearTemporaryMiniGameEffects(player1);
        clearTemporaryMiniGameEffects(player2);
    }

    private void clearBaseWorldEffectsForMiniGame(Player player, NGNLPlayer ngnlPlayer) {
        if (ngnlPlayer == null || ngnlPlayer.getRole() == null) {
            return;
        }

        switch (ngnlPlayer.getRole().getRoleType()) {
            case SORA:
            case SHIRO:
                player.removePotionEffect(PotionEffectType.SPEED);
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                player.removePotionEffect(PotionEffectType.WEAKNESS);
                break;
            default:
                break;
        }
    }

    /**
     * Give mini-game specific items or abilities to players
     *
     * @param player1 Player 1
     * @param player2 Player 2
     * @param miniGameType Type of mini-game
     */
    private void giveMiniGameItems(Player player1, Player player2, MiniGameType miniGameType) {
        // Get player data
        NGNLPlayer ngnlPlayer1 = plugin.getPlayerManager().getNGNLPlayer(player1.getUniqueId());
        NGNLPlayer ngnlPlayer2 = plugin.getPlayerManager().getNGNLPlayer(player2.getUniqueId());

        // Apply mini-game specific items or abilities based on mini-game type
        switch (miniGameType) {
            case BLOC_PARTY:
                // Check for Kurami and Izuna roles (they get special abilities in Bloc Party)
                if (ngnlPlayer1 != null && ngnlPlayer1.getRole() != null) {
                    if (ngnlPlayer1.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.KURAMI) {
                        // Kurami has 2 lives in Bloc Party
                        MessageUtil.sendMessage(player1, "&aYou have &e2 lives &ain this mini-game!");
                    } else if (ngnlPlayer1.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.IZUNA) {
                        player2.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                PotionEffectType.BLINDNESS,
                                Integer.MAX_VALUE,
                                0,
                                false,
                                false
                        ));
                        MessageUtil.sendMessage(player1, "&aYou've applied &eBlindness &ato your opponent!");
                        MessageUtil.sendMessage(player2, "&cIzuna applied &eBlindness &cto you!");
                    }
                }

                if (ngnlPlayer2 != null && ngnlPlayer2.getRole() != null) {
                    if (ngnlPlayer2.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.KURAMI) {
                        // Kurami has 2 lives in Bloc Party
                        MessageUtil.sendMessage(player2, "&aYou have &e2 lives &ain this mini-game!");
                    } else if (ngnlPlayer2.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.IZUNA) {
                        player1.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                PotionEffectType.BLINDNESS,
                                Integer.MAX_VALUE,
                                0,
                                false,
                                false
                        ));
                        MessageUtil.sendMessage(player2, "&aYou've applied &eBlindness &ato your opponent!");
                        MessageUtil.sendMessage(player1, "&cIzuna applied &eBlindness &cto you!");
                    }
                }
                break;

            case FLOOR_IS_LAVA:
                // Check for Stephanie and Feel roles
                if (ngnlPlayer1 != null && ngnlPlayer1.getRole() != null) {
                    if (ngnlPlayer1.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.STEPHANIE) {
                        // Stephanie has Speed in The Floor Is Lava
                        player1.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SPEED,
                                Integer.MAX_VALUE,
                                0,
                                false,
                                false
                        ));
                        MessageUtil.sendMessage(player1, "&aYou have &eSpeed I &ain this mini-game!");
                    } else if (ngnlPlayer1.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.FEEL) {
                        // Feel has 2 lives in The Floor Is Lava
                        MessageUtil.sendMessage(player1, "&aYou have &e2 lives &ain this mini-game!");
                    }
                }

                if (ngnlPlayer2 != null && ngnlPlayer2.getRole() != null) {
                    if (ngnlPlayer2.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.STEPHANIE) {
                        // Stephanie has Speed in The Floor Is Lava
                        player2.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SPEED,
                                Integer.MAX_VALUE,
                                0,
                                false,
                                false
                        ));
                        MessageUtil.sendMessage(player2, "&aYou have &eSpeed I &ain this mini-game!");
                    } else if (ngnlPlayer2.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.FEEL) {
                        // Feel has 2 lives in The Floor Is Lava
                        MessageUtil.sendMessage(player2, "&aYou have &e2 lives &ain this mini-game!");
                    }
                }
                break;

            case TNT_RUN:
            case SPLEGG:
                // Check for Ino role (gives slowness to opponent)
                if (ngnlPlayer1 != null && ngnlPlayer1.getRole() != null &&
                        ngnlPlayer1.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.INO) {

                    // Ino applies slowness to opponent
                    player2.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.SLOWNESS,
                            Integer.MAX_VALUE,
                            0,
                            false,
                            false
                    ));
                    MessageUtil.sendMessage(player1, "&aYou've applied &eSlowness I &ato your opponent!");
                    MessageUtil.sendMessage(player2, "&cIno has applied &eSlowness I &cto you!");
                }

                if (ngnlPlayer2 != null && ngnlPlayer2.getRole() != null &&
                        ngnlPlayer2.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.INO) {

                    // Ino applies slowness to opponent
                    player1.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.SLOWNESS,
                            Integer.MAX_VALUE,
                            0,
                            false,
                            false
                    ));
                    MessageUtil.sendMessage(player2, "&aYou've applied &eSlowness I &ato your opponent!");
                    MessageUtil.sendMessage(player1, "&cIno has applied &eSlowness I &cto you!");
                }
                break;

            case PARKOUR:
                // Check for Schwi role
                if (ngnlPlayer1 != null && ngnlPlayer1.getRole() != null &&
                        ngnlPlayer1.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.SCHWI) {

                    // Schwi has Speed and Jump boost
                    player1.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SPEED,
                            Integer.MAX_VALUE,
                            0,
                            false,
                            false
                    ));
                    player1.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.JUMP_BOOST,
                            Integer.MAX_VALUE,
                            0,
                            false,
                            false
                    ));
                    MessageUtil.sendMessage(player1, "&aYou have &eSpeed I &aand &eJump Boost I &ain this mini-game!");
                }

                if (ngnlPlayer2 != null && ngnlPlayer2.getRole() != null &&
                        ngnlPlayer2.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.SCHWI) {

                    // Schwi has Speed and Jump boost
                    player2.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SPEED,
                            Integer.MAX_VALUE,
                            0,
                            false,
                            false
                    ));
                    player2.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.JUMP_BOOST,
                            Integer.MAX_VALUE,
                            0,
                            false,
                            false
                    ));
                    MessageUtil.sendMessage(player2, "&aYou have &eSpeed I &aand &eJump Boost I &ain this mini-game!");
                }
                break;

            case DES_A_COUDRE:
                // Check for Kurami role (has 2 lives)
                if (ngnlPlayer1 != null && ngnlPlayer1.getRole() != null &&
                        ngnlPlayer1.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.KURAMI) {

                    MessageUtil.sendMessage(player1, "&aYou have &e2 lives &ain this mini-game!");
                }

                if (ngnlPlayer2 != null && ngnlPlayer2.getRole() != null &&
                        ngnlPlayer2.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.KURAMI) {

                    MessageUtil.sendMessage(player2, "&aYou have &e2 lives &ain this mini-game!");
                }
                break;

            case ANVIL_RAIN:
                // Check for Feel role (has 2 lives)
                if (ngnlPlayer1 != null && ngnlPlayer1.getRole() != null &&
                        ngnlPlayer1.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.FEEL) {

                    MessageUtil.sendMessage(player1, "&aYou have &e2 lives &ain this mini-game!");
                }

                if (ngnlPlayer2 != null && ngnlPlayer2.getRole() != null &&
                        ngnlPlayer2.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.FEEL) {

                    MessageUtil.sendMessage(player2, "&aYou have &e2 lives &ain this mini-game!");
                }
                break;

            default:
                // No special items or abilities for other mini-games
                break;
        }
    }

    /**
     * Give a reward to the winner of a mini-game
     *
     * @param winner Player who won the mini-game
     * @param miniGameType Type of mini-game
     */
    private void applyWinnerRoleBonuses(Player winner) {
        // Get player data
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(winner.getUniqueId());

        // Check if player is Riku (who gains 1 heart when winning mini-games)
        if (ngnlPlayer != null && ngnlPlayer.getRole() != null &&
                ngnlPlayer.getRole().getRoleType() == be.thespattt.ngnl.role.RoleType.RIKU) {

            // Give 1 heart
            double currentMaxHealth = ngnlPlayer.getMaxHealth();
            ngnlPlayer.setMaxHealth(currentMaxHealth + 2.0); // 1 heart = 2 health points

            MessageUtil.sendMessage(winner, "&aYou gained &c1 heart &afor winning the mini-game!");
        }
    }

    private void clearTemporaryMiniGameEffects(Player player) {
        if (player == null) {
            return;
        }
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    @EventHandler
    public void onMiniGameSelectionClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!ChatColor.stripColor(event.getView().getTitle()).equalsIgnoreCase("Choose a Mini-Game")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        MiniGameSessionManager manager = plugin.getMiniGameSessionManager();
        UUID victimId = manager.getPendingVictim(player.getUniqueId());
        if (victimId == null) {
            player.closeInventory();
            return;
        }

        String display = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        MiniGameType selected = MiniGameType.getByName(display);

        if (selected != null) {
            Player victim = Bukkit.getPlayer(victimId);
            if (victim != null) {
                manager.clearPending(player.getUniqueId());
                MessageUtil.sendMessage(victim, "Playing Minigame : " + selected);
                MessageUtil.sendMessage(player, "Playing Minigame : " + selected);
                plugin.getMiniGameEngine().startGame(selected, player, victim);
            }
        }

        player.closeInventory();
    }

}
