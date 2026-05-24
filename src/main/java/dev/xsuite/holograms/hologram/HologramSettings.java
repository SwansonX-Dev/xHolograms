package dev.xsuite.holograms.hologram;

import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

public record HologramSettings(
        double lineHeight,
        int updateIntervalTicks,
        float viewRange,
        boolean shadowed,
        boolean seeThrough,
        boolean defaultBackground,
        Color backgroundColor,
        int lineWidth,
        Display.Billboard billboard,
        TextDisplay.TextAlignment alignment,
        Display.Brightness brightness
) {

    public static @NotNull HologramSettings from(@NotNull FileConfiguration config) {
        int block = clampLight(config.getInt("settings.brightness.block", 15));
        int sky = clampLight(config.getInt("settings.brightness.sky", 15));
        return new HologramSettings(
                config.getDouble("settings.line-height", 0.28D),
                Math.max(1, config.getInt("settings.update-interval-ticks", 20)),
                (float) config.getDouble("settings.view-range", 64.0D),
                config.getBoolean("settings.shadowed", false),
                config.getBoolean("settings.see-through", false),
                config.getBoolean("settings.default-background", false),
                Color.fromARGB(config.getInt("settings.background-argb", 0)),
                Math.max(1, config.getInt("settings.line-width", 220)),
                parseEnum(Display.Billboard.class, config.getString("settings.billboard"), Display.Billboard.CENTER),
                parseEnum(TextDisplay.TextAlignment.class, config.getString("settings.alignment"), TextDisplay.TextAlignment.CENTER),
                new Display.Brightness(block, sky)
        );
    }

    private static int clampLight(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private static <E extends Enum<E>> @NotNull E parseEnum(@NotNull Class<E> type, String input, @NotNull E fallback) {
        if (input == null || input.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, input.strip().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
