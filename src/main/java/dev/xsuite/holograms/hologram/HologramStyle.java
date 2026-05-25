package dev.xsuite.holograms.hologram;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HologramStyle {

    private Double lineHeight;
    private Float scale;
    private Float viewRange;
    private Boolean shadowed;
    private Boolean seeThrough;
    private Boolean defaultBackground;
    private Color backgroundColor;
    private Integer lineWidth;
    private Display.Billboard billboard;
    private TextDisplay.TextAlignment alignment;

    public double lineHeight(@NotNull HologramSettings defaults) {
        return lineHeight == null ? defaults.lineHeight() : lineHeight;
    }

    public float scale() {
        return scale == null ? 1.0F : scale;
    }

    public float viewRange(@NotNull HologramSettings defaults) {
        return viewRange == null ? defaults.viewRange() : viewRange;
    }

    public boolean shadowed(@NotNull HologramSettings defaults) {
        return shadowed == null ? defaults.shadowed() : shadowed;
    }

    public boolean seeThrough(@NotNull HologramSettings defaults) {
        return seeThrough == null ? defaults.seeThrough() : seeThrough;
    }

    public boolean defaultBackground(@NotNull HologramSettings defaults) {
        return defaultBackground == null ? defaults.defaultBackground() : defaultBackground;
    }

    public @NotNull Color backgroundColor(@NotNull HologramSettings defaults) {
        return backgroundColor == null ? defaults.backgroundColor() : backgroundColor;
    }

    public int lineWidth(@NotNull HologramSettings defaults) {
        return lineWidth == null ? defaults.lineWidth() : lineWidth;
    }

    public @NotNull Display.Billboard billboard(@NotNull HologramSettings defaults) {
        return billboard == null ? defaults.billboard() : billboard;
    }

    public @NotNull TextDisplay.TextAlignment alignment(@NotNull HologramSettings defaults) {
        return alignment == null ? defaults.alignment() : alignment;
    }

    public @Nullable Double lineHeightOverride() {
        return lineHeight;
    }

    public @Nullable Float scaleOverride() {
        return scale;
    }

    public @Nullable Float viewRangeOverride() {
        return viewRange;
    }

    public @Nullable Boolean shadowedOverride() {
        return shadowed;
    }

    public @Nullable Boolean seeThroughOverride() {
        return seeThrough;
    }

    public @Nullable Boolean defaultBackgroundOverride() {
        return defaultBackground;
    }

    public @Nullable Color backgroundColorOverride() {
        return backgroundColor;
    }

    public @Nullable Integer lineWidthOverride() {
        return lineWidth;
    }

    public @Nullable Display.Billboard billboardOverride() {
        return billboard;
    }

    public @Nullable TextDisplay.TextAlignment alignmentOverride() {
        return alignment;
    }

    public void lineHeight(double lineHeight) {
        this.lineHeight = clamp(lineHeight, 0.05D, 2.0D);
    }

    public void scale(float scale) {
        this.scale = (float) clamp(scale, 0.25D, 4.0D);
    }

    public void viewRange(float viewRange) {
        this.viewRange = (float) clamp(viewRange, 1.0D, 256.0D);
    }

    public void shadowed(boolean shadowed) {
        this.shadowed = shadowed;
    }

    public void seeThrough(boolean seeThrough) {
        this.seeThrough = seeThrough;
    }

    public void defaultBackground(boolean defaultBackground) {
        this.defaultBackground = defaultBackground;
    }

    public void backgroundColor(@NotNull Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void lineWidth(int lineWidth) {
        this.lineWidth = Math.max(20, Math.min(1000, lineWidth));
    }

    public void billboard(@NotNull Display.Billboard billboard) {
        this.billboard = billboard;
    }

    public void alignment(@NotNull TextDisplay.TextAlignment alignment) {
        this.alignment = alignment;
    }

    public void reset() {
        lineHeight = null;
        scale = null;
        viewRange = null;
        shadowed = null;
        seeThrough = null;
        defaultBackground = null;
        backgroundColor = null;
        lineWidth = null;
        billboard = null;
        alignment = null;
    }

    public @NotNull HologramStyle copy() {
        HologramStyle copy = new HologramStyle();
        copy.lineHeight = lineHeight;
        copy.scale = scale;
        copy.viewRange = viewRange;
        copy.shadowed = shadowed;
        copy.seeThrough = seeThrough;
        copy.defaultBackground = defaultBackground;
        copy.backgroundColor = backgroundColor;
        copy.lineWidth = lineWidth;
        copy.billboard = billboard;
        copy.alignment = alignment;
        return copy;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
