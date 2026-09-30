package be.thespattt.ngnl.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * Builds and displays action-bar arrows pointing from a player toward another player.
 */
public final class DirectionArrow {

    private DirectionArrow() {
    }

    /**
     * Show an arrow (with the name and the distance of the target) in the viewer's action bar.
     *
     * @param viewer      Player who sees the arrow
     * @param target      Player the arrow points to (null if unavailable)
     * @param label       Label used when the target is unavailable
     * @param closeRange  Distance (blocks) under which the arrow is green
     */
    public static void show(Player viewer, Player target, String label, double closeRange) {
        String text = buildText(viewer, target, label, closeRange);
        viewer.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }

    /**
     * Build the legacy-formatted action-bar text.
     *
     * @param viewer     Player who sees the arrow
     * @param target     Player the arrow points to (null if unavailable)
     * @param label      Label used when the target is unavailable
     * @param closeRange Distance (blocks) under which the arrow is green
     * @return Formatted text
     */
    public static String buildText(Player viewer, Player target, String label, double closeRange) {
        if (target == null) {
            return "§c" + label + " n'est plus disponible";
        }
        if (!viewer.getWorld().equals(target.getWorld())) {
            return "§e" + target.getName() + " est dans un autre monde";
        }

        double dx = target.getLocation().getX() - viewer.getLocation().getX();
        double dz = target.getLocation().getZ() - viewer.getLocation().getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double relative = normalizeAngle(Math.toDegrees(Math.atan2(-dx, dz)) - viewer.getLocation().getYaw());
        String color = distance <= closeRange ? "§a" : "§c";
        return color + arrowFor(relative) + " §f" + target.getName() + " §7(" + String.format("%.1f", distance) + "m)";
    }

    /**
     * Normalize an angle to the [-180, 180] range.
     *
     * @param angle Angle in degrees
     * @return Normalized angle
     */
    private static double normalizeAngle(double angle) {
        double result = angle;
        while (result > 180) result -= 360;
        while (result < -180) result += 360;
        return result;
    }

    /**
     * Pick the arrow character matching a relative angle.
     *
     * @param angle Angle relative to where the viewer looks (-180..180)
     * @return Arrow character
     */
    private static String arrowFor(double angle) {
        if (angle >= -22.5 && angle < 22.5) return "↑";
        if (angle >= 22.5 && angle < 67.5) return "↗";
        if (angle >= 67.5 && angle < 112.5) return "→";
        if (angle >= 112.5 && angle < 157.5) return "↘";
        if (angle >= 157.5 || angle < -157.5) return "↓";
        if (angle >= -157.5 && angle < -112.5) return "↙";
        if (angle >= -112.5 && angle < -67.5) return "←";
        return "↖";
    }
}
