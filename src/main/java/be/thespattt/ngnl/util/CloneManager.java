package be.thespattt.ngnl.util;

import be.thespattt.ngnl.NoGameNoLife;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class CloneManager {

    private final NoGameNoLife plugin;
    private final Map<UUID, List<ArmorStand>> playerClones = new HashMap<>();
    private final Map<UUID, BukkitRunnable> cloneTasks = new HashMap<>();

    public CloneManager(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    public boolean spawnClones(Player player, int amount, int durationSeconds) {
        if (playerClones.containsKey(player.getUniqueId())) {
            removeClones(player.getUniqueId());
        }

        List<ArmorStand> clones = new ArrayList<>();
        Location playerLoc = player.getLocation();

        for (int i = 0; i < amount; i++) {
            double angle = (2 * Math.PI * i) / amount;
            double radius = 2.0;

            double x = playerLoc.getX() + (radius * Math.cos(angle));
            double z = playerLoc.getZ() + (radius * Math.sin(angle));
            double y = playerLoc.getY();

            Location cloneLocation = new Location(playerLoc.getWorld(), x, y, z);
            cloneLocation.setYaw((float) Math.toDegrees(angle + Math.PI));

            ArmorStand clone = createClone(player, cloneLocation);
            if (clone != null) {
                clones.add(clone);
            }
        }

        if (clones.isEmpty()) {
            return false;
        }

        playerClones.put(player.getUniqueId(), clones);

        BukkitRunnable animationTask = new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = durationSeconds * 20;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    removeClones(player.getUniqueId());
                    this.cancel();
                    return;
                }

                animateClones(clones, player, ticks);
                ticks++;
            }
        };

        animationTask.runTaskTimer(plugin, 0L, 1L);
        cloneTasks.put(player.getUniqueId(), animationTask);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeClones(player.getUniqueId());
        }, durationSeconds * 20L);

        return true;
    }

    private ArmorStand createClone(Player player, Location location) {
        try {
            ArmorStand clone = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);

            clone.setVisible(true);
            clone.setBasePlate(false);
            clone.setArms(true);
            clone.setGravity(false);
            clone.setInvulnerable(true);
            clone.setCanPickupItems(false);
            clone.setRemoveWhenFarAway(false);
            clone.setCustomName("§e" + player.getName() + "'s Clone");
            clone.setCustomNameVisible(true);

            copyPlayerAppearance(player, clone);

            return clone;
        } catch (Exception e) {
            MessageUtil.logError("Failed to create clone for " + player.getName(), e);
            return null;
        }
    }

    private void copyPlayerAppearance(Player player, ArmorStand clone) {
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack leggings = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (helmet != null) {
            clone.getEquipment().setHelmet(helmet.clone());
        }
        if (chestplate != null) {
            clone.getEquipment().setChestplate(chestplate.clone());
        }
        if (leggings != null) {
            clone.getEquipment().setLeggings(leggings.clone());
        }
        if (boots != null) {
            clone.getEquipment().setBoots(boots.clone());
        }
        if (mainHand != null) {
            clone.getEquipment().setItemInMainHand(mainHand.clone());
        }
        if (offHand != null) {
            clone.getEquipment().setItemInOffHand(offHand.clone());
        }

        clone.getEquipment().setHelmetDropChance(0.0f);
        clone.getEquipment().setChestplateDropChance(0.0f);
        clone.getEquipment().setLeggingsDropChance(0.0f);
        clone.getEquipment().setBootsDropChance(0.0f);
        clone.getEquipment().setItemInMainHandDropChance(0.0f);
        clone.getEquipment().setItemInOffHandDropChance(0.0f);
    }

    private void animateClones(List<ArmorStand> clones, Player player, int ticks) {
        if (!player.isOnline()) {
            return;
        }

        Location playerLoc = player.getLocation();

        for (int i = 0; i < clones.size(); i++) {
            ArmorStand clone = clones.get(i);
            if (clone == null || clone.isDead()) {
                continue;
            }

            double time = ticks * 0.05;
            double baseAngle = (2 * Math.PI * i) / clones.size();
            double angle = baseAngle + time;
            double radius = 2.0 + Math.sin(time * 2) * 0.5;

            double x = playerLoc.getX() + (radius * Math.cos(angle));
            double z = playerLoc.getZ() + (radius * Math.sin(angle));
            double y = playerLoc.getY() + Math.sin(time * 3 + i) * 0.3;

            Location newLoc = new Location(playerLoc.getWorld(), x, y, z);
            newLoc.setYaw((float) Math.toDegrees(angle + Math.PI));
            newLoc.setPitch(playerLoc.getPitch());

            clone.teleport(newLoc);

            if (ticks % 20 == 0) {
                copyPlayerAppearance(player, clone);
            }
        }
    }

    public void removeClones(UUID playerId) {
        List<ArmorStand> clones = playerClones.remove(playerId);
        if (clones != null) {
            for (ArmorStand clone : clones) {
                if (clone != null && !clone.isDead()) {
                    clone.remove();
                }
            }
        }

        BukkitRunnable task = cloneTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public void removeAllClones() {
        Set<UUID> playerIds = new HashSet<>(playerClones.keySet());
        for (UUID playerId : playerIds) {
            removeClones(playerId);
        }
    }

    public boolean hasClones(UUID playerId) {
        return playerClones.containsKey(playerId);
    }

    public int getCloneCount(UUID playerId) {
        List<ArmorStand> clones = playerClones.get(playerId);
        return clones != null ? clones.size() : 0;
    }
}