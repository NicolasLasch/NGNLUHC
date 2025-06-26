package be.thespattt.ngnl.arena;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche pour faire rétrécir la bordure de l'arène progressivement
 */
public class ArenaBorderShrinkTask extends BukkitRunnable {

    private final NoGameNoLife plugin;
    private final World arenaWorld;
    private final WorldBorder border;

    private final int initialSize;
    private final int finalSize;
    private final int shrinkInterval; // en secondes
    private final int totalDuration; // durée totale en secondes

    private int currentSize;
    private int timeElapsed = 0;
    private int lastWarningTime = 0;

    public ArenaBorderShrinkTask(NoGameNoLife plugin, World arenaWorld) {
        this.plugin = plugin;
        this.arenaWorld = arenaWorld;
        this.border = arenaWorld.getWorldBorder();

        // Charger la configuration
        this.initialSize = plugin.getConfigManager().getGameConfig().getInitialArenaBorderSize();
        this.finalSize = plugin.getConfigManager().getGameConfig().getFinalArenaBorderSize();
        this.shrinkInterval = plugin.getConfigManager().getGameConfig().getBorderShrinkInterval();
        this.totalDuration = plugin.getConfigManager().getGameConfig().getArenaTotalDuration();

        this.currentSize = initialSize;

        MessageUtil.logInfo("Tâche de rétrécissement de bordure initialisée:");
        MessageUtil.logInfo("- Taille initiale: " + initialSize + "x" + initialSize);
        MessageUtil.logInfo("- Taille finale: " + finalSize + "x" + finalSize);
        MessageUtil.logInfo("- Intervalle: " + shrinkInterval + " secondes");
        MessageUtil.logInfo("- Durée totale: " + totalDuration + " secondes");
    }

    @Override
    public void run() {
        timeElapsed++;

        // Vérifier si le jeu est toujours actif
        if (!plugin.getGameManager().isGameRunning()) {
            cancel();
            return;
        }

        // Vérifier si on a atteint la durée maximale
        if (timeElapsed >= totalDuration) {
            forceFinalShrink();
            cancel();
            return;
        }

        // Rétrécir la bordure selon l'intervalle
        if (timeElapsed % shrinkInterval == 0 && currentSize > finalSize) {
            shrinkBorder();
        }

        // Avertissements
        handleWarnings();

        // Vérifier les joueurs hors bordure
        checkPlayersOutsideBorder();
    }

    /**
     * Rétrécir la bordure d'un cran
     */
    private void shrinkBorder() {
        // Calculer la nouvelle taille
        int totalShrinks = totalDuration / shrinkInterval;
        int shrinkAmount = Math.max(1, (initialSize - finalSize) / totalShrinks);

        int newSize = Math.max(finalSize, currentSize - shrinkAmount);

        if (newSize != currentSize) {
            currentSize = newSize;

            // Appliquer le changement (transition lente)
            border.setSize(currentSize * 2, shrinkInterval); // Diamètre, durée de transition

            // Annoncer le rétrécissement
            String message = "&c&l⚠ BORDURE EN MOUVEMENT ⚠";
            String details = "&eLa bordure rétrécit à &c" + currentSize + "x" + currentSize + " &eblocs !";
            String warning = "&cRapprochez-vous du centre ou subissez des dégâts !";

            broadcastToArenaPlayers(message);
            broadcastToArenaPlayers(details);
            broadcastToArenaPlayers(warning);

            // Son d'alerte
            playAlertSound();

            MessageUtil.logInfo("Bordure rétrécie à " + currentSize + "x" + currentSize);
        }
    }

    /**
     * Forcer le rétrécissement final
     */
    private void forceFinalShrink() {
        if (currentSize > finalSize) {
            currentSize = finalSize;
            border.setSize(finalSize * 2, 30); // Rétrécissement rapide en 30 secondes

            broadcastToArenaPlayers("&4&l⚠ RÉTRÉCISSEMENT FINAL ⚠");
            broadcastToArenaPlayers("&cLa bordure se contracte à sa taille minimale !");
            broadcastToArenaPlayers("&4Battez-vous au centre ou mourez !");

            // Son dramatique
            playDramaticSound();

            MessageUtil.logInfo("Rétrécissement final forcé à " + finalSize + "x" + finalSize);
        }
    }

