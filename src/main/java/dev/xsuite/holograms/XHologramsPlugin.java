package dev.xsuite.holograms;

import dev.xsuite.holograms.command.HologramCommand;
import dev.xsuite.holograms.hologram.HologramManager;
import dev.xsuite.holograms.storage.HologramStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class XHologramsPlugin extends JavaPlugin {

    private HologramStorage storage;
    private HologramManager holograms;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("holograms.yml", false);

        storage = new HologramStorage(this);
        holograms = new HologramManager(this, storage);
        holograms.load();

        HologramCommand command = new HologramCommand(this, holograms);
        PluginCommand pluginCommand = getCommand("hologram");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getLogger().info("Loaded " + holograms.holograms().size() + " hologram(s).");
    }

    @Override
    public void onDisable() {
        if (holograms != null) {
            holograms.shutdown();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        holograms.reload();
    }

    public @NotNull HologramManager holograms() {
        return holograms;
    }
}
