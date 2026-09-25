package net.voidflame.arenas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArenaStateTest {
    @Test
    void lifecycleStatesAreDefined() {
        assertEquals(ArenaState.AVAILABLE, ArenaState.valueOf("AVAILABLE"));
        assertEquals(ArenaState.IN_USE, ArenaState.valueOf("IN_USE"));
        assertEquals(ArenaState.DISABLED, ArenaState.valueOf("DISABLED"));
    }
}
