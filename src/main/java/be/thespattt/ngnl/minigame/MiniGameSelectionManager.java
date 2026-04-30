package be.thespattt.ngnl.minigame;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.role.duo.StephanieRole;
import be.thespattt.ngnl.role.solo.TetoRole;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class MiniGameSelectionManager {

    private final NoGameNoLife plugin;
    private final Map<UUID, SelectionContext> activeSelections = new HashMap<>();

    public MiniGameSelectionManager(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    public void handleMiniGameSelection(Player winner, Player loser) {
        UUID winnerId = winner.getUniqueId();
        UUID loserId = loser.getUniqueId();

        NGNLPlayer winnerNGNL = plugin.getPlayerManager().getNGNLPlayer(winnerId);
        if (winnerNGNL == null || winnerNGNL.getRole() == null) {
            openNormalSelection(winner, loser);
            return;
        }

        Role winnerRole = winnerNGNL.getRole();

        // Sora must decide substitution before the PvP winner selects the mini-game.
        NGNLPlayer loserNGNL = plugin.getPlayerManager().getNGNLPlayer(loserId);
        if (loserNGNL != null && loserNGNL.getRole() != null &&
                loserNGNL.getRole().getRoleType() == RoleType.SORA) {
            offerSoraSubstitution(loser, winner, winner);
            return;
        }

        if (winnerRole.getRoleType() == RoleType.STEPHANIE) {
            StephanieRole stephanie = (StephanieRole) winnerRole;
            if (!stephanie.hasUsedMiniGameChoice()) {
                openStephanieSelection(winner, loser);
                return;
            }
        }

        if (winnerRole.getRoleType() == RoleType.TETO && winnerRole instanceof TetoRole tetoRole && tetoRole.canChooseMiniGame()) {
            openTetoSelection(winner, loser);
            return;
        }

        // Check for Sora's substitution ability
        if (winnerRole.getRoleType() == RoleType.SORA) {
            offerSoraSubstitution(winner, loser, winner);
            return;
        }

        if (loserNGNL != null && loserNGNL.getRole() instanceof TetoRole tetoRole && tetoRole.canChooseMiniGame()) {
            openTetoSelection(loser, winner);
            return;
        }

        // Default selection
        openNormalSelection(winner, loser);
    }

    private void openStephanieSelection(Player stephanie, Player opponent) {
        SelectionContext context = new SelectionContext(stephanie, opponent, SelectionType.STEPHANIE_CHOICE);
        activeSelections.put(stephanie.getUniqueId(), context);

        MessageUtil.sendMessage(stephanie, "&6You can choose the mini-game! (30 seconds)");
        MessageUtil.sendMessage(stephanie, "&eUse the inventory, or &a/duo choosegame <type>&e.");
        MessageUtil.sendMessage(opponent, "&6" + stephanie.getName() + " is choosing the mini-game...");

        // Open normal GUI but mark it as Stephanie-controlled
        plugin.getMiniGameManager().openMiniGameSelectionGUI(stephanie, opponent);

        // Set timeout
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeSelections.containsKey(stephanie.getUniqueId())) {
                MessageUtil.sendMessage(stephanie, "&cTime's up! Normal selection will proceed.");
                activeSelections.remove(stephanie.getUniqueId());
            }
        }, 600L); // 30 seconds
    }

    private void offerSoraSubstitution(Player sora, Player opponent, Player selectionController) {
        UUID shiroId = sora.getUniqueId(); // This needs to get actual Shiro ID
        NGNLPlayer soraNGNL = plugin.getPlayerManager().getNGNLPlayer(sora.getUniqueId());
        if (soraNGNL.getRole() != null) {
            shiroId = soraNGNL.getRole().getPartnerUUID();
        }

        if (shiroId == null) {
            openPostSoraSelection(selectionController, sora);
            return;
        }

        Player shiro = Bukkit.getPlayer(shiroId);
        if (shiro == null) {
            MessageUtil.sendMessage(sora, "&cShiro is not online for substitution.");
            openPostSoraSelection(selectionController, sora);
            return;
        }

        SelectionContext context = new SelectionContext(sora, opponent, SelectionType.SORA_SUBSTITUTION, selectionController);
        context.setShiro(shiro);
        activeSelections.put(sora.getUniqueId(), context);

        MessageUtil.sendMessage(sora, "&6You can ask Shiro to substitute! (30 seconds)");
        MessageUtil.sendMessage(sora, "&eUse the inventory, or &a/substitute &e/ &a/duo substitute&e.");
        MessageUtil.sendMessage(opponent, "&6" + sora.getName() + " might substitute with Shiro...");

        // Open normal GUI
        plugin.getMiniGameManager().openMiniGameSelectionGUI(sora, opponent);

        // Set timeout
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeSelections.containsKey(sora.getUniqueId())) {
                MessageUtil.sendMessage(sora, "&cTime's up! You will play yourself.");
                activeSelections.remove(sora.getUniqueId());
            }
        }, 600L); // 30 seconds
    }

    private void openNormalSelection(Player player1, Player player2) {
        plugin.getMiniGameManager().openMiniGameSelectionGUI(player1, player2);
    }

    private void openTetoSelection(Player teto, Player opponent) {
        SelectionContext context = new SelectionContext(teto, opponent, SelectionType.TETO_CHOICE);
        activeSelections.put(teto.getUniqueId(), context);
        MessageUtil.sendMessage(teto, "&6You control this mini-game selection as Teto.");
        MessageUtil.sendMessage(opponent, "&6Teto is choosing the mini-game.");
        plugin.getMiniGameManager().openMiniGameSelectionGUI(teto, opponent);
    }

    private void openPostSoraSelection(Player selectionController, Player soraParticipant) {
        if (selectionController == null || soraParticipant == null) {
            return;
        }

        NGNLPlayer controllerNGNL = plugin.getPlayerManager().getNGNLPlayer(selectionController.getUniqueId());
        Role controllerRole = controllerNGNL != null ? controllerNGNL.getRole() : null;

        if (controllerRole != null && controllerRole.getRoleType() == RoleType.STEPHANIE
                && controllerRole instanceof StephanieRole stephanieRole
                && !stephanieRole.hasUsedMiniGameChoice()) {
            openStephanieSelection(selectionController, soraParticipant);
            return;
        }

        if (controllerRole instanceof TetoRole tetoRole && tetoRole.canChooseMiniGame()) {
            openTetoSelection(selectionController, soraParticipant);
            return;
        }

        openNormalSelection(selectionController, soraParticipant);
    }

    public boolean processStephanieChoice(Player stephanie, MiniGameType gameType) {
        SelectionContext context = activeSelections.get(stephanie.getUniqueId());
        if (context == null || context.type != SelectionType.STEPHANIE_CHOICE) {
            return false;
        }

        NGNLPlayer stephNGNL = plugin.getPlayerManager().getNGNLPlayer(stephanie.getUniqueId());
        if (stephNGNL == null || !(stephNGNL.getRole() instanceof StephanieRole)) {
            return false;
        }

        StephanieRole stephanieRole = (StephanieRole) stephNGNL.getRole();
        if (!stephanieRole.chooseMiniGame(gameType)) {
            return false;
        }

        // Clean up and start game
        activeSelections.remove(stephanie.getUniqueId());
        plugin.getMiniGameSessionManager().clearPending(stephanie.getUniqueId());
        stephanie.closeInventory();
        if (context.opponent != null) {
            context.opponent.closeInventory();
        }

        MessageUtil.sendMessage(stephanie, "&aStarting chosen mini-game: &e" + gameType.getDisplayName());
        MessageUtil.sendMessage(context.opponent, "&6Starting mini-game: &e" + gameType.getDisplayName());

        plugin.getMiniGameEngine().startGame(gameType, stephanie, context.opponent);
        return true;
    }

    public boolean processTetoChoice(Player teto, MiniGameType gameType) {
        SelectionContext context = activeSelections.get(teto.getUniqueId());
        if (context == null || context.type != SelectionType.TETO_CHOICE) {
            return false;
        }

        activeSelections.remove(teto.getUniqueId());
        plugin.getMiniGameSessionManager().clearPending(teto.getUniqueId());
        teto.closeInventory();
        if (context.opponent != null) {
            context.opponent.closeInventory();
        }

        MessageUtil.sendMessage(teto, "&aStarting chosen mini-game: &e" + gameType.getDisplayName());
        MessageUtil.sendMessage(context.opponent, "&6Starting mini-game: &e" + gameType.getDisplayName());
        plugin.getMiniGameEngine().startGame(gameType, teto, context.opponent);
        return true;
    }

    public boolean processSoraSubstitution(Player sora) {
        SelectionContext context = activeSelections.get(sora.getUniqueId());
        if (context == null || context.type != SelectionType.SORA_SUBSTITUTION || context.shiro == null) {
            return false;
        }

        MessageUtil.sendMessage(context.shiro, "&6" + sora.getName() + " wants you to substitute! (15 seconds)");
        MessageUtil.sendMessage(context.shiro, "&eUse &a/duo acceptsub &eto accept");
        MessageUtil.sendMessage(sora, "&aSent substitution request to Shiro!");

        // Store for Shiro to accept
        context.waitingForShiroResponse = true;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeSelections.containsKey(sora.getUniqueId()) && context.waitingForShiroResponse) {
                MessageUtil.sendMessage(sora, "&cShiro didn't respond. You will play yourself.");
                MessageUtil.sendMessage(context.shiro, "&cSubstitution request timed out.");
                activeSelections.remove(sora.getUniqueId());
                if (plugin.getMiniGameManager().getMiniGameSelector() != null) {
                    plugin.getMiniGameManager().getMiniGameSelector().clearSelectionForController(sora.getUniqueId());
                }
                openPostSoraSelection(context.selectionController, context.controller);
            }
        }, 300L); // 15 seconds

        return true;
    }

    public Player processSoraSubstitutionFromGui(Player sora) {
        SelectionContext context = activeSelections.get(sora.getUniqueId());
        if (context == null || context.type != SelectionType.SORA_SUBSTITUTION || context.shiro == null) {
            return null;
        }

        context.waitingForShiroResponse = true;
        MessageUtil.sendMessage(sora, "&aSubstitution request sent to Shiro.");
        MessageUtil.sendMessage(context.shiro, "&6" + sora.getName() + " wants you to substitute.");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeSelections.containsKey(sora.getUniqueId()) && context.waitingForShiroResponse) {
                MessageUtil.sendMessage(sora, "&cShiro didn't respond. You will play yourself.");
                MessageUtil.sendMessage(context.shiro, "&cSubstitution request timed out.");
                activeSelections.remove(sora.getUniqueId());
                if (plugin.getMiniGameManager().getMiniGameSelector() != null) {
                    plugin.getMiniGameManager().getMiniGameSelector().clearSelectionForController(sora.getUniqueId());
                }
                openPostSoraSelection(context.selectionController, context.controller);
            }
        }, 300L);

        return context.shiro;
    }

    public boolean processSoraDecline(Player sora) {
        SelectionContext context = activeSelections.get(sora.getUniqueId());
        if (context != null && context.type == SelectionType.SORA_SUBSTITUTION) {
            UUID soraId = sora.getUniqueId();
            activeSelections.remove(soraId);
            if (plugin.getMiniGameManager().getMiniGameSelector() != null) {
                plugin.getMiniGameManager().getMiniGameSelector().clearSelectionForController(soraId);
            }
            openPostSoraSelection(context.selectionController, context.controller);
            return true;
        }
        return false;
    }

    public boolean processShiroAccept(Player shiro) {
        SelectionContext context = findContextForShiro(shiro.getUniqueId());
        if (context == null || !context.waitingForShiroResponse) {
            return false;
        }

        // Clean up and start substituted game
        UUID soraId = context.controller.getUniqueId();
        activeSelections.remove(soraId);

        context.controller.closeInventory();
        if (context.opponent != null) {
            context.opponent.closeInventory();
        }

        MessageUtil.sendMessage(context.controller, "&aShiro accepted the substitution!");
        MessageUtil.sendMessage(shiro, "&aYou will play instead of " + context.controller.getName() + "!");
        MessageUtil.sendMessage(context.opponent, "&6" + shiro.getName() + " will play instead!");
        swapSoraAndShiroPositions(context.controller, shiro);

        if (plugin.getMiniGameManager().getMiniGameSelector() != null) {
            plugin.getMiniGameManager().getMiniGameSelector().clearSelectionForController(soraId);
        }

        plugin.getMiniGameSessionManager().clearPending(context.controller.getUniqueId());
        if (context.selectionController.getUniqueId().equals(context.controller.getUniqueId())) {
            openPostSoraSelection(shiro, context.opponent);
        } else {
            openPostSoraSelection(context.selectionController, shiro);
        }
        return true;
    }

    private void swapSoraAndShiroPositions(Player sora, Player shiro) {
        Location soraSelectionLocation = sora.getLocation().clone();
        Location shiroOriginalLocation = shiro.getLocation().clone();

        NGNLPlayer soraNGNL = plugin.getPlayerManager().getNGNLPlayer(sora.getUniqueId());
        NGNLPlayer shiroNGNL = plugin.getPlayerManager().getNGNLPlayer(shiro.getUniqueId());
        Location soraReturnLocation = soraNGNL != null && soraNGNL.getLastLocation() != null
                ? soraNGNL.getLastLocation().clone()
                : soraSelectionLocation.clone();

        sora.teleport(shiroOriginalLocation);
        shiro.teleport(soraSelectionLocation);

        if (soraNGNL != null) {
            soraNGNL.setLastLocation(shiroOriginalLocation);
        }
        if (shiroNGNL != null) {
            shiroNGNL.setLastLocation(soraReturnLocation);
        }
    }

    public boolean processShiroDecline(Player shiro) {
        SelectionContext context = findContextForShiro(shiro.getUniqueId());
        if (context == null || !context.waitingForShiroResponse) {
            return false;
        }

        UUID soraId = context.controller.getUniqueId();
        context.waitingForShiroResponse = false;
        activeSelections.remove(soraId);
        if (plugin.getMiniGameManager().getMiniGameSelector() != null) {
            plugin.getMiniGameManager().getMiniGameSelector().clearSelectionForController(soraId);
        }

        MessageUtil.sendMessage(context.controller, "&cShiro declined. Continue the roulette yourself.");
        MessageUtil.sendMessage(shiro, "&eYou declined Sora's substitution.");
        openPostSoraSelection(context.selectionController, context.controller);
        return true;
    }

    private SelectionContext findContextForShiro(UUID shiroId) {
        for (SelectionContext context : activeSelections.values()) {
            if (context.shiro != null && context.shiro.getUniqueId().equals(shiroId)) {
                return context;
            }
        }
        return null;
    }

    public void cancelSelection(UUID playerId) {
        activeSelections.remove(playerId);
    }

    public boolean hasActiveSelection(UUID playerId) {
        return activeSelections.containsKey(playerId);
    }

    public boolean isSoraSubstitutionSelection(UUID playerId) {
        SelectionContext context = activeSelections.get(playerId);
        return context != null && context.type == SelectionType.SORA_SUBSTITUTION && !context.waitingForShiroResponse;
    }

    public UUID getSoraControllerForShiro(UUID shiroId) {
        SelectionContext context = findContextForShiro(shiroId);
        return context != null ? context.controller.getUniqueId() : null;
    }

    public void cleanup() {
        activeSelections.clear();
    }

    // Helper classes
    public enum SelectionType {
        NORMAL,
        STEPHANIE_CHOICE,
        SORA_SUBSTITUTION,
        TETO_CHOICE
    }

    private static class SelectionContext {
        final Player controller;
        final Player opponent;
        final SelectionType type;
        final Player selectionController;
        Player shiro;
        boolean waitingForShiroResponse = false;

        SelectionContext(Player controller, Player opponent, SelectionType type) {
            this(controller, opponent, type, controller);
        }

        SelectionContext(Player controller, Player opponent, SelectionType type, Player selectionController) {
            this.controller = controller;
            this.opponent = opponent;
            this.type = type;
            this.selectionController = selectionController;
        }

        void setShiro(Player shiro) {
            this.shiro = shiro;
        }
    }
}
