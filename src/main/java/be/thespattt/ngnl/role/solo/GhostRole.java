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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 179 Ghost: receives a mixed list of identities (Riku, Corone, Schwi + a decoy) and, in the finale,
 * turns into an invisible fast ghost that cannot attack. Killing Corone brings her to his side.
 */
public class GhostRole extends Role implements CombatRestrictions {

    private final GhostForm form = new GhostForm();

    /**
     * Constructor
     *
     * @param plugin   Plugin instance
     * @param playerId UUID of the player
     * @param roleType Role type (GHOST)
     */
    public GhostRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        addRoleHolder(names, RoleType.RIKU);
        addRoleHolder(names, RoleType.CORONE);
        addRoleHolder(names, RoleType.SCHWI);
        addDecoyName(names);
        Collections.shuffle(names);
        MessageUtil.sendMessage(player, "&7Identités mélangées (Riku, Corone, Lily, Schwi — tu ne sais pas qui est qui) : &f"
                + String.join("&7, &f", names));
    }

    /**
     * Add the name of the player holding a role to a list.
     *
     * @param names List to fill
     * @param type  Role to look for
     */
    private void addRoleHolder(List<String> names, RoleType type) {
        UUID id = plugin.getRoleManager().getPlayerByRole(type);
        Player holder = id != null ? Bukkit.getPlayer(id) : null;
        if (holder != null) {
            names.add(holder.getName());
        }
    }

    /**
     * Add a random other player as the decoy standing for "Lily".
     *
     * @param names List to fill
     */
    private void addDecoyName(List<String> names) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(playerId) && !names.contains(online.getName())) {
                candidates.add(online);
            }
        }
        if (!candidates.isEmpty()) {
            Collections.shuffle(candidates);
            names.add(candidates.get(0).getName());
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        Player player = getPlayer();
        if (player != null) {
            form.setHidden(player, true);
        }
    }

    @Override
    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        if (killerId == null || !killerId.equals(playerId)) {
            return;
        }
        NGNLPlayer victim = plugin.getPlayerManager().getNGNLPlayer(victimId);
        if (victim == null || victim.getRole() == null || victim.getRole().getRoleType() != RoleType.CORONE) {
            return;
        }
        Player ghost = getPlayer();
        if (ghost == null || !plugin.getGameManager().revivePlayer(victimId, ghost.getLocation(), victim.getMaxHealth())) {
            return;
        }
        linkWithCorone(victimId, victim);
    }

    /**
     * Make the converted Corone an ally and give her the Ghost item.
     *
     * @param coroneId UUID of Corone
     * @param corone   Her player data
     */
    private void linkWithCorone(UUID coroneId, NGNLPlayer corone) {
        NGNLPlayer self = getNGNLPlayer();
        if (self != null) {
            self.setAlliancePartner(coroneId);
            corone.setAlliancePartner(playerId);
        }
        if (corone.getRole() instanceof CoroneRole coroneRole) {
            coroneRole.grantGhostPower();
        }
        MessageUtil.broadcast("&7Corone a rejoint 179 Ghost !");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        giveItem(player, buildGhostItem());
    }

    /**
     * Build the item that toggles the ghost form.
     *
     * @return Ghost item
     */
    private ItemStack buildGhostItem() {
        return buildRoleItem(Material.PHANTOM_MEMBRANE, "&7&lObjet Fantôme",
                "&7Clic droit : redeviens visible / redeviens invisible.",
                "&7Après être redevenu visible, attends 5 minutes pour disparaître de nouveau.");
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        Player player = getPlayer();
        return item != null && item.getType() == Material.PHANTOM_MEMBRANE
                && player != null && isArenaPhaseActive() && form.toggle(player);
    }

    @Override
    public boolean isAttackBlocked() {
        return form.isHidden();
    }

    @Override
    public String attackBlockedMessage() {
        return "&cTu ne peux pas attaquer sous ta forme fantôme.";
    }

    @Override
    public boolean isFallImmune() {
        return form.isHidden();
    }

    /**
     * Whether Ghost is currently hidden.
     *
     * @return True in ghost form
     */
    public boolean isHidden() {
        return form.isHidden();
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are 179 Ghost.",
                "Your goal is to win alone or with an alliance (/alliance).",
                "You receive a mixed list of identities: Riku, Corone, Lily and Schwi."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You are invisible with Speed II and no fall damage, but you cannot attack.",
                "Right-click your Ghost item to become visible (5 minutes before hiding again).",
                "If you kill Corone she joins your team and gets the Ghost item."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
