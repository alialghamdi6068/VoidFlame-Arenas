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
    private String template;
    private int templateX, templateY, templateZ;

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
    public synchronized String template() { return template; }
    public synchronized int templateX() { return templateX; }
    public synchronized int templateY() { return templateY; }
    public synchronized int templateZ() { return templateZ; }
    public synchronized boolean hasTemplate() { return template != null && !template.isBlank(); }
    public synchronized void setTemplate(String template, Location paste) {
        if (template == null || template.isBlank() || paste == null || paste.getWorld() == null || !paste.getWorld().equals(world)) {
            throw new IllegalArgumentException("Template and paste location must be valid and belong to the arena world");
        }
        this.template = template.trim(); this.templateX = paste.getBlockX(); this.templateY = paste.getBlockY(); this.templateZ = paste.getBlockZ();
    }

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

    public synchronized void beginReset() {
        if (!enabled) { state = ArenaState.DISABLED; return; }
        state = ArenaState.RESETTING;
    }

    public synchronized void finishReset(boolean success) {
        state = success && enabled ? ArenaState.AVAILABLE : ArenaState.DISABLED;
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
