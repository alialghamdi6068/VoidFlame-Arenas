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
        this.name = Objects.requireNonNull(name);
        this.world = Objects.requireNonNull(world);
        this.spawnA = spawnA == null ? null : spawnA.clone();
        this.spawnB = spawnB == null ? null : spawnB.clone();
        this.enabled = enabled;
        this.state = enabled ? ArenaState.AVAILABLE : ArenaState.DISABLED;
    }

    public String name() { return name; }
    public World world() { return world; }
    public Location spawnA() { return spawnA == null ? null : spawnA.clone(); }
    public Location spawnB() { return spawnB == null ? null : spawnB.clone(); }
    public ArenaState state() { return state; }
    public boolean enabled() { return enabled; }

    public void setSpawnA(Location location) { spawnA = location == null ? null : location.clone(); }
    public void setSpawnB(Location location) { spawnB = location == null ? null : location.clone(); }

    public boolean isConfigured() {
        return spawnA != null && spawnB != null;
    }

    public boolean acquire() {
        if (!enabled || state != ArenaState.AVAILABLE || !isConfigured()) return false;
        state = ArenaState.IN_USE;
        return true;
    }

    public void release() {
        state = enabled ? ArenaState.AVAILABLE : ArenaState.DISABLED;
    }

    public void disable() {
        enabled = false;
        state = ArenaState.DISABLED;
    }

    public void enable() {
        enabled = true;
        state = state == ArenaState.IN_USE ? ArenaState.IN_USE : ArenaState.AVAILABLE;
    }

    public Arena copy() {
        Arena copy = new Arena(name, world, spawnA, spawnB, enabled);
        copy.state = state;
        return copy;
    }
}
