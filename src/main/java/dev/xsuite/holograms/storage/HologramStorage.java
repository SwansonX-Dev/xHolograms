package dev.xsuite.holograms.storage;

import dev.xsuite.holograms.XHologramsPlugin;
import dev.xsuite.holograms.hologram.Hologram;
import dev.xsuite.holograms.hologram.HologramLine;
import dev.xsuite.holograms.hologram.HologramStyle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class HologramStorage {

    private final XHologramsPlugin plugin;
    private final File file;

    public HologramStorage(@NotNull XHologramsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
    }

    public @NotNull List<Hologram> load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("holograms");
        if (root == null) return List.of();

        List<Hologram> holograms = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;

            World world = Bukkit.getWorld(section.getString("world", ""));
            if (world == null) {
                plugin.getLogger().warning("Skipping hologram '" + id + "' because world '" + section.getString("world") + "' is not loaded.");
                continue;
            }

            Location location = new Location(
                    world,
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    (float) section.getDouble("yaw"),
                    (float) section.getDouble("pitch")
            );
            List<HologramLine> lines = section.getStringList("lines").stream()
                    .map(HologramLine::parse)
                    .toList();
            holograms.add(new Hologram(id, location, lines, loadStyle(section.getConfigurationSection("style"))));
        }
        return holograms;
    }

    public void save(@NotNull Collection<Hologram> holograms) {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("holograms");

        for (Hologram hologram : holograms) {
            Location location = hologram.location();
            ConfigurationSection section = root.createSection(hologram.id());
            section.set("world", location.getWorld() == null ? "world" : location.getWorld().getName());
            section.set("x", location.getX());
            section.set("y", location.getY());
            section.set("z", location.getZ());
            section.set("yaw", location.getYaw());
            section.set("pitch", location.getPitch());
            section.set("lines", hologram.lines().stream().map(HologramLine::raw).toList());
            saveStyle(section.createSection("style"), hologram.style());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save holograms.yml: " + e.getMessage());
        }
    }

    private @NotNull HologramStyle loadStyle(ConfigurationSection section) {
        HologramStyle style = new HologramStyle();
        if (section == null) return style;

        if (section.isSet("line-height")) style.lineHeight(section.getDouble("line-height"));
        if (section.isSet("scale")) style.scale((float) section.getDouble("scale"));
        if (section.isSet("view-range")) style.viewRange((float) section.getDouble("view-range"));
        if (section.isSet("shadowed")) style.shadowed(section.getBoolean("shadowed"));
        if (section.isSet("see-through")) style.seeThrough(section.getBoolean("see-through"));
        if (section.isSet("default-background")) style.defaultBackground(section.getBoolean("default-background"));
        if (section.isSet("background-argb")) style.backgroundColor(Color.fromARGB(section.getInt("background-argb")));
        if (section.isSet("line-width")) style.lineWidth(section.getInt("line-width"));
        if (section.isSet("billboard")) style.billboard(parseEnum(Display.Billboard.class, section.getString("billboard"), Display.Billboard.CENTER));
        if (section.isSet("alignment")) style.alignment(parseEnum(TextDisplay.TextAlignment.class, section.getString("alignment"), TextDisplay.TextAlignment.CENTER));
        return style;
    }

    private void saveStyle(@NotNull ConfigurationSection section, @NotNull HologramStyle style) {
        if (style.lineHeightOverride() != null) section.set("line-height", style.lineHeightOverride());
        if (style.scaleOverride() != null) section.set("scale", style.scaleOverride());
        if (style.viewRangeOverride() != null) section.set("view-range", style.viewRangeOverride());
        if (style.shadowedOverride() != null) section.set("shadowed", style.shadowedOverride());
        if (style.seeThroughOverride() != null) section.set("see-through", style.seeThroughOverride());
        if (style.defaultBackgroundOverride() != null) section.set("default-background", style.defaultBackgroundOverride());
        if (style.backgroundColorOverride() != null) section.set("background-argb", style.backgroundColorOverride().asARGB());
        if (style.lineWidthOverride() != null) section.set("line-width", style.lineWidthOverride());
        if (style.billboardOverride() != null) section.set("billboard", style.billboardOverride().name());
        if (style.alignmentOverride() != null) section.set("alignment", style.alignmentOverride().name());
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
