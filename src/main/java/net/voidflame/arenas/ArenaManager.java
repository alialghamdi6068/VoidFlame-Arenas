package net.voidflame.arenas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaManager {
    private final JavaPlugin plugin;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();
    private final ArenaResetService resetService;

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.resetService = new ArenaResetService((VoidFlameArenasPlugin) plugin);
    }

    public synchronized void load() {
        arenas.clear();
        FileConfiguration config = plugin.getConfig();
        for (Map<?, ?> raw : config.getMapList("arenas.list")) {
            String name = string(raw.get("name"));
            String worldName = string(raw.get("world"));
            if (name.isBlank() || worldName.isBlank()) { warn("Ignoring arena with missing name/world."); continue; }

            World world = Bukkit.getWorld(worldName);
            if (world == null) { warn("Skipping '" + name + "': world '" + worldName + "' is not loaded."); continue; }

            Location a = readLocation(raw.get("spawn-a"), world);
            Location b = readLocation(raw.get("spawn-b"), world);
            boolean enabled = !raw.containsKey("enabled") || Boolean.parseBoolean(string(raw.get("enabled")));

            try {
                Arena arena = new Arena(name, world, a, b, enabled);
                String template = string(raw.get("template"));
                Location paste = readLocation(raw.get("template-paste"), world);
                if (!template.isBlank() && paste != null) {
                    arena.setTemplate(template, paste);
                }
                register(arena);
            } catch (IllegalArgumentException ex) {
                warn("Skipping invalid arena '" + name + "': " + ex.getMessage());
            }
        }

        if (config.getBoolean("arenas.auto-discover-worlds", false)) discoverWorlds();
        save();
    }

    public synchronized void discoverWorlds() {
        String prefix = plugin.getConfig().getString("settings.discovery-name-prefix", "Arena-");
        Set<String> existingWorlds = new HashSet<>();
        arenas.values().forEach(a -> existingWorlds.add(a.world().getName().toLowerCase(Locale.ROOT)));
        int index = 1;

        for (World world : Bukkit.getWorlds()) {
            if (existingWorlds.contains(world.getName().toLowerCase(Locale.ROOT)) || !matchesDiscovery(world.getName())) continue;
            String name;
            do { name = prefix + index++; } while (contains(name));
            register(new Arena(name, world, null, null, true));
            existingWorlds.add(world.getName().toLowerCase(Locale.ROOT));
        }
        save();
    }

    public Collection<Arena> all() {
        return arenas.values().stream()
                .sorted(Comparator.comparing(Arena::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<Arena> find(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(arenas.get(normalize(name)));
    }

    public synchronized boolean reload() {
        if (inUseCount() > 0 || resettingCount() > 0) return false;
        plugin.reloadConfig();
        load();
        return true;
    }

    public synchronized Optional<Arena> acquireAvailable() {
        return all().stream()
                .filter(Arena::isReady)
                .filter(Arena::acquire)
                .findFirst();
    }

    public synchronized boolean create(String name, World world) {
        if (name == null || name.isBlank() || world == null || contains(name)) return false;
        register(new Arena(name, world, null, null, true));
        save();
        return true;
    }

    public synchronized boolean delete(String name) {
        Optional<Arena> found = find(name);
        if (found.isEmpty()) return false;
        ArenaState state = found.get().state();
        if (state == ArenaState.IN_USE || state == ArenaState.RESETTING) return false;
        arenas.remove(normalize(name));
        save();
        return true;
    }

    public synchronized boolean setSpawn(String name, boolean first, Location location) {
        Optional<Arena> found = find(name);
        if (found.isEmpty() || location == null || found.get().state() == ArenaState.IN_USE
                || found.get().state() == ArenaState.RESETTING) return false;
        try {
            if (first) found.get().setSpawnA(location); else found.get().setSpawnB(location);
        } catch (IllegalArgumentException ex) { return false; }
        save();
        return true;
    }

    public synchronized boolean setEnabled(String name, boolean enabled) {
        Optional<Arena> found = find(name);
        if (found.isEmpty()) return false;
        Arena arena = found.get();
        if (!enabled && (arena.state() == ArenaState.IN_USE || arena.state() == ArenaState.RESETTING)) return false;
        if (enabled) arena.enable(); else arena.disable();
        save();
        return true;
    }

    /**
     * Performs a real arena restoration. An arena is never made available until
     * the configured template has been pasted successfully.
     */
    public CompletableFuture<Boolean> reset(Arena arena) {
        if (arena == null) return CompletableFuture.completedFuture(false);

        synchronized (this) {
            if (arena.state() != ArenaState.IN_USE) return CompletableFuture.completedFuture(false);
            if (!arena.hasTemplate()) {
                arena.finishReset(false);
                save();
                warn("Arena '" + arena.name() + "' has no template; disabled after match instead of being reused.");
                return CompletableFuture.completedFuture(false);
            }
            arena.beginReset();
            save();
        }

        return resetService.reset(arena).handle((success, error) -> {
            boolean ok = Boolean.TRUE.equals(success) && error == null;
            synchronized (this) {
                arena.finishReset(ok);
                save();
            }
            if (!ok) {
                warn("Arena '" + arena.name() + "' failed reset and has been disabled: " +
                        (error == null ? "template restore returned false" : error.getMessage()));
            }
            return ok;
        });
    }

    public synchronized boolean release(String name) {
        Optional<Arena> found = find(name);
        if (found.isEmpty()) return false;
        return release(found.get());
    }

    /**
     * Legacy administrative release. Match completion must use reset(Arena).
     */
    public synchronized boolean release(Arena arena) {
        if (arena == null || arena.state() != ArenaState.IN_USE) return false;
        arena.release();
        save();
        return true;
    }

    public synchronized boolean reset(String name) {
        Optional<Arena> found = find(name);
        if (found.isEmpty()) return false;
        return reset(found.get()).join();
    }

    public synchronized void releaseAll() {
        arenas.values().stream()
                .filter(a -> a.state() == ArenaState.IN_USE)
                .forEach(Arena::release);
        save();
    }

    public boolean resetServiceAvailable() { return resetService.available(); }

    public void save() {
        if (!plugin.getConfig().getBoolean("settings.persist-to-file", true)) return;
        List<Map<String, Object>> list = new ArrayList<>();
        for (Arena arena : all()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", arena.name());
            data.put("world", arena.world().getName());
            data.put("enabled", arena.enabled());
            data.put("spawn-a", writeLocation(arena.spawnA()));
            data.put("spawn-b", writeLocation(arena.spawnB()));

            if (arena.hasTemplate()) {
                data.put("template", arena.template());
                Map<String, Object> paste = new LinkedHashMap<>();
                paste.put("x", arena.templateX());
                paste.put("y", arena.templateY());
                paste.put("z", arena.templateZ());
                paste.put("yaw", 0f);
                paste.put("pitch", 0f);
                data.put("template-paste", paste);
            }
            list.add(data);
        }
        plugin.getConfig().set("arenas.list", list);
        plugin.saveConfig();
    }

    public long availableCount() { return all().stream().filter(Arena::isReady).count(); }
    public long inUseCount() { return all().stream().filter(a -> a.state() == ArenaState.IN_USE).count(); }
    public long resettingCount() { return all().stream().filter(a -> a.state() == ArenaState.RESETTING).count(); }
    public long disabledCount() { return all().stream().filter(a -> a.state() == ArenaState.DISABLED).count(); }

    private void register(Arena arena) {
        if (arenas.putIfAbsent(normalize(arena.name()), arena) != null) {
            warn("Duplicate arena ignored: " + arena.name());
        }
    }

    private boolean contains(String name) { return arenas.containsKey(normalize(name)); }

    private boolean matchesDiscovery(String worldName) {
        String lower = worldName.toLowerCase(Locale.ROOT);
        List<String> patterns = plugin.getConfig().getStringList("settings.discovery-world-name-contains");
        if (patterns.isEmpty()) patterns = List.of("duel", "arena");
        return patterns.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(lower::contains);
    }

    private Location readLocation(Object raw, World world) {
        if (!(raw instanceof Map<?, ?> map)) return null;
        try {
            return new Location(world, number(map.get("x")), number(map.get("y")), number(map.get("z")),
                    (float) number(map.get("yaw")), (float) number(map.get("pitch")));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Map<String, Object> writeLocation(Location location) {
        if (location == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    private double number(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }

    private String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private String normalize(String name) { return name.trim().toLowerCase(Locale.ROOT); }
    private void warn(String message) { plugin.getLogger().warning("[Arenas] " + message); }
}
