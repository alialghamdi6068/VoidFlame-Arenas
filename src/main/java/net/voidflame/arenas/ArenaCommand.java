package net.voidflame.arenas;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class ArenaCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "list", "info", "create", "delete", "setspawn", "enable", "disable", "reset", "reload");

    private final VoidFlameArenasPlugin plugin;
    private final ArenaManager manager;

    public ArenaCommand(VoidFlameArenasPlugin plugin, ArenaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(plugin.permission())) { sender.sendMessage(msg("no-permission")); return true; }
        if (args.length == 0) { sender.sendMessage(msg("usage")); return true; }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "create" -> create(sender, args);
            case "delete" -> action(sender, args, "delete");
            case "setspawn" -> setSpawn(sender, args);
            case "enable" -> action(sender, args, "enable");
            case "disable" -> action(sender, args, "disable");
            case "reset" -> reset(sender, args);
            case "reload" -> reload(sender);
            default -> sender.sendMessage(msg("usage"));
        }
        return true;
    }

    private void list(CommandSender sender) {
        var arenas = manager.all();
        if (arenas.isEmpty()) { sender.sendMessage(msg("list-empty")); return; }
        sender.sendMessage(msg("list-header")
                .replace("<total>", String.valueOf(arenas.size()))
                .replace("<available>", String.valueOf(manager.availableCount()))
                .replace("<used>", String.valueOf(manager.inUseCount()))
                .replace("<disabled>", String.valueOf(manager.disabledCount())));
        arenas.forEach(a -> sender.sendMessage(msg("list-entry")
                .replace("<arena>", a.name())
                .replace("<world>", a.world().getName())
                .replace("<state>", a.state().name())
                .replace("<ready>", Boolean.toString(a.isReady()))));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(msg("usage")); return; }
        var found = manager.find(args[1]);
        if (found.isEmpty()) { sender.sendMessage(msg("not-found").replace("<arena>", args[1])); return; }
        var a = found.get();
        sender.sendMessage(msg("info")
                .replace("<arena>", a.name()).replace("<world>", a.world().getName())
                .replace("<state>", a.state().name()).replace("<ready>", Boolean.toString(a.isReady()))
                .replace("<spawnA>", format(a.spawnA())).replace("<spawnB>", format(a.spawnB())));
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(msg("player-only")); return; }
        if (args.length < 2) { sender.sendMessage(msg("usage")); return; }
        if (!manager.create(args[1], player.getWorld())) {
            sender.sendMessage(msg("already-exists")); return;
        }
        sender.sendMessage(msg("created").replace("<arena>", args[1]));
    }

    private void action(CommandSender sender, String[] args, String action) {
        if (args.length < 2) { sender.sendMessage(msg("usage")); return; }
        String name = args[1];
        boolean success = switch (action) {
            case "delete" -> manager.delete(name);
            case "enable" -> manager.setEnabled(name, true);
            case "disable" -> manager.setEnabled(name, false);
            default -> false;
        };
        if (success) { sender.sendMessage(msg(action.equals("delete") ? "deleted" : action).replace("<arena>", name)); return; }

        var found = manager.find(name);
        if (found.isPresent() && found.get().state() == ArenaState.IN_USE) sender.sendMessage(msg("in-use"));
        else sender.sendMessage(msg("not-found").replace("<arena>", name));
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(msg("player-only")); return; }
        if (args.length < 3) { sender.sendMessage(msg("usage")); return; }
        boolean first = args[2].equalsIgnoreCase("a") || args[2].equals("1");
        boolean second = args[2].equalsIgnoreCase("b") || args[2].equals("2");
        if (!first && !second) { sender.sendMessage(msg("usage")); return; }
        if (!manager.setSpawn(args[1], first, player.getLocation())) {
            sender.sendMessage(msg("invalid-spawn")); return;
        }
        sender.sendMessage(msg("spawn-set").replace("<spawn>", first ? "A" : "B").replace("<arena>", args[1]));
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(msg("usage")); return; }
        if (!manager.reset(args[1])) { sender.sendMessage(msg("reset-blocked")); return; }
        sender.sendMessage(msg("reset").replace("<arena>", args[1]));
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        manager.load();
        sender.sendMessage(msg("reload").replace("<count>", String.valueOf(manager.all().size())));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(SUBCOMMANDS, args[0]);
        if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            return filter(manager.all().stream().map(Arena::name).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setspawn")) return filter(List.of("a", "b"), args[2]);
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private String format(Location l) {
        return l == null ? "unset" : String.format(Locale.ROOT, "%.2f, %.2f, %.2f (%.1f/%.1f)",
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    private String msg(String key) {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix", "") +
                plugin.getConfig().getString("messages." + key, key));
    }
}
