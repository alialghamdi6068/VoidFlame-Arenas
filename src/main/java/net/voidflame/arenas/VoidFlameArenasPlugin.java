package net.voidflame.arenas;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoidFlameArenasPlugin extends JavaPlugin {
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        arenaManager = new ArenaManager(this);
        arenaManager.load();

        PluginCommand command = getCommand("arena");
        if (command != null) {
            ArenaCommand handler = new ArenaCommand(this, arenaManager);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        getServer().getServicesManager().register(ArenaManager.class, arenaManager, this, ServicePriority.Normal);
        getLogger().info("VoidFlame-Arenas enabled with " + arenaManager.all().size() + " arenas.");
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) arenaManager.releaseAll();
    }

    public ArenaManager arenaManager() {
        return arenaManager;
    }

    public String permission() {
        return getConfig().getString("settings.command-permission", "voidflame.arenas.admin");
    }
}
