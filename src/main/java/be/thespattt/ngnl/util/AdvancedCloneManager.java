package be.thespattt.ngnl.util;

import be.thespattt.ngnl.NoGameNoLife;
import com.github.juliarn.npclib.api.Npc;
import com.github.juliarn.npclib.api.Platform;
import com.github.juliarn.npclib.api.profile.Profile;
import com.github.juliarn.npclib.api.protocol.enums.ItemSlot;
import com.github.juliarn.npclib.bukkit.BukkitPlatform;
import com.github.juliarn.npclib.bukkit.BukkitWorldAccessor;
import com.github.juliarn.npclib.bukkit.protocol.BukkitProtocolAdapter;
import com.github.juliarn.npclib.bukkit.util.BukkitPlatformUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AdvancedCloneManager {

    private static final int CLONE_DURATION_SECONDS = 60;
    private static final double WANDER_RADIUS = 8.0;

    private final NoGameNoLife plugin;
    private final Map<UUID, List<CloneNpc>> playerClones = new HashMap<>();
    private final Map<UUID, BukkitRunnable> cloneTasks = new HashMap<>();
    private Platform<World, Player, ItemStack, Plugin> platform;

    public AdvancedCloneManager(NoGameNoLife plugin) {
        this.plugin = plugin;
    }

    public boolean spawnClones(Player player, int amount, int ignoredDurationSeconds) {
        Platform<World, Player, ItemStack, Plugin> npcPlatform = getPlatform();
        if (npcPlatform == null) {
            MessageUtil.sendMessage(player, "&cNPCLib could not start. Install ProtocolLib or PacketEvents if NPC packets fail.");
            return false;
        }

        if (playerClones.containsKey(player.getUniqueId())) {
            removeClones(player.getUniqueId());
        }

        List<CloneNpc> clones = new ArrayList<>();
        Location playerLoc = player.getLocation();

        for (int i = 0; i < amount; i++) {
            double angle = (2 * Math.PI * i) / amount;
            Location cloneLocation = playerLoc.clone().add(2.5 * Math.cos(angle), 0, 2.5 * Math.sin(angle));
            cloneLocation.setYaw((float) Math.toDegrees(angle + Math.PI));
            clones.add(new CloneNpc(cloneLocation, i));
        }

        playerClones.put(player.getUniqueId(), clones);
        clones.forEach(clone -> spawnNpc(player, clone));
        startCloneWanderTask(player, clones);
        return true;
    }

    private Platform<World, Player, ItemStack, Plugin> getPlatform() {
        if (platform != null) {
            return platform;
        }

        try {
            platform = BukkitPlatform.bukkitNpcPlatformBuilder()
                    .extension(plugin)
                    .worldAccessor(BukkitWorldAccessor.nameBasedAccessor())
                    .packetFactory(BukkitProtocolAdapter.packetAdapter())
                    .actionController(builder -> {})
                    .build();
            return platform;
        } catch (Throwable throwable) {
            MessageUtil.logError("Failed to initialize NPCLib platform", throwable);
            return null;
        }
    }

    private void spawnNpc(Player owner, CloneNpc clone) {
        Platform<World, Player, ItemStack, Plugin> npcPlatform = getPlatform();
        if (npcPlatform == null) {
            return;
        }

        CompletableFuture<Npc.Builder<World, Player, ItemStack, Plugin>> future = npcPlatform.newNpcBuilder()
                .position(BukkitPlatformUtil.positionFromBukkitLegacy(clone.location))
                .profile(Profile.unresolved(owner.getUniqueId()));

        future.thenAccept(builder -> Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Npc<World, Player, ItemStack, Plugin> npc = builder
                        .flag(Npc.LOOK_AT_PLAYER, false)
                        .flag(Npc.HIT_WHEN_PLAYER_HITS, false)
                        .flag(Npc.SNEAK_WHEN_PLAYER_SNEAKS, false)
                        .buildAndTrack();

                clone.npc = npc;
                copyPlayerEquipment(owner, npc);
                npc.forceTrackPlayer(owner);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getUniqueId().equals(owner.getUniqueId())) {
                        npc.trackPlayer(online);
                    }
                }
                spawnCloneParticles(clone.location);
            } catch (Throwable throwable) {
                MessageUtil.logError("Failed to spawn NPCLib clone for " + owner.getName(), throwable);
            }
        }));
    }

    private void copyPlayerEquipment(Player owner, Npc<World, Player, ItemStack, Plugin> npc) {
        npc.changeItem(ItemSlot.HEAD, cloneOrAir(owner.getInventory().getHelmet())).scheduleForTracked();
        npc.changeItem(ItemSlot.CHEST, cloneOrAir(owner.getInventory().getChestplate())).scheduleForTracked();
        npc.changeItem(ItemSlot.LEGS, cloneOrAir(owner.getInventory().getLeggings())).scheduleForTracked();
        npc.changeItem(ItemSlot.FEET, cloneOrAir(owner.getInventory().getBoots())).scheduleForTracked();
        npc.changeItem(ItemSlot.MAIN_HAND, cloneOrAir(owner.getInventory().getItemInMainHand())).scheduleForTracked();
        npc.changeItem(ItemSlot.OFF_HAND, cloneOrAir(owner.getInventory().getItemInOffHand())).scheduleForTracked();
    }

    private ItemStack cloneOrAir(ItemStack item) {
        return item != null && item.getType() != Material.AIR ? item.clone() : new ItemStack(Material.AIR);
    }

    private void startCloneWanderTask(Player owner, List<CloneNpc> clones) {
        BukkitRunnable task = new BukkitRunnable() {
            private int ticks = 0;
            private final Random random = new Random();

            @Override
            public void run() {
                if (ticks >= CLONE_DURATION_SECONDS * 20 || !owner.isOnline()) {
                    removeClones(owner.getUniqueId());
                    cancel();
                    return;
                }

                if (ticks % 35 == 0) {
                    for (CloneNpc clone : clones) {
                        if (clone.npc == null) {
                            continue;
                        }
                        moveNpc(owner, clone, random);
                    }
                }

                ticks++;
            }
        };

        task.runTaskTimer(plugin, 0L, 1L);
        cloneTasks.put(owner.getUniqueId(), task);
    }

    private void moveNpc(Player owner, CloneNpc clone, Random random) {
        Location nextLocation = findNearbyTarget(clone.location, random);
        unlinkClone(clone);
        clone.location = nextLocation;
        spawnNpc(owner, clone);
    }

    private Location findNearbyTarget(Location base, Random random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 2.0 + random.nextDouble() * WANDER_RADIUS;
            Location target = base.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            target.setY(target.getWorld().getHighestBlockYAt(target) + 1);
            target.setYaw((float) Math.toDegrees(angle));

            if (target.getBlock().getType().isAir()) {
                return target;
            }
        }
        return base;
    }

    private void unlinkClone(CloneNpc clone) {
        if (clone.npc != null) {
            try {
                clone.npc.unlink();
            } catch (Throwable ignored) {
            }
            clone.npc = null;
        }
    }

    private void spawnCloneParticles(Location location) {
        if (location.getWorld() != null) {
            location.getWorld().spawnParticle(Particle.ENCHANT, location.clone().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
        }
    }

    public void removeClones(UUID playerId) {
        List<CloneNpc> clones = playerClones.remove(playerId);
        if (clones != null) {
            for (CloneNpc clone : clones) {
                spawnCloneParticles(clone.location);
                unlinkClone(clone);
            }
        }

        BukkitRunnable task = cloneTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
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
        List<CloneNpc> clones = playerClones.get(playerId);
        return clones != null && clones.stream().anyMatch(clone -> clone.npc != null);
    }

    public int getCloneCount(UUID playerId) {
        List<CloneNpc> clones = playerClones.get(playerId);
        if (clones == null) return 0;

        int count = 0;
        for (CloneNpc clone : clones) {
            if (clone.npc != null) {
                count++;
            }
        }
        return count;
    }

    public void cleanup() {
        removeAllClones();
    }

    private static final class CloneNpc {
        private Location location;
        private Npc<World, Player, ItemStack, Plugin> npc;

        private CloneNpc(Location location, int ignoredIndex) {
            this.location = location;
        }
    }
}
