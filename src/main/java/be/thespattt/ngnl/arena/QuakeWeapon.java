package be.thespattt.ngnl.arena;

import be.thespattt.ngnl.NoGameNoLife;
import be.thespattt.ngnl.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuakeWeapon implements Listener {

    private final NoGameNoLife plugin;
    private final Map<UUID, Long> lastShotTime = new HashMap<>();
    private final WeaponConfig config;

    public QuakeWeapon(NoGameNoLife plugin) {
        this.plugin = plugin;
        this.config = new WeaponConfig(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public ItemStack createQuakeWeapon() {
        ItemStack weapon = new ItemStack(Material.DIAMOND_HOE);
        ItemMeta meta = weapon.getItemMeta();

        if (meta != null) {
            setupWeaponMeta(meta);
            markAsQuakeWeapon(meta);
            weapon.setItemMeta(meta);
        }

        return weapon;
    }

    private void setupWeaponMeta(ItemMeta meta) {
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Love Gun");
        meta.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "Une arme de précision mortelle",
                ChatColor.GRAY + "Clic droit pour tirer",
                ChatColor.RED + "Dégâts: " + config.getDamage() + " ❤",
                ChatColor.YELLOW + "Portée: " + config.getRange() + " blocs"
        ));
        meta.setUnbreakable(true);
    }

    private void markAsQuakeWeapon(ItemMeta meta) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(new NamespacedKey(plugin, "quake_weapon"), PersistentDataType.BOOLEAN, true);
    }

    public boolean isQuakeWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(new NamespacedKey(plugin, "quake_weapon"), PersistentDataType.BOOLEAN);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isValidQuakeShot(event)) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (isOnCooldown(player)) {
            playFailSound(player);
            MessageUtil.sendMessage(player, "You can only shoot every seconds");
            return;
        }

        executeShot(player);
    }

    private boolean isValidQuakeShot(PlayerInteractEvent event) {
        if (!isQuakeWeapon(event.getItem())) return false;

        return event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isOnCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (lastShotTime.containsKey(playerId)) {
            long timeSinceLastShot = currentTime - lastShotTime.get(playerId);
            return timeSinceLastShot < config.getCooldown();
        }
        return false;
    }

    private void playFailSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.5f);
    }

    private void executeShot(Player player) {
        fireQuakeShot(player);
        lastShotTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void fireQuakeShot(Player shooter) {
        Snowball projectile = createProjectile(shooter);
        playShootEffects(shooter);
        createProjectileTrail(projectile);
    }

    private Snowball createProjectile(Player shooter) {
        Snowball projectile = shooter.launchProjectile(Snowball.class);
        projectile.setVelocity(projectile.getVelocity().multiply(3.0));

        PersistentDataContainer container = projectile.getPersistentDataContainer();
        container.set(new NamespacedKey(plugin, "quake_projectile"),
                PersistentDataType.STRING, shooter.getUniqueId().toString());

        return projectile;
    }

    private void playShootEffects(Player shooter) {
        shooter.playSound(shooter.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 2.0f);

        Location shootLocation = shooter.getEyeLocation();
        shooter.getWorld().spawnParticle(Particle.FLAME, shootLocation, 5, 0.1, 0.1, 0.1, 0.02);
    }

    private void createProjectileTrail(Projectile projectile) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (projectile.isDead() || !projectile.isValid()) {
                    cancel();
                    return;
                }

                spawnTrailParticles(projectile.getLocation());
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnTrailParticles(Location location) {
        location.getWorld().spawnParticle(Particle.HEART, location, 3, 0.1, 0.1, 0.1, 0);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!isQuakeProjectile(event)) return;

        Snowball projectile = (Snowball) event.getEntity();
        Player shooter = getShooter(projectile);
        Location impactLocation = projectile.getLocation();

        playImpactEffects(impactLocation);

        if (event.getHitEntity() instanceof Player) {
            handleDirectHit(shooter, (Player) event.getHitEntity(), impactLocation);
        } else {
            handleAreaDamage(shooter, impactLocation);
        }
    }

    private boolean isQuakeProjectile(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return false;

        Snowball projectile = (Snowball) event.getEntity();
        PersistentDataContainer container = projectile.getPersistentDataContainer();

        return container.has(new NamespacedKey(plugin, "quake_projectile"), PersistentDataType.STRING);
    }

    private Player getShooter(Snowball projectile) {
        PersistentDataContainer container = projectile.getPersistentDataContainer();
        String shooterIdStr = container.get(new NamespacedKey(plugin, "quake_projectile"), PersistentDataType.STRING);

        if (shooterIdStr == null) return null;

        UUID shooterId = UUID.fromString(shooterIdStr);
        return Bukkit.getPlayer(shooterId);
    }

    private void playImpactEffects(Location location) {
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
    }

    private void handleDirectHit(Player shooter, Player target, Location impactLocation) {
        if (shooter != null && target.getUniqueId().equals(shooter.getUniqueId())) return;

        applyQuakeDamage(shooter, target, impactLocation);
    }

    private void handleAreaDamage(Player shooter, Location impactLocation) {
        applyAreaDamage(shooter, impactLocation);
    }

    private void applyQuakeDamage(Player shooter, Player target, Location impactLocation) {
        double finalDamage = calculateDirectDamage(shooter, target);

        target.damage(finalDamage);
        applyKnockback(shooter, target);
        sendDamageMessages(shooter, target, finalDamage);
        spawnDamageParticles(target);
    }

    private double calculateDirectDamage(Player shooter, Player target) {
        double baseDamage = config.getDamage();

        if (shooter != null) {
            double distance = shooter.getLocation().distance(target.getLocation());
            if (distance > 20) {
                return baseDamage * 0.8;
            }
        }

        return baseDamage;
    }

    private void applyKnockback(Player shooter, Player target) {
        if (shooter == null) return;

        Vector direction = target.getLocation().subtract(shooter.getLocation()).toVector().normalize();
        direction.setY(0.3);
        target.setVelocity(direction.multiply(config.getKnockback()));
    }

    private void sendDamageMessages(Player shooter, Player target, double damage) {
        if (shooter != null) {
            MessageUtil.sendMessage(shooter, "&a✓ Touché " + target.getName() +
                    " (&c-" + String.format("%.1f", damage) + " ❤&a)");
        }
        MessageUtil.sendMessage(target, "&cVous avez été touché par " +
                (shooter != null ? shooter.getName() : "quelqu'un") + " !");
    }

    private void spawnDamageParticles(Player target) {
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                target.getLocation().add(0, 1, 0),
                10, 0.5, 0.5, 0.5, 0.1);
    }

    private void applyAreaDamage(Player shooter, Location impactLocation) {
        AreaDamageHandler handler = new AreaDamageHandler(config, shooter, impactLocation);
        handler.execute();
    }

    public void cleanup() {
        lastShotTime.clear();
    }

    private static class WeaponConfig {
        private final double damage;
        private final long cooldown;
        private final double knockback;
        private final int range;

        public WeaponConfig(NoGameNoLife plugin) {
            this.damage = getConfigValue(plugin.getConfigManager().getGameConfig().getQuakeWeaponDamage(), 4.0);
            this.cooldown = getConfigValue(plugin.getConfigManager().getGameConfig().getQuakeWeaponCooldown(), 1000L);
            this.knockback = getConfigValue(plugin.getConfigManager().getGameConfig().getQuakeWeaponKnockback(), 1.5);
            this.range = (int) getConfigValue(plugin.getConfigManager().getGameConfig().getQuakeWeaponRange(), 1000);
        }

        private double getConfigValue(double configValue, double defaultValue) {
            return configValue == 0 ? defaultValue : configValue;
        }

        private long getConfigValue(long configValue, long defaultValue) {
            return configValue == 0 ? defaultValue : configValue;
        }

        public double getDamage() { return damage; }
        public long getCooldown() { return cooldown; }
        public double getKnockback() { return knockback; }
        public int getRange() { return range; }
    }

    private static class AreaDamageHandler {
        private final WeaponConfig config;
        private final Player shooter;
        private final Location impactLocation;
        private final double splashRadius = 3.0;

        public AreaDamageHandler(WeaponConfig config, Player shooter, Location impactLocation) {
            this.config = config;
            this.shooter = shooter;
            this.impactLocation = impactLocation;
        }

        public void execute() {
            double splashDamage = config.getDamage() * 0.5;

            for (Entity entity : getNearbyEntities()) {
                if (!(entity instanceof Player)) continue;

                Player target = (Player) entity;
                if (shouldSkipTarget(target)) continue;

                processTarget(target, splashDamage);
            }
        }

        private java.util.Collection<Entity> getNearbyEntities() {
            return impactLocation.getWorld().getNearbyEntities(impactLocation,
                    splashRadius, splashRadius, splashRadius);
        }

        private boolean shouldSkipTarget(Player target) {
            return shooter != null && target.getUniqueId().equals(shooter.getUniqueId());
        }

        private void processTarget(Player target, double baseSplashDamage) {
            double distance = target.getLocation().distance(impactLocation);
            double damageMultiplier = 1.0 - (distance / splashRadius);
            double finalDamage = baseSplashDamage * damageMultiplier;

            if (finalDamage > 0.5) {
                applyDamageToTarget(target, finalDamage);
            }
        }

        private void applyDamageToTarget(Player target, double damage) {
            target.damage(damage);
            applySplashKnockback(target);
            sendSplashMessages(target, damage);
        }

        private void applySplashKnockback(Player target) {
            Vector direction = target.getLocation().subtract(impactLocation).toVector().normalize();
            direction.setY(0.2);
            target.setVelocity(direction.multiply(config.getKnockback() * 0.5));
        }

        private void sendSplashMessages(Player target, double damage) {
            if (shooter != null) {
                MessageUtil.sendMessage(shooter, "&6◉ Splash sur " + target.getName() +
                        " (&c-" + String.format("%.1f", damage) + " ❤&6)");
            }
            MessageUtil.sendMessage(target, "&6Vous avez pris des dégâts de zone !");
        }
    }
}