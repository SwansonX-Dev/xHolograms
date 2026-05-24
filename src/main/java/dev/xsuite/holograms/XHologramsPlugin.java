package dev.xsuite.holograms;

import dev.xsuite.holograms.command.HologramCommand;
import dev.xsuite.holograms.hologram.HologramManager;
import dev.xsuite.holograms.storage.HologramStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class XHologramsPlugin extends JavaPlugin implements Listener {

    private HologramStorage storage;
    private HologramManager holograms;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("holograms.yml", false);

        storage = new HologramStorage(this);
        holograms = new HologramManager(this, storage);
        holograms.load();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (holograms != null) {
                getLogger().info("Delayed startup refresh for persisted holograms.");
                holograms.reload();
            }
        }, 60L);

        HologramCommand command = new HologramCommand(this, holograms);
        PluginCommand pluginCommand = getCommand("hologram");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(this, this);

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

    @EventHandler
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        if (holograms != null) {
            getLogger().info("World '" + event.getWorld().getName() + "' loaded; refreshing holograms.");
            getServer().getScheduler().runTaskLater(this, holograms::reload, 20L);
        }
    }
}
