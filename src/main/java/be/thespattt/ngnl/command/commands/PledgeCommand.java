package be.thespattt.ngnl.command.commands;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Command for creating binding pledges between players
 */
public class PledgeCommand implements CommandExecutor, TabCompleter {

    private final NoGameNoLife plugin;

    // Map to track pledge requests (requester UUID -> target UUID)
    private final Map<UUID, UUID> pledgeRequests = new HashMap<>();
    // Map to track pledge terms (requester UUID -> terms)
    private final Map<UUID, String> pledgeTerms = new HashMap<>();
    // Map to track request expiration times
    private final Map<UUID, Long> requestExpireTimes = new HashMap<>();
    // Map to track active pledges (pledgeId -> PledgeInfo)
    private final Map<String, PledgeInfo> activePledges = new HashMap<>();
    // Request expiration time in seconds
    private static final int REQUEST_EXPIRE_TIME = 120;

    /**
     * Constructor
     *
     * @param plugin Plugin instance
     */
    public PledgeCommand(NoGameNoLife plugin) {
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

        // Check if pledges are active
        if (!plugin.getGameManager().getGame().arePledgesActive()) {
            MessageUtil.sendMessage(player, "&cPledges are only available during the mining phase!");
            return true;
        }

        // Command syntax
        if (args.length == 0) {
            MessageUtil.sendMessage(player, "&cUsage: /pledge <player> <terms>");
            MessageUtil.sendMessage(player, "&7Use '/pledge list' to see your active pledges");
            MessageUtil.sendMessage(player, "&7Use '/pledge accept <player>' to accept a pledge");
            MessageUtil.sendMessage(player, "&7Use '/pledge info <id>' to see details of a pledge");
            return true;
        }

        // Handle subcommands
        if (args[0].equalsIgnoreCase("list")) {
            return handleListPledges(player);
        } else if (args[0].equalsIgnoreCase("accept")) {
            return handleAcceptPledge(player, args);
        } else if (args[0].equalsIgnoreCase("info")) {
            return handlePledgeInfo(player, args);
        }

        // Handle pledge creation
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            MessageUtil.sendMessage(player, "&cPlayer not found: " + args[0]);
            return true;
        }

        // Can't pledge with yourself
        if (target.equals(player)) {
            MessageUtil.sendMessage(player, "&cYou cannot create a pledge with yourself!");
            return true;
        }

        // Check if target is alive
        if (!plugin.getGameManager().isPlayerAlive(target.getUniqueId())) {
            MessageUtil.sendMessage(player, "&c" + target.getName() + " is not alive in the current game!");
            return true;
        }

