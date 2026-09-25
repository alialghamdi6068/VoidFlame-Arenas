package net.voidflame.arenas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaManager {
    private final JavaPlugin plugin;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section != null) {
            var list = section.getMapList("list");
            for (var raw : list) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                String name = String.valueOf(map.getOrDefault("name", ""));
                World world = Bukkit.getWorld(String.valueOf(map.getOrDefault("world", "")));
                if (world == null) {
                    plugin.getLogger().warning("Skipping arena '" + name + "': world is not loaded.");
                    continue;
                }
                Location a = readMapLocation(map.get("spawn-a"), world);
                Location b = readMapLocation(map.get("spawn-b"), world);
                boolean enabled = !map.containsKey("enabled") || Boolean.parseBoolean(String.valueOf(map.get("enabled")));
                if (!name.isBlank()) arenas.put(normalize(name), new Arena(name, world, a, b, enabled));
            }
        }
        if (config.getBoolean("settings.auto-discover-worlds", false)) {
            discoverWorlds();
        }
        save();
    }

    public void discoverWorlds() {
        String prefix = plugin.getConfig().getString("settings.discovery-name-prefix", "Arena-");
        int index = 1;
        for (World world : Bukkit.getWorlds()) {
            String name = prefix + index++;
            while (arenas.containsKey(normalize(name))) name = prefix + index++;
            if (world.getName().toLowerCase(Locale.ROOT).contains("duel")
                    || world.getName().toLowerCase(Locale.ROOT).contains("arena")) {
                arenas.putIfAbsent(normalize(world.getName()),
                        new Arena(world.getName(), world, null, null, true));
            }
        }
        save();
    }

    public Collection<Arena> all() {
        return arenas.values().stream().sorted(Comparator.comparing(Arena::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Optional<Arena> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(arenas.get(normalize(name)));
    }

    public Optional<Arena> acquireAvailable() {
        return arenas.values().stream()
                .sorted(Comparator.comparing(Arena::name, String.CASE_INSENSITIVE_ORDER))
                .filter(Arena::isConfigured)
                .filter(a -> a.acquire())
                .findFirst();
    }

    public boolean create(String name, World world) {
        if (name == null || name.isBlank() || world == null || arenas.containsKey(normalize(name))) return false;
        arenas.put(normalize(name), new Arena(name.trim(), world, null, null, true));
        save();
        return true;
    }

    public boolean delete(String name) {
        Optional<Arena> found = find(name);
        if (found.isEmpty() || found.get().state() == ArenaState.IN_USE) return false;
        arenas.remove(normalize(name));
        save();
        return true;
    }

    public boolean setSpawn(String name, boolean first, Location location) {
        Optional<Arena> found = find(name);
        if (found.isEmpty() || location == null || found.get().state() == ArenaState.IN_USE) return false;
        if (first) found.get().setSpawnA(location);
        else found.get().setSpawnB(location);
        save();
        return true;
    }

    public boolean setEnabled(String name, boolean enabled) {
        Optional<Arena> found = find(name);
        if (found.isEmpty()) return false;
        if (enabled) found.get().enable();
        else {
            if (found.get().state() == ArenaState.IN_USE) return false;
            found.get().disable();
        }
        save();
        return true;
    }

    public boolean release(String name) {
        Optional<Arena> found = find(name);
        if (found.isEmpty() || found.get().state() != ArenaState.IN_USE) return false;
        found.get().release();
        save();
        return true;
    }

    public void releaseAll() {
        arenas.values().forEach(Arena::release);
        save();
    }

    public void save() {
        if (!plugin.getConfig().getBoolean("settings.persist-to-file", true)) return;
        List<Map<String, Object>> list = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", arena.name());
            data.put("world", arena.world().getName());
            data.put("enabled", arena.enabled());
            data.put("spawn-a", toMap(arena.spawnA()));
            data.put("spawn-b", toMap(arena.spawnB()));
            list.add(data);
        }
        plugin.getConfig().set("arenas.list", list);
        plugin.saveConfig();
    }

    private Location readLocation(ConfigurationSection section, World world) {
        if (section == null) return null;
        return new Location(world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    private Location readMapLocation(Object raw, World world) {
        if (!(raw instanceof Map<?, ?> map)) return null;
        return new Location(world,
                number(map.get("x")), number(map.get("y")), number(map.get("z")),
                (float) number(map.get("yaw")), (float) number(map.get("pitch")));
    }

    private Map<String, Object> toMap(Location location) {
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
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (Exception ignored) { return 0.0; }
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
