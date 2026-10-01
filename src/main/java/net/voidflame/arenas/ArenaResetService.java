package net.voidflame.arenas;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.File;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class ArenaResetService {
    private final VoidFlameArenasPlugin plugin;

    public ArenaResetService(VoidFlameArenasPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        new File(plugin.getDataFolder(), "snapshots").mkdirs();
    }

    public boolean available() {
        return true;
    }

    public boolean prepare(Arena arena) {
        if (arena == null || !arena.isConfigured()) return false;
        File file = snapshotFile(arena);
        if (file.isFile() && file.length() > 0) return true;
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, () -> capture(arena)).get();
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not prepare native arena snapshot for " + arena.name() + ": " + ex.getMessage());
            return false;
        }
    }

    public CompletableFuture<Boolean> reset(Arena arena) {
        if (arena == null || !arena.isConfigured()) return CompletableFuture.completedFuture(false);
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean success = Boolean.TRUE.equals(Bukkit.getScheduler().callSyncMethod(plugin, () -> restore(arena)).get());
                audit(success, arena);
                return success;
            } catch (Exception ex) {
                plugin.getLogger().severe("Native arena reset failed for " + arena.name() + ": " + ex.getMessage());
                audit(false, arena);
                return false;
            }
        });
    }

    private boolean capture(Arena arena) {
        try {
            Location[] bounds = bounds(arena);
            StructureManager manager = Bukkit.getStructureManager();
            Structure structure = manager.createStructure();
            structure.fill(bounds[0], bounds[1], false);
            File file = snapshotFile(arena);
            manager.saveStructure(file, structure);
            return file.isFile() && file.length() > 0;
        } catch (Exception ex) {
            plugin.getLogger().warning("Snapshot capture failed for " + arena.name() + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean restore(Arena arena) {
        Location[] bounds = bounds(arena);
        clearEntities(arena);
        File file = snapshotFile(arena);
        if (!file.isFile() || file.length() == 0) {
            if (!capture(arena)) return false;
        }

        try {
            Structure structure = Bukkit.getStructureManager().loadStructure(file);
            if (structure == null) return false;
            structure.place(bounds[0], false, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());
            return verify(arena, structure, bounds[0]);
        } catch (Exception ex) {
            plugin.getLogger().warning("Snapshot restore failed for " + arena.name() + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean verify(Arena arena, Structure structure, Location origin) {
        if (!plugin.getConfig().getBoolean("settings.verify-after-reset", true)) return true;
        var size = structure.getSize();
        int sampleSize = Math.max(1, plugin.getConfig().getInt("settings.verify-sample-size", 128));
        int total = Math.max(1, size.getBlockX() * size.getBlockY() * size.getBlockZ());
        int stride = Math.max(1, total / sampleSize);
        int checked = 0;

        for (int index = 0; index < total && checked < sampleSize; index += stride) {
            int x = index % size.getBlockX();
            int yz = index / size.getBlockX();
            int z = yz % size.getBlockZ();
            int y = yz / size.getBlockZ();
            arena.world().getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
            checked++;
        }
        return checked > 0;
    }

    private Location[] bounds(Arena arena) {
        Location a = arena.spawnA();
        Location b = arena.spawnB();
        World world = arena.world();

        int paddingXZ = Math.max(8, plugin.getConfig().getInt("settings.snapshot-padding-xz", 32));
        int paddingY = Math.max(4, plugin.getConfig().getInt("settings.snapshot-padding-y", 16));

        int minX = Math.min(a.getBlockX(), b.getBlockX()) - paddingXZ;
        int maxX = Math.max(a.getBlockX(), b.getBlockX()) + paddingXZ;
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ()) - paddingXZ;
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ()) + paddingXZ;
        int minY = Math.max(world.getMinHeight(), Math.min(a.getBlockY(), b.getBlockY()) - paddingY);
        int maxY = Math.min(world.getMaxHeight() - 1, Math.max(a.getBlockY(), b.getBlockY()) + paddingY);

        return new Location[]{
                new Location(world, minX, minY, minZ),
                new Location(world, maxX, maxY, maxZ)
        };
    }

    private void audit(boolean success, Arena arena) {
        var registration = Bukkit.getServicesManager().getRegistration(net.voidflame.core.api.AuditLogService.class);
        if (registration == null || registration.getProvider() == null) return;
        registration.getProvider().log("SYSTEM", success ? "ARENA_RESET_SUCCESS" : "ARENA_RESET_FAILURE",
                arena.name(), "world=" + arena.world().getName());
    }

    private File snapshotFile(Arena arena) {
        return new File(plugin.getDataFolder(), "snapshots/" + arena.name().replaceAll("[^a-zA-Z0-9._-]", "_") + ".nbt");
    }

    private void clearEntities(Arena arena) {
        Location center = arena.spawnA();
        double radiusSquared = Math.pow(plugin.getConfig().getDouble("settings.reset-entity-radius", 64.0), 2);

        for (Entity entity : center.getWorld().getEntities()) {
            if (entity instanceof Player) continue;
            if (entity.getLocation().distanceSquared(center) > radiusSquared) continue;
            if (entity instanceof org.bukkit.entity.Item && !plugin.getConfig().getBoolean("settings.cleanup-items", true)) continue;
            if ((entity instanceof org.bukkit.entity.Projectile || entity instanceof org.bukkit.entity.Firework)
                    && !plugin.getConfig().getBoolean("settings.cleanup-projectiles", true)) continue;
            if (entity instanceof org.bukkit.entity.ThrownPotion && !plugin.getConfig().getBoolean("settings.cleanup-potions", true)) continue;
            if (!plugin.getConfig().getBoolean("settings.cleanup-non-player-entities", true)
                    && !(entity instanceof org.bukkit.entity.Item)
                    && !(entity instanceof org.bukkit.entity.Projectile)
                    && !(entity instanceof org.bukkit.entity.ThrownPotion)
                    && !(entity instanceof org.bukkit.entity.Firework)) continue;
            entity.remove();
        }
    }
}
