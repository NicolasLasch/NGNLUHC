package be.thespattt.ngnl.util;

import be.thespattt.ngnl.NoGameNoLife;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AdvancedCloneManager {

    private final NoGameNoLife plugin;
    private final Map<UUID, List<ArmorStand>> playerClones = new HashMap<>();
    private final Map<UUID, BukkitRunnable> cloneTasks = new HashMap<>();

    public AdvancedCloneManager(NoGameNoLife plugin) {
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
            double radius = 2.5;

            double x = playerLoc.getX() + (radius * Math.cos(angle));
            double z = playerLoc.getZ() + (radius * Math.sin(angle));
            double y = playerLoc.getY();

            Location cloneLocation = new Location(playerLoc.getWorld(), x, y, z);
            cloneLocation.setYaw((float) Math.toDegrees(angle + Math.PI));

            ArmorStand clone = createPlayerClone(player, cloneLocation, i);
            if (clone != null) {
                clones.add(clone);
            }
        }

        if (clones.isEmpty()) {
            return false;
        }

        playerClones.put(player.getUniqueId(), clones);

        startCloneAnimation(player, clones, durationSeconds);

        return true;
    }

    private ArmorStand createPlayerClone(Player player, Location location, int cloneIndex) {
        try {
            ArmorStand clone = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);

            setupArmorStandProperties(clone, player, cloneIndex);
            copyPlayerAppearance(player, clone);
            setPlayerHead(clone, player);

            return clone;
        } catch (Exception e) {
            MessageUtil.logError("Failed to create clone " + cloneIndex + " for " + player.getName(), e);
            return null;
        }
    }

    private void setupArmorStandProperties(ArmorStand clone, Player player, int cloneIndex) {
        clone.setVisible(true);
        clone.setBasePlate(false);
        clone.setArms(true);
        clone.setGravity(false);
        clone.setInvulnerable(true);
        clone.setCanPickupItems(false);
        clone.setRemoveWhenFarAway(false);
        clone.setMarker(false);

        String cloneName = "§6" + player.getName() + " §7Clone #" + (cloneIndex + 1);
        clone.setCustomName(cloneName);
        clone.setCustomNameVisible(true);
    }

    private void setPlayerHead(ArmorStand clone, Player player) {
        try {
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();

            if (skullMeta != null) {
                skullMeta.setOwningPlayer(player);
                skullMeta.setDisplayName("§6" + player.getName() + "'s Head");
                playerHead.setItemMeta(skullMeta);

                clone.getEquipment().setHelmet(playerHead);
                clone.getEquipment().setHelmetDropChance(0.0f);
            }
        } catch (Exception e) {
            MessageUtil.logError("Failed to set player head for clone of " + player.getName(), e);

            ItemStack currentHelmet = player.getInventory().getHelmet();
            if (currentHelmet != null) {
                clone.getEquipment().setHelmet(currentHelmet.clone());
                clone.getEquipment().setHelmetDropChance(0.0f);
            }
        }
    }

    private void copyPlayerAppearance(Player player, ArmorStand clone) {
        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack leggings = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (chestplate != null) {
            clone.getEquipment().setChestplate(chestplate.clone());
            clone.getEquipment().setChestplateDropChance(0.0f);
        }
        if (leggings != null) {
            clone.getEquipment().setLeggings(leggings.clone());
            clone.getEquipment().setLeggingsDropChance(0.0f);
        }
        if (boots != null) {
            clone.getEquipment().setBoots(boots.clone());
            clone.getEquipment().setBootsDropChance(0.0f);
        }
        if (mainHand != null && mainHand.getType() != Material.AIR) {
            clone.getEquipment().setItemInMainHand(mainHand.clone());
            clone.getEquipment().setItemInMainHandDropChance(0.0f);
        }
        if (offHand != null && offHand.getType() != Material.AIR) {
            clone.getEquipment().setItemInOffHand(offHand.clone());
            clone.getEquipment().setItemInOffHandDropChance(0.0f);
        }
    }

    private void startCloneAnimation(Player player, List<ArmorStand> clones, int durationSeconds) {
        BukkitRunnable animationTask = new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = durationSeconds * 20;
            private final Random random = new Random();

            @Override
            public void run() {
                if (ticks >= maxTicks || !player.isOnline()) {
                    removeClones(player.getUniqueId());
                    this.cancel();
                    return;
                }

                animateClones(clones, player, ticks, random);
                ticks++;
            }
        };

        animationTask.runTaskTimer(plugin, 0L, 1L);
        cloneTasks.put(player.getUniqueId(), animationTask);
    }

    private void animateClones(List<ArmorStand> clones, Player player, int ticks, Random random) {
        Location playerLoc = player.getLocation();

        for (int i = 0; i < clones.size(); i++) {
            ArmorStand clone = clones.get(i);
            if (clone == null || clone.isDead()) {
                continue;
            }

            double time = ticks * 0.03;
            double baseAngle = (2 * Math.PI * i) / clones.size();
            double angle = baseAngle + time;

            double radiusVariation = Math.sin(time * 2 + i) * 0.3;
            double radius = 2.5 + radiusVariation;

            double x = playerLoc.getX() + (radius * Math.cos(angle));
            double z = playerLoc.getZ() + (radius * Math.sin(angle));
            double y = playerLoc.getY() + Math.sin(time * 3 + i) * 0.2;

            Location newLoc = new Location(playerLoc.getWorld(), x, y, z);

            double lookAngle = Math.atan2(z - playerLoc.getZ(), x - playerLoc.getX());
            newLoc.setYaw((float) Math.toDegrees(lookAngle + Math.PI));
            newLoc.setPitch(playerLoc.getPitch() + (float)(Math.sin(time * 4 + i) * 10));

            clone.teleport(newLoc);

            if (ticks % 40 == 0) {
                updateCloneEquipment(player, clone);
            }

            if (ticks % 60 == i * 10) {
                createCloneEffect(clone, random);
            }
        }
    }

    private void updateCloneEquipment(Player player, ArmorStand clone) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (mainHand != null && mainHand.getType() != Material.AIR) {
            clone.getEquipment().setItemInMainHand(mainHand.clone());
        } else {
            clone.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
        }

        if (offHand != null && offHand.getType() != Material.AIR) {
            clone.getEquipment().setItemInOffHand(offHand.clone());
        } else {
            clone.getEquipment().setItemInOffHand(new ItemStack(Material.AIR));
        }
    }

    private void createCloneEffect(ArmorStand clone, Random random) {
        Location effectLoc = clone.getLocation().add(0, 1, 0);

        try {
            clone.getWorld().spawnParticle(
                    Particle.ENCHANT,
                    effectLoc,
                    5,
                    0.2, 0.2, 0.2,
                    0.1
            );
        } catch (Exception e) {
        }
    }

    public void removeClones(UUID playerId) {
        List<ArmorStand> clones = playerClones.remove(playerId);
        if (clones != null) {
            for (ArmorStand clone : clones) {
                if (clone != null && !clone.isDead()) {
                    createRemovalEffect(clone);
                    clone.remove();
                }
            }
        }

        BukkitRunnable task = cloneTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void createRemovalEffect(ArmorStand clone) {
        try {
            Location effectLoc = clone.getLocation().add(0, 1, 0);
            clone.getWorld().spawnParticle(
                    org.bukkit.Particle.CLOUD,
                    effectLoc,
                    10,
                    0.5, 0.5, 0.5,
                    0.1
            );
        } catch (Exception e) {
            // Effect failed, continue
        }
    }

    public void removeAllClones() {
        Set<UUID> playerIds = new HashSet<>(playerClones.keySet());
        for (UUID playerId : playerIds) {
            removeClones(playerId);
        }
    }

    public boolean hasClones(UUID playerId) {
        List<ArmorStand> clones = playerClones.get(playerId);
        return clones != null && !clones.isEmpty();
    }

    public int getCloneCount(UUID playerId) {
        List<ArmorStand> clones = playerClones.get(playerId);
        if (clones == null) return 0;

        int count = 0;
        for (ArmorStand clone : clones) {
            if (clone != null && !clone.isDead()) {
                count++;
            }
        }
        return count;
    }

    public void cleanup() {
        removeAllClones();
    }
}