    /**
     * Gérer les avertissements temporels
     */
    private void handleWarnings() {
        int remainingTime = totalDuration - timeElapsed;

        // Avertissements à 10, 5, 3, 2, 1 minutes restantes
        int[] warningTimes = {600, 300, 180, 120, 60, 30, 10, 5, 3, 2, 1};

        for (int warningTime : warningTimes) {
            if (remainingTime == warningTime && lastWarningTime != warningTime) {
                lastWarningTime = warningTime;

                String timeString = formatTime(warningTime);
                String message = "&e⏰ &6" + timeString + " &erestantes avant la fin de partie !";

                broadcastToArenaPlayers(message);

                // Son selon l'urgence
                if (warningTime <= 10) {
                    playUrgentSound();
                } else if (warningTime <= 60) {
                    playWarningSound();
                }

                break;
            }
        }

        // Avertissement pour le prochain rétrécissement
        int timeToNextShrink = shrinkInterval - (timeElapsed % shrinkInterval);
        if (timeToNextShrink <= 10 && timeToNextShrink > 0) {
            if (timeElapsed % 1 == 0) { // Chaque seconde
                broadcastToArenaPlayers("&c⚠ Bordure rétrécit dans " + timeToNextShrink + " secondes !");
            }
        }
    }

    /**
     * Vérifier les joueurs en dehors de la bordure
     */
    private void checkPlayersOutsideBorder() {
        for (Player player : arenaWorld.getPlayers()) {
            if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) continue;

            double borderSize = border.getSize() / 2; // Rayon
            double centerX = border.getCenter().getX();
            double centerZ = border.getCenter().getZ();

            double playerX = player.getLocation().getX();
            double playerZ = player.getLocation().getZ();

            double distanceFromCenter = Math.sqrt(
                    Math.pow(playerX - centerX, 2) +
                            Math.pow(playerZ - centerZ, 2)
            );

            if (distanceFromCenter > borderSize) {
                // Joueur en dehors de la bordure
                double damage = Math.min(2.0, (distanceFromCenter - borderSize) * 0.5);

                player.damage(damage);
                MessageUtil.sendMessage(player, "&c💀 Vous êtes hors de la bordure ! (-" + String.format("%.1f", damage) + " ❤)");

                player.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR,
                        player.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    /**
     * Diffuser un message à tous les joueurs de l'arène
     */
    private void broadcastToArenaPlayers(String message) {
        for (Player player : arenaWorld.getPlayers()) {
            if (plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
                MessageUtil.sendMessage(player, message);
            }
        }
    }

    /**
     * Jouer un son d'alerte
     */
    private void playAlertSound() {
        for (Player player : arenaWorld.getPlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
        }
    }

    /**
     * Jouer un son d'avertissement
     */
    private void playWarningSound() {
        for (Player player : arenaWorld.getPlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
        }
    }

    /**
     * Jouer un son urgent
     */
    private void playUrgentSound() {
        for (Player player : arenaWorld.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
        }
    }

    /**
     * Jouer un son dramatique
     */
    private void playDramaticSound() {
        for (Player player : arenaWorld.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        }
    }

    /**
     * Formater le temps en string lisible
     */
    private String formatTime(int seconds) {
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            if (remainingSeconds > 0) {
                return minutes + "m" + remainingSeconds + "s";
            } else {
                return minutes + " minute" + (minutes > 1 ? "s" : "");
            }
        } else {
            return seconds + " seconde" + (seconds > 1 ? "s" : "");
        }
    }

    /**
     * Obtenir la taille actuelle de la bordure
     */
    public int getCurrentSize() {
        return currentSize;
    }

    /**
     * Obtenir le temps écoulé
     */
    public int getTimeElapsed() {
        return timeElapsed;
    }

    /**
     * Obtenir le temps restant
     */
    public int getTimeRemaining() {
        return totalDuration - timeElapsed;
    }
}