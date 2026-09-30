package be.thespattt.ngnl.role.duo;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.minigame.MiniGameType;
import be.thespattt.ngnl.player.NGNLPlayer;
import be.thespattt.ngnl.role.RoleType;
import be.thespattt.ngnl.util.ItemBuilder;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class NonnaRole extends DuoRole {

    private static final int ORACLE_COOLDOWN = 20 * 60;
    private boolean canChooseCamp = false;
    private int rikuWeaknessTaskId = -1;

    public NonnaRole(NoGameNoLife plugin, UUID playerId, RoleType roleType) {
        super(plugin, playerId, roleType);
    }

    @Override
    protected void onRoleSetup() {
        // Nonna learns Ivan through the three-name list sent with the role.
    }

    @Override
    public void onMiniGameStart(UUID opponent, MiniGameType miniGameType, boolean isWinner) {
        if (miniGameType == MiniGameType.SUMO) {
            Player player = getPlayer();
            if (player != null) {
                MessageUtil.sendMessage(player, "&eYou receive your Knockback stick for this Sumo round.");
            }
        }
    }

    @Override
    public void onArenaPhaseStart() {
        super.onArenaPhaseStart();
        resetCooldown("oracle_card");
    }

    @Override
    public void onPartnerDeath(UUID partnerId, UUID killerId) {
        Player player = getPlayer();
        if (player == null || !partnerId.equals(getPartnerUUID())) {
            return;
        }
        canChooseCamp = true;
        MessageUtil.sendMessage(player, "&eUse &a/duo joincorone &eor &a/duo joinriku&e to choose a new camp.");
    }

    @Override
    protected void giveArenaPhaseItems(Player player) {
        ItemStack card = new ItemBuilder(Material.PAPER)
                .name("&5&lNonna's Oracle Card")
                .lore("&7Randomly teleports a non-combat player.", "&c20% chance to teleport you instead.")
                .glow(true)
                .setTag("role_item", "NONNA")
                .build();
        player.getInventory().addItem(card);
    }

    @Override
    public boolean onItemUse(ItemStack item) {
        if (item != null && item.getType() == Material.PAPER) {
            return useOracleCard();
        }
        return false;
    }

    private boolean useOracleCard() {
        Player player = getPlayer();
        if (player == null || !isArenaPhaseActive()) {
            return false;
        }

        List<Player> candidates = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            boolean valid = !other.getUniqueId().equals(playerId)
                    && plugin.getGameManager().isPlayerAlive(other.getUniqueId())
                    && !plugin.getCombatTracker().isInCombat(other.getUniqueId());
            if (valid) {
                candidates.add(other);
            }
        }
        if (candidates.isEmpty()) {
            MessageUtil.sendMessage(player, "&cAucune cible valide (hors combat).");
            return false;
        }
        if (!tryUseCooldown("oracle_card", ORACLE_COOLDOWN)) {
            return false;
        }

        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        boolean mishap = ThreadLocalRandom.current().nextInt(100) < 20;
        (mishap ? player : target).teleport(randomSpotInBorder(player));
        MessageUtil.broadcast("&5Nonna a utilisé une Oracle Card.");
        if (mishap) {
            MessageUtil.sendMessage(player, "&cL'Oracle Card s'est retournée contre toi !");
        }
        return true;
    }

    /**
     * Pick a random safe spot inside the world border of the player's world.
     *
     * @param player Player whose world is used
     * @return A random location on the surface
     */
    private Location randomSpotInBorder(Player player) {
        org.bukkit.World world = player.getWorld();
        Location center = world.getWorldBorder().getCenter();
        double radius = world.getWorldBorder().getSize() / 2.0 * 0.8;
        int x = (int) (center.getX() + ThreadLocalRandom.current().nextDouble(-radius, radius));
        int z = (int) (center.getZ() + ThreadLocalRandom.current().nextDouble(-radius, radius));
        return world.getHighestBlockAt(x, z).getLocation().add(0.5, 1, 0.5);
    }

    public void joinCoroneCamp() {
        Player player = getPlayer();
        if (player == null || !canChooseCamp) {
            return;
        }
        UUID coroneId = plugin.getRoleManager().getPlayerByRole(RoleType.CORONE);
        if (coroneId == null) {
            MessageUtil.sendMessage(player, "&cCorone is not in the game.");
            return;
        }

        NGNLPlayer self = plugin.getPlayerManager().getNGNLPlayer(playerId);
        NGNLPlayer corone = plugin.getPlayerManager().getNGNLPlayer(coroneId);
        if (self != null && corone != null) {
            self.setAlliancePartner(coroneId);
            corone.setAlliancePartner(playerId);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        Player coronePlayer = Bukkit.getPlayer(coroneId);
        if (coronePlayer != null) {
            coronePlayer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }
        canChooseCamp = false;
        MessageUtil.sendMessage(player, "&aYou joined Corone's camp and both of you gained Speed I.");
    }

    public void joinRikuCamp() {
        Player player = getPlayer();
        if (player == null || !canChooseCamp) {
            return;
        }
        UUID rikuId = plugin.getRoleManager().getPlayerByRole(RoleType.RIKU);
        if (rikuId == null) {
            MessageUtil.sendMessage(player, "&cRiku is not in the game.");
            return;
        }

        NGNLPlayer self = plugin.getPlayerManager().getNGNLPlayer(playerId);
        NGNLPlayer riku = plugin.getPlayerManager().getNGNLPlayer(rikuId);
        if (self != null && riku != null) {
            self.setAlliancePartner(rikuId);
            riku.setAlliancePartner(playerId);
        }
        canChooseCamp = false;
        startRikuWeaknessTask(rikuId);
        MessageUtil.sendMessage(player, "&eYou joined Riku and Schwi's camp.");
    }

    private void startRikuWeaknessTask(UUID rikuId) {
        if (rikuWeaknessTaskId != -1) {
            Bukkit.getScheduler().cancelTask(rikuWeaknessTaskId);
        }

        rikuWeaknessTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player player = getPlayer();
            Player rikuPlayer = Bukkit.getPlayer(rikuId);
            if (player == null || rikuPlayer == null || !player.getWorld().equals(rikuPlayer.getWorld())) {
                return;
            }

            if (player.getLocation().distance(rikuPlayer.getLocation()) <= 10.0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false));
                rikuPlayer.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false));
            }
        }, 20L, 20L);
    }

    @Override
    public void onDeath(UUID killerId) {
        super.onDeath(killerId);
        if (rikuWeaknessTaskId != -1) {
            Bukkit.getScheduler().cancelTask(rikuWeaknessTaskId);
            rikuWeaknessTaskId = -1;
        }
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
                "You are Nonna Zell.",
                "Your goal is to win with Ivan.",
                "In Sumo, you receive a Knockback stick every round."
        );
    }

    @Override
    public List<String> getArenaPhaseDescription() {
        return Arrays.asList(
                "You receive an Oracle Card in finale.",
                "After Ivan's death, you may join Corone or Riku/Schwi."
        );
    }

    @Override
    public String getObjective() {
        return "Win the game with Ivan.";
    }
}
