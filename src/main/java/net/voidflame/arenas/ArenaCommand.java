package net.voidflame.arenas;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ArenaCommand implements CommandExecutor, TabCompleter {
    private final VoidFlameArenasPlugin plugin;
    private final ArenaManager manager;

    public ArenaCommand(VoidFlameArenasPlugin plugin, ArenaManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(plugin.permission())) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(msg("usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                if (manager.all().isEmpty()) {
                    sender.sendMessage(msg("list-empty"));
                } else {
                    manager.all().forEach(a -> sender.sendMessage(msg("list-entry")
                            .replace("<arena>", a.name())
                            .replace("<world>", a.world().getName())
                            .replace("<state>", a.state().name())
                            .replace("<enabled>", Boolean.toString(a.enabled()))));
                }
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage(msg("usage")); return true; }
                var arena = manager.find(args[1]);
                if (arena.isEmpty()) { sender.sendMessage(msg("not-found").replace("<arena>", args[1])); return true; }
                var a = arena.get();
                sender.sendMessage(msg("info")
                        .replace("<arena>", a.name())
                        .replace("<world>", a.world().getName())
                        .replace("<state>", a.state().name())
                        .replace("<spawnA>", format(a.spawnA()))
                        .replace("<spawnB>", format(a.spawnB())));
            }
            case "create" -> {
                if (!(sender instanceof Player player) || args.length < 2) { sender.sendMessage(msg("usage")); return true; }
                if (!manager.create(args[1], player.getWorld())) sender.sendMessage(msg("already-exists"));
                else sender.sendMessage(msg("created").replace("<arena>", args[1]));
            }
            case "delete" -> {
                if (args.length < 2) { sender.sendMessage(msg("usage")); return true; }
                if (!manager.delete(args[1])) {
                    if (manager.find(args[1]).map(a -> a.state() == ArenaState.IN_USE).orElse(false)) sender.sendMessage(msg("in-use"));
                    else sender.sendMessage(msg("not-found").replace("<arena>", args[1]));
                } else sender.sendMessage(msg("deleted").replace("<arena>", args[1]));
            }
            case "setspawn" -> {
                if (!(sender instanceof Player player) || args.length < 3) { sender.sendMessage(msg("usage")); return true; }
                boolean first = args[2].equalsIgnoreCase("a") || args[2].equalsIgnoreCase("1");
                boolean second = args[2].equalsIgnoreCase("b") || args[2].equalsIgnoreCase("2");
                if (!first && !second) { sender.sendMessage(msg("usage")); return true; }
                if (!manager.setSpawn(args[1], first, player.getLocation())) {
                    sender.sendMessage(msg("not-found").replace("<arena>", args[1]));
                } else sender.sendMessage(msg("spawn-set").replace("<spawn>", first ? "A" : "B").replace("<arena>", args[1]));
            }
            case "enable", "disable" -> {
                if (args.length < 2) { sender.sendMessage(msg("usage")); return true; }
                boolean enabled = sub.equals("enable");
                if (!manager.setEnabled(args[1], enabled)) sender.sendMessage(msg("in-use"));
                else sender.sendMessage(msg(enabled ? "enabled" : "disabled").replace("<arena>", args[1]));
            }
            case "reset" -> {
                if (args.length < 2) { sender.sendMessage(msg("usage")); return true; }
                if (!manager.release(args[1])) sender.sendMessage(msg("reset-blocked"));
                else sender.sendMessage(msg("reset").replace("<arena>", args[1]));
            }
            default -> sender.sendMessage(msg("usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("list", "info", "create", "delete", "setspawn", "enable", "disable", "reset"), args[0]);
        if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            return filter(manager.all().stream().map(Arena::name).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setspawn")) return filter(List.of("a", "b"), args[2]);
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))).collect(Collectors.toList());
    }

    private String format(org.bukkit.Location l) {
        return l == null ? "unset" : String.format(Locale.ROOT, "%.2f, %.2f, %.2f", l.getX(), l.getY(), l.getZ());
    }

    private String msg(String key) {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix", "") + plugin.getConfig().getString("messages." + key, key));
    }
}
