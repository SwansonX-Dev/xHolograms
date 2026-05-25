package dev.xsuite.holograms.hologram;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public record HologramLine(@NotNull String raw, @NotNull List<String> frames) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[a-z_]+}");

    public static @NotNull HologramLine parse(@NotNull String raw) {
        List<String> frames = Arrays.stream(raw.split("\\|\\|", -1))
                .map(String::strip)
                .map(frame -> frame.isEmpty() ? " " : frame)
                .toList();
        return new HologramLine(raw, frames.isEmpty() ? List.of(" ") : frames);
    }

    public @NotNull String frame(int tick) {
        return frames.get(Math.floorMod(tick, frames.size()));
    }

    public boolean animated() {
        return frames.size() > 1;
    }

    public boolean dynamic() {
        if (animated()) return true;
        return frames.stream().anyMatch(frame -> PLACEHOLDER.matcher(frame).find());
    }
}
