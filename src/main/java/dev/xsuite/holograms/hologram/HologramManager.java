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
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class HologramManager {

    private static final DecimalFormat TPS_FORMAT = new DecimalFormat("0.0");
    private static final DecimalFormat COORD_FORMAT = new DecimalFormat("0.##");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final XHologramsPlugin plugin;
    private final HologramStorage storage;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey hologramKey;
    private final Map<String, Hologram> holograms = new HashMap<>();
    private final Map<String, List<TextDisplay>> spawned = new HashMap<>();
    private final Set<String> dormant = new HashSet<>();
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
        dormant.clear();
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

    public void onWorldLoaded(@NotNull World world) {
        removeTaggedEntities(world);
        for (Hologram hologram : holograms.values()) {
            Location location = hologram.location();
            if (location.getWorld() == null) continue;
            if (!world.getName().equals(location.getWorld().getName())) continue;
            despawn(hologram.id());
            spawn(hologram);
        }
    }

    public void onWorldUnloaded(@NotNull World world) {
        String name = world.getName();
        for (Hologram hologram : holograms.values()) {
            Location location = hologram.location();
            if (location.getWorld() == null) continue;
            if (!name.equals(location.getWorld().getName())) continue;
            List<TextDisplay> entities = spawned.remove(hologram.id());
            if (entities != null) {
                entities.forEach(Entity::remove);
            }
            dormant.add(hologram.id());
        }
    }

    public boolean delete(@NotNull String id) {
        Hologram removed = holograms.remove(Hologram.normalizeId(id));
        if (removed == null) return false;
        despawn(removed.id());
        save();
        return true;
    }

    public @NotNull Hologram copy(@NotNull String sourceId, @NotNull String destinationId, @NotNull Location destination) {
        Hologram source = holograms.get(Hologram.normalizeId(sourceId));
        if (source == null) {
            throw new IllegalArgumentException("No hologram named '" + Hologram.normalizeId(sourceId) + "'.");
        }
        String normalized = Hologram.normalizeId(destinationId);
        if (holograms.containsKey(normalized)) {
            throw new IllegalArgumentException("A hologram named '" + normalized + "' already exists.");
        }
        Hologram copy = new Hologram(normalized, destination, source.lines(), source.style().copy());
        holograms.put(copy.id(), copy);
        spawn(copy);
        save();
        return copy;
    }

    public @NotNull Hologram rename(@NotNull String oldId, @NotNull String newId) {
        String oldNormalized = Hologram.normalizeId(oldId);
        Hologram existing = holograms.get(oldNormalized);
        if (existing == null) {
            throw new IllegalArgumentException("No hologram named '" + oldNormalized + "'.");
        }
        String newNormalized = Hologram.normalizeId(newId);
        if (oldNormalized.equals(newNormalized)) {
            throw new IllegalArgumentException("New id is the same as the old id.");
        }
        if (holograms.containsKey(newNormalized)) {
            throw new IllegalArgumentException("A hologram named '" + newNormalized + "' already exists.");
        }
        despawn(oldNormalized);
        holograms.remove(oldNormalized);
        Hologram renamed = new Hologram(newNormalized, existing.location(), existing.lines(), existing.style().copy());
        holograms.put(renamed.id(), renamed);
        spawn(renamed);
        save();
        return renamed;
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
        List<Hologram> matches = within(origin, maxDistance);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public @NotNull List<Hologram> within(@NotNull Location origin, double maxDistance) {
        World world = origin.getWorld();
        if (world == null) return List.of();
        double maxSquared = maxDistance * maxDistance;
        return holograms.values().stream()
                .filter(hologram -> world.equals(hologram.location().getWorld()))
                .filter(hologram -> hologram.location().distanceSquared(origin) <= maxSquared)
                .sorted(Comparator.comparingDouble(hologram -> hologram.location().distanceSquared(origin)))
                .toList();
    }

    private void spawn(@NotNull Hologram hologram) {
        Location base = hologram.location();
        World world = base.getWorld();
        if (world == null) {
            if (dormant.add(hologram.id())) {
                plugin.getLogger().warning("Skipping hologram '" + hologram.id() + "' because its world is not loaded.");
            }
            return;
        }
        dormant.remove(hologram.id());
        base.getChunk().load();

        HologramStyle style = hologram.style();
        List<TextDisplay> entities = new ArrayList<>();
        for (int index = 0; index < hologram.lines().size(); index++) {
            HologramLine line = hologram.lines().get(index);
            Location lineLocation = base.clone().subtract(0.0D, index * style.lineHeight(settings), 0.0D);
            TextDisplay display = world.spawn(lineLocation, TextDisplay.class, entity -> {
                entity.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, hologram.id());
                entity.setPersistent(true);
                entity.setInvulnerable(true);
                entity.setGravity(false);
                entity.setBillboard(style.billboard(settings));
                entity.setShadowed(style.shadowed(settings));
                entity.setSeeThrough(style.seeThrough(settings));
                entity.setDefaultBackground(style.defaultBackground(settings));
                entity.setBackgroundColor(style.backgroundColor(settings));
                entity.setLineWidth(style.lineWidth(settings));
                entity.setAlignment(style.alignment(settings));
                entity.setViewRange(style.viewRange(settings));
                entity.setBrightness(settings.brightness());
                entity.setTransformation(scaled(style.scale()));
                entity.setTeleportDuration(1);
                entity.text(render(line, base));
            });
            entities.add(display);
        }
        spawned.put(hologram.id(), entities);
    }

    private void despawn(@NotNull String id) {
        dormant.remove(id);
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
                if (dormant.contains(hologram.id()) && hologram.location().getWorld() == null) {
                    continue;
                }
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
                    HologramLine line = hologram.lines().get(index);
                    if (!line.dynamic()) continue;
                    display.text(render(line, base));
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
        if (input.indexOf('{') < 0) return input;
        double tps = Bukkit.getServer().getTPS()[0];
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return input
                .replace("{online}", Integer.toString(Bukkit.getOnlinePlayers().size()))
                .replace("{max_players}", Integer.toString(Bukkit.getMaxPlayers()))
                .replace("{world}", world)
                .replace("{x}", COORD_FORMAT.format(location.getX()))
                .replace("{y}", COORD_FORMAT.format(location.getY()))
                .replace("{z}", COORD_FORMAT.format(location.getZ()))
                .replace("{time}", LocalTime.now().format(TIME_FORMAT))
                .replace("{date}", LocalDate.now().format(DATE_FORMAT))
                .replace("{tps}", TPS_FORMAT.format(tps));
    }

    private void removeTaggedEntities() {
        for (World world : Bukkit.getWorlds()) {
            removeTaggedEntities(world);
        }
    }

    private void removeTaggedEntities(@NotNull World world) {
        for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
            if (entity.getPersistentDataContainer().has(hologramKey, PersistentDataType.STRING)) {
                entity.remove();
            }
        }
    }
}
