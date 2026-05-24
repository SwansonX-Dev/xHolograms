package dev.xsuite.holograms.hologram;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class Hologram {

    private final String id;
    private Location location;
    private final List<HologramLine> lines;

    public Hologram(@NotNull String id, @NotNull Location location, @NotNull List<HologramLine> lines) {
        this.id = normalizeId(id);
        this.location = location.clone();
        this.lines = new ArrayList<>(lines);
    }

    public static @NotNull String normalizeId(@NotNull String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull Location location() {
        return location.clone();
    }

    public void location(@NotNull Location location) {
        this.location = location.clone();
    }

    public @NotNull List<HologramLine> lines() {
        return Collections.unmodifiableList(lines);
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
