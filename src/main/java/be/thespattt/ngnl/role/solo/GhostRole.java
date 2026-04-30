package be.thespattt.ngnl.role.solo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.Role;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class GhostRole extends Role {

    private static final int REHIDE_COOLDOWN = 5 * 60;
    private boolean hidden = false;
    private long visibleSince = 0L;

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
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(playerId) && !names.contains(online.getName())) {
                names.add(online.getName());
                break;
            }
        }
        Collections.shuffle(names);
        MessageUtil.sendMessage(player, "&7Mixed identities: &f" + String.join("&7, &f", names));
    }

    private void addRoleHolder(List<String> names, RoleType type) {
        UUID id = plugin.getRoleManager().getPlayerByRole(type);
        Player player = id != null ? Bukkit.getPlayer(id) : null;
        if (player != null) {
            names.add(player.getName());
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        setHidden(true);
    }

    public void onAnyPlayerEliminated(UUID victimId, UUID killerId) {
        if (killerId == null || !killerId.equals(playerId)) {
            return;
        }
        var victim = plugin.getPlayerManager().getNGNLPlayer(victimId);
        if (victim == null || victim.getRole() == null || victim.getRole().getRoleType() != RoleType.CORONE) {
            return;
        }
        NGNLPlayer self = getNGNLPlayer();
        NGNLPlayer corone = plugin.getPlayerManager().getNGNLPlayer(victimId);
        if (self != null && corone != null) {
            self.setAlliancePartner(victimId);
            corone.setAlliancePartner(playerId);
        }
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack phantom = new ItemBuilder(Material.PHANTOM_MEMBRANE)
                .name("&7&lGhost Toggle")
                .lore("&7Switch between visible and invisible form.")
                .glow(true)
                .setTag("role_item", "GHOST")
                .build();
        player.getInventory().addItem(phantom);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        return item != null && item.getType() == Material.PHANTOM_MEMBRANE && toggleGhost();
    }

    private boolean toggleGhost() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }
        if (hidden) {
            setHidden(false);
            visibleSince = System.currentTimeMillis() / 1000;
            MessageUtil.sendMessage(player, "&eYou became visible.");
            return true;
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - visibleSince < REHIDE_COOLDOWN) {
            MessageUtil.sendMessage(player, "&cYou must wait before hiding again.");
            return false;
        }
        setHidden(true);
        return true;
    }

    private void setHidden(boolean hidden) {
        this.hidden = hidden;
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        if (hidden) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
            MessageUtil.sendMessage(player, "&7Ghost form active.");
        } else {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
    }

    public boolean isHidden() {
        return hidden;
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are 179 Ghost.",
                "Your goal is to win alone or with an alliance.",
                "You know a mixed list containing Riku, Corone and Schwi.",
                "You become invisible in finale and can toggle your form."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "Hidden form grants invisibility, Speed II and no-fall style mobility.",
                "You cannot attack while hidden."
        );
    }

    @Override
    public String getObjective() {
        return "Win alone or with your alliance.";
    }
}