        // Get pledge terms
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cYou must specify the terms of the pledge!");
            return true;
        }

        StringBuilder termsBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            termsBuilder.append(args[i]).append(" ");
        }
        String terms = termsBuilder.toString().trim();

        // Validate terms
        if (terms.length() < 3) {
            MessageUtil.sendMessage(player, "&cPledge terms must be at least 3 characters long!");
            return true;
        }

        if (terms.length() > 100) {
            MessageUtil.sendMessage(player, "&cPledge terms must be at most 100 characters long!");
            return true;
        }

        // Send pledge request
        pledgeRequests.put(player.getUniqueId(), target.getUniqueId());
        pledgeTerms.put(player.getUniqueId(), terms);
        requestExpireTimes.put(player.getUniqueId(), System.currentTimeMillis() + (REQUEST_EXPIRE_TIME * 1000));

        MessageUtil.sendMessage(player, "&aPledge request sent to " + target.getName() + ":");
        MessageUtil.sendMessage(player, "&7Terms: &f" + terms);
        MessageUtil.sendMessage(player, "&7The request will expire in " + REQUEST_EXPIRE_TIME + " seconds.");

        MessageUtil.sendMessage(target, "&6" + player.getName() + " has proposed a pledge:");
        MessageUtil.sendMessage(target, "&7Terms: &f" + terms);
        MessageUtil.sendMessage(target, "&6Type '/pledge accept " + player.getName() + "' to accept.");
        MessageUtil.sendMessage(target, "&c&lWARNING: &cPledges are binding under the 10 Pledges!");
        MessageUtil.sendMessage(target, "&7The request will expire in " + REQUEST_EXPIRE_TIME + " seconds.");

        return true;
    }

    /**
     * Handle the 'list' subcommand
     *
     * @param player Player requesting the list
     * @return True if handled
     */
    private boolean handleListPledges(Player player) {
        List<String> playerPledges = new ArrayList<>();

        // Find pledges involving the player
        for (Map.Entry<String, PledgeInfo> entry : activePledges.entrySet()) {
            PledgeInfo pledgeInfo = entry.getValue();

            if (pledgeInfo.player1Id.equals(player.getUniqueId()) ||
                    pledgeInfo.player2Id.equals(player.getUniqueId())) {
                playerPledges.add(entry.getKey());
            }
        }

        if (playerPledges.isEmpty()) {
            MessageUtil.sendMessage(player, "&7You have no active pledges.");
            return true;
        }

        MessageUtil.sendMessage(player, "&6Your active pledges:");

        for (String pledgeId : playerPledges) {
            PledgeInfo pledgeInfo = activePledges.get(pledgeId);
            String player1Name = Bukkit.getPlayer(pledgeInfo.player1Id) != null ?
                    Bukkit.getPlayer(pledgeInfo.player1Id).getName() : "Unknown";
            String player2Name = Bukkit.getPlayer(pledgeInfo.player2Id) != null ?
                    Bukkit.getPlayer(pledgeInfo.player2Id).getName() : "Unknown";

            MessageUtil.sendMessage(player, "&7[" + pledgeId + "] &f" + player1Name + " ⟷ " + player2Name);
            MessageUtil.sendMessage(player, "&7Terms: &f" + pledgeInfo.terms);
            MessageUtil.sendMessage(player, "");
        }

        return true;
    }

    /**
     * Handle the 'accept' subcommand
     *
     * @param player Player accepting the pledge
     * @param args Command arguments
     * @return True if handled
     */
    private boolean handleAcceptPledge(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUsage: /pledge accept <player>");
            return true;
        }

        Player requester = Bukkit.getPlayer(args[1]);
        if (requester == null) {
            MessageUtil.sendMessage(player, "&cPlayer not found: " + args[1]);
            return true;
        }

        // Check if there's a pending pledge
        if (!pledgeRequests.containsKey(requester.getUniqueId()) ||
                !pledgeRequests.get(requester.getUniqueId()).equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou don't have a pending pledge request from " + requester.getName() + "!");
            return true;
        }

        // Check if pledge has expired
        if (requestExpireTimes.containsKey(requester.getUniqueId()) &&
                System.currentTimeMillis() > requestExpireTimes.get(requester.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cThe pledge request from " + requester.getName() + " has expired!");
            pledgeRequests.remove(requester.getUniqueId());
            pledgeTerms.remove(requester.getUniqueId());
            requestExpireTimes.remove(requester.getUniqueId());
            return true;
        }

        // All checks passed, create pledge
        String terms = pledgeTerms.get(requester.getUniqueId());
        String pledgeId = createPledgeId();

        PledgeInfo pledgeInfo = new PledgeInfo(
                requester.getUniqueId(),
                player.getUniqueId(),
                terms,
                System.currentTimeMillis()
        );

        activePledges.put(pledgeId, pledgeInfo);

        // Remove request
        pledgeRequests.remove(requester.getUniqueId());
        pledgeTerms.remove(requester.getUniqueId());
        requestExpireTimes.remove(requester.getUniqueId());

        // Notify players
        MessageUtil.sendMessage(player, "&aYou have accepted the pledge from " + requester.getName() + "!");
        MessageUtil.sendMessage(player, "&7Terms: &f" + terms);
        MessageUtil.sendMessage(player, "&7Pledge ID: &f" + pledgeId);

        MessageUtil.sendMessage(requester, "&a" + player.getName() + " has accepted your pledge!");
        MessageUtil.sendMessage(requester, "&7Terms: &f" + terms);
        MessageUtil.sendMessage(requester, "&7Pledge ID: &f" + pledgeId);

        // Broadcast pledge creation
        MessageUtil.broadcast("&6A pledge has been formed between " + requester.getName() +
                " and " + player.getName() + "!");

        return true;
    }

    /**
     * Handle the 'info' subcommand
     *
     * @param player Player requesting pledge info
     * @param args Command arguments
     * @return True if handled
     */
    private boolean handlePledgeInfo(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUsage: /pledge info <id>");
            return true;
        }

        String pledgeId = args[1];

        if (!activePledges.containsKey(pledgeId)) {
            MessageUtil.sendMessage(player, "&cPledge not found: " + pledgeId);
            return true;
        }

        PledgeInfo pledgeInfo = activePledges.get(pledgeId);

        // Check if player is involved in the pledge
        if (!pledgeInfo.player1Id.equals(player.getUniqueId()) &&
                !pledgeInfo.player2Id.equals(player.getUniqueId())) {
            MessageUtil.sendMessage(player, "&cYou are not involved in this pledge!");
            return true;
        }

        // Display pledge info
        String player1Name = Bukkit.getPlayer(pledgeInfo.player1Id) != null ?
                Bukkit.getPlayer(pledgeInfo.player1Id).getName() : "Unknown";
        String player2Name = Bukkit.getPlayer(pledgeInfo.player2Id) != null ?
                Bukkit.getPlayer(pledgeInfo.player2Id).getName() : "Unknown";

        MessageUtil.sendMessage(player, "&6=== Pledge: " + pledgeId + " ===");
        MessageUtil.sendMessage(player, "&7Participants: &f" + player1Name + " ⟷ " + player2Name);
        MessageUtil.sendMessage(player, "&7Terms: &f" + pledgeInfo.terms);
        MessageUtil.sendMessage(player, "&7Created: &f" + new Date(pledgeInfo.creationTime));

        return true;
    }

    /**
     * Create a unique pledge ID
     *
     * @return Unique pledge ID
     */
    private String createPledgeId() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder idBuilder = new StringBuilder();
        Random random = new Random();

        // Create a 6-character ID
        for (int i = 0; i < 6; i++) {
            idBuilder.append(characters.charAt(random.nextInt(characters.length())));
        }

        String id = idBuilder.toString();

        // Ensure ID is unique
        if (activePledges.containsKey(id)) {
            return createPledgeId();
        }

        return id;
    }

    /**
     * Clean up expired pledge requests
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
            UUID targetId = pledgeRequests.get(requesterId);
            pledgeRequests.remove(requesterId);
            pledgeTerms.remove(requesterId);
            requestExpireTimes.remove(requesterId);

            // Notify players if online
            Player requester = Bukkit.getPlayer(requesterId);
            if (requester != null) {
                MessageUtil.sendMessage(requester, "&cYour pledge request has expired!");
            }

            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                MessageUtil.sendMessage(target, "&cThe pledge request from " +
                        (requester != null ? requester.getName() : "a player") + " has expired!");
            }
        }
    }

    /**
     * Check if a pledge exists between two players
     *
     * @param player1Id UUID of first player
     * @param player2Id UUID of second player
     * @return True if a pledge exists
     */
    public boolean hasPledge(UUID player1Id, UUID player2Id) {
        for (PledgeInfo pledgeInfo : activePledges.values()) {
            if ((pledgeInfo.player1Id.equals(player1Id) && pledgeInfo.player2Id.equals(player2Id)) ||
                    (pledgeInfo.player1Id.equals(player2Id) && pledgeInfo.player2Id.equals(player1Id))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get pledge information between two players
     *
     * @param player1Id UUID of first player
     * @param player2Id UUID of second player
     * @return PledgeInfo or null if no pledge exists
     */
    public PledgeInfo getPledge(UUID player1Id, UUID player2Id) {
        for (PledgeInfo pledgeInfo : activePledges.values()) {
            if ((pledgeInfo.player1Id.equals(player1Id) && pledgeInfo.player2Id.equals(player2Id)) ||
                    (pledgeInfo.player1Id.equals(player2Id) && pledgeInfo.player2Id.equals(player1Id))) {
                return pledgeInfo;
            }
        }

        return null;
    }

    /**
     * Clear all pledges (e.g., when arena phase starts)
     */
    public void clearAllPledges() {
        activePledges.clear();
        pledgeRequests.clear();
        pledgeTerms.clear();
        requestExpireTimes.clear();

        MessageUtil.broadcast("&c&lAll pledges have been dissolved! The arena phase has begun!");
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
            completions.add("list");
            completions.add("info");

            // Add online players
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.equals(player)) {
                    completions.add(onlinePlayer.getName());
                }
            }

            // Filter by input
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(args[0].toLowerCase()));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("accept")) {
                // Second argument for accept - show players with pending requests
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    UUID playerId = onlinePlayer.getUniqueId();

                    if (pledgeRequests.containsKey(playerId) &&
                            pledgeRequests.get(playerId).equals(player.getUniqueId())) {
                        completions.add(onlinePlayer.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("info")) {
                // Second argument for info - pledge IDs
                for (String pledgeId : activePledges.keySet()) {
                    PledgeInfo pledgeInfo = activePledges.get(pledgeId);

                    if (pledgeInfo.player1Id.equals(player.getUniqueId()) ||
                            pledgeInfo.player2Id.equals(player.getUniqueId())) {
                        completions.add(pledgeId);
                    }
                }
            }

            // Filter by input
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(args[1].toLowerCase()));
        }

        return completions;
    }

    /**
     * Class to store pledge information
     */
    public static class PledgeInfo {
        public final UUID player1Id;
        public final UUID player2Id;
        public final String terms;
        public final long creationTime;

        /**
         * Constructor
         *
         * @param player1Id UUID of first player
         * @param player2Id UUID of second player
         * @param terms Pledge terms
         * @param creationTime Creation time in milliseconds
         */
        public PledgeInfo(UUID player1Id, UUID player2Id, String terms, long creationTime) {
            this.player1Id = player1Id;
            this.player2Id = player2Id;
            this.terms = terms;
            this.creationTime = creationTime;
        }
    }
}