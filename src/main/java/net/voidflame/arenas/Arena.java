package net.voidflame.arenas;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public final class Arena {
    private final String name;
    private final World world;
    private Location spawnA;
    private Location spawnB;
    private ArenaState state;
    private boolean enabled;

    public Arena(String name, World world, Location spawnA, Location spawnB, boolean enabled) {
        this.name = Objects.requireNonNull(name).trim();
        if (this.name.isBlank()) throw new IllegalArgumentException("Arena name cannot be blank");
        this.world = Objects.requireNonNull(world);
        this.spawnA = cloneLocation(spawnA);
        this.spawnB = cloneLocation(spawnB);
        this.enabled = enabled;
        this.state = enabled ? ArenaState.AVAILABLE : ArenaState.DISABLED;
    }

    public String name() { return name; }
    public World world() { return world; }
    public synchronized Location spawnA() { return cloneLocation(spawnA); }
    public synchronized Location spawnB() { return cloneLocation(spawnB); }
    public synchronized ArenaState state() { return state; }
    public synchronized boolean enabled() { return enabled; }

    public synchronized void setSpawnA(Location location) {
        requireSameWorld(location);
        spawnA = cloneLocation(location);
    }

    public synchronized void setSpawnB(Location location) {
        requireSameWorld(location);
        spawnB = cloneLocation(location);
    }

    public synchronized boolean isConfigured() {
        return spawnA != null && spawnB != null;
    }

    public synchronized boolean acquire() {
        if (!enabled || state != ArenaState.AVAILABLE || !isConfigured()) return false;
        state = ArenaState.IN_USE;
        return true;
    }

    public synchronized void release() {
        state = enabled ? ArenaState.AVAILABLE : ArenaState.DISABLED;
    }

    public synchronized void disable() {
        enabled = false;
        state = ArenaState.DISABLED;
    }

    public synchronized void enable() {
        enabled = true;
        if (state != ArenaState.IN_USE) state = ArenaState.AVAILABLE;
    }

    public synchronized boolean isReady() {
        return enabled && state == ArenaState.AVAILABLE && isConfigured();
    }

    private void requireSameWorld(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(world)) {
            throw new IllegalArgumentException("Spawn must belong to arena world");
        }
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }
}
