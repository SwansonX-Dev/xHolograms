package dev.xsuite.holograms.hologram;

import dev.xsuite.holograms.XHologramsPlugin;
import dev.xsuite.holograms.storage.HologramStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HologramManager {

    private static final DecimalFormat TPS_FORMAT = new DecimalFormat("0.0");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final XHologramsPlugin plugin;
    private final HologramStorage storage;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey hologramKey;
    private final Map<String, Hologram> holograms = new HashMap<>();
    private final Map<String, List<TextDisplay>> spawned = new HashMap<>();
    private HologramSettings settings;
    private BukkitTask updateTask;
    private int animationTick;

    public HologramManager(@NotNull XHologramsPlugin plugin, @NotNull HologramStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.hologramKey = new NamespacedKey(plugin, "hologram_id");
    }

    public void load() {
        settings = HologramSettings.from(plugin.getConfig());
        removeTaggedEntities();
        holograms.clear();
        storage.load().forEach(hologram -> holograms.put(hologram.id(), hologram));
        holograms.values().forEach(this::spawn);
        startUpdater();
    }

    public void reload() {
        shutdown();
        load();
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        spawned.values().stream().flatMap(Collection::stream).forEach(Entity::remove);
        spawned.clear();
    }

    public @NotNull Collection<Hologram> holograms() {
        return holograms.values().stream()
                .sorted(Comparator.comparing(Hologram::id))
                .toList();
    }

    public @NotNull Optional<Hologram> get(@NotNull String id) {
        return Optional.ofNullable(holograms.get(Hologram.normalizeId(id)));
    }

    public @NotNull Hologram create(@NotNull String id, @NotNull Location location, @NotNull List<String> rawLines) {
        String normalized = Hologram.normalizeId(id);
        if (holograms.containsKey(normalized)) {
            throw new IllegalArgumentException("A hologram named '" + normalized + "' already exists.");
        }
        List<HologramLine> lines = rawLines.stream().map(HologramLine::parse).toList();
        Hologram hologram = new Hologram(normalized, location, lines.isEmpty() ? List.of(HologramLine.parse("<white>New hologram")) : lines);
        holograms.put(hologram.id(), hologram);
        spawn(hologram);
        save();
        return hologram;
    }

    public boolean delete(@NotNull String id) {
        Hologram removed = holograms.remove(Hologram.normalizeId(id));
        if (removed == null) return false;
        despawn(removed.id());
        save();
        return true;
    }

    public void respawn(@NotNull Hologram hologram) {
        despawn(hologram.id());
        spawn(hologram);
        save();
    }

    public void save() {
        storage.save(holograms());
    }

    public @Nullable Hologram nearest(@NotNull Location origin, double maxDistance) {
        World world = origin.getWorld();
        if (world == null) return null;
        double maxSquared = maxDistance * maxDistance;
        return holograms.values().stream()
                .filter(hologram -> world.equals(hologram.location().getWorld()))
                .filter(hologram -> hologram.location().distanceSquared(origin) <= maxSquared)
                .min(Comparator.comparingDouble(hologram -> hologram.location().distanceSquared(origin)))
                .orElse(null);
    }

    private void spawn(@NotNull Hologram hologram) {
        Location base = hologram.location();
        World world = base.getWorld();
        if (world == null) {
            plugin.getLogger().warning("Skipping hologram '" + hologram.id() + "' because its world is not loaded.");
            return;
        }
        base.getChunk().load();

        List<TextDisplay> entities = new ArrayList<>();
        for (int index = 0; index < hologram.lines().size(); index++) {
            HologramStyle style = hologram.style();
            Location lineLocation = base.clone().subtract(0.0D, index * style.lineHeight(settings), 0.0D);
            TextDisplay display = (TextDisplay) world.spawnEntity(lineLocation, EntityType.TEXT_DISPLAY);
            display.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, hologram.id());
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setBillboard(style.billboard(settings));
            display.setShadowed(style.shadowed(settings));
            display.setSeeThrough(style.seeThrough(settings));
            display.setDefaultBackground(style.defaultBackground(settings));
            display.setBackgroundColor(style.backgroundColor(settings));
            display.setLineWidth(style.lineWidth(settings));
            display.setAlignment(style.alignment(settings));
            display.setViewRange(style.viewRange(settings));
            display.setBrightness(settings.brightness());
            display.setTransformation(scaled(style.scale()));
            display.setTeleportDuration(1);
            display.text(render(hologram.lines().get(index), base));
            entities.add(display);
        }
        spawned.put(hologram.id(), entities);
    }

    private void despawn(@NotNull String id) {
        List<TextDisplay> entities = spawned.remove(id);
        if (entities != null) {
            entities.forEach(Entity::remove);
        }
    }

    private void startUpdater() {
        if (updateTask != null) updateTask.cancel();
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            animationTick++;
            for (Hologram hologram : holograms.values()) {
                List<TextDisplay> displays = spawned.get(hologram.id());
                if (displays == null || displays.size() != hologram.lines().size()
                        || displays.stream().anyMatch(display -> !display.isValid())) {
                    despawn(hologram.id());
                    spawn(hologram);
                    continue;
                }
                Location base = hologram.location();
                for (int index = 0; index < displays.size(); index++) {
                    TextDisplay display = displays.get(index);
                    if (!display.isValid()) continue;
                    display.text(render(hologram.lines().get(index), base));
                }
            }
        }, settings.updateIntervalTicks(), settings.updateIntervalTicks());
    }

    private @NotNull Transformation scaled(float scale) {
        return new Transformation(
                new Vector3f(),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        );
    }

    private @NotNull Component render(@NotNull HologramLine line, @NotNull Location location) {
        String text = applyPlaceholders(line.frame(animationTick), location);
        try {
            return miniMessage.deserialize(text);
        } catch (ParsingException ignored) {
            return Component.text(text);
        }
    }

    private @NotNull String applyPlaceholders(@NotNull String input, @NotNull Location location) {
        double tps = Bukkit.getServer().getTPS()[0];
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return input
                .replace("{online}", Integer.toString(Bukkit.getOnlinePlayers().size()))
                .replace("{max_players}", Integer.toString(Bukkit.getMaxPlayers()))
                .replace("{world}", world)
                .replace("{time}", LocalTime.now().format(TIME_FORMAT))
                .replace("{tps}", TPS_FORMAT.format(tps));
    }

    private void removeTaggedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getPersistentDataContainer().has(hologramKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }
}
