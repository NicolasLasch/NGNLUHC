package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.game.GameState;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.duo.DuoRole;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Command for solo players to form alliances
 */
public class AllianceCommand implements CommandExecutor, TabCompleter {

    private final NoGameNoLife plugin;

    // Map to track alliance requests (requester UUID -> target UUID)
    private final Map<UUID, UUID> allianceRequests = new HashMap<>();
    // Map to track request expiration times
    private final Map<UUID, Long> requestExpireTimes = new HashMap<>();
    // Request expiration time in seconds
    private static final int REQUEST_EXPIRE_TIME = 60;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public AllianceCommand(NoGameNoLife plugin) {
        this.plugin = plugin;

        // Schedule task to clean up expired requests
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredRequests, 20L, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, "&cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check if game is running
        if (!plugin.getGameManager().isGameRunning()) {
            MessageUtil.sendMessage(player, "&cThere is no game in progress!");
            return true;
        }

        // Check if player is alive
        if (!plugin.getGameManager().isPlayerAlive(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou are not alive in the current game!");
            return true;
        }

        // Check if solo alliances are allowed
        if (!plugin.getConfigManager().getGameConfig().isAllowSoloAlliances()) {
            MessageUtil.sendMessage(player, "&cSolo alliances are disabled in this game!");
            return true;
        }

        // Check if player has a role
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        if (ngnlPlayer == null || ngnlPlayer.getRole() == null) {
            MessageUtil.sendMessage(player, "&cYou don't have a role assigned!");
            return true;
        }

        Role role = ngnlPlayer.getRole();

        // Check if player is in a duo role (they can't form alliances)
        if (role instanceof DuoRole) {
            MessageUtil.sendMessage(player, "&cYou already have a duo partner and cannot form an alliance!");
            return true;
        }

        // Some solo roles (Think Nirvalen) win alone and can never ally
        if (!role.canFormAlliance()) {
            MessageUtil.sendMessage(player, "&cTon rôle te force à gagner seul : aucune alliance possible !");
            return true;
        }

        // Check if player already has an alliance
        if (ngnlPlayer.hasAlliancePartner()) {
            // Check if they want to break the alliance
            if (args.length > 0 && args[0].equalsIgnoreCase("break")) {
                return handleBreakAlliance(player, ngnlPlayer);
            }

            // Get partner name
            UUID partnerId = ngnlPlayer.getAlliancePartner();
            String partnerName = "Unknown";
            Player partnerPlayer = Bukkit.getPlayer(partnerId);
            if (partnerPlayer != null) {
                partnerName = partnerPlayer.getName();
            }

            MessageUtil.sendMessage(player, "&cYou already have an alliance with " + partnerName + "!");
            MessageUtil.sendMessage(player, "&cUse '/alliance break' to break this alliance.");
            return true;
        }

        // Check if we're in the arena phase (alliances can only be formed before arena)
        GameState gameState = plugin.getGameManager().getGameState();
        if (gameState == GameState.ARENA_PHASE) {
            MessageUtil.sendMessage(player, "&cAlliances cannot be formed during the arena phase!");
            return true;
        }

        // Handle alliance command
        if (args.length == 0) {
            MessageUtil.sendMessage(player, "&cUsage: /alliance <player> or /alliance accept <player> or /alliance break");
            return true;
        }

        // Handle alliance accept
        if (args[0].equalsIgnoreCase("accept")) {
            return handleAcceptAlliance(player, args);
        }

        // Handle alliance request
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            MessageUtil.sendMessage(player, "&cPlayer not found: " + args[0]);
            return true;
        }

        // Can't ally with yourself
        if (target.equals(player)) {
            MessageUtil.sendMessage(player, "&cYou cannot form an alliance with yourself!");
            return true;
        }

        // Check if target is alive
        if (!plugin.getGameManager().isPlayerAlive(target.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c" + target.getName() + " is not alive in the current game!");
            return true;
        }

        // Check if target has a role
        NGNLPlayer targetNGNLPlayer = plugin.getPlayerManager().getNGNLPlayer(target.getUniqueId());
        if (targetNGNLPlayer == null || targetNGNLPlayer.getRole() == null) {
            MessageUtil.sendMessage(player, "&c" + target.getName() + " doesn't have a role assigned!");
            return true;
        }

        Role targetRole = targetNGNLPlayer.getRole();

        // Roles that win alone can not be allied with
        if (!targetRole.canFormAlliance()) {
            MessageUtil.sendMessage(player, "&c" + target.getName() + " ne peut pas former d'alliance !");
            return true;
        }

        // Check if target is in a duo role
        if (targetRole instanceof DuoRole) {
            MessageUtil.sendMessage(player, "&c" + target.getName() + " is in a duo role and cannot form an alliance!");
            return true;
        }

        // Check if target already has an alliance
        if (targetNGNLPlayer.hasAlliancePartner()) {
            MessageUtil.sendMessage(player, "&c" + target.getName() + " already has an alliance!");
            return true;
        }

        // Send alliance request
        allianceRequests.put(player.getUniqueId(), target.getUniqueId());
        requestExpireTimes.put(player.getUniqueId(), System.currentTimeMillis() + (REQUEST_EXPIRE_TIME * 1000));

        MessageUtil.sendMessage(player, "&aAlliance request sent to " + target.getName() + "!");
        MessageUtil.sendMessage(player, "&7The request will expire in " + REQUEST_EXPIRE_TIME + " seconds.");

        MessageUtil.sendMessage(target, "&6" + player.getName() + " has requested to form an alliance with you!");
        MessageUtil.sendMessage(target, "&6Type '/alliance accept " + player.getName() + "' to accept.");
        MessageUtil.sendMessage(target, "&7The request will expire in " + REQUEST_EXPIRE_TIME + " seconds.");

        return true;
    }

    /**
     * Handle alliance acceptance
     *
     * @param player Player accepting the alliance
     * @param args Command arguments
     * @return True if handled
     */
    private boolean handleAcceptAlliance(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUsage: /alliance accept <player>");
            return true;
        }

        Player requester = Bukkit.getPlayer(args[1]);
        if (requester == null) {
            MessageUtil.sendMessage(player, "&cPlayer not found: " + args[1]);
            return true;
        }

        // Check if there's a pending request
        if (!allianceRequests.containsKey(requester.getUniqueId()) ||
                !allianceRequests.get(requester.getUniqueId()).equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou don't have a pending alliance request from " + requester.getName() + "!");
            return true;
        }

        // Check if request has expired
        if (requestExpireTimes.containsKey(requester.getUniqueId()) &&
                System.currentTimeMillis() > requestExpireTimes.get(requester.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cThe alliance request from " + requester.getName() + " has expired!");
            allianceRequests.remove(requester.getUniqueId());
            requestExpireTimes.remove(requester.getUniqueId());
            return true;
        }

        // All checks passed, form alliance
        NGNLPlayer ngnlPlayer = plugin.getPlayerManager().getNGNLPlayer(player.getUniqueId());
        NGNLPlayer requesterNGNLPlayer = plugin.getPlayerManager().getNGNLPlayer(requester.getUniqueId());

        ngnlPlayer.setAlliancePartner(requester.getUniqueId());
        requesterNGNLPlayer.setAlliancePartner(player.getUniqueId());

        // Remove request
        allianceRequests.remove(requester.getUniqueId());
        requestExpireTimes.remove(requester.getUniqueId());

        // Notify players
        MessageUtil.sendMessage(player, "&aYou have formed an alliance with " + requester.getName() + "!");
        MessageUtil.sendMessage(requester, "&a" + player.getName() + " has accepted your alliance request!");

        // Broadcast to all players
        plugin.getGameManager().getGame().getScoreboardManager().updateScoreboardsForAllPlayers();
        MessageUtil.broadcast("&6" + player.getName() + " and " + requester.getName() + " have formed an alliance!");

        return true;
    }

    /**
     * Handle breaking an alliance
     *
     * @param player Player breaking the alliance
     * @param ngnlPlayer Player's NGNLPlayer instance
     * @return True if handled
     */
    private boolean handleBreakAlliance(Player player, NGNLPlayer ngnlPlayer) {
        if (!ngnlPlayer.hasAlliancePartner()) {
            MessageUtil.sendMessage(player, "&cYou don't have an alliance to break!");
            return true;
        }

        UUID partnerId = ngnlPlayer.getAlliancePartner();
        Player partner = Bukkit.getPlayer(partnerId);

        // Break alliance
        ngnlPlayer.removeAlliancePartner();

        // Notify partner if online
        if (partner != null) {
            NGNLPlayer partnerNGNLPlayer = plugin.getPlayerManager().getNGNLPlayer(partnerId);
            if (partnerNGNLPlayer != null) {
                partnerNGNLPlayer.removeAlliancePartner();
            }

            MessageUtil.sendMessage(partner, "&c" + player.getName() + " has broken their alliance with you!");
        }

        // Update scoreboards
        plugin.getGameManager().getGame().getScoreboardManager().updateScoreboardsForAllPlayers();

        // Notify player
        MessageUtil.sendMessage(player, "&aYou have broken your alliance!");

        // Broadcast to all players
        String partnerName = partner != null ? partner.getName() : "their partner";
        MessageUtil.broadcast("&6" + player.getName() + " has broken their alliance with " + partnerName + "!");

        return true;
    }

    /**
     * Clean up expired alliance requests
     */
    private void cleanupExpiredRequests() {
        long currentTime = System.currentTimeMillis();

        // Find expired requests
        List<UUID> expiredRequests = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : requestExpireTimes.entrySet()) {
            if (currentTime > entry.getValue()) {
                expiredRequests.add(entry.getKey());
            }
        }

        // Remove expired requests
        for (UUID requesterId : expiredRequests) {
            UUID targetId = allianceRequests.get(requesterId);
            allianceRequests.remove(requesterId);
            requestExpireTimes.remove(requesterId);

            // Notify players if online
            Player requester = Bukkit.getPlayer(requesterId);
            if (requester != null) {
                MessageUtil.sendMessage(requester, "&cYour alliance request has expired!");
            }

            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                MessageUtil.sendMessage(target, "&cThe alliance request from " +
                        (requester != null ? requester.getName() : "a player") + " has expired!");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            // First argument
            completions.add("accept");
            completions.add("break");

            // Add online players
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.equals(player)) {
                    completions.add(onlinePlayer.getName());
                }
            }

            // Filter by input
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(args[0].toLowerCase()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            // Second argument for accept - show players with pending requests
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                UUID playerId = onlinePlayer.getUniqueId();

                if (allianceRequests.containsKey(playerId) &&
                        allianceRequests.get(playerId).equals(player.getUniqueId())) {
                    completions.add(onlinePlayer.getName());
                }
            }

            // Filter by input
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(args[1].toLowerCase()));
        }

        return completions;
    }
}