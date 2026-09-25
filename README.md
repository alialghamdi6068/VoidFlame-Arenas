# VoidFlame-Arenas

Professional arena lifecycle and configuration management for VoidFlame MC.

## Features

- YAML-backed arena persistence.
- Explicit arena list using world + Spawn A + Spawn B.
- Optional automatic discovery of duel/arena worlds.
- Thread-safe arena registry.
- Atomic acquire/release lifecycle to prevent two matches using one arena.
- Enable/disable controls.
- Administrative /arena command with tab completion.
- Bukkit ServicesManager registration for integration with other VoidFlame plugins.

## Configuration

arenas:
  auto-discover-worlds: true
  list:
    - name: Arena-1
      world: duels
      spawn-a:
        x: 0.5
        y: 100
        z: 0.5
        yaw: 90
        pitch: 0
      spawn-b:
        x: 20.5
        y: 100
        z: 0.5
        yaw: -90
        pitch: 0

The manager loads the configured world and both spawn locations at startup. An arena becomes AVAILABLE only when enabled and both spawns are configured.

## Commands

- /arena list
- /arena info <arena>
- /arena create <arena>
- /arena setspawn <arena> <a|b>
- /arena enable <arena>
- /arena disable <arena>
- /arena reset <arena>
- /arena delete <arena>

## Integration

Other VoidFlame plugins can obtain ArenaManager through Bukkit ServicesManager and use:

- find(name)
- all()
- acquireAvailable()
- release(name)

A successful acquireAvailable() transitions an arena to IN_USE. The consuming match system must release it when the match ends or is cancelled.
