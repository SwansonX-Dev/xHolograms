package dev.xsuite.holograms.web;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PinStore {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;
    private static final long TTL_MILLIS = 10L * 60L * 1000L;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pin> pins = new ConcurrentHashMap<>();

    public @NotNull String create(@NotNull Player player) {
        prune();
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Player world is not loaded.");
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = newCode();
            Pin pin = new Pin(
                    player.getUniqueId(),
                    player.getName(),
                    world.getName(),
                    at.getX(),
                    at.getY(),
                    at.getZ(),
                    at.getYaw(),
                    at.getPitch(),
                    System.currentTimeMillis()
            );
            if (pins.putIfAbsent(code, pin) == null) {
                return code;
            }
        }
        throw new IllegalStateException("Could not allocate a unique pin code; try again.");
    }

    public @Nullable Pin consume(@NotNull String code) {
        prune();
        return pins.remove(code.toUpperCase());
    }

    public @Nullable Pin peek(@NotNull String code) {
        prune();
        return pins.get(code.toUpperCase());
    }

    public void prune() {
        long cutoff = System.currentTimeMillis() - TTL_MILLIS;
        Iterator<Map.Entry<String, Pin>> it = pins.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().createdAt() < cutoff) {
                it.remove();
            }
        }
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    public static long ttlMillis() {
        return TTL_MILLIS;
    }

    public record Pin(
            @NotNull UUID playerUuid,
            @NotNull String playerName,
            @NotNull String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            long createdAt
    ) {
    }
}
