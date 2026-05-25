package dev.xsuite.holograms.command;

import dev.xsuite.holograms.XHologramsPlugin;
import dev.xsuite.holograms.hologram.Hologram;
import dev.xsuite.holograms.hologram.HologramLine;
import dev.xsuite.holograms.hologram.HologramManager;
import dev.xsuite.holograms.hologram.HologramStyle;
import dev.xsuite.holograms.web.PinStore;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class HologramCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "create", "delete", "list", "near", "nearby", "info", "teleport", "movehere", "move",
            "copy", "rename", "addline", "insertline", "setline", "removeline", "clear", "style", "pin", "reload"
    );
    private static final List<String> STYLE_KEYS = List.of(
            "spacing", "scale", "viewrange", "linewidth", "shadow", "seethrough",
            "background", "backgroundcolor", "billboard", "align", "reset"
    );

    private final XHologramsPlugin plugin;
    private final HologramManager holograms;
    private final PinStore pinStore;

    public HologramCommand(@NotNull XHologramsPlugin plugin, @NotNull HologramManager holograms, @NotNull PinStore pinStore) {
        this.plugin = plugin;
        this.holograms = holograms;
        this.pinStore = pinStore;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("xholograms.use") && !sender.hasPermission("xholograms.admin")) {
            sender.sendMessage("§cYou do not have permission to use xHolograms.");
            return true;
        }

        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "help" -> help(sender, label);
                case "create" -> create(sender, args);
                case "delete" -> delete(sender, args);
                case "list" -> list(sender, args);
                case "near" -> near(sender, args);
                case "nearby" -> nearby(sender, args);
                case "info" -> info(sender, args);
                case "teleport", "tp" -> teleport(sender, args);
                case "movehere" -> moveHere(sender, args);
                case "move" -> move(sender, args);
                case "copy", "clone" -> copy(sender, args);
                case "rename" -> rename(sender, args);
                case "addline" -> addLine(sender, args);
                case "insertline" -> insertLine(sender, args);
                case "setline" -> setLine(sender, args);
                case "removeline" -> removeLine(sender, args);
                case "clear" -> clear(sender, args);
                case "style" -> style(sender, args);
                case "pin" -> pin(sender);
                case "reload" -> reload(sender);
                default -> sender.sendMessage("§cUnknown subcommand. Use /" + label + " help.");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c" + e.getMessage());
        }
        return true;
    }

    private void help(@NotNull CommandSender sender, @NotNull String label) {
        sender.sendMessage("§6xHolograms §7commands:");
        sender.sendMessage("§e/" + label + " create <id> [text] §7- create at your location");
        sender.sendMessage("§e/" + label + " addline <id> <text> §7- append a line");
        sender.sendMessage("§e/" + label + " setline <id> <line> <text> §7- replace a line");
        sender.sendMessage("§e/" + label + " insertline <id> <line> <text> §7- insert a line");
        sender.sendMessage("§e/" + label + " removeline <id> <line> §7- remove a line");
        sender.sendMessage("§e/" + label + " clear <id> §7- empty all lines (keeps hologram)");
        sender.sendMessage("§e/" + label + " movehere <id> §7- move to your location");
        sender.sendMessage("§e/" + label + " move <id> [<x> <y> <z>] §7- move to coords (~ = current)");
        sender.sendMessage("§e/" + label + " copy <src> <dst> §7- duplicate at your location");
        sender.sendMessage("§e/" + label + " rename <old> <new> §7- rename a hologram");
        sender.sendMessage("§e/" + label + " style <id> <setting> <value> §7- customize display");
        sender.sendMessage("§e/" + label + " pin §7- create a browser editor code for your location");
        sender.sendMessage("§e/" + label + " list [world] §7- list all (optionally per-world)");
        sender.sendMessage("§e/" + label + " near [distance] §7- show closest hologram");
        sender.sendMessage("§e/" + label + " nearby [distance] §7- list all within range");
        sender.sendMessage("§e/" + label + " info|teleport|delete|reload");
        sender.sendMessage("§7Placeholders: §f{online} {max_players} {world} {x} {y} {z} {time} {date} {tps}");
        sender.sendMessage("§7MiniMessage is supported. Use §f||§7 between frames for animation.");
    }

    private void create(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        Player player = requirePlayer(sender);
        requireArgs(args, 2, "Usage: /hologram create <id> [text]");
        String safeId = Hologram.normalizeId(args[1]);
        List<String> lines = args.length >= 3
                ? List.of(join(args, 2))
                : List.of("<gold><bold>" + safeId + "</bold>", "<gray>Edit me with /holo setline");
        Hologram hologram = holograms.create(safeId, player.getLocation(), lines);
        sender.sendMessage("§aCreated hologram §f" + hologram.id() + "§a.");
    }

    private void delete(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        requireArgs(args, 2, "Usage: /hologram delete <id>");
        if (holograms.delete(args[1])) {
            sender.sendMessage("§aDeleted hologram §f" + Hologram.normalizeId(args[1]) + "§a.");
        } else {
            sender.sendMessage("§cNo hologram named '" + Hologram.normalizeId(args[1]) + "'.");
        }
    }

    private void list(@NotNull CommandSender sender, @NotNull String[] args) {
        String filter = args.length >= 2 ? args[1] : null;
        List<Hologram> all = holograms.holograms().stream()
                .filter(h -> filter == null || filter.equalsIgnoreCase(h.worldName()))
                .toList();
        if (all.isEmpty()) {
            sender.sendMessage(filter == null ? "§7No holograms exist yet." : "§7No holograms in world '" + filter + "'.");
            return;
        }
        String header = filter == null
                ? "§6xHolograms §7(" + all.size() + ")"
                : "§6xHolograms §7in §f" + filter + "§7 (" + all.size() + ")";
        sender.sendMessage(header + ":");
        for (Hologram hologram : all) {
            Location location = hologram.location();
            sender.sendMessage("§e" + hologram.id() + " §7- " + worldLabel(hologram) + " " + block(location) + " §8(" + hologram.lines().size() + " lines)");
        }
    }

    private String worldLabel(@NotNull Hologram hologram) {
        String name = hologram.worldName();
        if (name == null) return "unknown";
        Location loc = hologram.location();
        return loc.getWorld() == null ? name + " §8(unloaded)" : name;
    }

    private void near(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = requirePlayer(sender);
        double distance = parseDistance(args, 32.0D);
        Hologram nearest = holograms.nearest(player.getLocation(), distance);
        if (nearest == null) {
            sender.sendMessage("§7No holograms within " + ((long) distance) + " blocks.");
            return;
        }
        sender.sendMessage("§aNearest hologram: §f" + nearest.id() + " §7at " + block(nearest.location()));
    }

    private void nearby(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = requirePlayer(sender);
        double distance = parseDistance(args, 32.0D);
        Location origin = player.getLocation();
        List<Hologram> matches = holograms.within(origin, distance);
        if (matches.isEmpty()) {
            sender.sendMessage("§7No holograms within " + ((long) distance) + " blocks.");
            return;
        }
        sender.sendMessage("§6xHolograms §7within " + ((long) distance) + " blocks (" + matches.size() + "):");
        for (Hologram hologram : matches) {
            double blocks = Math.sqrt(hologram.location().distanceSquared(origin));
            sender.sendMessage("§e" + hologram.id() + " §7- " + String.format("%.1f", blocks) + "m " + block(hologram.location()));
        }
    }

    private double parseDistance(@NotNull String[] args, double defaultValue) {
        if (args.length < 2) return defaultValue;
        double distance = parseDouble(args[1], "distance");
        if (distance <= 0 || distance > 1024.0D) {
            throw new IllegalArgumentException("distance must be between 0 and 1024.");
        }
        return distance;
    }

    private void info(@NotNull CommandSender sender, @NotNull String[] args) {
        requireArgs(args, 2, "Usage: /hologram info <id>");
        Hologram hologram = requireHologram(args[1]);
        sender.sendMessage("§6" + hologram.id() + " §7in §f" + worldLabel(hologram) + " §7at " + block(hologram.location()));
        for (int i = 0; i < hologram.lines().size(); i++) {
            HologramLine line = hologram.lines().get(i);
            String marker = line.animated() ? " §8(" + line.frames().size() + " frames)" : "";
            sender.sendMessage("§e" + (i + 1) + "§7: §f" + line.raw() + marker);
        }
        HologramStyle style = hologram.style();
        sender.sendMessage("§7Style overrides:");
        sender.sendMessage("§7  spacing=" + value(style.lineHeightOverride())
                + ", scale=" + value(style.scaleOverride())
                + ", viewrange=" + value(style.viewRangeOverride())
                + ", linewidth=" + value(style.lineWidthOverride()));
        sender.sendMessage("§7  shadow=" + value(style.shadowedOverride())
                + ", seethrough=" + value(style.seeThroughOverride())
                + ", background=" + value(style.defaultBackgroundOverride())
                + ", backgroundcolor=" + colorValue(style.backgroundColorOverride()));
        sender.sendMessage("§7  billboard=" + value(style.billboardOverride())
                + ", align=" + value(style.alignmentOverride()));
    }

    private String colorValue(@Nullable Color color) {
        if (color == null) return "default";
        return String.format("#%08X", color.asARGB());
    }

    private void teleport(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = requirePlayer(sender);
        requireArgs(args, 2, "Usage: /hologram teleport <id>");
        Hologram hologram = requireHologram(args[1]);
        Location target = hologram.location();
        if (target.getWorld() == null) {
            throw new IllegalArgumentException("Cannot teleport: hologram '" + hologram.id() + "' is in an unloaded world.");
        }
        player.teleport(target);
        sender.sendMessage("§aTeleported to §f" + hologram.id() + "§a.");
    }

    private void moveHere(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        Player player = requirePlayer(sender);
        requireArgs(args, 2, "Usage: /hologram movehere <id>");
        Hologram hologram = requireHologram(args[1]);
        hologram.location(player.getLocation());
        holograms.respawn(hologram);
        sender.sendMessage("§aMoved §f" + hologram.id() + "§a.");
    }

    private void move(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        requireArgs(args, 2, "Usage: /hologram move <id> [<x> <y> <z>]");
        Hologram hologram = requireHologram(args[1]);
        Location target;
        if (args.length == 2) {
            Player player = requirePlayer(sender);
            target = player.getLocation();
        } else {
            requireArgs(args, 5, "Usage: /hologram move <id> <x> <y> <z>");
            Location current = hologram.location();
            if (current.getWorld() == null) {
                throw new IllegalArgumentException("Hologram '" + hologram.id() + "' is in an unloaded world; use /holo movehere instead.");
            }
            double x = parseCoord(args[2], current.getX(), "x");
            double y = parseCoord(args[3], current.getY(), "y");
            double z = parseCoord(args[4], current.getZ(), "z");
            target = new Location(current.getWorld(), x, y, z, current.getYaw(), current.getPitch());
        }
        hologram.location(target);
        holograms.respawn(hologram);
        sender.sendMessage("§aMoved §f" + hologram.id() + "§7 to " + block(target));
    }

    private void copy(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        Player player = requirePlayer(sender);
        requireArgs(args, 3, "Usage: /hologram copy <src> <dst>");
        Hologram copy = holograms.copy(args[1], args[2], player.getLocation());
        sender.sendMessage("§aCopied §f" + Hologram.normalizeId(args[1]) + "§a to §f" + copy.id() + "§a.");
    }

    private void rename(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        requireArgs(args, 3, "Usage: /hologram rename <old> <new>");
        Hologram renamed = holograms.rename(args[1], args[2]);
        sender.sendMessage("§aRenamed §f" + Hologram.normalizeId(args[1]) + "§a to §f" + renamed.id() + "§a.");
    }

    private double parseCoord(@NotNull String raw, double current, @NotNull String name) {
        if ("~".equals(raw)) return current;
        try {
            if (raw.startsWith("~")) return current + Double.parseDouble(raw.substring(1));
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number or relative ~offset.");
        }
    }

    private void addLine(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        requireArgs(args, 3, "Usage: /hologram addline <id> <text>");
        Hologram hologram = requireHologram(args[1]);
        hologram.addLine(join(args, 2));
        holograms.respawn(hologram);
        sender.sendMessage("§aAdded line " + hologram.lines().size() + " to §f" + hologram.id() + "§a.");
    }

    private void insertLine(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        requireArgs(args, 4, "Usage: /hologram insertline <id> <line> <text>");
        Hologram hologram = requireHologram(args[1]);
        int index = parseLine(args[2], hologram.lines().size() + 1);
        hologram.insertLine(index, join(args, 3));
        holograms.respawn(hologram);
        sender.sendMessage("§aInserted line " + (index + 1) + " in §f" + hologram.id() + "§a.");
    }

    private void setLine(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        requireArgs(args, 4, "Usage: /hologram setline <id> <line> <text>");
        Hologram hologram = requireHologram(args[1]);
        int index = parseLine(args[2], hologram.lines().size());
        hologram.setLine(index, join(args, 3));
        holograms.respawn(hologram);
        sender.sendMessage("§aUpdated line " + (index + 1) + " in §f" + hologram.id() + "§a.");
    }

    private void removeLine(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        requireArgs(args, 3, "Usage: /hologram removeline <id> <line>");
        Hologram hologram = requireHologram(args[1]);
        int index = parseLine(args[2], hologram.lines().size());
        hologram.removeLine(index);
        if (hologram.isEmpty()) {
            holograms.delete(hologram.id());
            sender.sendMessage("§aRemoved the last line and deleted §f" + hologram.id() + "§a.");
            return;
        }
        holograms.respawn(hologram);
        sender.sendMessage("§aRemoved line " + (index + 1) + " from §f" + hologram.id() + "§a.");
    }

    private void clear(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        requireArgs(args, 2, "Usage: /hologram clear <id>");
        Hologram hologram = requireHologram(args[1]);
        hologram.replaceLines(List.of(HologramLine.parse("<gray>(empty)")));
        holograms.respawn(hologram);
        sender.sendMessage("§aCleared lines of §f" + hologram.id() + "§a.");
    }

    private void style(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        requireArgs(args, 3, "Usage: /hologram style <id> <setting> [value]");
        Hologram hologram = requireHologram(args[1]);
        HologramStyle style = hologram.style();
        String setting = args[2].toLowerCase(Locale.ROOT);

        if ("reset".equals(setting)) {
            style.reset();
            holograms.respawn(hologram);
            sender.sendMessage("§aReset all style overrides for §f" + hologram.id() + "§a.");
            return;
        }

        requireArgs(args, 4, "Usage: /hologram style <id> " + setting + " <value>");
        String value = args[3];

        switch (setting) {
            case "spacing", "lineheight", "line-height" -> style.lineHeight(parseDouble(value, "spacing"));
            case "scale" -> style.scale((float) parseDouble(value, "scale"));
            case "viewrange", "view-range" -> style.viewRange((float) parseDouble(value, "viewrange"));
            case "linewidth", "line-width" -> style.lineWidth(parseInt(value, "linewidth"));
            case "shadow", "shadowed" -> style.shadowed(parseBoolean(value, "shadow"));
            case "seethrough", "see-through" -> style.seeThrough(parseBoolean(value, "seethrough"));
            case "background", "defaultbackground", "default-background" -> style.defaultBackground(parseBoolean(value, "background"));
            case "backgroundcolor", "background-color" -> style.backgroundColor(parseColor(value));
            case "billboard" -> style.billboard(parseEnum(Display.Billboard.class, value, "billboard"));
            case "align", "alignment" -> style.alignment(parseEnum(TextDisplay.TextAlignment.class, value, "alignment"));
            default -> throw new IllegalArgumentException("Unknown style setting. Use spacing, scale, viewrange, linewidth, shadow, seethrough, background, backgroundcolor, billboard, align, or reset.");
        }

        holograms.respawn(hologram);
        sender.sendMessage("§aUpdated style for §f" + hologram.id() + "§a.");
    }

    private void reload(@NotNull CommandSender sender) {
        requireAdmin(sender);
        plugin.reloadPlugin();
        sender.sendMessage("§aReloaded xHolograms.");
    }

    private void pin(@NotNull CommandSender sender) {
        requireAdmin(sender);
        Player player = requirePlayer(sender);
        String code = pinStore.create(player);
        long minutes = PinStore.ttlMillis() / 60000L;
        sender.sendMessage("§aWeb editor pin: §f" + code);
        sender.sendMessage("§7Paste this code in the web editor to use your current location. It expires in " + minutes + " minutes.");
    }

    private Hologram requireHologram(@NotNull String id) {
        return holograms.get(id).orElseThrow(() -> new IllegalArgumentException("No hologram named '" + Hologram.normalizeId(id) + "'."));
    }

    private void requireAdmin(@NotNull CommandSender sender) {
        if (!sender.hasPermission("xholograms.admin")) {
            throw new IllegalArgumentException("You do not have permission to manage holograms.");
        }
    }

    private Player requirePlayer(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Only players can use that command.");
        }
        return player;
    }

    private void requireArgs(@NotNull String[] args, int min, @NotNull String usage) {
        if (args.length < min) throw new IllegalArgumentException(usage);
    }

    private int parseLine(@NotNull String raw, int maxInclusive) {
        try {
            int line = Integer.parseInt(raw);
            if (line < 1 || line > maxInclusive) {
                throw new IllegalArgumentException("Line must be between 1 and " + maxInclusive + ".");
            }
            return line - 1;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Line must be a number.");
        }
    }

    private double parseDouble(@NotNull String raw, @NotNull String name) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number.");
        }
    }

    private int parseInt(@NotNull String raw, @NotNull String name) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a whole number.");
        }
    }

    private boolean parseBoolean(@NotNull String raw, @NotNull String name) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "enabled" -> true;
            case "false", "no", "off", "disabled" -> false;
            default -> throw new IllegalArgumentException(name + " must be true/false, on/off, or yes/no.");
        };
    }

    private Color parseColor(@NotNull String raw) {
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() == 6) hex = "ff" + hex;
        if (hex.length() != 8) throw new IllegalArgumentException("backgroundcolor must be #RRGGBB or #AARRGGBB.");
        try {
            return Color.fromARGB((int) Long.parseLong(hex, 16));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("backgroundcolor must be #RRGGBB or #AARRGGBB.");
        }
    }

    private <E extends Enum<E>> E parseEnum(@NotNull Class<E> type, @NotNull String raw, @NotNull String name) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " must be one of: " + String.join(", ", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList()));
        }
    }

    private String value(Object value) {
        return value == null ? "default" : value.toString();
    }

    private String join(@NotNull String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    private String block(@NotNull Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return filter(SUBCOMMANDS, args[0]);
        if (args.length == 2 && expectsHologram(args[0])) {
            return filter(holograms.holograms().stream().map(Hologram::id).toList(), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("near") || args[0].equalsIgnoreCase("nearby"))) {
            return filter(List.of("16", "32", "64", "128"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return filter(holograms.holograms().stream().map(Hologram::worldName).filter(Objects::nonNull).distinct().toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("style")) return filter(STYLE_KEYS, args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("style")) return styleValues(args[2], args[3]);
        if (args.length == 3 && List.of("setline", "insertline", "removeline").contains(args[0].toLowerCase(Locale.ROOT))) {
            Hologram hologram = holograms.get(args[1]).orElse(null);
            if (hologram == null) return List.of();
            int max = args[0].equalsIgnoreCase("insertline") ? hologram.lines().size() + 1 : hologram.lines().size();
            List<String> lines = new ArrayList<>();
            for (int i = 1; i <= max; i++) lines.add(Integer.toString(i));
            return filter(lines, args[2]);
        }
        if (args.length >= 3 && args.length <= 5 && args[0].equalsIgnoreCase("move") && sender instanceof Player player) {
            Location at = player.getLocation();
            double current = switch (args.length) {
                case 3 -> at.getX();
                case 4 -> at.getY();
                default -> at.getZ();
            };
            return filter(List.of("~", String.format("%.1f", current)), args[args.length - 1]);
        }
        return List.of();
    }

    private boolean expectsHologram(@NotNull String subcommand) {
        return !List.of("help", "create", "list", "near", "nearby", "pin", "reload").contains(subcommand.toLowerCase(Locale.ROOT));
    }

    private List<String> styleValues(@NotNull String setting, @NotNull String input) {
        return switch (setting.toLowerCase(Locale.ROOT)) {
            case "spacing", "lineheight", "line-height" -> filter(List.of("0.25", "0.38", "0.5", "0.75", "1.0"), input);
            case "scale" -> filter(List.of("0.75", "1.0", "1.25", "1.5", "2.0"), input);
            case "viewrange", "view-range" -> filter(List.of("32", "64", "96", "128"), input);
            case "linewidth", "line-width" -> filter(List.of("120", "180", "220", "320", "500"), input);
            case "shadow", "shadowed", "seethrough", "see-through", "background", "defaultbackground", "default-background" -> filter(List.of("true", "false"), input);
            case "billboard" -> filter(Arrays.stream(Display.Billboard.values()).map(Enum::name).toList(), input);
            case "align", "alignment" -> filter(Arrays.stream(TextDisplay.TextAlignment.values()).map(Enum::name).toList(), input);
            case "backgroundcolor", "background-color" -> filter(List.of("#00000000", "#99000000", "#ff000000"), input);
            default -> List.of();
        };
    }

    private List<String> filter(@NotNull List<String> options, @NotNull String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
