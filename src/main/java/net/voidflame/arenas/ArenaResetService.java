package net.voidflame.arenas;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Optional WorldEdit-backed arena restoration.
 * The plugin remains loadable without WorldEdit, but an arena with a template
 * is never marked available unless restoration succeeds.
 */
public final class ArenaResetService {
    private final VoidFlameArenasPlugin plugin;

    public ArenaResetService(VoidFlameArenasPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        try {
            Class.forName("com.sk89q.worldedit.WorldEdit");
            Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Class.forName("com.sk89q.worldedit.extent.clipboard.ClipboardFormats");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public CompletableFuture<Boolean> reset(Arena arena) {
        if (!arena.hasTemplate()) return CompletableFuture.completedFuture(false);
        if (!available()) {
            plugin.getLogger().severe("Arena " + arena.name() + " has a template but WorldEdit is not installed.");
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Bukkit.getScheduler().callSyncMethod(plugin, () -> paste(arena)).get();
                return true;
            } catch (Exception ex) {
                plugin.getLogger().severe("Arena reset failed for " + arena.name() + ": " + ex.getMessage());
                return false;
            }
        });
    }

    private boolean paste(Arena arena) throws Exception {
        World world = arena.world();
        File file = new File(plugin.getDataFolder(), "templates/" + arena.template());
        if (!file.isFile()) throw new IllegalStateException("Template not found: " + file.getPath());

        Class<?> worldEditClass = Class.forName("com.sk89q.worldedit.WorldEdit");
        Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Class<?> formatsClass = Class.forName("com.sk89q.worldedit.extent.clipboard.ClipboardFormats");
        Class<?> formatClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
        Class<?> readerClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardReader");
        Class<?> clipboardClass = Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard");
        Class<?> holderClass = Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
        Class<?> vectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
        Class<?> operationsClass = Class.forName("com.sk89q.worldedit.function.operation.Operations");

        Object format = formatsClass.getMethod("findByFile", File.class).invoke(null, file);
        if (format == null) throw new IllegalStateException("Unsupported schematic format: " + file.getName());

        Object clipboard;
        try (var input = new java.io.FileInputStream(file)) {
            Object reader = formatClass.getMethod("getReader", java.io.InputStream.class).invoke(format, input);
            try {
                clipboard = readerClass.getMethod("read").invoke(reader);
            } finally {
                readerClass.getMethod("close").invoke(reader);
            }
        }

        Object weWorld = adapterClass.getMethod("adapt", World.class).invoke(null, world);
        Object worldEdit = worldEditClass.getMethod("getInstance").invoke(null);
        Object editSession = worldEditClass.getMethod("newEditSession", Class.forName("com.sk89q.worldedit.world.World")).invoke(worldEdit, weWorld);

        clearEntities(arena);

        Object holder = holderClass.getConstructor(clipboardClass).newInstance(clipboard);
        Object pasteBuilder = holderClass.getMethod("createPaste", Class.forName("com.sk89q.worldedit.EditSession")).invoke(holder, editSession);
        Object vector = vectorClass.getMethod("at", int.class, int.class, int.class)
                .invoke(null, arena.templateX(), arena.templateY(), arena.templateZ());
        pasteBuilder = pasteBuilder.getClass().getMethod("to", vectorClass).invoke(pasteBuilder, vector);
        pasteBuilder = pasteBuilder.getClass().getMethod("ignoreAirBlocks", boolean.class).invoke(pasteBuilder, false);
        Object operation = pasteBuilder.getClass().getMethod("build").invoke(pasteBuilder);
        operationsClass.getMethod("complete", Class.forName("com.sk89q.worldedit.function.operation.Operation"))
                .invoke(null, operation);
        editSession.getClass().getMethod("close").invoke(editSession);
        if (plugin.getConfig().getBoolean("settings.verify-after-reset", true)) {
            return verifyPaste(arena, clipboard, vector, vectorClass);
        }
        return true;
    }


    private boolean verifyPaste(Arena arena, Object clipboard, Object pasteVector, Class<?> vectorClass) {
        try {
            int sampleSize = Math.max(1, plugin.getConfig().getInt("settings.verify-sample-size", 256));
            Object dimensions = clipboard.getClass().getMethod("getDimensions").invoke(clipboard);
            Object origin = clipboard.getClass().getMethod("getOrigin").invoke(clipboard);
            int dx = (int) dimensions.getClass().getMethod("getX").invoke(dimensions);
            int dy = (int) dimensions.getClass().getMethod("getY").invoke(dimensions);
            int dz = (int) dimensions.getClass().getMethod("getZ").invoke(dimensions);
            int ox = (int) origin.getClass().getMethod("getBlockX").invoke(origin);
            int oy = (int) origin.getClass().getMethod("getBlockY").invoke(origin);
            int oz = (int) origin.getClass().getMethod("getBlockZ").invoke(origin);
            var world = arena.world();
            int checked = 0;
            int total = Math.max(1, dx * dy * dz);
            int stride = Math.max(1, total / sampleSize);
            for (int index = 0; index < total && checked < sampleSize; index += stride) {
                int x = index % dx;
                int yz = index / dx;
                int z = yz % dz;
                int y = yz / dz;
                Object point = vectorClass.getMethod("at", int.class, int.class, int.class)
                        .invoke(null, arena.templateX() + x - ox, arena.templateY() + y - oy, arena.templateZ() + z - oz);
                Object expectedBlock = clipboard.getClass().getMethod("getBlock", vectorClass).invoke(clipboard, vectorClass.getMethod("at", int.class, int.class, int.class)
                        .invoke(null, x, y, z));
                Object expectedType = expectedBlock.getClass().getMethod("getBlockType").invoke(expectedBlock);
                Object expectedMaterial = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                        .getMethod("adapt", Class.forName("com.sk89q.worldedit.world.block.BlockType"))
                        .invoke(null, expectedType);
                if (expectedMaterial instanceof org.bukkit.Material material
                        && world.getBlockAt((int) point.getClass().getMethod("getBlockX").invoke(point),
                        (int) point.getClass().getMethod("getBlockY").invoke(point),
                        (int) point.getClass().getMethod("getBlockZ").invoke(point)).getType() != material) {
                    return false;
                }
                checked++;
            }
            return checked > 0;
        } catch (Exception ex) {
            plugin.getLogger().warning("Arena verification failed for " + arena.name() + ": " + ex.getMessage());
            return false;
        }
    }

    private void clearEntities(Arena arena) {
        var center = arena.spawnA();
        double radius = plugin.getConfig().getDouble("settings.reset-entity-radius", 64.0);
        double radiusSquared = radius * radius;
        for (Entity entity : center.getWorld().getEntities()) {
            if (entity instanceof Player) continue;
            if (entity.getLocation().distanceSquared(center) <= radiusSquared) entity.remove();
        }
    }
}
