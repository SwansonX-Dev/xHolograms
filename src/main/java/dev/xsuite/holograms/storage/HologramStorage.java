package dev.xsuite.holograms.storage;

import dev.xsuite.holograms.XHologramsPlugin;
import dev.xsuite.holograms.hologram.Hologram;
import dev.xsuite.holograms.hologram.HologramLine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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
            holograms.add(new Hologram(id, location, lines));
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
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save holograms.yml: " + e.getMessage());
        }
    }
}
