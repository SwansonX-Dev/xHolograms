package dev.xsuite.holograms.hologram;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class Hologram {

    private final String id;
    private Location location;
    private String worldName;
    private final List<HologramLine> lines;
    private final HologramStyle style;

    public Hologram(@NotNull String id, @NotNull Location location, @NotNull List<HologramLine> lines) {
        this(id, location, lines, new HologramStyle());
    }

    public Hologram(@NotNull String id, @NotNull Location location, @NotNull List<HologramLine> lines, @NotNull HologramStyle style) {
        this.id = normalizeId(id);
        this.location = location.clone();
        this.worldName = location.getWorld() == null ? null : location.getWorld().getName();
        this.lines = new ArrayList<>(lines);
        this.style = style;
    }

    public static @NotNull String normalizeId(@NotNull String id) {
        String normalized = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        if (normalized.isEmpty() || normalized.chars().allMatch(c -> c == '-')) {
            throw new IllegalArgumentException("Hologram id must contain at least one letter, digit, or underscore.");
        }
        return normalized;
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull Location location() {
        return location.clone();
    }

    public void location(@NotNull Location location) {
        this.location = location.clone();
        if (location.getWorld() != null) {
            this.worldName = location.getWorld().getName();
        }
    }

    public @Nullable String worldName() {
        return worldName;
    }

    public void worldName(@NotNull String worldName) {
        this.worldName = worldName;
    }

    public @NotNull List<HologramLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public @NotNull HologramStyle style() {
        return style;
    }

    public void addLine(@NotNull String raw) {
        lines.add(HologramLine.parse(raw));
    }

    public void setLine(int index, @NotNull String raw) {
        lines.set(index, HologramLine.parse(raw));
    }

    public void insertLine(int index, @NotNull String raw) {
        lines.add(index, HologramLine.parse(raw));
    }

    public @NotNull HologramLine removeLine(int index) {
        return lines.remove(index);
    }

    public void replaceLines(@NotNull List<HologramLine> replacement) {
        lines.clear();
        lines.addAll(replacement);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Hologram hologram)) return false;
        return id.equals(hologram.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
