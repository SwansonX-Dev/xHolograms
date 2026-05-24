package dev.xsuite.holograms.command;

import dev.xsuite.holograms.XHologramsPlugin;
import dev.xsuite.holograms.hologram.Hologram;
import dev.xsuite.holograms.hologram.HologramManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class HologramCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "create", "delete", "list", "near", "info", "teleport", "movehere",
            "addline", "insertline", "setline", "removeline", "clear", "reload"
    );

    private final XHologramsPlugin plugin;
    private final HologramManager holograms;

    public HologramCommand(@NotNull XHologramsPlugin plugin, @NotNull HologramManager holograms) {
        this.plugin = plugin;
        this.holograms = holograms;
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
                case "list" -> list(sender);
                case "near" -> near(sender);
                case "info" -> info(sender, args);
                case "teleport", "tp" -> teleport(sender, args);
                case "movehere" -> moveHere(sender, args);
                case "addline" -> addLine(sender, args);
                case "insertline" -> insertLine(sender, args);
                case "setline" -> setLine(sender, args);
                case "removeline" -> removeLine(sender, args);
                case "clear" -> clear(sender, args);
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
        sender.sendMessage("§e/" + label + " movehere <id> §7- move to your location");
        sender.sendMessage("§e/" + label + " list|near|info|delete|reload");
        sender.sendMessage("§7MiniMessage is supported. Use §f||§7 between frames for animation.");
    }

    private void create(@NotNull CommandSender sender, @NotNull String[] args) {
        requireAdmin(sender);
        Player player = requirePlayer(sender);
        requireArgs(args, 2, "Usage: /hologram create <id> [text]");
        List<String> lines = args.length >= 3 ? List.of(join(args, 2)) : List.of("<gold><bold>" + args[1] + "</bold>", "<gray>Edit me with /holo setline");
        Hologram hologram = holograms.create(args[1], player.getLocation(), lines);
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

    private void list(@NotNull CommandSender sender) {
        List<Hologram> all = new ArrayList<>(holograms.holograms());
        if (all.isEmpty()) {
            sender.sendMessage("§7No holograms exist yet.");
            return;
        }
        sender.sendMessage("§6xHolograms §7(" + all.size() + "):");
        for (Hologram hologram : all) {
            Location location = hologram.location();
            String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
            sender.sendMessage("§e" + hologram.id() + " §7- " + world + " " + block(location) + " §8(" + hologram.lines().size() + " lines)");
        }
    }

    private void near(@NotNull CommandSender sender) {
        Player player = requirePlayer(sender);
        Hologram nearest = holograms.nearest(player.getLocation(), 32.0D);
        if (nearest == null) {
            sender.sendMessage("§7No holograms within 32 blocks.");
            return;
        }
        sender.sendMessage("§aNearest hologram: §f" + nearest.id() + " §7at " + block(nearest.location()));
    }

    private void info(@NotNull CommandSender sender, @NotNull String[] args) {
        requireArgs(args, 2, "Usage: /hologram info <id>");
        Hologram hologram = requireHologram(args[1]);
        sender.sendMessage("§6" + hologram.id() + " §7at " + block(hologram.location()));
        for (int i = 0; i < hologram.lines().size(); i++) {
            sender.sendMessage("§e" + (i + 1) + "§7: §f" + hologram.lines().get(i).raw());
        }
    }

    private void teleport(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = requirePlayer(sender);
        requireArgs(args, 2, "Usage: /hologram teleport <id>");
        player.teleport(requireHologram(args[1]).location());
        sender.sendMessage("§aTeleported.");
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
        holograms.delete(hologram.id());
        sender.sendMessage("§aCleared §f" + hologram.id() + "§a.");
    }

    private void reload(@NotNull CommandSender sender) {
        requireAdmin(sender);
        plugin.reloadPlugin();
        sender.sendMessage("§aReloaded xHolograms.");
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
        if (args.length == 3 && List.of("setline", "insertline", "removeline").contains(args[0].toLowerCase(Locale.ROOT))) {
            Hologram hologram = holograms.get(args[1]).orElse(null);
            if (hologram == null) return List.of();
            int max = args[0].equalsIgnoreCase("insertline") ? hologram.lines().size() + 1 : hologram.lines().size();
            List<String> lines = new ArrayList<>();
            for (int i = 1; i <= max; i++) lines.add(Integer.toString(i));
            return filter(lines, args[2]);
        }
        return List.of();
    }

    private boolean expectsHologram(@NotNull String subcommand) {
        return !List.of("help", "create", "list", "near", "reload").contains(subcommand.toLowerCase(Locale.ROOT));
    }

    private List<String> filter(@NotNull List<String> options, @NotNull String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
