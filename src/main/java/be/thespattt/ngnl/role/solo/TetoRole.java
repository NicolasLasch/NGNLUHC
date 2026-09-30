package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.CombatRestrictions;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Teto: learns the role of one player of his choice at the start, always controls the mini-game
 * selection, and in the finale owns the King's Piece (10 s of invincibility) and Royal Recall
 * (revives any eliminated player as an ally).
 */
public class TetoRole extends Role implements CombatRestrictions {

    /** Cooldown of the King's Piece in seconds. */
    private static final int KING_COOLDOWN = 10 * 60;
    /** Invincibility duration in seconds. */
    private static final int KING_SECONDS = 10;
    /** Health (HP) paid to activate the King's Piece (2 hearts). */
    private static final double KING_HEALTH_COST = 4.0;
    /** Health (HP) of a player revived by Royal Recall. */
    private static final double REVIVE_HEALTH = 10.0;

    private long attackLockUntil = 0L;
    private boolean knowledgeUsed = false;
    private boolean reviveUsed = false;

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (TETO)
     */
    public TetoRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        giveItem(player, buildRoleItem(Material.ENDER_EYE, "&6&lŒil de Teto",
                "&7Clic droit : choisis un joueur dont tu découvres le rôle", "&7(une seule fois)."));
        openKnowledgePicker();
    }

    /**
     * Let Teto choose the player whose role he learns.
     */
    private void openKnowledgePicker() {
        Player player = getPlayer();
        if (player == null || knowledgeUsed) {
            return;
        }
        MessageUtil.sendMessage(player, "&eChoisis le joueur dont tu veux connaître le rôle (ton Œil de Teto te permet de rouvrir ce menu).");
        plugin.getPlayerPicker().open(player, "Œil de Teto - quel rôle connaître ?",
                plugin.getPlayerPicker().aliveCandidates(player), this::revealRoleOf);
    }

    /**
     * Reveal the role of the chosen player.
     *
     * @param targetId UUID of the chosen player
     */
    private void revealRoleOf(UUID targetId) {
        Player player = getPlayer();
        NGNLPlayer target = plugin.getPlayerManager().getNGNLPlayer(targetId);
        if (player == null || knowledgeUsed || target == null || target.getRole() == null) {
            return;
        }
        knowledgeUsed = true;
        Player online = Bukkit.getPlayer(targetId);
        String name = online != null ? online.getName() : targetId.toString();
        MessageUtil.sendMessage(player, "&eTu découvres le rôle de &a" + name + "&e : &f" + target.getRole().getDisplayName());
    }

    /**
     * Teto always chooses the mini-game, whether he won the PvP or not.
     *
     * @return Always true
     */
    public boolean canChooseMiniGame() {
        return true;
    }

    // ------------------------------------------------------------------ finale items

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildRoleItem(Material.NETHER_STAR, "&6&lRoi d'échec",
                "&710 secondes d'invincibilité, mais ta position est révélée.",
                "&7Tu ne peux pas attaquer pendant ce temps et tu perds 2 cœurs.", "",
                "&eClic droit pour activer", "&cRecharge : 10 minutes"));
        giveItem(player, buildRoleItem(Material.TOTEM_OF_UNDYING, "&e&lRappel royal",
                "&7Ressuscite le joueur éliminé de ton choix : il devient ton allié.", "",
                "&eClic droit pour activer", "&cUne seule utilisation"));
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item == null) {
            return false;
        }
        switch (item.getType()) {
            case ENDER_EYE:
                openKnowledgePicker();
                return true;
            case NETHER_STAR:
                return useKingPiece();
            case TOTEM_OF_UNDYING:
                return openRoyalRecallPicker();
            default:
                return false;
        }
    }

    /**
     * Become invincible for 10 seconds at the cost of 2 hearts (kills Teto if he has too few).
     *
     * @return True if the King's Piece was used
     */
    private boolean useKingPiece() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive() || !tryUseCooldown("king_piece", KING_COOLDOWN)) {
            return false;
        }
        if (player.getHealth() <= KING_HEALTH_COST) {
            MessageUtil.sendMessage(player, "&4Le Roi d'échec t'a coûté trop cher...");
            player.setHealth(0.0);
            return true;
        }
        player.setHealth(player.getHealth() - KING_HEALTH_COST);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, KING_SECONDS * 20, 10, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, KING_SECONDS * 20, 0, false, false));
        attackLockUntil = System.currentTimeMillis() + KING_SECONDS * 1000L;
        MessageUtil.broadcast("&6Teto révèle sa position avec le Roi d'échec : "
                + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ());
        return true;
    }

    /**
     * Let Teto choose which eliminated player to bring back.
     *
     * @return True if the picker was opened
     */
    private boolean openRoyalRecallPicker() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        if (reviveUsed) {
            MessageUtil.sendMessage(player, "&cLe Rappel royal a déjà été utilisé.");
            return false;
        }
        List<UUID> dead = plugin.getGameManager().getGame().getEliminatedPlayers();
        if (dead.isEmpty()) {
            MessageUtil.sendMessage(player, "&cAucun joueur éliminé à ressusciter.");
            return false;
        }
        plugin.getPlayerPicker().open(player, "Rappel royal - qui ressusciter ?", dead, this::reviveAsAlly);
        return true;
    }

    /**
     * Revive the chosen player as Teto's ally (works like /alliance).
     *
     * @param deadId UUID of the eliminated player
     */
    private void reviveAsAlly(UUID deadId) {
        Player player = getPlayer();
        if (player == null || reviveUsed || !plugin.getGameManager().revivePlayer(deadId, player.getLocation(), REVIVE_HEALTH)) {
            return;
        }
        reviveUsed = true;
        NGNLPlayer self = getNGNLPlayer();
        NGNLPlayer ally = plugin.getPlayerManager().getNGNLPlayer(deadId);
        if (self != null && ally != null) {
            self.setAlliancePartner(deadId);
            ally.setAlliancePartner(playerId);
        }
        Player revived = Bukkit.getPlayer(deadId);
        MessageUtil.broadcast("&eTeto a ressuscité " + (revived != null ? revived.getName() : "un joueur") + " comme allié !");
    }

    @Override
    public boolean isAttackBlocked() {
        return System.currentTimeMillis() < attackLockUntil;
    }

    @Override
    public String attackBlockedMessage() {
        return "&cLe Roi d'échec t'empêche d'attaquer pour le moment.";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Teto.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You learn the role of one player of your choice at the start.",
                "You always choose the mini-game, whether you won the PvP or not."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "King's Piece: 10 seconds of invincibility (position revealed, no attacks,",
                "costs 2 hearts, every 10 minutes).",
                "Royal Recall: revive any eliminated player as your ally (once)."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
