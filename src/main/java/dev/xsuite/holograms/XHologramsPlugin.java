package dev.xsuite.holograms;

import dev.xsuite.holograms.command.HologramCommand;
import dev.xsuite.holograms.hologram.HologramManager;
import dev.xsuite.holograms.storage.HologramStorage;
import dev.xsuite.holograms.web.HologramWebServer;
import dev.xsuite.holograms.web.PinStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class XHologramsPlugin extends JavaPlugin implements Listener {

    private HologramStorage storage;
    private HologramManager holograms;
    private PinStore pinStore;
    private HologramWebServer webServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("holograms.yml", false);

        storage = new HologramStorage(this);
        holograms = new HologramManager(this, storage);
        holograms.load();
        pinStore = new PinStore();
        webServer = new HologramWebServer(this, holograms, pinStore);
        webServer.start();

        HologramCommand command = new HologramCommand(this, holograms, pinStore);
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
        if (webServer != null) {
            webServer.stop();
        }
        if (holograms != null) {
            holograms.shutdown();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        holograms.reload();
        if (webServer != null) {
            webServer.stop();
        }
        webServer = new HologramWebServer(this, holograms, pinStore);
        webServer.start();
    }

    public @NotNull HologramManager holograms() {
        return holograms;
    }

    public @NotNull PinStore pinStore() {
        return pinStore;
    }

    @EventHandler
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        if (holograms == null) return;
        getServer().getScheduler().runTaskLater(this, () -> holograms.onWorldLoaded(event.getWorld()), 20L);
    }

    @EventHandler
    public void onWorldUnload(@NotNull WorldUnloadEvent event) {
        if (holograms == null) return;
        holograms.onWorldUnloaded(event.getWorld());
    }
}
