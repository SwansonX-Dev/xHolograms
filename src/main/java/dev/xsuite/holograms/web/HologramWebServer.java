package dev.xsuite.holograms.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.xsuite.holograms.XHologramsPlugin;
import dev.xsuite.holograms.hologram.Hologram;
import dev.xsuite.holograms.hologram.HologramLine;
import dev.xsuite.holograms.hologram.HologramManager;
import dev.xsuite.holograms.hologram.HologramStyle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HologramWebServer {

    private static final Pattern HOLOGRAM_ID_PATH = Pattern.compile("^/api/holograms/([^/]+)$");
    private static final Pattern PIN_PATH = Pattern.compile("^/api/pins/([A-Z0-9]+)$");
    private static final long MAIN_THREAD_TIMEOUT_MS = 5000L;
    private static final char[] TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final XHologramsPlugin plugin;
    private final HologramManager holograms;
    private final PinStore pinStore;
    private final SecureRandom random = new SecureRandom();
    private final AtomicLong requestCounter = new AtomicLong();
    private HttpServer server;
    private byte[] editorHtml;
    private String token;
    private String bindAddress;
    private int boundPort;

    public HologramWebServer(@NotNull XHologramsPlugin plugin, @NotNull HologramManager holograms, @NotNull PinStore pinStore) {
        this.plugin = plugin;
        this.holograms = holograms;
        this.pinStore = pinStore;
    }

    public synchronized void start() {
        if (server != null) return;
        if (!plugin.getConfig().getBoolean("web.enabled", false)) return;

        editorHtml = loadEditorHtml();
        if (editorHtml == null) {
            plugin.getLogger().severe("Web editor HTML resource missing; refusing to start web server.");
            return;
        }

        bindAddress = plugin.getConfig().getString("web.bind", "127.0.0.1");
        int port = plugin.getConfig().getInt("web.port", 8765);
        token = plugin.getConfig().getString("web.token", "");
        if (token == null || token.isBlank()) {
            token = generateToken();
            plugin.getConfig().set("web.token", token);
            plugin.saveConfig();
            plugin.getLogger().info("Generated new web editor token and saved to config.yml.");
        }

        try {
            HttpServer s = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            s.createContext("/", this::handle);
            s.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "xHolograms-Web-" + requestCounter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }));
            s.start();
            server = s;
            boundPort = s.getAddress().getPort();
            plugin.getLogger().info("Web editor available at http://" + bindAddress + ":" + boundPort + "/?token=" + token);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start web editor on " + bindAddress + ":" + port + " - " + e.getMessage());
            server = null;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private byte[] loadEditorHtml() {
        try (InputStream in = plugin.getResource("web/index.html")) {
            if (in == null) return null;
            return in.readAllBytes();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load web editor HTML: " + e.getMessage());
            return null;
        }
    }

    private static String generateToken() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) sb.append(TOKEN_ALPHABET[r.nextInt(TOKEN_ALPHABET.length)]);
        return sb.toString();
    }

    private void handle(@NotNull HttpExchange exchange) {
        try {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method) && (path.equals("/") || path.equals("/index.html"))) {
                respondHtml(exchange, 200, editorHtml);
                return;
            }
            if ("OPTIONS".equalsIgnoreCase(method)) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!path.startsWith("/api/")) {
                respondJson(exchange, 404, Map.of("error", "Not found"));
                return;
            }

            if (!isAuthorized(exchange, uri)) {
                respondJson(exchange, 401, Map.of("error", "Invalid or missing token"));
                return;
            }

            addCorsHeaders(exchange);
            route(exchange, method, path);
        } catch (Exception e) {
            plugin.getLogger().warning("Web editor request error: " + e.getMessage());
            try {
                respondJson(exchange, 500, Map.of("error", e.getMessage() == null ? "Internal error" : e.getMessage()));
            } catch (IOException ignored) {
            }
        } finally {
            exchange.close();
        }
    }

    private void route(@NotNull HttpExchange exchange, @NotNull String method, @NotNull String path) throws IOException {
        if (path.equals("/api/holograms")) {
            switch (method.toUpperCase(Locale.ROOT)) {
                case "GET" -> handleListHolograms(exchange);
                case "POST" -> handleCreateHologram(exchange);
                default -> methodNotAllowed(exchange);
            }
            return;
        }
        Matcher idMatch = HOLOGRAM_ID_PATH.matcher(path);
        if (idMatch.matches()) {
            String id = idMatch.group(1);
            switch (method.toUpperCase(Locale.ROOT)) {
                case "GET" -> handleGetHologram(exchange, id);
                case "PUT" -> handleUpdateHologram(exchange, id);
                case "DELETE" -> handleDeleteHologram(exchange, id);
                default -> methodNotAllowed(exchange);
            }
            return;
        }
        Matcher pinMatch = PIN_PATH.matcher(path);
        if (pinMatch.matches()) {
            if (!"GET".equalsIgnoreCase(method)) {
                methodNotAllowed(exchange);
                return;
            }
            handleConsumePin(exchange, pinMatch.group(1));
            return;
        }
        if (path.equals("/api/worlds") && "GET".equalsIgnoreCase(method)) {
            handleListWorlds(exchange);
            return;
        }
        if (path.equals("/api/styles") && "GET".equalsIgnoreCase(method)) {
            handleStyleEnums(exchange);
            return;
        }
        respondJson(exchange, 404, Map.of("error", "Unknown endpoint"));
    }

    private void handleListHolograms(@NotNull HttpExchange exchange) throws IOException {
        List<Map<String, Object>> list = callSync(() -> {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Hologram h : holograms.holograms()) {
                result.add(serializeHologram(h));
            }
            return result;
        });
        respondJson(exchange, 200, Map.of("holograms", list));
    }

    private void handleGetHologram(@NotNull HttpExchange exchange, @NotNull String id) throws IOException {
        Map<String, Object> dto = callSync(() -> holograms.get(id).map(this::serializeHologram).orElse(null));
        if (dto == null) {
            respondJson(exchange, 404, Map.of("error", "No hologram named '" + id + "'."));
            return;
        }
        respondJson(exchange, 200, dto);
    }

    private void handleCreateHologram(@NotNull HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJson(exchange);
        String id = Json.string(body, "id");
        if (id == null || id.isBlank()) {
            respondJson(exchange, 400, Map.of("error", "Missing 'id'."));
            return;
        }
        Location location = parseLocation(body);
        if (location == null) {
            respondJson(exchange, 400, Map.of("error", "Invalid or missing world/coords."));
            return;
        }
        List<String> rawLines = parseLines(body);
        Map<String, Object> styleObj = Json.object(body, "style");

        Map<String, Object> result = callSync(() -> {
            Hologram created = holograms.create(id, location, rawLines);
            if (styleObj != null) {
                applyStyle(created.style(), styleObj);
                holograms.respawn(created);
            }
            return serializeHologram(created);
        });
        respondJson(exchange, 200, result);
    }

    private void handleUpdateHologram(@NotNull HttpExchange exchange, @NotNull String id) throws IOException {
        Map<String, Object> body = readJson(exchange);
        Location location = parseLocation(body);
        List<String> rawLines = parseLines(body);
        Map<String, Object> styleObj = Json.object(body, "style");

        Map<String, Object> result = callSync(() -> {
            Hologram hologram = holograms.get(id).orElseThrow(() -> new IllegalArgumentException("No hologram named '" + id + "'."));
            if (location != null) hologram.location(location);
            if (!rawLines.isEmpty()) {
                List<HologramLine> parsed = rawLines.stream().map(HologramLine::parse).toList();
                hologram.replaceLines(parsed);
            }
            if (styleObj != null) applyStyle(hologram.style(), styleObj);
            holograms.respawn(hologram);
            return serializeHologram(hologram);
        });
        respondJson(exchange, 200, result);
    }

    private void handleDeleteHologram(@NotNull HttpExchange exchange, @NotNull String id) throws IOException {
        boolean deleted = callSync(() -> holograms.delete(id));
        if (!deleted) {
            respondJson(exchange, 404, Map.of("error", "No hologram named '" + id + "'."));
            return;
        }
        respondJson(exchange, 200, Map.of("deleted", id));
    }

    private void handleListWorlds(@NotNull HttpExchange exchange) throws IOException {
        List<String> worlds = callSync(() -> Bukkit.getWorlds().stream().map(World::getName).toList());
        respondJson(exchange, 200, Map.of("worlds", worlds));
    }

    private void handleStyleEnums(@NotNull HttpExchange exchange) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("billboard", Arrays.stream(Display.Billboard.values()).map(Enum::name).toList());
        body.put("alignment", Arrays.stream(TextDisplay.TextAlignment.values()).map(Enum::name).toList());
        body.put("placeholders", List.of("{online}", "{max_players}", "{world}", "{x}", "{y}", "{z}", "{time}", "{date}", "{tps}"));
        respondJson(exchange, 200, body);
    }

    private void handleConsumePin(@NotNull HttpExchange exchange, @NotNull String code) throws IOException {
        PinStore.Pin pin = pinStore.consume(code);
        if (pin == null) {
            respondJson(exchange, 404, Map.of("error", "Pin code invalid or expired."));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("world", pin.world());
        body.put("x", pin.x());
        body.put("y", pin.y());
        body.put("z", pin.z());
        body.put("yaw", (double) pin.yaw());
        body.put("pitch", (double) pin.pitch());
        body.put("player", pin.playerName());
        respondJson(exchange, 200, body);
    }

    private boolean isAuthorized(@NotNull HttpExchange exchange, @NotNull URI uri) {
        String header = exchange.getRequestHeaders().getFirst("X-Auth-Token");
        if (token.equals(header)) return true;
        String query = uri.getRawQuery();
        if (query == null) return false;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String key = part.substring(0, eq);
            String value = part.substring(eq + 1);
            if ("token".equals(key) && token.equals(value)) return true;
        }
        return false;
    }

    private void addCorsHeaders(@NotNull HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, X-Auth-Token");
    }

    private @NotNull Map<String, Object> serializeHologram(@NotNull Hologram h) {
        Location loc = h.location();
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", h.id());
        dto.put("world", h.worldName());
        dto.put("worldLoaded", loc.getWorld() != null);
        dto.put("x", loc.getX());
        dto.put("y", loc.getY());
        dto.put("z", loc.getZ());
        dto.put("yaw", (double) loc.getYaw());
        dto.put("pitch", (double) loc.getPitch());
        dto.put("lines", h.lines().stream().map(HologramLine::raw).toList());
        dto.put("style", serializeStyle(h.style()));
        return dto;
    }

    private @NotNull Map<String, Object> serializeStyle(@NotNull HologramStyle style) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("lineHeight", style.lineHeightOverride());
        dto.put("scale", style.scaleOverride());
        dto.put("viewRange", style.viewRangeOverride());
        dto.put("lineWidth", style.lineWidthOverride());
        dto.put("shadowed", style.shadowedOverride());
        dto.put("seeThrough", style.seeThroughOverride());
        dto.put("defaultBackground", style.defaultBackgroundOverride());
        dto.put("backgroundColor", style.backgroundColorOverride() == null ? null : String.format("#%08X", style.backgroundColorOverride().asARGB()));
        dto.put("billboard", style.billboardOverride() == null ? null : style.billboardOverride().name());
        dto.put("alignment", style.alignmentOverride() == null ? null : style.alignmentOverride().name());
        return dto;
    }

    private @Nullable Location parseLocation(@NotNull Map<String, Object> body) {
        String worldName = Json.string(body, "world");
        Number x = Json.number(body, "x");
        Number y = Json.number(body, "y");
        Number z = Json.number(body, "z");
        if (worldName == null || x == null || y == null || z == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        Number yaw = Json.number(body, "yaw");
        Number pitch = Json.number(body, "pitch");
        return new Location(world, x.doubleValue(), y.doubleValue(), z.doubleValue(),
                yaw == null ? 0f : yaw.floatValue(),
                pitch == null ? 0f : pitch.floatValue());
    }

    private @NotNull List<String> parseLines(@NotNull Map<String, Object> body) {
        List<Object> list = Json.list(body, "lines");
        if (list == null) return List.of();
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) result.add(Objects.toString(item, ""));
        return result;
    }

    private void applyStyle(@NotNull HologramStyle style, @NotNull Map<String, Object> obj) {
        if (obj.containsKey("reset") && Boolean.TRUE.equals(Json.bool(obj, "reset"))) {
            style.reset();
            return;
        }
        if (obj.containsKey("lineHeight")) {
            Number n = Json.number(obj, "lineHeight");
            if (n != null) style.lineHeight(n.doubleValue());
        }
        if (obj.containsKey("scale")) {
            Number n = Json.number(obj, "scale");
            if (n != null) style.scale(n.floatValue());
        }
        if (obj.containsKey("viewRange")) {
            Number n = Json.number(obj, "viewRange");
            if (n != null) style.viewRange(n.floatValue());
        }
        if (obj.containsKey("lineWidth")) {
            Number n = Json.number(obj, "lineWidth");
            if (n != null) style.lineWidth(n.intValue());
        }
        if (obj.containsKey("shadowed")) {
            Boolean b = Json.bool(obj, "shadowed");
            if (b != null) style.shadowed(b);
        }
        if (obj.containsKey("seeThrough")) {
            Boolean b = Json.bool(obj, "seeThrough");
            if (b != null) style.seeThrough(b);
        }
        if (obj.containsKey("defaultBackground")) {
            Boolean b = Json.bool(obj, "defaultBackground");
            if (b != null) style.defaultBackground(b);
        }
        if (obj.containsKey("backgroundColor")) {
            String hex = Json.string(obj, "backgroundColor");
            if (hex != null && !hex.isBlank()) style.backgroundColor(parseColor(hex));
        }
        if (obj.containsKey("billboard")) {
            String value = Json.string(obj, "billboard");
            if (value != null && !value.isBlank()) {
                style.billboard(Enum.valueOf(Display.Billboard.class, value.toUpperCase(Locale.ROOT)));
            }
        }
        if (obj.containsKey("alignment")) {
            String value = Json.string(obj, "alignment");
            if (value != null && !value.isBlank()) {
                style.alignment(Enum.valueOf(TextDisplay.TextAlignment.class, value.toUpperCase(Locale.ROOT)));
            }
        }
    }

    private @NotNull Color parseColor(@NotNull String raw) {
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() == 6) hex = "ff" + hex;
        if (hex.length() != 8) throw new IllegalArgumentException("backgroundColor must be #RRGGBB or #AARRGGBB.");
        return Color.fromARGB((int) Long.parseLong(hex, 16));
    }

    private <T> T callSync(@NotNull java.util.concurrent.Callable<T> task) {
        Future<T> future = Bukkit.getScheduler().callSyncMethod(plugin, task);
        try {
            return future.get(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while running task on main thread", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (TimeoutException e) {
            throw new RuntimeException("Server is busy; try again.", e);
        }
    }

    private @NotNull Map<String, Object> readJson(@NotNull HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) return Map.of();
            return Json.decodeObject(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private void respondHtml(@NotNull HttpExchange exchange, int status, byte[] body) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void respondJson(@NotNull HttpExchange exchange, int status, @NotNull Object body) throws IOException {
        byte[] data = Json.encode(body).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private void methodNotAllowed(@NotNull HttpExchange exchange) throws IOException {
        respondJson(exchange, 405, Map.of("error", "Method not allowed"));
    }
}
