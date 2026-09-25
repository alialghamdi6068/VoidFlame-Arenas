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
            for (String name : section.getKeys(false)) {
                ConfigurationSection arena = section.getConfigurationSection(name);
                if (arena == null) continue;
                World world = Bukkit.getWorld(arena.getString("world", ""));
                if (world == null) {
                    plugin.getLogger().warning("Skipping arena '" + name + "': world is not loaded.");
                    continue;
                }
                Location a = readLocation(arena.getConfigurationSection("spawn-a"), world);
                Location b = readLocation(arena.getConfigurationSection("spawn-b"), world);
                arenas.put(normalize(name), new Arena(name, world, a, b, arena.getBoolean("enabled", true)));
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
        plugin.getConfig().set("arenas", null);
        for (Arena arena : arenas.values()) {
            String base = "arenas." + arena.name();
            plugin.getConfig().set(base + ".world", arena.world().getName());
            plugin.getConfig().set(base + ".enabled", arena.enabled());
            writeLocation(base + ".spawn-a", arena.spawnA());
            writeLocation(base + ".spawn-b", arena.spawnB());
        }
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

    private void writeLocation(String path, Location location) {
        if (location == null) {
            plugin.getConfig().set(path, null);
            return;
        }
        plugin.getConfig().set(path + ".x", location.getX());
        plugin.getConfig().set(path + ".y", location.getY());
        plugin.getConfig().set(path + ".z", location.getZ());
        plugin.getConfig().set(path + ".yaw", location.getYaw());
        plugin.getConfig().set(path + ".pitch", location.getPitch());
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
